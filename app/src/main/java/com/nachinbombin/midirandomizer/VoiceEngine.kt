package com.nachinbombin.midirandomizer

import android.media.midi.MidiInputPort
import android.os.Handler
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Self-contained execution engine for one secondary voice (V2 or V3).
 * Supports three styles: Generative (Harmony + Independent), Single Note Drone,
 * and Evolving Drone.
 */
class VoiceEngine(
    private val voiceId:      Int,
    private val mainHandler:  Handler,
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
    private var currentNote = -1

    // helpers for Generative Independent
    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    private var markovChain: MarkovMelody? = null
    private var euclideanPattern: BooleanArray = BooleanArray(0)
    private var euclideanStep = 0

    // helpers for Evolving Drone
    private var evoVelocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 90)
    private var evoMarkovChain: MarkovMelody? = null

    // ─────────────────────────────────────────────────────────────────
    // Public lifecycle
    // ─────────────────────────────────────────────────────────────────

    /** Called by MidiService.startPlaying() for every enabled style. */
    fun start() {
        val cfg = config
        if (!cfg.enabled) return
        when (cfg.style) {
            VoiceStyle.GENERATIVE        -> if (cfg.mode == VoiceMode.INDEPENDENT) startIndependent()
            VoiceStyle.SINGLE_NOTE_DRONE -> startDrone()
            VoiceStyle.EVOLVING_DRONE    -> startEvolvingDrone()
        }
    }

    /** Called by MidiService.stopPlaying() and on config change. */
    fun stop() {
        running = false
        scheduler?.shutdownNow()
        try { scheduler?.awaitTermination(500, TimeUnit.MILLISECONDS) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        scheduler = null
        if (currentNote >= 0) {
            val ch = activeMidiChannel()
            onNoteOffRaw(currentNote, ch)
            currentNote = -1
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Harmony mode (Generative / triggered by V1)
    // ─────────────────────────────────────────────────────────────────

    fun onV1NoteOn(v1Note: Int, v1Velocity: Int) {
        val cfg = config
        if (!cfg.enabled || cfg.style != VoiceStyle.GENERATIVE || cfg.mode != VoiceMode.HARMONY) return
        val hc = cfg.harmonyConfig
        if (hc.skipProbability > 0f && Random.nextFloat() < hc.skipProbability) return

        val scaleIntervals = getScales().getOrNull(getGlobalScale()) ?: return
        val allowed  = DiatonicHarmony.allowedNotes(scaleIntervals, getGlobalRoot())
        val refNote  = if (voiceId == 3 && hc.referenceVoice == 2) getV2Note() else v1Note
        val target   = DiatonicHarmony.applyOffset(refNote, hc.toneStepOffset, allowed)
        val vel      = DiatonicHarmony.applyVelocity(v1Velocity, hc)
        val delay    = if (hc.timeDriftMs > 0) Random.nextLong(0, hc.timeDriftMs + 1) else 0L

        if (delay <= 0) {
            fireHarmonyNote(target, vel, hc.midiChannel)
        } else {
            mainHandler.postDelayed({
                if (config.style == VoiceStyle.GENERATIVE && config.mode == VoiceMode.HARMONY)
                    fireHarmonyNote(target, vel, hc.midiChannel)
            }, delay)
        }
    }

    private fun fireHarmonyNote(note: Int, vel: Int, ch: Int) {
        if (currentNote >= 0) onNoteOffRaw(currentNote, ch)
        onNoteOnRaw(note, vel, ch)
        onNotePlayed(note)
        currentNote = note
    }

    // ─────────────────────────────────────────────────────────────────
    // Generative Independent
    // ─────────────────────────────────────────────────────────────────

    fun startIndependent() {
        if (running) return
        running = true
        rebuildGenerativeHelpers(config.independentConfig)
        launchLoop(independentLoop)
    }

    fun stopIndependent() = stop()

    private val independentLoop = Runnable {
        while (running) {
            val ic = config.independentConfig
            val ps = ic.proSettings
            val euclidOn = ps.euclideanEnabled && ic.timingMode == MidiService.TIMING_EUCLIDEAN
            val isOnset = if (euclidOn) {
                val hit = euclideanPattern.