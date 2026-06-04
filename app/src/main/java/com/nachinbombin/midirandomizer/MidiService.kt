package com.nachinbombin.midirandomizer

import android.app.*
import android.content.Intent
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MidiService : Service() {

    companion object {
        private const val TAG           = "MidiService"
        private const val CHANNEL_ID    = "midi_channel"
        private const val NOTIFICATION_ID = 1

        const val TIMING_METRONOME  = 0
        const val TIMING_MIXED      = 1
        const val TIMING_RANDOMIZED = 2
        const val TIMING_EUCLIDEAN  = 3
    }

    inner class LocalBinder : Binder() {
        fun getService(): MidiService = this@MidiService
    }

    private val binder      = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduler: ExecutorService? = null

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice?   = null
    private var inputPort: MidiInputPort? = null

    @Volatile private var isPlaying = false

    // ── Voice parameters ─────────────────────────────────────────────────
    @Volatile private var v1Params = Voice1Params()
    @Volatile private var v2Config = VoiceConfig()
    @Volatile private var v3Config = VoiceConfig()

    data class Voice1Params(
        val bpm: Int = 120,
        val velocity: Int = 100,
        val minOctave: Int = 3,
        val maxOctave: Int = 5,
        val channel: Int = 0,
        val scale: Int = 0,
        val rootNote: Int = 0,
        val timingMode: Int = TIMING_METRONOME,
        val proSettings: ProSettings = ProSettings(),
        val style: VoiceStyle = VoiceStyle.GENERATIVE,
        val droneTiming: DroneTimingMode = DroneTimingMode.CONSTANT,
        val droneMinBeats: Int = 16,
        val droneMaxBeats: Int = 64
    )

    private var voice2Engine: VoiceEngine? = null
    private var voice3Engine: VoiceEngine? = null

    private val activeNotes = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val lastV2Note = AtomicInteger(60)

    private val scales = listOf(
        listOf(0,1,2,3,4,5,6,7,8,9,10,11),   // 0  Chromatic
        listOf(0,2,4,5,7,9,11),               // 1  Major (Ionian)
        listOf(0,2,3,5,7,8,10),               // 2  Minor (Natural)
        listOf(0,2,3,5,7,8,11),               // 3  Minor (Harmonic)
        listOf(0,2,4,7,9),                    // 4  Pentatonic Major
        listOf(0,3,5,7,10),                   // 5  Pentatonic Minor
        listOf(0,3,5,6,7,10),                 // 6  Blues
        listOf(0,2,3,5,7,9,10),               // 7  Dorian
        listOf(0,2,4,5,7,9,10),               // 8  Mixolydian
        listOf(0,2,4,6,8,10),                 // 9  Whole Tone
        listOf(0,2,3,5,7,8,10),               // 10 Kurd
        listOf(0,2,3,5,7,9,10),               // 11 Celtic Minor
        listOf(0,2,3,5,7,10),                 // 12 Pygmy
        listOf(0,2,4,6,7,9,10),               // 13 Lydian Dominant
        listOf(0,2,4,6,7,9,11),               // 14 Aegean (Lydian)
        listOf(0,1,4,5,7,8,10),               // 15 Hijaz
        listOf(0,2,3,7,8)                     // 16 Akebono
    )

    private var v1VelocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 100)

    // ── NEW: replaces the old v1MarkovChain field ─────────────────────────
    private var v1MelodicDispatcher: MelodicEngineDispatcher? = null

    private var v1Euclidean:     BooleanArray    = BooleanArray(0)
    private var v1EuclideanStep                  = 0
    private var v1CurrentNote                    = -1

    interface MidiEventListener {
        fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int)
        fun onStatusChanged(status: String)
        fun onPlaybackStateChanged(playing: Boolean)
        fun onVoiceParamsChanged(v1: Voice1Params, v2: VoiceConfig, v3: VoiceConfig)
    }
    private var listener: MidiEventListener? = null

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        activeNotes[1] = ConcurrentHashMap.newKeySet()
        activeNotes[2] = ConcurrentHashMap.newKeySet()
        activeNotes[3] = ConcurrentHashMap.newKeySet()

        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        createNotificationChannel()
        buildVoiceEngines()
    }

    fun setListener(l: MidiEventListener?) {
        listener = l
        l?.onPlaybackStateChanged(isPlaying)
        l?.onVoiceParamsChanged(v1Params, v2Config, v3Config)
    }

    fun isMidiPlaying() = isPlaying
    fun getV1Params() = v1Params
    fun getV2Config() = v2Config
    fun getV3Config() = v3Config

    @Synchronized
    fun updateV1Parameters(p: Voice1Params) {
        val rootChanged   = p.rootNote    != v1Params.rootNote
        val scaleChanged  = p.scale       != v1Params.scale
        val octaveChanged = p.minOctave   != v1Params.minOctave || p.maxOctave != v1Params.maxOctave
        val styleChanged  = p.style       != v1Params.style
        val proChanged    = p.proSettings != v1Params.proSettings

        v1Params = p
        v1VelocityShaper.baseVelocity = p.velocity
        rebuildV1ProHelpers()

        if (isPlaying && (styleChanged || (p.style == VoiceStyle.SINGLE_NOTE_DRONE && (rootChanged || scaleChanged || octaveChanged)))) {
            val old = scheduler
            scheduler = null
            old?.shutdownNow()
            scheduler = Executors.newSingleThreadExecutor()
            scheduler?.execute(noteLoop)
        }

        if (proChanged) {
            if (v2Config.mode == VoiceMode.INDEPENDENT && v2Config.independentConfig.useSharedPro) {
                voice2Engine?.config = effectiveVoiceConfig(v2Config)
            }
            if (v3Config.mode == VoiceMode.INDEPENDENT && v3Config.independentConfig.useSharedPro) {
                voice3Engine?.config = effectiveVoiceConfig(v3Config)
            }
        }

        if (rootChanged || scaleChanged) {
            restartDroneVoiceIfFollowingMain(voice2Engine, v2Config)
            restartDroneVoiceIfFollowingMain(voice3Engine, v3Config)
        }

        notifyParamsChanged()
    }

    private fun restartDroneVoiceIfFollowingMain(engine: VoiceEngine?, cfg: VoiceConfig) {
        if (!isPlaying) return
        if (engine == null || !cfg.enabled || cfg.mode != VoiceMode.INDEPENDENT) return
        val ic = cfg.independentConfig
        if (ic.style == VoiceStyle.SINGLE_NOTE_DRONE && ic.rootNote == 0) {
            engine.stopIndependent()
            engine.startIndependent()
        }
    }

    @Synchronized
    fun updateProSettings(settings: ProSettings) {
        if (v1Params.proSettings == settings) return
        v1Params = v1Params.copy(proSettings = settings)
        rebuildV1ProHelpers()

        if (v2Config.mode == VoiceMode.INDEPENDENT && v2Config.independentConfig.useSharedPro) {
            voice2Engine?.config = effectiveVoiceConfig(v2Config)
        }
        if (v3Config.mode == VoiceMode.INDEPENDENT && v3Config.independentConfig.useSharedPro) {
            voice3Engine?.config = effectiveVoiceConfig(v3Config)
        }

        notifyParamsChanged()
    }

    @Synchronized
    fun updateVoice2Config(cfg: VoiceConfig) {
        val oldCfg = v2Config
        v2Config = cfg
        voice2Engine?.config = effectiveVoiceConfig(cfg)

        if (isPlaying) {
            val ic    = cfg.independentConfig
            val oldIc = oldCfg.independentConfig

            val switchedToHarmony     = cfg.mode == VoiceMode.HARMONY     && oldCfg.mode != VoiceMode.HARMONY
            val switchedToIndependent = cfg.mode == VoiceMode.INDEPENDENT && oldCfg.mode != VoiceMode.INDEPENDENT

            when {
                switchedToHarmony -> {
                    voice2Engine?.stopIndependent()
                    if (cfg.enabled && v1CurrentNote >= 0) {
                        voice2Engine?.onV1NoteOn(v1CurrentNote, v1Params.velocity)
                    }
                }
                switchedToIndependent -> {
                    voice2Engine?.silenceCurrentNote()
                    if (cfg.enabled) voice2Engine?.startIndependent()
                }
                else -> {
                    val needsRestart =
                        (cfg.enabled != oldCfg.enabled) ||
                        (cfg.mode == VoiceMode.INDEPENDENT && (
                            ic.style != oldIc.style ||
                            (ic.style == VoiceStyle.SINGLE_NOTE_DRONE &&
                                (ic.rootNote != oldIc.rootNote ||
                                 ic.droneOctaveMin != oldIc.droneOctaveMin ||
                                 ic.droneOctaveMax != oldIc.droneOctaveMax))
                        ))

                    if (needsRestart) {
                        voice2Engine?.stopIndependent()
                        if (cfg.enabled && cfg.mode == VoiceMode.INDEPENDENT) voice2Engine?.startIndependent()
                    }
                }
            }

            if (!switchedToHarmony && !switchedToIndependent &&
                cfg.enabled && cfg.mode == VoiceMode.HARMONY &&
                cfg.harmonyConfig != oldCfg.harmonyConfig &&
                v1CurrentNote >= 0
            ) {
                voice2Engine?.onV1NoteOn(v1CurrentNote, v1Params.velocity)
            }
        }
        notifyParamsChanged()
    }

    @Synchronized
    fun updateVoice3Config(cfg: VoiceConfig) {
        val oldCfg = v3Config
        v3Config = cfg
        voice3Engine?.config = effectiveVoiceConfig(cfg)

        if (isPlaying) {
            val ic    = cfg.independentConfig
            val oldIc = oldCfg.independentConfig

            val switchedToHarmony     = cfg.mode == VoiceMode.HARMONY     && oldCfg.mode != VoiceMode.HARMONY
            val switchedToIndependent = cfg.mode == VoiceMode.INDEPENDENT && oldCfg.mode != VoiceMode.INDEPENDENT

            when {
                switchedToHarmony -> {
                    voice3Engine?.stopIndependent()
                    if (cfg.enabled && v1CurrentNote >= 0) {
                        voice3Engine?.onV1NoteOn(v1CurrentNote, v1Params.velocity)
                    }
                }
                switchedToIndependent -> {
                    voice3Engine?.silenceCurrentNote()
                    if (cfg.enabled) voice3Engine?.startIndependent()
                }
                else -> {
                    val needsRestart =
                        (cfg.enabled != oldCfg.enabled) ||
                        (cfg.mode == VoiceMode.INDEPENDENT && (
                            ic.style != oldIc.style ||
                            (ic.style == VoiceStyle.SINGLE_NOTE_DRONE &&
                                (ic.rootNote != oldIc.rootNote ||
                                 ic.droneOctaveMin != oldIc.droneOctaveMin ||
                                 ic.droneOctaveMax != oldIc.droneOctaveMax))
                        ))

                    if (needsRestart) {
                        voice3Engine?.stopIndependent()
                        if (cfg.enabled && cfg.mode == VoiceMode.INDEPENDENT) voice3Engine?.startIndependent()
                    }
                }
            }

            if (!switchedToHarmony && !switchedToIndependent &&
                cfg.enabled && cfg.mode == VoiceMode.HARMONY &&
                cfg.harmonyConfig != oldCfg.harmonyConfig &&
                v1CurrentNote >= 0
            ) {
                voice3Engine?.onV1NoteOn(v1CurrentNote, v1Params.velocity)
            }
        }
        notifyParamsChanged()
    }

    private fun effectiveVoiceConfig(cfg: VoiceConfig): VoiceConfig {
        return if (cfg.mode == VoiceMode.INDEPENDENT && cfg.independentConfig.useSharedPro) {
            cfg.copy(independentConfig = cfg.independentConfig.copy(proSettings = v1Params.proSettings))
        } else cfg
    }

    private fun notifyParamsChanged() {
        mainHandler.post { listener?.onVoiceParamsChanged(v1Params, v2Config, v3Config) }
    }

    fun connectToDevice(info: MidiDeviceInfo) {
        if (midiDevice?.info?.id == info.id) return
        closeDevice()
        midiManager?.openDevice(
            info,
            { device ->
                if (device == null) { notifyStatus("Failed to open device"); return@openDevice }
                midiDevice = device
                if (info.inputPortCount > 0) {
                    try {
                        inputPort = device.openInputPort(0)
                        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Device"
                        notifyStatus("Connected: $name")
                    } catch (e: Exception) {
                        notifyStatus("Error opening port: ${e.message}")
                        closeDevice()
                    }
                } else {
                    notifyStatus("Device has no input ports")
                }
            },
            mainHandler
        )
    }

    private fun closeDevice() {
        stopPlaying()
        try { inputPort?.close(); midiDevice?.close() } catch (e: IOException) { /* ignore */ }
        inputPort = null; midiDevice = null
    }

    fun togglePlayback() { if (isPlaying) stopPlaying() else startPlaying() }

    private fun startPlaying() {
        if (isPlaying) return
        isPlaying = true
        rebuildV1ProHelpers()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        voice2Engine?.let { if (v2Config.enabled && v2Config.mode == VoiceMode.INDEPENDENT) it.startIndependent() }
        voice3Engine?.let { if (v3Config.enabled && v3Config.mode == VoiceMode.INDEPENDENT) it.startIndependent() }

        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(noteLoop)
        notifyPlaybackState(playing = true)
    }

    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false

        val old = scheduler
        scheduler = null
        old?.shutdownNow()

        allNotesOff()
        voice2Engine?.stop()
        voice3Engine?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyPlaybackState(false)
    }

    private fun allNotesOff() {
        for (v in 1..3) {
            val set = activeNotes[v] ?: continue
            val notes = set.toList()
            set.clear()
            val baseCh = when (v) {
                1 -> v1Params.channel
                2 -> if (v2Config.mode == VoiceMode.HARMONY) v2Config.harmonyConfig.midiChannel else v2Config.independentConfig.midiChannel
                3 -> if (v3Config.mode == VoiceMode.HARMONY) v3Config.harmonyConfig.midiChannel else v3Config.independentConfig.midiChannel
                else -> 0
            }
            notes.forEach { note -> sendNoteOffRaw(v, note, baseCh) }
        }
        (0..15).forEach { ch -> sendRawMidi(byteArrayOf((0xB0 or ch).toByte(), 123, 0)) }
    }

    private val noteLoop = Runnable {
        while (isPlaying) {
            try {
                val p = v1Params
                if (p.style == VoiceStyle.SINGLE_NOTE_DRONE) {
                    sendRandomV1Note()
                    try { Thread.sleep(Long.MAX_VALUE) } catch (_: InterruptedException) { break }
                    break
                }

                val ps = p.proSettings
                val euclidOn = ps.euclideanEnabled && p.timingMode == TIMING_EUCLIDEAN
                val isOnset = if (euclidOn) {
                    val hit = v1Euclidean.getOrElse(v1EuclideanStep) { false }
                    v1EuclideanStep = (v1EuclideanStep + 1) % v1Euclidean.size.coerceAtLeast(1)
                    hit
                } else true

                if (isOnset) {
                    v1MelodicDispatcher?.advanceBeat()
                    sendRandomV1Note()
                }

                val interval = calculateV1Interval()
                Thread.sleep(interval.coerceAtLeast(10L))
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in noteLoop: ${e.message}", e)
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }
        }
    }

    private fun calculateV1Interval(): Long {
        val p = v1Params
        val safeBpm = p.bpm.coerceIn(10, 500)
        if (p.style == VoiceStyle.EVOLVING_DRONE) {
            val beats = if (p.droneTiming == DroneTimingMode.RANDOM) {
                val min = p.droneMinBeats.coerceIn(1, 256)
                val max = p.droneMaxBeats.coerceIn(min, 256)
                Random.nextInt(min, max + 1)
            } else 32
            return (60_000L / safeBpm) * beats
        }
        val base = (60_000.0 / safeBpm).toLong()
        val ps = p.proSettings
        val modeInterval = when (p.timingMode) {
            TIMING_METRONOME  -> base
            TIMING_MIXED      -> if (Random.nextFloat() < 0.3f) base / 2 else base
            TIMING_RANDOMIZED -> (base * (0.5 + Random.nextDouble())).toLong()
            TIMING_EUCLIDEAN  -> base
            else              -> base
        }
        return JitterEngine.applyJitter(modeInterval, ps.jitterAmount, ps.jitterType)
    }

    private fun sendRandomV1Note() {
        val p = v1Params
        val isDrone = p.style == VoiceStyle.SINGLE_NOTE_DRONE || p.style == VoiceStyle.EVOLVING_DRONE
        val prevNote = v1CurrentNote

        if (!isDrone && prevNote >= 0) sendNoteOffRaw(1, prevNote, p.channel)

        // ── velocity: apply gesture scale on top of shaper ────────────────
        val baseVel = v1VelocityShaper.next()
        val gestureScale = v1MelodicDispatcher?.gestureVelocityScale() ?: 1f
        val vel = (baseVel * gestureScale).toInt().coerceIn(1, 127)

        val noteNumber: Int

        if (p.style == VoiceStyle.SINGLE_NOTE_DRONE) {
            val octRange = (p.maxOctave - p.minOctave + 1).coerceAtLeast(1)
            val selectedOctave = if (octRange == 1) p.minOctave
                                 else p.minOctave + Random.nextInt(octRange)
            val rootOffset = if (p.rootNote > 0) p.rootNote - 1 else 0
            noteNumber = ((selectedOctave + 1) * 12 + rootOffset).coerceIn(0, 127)
        } else {
            val ps        = p.proSettings
            val intervals = scales.getOrNull(p.scale) ?: return

            // ── central dispatch: MelodicEngineDispatcher ─────────────────
            val rawDegree = v1MelodicDispatcher?.nextDegree() ?: Random.nextInt(intervals.size)

            // -1 means gesture density gate suppressed this note
            if (rawDegree < 0) return

            val degreeIdx = rawDegree.coerceIn(0, intervals.size - 1)
            val interval  = intervals[degreeIdx]

            // gesture register shift applied to octave selection
            val regShift  = v1MelodicDispatcher?.gestureRegisterShift() ?: 0
            val octRange  = (p.maxOctave - p.minOctave + 1).coerceAtLeast(1)
            val rawOctave = p.minOctave + Random.nextInt(octRange) + regShift
            val selectedOctave = rawOctave.coerceIn(p.minOctave, p.maxOctave)

            val rootOffset = if (p.rootNote > 0) p.rootNote - 1 else 0
            noteNumber = ((selectedOctave + 1) * 12 + interval + rootOffset).coerceIn(0, 127)
        }

        sendNoteOnRaw(1, noteNumber, vel, p.channel)
        v1CurrentNote = noteNumber

        if (isDrone && prevNote >= 0 && prevNote != noteNumber) {
            sendNoteOffRaw(1, prevNote, p.channel)
        }

        voice2Engine?.onV1NoteOn(noteNumber, vel)
        voice3Engine?.onV1NoteOn(noteNumber, vel)

        val noteName = noteNumberToName(noteNumber)
        mainHandler.post { listener?.onNotePlayed(noteName, noteNumber, vel) }
    }

    fun sendNoteOnRaw(voiceId: Int, note: Int, vel: Int, ch: Int) {
        val channels = if (ch == 0) (0..15).toList() else listOf(ch - 1)
        channels.forEach { c ->
            sendRawMidi(byteArrayOf((0x90 or c).toByte(), note.toByte(), vel.toByte()))
        }
        activeNotes[voiceId]?.add(note)
    }

    fun sendNoteOffRaw(voiceId: Int, note: Int, ch: Int) {
        val channels = if (ch == 0) (0..15).toList() else listOf(ch - 1)
        channels.forEach { c ->
            sendRawMidi(byteArrayOf((0x80 or c).toByte(), note.toByte(), 0))
        }
        activeNotes[voiceId]?.remove(note)
    }

    fun sendRawMidi(msg: ByteArray) {
        inputPort?.let { p ->
            try { p.send(msg, 0, msg.size) } catch (e: IOException) { Log.e(TAG, "Send error", e) }
        }
        MidiOutputService.getInstance()?.sendMidiToClients(msg, 0, msg.size, 0)
    }

    private fun buildVoiceEngines() {
        voice2Engine = VoiceEngine(
            voiceId        = 2,
            mainHandler    = mainHandler,
            getInputPort   = { inputPort },
            getScales      = { scales },
            getGlobalScale = { v1Params.scale },
            getGlobalRoot  = { if (v1Params.rootNote > 0) v1Params.rootNote - 1 else 0 },
            getV2Note      = { lastV2Note.get() },
            onNotePlayed   = { note -> lastV2Note.set(note) },
            onNoteOnRaw    = { n, v, ch -> sendNoteOnRaw(2, n, v, ch) },
            onNoteOffRaw   = { n, ch -> sendNoteOffRaw(2, n, ch) }
        ).also { it.config = effectiveVoiceConfig(v2Config) }

        voice3Engine = VoiceEngine(
            voiceId        = 3,
            mainHandler    = mainHandler,
            getInputPort   = { inputPort },
            getScales      = { scales },
            getGlobalScale = { v1Params.scale },
            getGlobalRoot  = { if (v1Params.rootNote > 0) v1Params.rootNote - 1 else 0 },
            getV2Note      = { lastV2Note.get() },
            onNotePlayed   = { /* V3 doesn't seed anyone yet */ },
            onNoteOnRaw    = { n, v, ch -> sendNoteOnRaw(3, n, v, ch) },
            onNoteOffRaw   = { n, ch -> sendNoteOffRaw(3, n, ch) }
        ).also { it.config = effectiveVoiceConfig(v3Config) }
    }

    private fun rebuildV1ProHelpers() {
        val p  = v1Params
        val ps = p.proSettings
        val intervals = scales.getOrNull(p.scale) ?: listOf(0,2,4,5,7,9,11)

        v1VelocityShaper = VelocityShaper(ps.velocityPattern, p.velocity).also { it.reset() }

        // ── Build the dispatcher for whatever engine is selected ──────────
        // NAIVE with markovEnabled=false falls through to dispatcher NAIVE mode;
        // legacy markovEnabled=true maps to MARKOV for backwards compatibility.
        val effectiveSettings = if (ps.melodicEngine == MelodicEngine.NAIVE && ps.markovEnabled) {
            ps.copy(melodicEngine = MelodicEngine.MARKOV)
        } else ps

        v1MelodicDispatcher = MelodicEngineDispatcher(
            scaleIntervals = intervals,
            settings       = effectiveSettings
        )

        if (ps.euclideanEnabled) {
            v1Euclidean = EuclideanRhythm.generate(
                ps.euclideanSteps.coerceIn(2, 32),
                ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                ps.euclideanRotation
            )
            v1EuclideanStep = 0
        }
    }

    private fun noteNumberToName(midiNote: Int): String {
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        return "${names[midiNote % 12]}${(midiNote / 12) - 1}"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "MIDI Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification {
        val pi = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MIDI Randomizer")
            .setContentText("Generating notes…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(status: String)          { mainHandler.post { listener?.onStatusChanged(status) } }
    private fun notifyPlaybackState(playing: Boolean)  { mainHandler.post { listener?.onPlaybackStateChanged(playing) } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand startForeground failed", e)
        }
        return START_STICKY
    }

    override fun onDestroy() { closeDevice(); scheduler?.shutdownNow(); super.onDestroy() }
}
