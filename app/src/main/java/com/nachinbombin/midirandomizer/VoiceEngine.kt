package com.nachinbombin.midirandomizer

import android.media.midi.MidiInputPort
import android.os.Handler
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Self-contained execution engine for one secondary voice (V2 or V3).
 *
 * Modes:
 *   HARMONY     – follows V1 with diatonic offset + drift
 *   INDEPENDENT – own generative loop (GENERATIVE / DRONE / CHORDS)
 *   MELODIC     – same loop as INDEPENDENT but note pool is filtered through
 *                 DiatonicHarmony.applyMelodicRelation() when contrastEnabled
 *
 * For MELODIC mode the engine needs two live read-backs from MidiService:
 *   getV1ChordNotes() – most recently emitted V1 chord (empty list if V1 is not
 *                        in CHORDS style)
 *   getV1LastNote()   – most recently emitted V1 single note (for COUNTER_MOTION
 *                        direction heuristic)
 *
 * Thread-safety guarantee (V9):
 *   All mutable engine state (currentNote, currentNoteCh, running, scheduler,
 *   lastChordNotes) is accessed only under `engineLock`. Public entry points
 *   (startIndependent, stopIndependent, silenceCurrentNote, onV1NoteOn) all
 *   acquire this lock before touching shared state, making it safe to call them
 *   from any thread (main thread via MidiService binder, or note-loop thread).
 */
