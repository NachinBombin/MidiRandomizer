package com.nachinbombin.midirandomizer

// ─────────────────────────────────────────────────────────────────────────────
// Shared enums
// ─────────────────────────────────────────────────────────────────────────────

enum class VelocityPattern { RANDOM, ACCENT, CRESCENDO, DECRESCENDO, FLAT }
enum class JitterType { NONE, SLIGHT, MODERATE, HEAVY }
enum class MelodicLogicStyle { STEPWISE, ARPEGGIATED, WIDE_LEAPS }

/**
 * Top-level melodic engine selector.
 * Names match ProSettingsFragment spinner references exactly.
 */
enum class MelodicEngine {
    NAIVE,
    MARKOV,
    PWG,
    LSYSTEM,
    CELLULAR,
    NRT
}

// ── Markov ───────────────────────────────────────────────────────────────────
enum class MarkovLogicStyle { STEPWISE, ARPEGGIATED, WIDE_LEAPS, CHROMATIC }

// ── NRT Melodic ───────────────────────────────────────────────────────────────
enum class NrtCycle { RANDOM_WALK, HEXATONIC, OCTATONIC }

// ── PWG ──────────────────────────────────────────────────────────────────────
enum class PwgMotif { ASCENDING, DESCENDING, ARCH, VALLEY, STATIC }

// ── L-System ─────────────────────────────────────────────────────────────────
enum class LSystemAxiom { A, B, C, D }

// ── Gesture curves ────────────────────────────────────────────────────────────
enum class GesturePitchShape       { FLAT, ARCH, VALLEY, ASCENDING, DESCENDING }
enum class GestureRegisterTendency { NEUTRAL, HIGH, LOW, CLIMBING, FALLING }
enum class GestureDensityProfile   { UNIFORM, DENSE_START, DENSE_END, SPARSE }
enum class GestureVelocityProfile  { FLAT, CRESCENDO, DECRESCENDO, ACCENT_BEATS }

// ─────────────────────────────────────────────────────────────────────────────
// Nested config data classes (used by engines internally)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Narmour Implication-Realization overlay.
 */
data class NarmourConfig(
    val enabled: Boolean = false,
    val processVsReversal: Float = 0.5f,
    val returnBias: Float = 0.5f,
    val maxLeapPenalty: Float = 0.5f
)

/**
 * Contour gravity — opposes extended melodic drift in one register.
 */
data class ContourGravityConfig(
    val enabled: Boolean = false,
    val threshold: Int = 5,
    val strength: Float = 3f
)

/**
 * Gesture curve config (Mazzola-inspired piecewise-linear phrase arcs).
 */
data class GestureCurveConfig(
    val pitchCurvePreset: Int    = 0,
    val registerCurvePreset: Int = 0,
    val densityCurvePreset: Int  = 0,
    val velocityCurvePreset: Int = 0,
    val gestureDepth: Float = 0f
)

/**
 * Probabilistic Weighted Grammar config.
 */
data class PWGConfig(
    val phraseLengthMotifs: Int = 2,
    val motifSetIndex: Int = 0,
    val directionBias: Float = 0f
)

/**
 * L-System fractal melody config.
 */
data class LSystemConfig(
    val axiomIndex: Int = 0,
    val iterations: Int = 2,
    val ruleVariance: Float = 0.1f
)

/**
 * Pitch-class cellular automaton config.
 */
data class CellAutomataConfig(
    val survivalMin: Int = 1,
    val survivalMax: Int = 2,
    val birthCount: Int  = 2,
    val mutationRate: Float = 0.05f
)

/**
 * Neo-Riemannian melodic engine config.
 */
