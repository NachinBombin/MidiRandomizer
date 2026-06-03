package com.nachinbombin.midirandomizer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.random.Random

// ── Style ─────────────────────────────────────────────────────────────────────
@Parcelize
enum class VoiceStyle : Parcelable {
    GENERATIVE,        // 0 – current random-note framework
    SINGLE_NOTE_DRONE, // 1 – one sustained note held until stopped
    EVOLVING_DRONE     // 2 – slow melodic drone that changes note on beat
}

// ── Mode (Harmony vs Independent) ────────────────────────────────────────────
@Parcelize
enum class VoiceMode : Parcelable { HARMONY, INDEPENDENT }

// ── Harmony config ────────────────────────────────────────────────────────────
@Parcelize
data class HarmonyConfig(
    val toneStepOffset:  Int   = 2,
    val timeDriftMs:     Long  = 10,
    val skipProbability: Float = 0f,
    val masterVelocity:  Int   = 100,
    val velocityDrift:   Int   = 8,
    val midiChannel:     Int   = 1,
    val referenceVoice:  Int   = 1
) : Parcelable

// ── Generative / Independent config ──────────────────────────────────────────
@Parcelize
data class IndependentConfig(
    val bpm:           Int = 120,
    val velocity:      Int = 90,
    val minOctave:     Int = 3,
    val maxOctave:     Int = 5,
    val midiChannel:   Int = 3,
    val selectedScale: Int = 1,
    val timingMode:    Int = 0,
    val rootNote:      Int = 0,   // 0=follow global; 1-12=C..B
    val proSettings:   ProSettings = ProSettings(),
    val useSharedPro:  Boolean = true
) : Parcelable

// ── Single Note Drone config ──────────────────────────────────────────────────
// droneNote: MIDI note number (0-127). UI lets user pick via chromatic buttons.
@Parcelize
data class DroneConfig(
    val droneNote:   Int = 60,   // middle C
    val midiChannel: Int = 3
) : Parcelable

// ── Evolving Drone config ─────────────────────────────────────────────────────
// holdType: 0=CONSTANT, 1=RANDOM
// holdMs: used when holdType==CONSTANT (ms between note changes)
// holdMinMs / holdMaxMs: range when holdType==RANDOM
@Parcelize
data class EvolvingDroneConfig(
    val velocity:      Int = 90,
    val minOctave:     Int = 3,
    val maxOctave:     Int = 5,
    val midiChannel:   Int = 3,
    val selectedScale: Int = 1,
    val rootNote:      Int = 0,   // 0=follow global; 1-12=C..B
    val holdType:      Int = 0,   // 0=Constant, 1=Random
    val holdMs:        Long = 8_000L,   // constant hold duration (ms)
    val holdMinMs:     Long = 4_000L,   // random hold minimum (ms)
    val holdMaxMs:     Long = 30_000L,  // random hold maximum (ms)
    val useSharedPro:  Boolean = true,
    val proSettings:   ProSettings = ProSettings()  // Markov + Velocity only
) : Parcelable

// ── Master VoiceConfig ────────────────────────────────────────────────────────
@Parcelize
data class VoiceConfig(
    val enabled:             Boolean           = false,
    val style:               VoiceStyle        = VoiceStyle.GENERATIVE,
    // --- Generative sub-mode ---
    val mode:                VoiceMode         = VoiceMode.HARMONY,
    val harmonyConfig:       HarmonyConfig     = HarmonyConfig(),
    val independentConfig:   IndependentConfig = IndependentConfig(),
    // --- Drone configs ---
    val droneConfig:         DroneConfig         = DroneConfig(),
    val evolvingDroneConfig: EvolvingDroneConfig = EvolvingDroneConfig()
) : Parcelable

// ── Diatonic harmony helpers ──────────────────────────────────────────────────
object DiatonicHarmony {
    fun allowedNotes(scaleIntervals: List<Int>, rootNote: Int = 0): IntArray {
        val result = mutableListOf<Int>()
        for (midi in 0..127) {
            if (((midi - rootNote) % 12 + 12) % 12 in scaleIntervals) result.add(midi)
        }
        return result.toIntArray()
    }

    fun indexOf(midiNote: Int, allowedNotes: IntArray): Int {
        val exact = allowedNotes.indexOf(midiNote)
        if (exact >= 0) return exact
        return allowedNotes.indices.minByOrNull { kotlin.math.abs(allowedNotes[it] - midiNote) } ?: 0
    }

    fun applyOffset(
        v1MidiNote:   Int,
        stepOffset:   Int,
        allowedNotes: IntArray,
        wrapAround:   Boolean = false
    ): Int {
        if (allowedNotes.isEmpty()) return v1MidiNote
        val idx       = indexOf(v1MidiNote, allowedNotes)
        val targetIdx = idx + stepOffset
        val clampedIdx = when {
            wrapAround -> ((targetIdx % allowedNotes.size) + allowedNotes.size) % allowedNotes.size
            else       -> targetIdx.coerceIn(0, allowedNotes.lastIndex)
        }
        return allowedNotes[clampedIdx]
    }

    fun applyVelocity(v1Velocity: Int, cfg: HarmonyConfig): Int {
        val scaled = (v1Velocity * (cfg.masterVelocity / 127.0)).toInt()
        val drift  = if (cfg.velocityDrift > 0) Random.nextInt(-cfg.velocityDrift, cfg.velocityDrift + 1) else 0
        return (scaled + drift).coerceIn(1, 127)
    }
}
