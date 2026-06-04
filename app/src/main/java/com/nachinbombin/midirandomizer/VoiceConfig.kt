package com.nachinbombin.midirandomizer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.random.Random

@Parcelize
enum class VoiceMode : Parcelable { HARMONY, INDEPENDENT }

@Parcelize
enum class VoiceStyle : Parcelable { GENERATIVE, SINGLE_NOTE_DRONE, EVOLVING_DRONE }

@Parcelize
enum class DroneTimingMode : Parcelable { CONSTANT, RANDOM }

@Parcelize
data class HarmonyConfig(
    val toneStepOffset:  Int   = 2,
    val timeDriftMs:     Long  = 10,
    val skipProbability: Float = 0f,
    val masterVelocity:  Int   = 100,
    val velocityDrift:   Int   = 8,
    val midiChannel:     Int   = 0,
    val referenceVoice:  Int   = 1
) : Parcelable

@Parcelize
data class IndependentConfig(
    val bpm:              Int              = 120,
    val velocity:         Int              = 90,
    val minOctave:        Int              = 3,
    val maxOctave:        Int              = 5,
    val midiChannel:      Int              = 0,
    val selectedScale:    Int              = 1,
    val timingMode:       Int              = 0,
    val rootNote:         Int              = 0,
    // ProSettings is a data class with many nested configs; @RawValue tells
    // Parcelize to serialise it via writeValue() / Serializable fallback.
    val proSettings:      @RawValue ProSettings = ProSettings(),
    val useSharedPro:     Boolean          = true,
    val style:            VoiceStyle       = VoiceStyle.GENERATIVE,
    val droneTiming:      DroneTimingMode  = DroneTimingMode.CONSTANT,
    val droneMinBeats:    Int              = 16,
    val droneMaxBeats:    Int              = 64,
    val droneOctaveMin:   Int              = 3,
    val droneOctaveMax:   Int              = 5
) : Parcelable

@Parcelize
data class VoiceConfig(
    val enabled:           Boolean           = false,
    val mode:              VoiceMode         = VoiceMode.HARMONY,
    val harmonyConfig:     HarmonyConfig     = HarmonyConfig(),
    val independentConfig: IndependentConfig = IndependentConfig()
) : Parcelable

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
