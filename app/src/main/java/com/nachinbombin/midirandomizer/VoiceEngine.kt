package com.nachinbombin.midirandomizer

import android.media.midi.MidiInputPort
import android.os.Handler
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Self-contained execution engine for one secondary voice (V2 or V3).
 */
class VoiceEngine(
    private val voiceId:     Int,
    private val mainHandler: Handler,
    private val getInputPort: () -> MidiInputPort?,
    private val getScales:    () -> List<List<Int>>,
    private val getGlobalScale: () -> Int,
    private val getGlobalRoot:  () -> Int,
    private val getV2Note:    () -> Int,
    private val onNotePlayed: (Int) -> Unit,
    private val onNoteOnRaw:  (Int, Int, Int) -> Unit,
    private val onNoteOffRaw: (Int, Int) -> Unit
) {
    companion object { private const val TAG = "VoiceEngine" }

    @Volatile var config: VoiceConfig = VoiceConfig()

    private var scheduler: ExecutorService? = null
    @Volatile private var running = false

    private var currentNote    = -1
    private var currentNoteCh  = 0

    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)

    // Replaced markovChain: MarkovMelody? with a full dispatcher
    private var dispatcher: MelodicEngineDispatcher? = null

    private var euclideanPattern: BooleanArray  = BooleanArray(0)
    private var euclideanStep = 0

    // ── Harmony mode ──────────────────────────────────────────────────────

    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
        val cfg = config
        if (!cfg.enabled || cfg.mode != VoiceMode.HARMONY) return
        val hc = cfg.harmonyConfig

        if (hc.skipProbability > 0f && Random.nextFloat() < hc.skipProbability) return

        val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: return
        val allowed  = DiatonicHarmony.allowedNotes(scaleIntervals, getGlobalRoot())
        val refNote  = if (voiceId == 3 && hc.referenceVoice == 2) getV2Note() else v1Note
        val targetNote = DiatonicHarmony.applyOffset(refNote, hc.toneStepOffset, allowed)
        val vel   = DiatonicHarmony.applyVelocity(v1Velocity, hc)
        val delay = if (hc.timeDriftMs > 0) Random.nextLong(0, hc.timeDriftMs + 1) else 0L

        if (delay <= 0) {
            fireHarmonyNote(targetNote, vel, hc.midiChannel)
        } else {
            mainHandler.postDelayed({
                if (config.mode == VoiceMode.HARMONY)
                    fireHarmonyNote(targetNote, vel, hc.midiChannel)
            }, delay)
        }
    }

    private fun fireHarmonyNote(note: Int, vel: Int, ch: Int) {
        if (currentNote >= 0) onNoteOffRaw(currentNote, currentNoteCh)
        onNoteOnRaw(note, vel, ch)
        onNotePlayed(note)
        currentNote   = note
        currentNoteCh = ch
    }

    fun silenceCurrentNote() {
        val note = currentNote
        val ch   = currentNoteCh
        if (note >= 0) {
            onNoteOffRaw(note, ch)
            currentNote   = -1
            currentNoteCh = 0
        }
    }

    // ── Independent mode ──────────────────────────────────────────────────

    fun startIndependent() {
        val cfg = config
        if (!cfg.enabled || cfg.mode != VoiceMode.INDEPENDENT) return
        if (running) return
        running = true
        rebuildHelpers(cfg.independentConfig)
        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(independentLoop)
    }

    fun stopIndependent() {
        running = false
        val s  = scheduler
        scheduler = null
        s?.shutdownNow()
        silenceCurrentNote()
    }

    fun stop() { stopIndependent() }

    private val independentLoop = Runnable {
        try {
            while (running) {
                val ic = config.independentConfig

                if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
                    fireIndependentNote(ic)
                    try { Thread.sleep(Long.MAX_VALUE) } catch (_: InterruptedException) {}
                    break
                }

                val ps       = ic.proSettings
                val euclidOn = ps.euclideanEnabled && ic.timingMode == MidiService.TIMING_EUCLIDEAN
                val isOnset  = if (euclidOn) {
                    val hit = euclideanPattern.getOrElse(euclideanStep) { false }
                    euclideanStep = (euclideanStep + 1) % euclideanPattern.size.coerceAtLeast(1)
                    hit
                } else true

                if (isOnset) {
                    dispatcher?.advanceBeat()
                    fireIndependentNote(ic)
                }

                try {
                    Thread.sleep(calcInterval(ic).coerceAtLeast(10L))
                } catch (_: InterruptedException) {
                    break
                }
            }
        } finally {
            silenceCurrentNote()
            running = false
        }
    }

    private fun fireIndependentNote(ic: IndependentConfig) {
        val isDrone  = ic.style == VoiceStyle.SINGLE_NOTE_DRONE || ic.style == VoiceStyle.EVOLVING_DRONE
        val prevNote = currentNote
        val prevCh   = currentNoteCh

        if (!isDrone && prevNote >= 0) onNoteOffRaw(prevNote, prevCh)

        val octMin   = ic.droneOctaveMin
        val octMax   = ic.droneOctaveMax
        val octRange = (octMax - octMin + 1).coerceAtLeast(1)

        val noteNumber: Int

        if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
            val selectedOctave = if (octRange == 1) octMin else octMin + Random.nextInt(octRange)
            if (ic.rootNote == 0) {
                val globalRoot     = getGlobalRoot()
                val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: listOf(0)
                val rootInterval   = scaleIntervals[0]
                noteNumber = ((selectedOctave + 1) * 12 + globalRoot + rootInterval).coerceIn(0, 127)
            } else {
                val rootOffset = ic.rootNote - 1
                noteNumber = ((selectedOctave + 1) * 12 + rootOffset).coerceIn(0, 127)
            }
        } else {
            val intervals = getScales().getOrNull(ic.selectedScale) ?: return
            val ps        = ic.proSettings

            // — Dispatch through MelodicEngineDispatcher (replaces direct markovChain call) —
            val degreeIdx = dispatcher?.nextDegree() ?: Random.nextInt(intervals.size)

            // -1 means the density gate suppressed this onset; skip the note
            if (degreeIdx == -1) return

            val interval = intervals[degreeIdx]

            // Gesture register shift adjusts the octave selection window
            val regShift = dispatcher?.gestureRegisterShift() ?: 0
            val shiftedMin = (octMin + regShift).coerceIn(0, 8)
            val shiftedMax = (octMax + regShift).coerceIn(shiftedMin, 9)
            val shiftedRange = (shiftedMax - shiftedMin + 1).coerceAtLeast(1)

            val oct  = shiftedMin + Random.nextInt(shiftedRange)
            val root = if (ic.rootNote > 0) ic.rootNote - 1 else getGlobalRoot()
            noteNumber = ((oct + 1) * 12 + interval + root).coerceIn(0, 127)
        }

        // Gesture velocity scale applied on top of shaper output
        val baseVel = velocityShaper.next()
        val gScale  = dispatcher?.gestureVelocityScale() ?: 1f
        val vel     = (baseVel * gScale).toInt().coerceIn(1, 127)

        onNoteOnRaw(noteNumber, vel, ic.midiChannel)
        onNotePlayed(noteNumber)
        currentNote   = noteNumber
        currentNoteCh = ic.midiChannel

        if (isDrone && prevNote >= 0 && prevNote != noteNumber) {
            onNoteOffRaw(prevNote, prevCh)
        }
    }

    private fun calcInterval(ic: IndependentConfig): Long {
        if (ic.style == VoiceStyle.EVOLVING_DRONE) {
            val beats = if (ic.droneTiming == DroneTimingMode.RANDOM) {
                val min = ic.droneMinBeats.coerceIn(1, 256)
                val max = ic.droneMaxBeats.coerceIn(min, 256)
                Random.nextInt(min, max + 1)
            } else 32
            return (60_000L / ic.bpm.coerceAtLeast(1)) * beats
        }
        val base   = (60_000.0 / ic.bpm.coerceAtLeast(1)).toLong()
        val ps     = ic.proSettings
        val modeMs = when (ic.timingMode) {
            MidiService.TIMING_METRONOME  -> base
            MidiService.TIMING_MIXED      -> if (Random.nextFloat() < 0.3f) base / 2 else base
            MidiService.TIMING_RANDOMIZED -> (base * (0.5 + Random.nextDouble())).toLong()
            MidiService.TIMING_EUCLIDEAN  -> base
            else -> base
        }
        return JitterEngine.applyJitter(modeMs, ps.jitterAmount, ps.jitterType)
    }

    private fun rebuildHelpers(ic: IndependentConfig) {
        val ps   = ic.proSettings
        val intervals = getScales().getOrNull(ic.selectedScale) ?: listOf(0, 2, 4, 5, 7, 9, 11)

        velocityShaper = VelocityShaper(ps.velocityPattern, ic.velocity).also { it.reset() }

        // Build dispatcher for any engine (NAIVE, MARKOV, PWG, L_SYSTEM, CELL_AUTOMATA, NRT_MELODIC)
        // NAIVE mode: dispatcher.nextDegree() returns Random.nextInt(scaleSize) — same as before
        dispatcher = MelodicEngineDispatcher(intervals, ps).also { /* already reset internally */ }

        if (ps.euclideanEnabled) {
            euclideanPattern = EuclideanRhythm.generate(
                ps.euclideanSteps.coerceIn(2, 32),
                ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                ps.euclideanRotation
            )
            euclideanStep = 0
        }
    }
}
