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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MidiService : Service() {

    companion object {
        private const val TAG = "MidiService"
        private const val CHANNEL_ID = "midi_channel"
        private const val NOTIFICATION_ID = 1

        const val TIMING_METRONOME  = 0
        const val TIMING_MIXED      = 1
        const val TIMING_RANDOMIZED = 2
        const val TIMING_EUCLIDEAN  = 3   // Pro
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

    // ── Base parameters ───────────────────────────────────────────────────────
    private var bpm           = 120
    private var velocity      = 100
    private var minOctave     = 3
    private var maxOctave     = 5
    private var channel       = 0
    private var selectedScale = 0
    private var timingMode    = TIMING_METRONOME

    // ── Pro parameters ────────────────────────────────────────────────────────
    @Volatile private var proSettings = ProSettings()

    // ── Scale definitions ─────────────────────────────────────────────────────
    private val scales = listOf(
        listOf(0,1,2,3,4,5,6,7,8,9,10,11), // Chromatic
        listOf(0,2,4,5,7,9,11),             // Major
        listOf(0,2,3,5,7,8,10),             // Minor Natural
        listOf(0,2,3,5,7,8,11),             // Minor Harmonic
        listOf(0,2,4,7,9),                  // Pentatonic Major
        listOf(0,3,5,7,10),                 // Pentatonic Minor
        listOf(0,3,5,6,7,10),               // Blues
        listOf(0,2,3,5,7,9,10),             // Dorian
        listOf(0,2,4,5,7,9,10),             // Mixolydian
        listOf(0,2,4,6,8,10)                // Whole Tone
    )

    // ── Stateful helpers (re-created when pro settings change) ────────────────
    private var velocityShaper: VelocityShaper = VelocityShaper(VelocityPattern.RANDOM, 100)
    private var markovChain:   MarkovMelody?   = null

    // Euclidean pattern state
    private var euclideanPattern: BooleanArray = BooleanArray(0)
    private var euclideanStep   = 0

    private var currentNoteNumber = -1

    // ── Listener ──────────────────────────────────────────────────────────────
    interface MidiEventListener {
        fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int)
        fun onStatusChanged(status: String)
        fun onPlaybackStateChanged(playing: Boolean)
    }
    private var listener: MidiEventListener? = null

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        createNotificationChannel()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setListener(l: MidiEventListener?) {
        listener = l
        l?.onPlaybackStateChanged(isPlaying)
    }

    fun updateParameters(
        bpm: Int, velocity: Int,
        minOct: Int, maxOct: Int,
        chan: Int, scale: Int, timing: Int
    ) {
        this.bpm           = bpm
        this.velocity      = velocity
        this.minOctave     = minOct
        this.maxOctave     = maxOct
        this.channel       = chan
        this.selectedScale = scale
        this.timingMode    = timing
        velocityShaper.baseVelocity = velocity
    }

    fun updateProSettings(settings: ProSettings) {
        proSettings = settings
        rebuildProHelpers()
    }

    fun connectToDevice(info: MidiDeviceInfo) {
        if (midiDevice?.info?.id == info.id) return
        closeDevice()
        midiManager?.openDevice(info, { device ->
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
        }, mainHandler)
    }

    private fun closeDevice() {
        stopPlaying()
        try { inputPort?.close(); midiDevice?.close() } catch (e: IOException) { /* ignore */ }
        inputPort = null; midiDevice = null
    }

    fun togglePlayback() {
        if (isPlaying) stopPlaying() else startPlaying()
    }

    private fun startPlaying() {
        if (isPlaying) return
        isPlaying = true
        rebuildProHelpers()
        try { startForeground(NOTIFICATION_ID, createNotification()) }
        catch (e: Exception) { Log.e(TAG, "startForeground failed", e) }
        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(noteLoop)
        notifyPlaybackState(true)
    }

    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false
        scheduler?.shutdownNow()
        try { scheduler?.awaitTermination(500, TimeUnit.MILLISECONDS) }
        catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        scheduler = null
        if (currentNoteNumber >= 0) { sendNoteOff(currentNoteNumber); currentNoteNumber = -1 }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyPlaybackState(false)
    }

    // ── Note loop ─────────────────────────────────────────────────────────────

    private val noteLoop = Runnable {
        while (isPlaying) {
            // Euclidean: advance step, skip if not an onset
            val ps = proSettings
            val euclidOn = ps.euclideanEnabled && timingMode == TIMING_EUCLIDEAN
            val isOnset = if (euclidOn) {
                val hit = euclideanPattern.getOrElse(euclideanStep) { false }
                euclideanStep = (euclideanStep + 1) % euclideanPattern.size.coerceAtLeast(1)
                hit
            } else true

            if (isOnset) sendRandomNote()

            try { Thread.sleep(calculateInterval()) }
            catch (e: InterruptedException) { break }
        }
    }

    // ── Interval calculation ──────────────────────────────────────────────────

    private fun calculateInterval(): Long {
        val base = (60_000.0 / bpm).toLong()
        val ps   = proSettings

        val modeInterval = when (timingMode) {
            TIMING_METRONOME  -> base
            TIMING_MIXED      -> if (Random.nextFloat() < 0.3f) base / 2 else base
            TIMING_RANDOMIZED -> (base * (0.5 + Random.nextDouble())).toLong()
            TIMING_EUCLIDEAN  -> base  // each step = one beat
            else              -> base
        }
        // Apply jitter
        return JitterEngine.applyJitter(modeInterval, ps.jitterAmount, ps.jitterType)
    }

    // ── Note generation ───────────────────────────────────────────────────────

    private fun sendRandomNote() {
        if (currentNoteNumber >= 0) sendNoteOff(currentNoteNumber)

        val ps         = proSettings
        val intervals  = scales.getOrNull(selectedScale) ?: return
        val scaleSize  = intervals.size

        // Pitch selection
        val degreeIndex = if (ps.markovEnabled) {
            markovChain?.nextDegree() ?: Random.nextInt(scaleSize)
        } else {
            Random.nextInt(scaleSize)
        }
        val interval = intervals[degreeIndex]

        val range          = (maxOctave - minOctave + 1).coerceAtLeast(1)
        val selectedOctave = minOctave + Random.nextInt(range)
        val noteNumber     = ((selectedOctave + 1) * 12 + interval).coerceIn(0, 127)

        // Velocity
        val vel = velocityShaper.next()

        val noteOnMsg = byteArrayOf(
            (0x90 or channel).toByte(),
            noteNumber.toByte(),
            vel.toByte()
        )

        inputPort?.let { port ->
            try { port.send(noteOnMsg, 0, noteOnMsg.size) }
            catch (e: IOException) { Log.e(TAG, "Send error", e) }
        }
        MidiOutputService.getInstance()?.sendMidiToClients(noteOnMsg, 0, noteOnMsg.size, 0)

        currentNoteNumber = noteNumber
        val noteName = noteNumberToName(noteNumber)
        mainHandler.post { listener?.onNotePlayed(noteName, noteNumber, vel) }
    }

    private fun sendNoteOff(noteNumber: Int) {
        val msg = byteArrayOf((0x80 or channel).toByte(), noteNumber.toByte(), 0)
        try { inputPort?.send(msg, 0, msg.size) } catch (e: IOException) { /* ignore */ }
        MidiOutputService.getInstance()?.sendMidiToClients(msg, 0, msg.size, 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rebuildProHelpers() {
        val ps = proSettings
        val scaleSize = scales.getOrNull(selectedScale)?.size ?: 7

        velocityShaper = VelocityShaper(ps.velocityPattern, velocity)
        velocityShaper.reset()

        markovChain = if (ps.markovEnabled) {
            MarkovMelody(scaleSize, ps.melodicLogicStyle).also { it.reset() }
        } else null

        if (ps.euclideanEnabled) {
            euclideanPattern = EuclideanRhythm.generate(
                ps.euclideanSteps.coerceIn(2, 32),
                ps.euclideanDensity.coerceIn(1, ps.euclideanSteps),
                ps.euclideanRotation
            )
            euclideanStep = 0
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

    private fun notifyStatus(s: String)        { mainHandler.post { listener?.onStatusChanged(s) } }
    private fun notifyPlaybackState(p: Boolean) { mainHandler.post { listener?.onPlaybackStateChanged(p) } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIFICATION_ID, createNotification()) }
        catch (e: Exception) { Log.e(TAG, "onStartCommand startForeground failed", e) }
        return START_STICKY
    }

    override fun onDestroy() {
        closeDevice()
        scheduler?.shutdownNow()
        super.onDestroy()
    }
}
