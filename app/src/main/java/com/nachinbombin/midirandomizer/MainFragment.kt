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

    // ── Standard V1 views ────────────────────────────────────────────────
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
    private lateinit var layoutEuclideanCompact: View
    private lateinit var seekEucStepsCompact:    SeekBar
    private lateinit var tvEucStepsCompact:      TextView
    private lateinit var seekEucDensityCompact:  SeekBar
    private lateinit var tvEucDensityCompact:    TextView
    private lateinit var seekEucRotationCompact: SeekBar
    private lateinit var tvEucRotationCompact:   TextView
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

    // ── Chord-settings panel views ───────────────────────────────────────
    private lateinit var layoutChordSettings:   View
    private lateinit var spinnerChordType:       Spinner
    private lateinit var spinnerPluckingStyle:   Spinner
    private lateinit var seekPluckDelay:         SeekBar
    private lateinit var tvPluckDelay:           TextView
    private lateinit var spinnerInversionMode:   Spinner
    private lateinit var spinnerVoicingDensity:  Spinner
    private lateinit var seekTensionLevel:       SeekBar
    private lateinit var tvTensionLevel:         TextView
    private lateinit var seekMutationChance:     SeekBar
    private lateinit var tvMutationChance:       TextView
    private lateinit var spinnerBuildStrategy:   Spinner
    private lateinit var spinnerRhythmicFigure:  Spinner
    private lateinit var seekNoteDropChance:     SeekBar
    private lateinit var tvNoteDropChance:       TextView

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
    // Note: "Chords" is 4th but maps to VoiceStyle.CHORDS (ordinal 3)
    private val styles = listOf("Generative", "Single note Drone", "Evolving Drone", "Chords")

    // ── Chord option label lists ─────────────────────────────────────────
    private val chordTypeLabels       = listOf("Triad", "7th", "9th", "Sus2", "Sus4", "Power")
    private val pluckingStyleLabels   = listOf("Simultaneous", "Ascending", "Descending", "Random", "Percussive Up")
    private val inversionModeLabels   = listOf("Root", "1st Inversion", "2nd Inversion", "Auto (Voice Leading)")
    private val voicingDensityLabels  = listOf("Full", "Drop 5th", "Shell (Root+3+7)", "Drop Root")
    private val buildStrategyLabels   = listOf("Diatonic Stack", "Modal Snap")
    private val rhythmicFigureLabels  = listOf("Sustained", "Re-Attack", "Broken / Alberti", "Ostinato")
    private val tensionLabels         = listOf("Triad", "Triad + 7th", "Triad + 9th", "Full Extensions (11/13)")

    // ── Root note helpers ────────────────────────────────────────────────

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

    // ── Fragment lifecycle ───────────────────────────────────────────────

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

    private fun makeSpinner(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
        rgTiming               = v.findViewById(R.id.rgTiming)
        layoutEuclideanCompact = v.findViewById(R.id.layoutEuclideanCompact)
        seekEucStepsCompact    = v.findViewById(R.id.seekEucStepsCompact)
        tvEucStepsCompact      = v.findViewById(R.id.tvEucStepsCompact)
        seekEucDensityCompact  = v.findViewById(R.id.seekEucDensityCompact)
        tvEucDensityCompact    = v.findViewById(R.id.tvEucDensityCompact)
        seekEucRotationCompact = v.findViewById(R.id.seekEucRotationCompact)
        tvEucRotationCompact   = v.findViewById(R.id.tvEucRotationCompact)
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

        // ── Chord panel ──────────────────────────────────────────────────
        layoutChordSettings  = v.findViewById(R.id.layoutChordSettings)
        spinnerChordType     = v.findViewById(R.id.spinnerChordType)
        spinnerPluckingStyle = v.findViewById(R.id.spinnerPluckingStyle)
        seekPluckDelay       = v.findViewById(R.id.seekPluckDelay)
        tvPluckDelay         = v.findViewById(R.id.tvPluckDelay)
        spinnerInversionMode = v.findViewById(R.id.spinnerInversionMode)
        spinnerVoicingDensity= v.findViewById(R.id.spinnerVoicingDensity)
        seekTensionLevel     = v.findViewById(R.id.seekTensionLevel)
        tvTensionLevel       = v.findViewById(R.id.tvTensionLevel)
        seekMutationChance   = v.findViewById(R.id.seekMutationChance)
        tvMutationChance     = v.findViewById(R.id.tvMutationChance)
        spinnerBuildStrategy = v.findViewById(R.id.spinnerBuildStrategy)
        spinnerRhythmicFigure= v.findViewById(R.id.spinnerRhythmicFigure)
        seekNoteDropChance   = v.findViewById(R.id.seekNoteDropChance)
        tvNoteDropChance     = v.findViewById(R.id.tvNoteDropChance)

        // Populate spinners
        spinnerChannel.adapter = makeSpinner((1..16).map { "Ch $it" })
        spinnerScale.adapter   = makeSpinner(scales)
        spinnerStyle.adapter   = makeSpinner(styles)
        spinnerChordType.adapter      = makeSpinner(chordTypeLabels)
        spinnerPluckingStyle.adapter  = makeSpinner(pluckingStyleLabels)
        spinnerInversionMode.adapter  = makeSpinner(inversionModeLabels)
        spinnerVoicingDensity.adapter = makeSpinner(voicingDensityLabels)
        spinnerBuildStrategy.adapter  = makeSpinner(buildStrategyLabels)
        spinnerRhythmicFigure.adapter = makeSpinner(rhythmicFigureLabels)
    }

    // ── Listeners ────────────────────────────────────────────────────────

    private fun setupListeners() {
        btnStartStop.setOnClickListener {
            host?.getMidiService()?.togglePlayback()
        }
        btnTheme.setOnClickListener {
            val current = ThemeManager.loadTheme(requireContext())
            ThemePickerDialog.show(requireContext(), current) { preset ->
                ThemeManager.saveTheme(requireContext(), preset)
                ThemeManager.applyToView(requireView(), preset)
            }
        }

        seekBpm.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(bpm = p + 20)
            tvBpm.text = getString(R.string.label_bpm, p + 20); push()
        })
        seekVelocity.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(velocity = p + 1)
            tvVelocity.text = getString(R.string.label_velocity, p + 1); push()
        })
        rangeOctave.addOnChangeListener { _, _, fromUser ->
            if (!fromUser || isUpdatingFromSync) return@addOnChangeListener
            val lo = rangeOctave.values[0].toInt()
            val hi = rangeOctave.values[1].toInt()
            currentParams = currentParams.copy(minOctave = lo, maxOctave = hi)
            tvOctave.text = getString(R.string.label_octave_range, lo, hi); push()
        }

        spinnerChannel.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(channel = pos); push()
        }
        spinnerScale.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(scale = pos); push()
        }
        spinnerStyle.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(style = VoiceStyle.entries[pos])
            val isSingle   = pos == 1
            val isEvolving = pos == 2
            val isChord    = pos == 3
            val isDrone    = isSingle || isEvolving
            layoutDroneTiming.visibility   = if (isDrone) View.VISIBLE else View.GONE
            layoutDroneRange.visibility    = if (isDrone && rgDroneTiming.checkedRadioButtonId == R.id.rbDroneRandom) View.VISIBLE else View.GONE
            layoutChordSettings.visibility = if (isChord) View.VISIBLE else View.GONE
            push()
        }

        rgDroneTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val isRandom = id == R.id.rbDroneRandom
            currentParams = currentParams.copy(
                droneTiming = if (isRandom) DroneTimingMode.RANDOM else DroneTimingMode.CONSTANT
            )
            layoutDroneRange.visibility = if (isRandom) View.VISIBLE else View.GONE
            push()
        }
        rangeDroneBeats.addOnChangeListener { _, _, fromUser ->
            if (!fromUser || isUpdatingFromSync) return@addOnChangeListener
            currentParams = currentParams.copy(
                droneMinBeats = rangeDroneBeats.values[0].toInt(),
                droneMaxBeats = rangeDroneBeats.values[1].toInt()
            )
            tvDroneRange.text = "Drone beat range: ${rangeDroneBeats.values[0].toInt()} - ${rangeDroneBeats.values[1].toInt()}"
            push()
        }

        rgTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val isEucl = id == R.id.rbEuclidean
            currentParams = currentParams.copy(timingMode = when (id) {
                R.id.rbMetronome  -> MidiService.TIMING_METRONOME
                R.id.rbMixed      -> MidiService.TIMING_MIXED
                R.id.rbRandomized -> MidiService.TIMING_RANDOMIZED
                R.id.rbEuclidean  -> MidiService.TIMING_EUCLIDEAN
                else              -> MidiService.TIMING_METRONOME
            })
            layoutEuclideanCompact.visibility = if (isEucl) View.VISIBLE else View.GONE
            if (isEucl) pushEuclideanCompact()
            push()
        }

        seekEucStepsCompact.setOnSeekBarChangeListener(simpleSeek { p ->
            tvEucStepsCompact.text = "Steps: ${p + 2}"
            pushEuclideanCompact()
        })
        seekEucDensityCompact.setOnSeekBarChangeListener(simpleSeek { p ->
            tvEucDensityCompact.text = "Density: ${p + 1}"
            pushEuclideanCompact()
        })
        seekEucRotationCompact.setOnSeekBarChangeListener(simpleSeek { p ->
            tvEucRotationCompact.text = "Rotation: $p"
            pushEuclideanCompact()
        })

        // ── Root note ────────────────────────────────────────────────────
        val rootListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
            if (isUpdatingFromSync || checkedId == -1) return@OnCheckedChangeListener
            if (group == rgRootRow1) { rgRootRow2.clearCheck(); rgRootFree.clearCheck() }
            if (group == rgRootRow2) { rgRootRow1.clearCheck(); rgRootFree.clearCheck() }
            if (group == rgRootFree) { rgRootRow1.clearCheck(); rgRootRow2.clearCheck() }
            val semitone = selectedRootTag()
            currentParams = currentParams.copy(rootNote = if (semitone >= 0) semitone + 1 else 0)
            push()
        }
        rgRootRow1.setOnCheckedChangeListener(rootListener)
        rgRootRow2.setOnCheckedChangeListener(rootListener)
        rgRootFree.setOnCheckedChangeListener(rootListener)

        // ── Chord panel ────────────────────────────────────────────────
        spinnerChordType.onItemSelectedListener     = simpleSpinner { pushChord() }
        spinnerPluckingStyle.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerInversionMode.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerVoicingDensity.onItemSelectedListener= simpleSpinner { pushChord() }
        spinnerBuildStrategy.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerRhythmicFigure.onItemSelectedListener= simpleSpinner { pushChord() }

        seekPluckDelay.setOnSeekBarChangeListener(simpleSeek { p ->
            tvPluckDelay.text = "Pluck delay: ${p + 1} ms"; pushChord()
        })
        seekTensionLevel.setOnSeekBarChangeListener(simpleSeek { p ->
            tvTensionLevel.text = "Tension: ${tensionLabels.getOrElse(p) { "$p" }}"; pushChord()
        })
        seekMutationChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvMutationChance.text = "Mutation: $p%"; pushChord()
        })
        seekNoteDropChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvNoteDropChance.text = "Note drop: $p%"; pushChord()
        })

        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val name = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info = deviceMap[name] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
    }

    // ── MidiEventListener ────────────────────────────────────────────────

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            tvLastNote.text = "Last note: $noteName (vel $velocity)"
        }
    }

    override fun onStatusChanged(status: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread { tvStatus.text = status }
    }

    override fun onPlaybackStateChanged(playing: Boolean) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            btnStartStop.text = if (playing) getString(R.string.btn_stop) else getString(R.string.btn_start)
            val accent = if (playing) 0xFF01696F.toInt() else 0xFF4F9AA5.toInt()
            btnStartStop.backgroundTintList = ColorStateList.valueOf(accent)
        }
    }

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded || view == null) return
        requireActivity().runOnUiThread {
            isUpdatingFromSync = true

            seekBpm.progress = (v1.bpm - 20).coerceIn(0, seekBpm.max)
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
            val eucl = v1.timingMode == MidiService.TIMING_EUCLIDEAN
            layoutEuclideanCompact.visibility = if (eucl) View.VISIBLE else View.GONE
            val ps = v1.proSettings
            seekEucStepsCompact.progress    = (ps.euclideanSteps - 2).coerceIn(0, seekEucStepsCompact.max)
            tvEucStepsCompact.text          = "Steps: ${ps.euclideanSteps}"
            seekEucDensityCompact.progress  = (ps.euclideanDensity - 1).coerceIn(0, seekEucDensityCompact.max)
            tvEucDensityCompact.text        = "Density: ${ps.euclideanDensity}"
            seekEucRotationCompact.progress = ps.euclideanRotation.coerceIn(0, seekEucRotationCompact.max)
            tvEucRotationCompact.text       = "Rotation: ${ps.euclideanRotation}"
            spinnerChannel.setSelection(v1.channel)
            spinnerScale.setSelection(v1.scale)
            spinnerStyle.setSelection(v1.style.ordinal)
            rgDroneTiming.check(if (v1.droneTiming == DroneTimingMode.RANDOM) R.id.rbDroneRandom else R.id.rbDroneConstant)
            rangeDroneBeats.values = listOf(v1.droneMinBeats.toFloat(), v1.droneMaxBeats.toFloat())
            tvDroneRange.text = "Drone beat range: ${v1.droneMinBeats} - ${v1.droneMaxBeats}"
            if (v1.rootNote != 0) selectRoot(v1.rootNote - 1) else selectRoot(-1)
            syncChordPanelFromParams(v1.chordConfig)

            val isSingle   = v1.style == VoiceStyle.SINGLE_NOTE_DRONE
            val isEvolving = v1.style == VoiceStyle.EVOLVING_DRONE
            val isChord    = v1.style == VoiceStyle.CHORDS
            val isDrone    = isSingle || isEvolving
            layoutDroneTiming.visibility   = if (isDrone) View.VISIBLE else View.GONE
            layoutDroneRange.visibility    = if (isDrone && v1.droneTiming == DroneTimingMode.RANDOM) View.VISIBLE else View.GONE
            layoutChordSettings.visibility = if (isChord) View.VISIBLE else View.GONE
            rgTiming.visibility            = if (isSingle || isEvolving) View.GONE else View.VISIBLE

            currentParams      = v1
            isUpdatingFromSync = false
        }
    }

    // ── Chord sync helper ────────────────────────────────────────────────

    private fun syncChordPanelFromParams(cc: ChordConfig) {
        spinnerChordType.setSelection(cc.chordType.coerceIn(0, chordTypeLabels.lastIndex))
        spinnerPluckingStyle.setSelection(cc.pluckingStyle.coerceIn(0, pluckingStyleLabels.lastIndex))
        seekPluckDelay.progress = (cc.pluckingDelayMs - 1).toInt().coerceIn(0, seekPluckDelay.max)
        tvPluckDelay.text       = "Pluck delay: ${cc.pluckingDelayMs} ms"
        spinnerInversionMode.setSelection(cc.inversionMode.ordinal.coerceIn(0, inversionModeLabels.lastIndex))
        spinnerVoicingDensity.setSelection(cc.voicingDensity.ordinal.coerceIn(0, voicingDensityLabels.lastIndex))
        seekTensionLevel.progress = cc.tensionLevel.ordinal
        tvTensionLevel.text       = "Tension: ${tensionLabels.getOrElse(cc.tensionLevel.ordinal) { "Triad" }}"
        seekMutationChance.progress   = (cc.mutationChance * 100).toInt().coerceIn(0, 30)
        tvMutationChance.text         = "Mutation: ${(cc.mutationChance * 100).toInt()}%"
        spinnerBuildStrategy.setSelection(cc.chordBuildStrategy.ordinal)
        spinnerRhythmicFigure.setSelection(cc.rhythmicFigure.ordinal.coerceIn(0, rhythmicFigureLabels.lastIndex))
        seekNoteDropChance.progress   = (cc.noteDropChance * 100).toInt().coerceIn(0, 50)
        tvNoteDropChance.text         = "Note drop: ${(cc.noteDropChance * 100).toInt()}%"
    }

    // ── Chord push helper ────────────────────────────────────────────────

    private fun pushChord() {
        if (isUpdatingFromSync) return
        val cc = ChordConfig(
            chordType          = spinnerChordType.selectedItemPosition,
            pluckingStyle      = spinnerPluckingStyle.selectedItemPosition,
            pluckingDelayMs    = (seekPluckDelay.progress + 1).toLong(),
            inversionMode      = InversionMode.entries[spinnerInversionMode.selectedItemPosition],
            voicingDensity     = VoicingDensity.entries[spinnerVoicingDensity.selectedItemPosition],
            noteDropChance     = seekNoteDropChance.progress / 100f,
            rhythmicFigure     = RhythmicFigure.entries[spinnerRhythmicFigure.selectedItemPosition],
            chordBuildStrategy = ChordBuildStrategy.entries[spinnerBuildStrategy.selectedItemPosition],
            tensionLevel       = TensionLevel.entries[seekTensionLevel.progress.coerceIn(0, 3)],
            mutationChance     = seekMutationChance.progress / 100f,
        )
        currentParams = currentParams.copy(chordConfig = cc)
        push()
    }

    // ── Device list ──────────────────────────────────────────────────────

    fun refreshDeviceList() {
        val manager = host?.getMidiManager() ?: return
        val devices = manager.getDevices() ?: return
        deviceAdapter.clear(); deviceMap.clear()
        devices.forEach { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device"
            deviceAdapter.add(name)
            deviceMap[name] = info
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun push() {
        if (isUpdatingFromSync) return
        host?.getMidiService()?.updateV1Parameters(currentParams)
    }

    private fun pushEuclideanCompact() {
        if (isUpdatingFromSync) return
        val steps    = seekEucStepsCompact.progress + 2
        val density  = (seekEucDensityCompact.progress + 1).coerceAtMost(steps)
        val rotation = seekEucRotationCompact.progress
        currentParams = currentParams.copy(
            proSettings = currentParams.proSettings.copy(
                euclideanEnabled  = true,
                euclideanSteps    = steps,
                euclideanDensity  = density,
                euclideanRotation = rotation
            )
        )
        push()
    }

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
