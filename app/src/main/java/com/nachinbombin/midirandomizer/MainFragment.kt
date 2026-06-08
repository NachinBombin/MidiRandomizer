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
    private lateinit var layoutEuclideanSettings: View
    private lateinit var tvEuclideanSteps:    TextView
    private lateinit var seekEuclideanSteps:  SeekBar
    private lateinit var tvEuclideanDensity:  TextView
    private lateinit var seekEuclideanDensity: SeekBar
    private lateinit var tvEuclideanRotation: TextView
    private lateinit var seekEuclideanRotation: SeekBar
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
    private lateinit var tvChordHint: TextView

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
    
    // ── Playability Layer ──
    private lateinit var switchPlayability:      Switch
    private lateinit var layoutPlayabilityControls: View
    private lateinit var spinnerNumerator:       Spinner
    private lateinit var spinnerDenominator:     Spinner
    private lateinit var layoutOddMeter:         View
    private lateinit var etOddMeterGrouping:     EditText
    private lateinit var spinnerPlayabilityFigure: Spinner
    private lateinit var spinnerRhythmicModifier: Spinner
    private lateinit var switchAccent:           Switch
    private lateinit var tvAccentDepth:          TextView
    private lateinit var seekAccentDepth:        SeekBar
    private lateinit var tvGateTime:             TextView
    private lateinit var seekGateTime:           SeekBar
    private lateinit var switchHarmonicAnchor:   Switch
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
    
    private val numeratorLabels       = (1..16).map { it.toString() }
    private val denominatorLabels     = listOf("2", "4", "8", "16")
    private val rhythmicFigureLabels_P = listOf("Whole", "Half", "Quarter", "Eighth", "16th", "32nd", "64th")
    private val rhythmicModifierLabels = listOf("Straight", "Dotted", "Triplet")

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
        rgTiming          = v.findViewById(R.id.rgTiming)
        layoutEuclideanSettings = v.findViewById(R.id.layoutEuclideanSettings)
        tvEuclideanSteps  = v.findViewById(R.id.tvEuclideanSteps)
        seekEuclideanSteps = v.findViewById(R.id.seekEuclideanSteps)
        tvEuclideanDensity = v.findViewById(R.id.tvEuclideanDensity)
        seekEuclideanDensity = v.findViewById(R.id.seekEuclideanDensity)
        tvEuclideanRotation = v.findViewById(R.id.tvEuclideanRotation)
        seekEuclideanRotation = v.findViewById(R.id.seekEuclideanRotation)
        spinnerChannel    = v.findViewById(R.id.spinnerChannel)
        
        switchPlayability      = v.findViewById(R.id.switchPlayability)
        layoutPlayabilityControls = v.findViewById(R.id.layoutPlayabilityControls)
        spinnerNumerator       = v.findViewById(R.id.spinnerNumerator)
        spinnerDenominator     = v.findViewById(R.id.spinnerDenominator)
        layoutOddMeter         = v.findViewById(R.id.layoutOddMeter)
        etOddMeterGrouping     = v.findViewById(R.id.etOddMeterGrouping)
        spinnerPlayabilityFigure = v.findViewById(R.id.spinnerPlayabilityFigure)
        spinnerRhythmicModifier = v.findViewById(R.id.spinnerRhythmicModifier)
        switchAccent           = v.findViewById(R.id.switchAccent)
        tvAccentDepth          = v.findViewById(R.id.tvAccentDepth)
        seekAccentDepth        = v.findViewById(R.id.seekAccentDepth)
        tvGateTime             = v.findViewById(R.id.tvGateTime)
        seekGateTime           = v.findViewById(R.id.seekGateTime)
        switchHarmonicAnchor   = v.findViewById(R.id.switchHarmonicAnchor)

        spinnerNumerator.adapter = makeSpinner(numeratorLabels)
        spinnerDenominator.adapter = makeSpinner(denominatorLabels)
        spinnerPlayabilityFigure.adapter = makeSpinner(rhythmicFigureLabels_P)
        spinnerRhythmicModifier.adapter = makeSpinner(rhythmicModifierLabels)
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
        tvChordHint       = v.findViewById(R.id.tvChordHint)

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

        // Populate chord spinners
        spinnerChordType.adapter      = makeSpinner(chordTypeLabels)
        spinnerPluckingStyle.adapter  = makeSpinner(pluckingStyleLabels)
        spinnerInversionMode.adapter  = makeSpinner(inversionModeLabels)
        spinnerVoicingDensity.adapter = makeSpinner(voicingDensityLabels)
        spinnerBuildStrategy.adapter  = makeSpinner(buildStrategyLabels)
        spinnerRhythmicFigure.adapter = makeSpinner(rhythmicFigureLabels)

        seekTensionLevel.max = 3
        seekPluckDelay.max   = 99   // 10..200 ms mapped as (value+1)*2
        seekMutationChance.max   = 30  // 0..30 %
        seekNoteDropChance.max   = 50  // 0..50 %

        // Standard view setup
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

        spinnerScale.adapter = makeSpinner(scales)
        spinnerStyle.adapter = makeSpinner(styles)

        deviceListView.adapter = deviceAdapter
        selectRoot(-1)
        syncChordPanelFromParams(currentParams.chordConfig)
    }

    fun refreshDeviceList() {
        val mm = host?.getMidiManager() ?: return
        deviceAdapter.clear(); deviceMap.clear()
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

    // ── Chord panel sync helpers ─────────────────────────────────────────

    private fun syncChordPanelFromParams(cc: ChordConfig) {
        isUpdatingFromSync = true
        spinnerChordType.setSelection(cc.chordType.coerceIn(0, chordTypeLabels.lastIndex))
        spinnerPluckingStyle.setSelection(cc.pluckingStyle.coerceIn(0, pluckingStyleLabels.lastIndex))
        seekPluckDelay.progress   = ((cc.pluckingDelayMs.coerceIn(10, 200) / 2) - 1).toInt().coerceIn(0, 99)
        tvPluckDelay.text         = "Pluck delay: ${cc.pluckingDelayMs} ms"
        spinnerInversionMode.setSelection(cc.inversionMode.ordinal)
        spinnerVoicingDensity.setSelection(cc.voicingDensity.ordinal)
        seekTensionLevel.progress = cc.tensionLevel.ordinal
        tvTensionLevel.text       = "Tension: ${tensionLabels.getOrElse(cc.tensionLevel.ordinal) { "Triad" }}"
        seekMutationChance.progress   = (cc.mutationChance * 100).toInt().coerceIn(0, 30)
        tvMutationChance.text         = "Mutation: ${(cc.mutationChance * 100).toInt()}%"
        spinnerBuildStrategy.setSelection(cc.chordBuildStrategy.ordinal)
        spinnerRhythmicFigure.setSelection(cc.rhythmicFigure.ordinal)
        seekNoteDropChance.progress   = (cc.noteDropChance * 100).toInt().coerceIn(0, 50)
        tvNoteDropChance.text         = "Note drop: ${(cc.noteDropChance * 100).toInt()}%"
        isUpdatingFromSync = false
    }

    private fun buildChordConfigFromUi(): ChordConfig {
        val pluckDelayMs = ((seekPluckDelay.progress + 1) * 2).toLong().coerceIn(10, 200)
        return ChordConfig(
            chordType          = spinnerChordType.selectedItemPosition,
            pluckingStyle      = spinnerPluckingStyle.selectedItemPosition,
            pluckingDelayMs    = pluckDelayMs,
            chordSpread        = 1,
            noteDropChance     = seekNoteDropChance.progress / 100f,
            chordRhythmPattern = 0,
            strumLength        = 4,  // default: strum all notes
            chordBuildStrategy = ChordBuildStrategy.entries[spinnerBuildStrategy.selectedItemPosition],
            inversionMode      = InversionMode.entries[spinnerInversionMode.selectedItemPosition],
            voicingDensity     = VoicingDensity.entries[spinnerVoicingDensity.selectedItemPosition],
            tensionLevel       = TensionLevel.entries[seekTensionLevel.progress.coerceIn(0, 3)],
            mutationChance     = seekMutationChance.progress / 100f,
            rhythmicFigure     = ChordRhythmFigure.entries[spinnerRhythmicFigure.selectedItemPosition]
        )
    }

    private fun pushChord() {
        if (isUpdatingFromSync) return
        currentParams = currentParams.copy(chordConfig = buildChordConfigFromUi())
        push()
    }

    // ── Listeners ────────────────────────────────────────────────────────

    private fun setupListeners() {
        btnTheme.setOnClickListener {
            val current = ThemeManager.loadTheme(requireContext())
            ThemePickerDialog.show(requireContext(), current) { preset ->
                ThemeManager.saveTheme(requireContext(), preset)
                ThemeManager.applyToView(requireView(), preset)
            }
        }

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

        switchPlayability.setOnCheckedChangeListener { _, on ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(isEnabled = on))
            updateUiVisibility()
            push()
        }
        
        spinnerNumerator.onItemSelectedListener = simpleSpinner { pos ->
            val num = pos + 1
            currentParams = currentParams.copy(playability = currentParams.playability.copy(timeSignatureNumerator = num))
            updateUiVisibility()
            push()
        }
        
        spinnerDenominator.onItemSelectedListener = simpleSpinner { pos ->
            val den = denominatorLabels[pos].toInt()
            currentParams = currentParams.copy(playability = currentParams.playability.copy(timeSignatureDenominator = den))
            push()
        }
        
        etOddMeterGrouping.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val groups = s.toString().split(",").mapNotNull { it.trim().toIntOrNull() }
                if (groups.isNotEmpty()) {
                    currentParams = currentParams.copy(playability = currentParams.playability.copy(oddMeterGrouping = groups))
                    push()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        spinnerPlayabilityFigure.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(rhythmicFigure = RhythmicFigure.entries[pos]))
            push()
        }
        
        spinnerRhythmicModifier.onItemSelectedListener = simpleSpinner { pos ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(rhythmicModifier = RhythmicModifier.entries[pos]))
            push()
        }
        
        switchAccent.setOnCheckedChangeListener { _, on ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(
                accentConfig = currentParams.playability.accentConfig.copy(isEnabled = on)
            ))
            push()
        }
        
        seekAccentDepth.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(
                accentConfig = currentParams.playability.accentConfig.copy(accentDepth = p / 100f)
            ))
            tvAccentDepth.text = "Accent Depth: $p%"
            push()
        })
        
        seekGateTime.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(gatePercentage = (p + 10) / 100f))
            tvGateTime.text = "Gate Time: ${p + 10}%"
            push()
        })
        
        switchHarmonicAnchor.setOnCheckedChangeListener { _, on ->
            currentParams = currentParams.copy(playability = currentParams.playability.copy(isHarmonicAnchorEnabled = on))
            push()
        }

        rgTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val newMode = when (id) {
                R.id.rbMetronome  -> MidiService.TIMING_METRONOME
                R.id.rbMixed      -> MidiService.TIMING_MIXED
                R.id.rbRandomized -> MidiService.TIMING_RANDOMIZED
                R.id.rbEuclidean  -> MidiService.TIMING_EUCLIDEAN
                else              -> MidiService.TIMING_METRONOME
            }
            val euclOn = newMode == MidiService.TIMING_EUCLIDEAN
            currentParams = currentParams.copy(
                timingMode = newMode,
                proSettings = currentParams.proSettings.copy(euclideanEnabled = euclOn)
            )
            updateUiVisibility()
            push()
        }

        seekEuclideanSteps.setOnSeekBarChangeListener(simpleSeek { p ->
            val steps = p + 2
            tvEuclideanSteps.text = "Steps: $steps"
            currentParams = currentParams.copy(proSettings = currentParams.proSettings.copy(euclideanSteps = steps))
            push()
        })
        seekEuclideanDensity.setOnSeekBarChangeListener(simpleSeek { p ->
            tvEuclideanDensity.text = "Density: $p"
            currentParams = currentParams.copy(proSettings = currentParams.proSettings.copy(euclideanDensity = p))
            push()
        })
        seekEuclideanRotation.setOnSeekBarChangeListener(simpleSeek { p ->
            tvEuclideanRotation.text = "Rotation: $p"
            currentParams = currentParams.copy(proSettings = currentParams.proSettings.copy(euclideanRotation = p))
            push()
        })

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

        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val label = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info  = deviceMap[label] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectToDevice(info)
        }
        btnStartStop.setOnClickListener { host?.getMidiService()?.togglePlayback() }

        // ── Chord panel listeners ────────────────────────────────────────
        spinnerChordType.onItemSelectedListener     = simpleSpinner { pushChord() }
        spinnerPluckingStyle.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerInversionMode.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerVoicingDensity.onItemSelectedListener= simpleSpinner { pushChord() }
        spinnerBuildStrategy.onItemSelectedListener = simpleSpinner { pushChord() }
        spinnerRhythmicFigure.onItemSelectedListener= simpleSpinner { pushChord() }

        seekPluckDelay.setOnSeekBarChangeListener(simpleSeek { p ->
            val ms = ((p + 1) * 2).toLong()
            tvPluckDelay.text = "Pluck delay: $ms ms"
            pushChord()
        })
        seekTensionLevel.setOnSeekBarChangeListener(simpleSeek { p ->
            tvTensionLevel.text = "Tension: ${tensionLabels.getOrElse(p) { "Triad" }}"
            pushChord()
        })
        seekMutationChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvMutationChance.text = "Mutation: $p%"
            pushChord()
        })
        seekNoteDropChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvNoteDropChance.text = "Note drop: $p%"
            pushChord()
        })
    }

    private fun updateUiVisibility() {
        if (view == null) return
        val style       = currentParams.style
        val isSingle    = style == VoiceStyle.SINGLE_NOTE_DRONE
        val isEvolving  = style == VoiceStyle.EVOLVING_DRONE
        val isChords    = style == VoiceStyle.CHORDS
        val isRandDrone = isEvolving && rgDroneTiming.checkedRadioButtonId == R.id.rbDroneRandom
        val isEuclidean = rgTiming.checkedRadioButtonId == R.id.rbEuclidean && !isSingle && !isEvolving

        seekBpm.visibility    = if (isSingle) View.GONE else View.VISIBLE
        tvBpm.visibility      = if (isSingle) View.GONE else View.VISIBLE
        // Hide timing radio for drones; keep it visible for CHORDS (BPM drives strum interval)
        rgTiming.visibility   = if (isSingle || isEvolving) View.GONE else View.VISIBLE
        layoutDroneTiming.visibility = if (isEvolving) View.VISIBLE else View.GONE
        layoutDroneRange.visibility  = if (isRandDrone) View.VISIBLE else View.GONE
        
        layoutEuclideanSettings.visibility = if (isEuclidean) View.VISIBLE else View.GONE
        
        val pl = currentParams.playability
        layoutPlayabilityControls.visibility = if (pl.isEnabled) View.VISIBLE else View.GONE
        layoutOddMeter.visibility = if (pl.timeSignatureNumerator % 2 != 0 && pl.timeSignatureNumerator > 1) View.VISIBLE else View.GONE
        
        // Show chord settings panel only when CHORDS is selected
        layoutChordSettings.visibility = if (isChords) View.VISIBLE else View.GONE
        tvChordHint.visibility = if (isChords) View.VISIBLE else View.GONE
    }

    private fun push() {
        if (!isUpdatingFromSync) host?.getMidiService()?.updateV1Parameters(currentParams)
    }

    // ── MidiEventListener ───────────────────────────────────────────────

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
        val v1ps = v1.proSettings
        seekEuclideanSteps.progress = (v1ps.euclideanSteps - 2).coerceIn(0, 30)
        tvEuclideanSteps.text = "Steps: ${v1ps.euclideanSteps}"
        seekEuclideanDensity.progress = v1ps.euclideanDensity
        tvEuclideanDensity.text = "Density: ${v1ps.euclideanDensity}"
        seekEuclideanRotation.progress = v1ps.euclideanRotation
        tvEuclideanRotation.text = "Rotation: ${v1ps.euclideanRotation}"

        syncChordPanelFromParams(v1.chordConfig)
        
        val pl = v1.playability
        isUpdatingFromSync = true
        switchPlayability.isChecked = pl.isEnabled
        spinnerNumerator.setSelection(pl.timeSignatureNumerator - 1)
        spinnerDenominator.setSelection(denominatorLabels.indexOf(pl.timeSignatureDenominator.toString()).coerceAtLeast(0))
        etOddMeterGrouping.setText(pl.oddMeterGrouping.joinToString(","))
        spinnerPlayabilityFigure.setSelection(pl.rhythmicFigure.ordinal)
        spinnerRhythmicModifier.setSelection(pl.rhythmicModifier.ordinal)
        switchAccent.isChecked = pl.accentConfig.isEnabled
        seekAccentDepth.progress = (pl.accentConfig.accentDepth * 100).toInt()
        tvAccentDepth.text = "Accent Depth: ${seekAccentDepth.progress}%"
        seekGateTime.progress = (pl.gatePercentage * 100 - 10).toInt()
        tvGateTime.text = "Gate Time: ${(pl.gatePercentage * 100).toInt()}%"
        switchHarmonicAnchor.isChecked = pl.isHarmonicAnchorEnabled
        isUpdatingFromSync = false
        
        updateUiVisibility()
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

    // ── Helpers ──────────────────────────────────────────────────────────

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
