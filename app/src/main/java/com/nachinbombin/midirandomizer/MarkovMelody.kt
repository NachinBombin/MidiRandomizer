package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Markov-chain melodic generator (Tier-1 engine).
 *
 * Supports:
 *  - First-order (history = 1 degree) — original behaviour.
 *  - Second-order (history = 2 degrees) — captures melodic direction.
 *  - Narmour IR scoring overlay — rescales weights by implication/realization rules.
 *  - Contour gravity overlay — opposes prolonged register drift.
 *  - Gesture curve pitch bias — adds directional pressure from GestureEngine.
 *
 * All overlays are opt-in; default values leave weight tables identical to
 * the original first-order implementation, preserving backwards compatibility.
 */
class MarkovMelody(
    private val scaleSize: Int,
    private val style: MelodicLogicStyle,
    private val secondOrder: Boolean       = false,
    private val narmour: NarmourConfig     = NarmourConfig(),
    private val gravity: ContourGravityConfig = ContourGravityConfig()
) {

    // ── state ─────────────────────────────────────────────────────────────────
    private var prev: Int = 0
    private var prevPrev: Int = 0  // used only when secondOrder == true
    private var contourAccum: Int = 0  // running signed sum of interval deltas

    // ── public API ────────────────────────────────────────────────────────────

    fun reset() {
        prev       = Random.nextInt(scaleSize)
        prevPrev   = prev
        contourAccum = 0
    }

    /**
     * Pick the next scale-degree index.
     *
     * @param gesturePitchBias  signed bias from GestureEngine (negative = pull down,
     *                          positive = pull up). 0f = no gesture influence.
     */
    fun nextDegree(gesturePitchBias: Float = 0f): Int {
        val weights = buildWeights(prev)

        // ── Tier-1 overlays ───────────────────────────────────────────────────
        if (secondOrder)    applySecondOrderBias(weights)
        if (narmour.enabled) applyNarmourScoring(weights)
        if (gravity.enabled) applyContourGravity(weights)
        if (gesturePitchBias != 0f) applyGestureBias(weights, gesturePitchBias)

        val next = weightedSample(weights)
        val delta = next - prev
        contourAccum += delta
        prevPrev = prev
        prev     = next
        return next
    }

    // ── weight construction ───────────────────────────────────────────────────

    private fun buildWeights(from: Int): FloatArray {
        val w = FloatArray(scaleSize) { 1f }   // uniform baseline
        w[from] = 0f                           // suppress self-repeat by default
        when (style) {
            MelodicLogicStyle.STEPWISE -> {
                for (to in 0 until scaleSize) {
                    val d = Math.abs(to - from)
                    when (d) {
                        1 -> w[to] += 8f
                        2 -> w[to] += 2f
                    }
                }
            }
            MelodicLogicStyle.ARPEGGIATED -> {
                for (to in 0 until scaleSize) {
                    val d = to - from
                    when (d) {
                        2, 4 -> w[to] += 6f
                        -2, -4 -> w[to] += 3f
                    }
                }
            }
            MelodicLogicStyle.WIDE_LEAPS -> {
                for (to in 0 until scaleSize) {
                    val d = Math.abs(to - from)
                    if (d in 4..6) w[to] += 5f
                }
            }
        }
        return w
    }

    // ── Tier-1: second-order direction bias ───────────────────────────────────

    /**
     * If the last two notes moved in direction D (up/down), boost candidates
     * that continue D and suppress strong reversals. This makes STEPWISE feel
     * like a walking line rather than a random hop.
     */
    private fun applySecondOrderBias(w: FloatArray) {
        val direction = (prev - prevPrev).coerceIn(-1, 1)  // -1, 0, +1
        if (direction == 0) return
        for (to in 0 until scaleSize) {
            val delta = to - prev
            val continueBonus = if (delta * direction > 0) 3f else 0f
            val reversalPenalty = if (delta * direction < 0 && Math.abs(delta) > 2) -2f else 0f
            w[to] = (w[to] + continueBonus + reversalPenalty).coerceAtLeast(0f)
        }
    }

    // ── Tier-1: Narmour IR scoring ────────────────────────────────────────────

    /**
     * Narmour Implication-Realization:
     *  - Small previous interval (≤2) → implication of continuation; boost same-direction
     *    candidates proportional to processVsReversal.
     *  - Large previous interval (≥4) → implication of reversal; boost step-reverse
     *    candidates, penalise second consecutive large leap.
     */
    private fun applyNarmourScoring(w: FloatArray) {
        val prevInterval = Math.abs(prev - prevPrev)
        val n = narmour
        for (to in 0 until scaleSize) {
            val delta = to - prev
            if (prevInterval <= 2) {
                // Small interval: continuation bias
                val prevDir = (prev - prevPrev).coerceIn(-1, 1)
                if (delta * prevDir > 0) {
                    w[to] += n.processVsReversal * 4f
                } else {
                    w[to] += (1f - n.processVsReversal) * 4f
                }
            } else if (prevInterval >= 4) {
                // Large interval: reversal + return bias
                val leapDir = (prev - prevPrev).coerceIn(-1, 1)
                val isStepReverse = (delta * leapDir < 0) && Math.abs(delta) <= 2
                if (isStepReverse) {
                    w[to] += n.returnBias * 5f
                }
                // Penalise second consecutive large leap in same direction
                if (delta * leapDir > 0 && Math.abs(delta) >= 4) {
                    w[to] = (w[to] - n.maxLeapPenalty * 6f).coerceAtLeast(0f)
                }
            }
        }
    }

    // ── Tier-1: contour gravity ────────────────────────────────────────────────

    /**
     * Once the melody has drifted up or down by more than [threshold] accumulated
     * degree steps, add a force pulling back toward the register centre.
     */
    private fun applyContourGravity(w: FloatArray) {
        if (Math.abs(contourAccum) <= gravity.threshold) return
        val pullDir = if (contourAccum > 0) -1 else +1  // oppose drift
        for (to in 0 until scaleSize) {
            val delta = to - prev
            if (delta * pullDir > 0) {
                w[to] += gravity.strength
            }
        }
    }

    // ── Tier-1: gesture curve pitch bias ──────────────────────────────────────

    /**
     * Translate a signed continuous bias value from GestureEngine into
     * a directional weight addend. Positive bias boosts higher degrees;
     * negative boosts lower degrees.
     */
    private fun applyGestureBias(w: FloatArray, bias: Float) {
        val mid = scaleSize / 2f
        for (to in 0 until scaleSize) {
            val distFromMid = (to - mid) / mid   // -1 … +1
            val bonus = bias * distFromMid * 4f
            w[to] = (w[to] + bonus).coerceAtLeast(0f)
        }
    }

    // ── utility ───────────────────────────────────────────────────────────────

    private fun weightedSample(w: FloatArray): Int {
        val total = w.sum()
        if (total <= 0f) return Random.nextInt(scaleSize)
        var r = Random.nextFloat() * total
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0f) return i
        }
        return w.indices.last
    }
}
