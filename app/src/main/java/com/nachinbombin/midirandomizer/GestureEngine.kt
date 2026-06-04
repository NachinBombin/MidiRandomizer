package com.nachinbombin.midirandomizer

/**
 * Mazzola-inspired gesture curve engine.
 *
 * Evaluates piecewise-linear phrase arcs at a normalised phrase position t ∈ [0,1].
 * Each preset is a list of (t, value) control points; value is in [-1, +1].
 *
 * Outputs:
 *  - pitchBias      : signed float added to MarkovMelody as a weight bias
 *  - registerShift  : signed int applied to octave selection window
 *  - densityGate    : float 0–1; note is suppressed if Random.nextFloat() > densityGate
 *  - velocityScale  : float 0–2; multiplied onto the base velocity
 */
object GestureEngine {

    data class ControlPoint(val t: Float, val value: Float)

    /** Built-in curve presets indexed by the Int stored in GestureCurveConfig. */
    val PRESETS: List<List<ControlPoint>> = listOf(
        // 0 – flat (identity)
        listOf(ControlPoint(0f, 0f), ControlPoint(1f, 0f)),
        // 1 – rising arch
        listOf(ControlPoint(0f, -0.5f), ControlPoint(0.5f, 1f), ControlPoint(1f, -0.5f)),
        // 2 – falling arch
        listOf(ControlPoint(0f, 0.5f), ControlPoint(0.5f, -1f), ControlPoint(1f, 0.5f)),
        // 3 – sawtooth rise
        listOf(ControlPoint(0f, -1f), ControlPoint(1f, 1f)),
        // 4 – sawtooth fall
        listOf(ControlPoint(0f, 1f), ControlPoint(1f, -1f)),
        // 5 – step up at midpoint
        listOf(ControlPoint(0f, -0.5f), ControlPoint(0.49f, -0.5f), ControlPoint(0.5f, 0.5f), ControlPoint(1f, 0.5f)),
        // 6 – crescendo-decrescendo (density/velocity only)
        listOf(ControlPoint(0f, 0f), ControlPoint(0.5f, 1f), ControlPoint(1f, 0f)),
        // 7 – valley (low density at phrase start and end)
        listOf(ControlPoint(0f, 1f), ControlPoint(0.25f, 0f), ControlPoint(0.75f, 0f), ControlPoint(1f, 1f))
    )

    /** Evaluate a preset curve at normalised position [t] in [0, 1]. */
    fun evaluate(presetIndex: Int, t: Float): Float {
        val pts = PRESETS.getOrElse(presetIndex) { PRESETS[0] }
        if (pts.size == 1) return pts[0].value
        val clampedT = t.coerceIn(0f, 1f)
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            if (clampedT <= b.t) {
                val frac = if (b.t - a.t == 0f) 0f else (clampedT - a.t) / (b.t - a.t)
                return a.value + frac * (b.value - a.value)
            }
        }
        return pts.last().value
    }

    data class GestureFrame(
        val pitchBias: Float,
        val registerShift: Int,
        val densityGate: Float,   // 0..1; note fires if Random < gate
        val velocityScale: Float  // applied multiplicatively to base velocity
    )

    /**
     * Compute a full GestureFrame for the current phrase position.
     *
     * @param cfg       the GestureCurveConfig from ProSettings
     * @param beatInPhrase 0-based beat within the current phrase
     * @param phraseLen    total beats per phrase
     */
    fun frame(cfg: GestureCurveConfig, beatInPhrase: Int, phraseLen: Int): GestureFrame {
        if (cfg.gestureDepth == 0f || phraseLen <= 0) {
            return GestureFrame(0f, 0, 1f, 1f)
        }
        val t = (beatInPhrase % phraseLen) / phraseLen.toFloat()
        val d = cfg.gestureDepth
        val pitch  = evaluate(cfg.pitchCurvePreset,    t) * d
        val reg    = evaluate(cfg.registerCurvePreset, t) * d
        val dens   = ((evaluate(cfg.densityCurvePreset,  t) + 1f) / 2f).coerceIn(0f, 1f)
        val densGated = (dens * d + (1f - d)).coerceIn(0f, 1f)  // at d=0 gate is always 1
        val velRaw = evaluate(cfg.velocityCurvePreset, t) * d
        val velScale = (1f + velRaw).coerceIn(0.2f, 2f)
        return GestureFrame(
            pitchBias     = pitch,
            registerShift = (reg * 2f).toInt().coerceIn(-2, 2),
            densityGate   = densGated,
            velocityScale = velScale
        )
    }
}
