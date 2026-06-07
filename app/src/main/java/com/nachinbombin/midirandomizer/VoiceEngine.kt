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
    private val getV1LastNote:   () -> Int        = { 60 },
    private val getContext:      (Int) -> List<MidiService.MelodicEvent> = { emptyList() },
    private val getFuture:       (Int) -> List<MidiService.MelodicEvent> = { emptyList() }
) {
    companion object { private const val TAG = "VoiceEngine" }

    @Volatile var config: VoiceConfig = VoiceConfig()

    private var scheduler: ExecutorService? = null
    @Volatile private var running = false

    @Volatile private var currentNote   = -1
    @Volatile private var currentNoteCh = 0

    // Tracks previous chord for voice-leading AUTO inversion
    @Volatile private var lastChordNotes: List<Int> = emptyList()

    @Volatile private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    @Volatile private var dispatcher:     MelodicEngineDispatcher? = null

    @Volatile private var euclideanPattern: BooleanArray = BooleanArray(0)
    private var euclideanStep = 0

    // ── Harmony mode ────────────────────────────────────────────────

    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
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
            try {
                onNoteOffRaw(note, ch)
            } catch (e: Exception) {
                Log.e(TAG, "Error in silenceCurrentNote: ${e.message}")
            }
            currentNote   = -1
            currentNoteCh = 0
        }
    }

    // ── Independent / Melodic mode ─────────────────────────────────────

    fun startIndependent() {
        val cfg = config
        if (!cfg.enabled) return
        if (cfg.mode != VoiceMode.INDEPENDENT && cfg.mode != VoiceMode.MELODIC) return
        if (running) return
        running = true
        rebuildHelpers()
        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(independentLoop)
    }

    fun stopIndependent() {
        running = false
        val s = scheduler
        scheduler = null
        s?.shutdownNow()
        silenceCurrentNote()
    }

    fun stop() { stopIndependent() }

    /**
     * Public hook for MidiService to trigger a rebuild when shared ProSettings change.
     */
    fun forceRebuildHelpers() {
        if (running) rebuildHelpers()
    }

    private val independentLoop = Runnable {
        try {
            while (running) {
                try {
                    val cfg = config
                    val ic = cfg.independentConfig
                    val isMelodic = cfg.mode == VoiceMode.MELODIC

                    if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
                        fireIndependentNote(ic)
                        try { Thread.sleep(Long.MAX_VALUE) } catch (_: InterruptedException) { }
                        break
                    }

                    if (isMelodic) {
                        playMelodicPhrase(cfg)
                    } else {
                        playIndependentBeat(ic)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in independentLoop iteration: ${e.message}")
                    try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                }
            }
        } finally {
            silenceCurrentNote()
            running = false
        }
    }

    private fun playIndependentBeat(ic: IndependentConfig) {
        // Euclidean mode is active when the voice's timingMode selector == TIMING_EUCLIDEAN.
        // The euclideanEnabled toggle in ProSettings is a parameter editor, NOT a gate.
        val euclidOn = ic.timingMode == MidiService.TIMING_EUCLIDEAN
        val isOnset = if (euclidOn) {
            val pattern = euclideanPattern
            val hit = pattern.getOrElse(euclideanStep) { false }
            if (pattern.isNotEmpty()) {
                euclideanStep = (euclideanStep + 1) % pattern.size
            }
            hit
        } else true

        if (isOnset) {
            dispatcher?.advanceBeat()
            fireIndependentNote(ic)
        }

        val interval = calcInterval(ic)
        Thread.sleep(interval.coerceAtLeast(10L))
    }

    private fun playMelodicPhrase(cfg: VoiceConfig) {
        val ic = cfg.independentConfig
        val rc = cfg.melodicRelationConfig
        val baseInterval = (60_000.0 / ic.bpm.coerceAtLeast(1)).toLong()
        
        // Generate a simple rhythmic motif: a list of multipliers for baseInterval
        val motifs = listOf(
            listOf(0.5f, 0.5f, 1.0f, 2.0f),
            listOf(1.0f, 1.0f, 0.5f, 0.5f),
            listOf(0.75f, 0.25f, 1.0f, 1.0f),
            listOf(1.5f, 0.5f, 1.0f, 1.0f)
        )
        val motif = motifs.random()
        
        for (multiplier in motif) {
            if (!running) break
            
            val interval = (baseInterval * multiplier).toLong()
            
            // Awareness: RHYTHMIC_COMPLEMENT
            if (rc.enabled && rc.mode == MelodicRelationMode.RHYTHMIC_COMPLEMENT) {
                val future = getFuture(rc.referenceVoice)
                if (future.isNotEmpty()) {
                    val nextV1Dur = future[0].durationMs
                    if (nextV1Dur < baseInterval / 2) {
                        if (Random.nextFloat() < 0.5f) {
                            Thread.sleep(interval)
                            continue 
                        }
                    }
                }
            }

            dispatcher?.advanceBeat()
            fireIndependentNote(ic)
            
            Thread.sleep(interval.coerceAtLeast(10L))
        }
    }

    /**
     * Core note-fire for INDEPENDENT and MELODIC modes.
     *
     * For CHORDS style: builds a full chord via DiatonicHarmony.buildChordNotes
     * and strums/fires notes according to ic.chordConfig.
     *
     * For MELODIC mode with contrast enabled: filters the candidate note pool
     * through DiatonicHarmony.applyMelodicRelation before picking a pitch.
     */
    private fun fireIndependentNote(ic: IndependentConfig) {
        try {
            val isDrone = ic.style == VoiceStyle.SINGLE_NOTE_DRONE || ic.style == VoiceStyle.EVOLVING_DRONE
            val isChords = ic.style == VoiceStyle.CHORDS
            val prevNote = currentNote
            val prevCh = currentNoteCh

            if (!isDrone && !isChords && prevNote >= 0) onNoteOffRaw(prevNote, prevCh)

            if (isChords) {
                fireChordNotes(ic)
                return
            }

            val octMin = ic.minOctave
            val octMax = ic.maxOctave.coerceAtLeast(octMin)
            val octRange = (octMax - octMin + 1).coerceAtLeast(1)

            val noteNumber: Int

            if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE) {
                val selectedOctave = if (octRange <= 1) octMin else octMin + Random.nextInt(octRange)
                val rootOffset = if (ic.rootNote > 0) ic.rootNote - 1 else 0
                noteNumber = ((selectedOctave + 1) * 12 + rootOffset).coerceIn(0, 127)
            } else {
                val intervals = getScales().getOrNull(ic.selectedScale) ?: return

                val rawDegreeIdx = dispatcher?.nextDegree() ?: Random.nextInt(intervals.size)
                if (rawDegreeIdx == -1) return

                val interval = intervals.getOrElse(rawDegreeIdx % intervals.size) { intervals[0] }

                val regShift = dispatcher?.gestureRegisterShift() ?: 0
                val shiftedMin = (octMin + regShift).coerceIn(0, 8)
                val shiftedMax = (octMax + regShift).coerceIn(shiftedMin, 9)
                val shiftedRange = (shiftedMax - shiftedMin + 1).coerceAtLeast(1)

                val oct = if (shiftedRange <= 1) shiftedMin else shiftedMin + Random.nextInt(shiftedRange)
                val rootOffset = if (ic.rootNote > 0) ic.rootNote - 1 else 0
                var candidate = ((oct + 1) * 12 + interval + rootOffset).coerceIn(0, 127)

                val mrc = config.melodicRelationConfig
                if (config.mode == VoiceMode.MELODIC && mrc.enabled) {
                    val voiceScale = getScales().getOrNull(ic.selectedScale) ?: intervals
                    val voiceRootOffset = if (ic.rootNote > 0) ic.rootNote - 1 else 0
                    val poolList = mutableListOf<Int>()
                    for (o in shiftedMin..shiftedMax) {
                        for (iv in voiceScale) {
                            val n = ((o + 1) * 12 + iv + voiceRootOffset).coerceIn(0, 127)
                            poolList.add(n)
                        }
                    }
                    val filteredPool = DiatonicHarmony.applyMelodicRelation(
                        candidateNotes = poolList.toIntArray(),
                        context        = getContext(mrc.referenceVoice),
                        relationCfg    = mrc
                    )
                    if (filteredPool.isNotEmpty()) {
                        candidate = filteredPool[Random.nextInt(filteredPool.size)]
                    }
                }

                noteNumber = candidate
            }

            val baseVel = velocityShaper.next()
            val gScale = dispatcher?.gestureVelocityScale() ?: 1f
            val vel = (baseVel * gScale).toInt().coerceIn(1, 127)

            onNoteOnRaw(noteNumber, vel, ic.midiChannel)
            onNotePlayed(noteNumber)
            currentNote = noteNumber
            currentNoteCh = ic.midiChannel

            if (isDrone && prevNote >= 0 && prevNote != noteNumber) {
                onNoteOffRaw(prevNote, prevCh)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fireIndependentNote: ${e.message}")
        }
    }

    /**
     * Chord emission for V2/V3 CHORDS style.
     */
    private fun fireChordNotes(ic: IndependentConfig) {
        try {
            val cc = ic.chordConfig
            val scaleIdx = ic.selectedScale
            val scaleIntervals = getScales().getOrNull(scaleIdx) ?: return
            val rootOffset = if (ic.rootNote > 0) ic.rootNote - 1 else 0

            val octMin = ic.minOctave
            val octMax = ic.maxOctave.coerceAtLeast(octMin)
            val octRange = octMax - octMin + 1
            val oct = if (octRange <= 1) octMin else octMin + Random.nextInt(octRange)
            val rootMidi = ((oct + 1) * 12 + scaleIntervals[Random.nextInt(scaleIntervals.size)] + rootOffset)
                .coerceIn(0, 127)

            var chordNotes = DiatonicHarmony.buildChordNotes(rootMidi, scaleIntervals, ic.rootNote, cc)
            chordNotes = DiatonicHarmony.applyInversion(chordNotes, cc.inversionMode, lastChordNotes.toList())
            lastChordNotes = chordNotes

            if (currentNote >= 0) onNoteOffRaw(currentNote, currentNoteCh)

            val baseVel = velocityShaper.next()
            val gScale = dispatcher?.gestureVelocityScale() ?: 1f
            val vel = (baseVel * gScale).toInt().coerceIn(1, 127)
            val ch = ic.midiChannel

            val orderedNotes = when (cc.pluckingStyle) {
                1 -> chordNotes.sorted()
                2 -> chordNotes.sortedDescending()
                3 -> chordNotes.shuffled()
                4 -> chordNotes.sorted()
                else -> chordNotes
            }

            val delayMs = cc.pluckingDelayMs
            val strumSize = cc.strumLength.coerceIn(1, orderedNotes.size)

            if (cc.pluckingStyle == 0 || delayMs <= 0L) {
                orderedNotes.forEach { note -> onNoteOnRaw(note, vel, ch) }
            } else {
                orderedNotes.take(strumSize).forEachIndexed { idx, note ->
                    if (idx == 0) {
                        onNoteOnRaw(note, vel, ch)
                    } else {
                        mainHandler.postDelayed({
                            if (running) onNoteOnRaw(note, vel, ch)
                        }, delayMs * idx)
                    }
                }
                if (orderedNotes.size > strumSize) {
                    val lastDelay = delayMs * strumSize
                    orderedNotes.drop(strumSize).forEach { note ->
                        mainHandler.postDelayed({
                            if (running) onNoteOnRaw(note, vel, ch)
                        }, lastDelay)
                    }
                }
            }

            currentNote = orderedNotes.firstOrNull() ?: rootMidi
            currentNoteCh = ch
            onNotePlayed(currentNote)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fireChordNotes: ${e.message}")
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
     *
     * Euclidean pattern is built when ic.timingMode == TIMING_EUCLIDEAN.
     * The euclideanEnabled toggle in ProSettings only gates the parameter fields
     * in the UI — it is NOT a runtime gate here.
     */
    @Synchronized
    private fun rebuildHelpers() {
        try {
            val ic = config.independentConfig
            val ps = ic.proSettings
            val intervals = getScales().getOrNull(ic.selectedScale) ?: listOf(0, 2, 4, 5, 7, 9, 11)

            velocityShaper = VelocityShaper(ps.velocityPattern, ic.velocity).also { it.reset() }
            dispatcher = MelodicEngineDispatcher(intervals, ps, getContext, getFuture)

            if (ic.timingMode == MidiService.TIMING_EUCLIDEAN) {
                euclideanPattern = EuclideanRhythm.generate(
                    ps.euclideanSteps.coerceIn(2, 32),
                    ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                    ps.euclideanRotation
                )
                euclideanStep = 0
            } else {
                euclideanPattern = BooleanArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in rebuildHelpers: ${e.message}")
        }
    }
}
