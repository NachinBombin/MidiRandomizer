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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    // ── Voice parameters (Shared for sync) ───────────────────────────────────
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
        val proSettings: ProSettings = ProSettings()
    )

    private var voice2Engine: VoiceEngine? = null
    private var voice3Engine: VoiceEngine? = null

    private val activeNotes = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val lastV2Note = AtomicInteger(60)

    private val scales = listOf(
        listOf(0,1,2,3,4,5,6,7,8,9,10,11),
        listOf(0,2,4,5,7,9,11),
        listOf(0,2,3,5,7,8,10),
        listOf(0,2,3,5,7,8,11),
        listOf(0,2,4,7,9),
        listOf(0,3,5,7,10),
        listOf(0,3,5,6,7,10),
        listOf(0,2,3,5,7,9,10),
        listOf(0,2,4,5,7,9,10),
        listOf(0,2,4,6,8,10)
    )

    private var v1VelocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 100)
    private var v1MarkovChain:    MarkovMelody?   = null
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
        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        createNotificationChannel()
        activeNotes[1] = ConcurrentHashMap.newKeySet()
        activeNotes[2] = ConcurrentHashMap.newKeySet()
        activeNotes[3] = ConcurrentHashMap.newKeySet()
        buildVoiceEngines()
    }

    fun setListener(l: MidiEventListener?) {
        listener = l
        l?.onPlaybackStateChanged(isPlaying)
        l?.onVoiceParamsChanged(v1Params, v2Config, v3Config)
    }

    // State accessors for manual polling
    fun isMidiPlaying() = isPlaying
    fun getV1Params() = v1Params
    fun getV2Config() = v2Config
    fun getV3Config() = v3Config

    @Synchronized
    fun updateV1Parameters(p: Voice1Params) {
        v1Params = p
        v1VelocityShaper.baseVelocity = p.velocity
        rebuildV1ProHelpers()
        
        updateVoice2Config(v2Config)
        updateVoice3Config(v3Config)
        notifyParamsChanged()
    }

    @Synchronized
    fun updateProSettings(settings: ProSettings) {
        v1Params = v1Params.copy(proSettings = settings)
        rebuildV1ProHelpers()
        
        updateVoice2Config(v2Config)
        updateVoice3Config(v3Config)
        notifyParamsChanged()
    }

    @Synchronized
    fun updateVoice2Config(cfg: VoiceConfig) {
        v2Config = cfg
        voice2Engine?.config = effectiveVoiceConfig(cfg)
        if (isPlaying) {
            voice2Engine?.stopIndependent()
            if (cfg.enabled && (cfg.mode == VoiceMode.INDEPENDENT)) voice2Engine?.startIndependent()
        }
        notifyParamsChanged()
    }

    @Synchronized
    fun updateVoice3Config(cfg: VoiceConfig) {
        v3Config = cfg
        voice3Engine?.config = effectiveVoiceConfig(cfg)
        if (isPlaying) {
            voice3Engine?.stopIndependent()
            if (cfg.enabled && (cfg.mode == VoiceMode.INDEPENDENT)) voice3Engine?.startIndependent()
        }
        notifyParamsChanged()
    }

    private fun effectiveVoiceConfig(cfg: VoiceConfig): VoiceConfig {
        return if (cfg.mode == VoiceMode.INDEPENDENT && cfg.independentConfig.useSharedPro) {
            cfg.copy(independentConfig = cfg.independentConfig.copy(proSettings = v1Params.proSettings))
        } else {
            cfg
        }
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
                if (device == null) {
                    notifyStatus("Failed to open device")
                    return@openDevice
                }
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
        try { startForeground(NOTIFICATION_ID, createNotification()) }
        catch (e: Exception) { Log.e(TAG, "startForeground failed", e) }

        voice2Engine?.let { if (v2Config.enabled && (v2Config.mode == VoiceMode.INDEPENDENT)) it.startIndependent() }
        voice3Engine?.let { if (v3Config.enabled && (v3Config.mode == VoiceMode.INDEPENDENT)) it.startIndependent() }

        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(noteLoop)
        notifyPlaybackState(playing = true)
    }

    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false
        scheduler?.shutdownNow()
        try { scheduler?.awaitTermination(500, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        scheduler = null
        
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
            notes.forEach { note ->
                val baseCh = when(v) {
                    1 -> v1Params.channel
                    2 -> v2Config.harmonyConfig.midiChannel
                    3 -> v3Config.harmonyConfig.midiChannel
                    else -> 0
                }
                sendNoteOffRaw(v, note, baseCh)
            }
        }
        (0..15).forEach { ch ->
            sendRawMidi(byteArrayOf((0xB0 or ch).toByte(), 123, 0))
        }
    }

    private val noteLoop = Runnable {
        while (isPlaying) {
            val p = v1Params
            val ps = p.proSettings
            val euclidOn = ps.euclideanEnabled && p.timingMode == TIMING_EUCLIDEAN
            val isOnset = if (euclidOn) {
                val hit = v1Euclidean.getOrElse(v1EuclideanStep) { false }
                v1EuclideanStep = (v1EuclideanStep + 1) % v1Euclidean.size.coerceAtLeast(1)
                hit
            } else true

            if (isOnset) sendRandomV1Note()

            try { Thread.sleep(calculateV1Interval()) }
            catch (_: InterruptedException) { break }
        }
    }

    private fun calculateV1Interval(): Long {
        val p = v1Params
        val base = (60_000.0 / p.bpm).toLong()
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
        if (v1CurrentNote >= 0) sendNoteOffRaw(1, v1CurrentNote, v1Params.channel)

        val p = v1Params
        val ps = p.proSettings
        val intervals = scales.getOrNull(p.scale) ?: return

        val degreeIdx = if (ps.markovEnabled) {
            v1MarkovChain?.nextDegree() ?: Random.nextInt(intervals.size)
        } else Random.nextInt(intervals.size)

        val interval = intervals[degreeIdx]
        val range = (p.maxOctave - p.minOctave + 1).coerceAtLeast(1)
        val selectedOctave = p.minOctave + Random.nextInt(range)
        val noteNumber = ((selectedOctave + 1) * 12 + interval).coerceIn(0, 127)
        val vel = v1VelocityShaper.next()

        sendNoteOnRaw(1, noteNumber, vel, p.channel)
        v1CurrentNote = noteNumber

        voice2Engine?.onV1NoteOn(noteNumber, vel)
        voice3Engine?.onV1NoteOn(noteNumber, vel)

        val noteName = noteNumberToName(noteNumber)
        mainHandler.post { listener?.onNotePlayed(noteName, noteNumber, vel) }
    }

    fun sendNoteOnRaw(voiceId: Int, note: Int, vel: Int, ch: Int) {
        val channels = if (ch == 0) (0..15).toList() else listOf(ch - 1)
        channels.forEach { c ->
            val msg = byteArrayOf((0x90 or c).toByte(), note.toByte(), vel.toByte())
            sendRawMidi(msg)
        }
        activeNotes[voiceId]?.add(note)
    }

    fun sendNoteOffRaw(voiceId: Int, note: Int, ch: Int) {
        val channels = if (ch == 0) (0..15).toList() else listOf(ch - 1)
        channels.forEach { c ->
            val msg = byteArrayOf((0x80 or c).toByte(), note.toByte(), 0)
            sendRawMidi(msg)
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
            voiceId      = 2,
            mainHandler  = mainHandler,
            getInputPort = { inputPort },
            getScales    = { scales },
            getGlobalScale = { v1Params.scale },
            getGlobalRoot  = { v1Params.rootNote },
            getV2Note    = { lastV2Note.get() },
            onNotePlayed = { note -> lastV2Note.set(note) },
            onNoteOnRaw  = { n, v, ch -> sendNoteOnRaw(2, n, v, ch) },
            onNoteOffRaw = { n, ch -> sendNoteOffRaw(2, n, ch) }
        ).also { it.config = effectiveVoiceConfig(v2Config) }

        voice3Engine = VoiceEngine(
            voiceId      = 3,
            mainHandler  = mainHandler,
            getInputPort = { inputPort },
            getScales    = { scales },
            getGlobalScale = { v1Params.scale },
            getGlobalRoot  = { v1Params.rootNote },
            getV2Note    = { lastV2Note.get() },
            onNotePlayed = { /* V3 doesn't seed anyone yet */ },
            onNoteOnRaw  = { n, v, ch -> sendNoteOnRaw(3, n, v, ch) },
            onNoteOffRaw = { n, ch -> sendNoteOffRaw(3, n, ch) }
        ).also { it.config = effectiveVoiceConfig(v3Config) }
    }

    private fun rebuildV1ProHelpers() {
        val p = v1Params
        val ps = p.proSettings
        val scaleSize = scales.getOrNull(p.scale)?.size ?: 7
        v1VelocityShaper = VelocityShaper(ps.velocityPattern, p.velocity).also { it.reset() }
        v1MarkovChain = if (ps.markovEnabled)
            MarkovMelody(scaleSize, ps.melodicLogicStyle).also { it.reset() } else null
        if (ps.euclideanEnabled) {
            v1Euclidean = EuclideanRhythm.generate(
                ps.euclideanSteps.coerceIn(2, 32),
                ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                ps.euclideanRotation,
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

    private fun notifyStatus(status: String)         { mainHandler.post { listener?.onStatusChanged(status) } }
    private fun notifyPlaybackState(playing: Boolean) { mainHandler.post { listener?.onPlaybackStateChanged(playing) } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIFICATION_ID, createNotification()) }
        catch (e: Exception) { Log.e(TAG, "onStartCommand startForeground failed", e) }
        return START_STICKY
    }

    override fun onDestroy() { closeDevice(); scheduler?.shutdownNow(); super.onDestroy() }
}
