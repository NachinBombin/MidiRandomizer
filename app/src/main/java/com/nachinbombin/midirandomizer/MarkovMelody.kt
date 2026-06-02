package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * First-order Markov chain over scale degrees.
 *
 * Each [MelodicLogicStyle] encodes a different transition bias:
 *   - STEPWISE   : strong preference for adjacent scale degrees (+/-1 step)
 *   - ARPEGGIATED: preference for +2 / +4 jumps (chord-like leaps)
 *   - WIDE_LEAPS : preference for jumps of 4–6 scale degrees (experimental)
 *
 * The chain always stays within the provided scale size, wrapping around.
 */
class MarkovMelody(private val scaleSize: Int, private val style: MelodicLogicStyle) {

    private var currentDegree: Int = 0

    /**
     * Returns the next scale degree index (0-based, within [scaleSize]).
     * If [currentDegree] has not been set yet a random starting point is chosen.
     */
    fun nextDegree(): Int {
        if (scaleSize <= 1) return 0
        val weights = buildWeights(currentDegree)
        currentDegree = weightedSample(weights)
        return currentDegree
    }

    fun reset() { currentDegree = Random.nextInt(scaleSize) }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun buildWeights(from: Int): FloatArray {
        val w = FloatArray(scaleSize) { 1f }          // uniform baseline
        when (style) {
            MelodicLogicStyle.STEPWISE -> {
                // Heavily bias toward ±1 step
                adjust(w, from, delta = 1, weight = 8f)
                adjust(w, from, delta = -1, weight = 8f)
                adjust(w, from, delta = 2, weight = 2f)
                adjust(w, from, delta = -2, weight = 2f)
            }
            MelodicLogicStyle.ARPEGGIATED -> {
                // Bias toward root, third, fifth (degrees 0, 2, 4 in diatonic)
                adjust(w, from, delta = 2, weight = 6f)
                adjust(w, from, delta = 4, weight = 6f)
                adjust(w, from, delta = -2, weight = 4f)
                adjust(w, from, delta = -4, weight = 4f)
            }
            MelodicLogicStyle.WIDE_LEAPS -> {
                // Prefer big leaps
                for (delta in 4..6) {
                    adjust(w, from, delta, 5f)
                    adjust(w, from, -delta, 5f)
                }
                // Keep some baseline for stepwise too
                adjust(w, from, 1, 2f)
                adjust(w, from, -1, 2f)
            }
        }
        // Never stay on the same note (weight 0 for self)
        w[from] = 0f
        return w
    }

    /** Adds [weight] to the entry at (from + delta) mod scaleSize */
    private fun adjust(w: FloatArray, from: Int, delta: Int, weight: Float) {
        val idx = ((from + delta) % scaleSize + scaleSize) % scaleSize
        w[idx] += weight
    }

    private fun weightedSample(weights: FloatArray): Int {
        val total = weights.sum()
        if (total == 0f) return Random.nextInt(scaleSize)
        var r = Random.nextFloat() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0f) return i
        }
        return weights.size - 1
    }
}
