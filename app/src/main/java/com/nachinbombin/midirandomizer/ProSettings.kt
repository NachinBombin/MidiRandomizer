package com.nachinbombin.midirandomizer

/**
 * Immutable data class that carries all Pro-tier parameters.
 * Defaults match the original app behaviour (everything off / neutral).
 */
data class ProSettings(
    // ── Tier 1: Foundational ──────────────────────────────────────────────────
    /** 0–100 % humanising jitter applied on top of any timing mode */
    val jitterAmount: Int = 0,
    /** Distribution used for jitter sampling */
    val jitterType: JitterType = JitterType.UNIFORM,
    /** How note velocities are shaped across successive notes */
    val velocityPattern: VelocityPattern = VelocityPattern.RANDOM,

    // ── Tier 2: Structured generators ────────────────────────────────────────
    /** When true the Euclidean mode drives timing instead of the base mode */
    val euclideanEnabled: Boolean = false,
    /** Total number of steps in the Euclidean sequence (2–32) */
    val euclideanSteps: Int = 16,
    /** Number of active pulses to distribute (1..steps) */
    val euclideanDensity: Int = 5,
    /** Rotate the onset pattern by N positions */
    val euclideanRotation: Int = 0,

    /** When true a Markov-chain guides the next scale-degree choice */
    val markovEnabled: Boolean = false,
    /** Style of the transition matrix used by the Markov chain */
    val melodicLogicStyle: MelodicLogicStyle = MelodicLogicStyle.STEPWISE,

    // ── Tier 3: Advanced hybrid ───────────────────────────────────────────────
    /** Active preset; NONE means manual settings are used */
    val activePreset: ProPreset = ProPreset.NONE
)

enum class JitterType(val label: String) {
    UNIFORM("Uniform"),
    GAUSSIAN("Gaussian"),
    EXPONENTIAL("Exponential")
}

enum class VelocityPattern(val label: String) {
    RANDOM("Random"),
    ASCENDING("Ascending"),
    DESCENDING("Descending"),
    PEAK_CENTER("Peak at Centre"),
    ACCENT_BEATS("Accent Beats")
}

enum class MelodicLogicStyle(val label: String) {
    STEPWISE("Stepwise (Melody Flow)"),
    ARPEGGIATED("Arpeggiated"),
    WIDE_LEAPS("Wide Leaps (Experimental)")
}

enum class ProPreset(val label: String) {
    NONE("— Manual —"),
    AMBIENT_TEXTURE("Ambient Texture"),
    EXPERIMENTAL_SOUNDSCAPE("Experimental Soundscape")
}
