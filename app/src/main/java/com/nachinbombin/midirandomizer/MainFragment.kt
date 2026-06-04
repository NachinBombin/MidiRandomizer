package com.nachinbombin.midirandomizer

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.RangeSlider

class MainFragment : Fragment(), MidiService.MidiEventListener {

    interface MainFragmentHost {
        fun getMidiService(): MidiService?
        fun getMidiManager(): MidiManager?
    }

    private var host: MainFragmentHost? = null

    private lateinit var btnStartStop:   Button
    private lateinit var btnTheme:       Button
    private lateinit var tvStatus:       TextView
    private lateinit var tvLastNote:     TextView
    private lateinit var seekBpm:        SeekBar
    private lateinit var tvBpm:          TextView
    private lateinit var seekVelocity:   SeekBar
    private lateinit var tvVelocity:     TextView
    private lateinit var tvOctave:       TextView
    private lateinit var rangeOctave:    RangeSlider
    private lateinit var rgTiming:       RadioGroup
    private lateinit var spinnerChannel: Spinner
    private lateinit var spinnerScale:   Spinner
    private lateinit var spinnerStyle:   Spinner
    private lateinit var layoutDroneTiming: View
    private lateinit var layoutDroneRange:  View
    private lateinit var tvDroneRange:   TextView
    private lateinit var rangeDroneBeats: RangeSlider
    private lateinit var rgDroneTiming:  RadioGroup
    private lateinit var deviceListView: ListView
    private lateinit var tvDeviceInfo:   TextView

    private lateinit var rgRootRow1: RadioGroup
    private lateinit var rgRootRow2: RadioGroup
    private lateinit var rgRootFree: RadioGroup

    private var currentParams      = MidiService.Voice1Params()
    private var isUpdatingFromSync = false

    private val deviceAdapter by lazy {
        ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
    }
    private val deviceMap = mutableMapOf<String, MidiDeviceInfo>()

    private val scales = listOf(
        "Chromatic", "Major", "Minor (Natural)", "Minor (Harmonic)",
        "Pentatonic Major", "Pentatonic Minor", "Blues", "Dorian",
        "Mixolydian", "Whole Tone", "Kurd (Annaziska / Aeolian)",
        "Celtic Minor (Amara)", "Pygmy", "SaBye / SaByeD",
        "Aegean (Lydian)", "Hijaz", "Akebono"
    )
    private val styles = listOf("Generative", "Single note Drone", "Evolving Drone")

    // ── Root note helpers ──────────────────────────────────────────────────────

    private fun selectedRootTag(): Int {
        val r1id = rgRootRow1.checkedRadioButtonId
        if (r1id != -1) {
            val tag = requireView().findViewById<RadioButton>(r1id).tag as? String
            tag?.toIntOrNull()?.let { return it }
        }
        val r2id = rgRootRow2.checkedRadioButtonId
        if (r2id != -1) {
            val tag = requireView().findViewById<RadioButton>(r2id).tag as? String
            tag?.toIntOrNull()?.let { return it }
        }
        return -1
    }

    private fun selectRoot(semitone: Int) {
        isUpdatingFromSync = true
        when {
            semitone in 0..5 -> {
                rgRootRow2.clearCheck(); rgRootFree.clearCheck()
                rgRootRow1.findViewWithTag<RadioButton>(semitone.toString())?.isChecked = true
            }
            semitone in 6..11 -> {
                rgRootRow1.clearCheck(); rgRootFree.clearCheck()
                rgRootRow2.findViewWithTag<RadioButton>(semitone.toString())?.isChecked = true
            }
            else -> {
                rgRootRow1.clearCheck(); rgRootRow2.clearCheck()
                rgRootFree.check(R.id.rbRootFree)
            }
        }
        isUpdatingFromSync = false
    }

    // ── Fragment lifecycle ──────────────────────────────────────────────────────

    override fun onStart() { super.onStart(); (activity as? MainActivity)?.addMidiListener(this) }
    override fun onStop()  { super.onStop();  (activity as? MainActivity)?.removeMidiListener(this) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        host = activity as? MainFragmentHost
        bindViews(view)
        ThemeManager.applyToView(view, ThemeManager.loadTheme(requireContext()))
        if (!requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            tvStatus.text = getString(R.string.midi_not_supported)
            btnStartStop.isEnabled = false
        }
        refreshDeviceList()
        setupListeners()
    }

