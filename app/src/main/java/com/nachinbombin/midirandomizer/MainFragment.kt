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
        tvChordHint       = v.findViewById(R.id.tvChordHint)

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
        fun simpleSeek(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) block(p) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

        btnStartStop.setOnClickListener {
            val svc = host?.getMidiService() ?: return@setOnClickListener
            if (svc.isRunning()) svc.stop() else svc.start()
        }
        btnTheme.setOnClickListener { ThemeManager.cycleTheme(requireContext(), requireView()) }

        seekBpm.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(bpm = p + 20)
            tvBpm.text = "BPM: ${p + 20}"; push()
        })
        seekVelocity.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(velocity = p + 1)
            tvVelocity.text = "Velocity: ${p + 1}"; push()
        })
        rangeOctave.addOnChangeListener { _, _, fromUser ->
            if (!fromUser || isUpdatingFromSync) return@addOnChangeListener
            val lo = rangeOctave.values[0].toInt()
            val hi = rangeOctave.values[1].toInt()
            currentParams = currentParams.copy(octaveLow = lo, octaveHigh = hi)
            tvOctave.text = "Octave range: $lo – $hi"; push()
        }

        spinnerChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(midiChannel = pos + 1); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerScale.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(scaleIndex = pos); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                val style = MidiService.VoiceStyle.entries[pos]
                currentParams = currentParams.copy(style = style)
                val isDrone = style == MidiService.VoiceStyle.DRONE_SINGLE || style == MidiService.VoiceStyle.DRONE_EVOLVING
                val isChord = style == MidiService.VoiceStyle.CHORDS
                layoutDroneTiming.visibility = if (isDrone) View.VISIBLE else View.GONE
                layoutDroneRange.visibility  = if (isDrone && rgDroneTiming.checkedRadioButtonId == R.id.rbDroneRandom) View.VISIBLE else View.GONE
                layoutChordSettings.visibility = if (isChord) View.VISIBLE else View.GONE
                tvChordHint.visibility         = if (isChord) View.VISIBLE else View.GONE
                push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        rgDroneTiming.setOnCheckedChangeListener { _, id ->
            if (isUpdatingFromSync) return@setOnCheckedChangeListener
            val isRandom = id == R.id.rbDroneRandom
            currentParams = currentParams.copy(droneRandomGate = isRandom)
            layoutDroneRange.visibility = if (isRandom) View.VISIBLE else View.GONE
            push()
        }
        rangeDroneBeats.addOnChangeListener { _, _, fromUser ->
            if (!fromUser || isUpdatingFromSync) return@addOnChangeListener
            currentParams = currentParams.copy(
                droneBeatMin = rangeDroneBeats.values[0].toInt(),
                droneBeatMax = rangeDroneBeats.values[1].toInt()
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
            tvEucStepsCompact.text = "Steps: ${p + 1}"
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
            val tag = requireView().findViewById<RadioButton>(checkedId).tag as? String
            val semitone = tag?.toIntOrNull() ?: -1
            currentParams = currentParams.copy(rootSemitone = semitone)
            push()
        }
        rgRootRow1.setOnCheckedChangeListener(rootListener)
        rgRootRow2.setOnCheckedChangeListener(rootListener)
        rgRootFree.setOnCheckedChangeListener(rootListener)

        // ── Chord settings ───────────────────────────────────────────────
        spinnerChordType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(chordType = MidiService.ChordType.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerPluckingStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(pluckingStyle = MidiService.PluckingStyle.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        seekPluckDelay.setOnSeekBarChangeListener(simpleSeek { p ->
            currentParams = currentParams.copy(pluckDelayMs = p + 1)
            tvPluckDelay.text = "Pluck delay: ${p + 1} ms"; push()
        })
        spinnerInversionMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(inversionMode = MidiService.InversionMode.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerVoicingDensity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(voicingDensity = MidiService.VoicingDensity.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        seekTensionLevel.setOnSeekBarChangeListener(simpleSeek { p ->
            tvTensionLevel.text = "Tension: ${tensionLabels.getOrNull(p) ?: p}"
            currentParams = currentParams.copy(tensionLevel = p); push()
        })
        seekMutationChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvMutationChance.text = "Mutation: $p%"
            currentParams = currentParams.copy(mutationChance = p); push()
        })
        spinnerBuildStrategy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(buildStrategy = MidiService.BuildStrategy.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerRhythmicFigure.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                currentParams = currentParams.copy(rhythmicFigure = MidiService.RhythmicFigure.entries[pos]); push()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        seekNoteDropChance.setOnSeekBarChangeListener(simpleSeek { p ->
            tvNoteDropChance.text = "Note drop: $p%"
            currentParams = currentParams.copy(noteDropChance = p); push()
        })

        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            val name = deviceAdapter.getItem(pos) ?: return@setOnItemClickListener
            val info = deviceMap[name] ?: return@setOnItemClickListener
            host?.getMidiService()?.connectDevice(info)
        }
    }

    // ── MidiEventListener ────────────────────────────────────────────────

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded || view == null) return
        requireActivity().runOnUiThread {
            isUpdatingFromSync = true

            seekBpm.progress = (v1.bpm - 20).coerceIn(0, seekBpm.max)
            tvBpm.text = "BPM: ${v1.bpm}"
            seekVelocity.progress = (v1.velocity - 1).coerceIn(0, seekVelocity.max)
            tvVelocity.text = "Velocity: ${v1.velocity}"
            rangeOctave.values = listOf(v1.octaveLow.toFloat(), v1.octaveHigh.toFloat())
            tvOctave.text = "Octave range: ${v1.octaveLow} – ${v1.octaveHigh}"

            val isDrone = v1.style == MidiService.VoiceStyle.DRONE_SINGLE || v1.style == MidiService.VoiceStyle.DRONE_EVOLVING
            val isChord = v1.style == MidiService.VoiceStyle.CHORDS
            layoutDroneTiming.visibility   = if (isDrone) View.VISIBLE else View.GONE
            layoutChordSettings.visibility = if (isChord) View.VISIBLE else View.GONE
            tvChordHint.visibility         = if (isChord) View.VISIBLE else View.GONE

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
            seekEucStepsCompact.progress    = (ps.euclideanSteps - 1).coerceIn(0, 30)
            tvEucStepsCompact.text          = "Steps: ${ps.euclideanSteps}"
            seekEucDensityCompact.progress  = (ps.euclideanDensity - 1).coerceIn(0, 15)
            tvEucDensityCompact.text        = "Density: ${ps.euclideanDensity}"
            seekEucRotationCompact.progress = ps.euclideanRotation.coerceIn(0, 15)
            tvEucRotationCompact.text       = "Rotation: ${ps.euclideanRotation}"

            selectRoot(v1.rootSemitone)

            if (isChord) {
                spinnerChordType.setSelection(v1.chordType.ordinal.coerceIn(0, chordTypeLabels.size - 1))
                spinnerPluckingStyle.setSelection(v1.pluckingStyle.ordinal.coerceIn(0, pluckingStyleLabels.size - 1))
                seekPluckDelay.progress = (v1.pluckDelayMs - 1).coerceIn(0, seekPluckDelay.max)
                tvPluckDelay.text       = "Pluck delay: ${v1.pluckDelayMs} ms"
                spinnerInversionMode.setSelection(v1.inversionMode.ordinal.coerceIn(0, inversionModeLabels.size - 1))
                spinnerVoicingDensity.setSelection(v1.voicingDensity.ordinal.coerceIn(0, voicingDensityLabels.size - 1))
                seekTensionLevel.progress = v1.tensionLevel.coerceIn(0, seekTensionLevel.max)
                tvTensionLevel.text       = "Tension: ${tensionLabels.getOrNull(v1.tensionLevel) ?: v1.tensionLevel}"
                seekMutationChance.progress = v1.mutationChance.coerceIn(0, seekMutationChance.max)
                tvMutationChance.text       = "Mutation: ${v1.mutationChance}%"
                spinnerBuildStrategy.setSelection(v1.buildStrategy.ordinal.coerceIn(0, buildStrategyLabels.size - 1))
                spinnerRhythmicFigure.setSelection(v1.rhythmicFigure.ordinal.coerceIn(0, rhythmicFigureLabels.size - 1))
                seekNoteDropChance.progress = v1.noteDropChance.coerceIn(0, seekNoteDropChance.max)
                tvNoteDropChance.text       = "Note drop: ${v1.noteDropChance}%"
            }

            currentParams      = v1
            isUpdatingFromSync = false
        }
    }

    override fun onServiceStateChanged(running: Boolean) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            btnStartStop.text = if (running) "STOP" else "START"
            val accent = if (running) 0xFF01696F.toInt() else 0xFF4F9AA5.toInt()
            btnStartStop.backgroundTintList = ColorStateList.valueOf(accent)
            tvStatus.text = if (running) "Running…" else "Idle"
        }
    }

    override fun onNoteEvent(note: Int, velocity: Int) {
        if (!isAdded) return
        val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val name  = names[note % 12]
        val octave = note / 12 - 1
        requireActivity().runOnUiThread { tvLastNote.text = "Last note: $name$octave (vel $velocity)" }
    }

    // ── Device list ──────────────────────────────────────────────────────

    fun refreshDeviceList() {
        val manager = host?.getMidiManager() ?: return
        deviceMap.clear()
        deviceAdapter.clear()
        manager.getDevices()?.forEach { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown"
            deviceMap[name] = info
            deviceAdapter.add(name)
        }
        deviceListView.adapter = deviceAdapter
        tvDeviceInfo.text = if (deviceMap.isEmpty()) "No MIDI devices found" else "Tap device to connect"
    }

    // ── Push ─────────────────────────────────────────────────────────────

    private fun push() {
        if (isUpdatingFromSync) return
        host?.getMidiService()?.updateVoice1Params(currentParams)
    }

    // ── Euclidean compact helper ─────────────────────────────────────────

    private fun pushEuclideanCompact() {
        if (isUpdatingFromSync) return
        val steps    = seekEucStepsCompact.progress + 1
        val density  = seekEucDensityCompact.progress + 1
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
}
