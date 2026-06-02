package com.nachinbombin.midirandomizer

import android.app.*
import android.content.Context
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
        
        const val TIMING_METRONOME = 0
        const val TIMING_MIXED = 1
        const val TIMING_RANDOMIZED = 2
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduler: ExecutorService? = null
    
    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    
    @Volatile
    private var isPlaying = false
    private var bpm = 120
    private var velocity = 100
    private var minOctave = 3
    private var maxOctave = 5
    private var channel = 0
    private var selectedScale = 0
    private var timingMode = TIMING_METRONOME
    private var currentNoteNumber = -1

    private val scales = mapOf(
        "Chromatic" to listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        "Major" to listOf(0, 2, 4, 5, 7, 9, 11),
        "Minor (Natural)" to listOf(0, 2, 3, 5, 7, 8, 10),
        "Minor (Harmonic)" to listOf(0, 2, 3, 5, 7, 8, 11),
        "Pentatonic Major" to listOf(0, 2, 4, 7, 9),
        "Pentatonic Minor" to listOf(0, 3, 5, 7, 10),
        "Blues" to listOf(0, 3, 5, 6, 7, 10),
        "Dorian" to listOf(0, 2, 3, 5, 7, 9, 10),
        "Mixolydian" to listOf(0, 2, 4, 5, 7, 9, 10),
        "Whole Tone" to listOf(0, 2, 4, 6, 8, 10),
    )
    private val scaleIntervals = scales.values.toList()

    interface MidiEventListener {
        fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int)
        fun onStatusChanged(status: String)
        fun onPlaybackStateChanged(playing: Boolean)
    }

    private var listener: MidiEventListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): MidiService = this@MidiService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        createNotificationChannel()
    }

    fun setListener(listener: MidiEventListener?) {
        this.listener = listener
        // Notify current state to new listener
        listener?.onPlaybackStateChanged(isPlaying)
    }

    fun updateParameters(bpm: Int, velocity: Int, minOct: Int, maxOct: Int, chan: Int, scale: Int, timing: Int) {
        this.bpm = bpm
        this.velocity = velocity
        this.minOctave = minOct
        this.maxOctave = maxOct
        this.channel = chan
        this.selectedScale = scale
        this.timingMode = timing
    }

    fun connectToDevice(info: MidiDeviceInfo) {
        if (midiDevice?.info?.id == info.id) {
            Log.d(TAG, "Already connected to device: ${info.id}")
            return
        }
        closeDevice()
        Log.d(TAG, "Connecting to device: ${info.id}")
        midiManager?.openDevice(info, { device ->
            if (device == null) {
                Log.e(TAG, "Failed to open device: ${info.id}")
                notifyStatus("Failed to open device")
                return@openDevice
            }
            midiDevice = device
            Log.d(TAG, "Device opened: ${info.id}. Input ports: ${info.inputPortCount}")
            if (info.inputPortCount > 0) {
                try {
                    inputPort = device.openInputPort(0)
                    if (inputPort == null) {
                        Log.e(TAG, "Failed to open input port 0 on device: ${info.id}")
                        notifyStatus("Failed to open input port")
                        return@openDevice
                    }
                    val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Device"
                    notifyStatus("Connected: $name")
                    Log.d(TAG, "Successfully connected to $name")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception opening port", e)
                    notifyStatus("Error opening port: ${e.message}")
                    closeDevice()
                }
            } else {
                Log.w(TAG, "Device has no input ports: ${info.id}")
                notifyStatus("Device has no input ports")
            }
        }, mainHandler)
    }

    private fun closeDevice() {
        stopPlaying()
        try {
            inputPort?.close()
            midiDevice?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing device", e)
        }
        inputPort = null
        midiDevice = null
    }

    fun togglePlayback() {
        Log.d(TAG, "togglePlayback: isPlaying=$isPlaying, inputPort=${inputPort != null}")
        if (isPlaying) {
            stopPlaying()
        } else {
            startPlaying()
        }
    }

    private fun startPlaying() {
        if (isPlaying) return
        isPlaying = true
        Log.i(TAG, "Starting playback")
        
        // Ensure we are in foreground
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure foreground service", e)
        }
        
        scheduler = Executors.newSingleThreadExecutor()
        scheduler?.execute(noteLoop)
        
        notifyPlaybackState(true)
    }

    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false
        
        scheduler?.shutdownNow()
        try {
            scheduler?.awaitTermination(500, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        scheduler = null

        if (currentNoteNumber >= 0) {
            sendNoteOff(currentNoteNumber)
            currentNoteNumber = -1
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyPlaybackState(false)
    }

    private val noteLoop = Runnable {
        while (isPlaying) {
            sendRandomNote()
            try {
                val intervalMs = calculateInterval()
                Thread.sleep(intervalMs)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun calculateInterval(): Long {
        val baseInterval = (60_000.0 / bpm).toLong()
        return when (timingMode) {
            TIMING_METRONOME -> baseInterval
            TIMING_MIXED -> {
                // 30% chance of a flurry/grace note (short delay)
                if (Random.nextFloat() < 0.3f) {
                    baseInterval / 2
                } else {
                    baseInterval
                }
            }
            TIMING_RANDOMIZED -> {
                // Range from 50% to 150% of base interval
                (baseInterval * (0.5 + Random.nextDouble())).toLong()
            }
            else -> baseInterval
        }
    }

    private fun sendRandomNote() {
        if (currentNoteNumber >= 0) sendNoteOff(currentNoteNumber)

        val intervals = scaleIntervals.getOrNull(selectedScale) ?: return
        val interval = intervals[Random.nextInt(intervals.size)]
        
        // Pick a random octave within the range
        val range = maxOctave - minOctave + 1
        val selectedOctave = minOctave + Random.nextInt(range)
        
        val noteNumber = (selectedOctave + 1) * 12 + interval
        val clampedNote = noteNumber.coerceIn(0, 127)
        val randomVelocity = if (velocity >= 127) 127 else (velocity - 10).coerceAtLeast(1) + Random.nextInt(20)
        val clampedVelocity = randomVelocity.coerceIn(1, 127)

        val noteOnMsg = byteArrayOf(
            (0x90 or channel).toByte(),
            clampedNote.toByte(),
            clampedVelocity.toByte()
        )

        // Send to physical/external port if connected
        inputPort?.let { port ->
            try {
                port.send(noteOnMsg, 0, noteOnMsg.size)
            } catch (e: IOException) {
                Log.e(TAG, "IOException in sendRandomNote (physical port)", e)
            }
        }

        // Always send to virtual port if it exists
        MidiOutputService.getInstance()?.sendMidiToClients(noteOnMsg, 0, noteOnMsg.size, 0)

        currentNoteNumber = clampedNote
        val noteName = noteNumberToName(clampedNote)
        mainHandler.post { listener?.onNotePlayed(noteName, clampedNote, clampedVelocity) }
        Log.v(TAG, "Sent Note ON: $clampedNote to ${if (inputPort != null) "physical" else "virtual only"}")
    }

    private fun sendNoteOff(noteNumber: Int) {
        val noteOffMsg = byteArrayOf(
            (0x80 or channel).toByte(),
            noteNumber.toByte(),
            0
        )

        inputPort?.let { port ->
            try {
                port.send(noteOffMsg, 0, noteOffMsg.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error sending note off (physical port)", e)
            }
        }

        MidiOutputService.getInstance()?.sendMidiToClients(noteOffMsg, 0, noteOffMsg.size, 0)
        Log.v(TAG, "Sent Note OFF: $noteNumber")
    }

    private fun noteNumberToName(midiNote: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val oct = (midiNote / 12) - 1
        val name = noteNames[midiNote % 12]
        return "$name$oct"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MIDI Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MIDI Randomizer")
            .setContentText("Generating notes...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(status: String) {
        mainHandler.post { listener?.onStatusChanged(status) }
    }

    private fun notifyPlaybackState(playing: Boolean) {
        mainHandler.post { listener?.onPlaybackStateChanged(playing) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // We must call startForeground as soon as possible if we were started with startForegroundService
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand startForeground", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        closeDevice()
        scheduler?.shutdownNow()
        super.onDestroy()
    }
}
