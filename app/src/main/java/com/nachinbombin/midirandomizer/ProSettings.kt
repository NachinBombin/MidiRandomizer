package com.nachinbombin.midirandomizer

// ─────────────────────────────────────────────────────────────────────────────
// Enums shared across engines
// ─────────────────────────────────────────────────────────────────────────────

enum class VelocityPattern { RANDOM, ACCENT, CRESCENDO, DECRESCENDO, FLAT }

enum class JitterType { NONE, SLIGHT, MODERATE, HEAVY }

enum class MelodicLogicStyle { STEPWISE, ARPEGGIATED, WIDE_LEAPS }

/**
 * Top-level melodic engine selector.
 *
 * NAIVE        – pure random degree, no memory (always the default fallback).
 * MARKOV       – first/second-order Markov chain with optional Tier-1 overlays
 *                (Narmour IR scoring, contour gravity, beat-phase boost).
 * PWG          – Probabilistic Weighted Grammar: phrase-level motif rewriting.
 * L_SYSTEM     – Lindenmayer fractal melody; self-similar structure.
 * CELL_AUTOMATA– Pitch-class cellular automaton; spectral evolution.
 * NRT_MELODIC  – Melody derived from Neo-Riemannian Tonnetz walks (PLR).
 */
enum class MelodicEngine {
    NAIVE,
    MARKOV,
    PWG,
    L_SYSTEM,
    CELL_AUTOMATA,
    NRT_MELODIC
}

// ─────────────────────────────────────────────────────────────────────────────
// Tier-1 overlay configs (only active when melodicEngine == MARKOV)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Narmour Implication-Realization overlay.
 * Rescales Markov weights based on previous-interval size.
 *
 * @param enabled           master on/off switch
 * @param processVsReversal 0 = always reverse after small interval,
 *                          1 = always continue; 0.5 = neutral
 * @param returnBias        strength of post-leap step-return pull (0–1)
 * @param maxLeapPenalty    how much to suppress a second consecutive large leap (0–1)
 */
data class NarmourConfig(
    val enabled: Boolean = false,
    val processVsReversal: Float = 0.5f,
    val returnBias: Float = 0.5f,
    val maxLeapPenalty: Float = 0.5f
)

/**
 * Contour gravity — opposes extended melodic drift in one register.
 *
 * @param enabled    master on/off
 * @param threshold  accumulated delta steps before gravity kicks in
 * @param strength   bias magnitude added per step past threshold (scale: 0–8)
 */
data class ContourGravityConfig(
    val enabled: Boolean = false,
    val threshold: Int = 5,
    val strength: Float = 3f
)

/**
 * Gesture curve config (Mazzola-inspired piecewise-linear phrase arcs).
 * Each curve preset is a piecewise-linear index into GestureEngine.PRESETS.
 *
 * @param gestureDepth 0 = off (pure Markov), 1 = full curve dominance
 */
data class GestureCurveConfig(
    val pitchCurvePreset: Int    = 0,   // 0=flat
    val registerCurvePreset: Int = 0,
    val densityCurvePreset: Int  = 0,
    val velocityCurvePreset: Int = 0,
    val gestureDepth: Float = 0f
)

// ─────────────────────────────────────────────────────────────────────────────
// Tier-2 replacement engine configs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Probabilistic Weighted Grammar config.
 *
 * @param phraseLengthMotifs how many motifs to string per phrase (1–8)
 * @param motifSetIndex      which built-in motif vocabulary (0–3)
 * @param directionBias      -1 = descending bias, 0 = neutral, +1 = ascending bias
 */
data class PWGConfig(
    val phraseLengthMotifs: Int = 2,
    val motifSetIndex: Int = 0,
    val directionBias: Float = 0f
)

/**
 * L-System fractal melody config.
 *
 * @param axiomIndex    index into LSystemMelody.AXIOMS (seed symbol)
 * @param iterations    rewrite iterations applied at phrase start (1–4)
 * @param ruleVariance  0 = deterministic, 1 = frequent symbol mutations
 */
data class LSystemConfig(
    val axiomIndex: Int = 0,
    val iterations: Int = 2,
    val ruleVariance: Float = 0.1f
)

/**
 * Pitch-class cellular automaton config.
 *
 * @param survivalMin   min living neighbors to survive
 * @param survivalMax   max living neighbors to survive
 * @param birthCount    exact neighbors to be born
 * @param mutationRate  probability of random bit-flip per generation (0–1)
 */
data class CellAutomataConfig(
    val survivalMin: Int = 1,
    val survivalMax: Int = 2,
    val birthCount: Int  = 2,
    val mutationRate: Float = 0.05f
)

/**
 * Neo-Riemannian melodic engine config.
 * Melody notes are drawn from the chord tones of the current NRT Klang.
 *
 * @param pWeight  probability weight for P (Parallel) transformation
 * @param lWeight  probability weight for L (Leading-tone exchange)
 * @param rWeight  probability weight for R (Relative)
 * @param cyclePreset 0 = random PLR walk, 1 = LPPL hexatonic, 2 = PRRP octatonic
 */
data class NRTMelodicConfig(
    val pWeight: Float = 1f,
    val lWeight: Float = 1f,
    val rWeight: Float = 1f,
    val cyclePreset: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Master ProSettings data class
// ─────────────────────────────────────────────────────────────────────────────

data class ProSettings(
    // ── existing fields (unchanged) ──────────────────────────────────────────
    val markovEnabled: Boolean               = false,
    val melodicLogicStyle: MelodicLogicStyle = MelodicLogicStyle.STEPWISE,
    val velocityPattern: VelocityPattern     = VelocityPattern.RANDOM,
    // jitterAmount is 0–100 Int (percentage of base interval)
    val jitterAmount: Int                    = 0,
    val jitterType: JitterType               = JitterType.NONE,
    val euclideanEnabled: Boolean            = false,
    val euclideanSteps: Int                  = 16,
    val euclideanDensity: Int                = 8,
    val euclideanRotation: Int               = 0,

    // ── new: top-level engine selector ───────────────────────────────────────
    val melodicEngine: MelodicEngine = MelodicEngine.NAIVE,

    // ── new: Tier-1 overlays (active only when melodicEngine == MARKOV) ──────
    val narmourConfig: NarmourConfig               = NarmourConfig(),
    val contourGravityConfig: ContourGravityConfig = ContourGravityConfig(),
    val gestureCurveConfig: GestureCurveConfig     = GestureCurveConfig(),
    val secondOrderMarkov: Boolean                 = false,

    // ── new: Tier-2 replacement engine configs ────────────────────────────────
    val pwgConfig: PWGConfig                   = PWGConfig(),
    val lSystemConfig: LSystemConfig           = LSystemConfig(),
    val cellAutomataConfig: CellAutomataConfig = CellAutomataConfig(),
    val nrtMelodicConfig: NRTMelodicConfig     = NRTMelodicConfig()
)
