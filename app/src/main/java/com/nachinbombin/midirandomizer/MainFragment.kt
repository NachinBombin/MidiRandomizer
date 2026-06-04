package com.nachinbombin.midirandomizer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.RangeSlider
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager

class MainFragment : Fragment(), MidiService.MidiEventListener {

    interface MainFragmentHost {
        fun getMidiService(): MidiService?
        fun getMidiManager(): MidiManager?
    }

    private var host: MainFragmentHost? = null

    private lateinit var tvStatus:       TextView
    private lateinit var tvLastNote:     TextView
    private lateinit var seekBpm:        SeekBar
    private lateinit var tvBpm:          TextView
    private lateinit var seekVelocity:   SeekBar
    private lateinit var tvVelocity:     TextView
    private lateinit var rangeOctave:    RangeSlider
    private lateinit var tvOctave:       TextView
    private lateinit var rgTiming:       RadioGroup
    private lateinit var spinnerChannel: Spinner
    private lateinit var spinnerScale:   Spinner
    private lateinit var spinnerStyle:   Spinner
    private lateinit var btnStartStop:   Button
    private lateinit var deviceListView: ListView
    private lateinit var rgRootRow1:     RadioGroup
    private lateinit var rgRootRow2:     RadioGroup
    private lateinit var rgRootFree:     RadioGroup
    private lateinit var layoutDroneTiming: View
    private lateinit var layoutDroneRange:  View
    private lateinit var rgDroneTiming:     RadioGroup
    private lateinit var rangeDroneBeats:   RangeSlider
    private lateinit var tvDroneRange:      TextView

    private var deviceAdapter = ArrayAdapter<String>(android.app.Application(), android.R.layout.simple_list_item_1)
    private val deviceMap = mutableMapOf<String, MidiDeviceInfo>()