class VoiceEngine(
    private val voiceId:        Int,
    private val mainHandler:    Handler,
    private val getInputPort:   () -> MidiInputPort?,
    private val getScales:      () -> List<List<Int>>,
    private val getGlobalScale: () -> Int,
    private val getGlobalRoot:  () -> Int,
    private val getV2Note:      () -> Int,
    private val onNotePlayed:   (Int) -> Unit,
    private val onNoteOnRaw:    (Int, Int, Int) -> Unit,
    private val onNoteOffRaw:   (Int, Int) -> Unit,
    private val getV1ChordNotes: () -> List<Int> = { emptyList() },
    private val getV1LastNote:   () -> Int        = { 60 }
) {
    companion object { private const val TAG = "VoiceEngine" }

    @Volatile var config: VoiceConfig = VoiceConfig()

    // Single lock that guards ALL mutable engine state accessed from multiple threads.
    private val engineLock = Any()

    // Protected by engineLock:
    private var scheduler: ExecutorService? = null
    private var running = false
    private var currentNote   = -1
    private var currentNoteCh = 0
    private var lastChordNotes: List<Int> = emptyList()

    // Only used inside the independentLoop (single thread), no lock needed:
    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    private var dispatcher:     MelodicEngineDispatcher? = null
    private var euclideanPattern: BooleanArray = BooleanArray(0)
    private var euclideanStep = 0

    // ── Harmony mode ──────────────────────────────────────────

    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
        // Guard: never process invalid note values
        if (v1Note < 0 || v1Note > 127) return

        val cfg = config
        if (!cfg.enabled || cfg.mode != VoiceMode.HARMONY) return
        val hc = cfg.harmonyConfig

        if (hc.skipProbability > 0f && Random.nextFloat() < hc.skipProbability) return

        val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: return
        val allowed    = DiatonicHarmony.allowedNotes(scaleIntervals, getGlobalRoot())
        val refNote    = if (voiceId == 3 && hc.referenceVoice == 2) getV2Note() else v1Note
        val targetNote = DiatonicHarmony.applyOffset(refNote, hc.toneStepOffset, allowed)
        val vel        = DiatonicHarmony.applyVelocity(v1Velocity, hc)
        val delay      = if (hc.timeDriftMs > 0) Random.nextLong(0, hc.timeDriftMs + 1) else 0L

        if (delay <= 0L) {
            fireHarmonyNote(targetNote, vel, hc.midiChannel)
        } else {
            mainHandler.postDelayed({
                if (config.mode == VoiceMode.HARMONY)
                    fireHarmonyNote(targetNote, vel, hc.midiChannel)
            }, delay)
        }
    }

    private fun fireHarmonyNote(note: Int, vel: Int, ch: Int) {
        synchronized(engineLock) {
            val prev   = currentNote
            val prevCh = currentNoteCh
            if (prev >= 0) onNoteOffRaw(prev, prevCh)
            onNoteOnRaw(note, vel, ch)
            onNotePlayed(note)
            currentNote   = note
            currentNoteCh = ch
        }
    }

    /**
     * Silence the currently held note, if any.
     * Safe to call from any thread.
     */
    fun silenceCurrentNote() {
        val note: Int
        val ch: Int
        synchronized(engineLock) {
            note = currentNote
            ch   = currentNoteCh
            currentNote   = -1
            currentNoteCh = 0
        }
        // Send MIDI outside the lock to avoid holding it during I/O
        if (note >= 0) onNoteOffRaw(note, ch)
    }

    // ── Independent / Melodic mode ────────────────────────────────────

    /**
     * Start the independent note loop.
     * Idempotent — if already running, performs a clean stop-then-start.
     * Safe to call from any thread.
     */
    fun startIndependent() {
        val cfg = config
        if (!cfg.enabled) return
        if (cfg.mode != VoiceMode.INDEPENDENT && cfg.mode != VoiceMode.MELODIC) return

        // Stop any existing loop first (idempotent, clears running flag)
        stopIndependent()

        synchronized(engineLock) {
            // Rebuild helpers AFTER stop so they read fresh config
            rebuildHelpers()
            running   = true
            scheduler = Executors.newSingleThreadExecutor()
            scheduler?.execute(independentLoop)
        }
    }

    /**
     * Stop the independent loop and silence the held note.
     * Safe to call from any thread, including from within the loop.
     */
    fun stopIndependent() {
        val oldScheduler: ExecutorService?
        synchronized(engineLock) {
            running      = false
            oldScheduler = scheduler
            scheduler    = null
        }
        oldScheduler?.shutdownNow()
        silenceCurrentNote()
    }

    fun stop() { stopIndependent() }

    private val independentLoop = Runnable {
        try {
            while (true) {
                // Snapshot running under lock
                val shouldRun = synchronized(engineLock) { running }
                if (!shouldRun) break

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
        } catch (e: Exception) {
            Log.e(TAG, "independentLoop[$voiceId] crashed", e)
        } finally {
            synchronized(engineLock) { running = false }
            silenceCurrentNote()
        }
    }

    /**
     * Core note-fire for INDEPENDENT and MELODIC modes.
     * Called only from independentLoop (single thread), but note-off calls
     * go through onNoteOffRaw which may touch shared activeNotes — those are
     * ConcurrentHashMap so individual ops are thread-safe.
     * currentNote is updated inside engineLock.
     */
    private fun fireIndependentNote(ic: IndependentConfig) {
        val isDrone  = ic.style == VoiceStyle.SINGLE_NOTE_DRONE || ic.style == VoiceStyle.EVOLVING_DRONE
        val isChords = ic.style == VoiceStyle.CHORDS

        // Snapshot currentNote/currentNoteCh under lock
        val prevNote: Int
        val prevCh: Int
        synchronized(engineLock) {
            prevNote = currentNote
            prevCh   = currentNoteCh
        }

        if (!isDrone && !isChords && prevNote >= 0) onNoteOffRaw(prevNote, prevCh)

        if (isChords) {
            fireChordNotes(ic)
            return
        }

        val octMin   = ic.droneOctaveMin
        val octMax   = ic.droneOctaveMax
        val octRange = (octMax - octMin + 1).coerceAtLeast(1)

        val noteNumber: Int

        if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
            val selectedOctave = if (octRange == 1) octMin else octMin + Random.nextInt(octRange)
            noteNumber = if (ic.rootNote == 0) {
                val globalRoot     = getGlobalRoot()
                val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: listOf(0)
                ((selectedOctave + 1) * 12 + globalRoot + scaleIntervals[0]).coerceIn(0, 127)
            } else {
                ((selectedOctave + 1) * 12 + (ic.rootNote - 1)).coerceIn(0, 127)
            }
        } else {
            val intervals = getScales().getOrNull(ic.selectedScale) ?: return
            val ps        = ic.proSettings

            val rawDegreeIdx = dispatcher?.nextDegree() ?: Random.nextInt(intervals.size)
            if (rawDegreeIdx == -1) return   // density gate suppressed this onset

            val interval = intervals[rawDegreeIdx]

            val regShift     = dispatcher?.gestureRegisterShift() ?: 0
            val shiftedMin   = (octMin + regShift).coerceIn(0, 8)
            val shiftedMax   = (octMax + regShift).coerceIn(shiftedMin, 9)
            val shiftedRange = (shiftedMax - shiftedMin + 1).coerceAtLeast(1)

            val oct  = shiftedMin + Random.nextInt(shiftedRange)
            val root = if (ic.rootNote > 0) ic.rootNote - 1 else getGlobalRoot()
            var candidate = ((oct + 1) * 12 + interval + root).coerceIn(0, 127)

            // MELODIC contrast filter
            val mrc = config.melodicRelationConfig
            if (config.mode == VoiceMode.MELODIC && mrc.enabled) {
                val voiceScale = getScales().getOrNull(ic.selectedScale) ?: intervals
                val voiceRoot  = if (ic.rootNote > 0) ic.rootNote - 1 else getGlobalRoot()
                val poolList   = mutableListOf<Int>()
                for (o in shiftedMin..shiftedMax) {
                    for (iv in voiceScale) {
                        poolList.add(((o + 1) * 12 + iv + voiceRoot).coerceIn(0, 127))
                    }
                }
                val filteredPool = DiatonicHarmony.applyMelodicRelation(
                    candidateNotes = poolList.toIntArray(),
                    v1ChordNotes   = getV1ChordNotes(),
                    v1LastNote     = getV1LastNote(),
                    relationCfg    = mrc
                )
                if (filteredPool.isNotEmpty()) {
                    candidate = filteredPool[Random.nextInt(filteredPool.size)]
                }
            }

            noteNumber = candidate
        }

        val baseVel = velocityShaper.next()
        val gScale  = dispatcher?.gestureVelocityScale() ?: 1f
        val vel     = (baseVel * gScale).toInt().coerceIn(1, 127)

        onNoteOnRaw(noteNumber, vel, ic.midiChannel)
        onNotePlayed(noteNumber)

        synchronized(engineLock) {
            currentNote   = noteNumber
            currentNoteCh = ic.midiChannel
        }

        if (isDrone && prevNote >= 0 && prevNote != noteNumber) {
            onNoteOffRaw(prevNote, prevCh)
        }
    }

    /**
     * Chord emission for V2/V3 CHORDS style.
     */
    private fun fireChordNotes(ic: IndependentConfig) {
        val cc = ic.chordConfig
        val scaleIntervals = getScales().getOrNull(ic.selectedScale) ?: return
        val root     = if (ic.rootNote > 0) ic.rootNote - 1 else getGlobalRoot()

        val octMin   = ic.minOctave
        val octMax   = ic.maxOctave.coerceAtLeast(octMin)
        val oct      = octMin + Random.nextInt(octMax - octMin + 1)
        val rootMidi = ((oct + 1) * 12 + scaleIntervals[Random.nextInt(scaleIntervals.size)] + root)
            .coerceIn(0, 127)

        val prevLastChord: List<Int>
        synchronized(engineLock) { prevLastChord = lastChordNotes }

        var chordNotes = DiatonicHarmony.buildChordNotes(rootMidi, scaleIntervals, root + 1, cc)
        chordNotes     = DiatonicHarmony.applyInversion(chordNotes, cc.inversionMode, prevLastChord)

        synchronized(engineLock) { lastChordNotes = chordNotes }

        // Silence previous note
        val prevNote: Int
        val prevCh: Int
        synchronized(engineLock) {
            prevNote = currentNote
            prevCh   = currentNoteCh
        }
        if (prevNote >= 0) onNoteOffRaw(prevNote, prevCh)

        val baseVel = velocityShaper.next()
        val gScale  = dispatcher?.gestureVelocityScale() ?: 1f
        val vel     = (baseVel * gScale).toInt().coerceIn(1, 127)
        val ch      = ic.midiChannel

        val orderedNotes = when (cc.pluckingStyle) {
            1    -> chordNotes.sorted()
            2    -> chordNotes.sortedDescending()
            3    -> chordNotes.shuffled()
            4    -> chordNotes.sorted()
            else -> chordNotes
        }

        val delayMs   = cc.pluckingDelayMs
        val strumSize = cc.strumLength.coerceIn(1, orderedNotes.size)

        if (cc.pluckingStyle == 0 || delayMs <= 0L) {
            orderedNotes.forEach { note -> onNoteOnRaw(note, vel, ch) }
        } else {
            orderedNotes.take(strumSize).forEachIndexed { idx, note ->
                if (idx == 0) {
                    onNoteOnRaw(note, vel, ch)
                } else {
                    mainHandler.postDelayed({ onNoteOnRaw(note, vel, ch) }, delayMs * idx)
                }
            }
            if (orderedNotes.size > strumSize) {
                val lastDelay = delayMs * strumSize
                orderedNotes.drop(strumSize).forEach { note ->
                    mainHandler.postDelayed({ onNoteOnRaw(note, vel, ch) }, lastDelay)
                }
            }
        }

        val firstNote = orderedNotes.firstOrNull() ?: rootMidi
        synchronized(engineLock) {
            currentNote   = firstNote
            currentNoteCh = ch
        }
        onNotePlayed(firstNote)
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
        val base = (60_000.0 / ic.bpm.coerceAtLeast(1)).toLong()
        val ps   = ic.proSettings
        val modeMs = when (ic.timingMode) {
            MidiService.TIMING_METRONOME  -> base
            MidiService.TIMING_MIXED      -> if (Random.nextFloat() < 0.3f) base / 2 else base
            MidiService.TIMING_RANDOMIZED -> (base * (0.5 + Random.nextDouble())).toLong()
            MidiService.TIMING_EUCLIDEAN  -> base
            else -> base
        }
        return JitterEngine.applyJitter(modeMs, ps.jitterAmount, ps.jitterType)
    }

    /**
     * Rebuild velocity shaper, melodic dispatcher, and euclidean pattern.
     * Must be called with engineLock held, or before the loop thread starts.
     */
    private fun rebuildHelpers() {
        val ic = config.independentConfig
        val ps = ic.proSettings
        val intervals = getScales().getOrNull(ic.selectedScale) ?: listOf(0, 2, 4, 5, 7, 9, 11)

        velocityShaper   = VelocityShaper(ps.velocityPattern, ic.velocity).also { it.reset() }
        dispatcher       = MelodicEngineDispatcher(intervals, ps)
        euclideanPattern = BooleanArray(0)
        euclideanStep    = 0

        if (ps.euclideanEnabled) {
            euclideanPattern = EuclideanRhythm.generate(
                ps.euclideanSteps.coerceIn(2, 32),
                ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                ps.euclideanRotation
            )
        }
    }
}