    private fun bindViews(v: View) {
        btnStartStop      = v.findViewById(R.id.btnStartStop)
        btnTheme          = v.findViewById(R.id.btnTheme)
        tvStatus          = v.findViewById(R.id.tvStatus)
        tvLastNote        = v.findViewById(R.id.tvLastNote)
        seekBpm           = v.findViewById(R.id.seekBpm)
        tvBpm             = v.findViewById(R.id.tvBpm)
        seekVelocity      = v.findViewById(R.id.seekVelocity)
        tvVelocity        = v.findViewById(R.id.tvVelocity)
        tvOctave          = v.findViewById(R.id.tvOctave)
        rangeOctave       = v.findViewById(R.id.rangeOctave)
        rgTiming          = v.findViewById(R.id.rgTiming)
        spinnerChannel    = v.findViewById(R.id.spinnerChannel)
        spinnerScale      = v.findViewById(R.id.spinnerScale)
        spinnerStyle      = v.findViewById(R.id.spinnerStyle)
        layoutDroneTiming = v.findViewById(R.id.layoutDroneTiming)
        layoutDroneRange  = v.findViewById(R.id.layoutDroneRange)
        tvDroneRange      = v.findViewById(R.id.tvDroneRange)
        rangeDroneBeats   = v.findViewById(R.id.rangeDroneBeats)
        rgDroneTiming     = v.findViewById(R.id.rgDroneTiming)
        deviceListView    = v.findViewById(R.id.listViewDevices)
        tvDeviceInfo      = v.findViewById(R.id.tvDeviceInfo)
        rgRootRow1        = v.findViewById(R.id.rgRootRow1)
        rgRootRow2        = v.findViewById(R.id.rgRootRow2)
        rgRootFree        = v.findViewById(R.id.rgRootFree)

        seekBpm.max      = 280
        seekBpm.progress = currentParams.bpm - 20
        tvBpm.text       = getString(R.string.label_bpm, currentParams.bpm)

        seekVelocity.max      = 126
        seekVelocity.progress = currentParams.velocity - 1
        tvVelocity.text       = getString(R.string.label_velocity, currentParams.velocity)

        tvOctave.text = getString(R.string.label_octave_range, currentParams.minOctave, currentParams.maxOctave)

        val channels = (0..16).map { if (it == 0) "Ch Omni (0)" else getString(R.string.channel_format, it) }
        spinnerChannel.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, channels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerScale.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, scales
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerStyle.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, styles
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        deviceListView.adapter = deviceAdapter
        selectRoot(-1)
    }

