package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * NRT Melodic Engine — Tier-2 replacement.
 *
 * Drives melody by walking the Tonnetz via PLR operations (NRTEngine) and
 * mapping the resulting Klang's chord tones to the nearest scale degree indices.
 *
 * One Tonnetz step is taken every [stepsPerKlang] notes, so the melodic
 * content shifts organically with each harmonic transformation.
 *
 * The engine produces notes that always belong to the current scale
 * (because degree indices are clamped to the scale array bounds).
 */
class NRTMelodicEngine(
    private val scaleIntervals: List<Int>,
    private val cfg: NRTMelodicConfig
) {
    private val nrtEngine    = NRTEngine()
    private val noteQueue    = ArrayDeque<Int>()
    private var notesServed  = 0
    private val stepsPerKlang = 4   // PLR step every 4 notes

    fun reset(rootPc: Int = 0, isMajor: Boolean = true) {
        nrtEngine.reset(rootPc, isMajor)
        noteQueue.clear()
        notesServed = 0
        enqueueKlangNotes()
    }

    fun nextDegree(): Int {
        if (noteQueue.isEmpty()) {
            if (notesServed % stepsPerKlang == 0) {
                nrtEngine.step(cfg)
            }
            enqueueKlangNotes()
        }
        if (noteQueue.isEmpty()) return Random.nextInt(scaleIntervals.size)
        notesServed++
        return noteQueue.removeFirst()
    }

    /**
     * Map current Klang chord tones to scale degree indices, then shuffle and
     * enqueue them so they can be served as individual notes.
     */
    private fun enqueueKlangNotes() {
        val tones   = nrtEngine.chordTones()
        val degrees = tones.mapNotNull { pc ->
            // Find the scale degree whose interval is closest to this PC
            scaleIntervals
                .mapIndexed { idx, interval -> idx to Math.abs(interval - pc) }
                .minByOrNull { it.second }
                ?.first
        }.distinct()
        noteQueue.addAll(degrees.shuffled())
    }
}
