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
    private val settings: ProSettings,
    private val getContext: (Int) -> List<MidiService.MelodicEvent> = { emptyList() },
    private val getFuture: (Int) -> List<MidiService.MelodicEvent> = { emptyList() }
) {
    private val scaleSize = scaleIntervals.size

    // Tier-1
    private val markov: MarkovMelody? = if (
        settings.melodicEngine == MelodicEngine.MARKOV
    ) MarkovMelody(
        scaleSize   = scaleSize,
        style       = settings.melodicLogicStyle,
        secondOrder = settings.markovSecondOrder,
        narmour     = settings.toNarmourConfig(),
        gravity     = settings.toGravityConfig()
    ).also { it.reset() } else null

    // Tier-2
    private val pwg: PhraseGrammar? = if (
        settings.melodicEngine == MelodicEngine.PWG
    ) PhraseGrammar(scaleSize, settings.toPWGConfig()).also { it.reset() } else null

    private val lSystem: LSystemMelody? = if (
        settings.melodicEngine == MelodicEngine.LSYSTEM
    ) LSystemMelody(scaleSize, settings.toLSystemConfig()).also { it.reset() } else null

    private val cellAuto: CellAutomata? = if (
        settings.melodicEngine == MelodicEngine.CELLULAR
    ) CellAutomata(scaleIntervals, settings.toCellAutoConfig()).also { it.reset() } else null

    private val nrtMelodic: NRTMelodicEngine? = if (
        settings.melodicEngine == MelodicEngine.NRT
    ) NRTMelodicEngine(scaleIntervals, settings.toNRTConfig()).also { it.reset() } else null

    // Gesture state
    private val gestureCfg = settings.toGestureConfig()
    private var beatInPhrase: Int = 0
    private val phraseLen: Int = 16

    // ── public API ──────────────────────────────────────────────────────────────────

    fun advanceBeat() {
        beatInPhrase = (beatInPhrase + 1) % phraseLen
    }

    /**
     * Returns the next scale-degree index.
     * Returns -1 if the note should be suppressed (gesture density gate).
     */
    fun nextDegree(): Int {
        val gestureFrame = GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen)

        val context = getContext(1)

        if (settings.melodicEngine == MelodicEngine.MARKOV &&
            gestureCfg.gestureDepth > 0f &&
            Random.nextFloat() > gestureFrame.densityGate
        ) {
            return -1
        }

        return when (settings.melodicEngine) {
            MelodicEngine.NAIVE    -> Random.nextInt(scaleSize)
            MelodicEngine.MARKOV   -> markov?.nextDegree(gestureFrame.pitchBias, context)
                                             ?: Random.nextInt(scaleSize)
            MelodicEngine.PWG      -> pwg?.nextDegree(context)      ?: Random.nextInt(scaleSize)
            MelodicEngine.LSYSTEM  -> lSystem?.nextDegree(context)  ?: Random.nextInt(scaleSize)
            MelodicEngine.CELLULAR -> cellAuto?.nextDegree(context) ?: Random.nextInt(scaleSize)
            MelodicEngine.NRT      -> nrtMelodic?.nextDegree(context) ?: Random.nextInt(scaleSize)
        }
    }

    fun gestureVelocityScale(): Float {
        if (gestureCfg.gestureDepth == 0f) return 1f
        return GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen).velocityScale
    }

    fun gestureRegisterShift(): Int {
        if (gestureCfg.gestureDepth == 0f) return 0
        return GestureEngine.frame(gestureCfg, beatInPhrase, phraseLen).registerShift
    }
}
