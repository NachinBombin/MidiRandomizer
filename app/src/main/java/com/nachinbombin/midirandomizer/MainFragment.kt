package com.nachinbombin.midirandomizer

import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.RangeSlider

/**
 * The original main UI, now living in its own Fragment so the Activity
 * can host it alongside ProSettingsFragment in a ViewPager2.
 */
class MainFragment : Fragment(), MidiService.MidiEventListener {

    // Callback into the Activity to reach the bound service
    interface MainFragmentHost {
        fun getMidiService(): MidiService?
        fun getMidiManager(): MidiManager?
    }

    private var host: MainFragmentHost? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var btnStartStop:  Button
    private lateinit var tvStatus:      TextView
    private lateinit var tvLastNote:    TextView
    private lateinit var seekBpm:       SeekBar
    private lateinit var tvBpm:         TextView
    private lateinit var seekVelocity:  SeekBar
    private lateinit var tvVelocity:    TextView
    private lateinit var tvOctave:      TextView
    private lateinit var rangeOctave:   RangeSlider
    private lateinit var rgTiming:      RadioGroup
    private lateinit var spinnerChannel: Spinner
    private lateinit var spinnerScale:  Spinner
    private lateinit var deviceListView: ListView
    private lateinit var tvDeviceInfo:  TextView

    // ── State ─────────────────────────────────────────────────────────────────
    private var bpm           = 120
    private var velocity      = 100
    private var minOctave     = 3
    private var maxOctave     = 5
    private var channel       = 0
    private var selectedScale = 0
    private var timingMode    = MidiService.TIMING_METRONOME

    private val deviceAdapter by lazy {
        ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
    }
    private val deviceMap = mutableMapOf<String, MidiDeviceInfo>()

    private val scales = listOf(
        "Chromatic","Major","Minor (Natural)","Minor (Harmonic)",
        "Pentatonic Major","Pentatonic Minor","Blues",
        "Dorian","Mixolydian","Whole Tone"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        host = activity as? MainFragmentHost
        bindViews(view)
        if (!requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            tvStatus.text = getString(R.string.midi_not_supported)
            btnStartStop.isEnabled = false
        }
        refreshDeviceList()
        setupListeners()
    }

    fun onServiceReady() {
        host?.getMidiService()?.setListener(this)
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        btnStartStop   = v.findViewById(R.id.btnStartStop)
        tvStatus       = v.findViewById(R.id.tvStatus)
        tvLastNote     = v.findViewById(R.id.tvLastNote)
        seekBpm        = v.findViewById(R.id.seekBpm)
        tvBpm          = v.findViewById(R.id.tvBpm)
        seekVelocity   = v.findViewById(R.id.seekVelocity)
        tvVelocity     = v.findViewById(R.id.tvVelocity)
        tvOctave       = v.findViewById(R.id.tvOctave)
        rangeOctave    = v.findViewById(R.id.rangeOctave)
        rgTiming       = v.findViewById(R.id.rgTiming)
        spinnerChannel = v.findViewById(R.id.spinnerChannel)
        spinnerScale   = v.findViewById(R.id.spinnerScale)
        deviceListView = v.findViewById(R.id.listViewDevices)
        tvDeviceInfo   = v.findViewById(R.id.tvDeviceInfo)

        seekBpm.max      = 280
        seekBpm.progress = bpm - 20
        tvBpm.text       = getString(R.string.label_bpm, bpm)

        seekVelocity.max      = 126
        seekVelocity.progress = velocity - 1
        tvVelocity.text       = getString(R.string.label_velocity, velocity)

        tvOctave.text = getString(R.string.label_octave_range, minOctave, maxOctave)

        val chAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            (1..16).map { getString(R.string.channel_format, it) })
        chAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerChannel.adapter = chAdapter

        val scaleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, scales)
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerScale.adapter = scaleAdapter

        deviceListView.adapter = deviceAdapter
    }

    fun refreshDeviceList() {
        val mm = host?.getMidiManager() ?: return
        deviceAdapter.clear()
        deviceMap.clear()
        val devices = mm.devices
        if (devices.isEmpty()) {
            deviceAdapter.add(getString(R.string.no_devices_found))
            tvDeviceInfo.text = getString(R.string.connect_device_hint)
            return
        }
        for (info in devices) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "Unknown Device"
            val label = "$name  [${info.inputPortCount}↓ ${info.outputPortCount}↑]"
            deviceAdapter.add(label)
            deviceMap[label] = info
        }
        tvDeviceInfo.text = getString(R.string.tap_to_connect)
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        seekBpm.setOnSeekBarChangeListener(simpleSeek { p ->
            bpm = p + 20; tvBpm.text = getString(R.string.label_bpm, bpm); push()
        })
        seekVelocity.setOnSeekBarChangeListener(simpleSeek { p ->
            velocity = p + 1; tvVelocity.text = getString(R.string.label_velocity, velocity); push()
        })
        rangeOctave.addOnChangeListener { slider, _, _ ->
            val vals = slider.values
            minOctave = vals[0].toInt(); maxOctave = vals[1].toInt()
            tvOctave.text = getString(R.string.label_octave_range, minOctave, maxOctave)
            push()
        }
        rgTiming.setOnCheckedChangeListener { _, id ->
            timingMode = when (id) {
                R.id.rbMetronome  -> MidiService.TIMING_METRONOME
                R.id.rbMixed      -> MidiService.TIMING_MIXED
                R.id.rbRandomized -> MidiService.TIMING_RANDOMIZED
                R.id.rbEuclidean  -> MidiService.TIMING_EUCLIDEAN
                else              -> MidiService.TIMING_METRONOME
            }
            push()
        }
        spinnerChannel.onItemSelectedListener = simpleSpinner { channel = it; push() }
        spinnerScale.onItemSelectedListener   = simpleSpinner { selectedScale = it; push() }
        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val label = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info  = deviceMap[label] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
        btnStartStop.setOnClickListener { host?.getMidiService()?.togglePlayback() }
    }

    private fun push() {
        host?.getMidiService()?.updateParameters(
            bpm, velocity, minOctave, maxOctave, channel, selectedScale, timingMode
        )
    }

    // ── MidiEventListener ─────────────────────────────────────────────────────

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {
        mainHandler.post {
            if (isAdded) tvLastNote.text = getString(R.string.last_note_format, noteName, midiNote, velocity)
        }
    }
    override fun onStatusChanged(status: String) {
        mainHandler.post { if (isAdded) tvStatus.text = status }
    }
    override fun onPlaybackStateChanged(playing: Boolean) {
        mainHandler.post {
            if (!isAdded) return@post
            btnStartStop.text = if (playing) getString(R.string.btn_stop) else getString(R.string.btn_start)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun simpleSeek(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { onChange(p) }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }

    private fun simpleSpinner(onSelect: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
}