    fun refreshDeviceList() {
        val mm = host?.getMidiManager() ?: return
        deviceAdapter.clear()
        deviceMap.clear()
        @Suppress("DEPRECATION")
        val devices = mm.devices
        if (devices.isEmpty()) {
            deviceAdapter.add(getString(R.string.no_devices_found))
            tvDeviceInfo.text = getString(R.string.connect_device_hint)
            return
        }
        for (info in devices) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "Unknown Device"
            val label = "$name  [${info.inputPortCount}\u2193 ${info.outputPortCount}\u2191]"
            deviceAdapter.add(label)
            deviceMap[label] = info
        }
        tvDeviceInfo.text = getString(R.string.tap_to_connect)
    }

    private fun setupListeners() {
        // ── Theme picker ─────────────────────────────────────────────────
        btnTheme.setOnClickListener {
            val current = ThemeManager.loadTheme(requireContext())
            ThemePickerDialog.show(requireContext(), current) { preset ->
                ThemeManager.saveTheme(requireContext(), preset)
                ThemeManager.applyToView(requireView(), preset)
            }
        }

        // ── BPM / Velocity / Octave ──────────────────────────────────────────
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
            currentParams = currentParams.copy(minOctave = vals[0].toInt(), maxOctave = vals[1].toInt())
            tvOctave.text = getString(R.string.label_octave_range, vals[0].toInt(), vals[1].toInt())
            push()
        }

        // ── Timing ────────────────────────────────────────────────────────
        rgTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            currentParams = currentParams.copy(timingMode = when (id) {
                R.id.rbMetronome  -> MidiService.TIMING_METRONOME
                R.id.rbMixed      -> MidiService.TIMING_MIXED
                R.id.rbRandomized -> MidiService.TIMING_RANDOMIZED
                R.id.rbEuclidean  -> MidiService.TIMING_EUCLIDEAN
                else              -> MidiService.TIMING_METRONOME
            })
            push()
        }

        // ── Spinners ─────────────────────────────────────────────────────
        spinnerChannel.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(channel = pos); push()
        }
        spinnerScale.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(scale = pos); push()
        }
        spinnerStyle.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(style = VoiceStyle.entries[pos])
            updateUiVisibility(); push()
        }

        // ── Drone timing ────────────────────────────────────────────────
        rgDroneTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            currentParams = currentParams.copy(
                droneTiming = if (id == R.id.rbDroneRandom) DroneTimingMode.RANDOM else DroneTimingMode.CONSTANT
            )
            updateUiVisibility(); push()
        }
        rangeDroneBeats.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val vals = slider.values
            currentParams = currentParams.copy(droneMinBeats = vals[0].toInt(), droneMaxBeats = vals[1].toInt())
            tvDroneRange.text = "Drone beat range: ${currentParams.droneMinBeats} - ${currentParams.droneMaxBeats}"
            push()
        }

        // ── Root note ────────────────────────────────────────────────────
        val rootRowListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (isUpdatingFromSync || checkedId == -1) return@OnCheckedChangeListener
            isUpdatingFromSync = true
            if (group.id == R.id.rgRootRow1) { rgRootRow2.clearCheck(); rgRootFree.clearCheck() }
            else { rgRootRow1.clearCheck(); rgRootFree.clearCheck() }
            isUpdatingFromSync = false
            currentParams = currentParams.copy(rootNote = selectedRootTag() + 1)
            push()
        }
        rgRootRow1.setOnCheckedChangeListener(rootRowListener)
        rgRootRow2.setOnCheckedChangeListener(rootRowListener)
        rgRootFree.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingFromSync || checkedId == -1) return@setOnCheckedChangeListener
            isUpdatingFromSync = true
            rgRootRow1.clearCheck(); rgRootRow2.clearCheck()
            isUpdatingFromSync = false
            currentParams = currentParams.copy(rootNote = 0)
            push()
        }

        // ── Device list + Start/Stop ───────────────────────────────────────
        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val label = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info  = deviceMap[label] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
        btnStartStop.setOnClickListener { host?.getMidiService()?.togglePlayback() }
    }

    private fun updateUiVisibility() {
        if (view == null) return
        val style       = currentParams.style
        val isSingle    = style == VoiceStyle.SINGLE_NOTE_DRONE
        val isEvolving  = style == VoiceStyle.EVOLVING_DRONE
        val isRandDrone = isEvolving && rgDroneTiming.checkedRadioButtonId == R.id.rbDroneRandom

        seekBpm.visibility    = if (isSingle) View.GONE else View.VISIBLE
        tvBpm.visibility      = if (isSingle) View.GONE else View.VISIBLE
        rgTiming.visibility   = if (isSingle || isEvolving) View.GONE else View.VISIBLE
        layoutDroneTiming.visibility = if (isEvolving) View.VISIBLE else View.GONE
        layoutDroneRange.visibility  = if (isRandDrone) View.VISIBLE else View.GONE
    }

    private fun push() {
        if (!isUpdatingFromSync) host?.getMidiService()?.updateV1Parameters(currentParams)
    }

    // ── MidiEventListener ────────────────────────────────────────────────────

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
        if (!isAdded || view == null) return
        isUpdatingFromSync = true
        currentParams = v1

        seekBpm.progress = v1.bpm - 20
        tvBpm.text = getString(R.string.label_bpm, v1.bpm)
        seekVelocity.progress = v1.velocity - 1
        tvVelocity.text = getString(R.string.label_velocity, v1.velocity)
        rangeOctave.values = listOf(v1.minOctave.toFloat(), v1.maxOctave.toFloat())
        tvOctave.text = getString(R.string.label_octave_range, v1.minOctave, v1.maxOctave)

        rgTiming.check(when (v1.timingMode) {
            MidiService.TIMING_METRONOME  -> R.id.rbMetronome
            MidiService.TIMING_MIXED      -> R.id.rbMixed
            MidiService.TIMING_RANDOMIZED -> R.id.rbRandomized
            MidiService.TIMING_EUCLIDEAN  -> R.id.rbEuclidean
            else -> R.id.rbMetronome
        })
        spinnerChannel.setSelection(v1.channel)
        spinnerScale.setSelection(v1.scale)
        spinnerStyle.setSelection(v1.style.ordinal)
        rgDroneTiming.check(if (v1.droneTiming == DroneTimingMode.RANDOM) R.id.rbDroneRandom else R.id.rbDroneConstant)
        rangeDroneBeats.values = listOf(v1.droneMinBeats.toFloat(), v1.droneMaxBeats.toFloat())
        tvDroneRange.text = "Drone beat range: ${v1.droneMinBeats} - ${v1.droneMaxBeats}"
        if (v1.rootNote != 0) selectRoot(v1.rootNote - 1) else selectRoot(-1)
        updateUiVisibility()
        isUpdatingFromSync = false
    }

    fun updateDeviceList(devices: List<MidiDeviceInfo>) {
        if (!isAdded) return
        deviceAdapter.clear(); deviceMap.clear()
        devices.forEach { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device"
            deviceAdapter.add(name)
            deviceMap[name] = info
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun simpleSeek(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) block(progress) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
    private fun simpleSpinner(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) { if (!isUpdatingFromSync) block(pos) }
        override fun onNothingSelected(p: AdapterView<*>) {}
    }
}
