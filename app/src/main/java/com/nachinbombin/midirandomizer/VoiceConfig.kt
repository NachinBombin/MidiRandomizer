package com.nachinbombin.midirandomizer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlin.random.Random

@Parcelize
enum class VoiceMode : Parcelable { HARMONY, INDEPENDENT, MELODIC }

@Parcelize
enum class VoiceStyle : Parcelable { GENERATIVE, SINGLE_NOTE_DRONE, EVOLVING_DRONE, CHORDS }

@Parcelize
enum class DroneTimingMode : Parcelable { CONSTANT, RANDOM }

// ── Chord mode enums ────────────────────────────────────────────────────────

@Parcelize
enum class ChordBuildStrategy : Parcelable { DIATONIC_STACK, MODAL_SNAP }

@Parcelize
enum class InversionMode : Parcelable { ROOT, FIRST, SECOND, AUTO }

@Parcelize
enum class VoicingDensity : Parcelable { FULL, DROP5, SHELL, DROP_ROOT }

/** Maximum harmonic extension allowed when building a chord. */
@Parcelize
enum class TensionLevel : Parcelable { TRIAD, SEVENTH, NINTH, ELEVENTH_THIRTEENTH }

@Parcelize
enum class RhythmicFigure : Parcelable { SUSTAINED, REATTACK, BROKEN, OSTINATO }

/**
 * All chord-exclusive parameters.  Defaults reproduce naïve strum behaviour so
 * the instrument sounds exactly as before unless the performer turns a knob.
 */
@Parcelize
data class ChordConfig(
    // ── Basic ──────────────────────────────────────────────────────────────
    val chordType:           Int              = 0,   // 0=Triad 1=7th 2=9th 3=Sus2 4=Sus4 5=Power
    val pluckingStyle:       Int              = 0,   // 0=Simultaneous 1=Asc 2=Desc 3=Random 4=PercussiveUp
    val pluckingDelayMs:     Long             = 30,
    val chordSpread:         Int              = 1,   // voicing spread multiplier
    val noteDropChance:      Float            = 0f,  // 0..1 probability to omit one non-root note
    val chordRhythmPattern:  Int              = 0,   // 0=All beats 1=Accents only 2=Syncopated
    val strumLength:         Int              = 2,   // number of notes staggered in one strum (1..chord size)
    // ── Theory-driven (all default to naïve) ─────────────────────────────
    val chordBuildStrategy:  ChordBuildStrategy = ChordBuildStrategy.DIATONIC_STACK,
    val inversionMode:       InversionMode    = InversionMode.ROOT,
    val voicingDensity:      VoicingDensity   = VoicingDensity.FULL,
    val tensionLevel:        TensionLevel     = TensionLevel.TRIAD,
    val mutationChance:      Float            = 0f,  // 0..0.30 probability to shift one non-root tone ±1 scale degree
    val rhythmicFigure:      RhythmicFigure   = RhythmicFigure.SUSTAINED
) : Parcelable

// ── Melodic relation mode (V2/V3 MELODIC mode only) ────────────────────────

/**
 * How a MELODIC voice should relate to V1 when V1 is playing CHORDS.
 * - COUNTER_MOTION       : move in opposite direction to V1 melodic motion
 * - RHYTHMIC_COMPLEMENT  : fill rhythmic gaps left by V1
 * - REGISTER_CONTRAST    : stay in a register well above or below V1
 * - CHORD_AWARE          : select pitches from current V1 chord tones (requires V1 CHORDS)
 */
@Parcelize
enum class MelodicRelationMode : Parcelable {
    COUNTER_MOTION, RHYTHMIC_COMPLEMENT, REGISTER_CONTRAST, CHORD_AWARE
}

/** Parameters governing how a V2/V3 MELODIC voice relates to V1. */
@Parcelize
data class MelodicRelationConfig(
    val enabled:       Boolean              = false,
    val contrastDepth: Int                  = 50,   // 0-100, how strongly the constraint is applied
    val mode:          MelodicRelationMode  = MelodicRelationMode.COUNTER_MOTION
) : Parcelable

