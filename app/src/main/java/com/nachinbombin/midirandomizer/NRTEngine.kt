package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Neo-Riemannian Theory engine.
 *
 * Maintains a Klang (consonant triad state) and performs P/L/R Tonnetz walks.
 * Used both as:
 *  - A CHORD harmony engine (via HarmonyMode.NEO_RIEMANNIAN in future chord update).
 *  - A MELODIC engine (NRTMelodicEngine) that maps Klang chord tones to scale degrees.
 *
 * Transformations (all involutions — applying twice returns to origin):
 *  P  Parallel:           toggles major/minor, same root
 *  L  Leading-tone exch:  C maj → E min  (root: C→B semitone)
 *  R  Relative:           C maj → A min  (root shift of ±9/3 semitones)
 *
 * Cycle presets for live performance:
 *  0 – random PLR walk
 *  1 – LPPL hexatonic cycle (6 chords, repeating)
 *  2 – PRRP octatonic cycle (8 chords)
 */
class NRTEngine(startRoot: Int = 0, startMajor: Boolean = true) {

    data class Klang(val rootPc: Int, val isMajor: Boolean)

    var klang: Klang = Klang(startRoot, startMajor)
        private set

    // ── cycle state ────────────────────────────────────────────────────────

    private val hexatonicCycle = listOf('P','L','P','L','P','L')   // LPLPLP repeating => actually LPPL is 4-op
    private val LPPL           = listOf('L','P','P','L')
    private val PRRP           = listOf('P','R','R','P')

    private var cyclePos: Int = 0

    // ── public API ──────────────────────────────────────────────────────────

    fun reset(rootPc: Int, isMajor: Boolean) {
        klang    = Klang(rootPc, isMajor)
        cyclePos = 0
    }

    /**
     * Advance the NRT state by one step.
     *
     * @param cfg  NRTMelodicConfig (weights + cycle preset)
     * @return the new Klang after the transformation
     */
    fun step(cfg: NRTMelodicConfig): Klang {
        val op = when (cfg.cyclePreset) {
            1    -> nextCycleOp(LPPL)
            2    -> nextCycleOp(PRRP)
            else -> weightedOp(cfg.pWeight, cfg.lWeight, cfg.rWeight)
        }
        klang = applyOp(op, klang)
        return klang
    }

    // ── transformations ────────────────────────────────────────────────────

    fun applyP(k: Klang = klang): Klang = k.copy(isMajor = !k.isMajor)

    fun applyL(k: Klang = klang): Klang {
        val shift = if (k.isMajor) -1 else +1
        return Klang((k.rootPc + shift + 12) % 12, !k.isMajor)
    }

    fun applyR(k: Klang = klang): Klang {
        val shift = if (k.isMajor) 9 else 3
        return Klang((k.rootPc + shift) % 12, !k.isMajor)
    }

    /** Return the three pitch classes of a Klang: [root, third, fifth]. */
    fun chordTones(k: Klang = klang): List<Int> {
        val root  = k.rootPc
        val third = (root + if (k.isMajor) 4 else 3) % 12
        val fifth = (root + 7) % 12
        return listOf(root, third, fifth)
    }

    // ── private helpers ────────────────────────────────────────────────────

    private fun applyOp(op: Char, k: Klang): Klang = when (op) {
        'P'  -> applyP(k)
        'L'  -> applyL(k)
        else -> applyR(k)
    }

    private fun nextCycleOp(cycle: List<Char>): Char {
        val op = cycle[cyclePos % cycle.size]
        cyclePos++
        return op
    }

    private fun weightedOp(pw: Float, lw: Float, rw: Float): Char {
        val total = pw + lw + rw
        var r = Random.nextFloat() * total
        r -= pw; if (r <= 0f) return 'P'
        r -= lw; if (r <= 0f) return 'L'
        return 'R'
    }
}
