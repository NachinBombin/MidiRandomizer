package com.nachinbombin.midirandomizer

import android.media.midi.MidiInputPort
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Self-contained execution engine for one secondary voice (V2 or V3).
 *
 * In HARMONY mode the engine is fed Voice-1 events via [onV1NoteOn] and
 * schedules its own transformed Note-On after a micro-delay.
 *
 * In INDEPENDENT mode the engine runs its own note loop on a dedicated thread,
 * mirroring the full MidiService note loop logic but using its own config.
 */
class VoiceEngine(
    private val voiceId:    Int,          // 2 or 3
    private val mainHandler: Handler,
    private val getInputPort: () -> MidiInputPort?,
    private val getScales: () -> List<List<Int>>,
    private val getGlobalScale: () -> Int,
    private val getGlobalRoot:  () -> Int,
    private val getV2Note: () -> Int      // for V3 cascade reference
) {
    companion object { private const val TAG = "VoiceEngine" }

    @Volatile var config: VoiceConfig = VoiceConfig()

    // Independent-mode state
    private var scheduler: ExecutorService? = null
    @Volatile private var running = false
    private var currentNote = -1

    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    private var markovChain: MarkovMelody? = null
    private var euclideanPattern: BooleanArray = BooleanArray(0)
    private var euclideanStep = 0

    // ── Harmony mode ─────────────────────────────────────────────────────────

    /** Called by MidiService immediately after Voice 1 fires a Note-On. */
    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
        val cfg = config
        if (!cfg.enabled || cfg.mode != VoiceMode.HARMONY) return
        val hc = cfg.harmonyConfig

        // Skip probability check
        if (hc.skipProbability > 0f && Random.nextFloat() < hc.skipProbability) return

        // Compute target pitch
        val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: return
        val allowed = DiatonicHarmony.allowedNotes(scaleIntervals, getGlobalRoot())
        val refNote = if (voiceId == 3 && hc.referenceVoice == 2) getV2Note() else v1Note
        val targetNote = DiatonicHarmony.applyOffset(refNote, hc.toneStepOffset, allowed)

        // Velocity
        val vel = DiatonicHarmony.applyVelocity(v1Velocity, hc)

        // Schedule with time drift
        val delay = if (hc.timeDriftMs > 0) Random.nextLong(0, hc.timeDriftMs + 1) else 0L
        mainHandler.postDelayed({
            sendNote(targetNote, vel, hc.midiChannel)
        }, delay)
    }

    // ── Independent mode ─────────────────────────────────────────────────────

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
        scheduler?.shutdownNow()
        try { scheduler?.awaitTermination(500, TimeUnit.MILLISECONDS) }
        catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        scheduler = null
        if (currentNote >= 0) { sendNoteOff(currentNote, config.independentConfig.midiChannel); currentNote = -1 }
    }

    fun stop() { stopIndependent() }

    private val independentLoop = Runnable {
        while (running) {
            val ic  = config.independentConfig
            val ps  = ic.proSettings

            val euclidOn = ps.euclideanEnabled && ic.timingMode == MidiService.TIMING_EUCLIDEAN
            val isOnset  = if (euclidOn) {
                val hit = euclideanPattern.getOrElse(euclideanStep) { false }
                euclideanStep = (euclideanStep + 1) % euclideanPattern.size.coerceAtLeast(1)
                hit
            } else true

            if (isOnset) fireIndependentNote(ic)

            try { Thread.sleep(calcInterval(ic)) }
            catch (e: InterruptedException) { break }
        }
    }

    private fun fireIndependentNote(ic: IndependentConfig) {
        if (currentNote >= 0) { sendNoteOff(currentNote, ic.midiChannel); currentNote = -1 }
        val intervals = getScales().getOrNull(ic.selectedScale) ?: return
        val ps = ic.proSettings

        val degreeIdx = if (ps.markovEnabled) {
            markovChain?.nextDegree() ?: Random.nextInt(intervals.size)
        } else Random.nextInt(intervals.size)

        val interval = intervals[degreeIdx]
        val range    = (ic.maxOctave - ic.minOctave + 1).coerceAtLeast(1)
        val oct      = ic.minOctave + Random.nextInt(range)
        val noteNum  = ((oct + 1) * 12 + interval).coerceIn(0, 127)
        val vel      = velocityShaper.next()

        sendNote(noteNum, vel, ic.midiChannel)
        currentNote = noteNum
    }

    private fun calcInterval(ic: IndependentConfig): Long {
        val base = (60_000.0 / ic.bpm).toLong()
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

    // ── MIDI send helpers ─────────────────────────────────────────────────────

    private fun sendNote(note: Int, vel: Int, channel: Int) {
        val channels = if (channel == 0) (0..15).toList() else listOf(channel - 1)
        channels.forEach { ch ->
            val msg = byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte())
            try { getInputPort()?.send(msg, 0, msg.size) }
            catch (e: IOException) { Log.e(TAG, "V$voiceId send error", e) }
            MidiOutputService.getInstance()?.sendMidiToClients(msg, 0, msg.size, 0)
        }
    }

    private fun sendNoteOff(note: Int, channel: Int) {
        val channels = if (channel == 0) (0..15).toList() else listOf(channel - 1)
        channels.forEach { ch ->
            val msg = byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0)
            try { getInputPort()?.send(msg, 0, msg.size) }
            catch (e: IOException) { /* ignore */ }
            MidiOutputService.getInstance()?.sendMidiToClients(msg, 0, msg.size, 0)
        }
    }

    // ── Helper rebuild ────────────────────────────────────────────────────────

    private fun rebuildHelpers(ic: IndependentConfig) {
        val ps       = ic.proSettings
        val scSz     = getScales().getOrNull(ic.selectedScale)?.size ?: 7
        velocityShaper = VelocityShaper(ps.velocityPattern, ic.velocity).also { it.reset() }
        markovChain  = if (ps.markovEnabled)
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
