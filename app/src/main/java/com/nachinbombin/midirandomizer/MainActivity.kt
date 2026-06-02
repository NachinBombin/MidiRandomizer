package com.nachinbombin.midirandomizer

import android.content.pm.PackageManager
import android.media.midi.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var midiManager: MidiManager
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    // UI elements
    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLastNote: TextView
    private lateinit var seekBpm: SeekBar
    private lateinit var tvBpm: TextView
    private lateinit var seekVelocity: SeekBar
    private lateinit var tvVelocity: TextView
    private lateinit var seekOctave: SeekBar
    private lateinit var tvOctave: TextView
    private lateinit var spinnerChannel: Spinner
    private lateinit var spinnerScale: Spinner
    private lateinit var deviceListView: ListView
    private lateinit var tvDeviceInfo: TextView

    private var bpm = 120
    private var velocity = 100
    private var octave = 4
    private var channel = 0
    private var selectedScale = 0

    private val deviceAdapter by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) }
    private val deviceMap = mutableMapOf<String, MidiDeviceInfo>()
    private var selectedDeviceInfo: MidiDeviceInfo? = null

    private val scales = mapOf(
        "Chromatic" to listOf(0,1,2,3,4,5,6,7,8,9,10,11),
        "Major" to listOf(0,2,4,5,7,9,11),
        "Minor (Natural)" to listOf(0,2,3,5,7,8,10),
        "Minor (Harmonic)" to listOf(0,2,3,5,7,8,11),
        "Pentatonic Major" to listOf(0,2,4,7,9),
        "Pentatonic Minor" to listOf(0,3,5,7,10),
        "Blues" to listOf(0,3,5,6,7,10),
        "Dorian" to listOf(0,2,3,5,7,9,10),
        "Mixolydian" to listOf(0,2,4,5,7,9,10),
        "Whole Tone" to listOf(0,2,4,6,8,10)
    )
    private val scaleNames = scales.keys.toList()
    private val scaleIntervals = scales.values.toList()

    private var currentNoteNumber = -1
    private val noteRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            sendRandomNote()
            val intervalMs = (60_000.0 / bpm).toLong()
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupMidi()
        setupListeners()
    }

    private fun bindViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvLastNote = findViewById(R.id.tvLastNote)
        seekBpm = findViewById(R.id.seekBpm)
        tvBpm = findViewById(R.id.tvBpm)
        seekVelocity = findViewById(R.id.seekVelocity)
        tvVelocity = findViewById(R.id.tvVelocity)
        seekOctave = findViewById(R.id.seekOctave)
        tvOctave = findViewById(R.id.tvOctave)
        spinnerChannel = findViewById(R.id.spinnerChannel)
        spinnerScale = findViewById(R.id.spinnerScale)
        deviceListView = findViewById(R.id.listViewDevices)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)

        // BPM seekbar: 20–300
        seekBpm.max = 280
        seekBpm.progress = bpm - 20
        tvBpm.text = "BPM: $bpm"

        // Velocity seekbar: 1–127
        seekVelocity.max = 126
        seekVelocity.progress = velocity - 1
        tvVelocity.text = "Velocity: $velocity"

        // Octave seekbar: 0–8
        seekOctave.max = 8
        seekOctave.progress = octave
        tvOctave.text = "Octave: $octave"

        // Channel spinner 1–16
        val channelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            (1..16).map { "Ch $it" })
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChannel.adapter = channelAdapter

        // Scale spinner
        val scaleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scaleNames)
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerScale.adapter = scaleAdapter

        deviceListView.adapter = deviceAdapter
    }

    private fun setupMidi() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            tvStatus.text = "MIDI not supported on this device"
            btnStartStop.isEnabled = false
            return
        }
        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        refreshDeviceList()
        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) {
                runOnUiThread { refreshDeviceList() }
            }
            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                runOnUiThread {
                    if (selectedDeviceInfo?.id == device.id) {
                        stopPlaying()
                        closeDevice()
                        selectedDeviceInfo = null
                        tvStatus.text = "Device disconnected"
                    }
                    refreshDeviceList()
                }
            }
        }, handler)
    }

    private fun refreshDeviceList() {
        deviceAdapter.clear()
        deviceMap.clear()
        val devices = midiManager.devices
        if (devices.isEmpty()) {
            deviceAdapter.add("No MIDI devices found")
            tvDeviceInfo.text = "Connect a MIDI device or use a MIDI app with virtual ports (e.g. FluidSynth, Caustic)."
            return
        }
        for (info in devices) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "Unknown Device"
            val label = "$name  [${info.inputPortCount} in / ${info.outputPortCount} out]"
            deviceAdapter.add(label)
            deviceMap[label] = info
        }
        tvDeviceInfo.text = "Tap a device to connect."
    }

    private fun setupListeners() {
        seekBpm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                bpm = progress + 20
                tvBpm.text = "BPM: $bpm"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        seekVelocity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                velocity = progress + 1
                tvVelocity.text = "Velocity: $velocity"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        seekOctave.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                octave = progress
                tvOctave.text = "Octave: $octave"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        spinnerChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                channel = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerScale.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedScale = pos
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val label = deviceAdapter.getItem(position) ?: return@setOnItemClickListener
            val info = deviceMap[label] ?: return@setOnItemClickListener
            connectToDevice(info)
        }

        btnStartStop.setOnClickListener {
            if (inputPort == null) {
                Toast.makeText(this, "No MIDI device connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isPlaying) stopPlaying() else startPlaying()
        }
    }

    private fun connectToDevice(info: MidiDeviceInfo) {
        closeDevice()
        selectedDeviceInfo = info
        midiManager.openDevice(info, { device ->
            if (device == null) {
                runOnUiThread { tvStatus.text = "Failed to open device" }
                return@openDevice
            }
            midiDevice = device
            if (info.inputPortCount > 0) {
                inputPort = device.openInputPort(0)
                runOnUiThread {
                    val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Device"
                    tvStatus.text = "Connected: $name"
                    tvDeviceInfo.text = "Ready. Press Start to send random notes."
                }
            } else {
                runOnUiThread { tvStatus.text = "Device has no input ports" }
            }
        }, handler)
    }

    private fun closeDevice() {
        try {
            inputPort?.close()
            midiDevice?.close()
        } catch (e: IOException) {
            // ignore
        }
        inputPort = null
        midiDevice = null
    }

    private fun startPlaying() {
        isPlaying = true
        btnStartStop.text = "STOP"
        tvStatus.text = "Playing…"
        handler.post(noteRunnable)
    }

    private fun stopPlaying() {
        isPlaying = false
        handler.removeCallbacks(noteRunnable)
        // Send note-off for current note just in case
        if (currentNoteNumber >= 0) sendNoteOff(currentNoteNumber)
        currentNoteNumber = -1
        btnStartStop.text = "START"
        tvStatus.text = if (inputPort != null) "Stopped" else "No device connected"
        tvLastNote.text = "Last note: —"
    }

    private fun sendRandomNote() {
        val port = inputPort ?: return
        // Turn off previous note
        if (currentNoteNumber >= 0) sendNoteOff(currentNoteNumber)

        val intervals = scaleIntervals[selectedScale]
        val interval = intervals[Random.nextInt(intervals.size)]
        val noteNumber = (octave + 1) * 12 + interval  // MIDI octave convention
        val clampedNote = noteNumber.coerceIn(0, 127)
        val randomVelocity = if (velocity >= 127) 127 else (velocity - 10).coerceAtLeast(1) + Random.nextInt(20)
        val clampedVelocity = randomVelocity.coerceIn(1, 127)

        val noteOnMsg = byteArrayOf(
            (0x90 or channel).toByte(),
            clampedNote.toByte(),
            clampedVelocity.toByte()
        )
        try {
            port.send(noteOnMsg, 0, noteOnMsg.size)
            currentNoteNumber = clampedNote
            val noteName = noteNumberToName(clampedNote)
            runOnUiThread { tvLastNote.text = "Last note: $noteName (MIDI $clampedNote, vel $clampedVelocity)" }
        } catch (e: IOException) {
            runOnUiThread { tvStatus.text = "Send error: ${e.message}" }
        }
    }

    private fun sendNoteOff(noteNumber: Int) {
        val port = inputPort ?: return
        val noteOffMsg = byteArrayOf(
            (0x80 or channel).toByte(),
            noteNumber.toByte(),
            0
        )
        try { port.send(noteOffMsg, 0, noteOffMsg.size) } catch (e: IOException) { /* ignore */ }
    }

    private fun noteNumberToName(midiNote: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val oct = (midiNote / 12) - 1
        val name = noteNames[midiNote % 12]
        return "$name$oct"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlaying()
        closeDevice()
    }
}
