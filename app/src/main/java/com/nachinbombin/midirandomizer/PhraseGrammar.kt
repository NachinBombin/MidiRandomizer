package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Probabilistic Weighted Grammar — Tier-2 replacement melodic engine.
 *
 * Generates melodies as sequences of pre-defined degree-delta motifs.
 * A full phrase is generated up-front; notes are served one by one from the
 * buffer. When the buffer empties the next phrase is generated.
 *
 * Unlike Markov, this produces phrase-level coherence: the shape of each
 * motif is intentional, and direction is determined at the phrase level.
 */
class PhraseGrammar(
    private val scaleSize: Int,
    private val cfg: PWGConfig
) {

    // ── built-in motif vocabularies ───────────────────────────────────────────

    data class Motif(val name: String, val deltas: List<Int>)

    companion object {
        /** Four motif sets with different characters. */
        val MOTIF_SETS: List<List<Motif>> = listOf(
            // 0 – balanced (stepwise dominant)
            listOf(
                Motif("Step Up",       listOf(+1, +1)),
                Motif("Step Down",     listOf(-1, -1)),
                Motif("Neighbor",      listOf(+1, -1, 0)),
                Motif("Ascend 3rd",   listOf(+1, +2)),
                Motif("Leap Return",  listOf(+3, -2)),
                Motif("Trill Up",     listOf(+1, -1, +1, -1))
            ),
            // 1 – ascending (bright, driving)
            listOf(
                Motif("Scale Run",    listOf(+1, +1, +1)),
                Motif("Arpeggio Up",  listOf(+2, +2)),
                Motif("Big Leap",     listOf(+4, -1)),
                Motif("Step + Leap",  listOf(+1, +3)),
                Motif("Rocket",       listOf(+2, +2, +1)),
                Motif("Step Back",    listOf(+2, -1))
            ),
            // 2 – descending (dark, resolving)
            listOf(
                Motif("Fall",         listOf(-1, -1, -1)),
                Motif("Arpeggio Dn",  listOf(-2, -2)),
                Motif("Drop",         listOf(-4, +1)),
                Motif("Sigh",         listOf(-1, -2)),
                Motif("Descent Run",  listOf(-2, -1, -1)),
                Motif("Bump Down",    listOf(-2, +1))
            ),
            // 3 – angular (wide, chromatic character)
            listOf(
                Motif("Wide Leap",    listOf(+5, -3)),
                Motif("Counter",      listOf(-4, +3, -1)),
                Motif("Zigzag",       listOf(+3, -4, +2)),
                Motif("Long Leap",    listOf(+6, -1)),
                Motif("Fall Back Up", listOf(-5, +4)),
                Motif("Arch",         listOf(+2, +2, -3))
            )
        )
    }

    // ── state ─────────────────────────────────────────────────────────────────

    private val buffer: ArrayDeque<Int> = ArrayDeque()
    private var currentDegree: Int = Random.nextInt(scaleSize)

    // ── public API ────────────────────────────────────────────────────────────

    fun reset() {
        buffer.clear()
        currentDegree = Random.nextInt(scaleSize)
    }

    fun nextDegree(context: List<MidiService.MelodicEvent> = emptyList()): Int {
        if (buffer.isEmpty()) generatePhrase()
        val delta = buffer.removeFirst()
        currentDegree = wrapDegree(currentDegree + delta)
        return currentDegree
    }

    // ── phrase generation ─────────────────────────────────────────────────────

    private fun generatePhrase() {
        val motifs  = MOTIF_SETS.getOrElse(cfg.motifSetIndex) { MOTIF_SETS[0] }
        val count   = cfg.phraseLengthMotifs.coerceIn(1, 8)
        val bias    = cfg.directionBias                       // -1..+1
        val weights = buildMotifWeights(motifs, bias)

        repeat(count) {
            val motif = weightedSample(motifs, weights)
            buffer.addAll(motif.deltas)
        }
    }

    /**
     * Build per-motif weights influenced by [bias].
     * Positive bias boosts ascending motifs; negative boosts descending.
     */
    private fun buildMotifWeights(motifs: List<Motif>, bias: Float): FloatArray {
        return FloatArray(motifs.size) { i ->
            val m   = motifs[i]
            val net = m.deltas.sum().toFloat()  // net direction of this motif
            val w   = 1f + bias * (net / (m.deltas.size.toFloat()))
            w.coerceAtLeast(0.1f)
        }
    }

    private fun weightedSample(motifs: List<Motif>, weights: FloatArray): Motif {
        val total = weights.sum()
        var r = Random.nextFloat() * total
        for (i in motifs.indices) {
            r -= weights[i]
            if (r <= 0f) return motifs[i]
        }
        return motifs.last()
    }

    private fun wrapDegree(d: Int): Int = ((d % scaleSize) + scaleSize) % scaleSize
}