    @Volatile private var currentParams = MidiService.Voice1Params()
    @Volatile private var isUpdatingFromSync = false

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        host = context as? MainFragmentHost
    }

    override fun onDetach() {
        super.onDetach()
        host = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus       = view.findViewById(R.id.tvStatus)
        tvLastNote     = view.findViewById(R.id.tvLastNote)
        seekBpm        = view.findViewById(R.id.seekBpm)
        tvBpm          = view.findViewById(R.id.tvBpm)
        seekVelocity   = view.findViewById(R.id.seekVelocity)
        tvVelocity     = view.findViewById(R.id.tvVelocity)
        rangeOctave    = view.findViewById(R.id.rangeOctave)
        tvOctave       = view.findViewById(R.id.tvOctave)
        rgTiming       = view.findViewById(R.id.rgTiming)
        spinnerChannel = view.findViewById(R.id.spinnerChannel)
        spinnerScale   = view.findViewById(R.id.spinnerScale)
        spinnerStyle   = view.findViewById(R.id.spinnerStyle)
        btnStartStop   = view.findViewById(R.id.btnStartStop)
        deviceListView = view.findViewById(R.id.listViewDevices)
        rgRootRow1     = view.findViewById(R.id.rgRootRow1)
        rgRootRow2     = view.findViewById(R.id.rgRootRow2)
        rgRootFree     = view.findViewById(R.id.rgRootFree)
        layoutDroneTiming = view.findViewById(R.id.layoutDroneTiming)
        layoutDroneRange  = view.findViewById(R.id.layoutDroneRange)
        rgDroneTiming     = view.findViewById(R.id.rgDroneTiming)
        rangeDroneBeats   = view.findViewById(R.id.rangeDroneBeats)
        tvDroneRange      = view.findViewById(R.id.tvDroneRange)

        deviceListView.adapter = deviceAdapter

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
            val low  = vals[0].toInt()
            val high = vals[1].toInt()
            // Allow low == high (single octave selection) — no minimum gap enforced
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
        spinnerScale.onItemSelectedListener = simpleSpinner {
            currentParams = currentParams.copy(scale = it)
            push()
        }
        spinnerStyle.onItemSelectedListener = simpleSpinner {
            currentParams = currentParams.copy(style = VoiceStyle.entries[it])
            updateUiVisibility()
            push()
        }
        rgDroneTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val timing = if (id == R.id.rbDroneRandom) DroneTimingMode.RANDOM else DroneTimingMode.CONSTANT
            currentParams = currentParams.copy(droneTiming = timing)
            updateUiVisibility()
            push()
        }
        rangeDroneBeats.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val vals = slider.values
            currentParams = currentParams.copy(
                droneMinBeats = vals[0].toInt(),
                droneMaxBeats = vals[1].toInt()
            )
            tvDroneRange.text = "Drone beat range: ${currentParams.droneMinBeats} - ${currentParams.droneMaxBeats}"
            push()
        }

        val rootRowListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (isUpdatingFromSync || checkedId == -1) return@OnCheckedChangeListener
            isUpdatingFromSync = true
            if (group.id == R.id.rgRootRow1) {
                rgRootRow2.clearCheck()
                rgRootFree.clearCheck()
            } else if (group.id == R.id.rgRootRow2) {
                rgRootRow1.clearCheck()
                rgRootFree.clearCheck()
            }
            isUpdatingFromSync = false
            val tag = requireView().findViewById<RadioButton>(checkedId).tag as? String
            val semitone = tag?.toIntOrNull() ?: 0
            currentParams = currentParams.copy(rootNote = semitone + 1)
            push()
        }
        rgRootRow1.setOnCheckedChangeListener(rootRowListener)
        rgRootRow2.setOnCheckedChangeListener(rootRowListener)

        rgRootFree.setOnCheckedChangeListener { _, checkedId ->
            if (isUpdatingFromSync || checkedId == -1) return@setOnCheckedChangeListener
            isUpdatingFromSync = true
            rgRootRow1.clearCheck()
            rgRootRow2.clearCheck()
            isUpdatingFromSync = false
            currentParams = currentParams.copy(rootNote = 0)
            push()
        }

        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val label = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info  = deviceMap[label] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
        btnStartStop.setOnClickListener { host?.getMidiService()?.togglePlayback() }
    }

    private fun updateUiVisibility() {
        if (view == null) return
        val style = currentParams.style

        val isSingleNote = style == VoiceStyle.SINGLE_NOTE_DRONE
        seekBpm.visibility    = if (isSingleNote) View.GONE else View.VISIBLE
        tvBpm.visibility      = if (isSingleNote) View.GONE else View.VISIBLE
        rgTiming.visibility   = if (isSingleNote) View.GONE else View.VISIBLE
        // Octave slider always visible (needed for single note drone octave selection too)
        tvOctave.visibility    = View.VISIBLE
        rangeOctave.visibility = View.VISIBLE

        val isEvolving    = style == VoiceStyle.EVOLVING_DRONE
        val isRandomDrone = isEvolving && rgDroneTiming.checkedRadioButtonId == R.id.rbDroneRandom

        if (isEvolving) {
            rgTiming.visibility          = View.GONE
            layoutDroneTiming.visibility = View.VISIBLE
            layoutDroneRange.visibility  = if (isRandomDrone) View.VISIBLE else View.GONE
        } else if (!isSingleNote) {
            rgTiming.visibility          = View.VISIBLE
            layoutDroneTiming.visibility = View.GONE
            layoutDroneRange.visibility  = View.GONE
        } else {
            layoutDroneTiming.visibility = View.GONE
            layoutDroneRange.visibility  = View.GONE
        }
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

        isUpdatingFromSync = false
        if (v1.rootNote != 0) selectRoot(v1.rootNote - 1)

        isUpdatingFromSync = true
        spinnerStyle.setSelection(v1.style.ordinal)
        if (v1.droneTiming == DroneTimingMode.RANDOM) {
            rgDroneTiming.check(R.id.rbDroneRandom)
        } else {
            rgDroneTiming.check(R.id.rbDroneConstant)
        }
        rangeDroneBeats.values = listOf(v1.droneMinBeats.toFloat(), v1.droneMaxBeats.toFloat())
        tvDroneRange.text = "Drone beat range: ${v1.droneMinBeats} - ${v1.droneMaxBeats}"
        updateUiVisibility()
        isUpdatingFromSync = false
    }

    fun updateDeviceList(devices: List<MidiDeviceInfo>) {
        if (!isAdded) return
        deviceAdapter.clear()
        deviceMap.clear()
        devices.forEach { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device"
            deviceAdapter.add(name)
            deviceMap[name] = info
        }
    }

    fun refreshDeviceList() {
        val manager = host?.getMidiManager() ?: return
        updateDeviceList(manager.devices.toList())
    }

    private fun selectRoot(semitone: Int) {
        isUpdatingFromSync = true
        rgRootFree.clearCheck()
        val row1Ids = listOf(R.id.rbRootC, R.id.rbRootCs, R.id.rbRootD, R.id.rbRootDs, R.id.rbRootE, R.id.rbRootF)
        val row2Ids = listOf(R.id.rbRootFs, R.id.rbRootG, R.id.rbRootGs, R.id.rbRootA, R.id.rbRootAs, R.id.rbRootB)
        val allIds  = row1Ids + row2Ids
        allIds.forEachIndexed { idx, id ->
            if (idx == semitone) {
                if (id in row1Ids) { rgRootRow2.clearCheck(); rgRootRow1.check(id) }
                else               { rgRootRow1.clearCheck(); rgRootRow2.check(id) }
            }
        }
        isUpdatingFromSync = false
    }

    private fun simpleSeek(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) block(progress)
        }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    private fun simpleSpinner(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
            if (!isUpdatingFromSync) block(pos)
        }
        override fun onNothingSelected(p: AdapterView<*>) {}
    }
}
