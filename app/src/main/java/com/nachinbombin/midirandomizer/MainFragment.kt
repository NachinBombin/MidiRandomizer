package com.nachinbombin.midirandomizer

import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
 * The original main UI.
 */
class MainFragment : Fragment(), MidiService.MidiEventListener {

    interface MainFragmentHost {
        fun getMidiService(): MidiService?
        fun getMidiManager(): MidiManager?
    }

    private var host: MainFragmentHost? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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

    private var currentParams = MidiService.Voice1Params()
    private var isUpdatingFromSync = false

    private val deviceAdapter by lazy {
        ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
    }
    private val deviceMap = mutableMapOf<String, MidiDeviceInfo>()

    private val scales = listOf(
        "Chromatic","Major","Minor (Natural)","Minor (Harmonic)",
        "Pentatonic Major","Pentatonic Minor","Blues",
        "Dorian","Mixolydian","Whole Tone"
    )

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.addMidiListener(this)
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.removeMidiListener(this)
    }

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
        push()
    }

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
        seekBpm.progress = currentParams.bpm - 20
        tvBpm.text       = getString(R.string.label_bpm, currentParams.bpm)

        seekVelocity.max      = 126
        seekVelocity.progress = currentParams.velocity - 1
        tvVelocity.text       = getString(R.string.label_velocity, currentParams.velocity)

        tvOctave.text = getString(R.string.label_octave_range, currentParams.minOctave, currentParams.maxOctave)

        val chAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            (0..16).map { if (it == 0) "Ch Omni (0)" else getString(R.string.channel_format, it) })
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

    private fun setupListeners() {
        seekBpm.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(bpm = p + 20)
            tvBpm.text = getString(R.string.label_bpm, currentParams.bpm)
            push()
        })
        seekVelocity.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(velocity = p + 1)
            tvVelocity.text = getString(R.string.label_velocity, currentParams.velocity)
            push()
        })
        rangeOctave.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val vals = slider.values
            var low = vals[0].toInt()
            var high = vals[1].toInt()
            if (high - low < 1) {
                if (low > 0) low = high - 1 else high = low + 1
                slider.values = listOf(low.toFloat(), high.toFloat())
            }
            currentParams = currentParams.copy(minOctave = low, maxOctave = high)
            tvOctave.text = getString(R.string.label_octave_range, low, high)
            push()
        }
        rgTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val mode = when (id) {
                R.id.rbMetronome  -> MidiService.TIMING_METRONOME
                R.id.rbMixed      -> MidiService.TIMING_MIXED
                R.id.rbRandomized -> MidiService.TIMING_RANDOMIZED
                R.id.rbEuclidean  -> MidiService.TIMING_EUCLIDEAN
                else              -> MidiService.TIMING_METRONOME
            }
            currentParams = currentParams.copy(timingMode = mode)
            push()
        }
        spinnerChannel.onItemSelectedListener = simpleSpinner { 
            currentParams = currentParams.copy(channel = it)
            push() 
        }
        spinnerScale.onItemSelectedListener   = simpleSpinner { 
            currentParams = currentParams.copy(scale = it)
            push() 
        }
        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val label = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info  = deviceMap[label] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
        btnStartStop.setOnClickListener { host?.getMidiService()?.togglePlayback() }
    }

    private fun push() {
        if (isUpdatingFromSync) return
        host?.getMidiService()?.updateV1Parameters(currentParams)
    }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {
        if (isAdded) tvLastNote.text = getString(R.string.last_note_format, noteName, midiNote, velocity)
    }
    override fun onStatusChanged(status: String) {
        if (isAdded) tvStatus.text = status
    }
    override fun onPlaybackStateChanged(playing: Boolean) {
        if (!isAdded) return
        btnStartStop.text = if (playing) getString(R.string.btn_stop) else getString(R.string.btn_start)
        btnStartStop.backgroundTintList = ColorStateList.valueOf(
            if (playing) 0xFF8B0000.toInt() else 0xFF01696F.toInt()
        )
    }

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded) return
        isUpdatingFromSync = true
        currentParams = v1
        
        seekBpm.progress = v1.bpm - 20
        tvBpm.text = getString(R.string.label_bpm, v1.bpm)
        
        seekVelocity.progress = v1.velocity - 1
        tvVelocity.text = getString(R.string.label_velocity, v1.velocity)
        
        rangeOctave.values = listOf(v1.minOctave.toFloat(), v1.maxOctave.toFloat())
        tvOctave.text = getString(R.string.label_octave_range, v1.minOctave, v1.maxOctave)
        
        rgTiming.check(when(v1.timingMode) {
            MidiService.TIMING_METRONOME -> R.id.rbMetronome
            MidiService.TIMING_MIXED -> R.id.rbMixed
            MidiService.TIMING_RANDOMIZED -> R.id.rbRandomized
            MidiService.TIMING_EUCLIDEAN -> R.id.rbEuclidean
            else -> R.id.rbMetronome
        })
        
        spinnerChannel.setSelection(v1.channel)
        spinnerScale.setSelection(v1.scale)
        
        isUpdatingFromSync = false
    }

    private fun simpleSeek(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { 
                if (fromUser && !isUpdatingFromSync) onChange(p) 
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }

    private fun simpleSpinner(onSelect: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) { 
                if (!isUpdatingFromSync) onSelect(pos) 
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
}