// ── Harmony / V2-V3 configs ─────────────────────────────────────────────────

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
    val proSettings:      @RawValue ProSettings = ProSettings(),
    val useSharedPro:     Boolean          = true,
    val style:            VoiceStyle       = VoiceStyle.GENERATIVE,
    val droneTiming:      DroneTimingMode  = DroneTimingMode.CONSTANT,
    val droneMinBeats:    Int              = 16,
    val droneMaxBeats:    Int              = 64,
    val droneOctaveMin:   Int              = 3,
    val droneOctaveMax:   Int              = 5,
    // Full chord config for V2/V3 CHORDS style
    val chordConfig:      ChordConfig      = ChordConfig()
) : Parcelable

@Parcelize
data class VoiceConfig(
    val enabled:              Boolean              = false,
    val mode:                 VoiceMode            = VoiceMode.HARMONY,
    val harmonyConfig:        HarmonyConfig        = HarmonyConfig(),
    val independentConfig:    IndependentConfig    = IndependentConfig(),
    val melodicRelationConfig: MelodicRelationConfig = MelodicRelationConfig()
) : Parcelable

// ── Diatonic helpers ────────────────────────────────────────────────────────

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
        val idx        = indexOf(v1MidiNote, allowedNotes)
        val targetIdx  = idx + stepOffset
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

    /**
     * Build a list of MIDI note numbers for a chord rooted at [rootMidi].
     * Strategy DIATONIC_STACK stacks scale degrees by thirds (guaranteed in-scale).
     * Strategy MODAL_SNAP uses textbook intervals then snaps off-scale notes.
     */
    fun buildChordNotes(
        rootMidi:    Int,
        scaleIntervals: List<Int>,
        rootNote:    Int,       // global root offset (0 = free)
        cfg:         ChordConfig
    ): List<Int> {
        val allowed  = allowedNotes(scaleIntervals, if (rootNote > 0) rootNote - 1 else 0)
        if (allowed.isEmpty()) return listOf(rootMidi)

        val targetTones = when (cfg.tensionLevel) {
            TensionLevel.TRIAD                -> 3
            TensionLevel.SEVENTH              -> 4
            TensionLevel.NINTH                -> 5
            TensionLevel.ELEVENTH_THIRTEENTH  -> 6
        }.let { t ->
            when (cfg.chordType) {
                3, 4 -> t.coerceAtMost(3)
                5    -> 2
                else -> t
            }
        }

        val rootIdx = indexOf(rootMidi, allowed)
        val notes   = mutableListOf<Int>()

        if (cfg.chordBuildStrategy == ChordBuildStrategy.DIATONIC_STACK) {
            var stepIdx = rootIdx
            repeat(targetTones) {
                notes.add(allowed.getOrElse(stepIdx.coerceIn(0, allowed.lastIndex)) { rootMidi })
                stepIdx += 2
            }
        } else {
            val intervals = when (cfg.chordType) {
                1 -> listOf(0, 4, 7, 10)
                2 -> listOf(0, 4, 7, 10, 14)
                3 -> listOf(0, 2, 7)
                4 -> listOf(0, 5, 7)
                5 -> listOf(0, 7)
                else -> listOf(0, 4, 7)
            }.take(targetTones)
            for (ivl in intervals) {
                val raw     = rootMidi + ivl
                val snapped = allowed.getOrElse(indexOf(raw, allowed)) { raw }
                notes.add(snapped)
            }
        }

        val reduced = when (cfg.voicingDensity) {
            VoicingDensity.DROP5     -> notes.filterIndexed { i, _ -> i != 2 }
            VoicingDensity.DROP_ROOT -> if (notes.size >= 2) notes.drop(1) else notes
            VoicingDensity.SHELL     -> {
                listOfNotNull(notes.getOrNull(0), notes.getOrNull(1), notes.getOrNull(3))
                    .ifEmpty { notes }
            }
            VoicingDensity.FULL -> notes
        }.ifEmpty { listOf(rootMidi) }

        val withDrop = if (cfg.noteDropChance > 0f && reduced.size > 1 && Random.nextFloat() < cfg.noteDropChance) {
            val dropIdx = Random.nextInt(1, reduced.size)
            reduced.filterIndexed { i, _ -> i != dropIdx }
        } else reduced

        val mutated = if (cfg.mutationChance > 0f && withDrop.size > 1 && Random.nextFloat() < cfg.mutationChance) {
            val mutIdx = Random.nextInt(1, withDrop.size)
            val noteToMutate = withDrop[mutIdx]
            val noteIdx = indexOf(noteToMutate, allowed)
            val shift = if (Random.nextBoolean()) 1 else -1
            val mut = allowed.getOrElse((noteIdx + shift).coerceIn(0, allowed.lastIndex)) { noteToMutate }
            withDrop.toMutableList().also { it[mutIdx] = mut }
        } else withDrop

        return mutated.map { it.coerceIn(0, 127) }
    }

    /**
     * Apply inversion.  AUTO picks the candidate (root/1st/2nd) with the lowest
     * total MIDI distance from [prevChordNotes].
     */
    fun applyInversion(
        notes:          List<Int>,
        mode:           InversionMode,
        prevChordNotes: List<Int>
    ): List<Int> {
        if (notes.size < 2) return notes
        val root  = notes
        val inv1  = notes.drop(1) + listOf(notes[0] + 12)
        val inv2  = if (notes.size >= 3) notes.drop(2) + notes.take(2).map { it + 12 } else inv1
        return when (mode) {
            InversionMode.ROOT   -> root
            InversionMode.FIRST  -> inv1
            InversionMode.SECOND -> inv2
            InversionMode.AUTO   -> {
                if (prevChordNotes.isEmpty()) return root
                listOf(root, inv1, inv2).minByOrNull { candidate ->
                    candidate.zip(prevChordNotes).sumOf { (a, b) -> kotlin.math.abs(a - b) }.toDouble()
                } ?: root
            }
        }
    }

    /**
     * Given the current V1 chord notes and a MELODIC voice's relation config,
     * filter or re-weight the [candidateNotes] pool so the melodic voice
     * respects the requested contrast mode.
     *
     * Returns a non-empty subset of [candidateNotes] (or the full pool if
     * the config is disabled or the pool is too small to filter).
     */
    fun applyMelodicRelation(
        candidateNotes:    IntArray,
        v1ChordNotes:      List<Int>,
        v1LastNote:        Int,
        relationCfg:       MelodicRelationConfig
    ): IntArray {
        if (!relationCfg.enabled || candidateNotes.size < 3 || v1ChordNotes.isEmpty()) {
            return candidateNotes
        }
        val depth = relationCfg.contrastDepth / 100f   // 0..1
        val filtered: List<Int> = when (relationCfg.mode) {

            MelodicRelationMode.COUNTER_MOTION -> {
                // Prefer notes moving away from v1LastNote direction
                // Simple heuristic: if last V1 note is in upper half, prefer lower candidates
                val v1Mid = v1ChordNotes.average()
                val prefer = if (v1Mid > 60) candidateNotes.filter { it < 60 }
                             else            candidateNotes.filter { it >= 60 }
                prefer.ifEmpty { candidateNotes.toList() }
            }

            MelodicRelationMode.RHYTHMIC_COMPLEMENT -> {
                // No pitch filtering for rhythmic complement — timing handled by VoiceEngine;
                // return full pool so the engine can pick freely
                candidateNotes.toList()
            }

            MelodicRelationMode.REGISTER_CONTRAST -> {
                val v1Max = v1ChordNotes.maxOrNull() ?: 60
                val v1Min = v1ChordNotes.minOrNull() ?: 60
                // Prefer notes at least a fifth away from v1 range
                val above = candidateNotes.filter { it > v1Max + 5 }
                val below = candidateNotes.filter { it < v1Min - 5 }
                val pool  = (above + below).distinct()
                pool.ifEmpty { candidateNotes.toList() }
            }

            MelodicRelationMode.CHORD_AWARE -> {
                // Prefer chord tones when depth is high; mix in passing tones when low
                val chordSet = v1ChordNotes.map { it % 12 }.toSet()
                val chordTones = candidateNotes.filter { (it % 12) in chordSet }
                val passing    = candidateNotes.filter { (it % 12) !in chordSet }
                // Blend: at depth=1 use only chord tones; at depth=0 full pool
                val blendSize  = (chordTones.size + (passing.size * (1f - depth)).toInt()).coerceAtLeast(1)
                (chordTones + passing.take((passing.size * (1f - depth)).toInt()))
                    .take(blendSize)
                    .ifEmpty { candidateNotes.toList() }
            }
        }
        return filtered.toIntArray()
    }
}