data class NRTMelodicConfig(
    val pWeight: Float = 1f,
    val lWeight: Float = 1f,
    val rWeight: Float = 1f,
    val cyclePreset: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Master ProSettings — flat fields matching ProSettingsFragment bindings
// ─────────────────────────────────────────────────────────────────────────────

data class ProSettings(
    // ── Basic ─────────────────────────────────────────────────────────────────
    val markovEnabled: Boolean               = false,
    val melodicLogicStyle: MelodicLogicStyle = MelodicLogicStyle.STEPWISE,
    val velocityPattern: VelocityPattern     = VelocityPattern.RANDOM,
    val jitterAmount: Int                    = 0,
    val jitterType: JitterType               = JitterType.NONE,
    val euclideanEnabled: Boolean            = false,
    val euclideanSteps: Int                  = 16,
    val euclideanDensity: Int                = 8,
    val euclideanRotation: Int               = 0,

    // ── Engine selector ───────────────────────────────────────────────────────
    val melodicEngine: MelodicEngine = MelodicEngine.NAIVE,

    // ── Markov ────────────────────────────────────────────────────────────────
    val markovLogicStyle: MarkovLogicStyle = MarkovLogicStyle.STEPWISE,
    val markovSecondOrder: Boolean         = false,

    // ── Narmour overlay ───────────────────────────────────────────────────────
    val narmourEnabled: Boolean   = false,
    val narmourProcessWeight: Int = 50,
    val narmourReturnWeight: Int  = 50,
    val narmourLeapThreshold: Int = 5,

    // ── Contour Gravity overlay ───────────────────────────────────────────────
    val gravityEnabled: Boolean = false,
    val gravityThreshold: Int   = 5,
    val gravityStrength: Int    = 3,

    // ── Gesture overlay ───────────────────────────────────────────────────────
    val gestureEnabled: Boolean                      = false,
    val gestureDepth: Int                            = 0,
    val gesturePitchShape: GesturePitchShape         = GesturePitchShape.FLAT,
    val gestureRegister: GestureRegisterTendency     = GestureRegisterTendency.NEUTRAL,
    val gestureDensity: GestureDensityProfile        = GestureDensityProfile.UNIFORM,
    val gestureVelocity: GestureVelocityProfile      = GestureVelocityProfile.FLAT,

    // ── NRT Melodic ───────────────────────────────────────────────────────────
    val nrtCycle: NrtCycle = NrtCycle.RANDOM_WALK,
    val nrtPWeight: Int    = 50,
    val nrtLWeight: Int    = 50,
    val nrtRWeight: Int    = 50,

    // ── PWG ───────────────────────────────────────────────────────────────────
    val pwgMotif: PwgMotif    = PwgMotif.STATIC,
    val pwgPhraseLen: Int     = 2,
    val pwgDirectionBias: Int = 0,

    // ── L-System ──────────────────────────────────────────────────────────────
    val lSystemAxiom: LSystemAxiom = LSystemAxiom.A,
    val lSystemIterations: Int     = 2,
    val lSystemVariance: Int       = 10,

    // ── Cell Automata ─────────────────────────────────────────────────────────
    val caSurvMin: Int  = 1,
    val caSurvMax: Int  = 2,
    val caBirth: Int    = 2,
    val caMutation: Int = 5
) {
    /** Synthesise nested configs for engine consumption. */
    fun toNarmourConfig() = NarmourConfig(
        enabled           = narmourEnabled,
        processVsReversal = narmourProcessWeight / 100f,
        returnBias        = narmourReturnWeight / 100f,
        maxLeapPenalty    = narmourLeapThreshold / 12f
    )

    fun toGravityConfig() = ContourGravityConfig(
        enabled   = gravityEnabled,
        threshold = gravityThreshold,
        strength  = gravityStrength.toFloat()
    )

    fun toGestureConfig() = GestureCurveConfig(
        pitchCurvePreset    = gesturePitchShape.ordinal,
        registerCurvePreset = gestureRegister.ordinal,
        densityCurvePreset  = gestureDensity.ordinal,
        velocityCurvePreset = gestureVelocity.ordinal,
        gestureDepth        = gestureDepth / 100f
    )

    fun toPWGConfig() = PWGConfig(
        phraseLengthMotifs = pwgPhraseLen,
        motifSetIndex      = pwgMotif.ordinal,
        directionBias      = pwgDirectionBias / 4f
    )

    fun toLSystemConfig() = LSystemConfig(
        axiomIndex    = lSystemAxiom.ordinal,
        iterations    = lSystemIterations,
        ruleVariance  = lSystemVariance / 100f
    )

    fun toCellAutoConfig() = CellAutomataConfig(
        survivalMin  = caSurvMin,
        survivalMax  = caSurvMax,
        birthCount   = caBirth,
        mutationRate = caMutation / 100f
    )

    fun toNRTConfig() = NRTMelodicConfig(
        pWeight      = nrtPWeight / 100f,
        lWeight      = nrtLWeight / 100f,
        rWeight      = nrtRWeight / 100f,
        cyclePreset  = nrtCycle.ordinal
    )
}
