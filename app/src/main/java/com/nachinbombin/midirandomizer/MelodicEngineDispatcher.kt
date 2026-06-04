package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Central dispatch point for all melodic engines.
 *
 * Given a [ProSettings], this class maintains the active Tier-1 or Tier-2
 * engine instance and exposes a single [nextDegree] call.
 *
 * MidiService and VoiceEngine both use this instead of calling
 * MarkovMelody directly.
 *
 * Beat tracking (for gesture curves) is handled internally:
 * call [advanceBeat] once per note event.
 */
class MelodicEngineDispatcher(
    private val scaleIntervals: List<Int>,
    private val settings: ProSettings
) {
    private val scaleSize = scaleIntervals.size

    // Tier-1
    private val markov: MarkovMelody? = if (
        settings.melodicEngine == MelodicEngine.MARKOV
    ) MarkovMelody(
        scaleSize  = scaleSize,
        style      = settings.melodicLogicStyle,
        secondOrder = settings.secondOrderMarkov,
        narmour    = settings.narmourConfig,
        gravity    = settings.contourGravityConfig
    ).also { it.reset() } else null

    // Tier-2
    private val pwg: PhraseGrammar? = if (
        settings.melodicEngine == MelodicEngine.PWG
    ) PhraseGrammar(scaleSize, settings.pwgConfig).also { it.reset() } else null

    private val lSystem: LSystemMelody? = if (
        settings.melodicEngine == MelodicEngine.L_SYSTEM
    ) LSystemMelody(scaleSize, settings.lSystemConfig).also { it.reset() } else null

    private val cellAuto: CellAutomata? = if (
        settings.melodicEngine == MelodicEngine.CELL_AUTOMATA
    ) CellAutomata(scaleIntervals, settings.cellAutomataConfig).also { it.reset() } else null

    private val nrtMelodic: NRTMelodicEngine? = if (
        settings.melodicEngine == MelodicEngine.NRT_MELODIC
    ) NRTMelodicEngine(scaleIntervals, settings.nrtMelodicConfig).also { it.reset() } else null

    // Gesture state
    private val gestureCfg = settings.gestureCurveConfig
    private var beatInPhrase: Int = 0
    private val phraseLen: Int = 16   // fixed 16-beat phrase for gesture arcs

    // ── public API ────────────────────────────────────────────────────────────

    fun advanceBeat() {
        beatInPhrase = (beatInPhrase + 1) % phraseLen
    }

    /**
     * Returns the next scale-degree index.
     * Applies gesture density gate internally; returns -1 if the note
     * should be suppressed (caller must handle skip).
     */
    fun nextDegree(): Int {
        val gestureFrame = GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen)

        // Density gate (Tier-1 only; Tier-2 engines own their own density)
        if (settings.melodicEngine == MelodicEngine.MARKOV &&
            gestureCfg.gestureDepth > 0f &&
            Random.nextFloat() > gestureFrame.densityGate
        ) {
            return -1  // caller should skip this onset
        }

        return when (settings.melodicEngine) {
            MelodicEngine.NAIVE         -> Random.nextInt(scaleSize)
            MelodicEngine.MARKOV        -> markov?.nextDegree(gestureFrame.pitchBias)
                                                  ?: Random.nextInt(scaleSize)
            MelodicEngine.PWG           -> pwg?.nextDegree()           ?: Random.nextInt(scaleSize)
            MelodicEngine.L_SYSTEM      -> lSystem?.nextDegree()       ?: Random.nextInt(scaleSize)
            MelodicEngine.CELL_AUTOMATA -> cellAuto?.nextDegree()      ?: Random.nextInt(scaleSize)
            MelodicEngine.NRT_MELODIC   -> nrtMelodic?.nextDegree()    ?: Random.nextInt(scaleSize)
        }
    }

    /** Expose gesture velocity scale to the caller for velocity shaping. */
    fun gestureVelocityScale(): Float {
        if (gestureCfg.gestureDepth == 0f) return 1f
        return GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen).velocityScale
    }

    /** Expose gesture register shift for octave selection adjustment. */
    fun gestureRegisterShift(): Int {
        if (gestureCfg.gestureDepth == 0f) return 0
        return GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen).registerShift
    }
}
