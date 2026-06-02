package com.nachinbombin.midirandomizer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), MidiService.MidiEventListener {

    private lateinit var midiManager: MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

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

    private val scales = listOf(
        "Chromatic", "Major", "Minor (Natural)", "Minor (Harmonic)",
        "Pentatonic Major", "Pentatonic Minor", "Blues",
        "Dorian", "Mixolydian", "Whole Tone"
    )

    private var midiService: MidiService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MidiService.LocalBinder
            midiService = binder.getService()
            midiService?.setListener(this@MainActivity)
            isBound = true
            updateServiceParams()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            midiService?.setListener(null)
            midiService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMidiService()
        } else {
            Toast.makeText(this, "Notification permission required for background playback", Toast.LENGTH_LONG).show()
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            refreshDeviceList()
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (selectedDeviceInfo?.id == device.id) {
                selectedDeviceInfo = null
                tvStatus.text = getString(R.string.status_disconnected)
            }
            refreshDeviceList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupMidi()
        setupListeners()

        checkPermissionsAndStartService()
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startMidiService()
            }
        } else {
            startMidiService()
        }
    }

    private fun startMidiService() {
        val intent = Intent(this, MidiService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, BIND_AUTO_CREATE)
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

        seekBpm.max = 280
        seekBpm.progress = bpm - 20
        tvBpm.text = getString(R.string.label_bpm, bpm)

        seekVelocity.max = 126
        seekVelocity.progress = velocity - 1
        tvVelocity.text = getString(R.string.label_velocity, velocity)

        seekOctave.max = 8
        seekOctave.progress = octave
        tvOctave.text = getString(R.string.label_octave, octave)

        val channelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            (1..16).map { getString(R.string.channel_format, it) })
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChannel.adapter = channelAdapter

        val scaleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scales)
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerScale.adapter = scaleAdapter

        deviceListView.adapter = deviceAdapter
    }

    private fun setupMidi() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            tvStatus.text = getString(R.string.midi_not_supported)
            btnStartStop.isEnabled = false
            return
        }
        midiManager = getSystemService(MIDI_SERVICE) as MidiManager
        refreshDeviceList()
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
    }

    private fun refreshDeviceList() {
        deviceAdapter.clear()
        deviceMap.clear()
        val devices = midiManager.devices
        if (devices.isEmpty()) {
            deviceAdapter.add(getString(R.string.no_devices_found))
            tvDeviceInfo.text = getString(R.string.connect_device_hint)
            return
        }
        for (info in devices) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "Unknown Device"
            val label = "$name  [${info.inputPortCount} in / ${info.outputPortCount} out]"
            deviceAdapter.add(label)
            deviceMap[label] = info
        }
        tvDeviceInfo.text = getString(R.string.tap_to_connect)
    }

    private fun setupListeners() {
        seekBpm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                bpm = progress + 20
                tvBpm.text = getString(R.string.label_bpm, bpm)
                updateServiceParams()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        seekVelocity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                velocity = progress + 1
                tvVelocity.text = getString(R.string.label_velocity, velocity)
                updateServiceParams()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        seekOctave.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                octave = progress
                tvOctave.text = getString(R.string.label_octave, octave)
                updateServiceParams()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        spinnerChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                channel = pos
                updateServiceParams()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerScale.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedScale = pos
                updateServiceParams()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val label = deviceAdapter.getItem(position) ?: return@setOnItemClickListener
            val info = deviceMap[label] ?: return@setOnItemClickListener
            selectedDeviceInfo = info
            midiService?.connectToDevice(info)
        }

        btnStartStop.setOnClickListener {
            if (midiService == null) {
                Toast.makeText(this, R.string.service_not_ready, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            midiService?.togglePlayback()
        }
    }

    private fun updateServiceParams() {
        midiService?.updateParameters(bpm, velocity, octave, channel, selectedScale)
    }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {
        mainHandler.post {
            tvLastNote.text = getString(R.string.last_note_format, noteName, midiNote, velocity)
        }
    }

    override fun onStatusChanged(status: String) {
        mainHandler.post {
            tvStatus.text = status
        }
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        mainHandler.post {
            btnStartStop.text = if (playing) getString(R.string.btn_stop) else getString(R.string.btn_start)
            tvStatus.text = if (playing) getString(R.string.status_playing) else getString(R.string.status_stopped)
        }
    }

    override fun onDestroy() {
        if (isBound) {
            midiService?.setListener(null)
            unbindService(connection)
            isBound = false
        }
        midiManager.unregisterDeviceCallback(deviceCallback)
        super.onDestroy()
    }
}
