package com.nachinbombin.midirandomizer

import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

enum class VoiceMode { HARMONY, INDEPENDENT }

/**
 * Harmony-mode parameters for Voice 2 / Voice 3.
 *
 * @param toneStepOffset   Diatonic index offset relative to the reference voice (+/-N steps).
 * @param timeDriftMs      Max micro-delay applied to Note-On (0–45 ms).
 * @param skipProbability  0.0–1.0: chance to omit a note (quantisation skip).
 * @param masterVelocity   0–127 master scaling factor for velocity.
 * @param velocityDrift    ± random spread added after master scaling.
 * @param midiChannel      0 = Omni (broadcast ch 1-16), 1-16 = specific channel.
 * @param referenceVoice   Which voice this is slaved to: 1 = Voice 1, 2 = Voice 2.
 */
data class HarmonyConfig(
    val toneStepOffset:  Int   = 2,
    val timeDriftMs:     Long  = 10L,
    val skipProbability: Float = 0f,
    val masterVelocity:  Int   = 100,
    val velocityDrift:   Int   = 8,
    val midiChannel:     Int   = 2,
    val referenceVoice:  Int   = 1   // 1 or 2 (Voice 3 only)
)

/**
 * Independent-mode parameters for Voice 2 / Voice 3 — mirrors all Voice 1 params.
 */
data class IndependentConfig(
    val bpm:           Int              = 120,
    val velocity:      Int              = 90,
    val minOctave:     Int              = 3,
    val maxOctave:     Int              = 5,
    val midiChannel:   Int              = 3,
    val selectedScale: Int              = 1,
    val timingMode:    Int              = MidiService.TIMING_METRONOME,
    val proSettings:   ProSettings      = ProSettings()
)

/**
 * Top-level descriptor for a secondary voice slot.
 */
data class VoiceConfig(
    val enabled:         Boolean           = false,
    val mode:            VoiceMode         = VoiceMode.HARMONY,
    val harmonyConfig:   HarmonyConfig     = HarmonyConfig(),
    val independentConfig: IndependentConfig = IndependentConfig()
)

// ─────────────────────────────────────────────────────────────────────────────
//  DIATONIC HARMONY ENGINE
// ─────────────────────────────────────────────────────────────────────────────

object DiatonicHarmony {

    /**
     * Build the full set of MIDI notes that are allowed by [scaleIntervals] and [rootNote]
     * across the entire 0-127 range.
     */
    fun allowedNotes(scaleIntervals: List<Int>, rootNote: Int = 0): IntArray {
        val result = mutableListOf<Int>()
        for (midi in 0..127) {
            if (((midi - rootNote) % 12 + 12) % 12 in scaleIntervals) result.add(midi)
        }
        return result.toIntArray()
    }

    /**
     * Locate [midiNote] in [allowedNotes] (nearest match) and return its index.
     */
    fun indexOf(midiNote: Int, allowedNotes: IntArray): Int {
        val exact = allowedNotes.indexOf(midiNote)
        if (exact >= 0) return exact
        // Nearest neighbour fallback
        return allowedNotes.indices.minByOrNull { kotlin.math.abs(allowedNotes[it] - midiNote) } ?: 0
    }

    /**
     * Apply a diatonic [stepOffset] to Voice 1's [v1MidiNote], clamped within
     * [allowedNotes]. Wrap-around if [wrapAround] is true, otherwise clamp to boundary.
     */
    fun applyOffset(
        v1MidiNote:   Int,
        stepOffset:   Int,
        allowedNotes: IntArray,
        wrapAround:   Boolean = false
    ): Int {
        if (allowedNotes.isEmpty()) return v1MidiNote
        val idx = indexOf(v1MidiNote, allowedNotes)
        val targetIdx = idx + stepOffset
        val clampedIdx = when {
            wrapAround  -> ((targetIdx % allowedNotes.size) + allowedNotes.size) % allowedNotes.size
            else        -> targetIdx.coerceIn(0, allowedNotes.lastIndex)
        }
        return allowedNotes[clampedIdx]
    }

    /**
     * Scale + drift velocity according to HarmonyConfig.
     */
    fun applyVelocity(v1Velocity: Int, cfg: HarmonyConfig): Int {
        val scaled = (v1Velocity * (cfg.masterVelocity / 127.0)).toInt()
        val drift  = if (cfg.velocityDrift > 0) Random.nextInt(-cfg.velocityDrift, cfg.velocityDrift + 1) else 0
        return (scaled + drift).coerceIn(1, 127)
    }
}
