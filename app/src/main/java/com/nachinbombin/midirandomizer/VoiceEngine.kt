package com.nachinbombin.midirandomizer

import android.media.midi.MidiInputPort
import android.os.Handler
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

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

    // Track the last V1 note/velocity so harmony can be re-fired when params change
    @Volatile var lastV1Note: Int = -1
    @Volatile var lastV1Vel:  Int = 100

    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    private var markovChain:    MarkovMelody?   = null
    private var euclideanPattern: BooleanArray  = BooleanArray(0)
    private var euclideanStep = 0

    // ── Harmony mode ───────────────────────────────────────────────────────

    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
        lastV1Note = v1Note
        lastV1Vel  = v1Velocity
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

    /**
     * Called when tone offset (or other harmony params) change while a V1 note
     * is already held. Immediately replaces the current harmony note with the
     * new offset applied to the last known V1 note.
     */
    fun onToneOffsetChanged() {
        val cfg = config
        if (!cfg.enabled || cfg.mode != VoiceMode.HARMONY) return
        val v1Note = lastV1Note
        if (v1Note < 0) return        // nothing ever played in V1
        if (currentNote < 0) return   // nothing sounding in this voice
        val hc             = cfg.harmonyConfig
        val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: return
        val allowed        = DiatonicHarmony.allowedNotes(scaleIntervals, getGlobalRoot())
        val refNote        = if (voiceId == 3 && hc.referenceVoice == 2) getV2Note() else v1Note
        val targetNote     = DiatonicHarmony.applyOffset(refNote, hc.toneStepOffset, allowed)
        val vel            = DiatonicHarmony.applyVelocity(lastV1Vel, hc)
        fireHarmonyNote(targetNote, vel, hc.midiChannel)
    }

    /**
     * Start harmonising immediately using the currently held V1 note.
     * Called when this voice switches to HARMONY mode mid-session.
     */
    fun harmonizeCurrentV1Note() {
        val v1Note = lastV1Note
        if (v1Note >= 0) onV1NoteOn(v1Note, lastV1Vel)
    }

    private fun fireHarmonyNote(note: Int, vel: Int, ch: Int) {
        if (currentNote >= 0) onNoteOffRaw(currentNote, currentNoteCh)
        onNoteOnRaw(note, vel, ch)
        onNotePlayed(note)
        currentNote   = note
        currentNoteCh = ch
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
        val note = currentNote
        val ch   = currentNoteCh
        if (note >= 0) {
            onNoteOffRaw(note, ch)
            currentNote   = -1
            currentNoteCh = 0
        }
    }

    fun stop() {
        stopIndependent()
    }

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

                if (isOnset) fireIndependentNote(ic)

                try {
                    Thread.sleep(calcInterval(ic).coerceAtLeast(10L))
                } catch (_: InterruptedException) {
                    break
                }
            }
        } finally {
            val note = currentNote
            val ch   = currentNoteCh
            if (note >= 0) {
                onNoteOffRaw(note, ch)
                currentNote   = -1
                currentNoteCh = 0
            }
            running = false
        }
    }

    private fun fireIndependentNote(ic: IndependentConfig) {
        val isDrone  = ic.style == VoiceStyle.SINGLE_NOTE_DRONE || ic.style == VoiceStyle.EVOLVING_DRONE
        val prevNote = currentNote
        val prevCh   = currentNoteCh

        if (!isDrone && prevNote >= 0) onNoteOffRaw(prevNote, prevCh)

        val octMin = ic.droneOctaveMin
        val octMax = ic.droneOctaveMax
        val octRange = (octMax - octMin + 1).coerceAtLeast(1)

        val noteNumber: Int

        if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
            val selectedOctave = if (octRange == 1) octMin
                                 else octMin + Random.nextInt(octRange)
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
            val degreeIdx = if (ps.markovEnabled) {
                markovChain?.nextDegree() ?: Random.nextInt(intervals.size)
            } else Random.nextInt(intervals.size)
            val interval = intervals[degreeIdx]
            val oct      = octMin + Random.nextInt(octRange)
            val root     = if (ic.rootNote > 0) ic.rootNote - 1 else getGlobalRoot()
            noteNumber   = ((oct + 1) * 12 + interval + root).coerceIn(0, 127)
        }

        val vel = velocityShaper.next()
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
        val scSz = getScales().getOrNull(ic.selectedScale)?.size ?: 7
        velocityShaper = VelocityShaper(ps.velocityPattern, ic.velocity).also { it.reset() }
        markovChain    = if (ps.markovEnabled)
            MarkovMelody(scSz, ps.melodicLogicStyle).also { it.reset() } else null
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
