package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment — hosts the UI for Voice 2 and Voice 3.
 */
class VoicesFragment : Fragment(), MidiService.MidiEventListener {

    private var serviceProvider: ServiceProvider? = null

    private var currentV2 = VoiceConfig()
    private var currentV3 = VoiceConfig()
    private var isUpdatingFromSync = false

    private lateinit var panelV2: LinearLayout
    private lateinit var panelV3: LinearLayout
    // One octave RangeSlider per voice (replaces the old indMinOct / indMaxOct pair)
    private var octaveSliderV2: com.google.android.material.slider.RangeSlider? = null
    private var octaveSliderV3: com.google.android.material.slider.RangeSlider? = null

    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    fun onServiceReady() {}

    override fun onAttach(context: Context) {
        super.onAttach(context)
        serviceProvider = context as? ServiceProvider
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.addMidiListener(this)
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.removeMidiListener(this)
    }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {}
    override fun onStatusChanged(status: String) {}
    override fun onPlaybackStateChanged(playing: Boolean) {}

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded || view == null) return
        syncFromService(v1, v2, v3)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF111318.toInt())
        }
        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        root.addView(column)

        panelV2 = buildVoicePanel(2, "Voice 2")
        column.addView(panelV2)
        column.addView(divider())
        panelV3 = buildVoicePanel(3, "Voice 3")
        column.addView(panelV3)
        return root
    }

    private fun buildVoicePanel(voiceId: Int, label: String): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            tag = "root$voiceId"
        }

        val headerRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val enableSwitch = Switch(ctx).apply {
            text = label
            isChecked = false
            textSize = 18f
            setTextColor(0xFFE8E6E1.toInt())
            tag = "enable"
        }
        headerRow.addView(enableSwitch)
        panel.addView(headerRow)

        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
            tag = "modeRow"
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true).apply { tag = "rbHarmony" }
        val independentBtn = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        modeRow.addView(harmonyBtn)
        modeRow.addView(independentBtn)
        panel.addView(modeRow)

        var syncConfigAction: (() -> Unit)? = null

        val harmonyPanel     = buildHarmonyPanel(voiceId) { syncConfigAction?.invoke() }.apply { tag = "harmonyPanel" }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }.apply { tag = "independentPanel" }
        independentPanel.visibility = View.GONE
        panel.addView(harmonyPanel)
        panel.addView(independentPanel)

        fun setEnabledState(on: Boolean) {
            modeRow.alpha          = if (on) 1f else 0.4f
            harmonyPanel.alpha     = if (on) 1f else 0.4f
            independentPanel.alpha = if (on) 1f else 0.4f
            setChildrenEnabled(modeRow, on)
            setChildrenEnabled(harmonyPanel, on)
            setChildrenEnabled(independentPanel, on)
        }
        setEnabledState(false)

        fun syncConfig() {
            if (isUpdatingFromSync) return
            val svc = serviceProvider?.getMidiService() ?: return
            val cfg = VoiceConfig(
                enabled = enableSwitch.isChecked,
                mode = if (harmonyBtn.isChecked) VoiceMode.HARMONY else VoiceMode.INDEPENDENT,
                harmonyConfig = readHarmonyConfig(harmonyPanel, voiceId),
                independentConfig = readIndependentConfig(independentPanel)
            )
            if (voiceId == 2) svc.updateVoice2Config(cfg) else svc.updateVoice3Config(cfg)
        }
        syncConfigAction = { syncConfig() }

        enableSwitch.setOnCheckedChangeListener { _, on ->
            setEnabledState(on)
            syncConfig()
        }

        fun switchMode(harmony: Boolean) {
            harmonyBtn.isChecked     = harmony
            independentBtn.isChecked = !harmony
            harmonyPanel.visibility     = if (harmony) View.VISIBLE else View.GONE
            independentPanel.visibility = if (harmony) View.GONE    else View.VISIBLE
            syncConfig()
        }

        harmonyBtn.setOnClickListener     { switchMode(true)  }
        independentBtn.setOnClickListener { switchMode(false) }

        attachSyncListeners(harmonyPanel, { syncConfig() }, skipSeekBars = true)
        attachSyncListeners(independentPanel, { syncConfig() }, skipSeekBars = true, skipTaggedSpinners = setOf("indStyle", "indDroneTiming"))

        return panel
    }

    private fun buildHarmonyPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "Tone Step Offset", -7, 7, 2,    tag = "toneStep",  onSync))
        panel.addView(labeledSeekBar(ctx, "Time Drift (ms)",   0, 45, 10,  tag = "timeDrift", onSync))
        panel.addView(labeledSeekBar(ctx, "Skip % (0-100)",    0, 100, 0,  tag = "skipPct",   onSync))
        panel.addView(labeledSeekBar(ctx, "Master Velocity",   0, 127, 100,tag = "masterVel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity Drift \u00b1",  0, 20, 8,   tag = "velDrift",  onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)", 0, 16, voiceId, tag = "midiCh", onSync))

        if (voiceId == 3) {
            val refRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            refRow.addView(TextView(ctx).apply { text = "Reference: "; setTextColor(0xFF797876.toInt()) })
            val refSpinner = Spinner(ctx).apply { tag = "refVoice" }
            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, listOf("Voice 1", "Voice 2"))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            refSpinner.adapter = adapter
            refRow.addView(refSpinner)
            panel.addView(refRow)
        }
        return panel
    }

    private fun buildIndependentPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "BPM",            20,  300, 120, tag = "indBpm",    onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",        0,  127,  90, tag = "indVel",    onSync))

        // Single octave RangeSlider — matches main voice, one control per voice
        val octLabel = TextView(ctx).apply {
            text = "Octave: 3 \u2013 5"
            setTextColor(0xFFE8E6E1.toInt())
            tag = "tvOctaveLabel"
        }
        val octSlider = com.google.android.material.slider.RangeSlider(ctx).apply {
            tag = "octSlider"
            valueFrom = 0f; valueTo = 8f; stepSize = 1f
            values = listOf(3f, 5f)
            setLabelFormatter { it.toInt().toString() }
        }
        octSlider.addOnChangeListener { slider, _, fromUser ->
            val lo = slider.values[0].toInt(); val hi = slider.values[1].toInt()
            octLabel.text = if (lo == hi) "Octave: $lo" else "Octave: $lo \u2013 $hi"
            if (fromUser && !isUpdatingFromSync) onSync()
        }
        if (voiceId == 2) octaveSliderV2 = octSlider else octaveSliderV3 = octSlider
        panel.addView(octLabel)
        panel.addView(octSlider)

        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)", 0, 16, voiceId + 1, tag = "indMidiCh", onSync))

        // Scale
        val scaleNames = listOf(
            "Chromatic", "Major", "Minor Natural", "Minor Harmonic",
            "Pentatonic Maj", "Pentatonic Min", "Blues", "Dorian", "Mixolydian", "Whole Tone",
            "Kurd (Annaziska)", "Celtic Minor (Amara)", "Pygmy",
            "SaBye / SaByeD", "Aegean (Lydian)", "Hijaz", "Akebono"
        )
        val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        scaleRow.addView(TextView(ctx).apply { text = "Scale: "; setTextColor(0xFFE8E6E1.toInt()) })
        val scaleSpinner = Spinner(ctx).apply { tag = "indScale" }
        scaleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, scaleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scaleRow.addView(scaleSpinner)
        panel.addView(scaleRow)

        // Root note
        val rootNames = listOf("Follow Main", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val rootRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rootRow.addView(TextView(ctx).apply { text = "Root Note: "; setTextColor(0xFFE8E6E1.toInt()) })
        val rootSpinner = Spinner(ctx).apply { tag = "indRoot" }
        rootSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, rootNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rootRow.addView(rootSpinner)
        panel.addView(rootRow)

        // Style
        val styleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        styleRow.addView(TextView(ctx).apply { text = "Style: "; setTextColor(0xFFE8E6E1.toInt()) })
        val styleSpinner = Spinner(ctx).apply { tag = "indStyle" }
        styleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Generative", "Single note Drone", "Evolving Drone"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        styleRow.addView(styleSpinner)
        panel.addView(styleRow)

        // Timing (Generative only)
        val timingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0); tag = "indTimingRow" }
        timingRow.addView(TextView(ctx).apply { text = "Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        val timingSpinner = Spinner(ctx).apply { tag = "indTiming" }
        timingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome", "Mixed", "Randomized", "Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timingRow.addView(timingSpinner)
        panel.addView(timingRow)

        // Drone Timing (Evolving Drone only)
        val droneTimingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0); tag = "indDroneTimingRow"; visibility = View.GONE }
        droneTimingRow.addView(TextView(ctx).apply { text = "Drone Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        val droneTimingSpinner = Spinner(ctx).apply { tag = "indDroneTiming" }
        droneTimingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, listOf("Constant", "Random"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        droneTimingRow.addView(droneTimingSpinner)
        panel.addView(droneTimingRow)

        // Drone Range (Evolving Drone + Random timing only)
        val droneRangeRow = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0); tag = "indDroneRangeRow"; visibility = View.GONE }
        val tvDroneRange = TextView(ctx).apply { text = "Drone beat range: 16 - 64"; setTextColor(0xFFE8E6E1.toInt()); tag = "tvDroneRange" }
        droneRangeRow.addView(tvDroneRange)
        val sbMin = SeekBar(ctx).apply { tag = "droneMin"; max = 127; progress = 15 }
        val sbMax = SeekBar(ctx).apply { tag = "droneMax"; max = 127; progress = 63 }
        fun updateRangeText() {
            tvDroneRange.text = "Drone beat range: ${sbMin.progress + 1} - ${sbMax.progress + 1}"
        }
        sbMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (sbMax.progress < p) sbMax.progress = p
                updateRangeText()
                if (f && !isUpdatingFromSync) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (sbMin.progress > p) sbMin.progress = p
                updateRangeText()
                if (f && !isUpdatingFromSync) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        droneRangeRow.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(ctx).apply { text = "Min: "; setTextColor(0xFF797876.toInt()) })
            addView(sbMin, LinearLayout.LayoutParams(0, -2, 1f))
        })
        droneRangeRow.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(ctx).apply { text = "Max: "; setTextColor(0xFF797876.toInt()) })
            addView(sbMax, LinearLayout.LayoutParams(0, -2, 1f))
        })
        panel.addView(droneRangeRow)

        // Visibility logic
        fun updateIndVisibility() {
            val style      = VoiceStyle.entries[styleSpinner.selectedItemPosition]
            val isSingle   = style == VoiceStyle.SINGLE_NOTE_DRONE
            val isEvolving = style == VoiceStyle.EVOLVING_DRONE
            val isRandomDrone = isEvolving && droneTimingSpinner.selectedItemPosition == 1

            // BPM row: hide for single note drone (it fires once, no loop interval needed).
            panel.findViewWithTag<SeekBar>("indBpm")?.let {
                (it.parent as? View)?.visibility = if (isSingle) View.GONE else View.VISIBLE
            }

            // Octave RangeSlider always visible — used for all drone modes
            panel.findViewWithTag<com.google.android.material.slider.RangeSlider>("octSlider")?.visibility = View.VISIBLE
            panel.findViewWithTag<TextView>("tvOctaveLabel")?.visibility = View.VISIBLE

            // Timing rows
            timingRow.visibility      = if (isSingle || isEvolving) View.GONE else View.VISIBLE
            droneTimingRow.visibility = if (isEvolving)             View.VISIBLE else View.GONE
            droneRangeRow.visibility  = if (isRandomDrone)          View.VISIBLE else View.GONE

            // Scale spinner: not meaningful for single-note-drone with a fixed root,
            // but keep visible since Follow Main mode still uses it conceptually and
            // hiding it could be confusing.
        }

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                updateIndVisibility()
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        droneTimingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                updateIndVisibility()
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Pro settings
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Use Shared Pro Settings", true).apply { tag = "sharedPro" }
        val customProBtn = radioButton(ctx, "Use Custom Pro Settings", false).apply { tag = "customPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(16, 8, 0, 0)
            tag = "customProPanel"
        }
        customProPanel.addView(labeledSeekBar(ctx, "Jitter %", 0, 100, 0, tag = "jitAmt", onSync))

        val jitterRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        jitterRow.addView(TextView(ctx).apply { text = "Jitter Type: "; setTextColor(0xFFE8E6E1.toInt()) })
        val jitterSpinner = Spinner(ctx).apply { tag = "jitType" }
        jitterSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, JitterType.entries.map { it.label })
        jitterRow.addView(jitterSpinner)
        customProPanel.addView(jitterRow)

        val velRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        velRow.addView(TextView(ctx).apply { text = "Velocity Pattern: "; setTextColor(0xFFE8E6E1.toInt()) })
        val velSpinner = Spinner(ctx).apply { tag = "velPattern" }
        velSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, VelocityPattern.entries.map { it.label })
        velRow.addView(velSpinner)
        customProPanel.addView(velRow)

        val eSwitch = Switch(ctx).apply { text = "Enable Euclidean"; tag = "eEnabled"; setTextColor(0xFFE8E6E1.toInt()) }
        customProPanel.addView(eSwitch)
        val eLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(16, 0, 0, 0); tag = "eLayout" }
        eLayout.addView(labeledSeekBar(ctx, "E-Steps",    2, 32, 16, "eSteps",   onSync))
        eLayout.addView(labeledSeekBar(ctx, "E-Density",  1, 16,  5, "eDensity", onSync))
        eLayout.addView(labeledSeekBar(ctx, "E-Rotation", 0, 16,  0, "eRot",     onSync))
        customProPanel.addView(eLayout)
        eSwitch.setOnCheckedChangeListener { _, on -> eLayout.visibility = if (on) View.VISIBLE else View.GONE; if (!isUpdatingFromSync) onSync() }

        val mSwitch = Switch(ctx).apply { text = "Enable Markov"; tag = "mEnabled"; setTextColor(0xFFE8E6E1.toInt()) }
        customProPanel.addView(mSwitch)
        val mLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(16, 0, 0, 0); tag = "mLayout" }
        val mSpinner = Spinner(ctx).apply { tag = "mStyle" }
        mSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, MelodicLogicStyle.entries.map { it.label })
        mLayout.addView(mSpinner)
        customProPanel.addView(mLayout)
        mSwitch.setOnCheckedChangeListener { _, on -> mLayout.visibility = if (on) View.VISIBLE else View.GONE; if (!isUpdatingFromSync) onSync() }

        fun togglePro(shared: Boolean) {
            sharedProBtn.isChecked = shared
            customProBtn.isChecked = !shared
            customProPanel.visibility = if (shared) View.GONE else View.VISIBLE
            if (!isUpdatingFromSync) onSync()
        }
        sharedProBtn.setOnClickListener { togglePro(true) }
        customProBtn.setOnClickListener { togglePro(false) }

        panel.addView(customProPanel)
        return panel
    }

    private fun readHarmonyConfig(panel: LinearLayout, voiceId: Int): HarmonyConfig {
        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        return HarmonyConfig(
            toneStepOffset  = seekVal("toneStep") - 7,
            timeDriftMs     = seekVal("timeDrift").toLong(),
            skipProbability = seekVal("skipPct") / 100f,
            masterVelocity  = seekVal("masterVel"),
            velocityDrift   = seekVal("velDrift"),
            midiChannel     = seekVal("midiCh"),
            referenceVoice  = if (voiceId == 3) (panel.findViewWithTag<Spinner>("refVoice")?.selectedItemPosition ?: 0) + 1 else 1
        )
    }

    private fun readIndependentConfig(panel: LinearLayout): IndependentConfig {
        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        val shared         = panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked ?: true
        val rootPos        = panel.findViewWithTag<Spinner>("indRoot")?.selectedItemPosition ?: 0
        val styleIdx       = panel.findViewWithTag<Spinner>("indStyle")?.selectedItemPosition ?: 0
        val droneTimingIdx = panel.findViewWithTag<Spinner>("indDroneTiming")?.selectedItemPosition ?: 0
        val minBeats       = (panel.findViewWithTag<SeekBar>("droneMin")?.progress ?: 15) + 1
        val maxBeats       = (panel.findViewWithTag<SeekBar>("droneMax")?.progress ?: 63) + 1
        return IndependentConfig(
            bpm           = seekVal("indBpm") + 20,
            velocity      = seekVal("indVel"),
            minOctave     = (if (panel == panelV2) octaveSliderV2 else octaveSliderV3)?.values?.get(0)?.toInt() ?: 3,
            maxOctave     = (if (panel == panelV2) octaveSliderV2 else octaveSliderV3)?.values?.get(1)?.toInt() ?: 5,
            midiChannel   = seekVal("indMidiCh"),
            selectedScale = panel.findViewWithTag<Spinner>("indScale")?.selectedItemPosition ?: 0,
            rootNote      = rootPos,
            timingMode    = panel.findViewWithTag<Spinner>("indTiming")?.selectedItemPosition ?: 0,
            useSharedPro  = shared,
            style         = VoiceStyle.entries.getOrElse(styleIdx) { VoiceStyle.GENERATIVE },
            droneTiming   = DroneTimingMode.entries.getOrElse(droneTimingIdx) { DroneTimingMode.CONSTANT },
            droneMinBeats = minBeats,
            droneMaxBeats = maxBeats,
            proSettings   = ProSettings(
                jitterAmount      = seekVal("jitAmt"),
                jitterType        = JitterType.entries[panel.findViewWithTag<Spinner>("jitType")?.selectedItemPosition ?: 0],
                velocityPattern   = VelocityPattern.entries[panel.findViewWithTag<Spinner>("velPattern")?.selectedItemPosition ?: 0],
                euclideanEnabled  = panel.findViewWithTag<Switch>("eEnabled")?.isChecked ?: false,
                euclideanSteps    = seekVal("eSteps") + 2,
                euclideanDensity  = seekVal("eDensity") + 1,
                euclideanRotation = seekVal("eRot"),
                markovEnabled     = panel.findViewWithTag<Switch>("mEnabled")?.isChecked ?: false,
                melodicLogicStyle = MelodicLogicStyle.entries[panel.findViewWithTag<Spinner>("mStyle")?.selectedItemPosition ?: 0]
            )
        )
    }

    fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!::panelV2.isInitialized) return
        isUpdatingFromSync = true
        currentV2 = v2
        currentV3 = v3
        applyToPanel(panelV2, v2)
        applyToPanel(panelV3, v3)
        isUpdatingFromSync = false
    }

    private fun applyToPanel(panel: LinearLayout, cfg: VoiceConfig) {
        panel.findViewWithTag<Switch>("enable")?.isChecked = cfg.enabled
        val isHarmony = cfg.mode == VoiceMode.HARMONY
        panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked     = isHarmony
        panel.findViewWithTag<RadioButton>("rbIndependent")?.isChecked = !isHarmony

        val harmonyPanel     = panel.findViewWithTag<LinearLayout>("harmonyPanel")
        val independentPanel = panel.findViewWithTag<LinearLayout>("independentPanel")
        harmonyPanel?.visibility     = if (isHarmony) View.VISIBLE else View.GONE
        independentPanel?.visibility = if (isHarmony) View.GONE else View.VISIBLE

        val hc = cfg.harmonyConfig
        harmonyPanel?.findViewWithTag<SeekBar>("toneStep")?.progress  = hc.toneStepOffset + 7
        harmonyPanel?.findViewWithTag<SeekBar>("timeDrift")?.progress = hc.timeDriftMs.toInt()
        harmonyPanel?.findViewWithTag<SeekBar>("skipPct")?.progress   = (hc.skipProbability * 100).toInt()
        harmonyPanel?.findViewWithTag<SeekBar>("masterVel")?.progress = hc.masterVelocity
        harmonyPanel?.findViewWithTag<SeekBar>("velDrift")?.progress  = hc.velocityDrift
        harmonyPanel?.findViewWithTag<SeekBar>("midiCh")?.progress    = hc.midiChannel
        harmonyPanel?.findViewWithTag<Spinner>("refVoice")?.setSelection(hc.referenceVoice - 1)

        val ic = cfg.independentConfig
        independentPanel?.findViewWithTag<SeekBar>("indBpm")?.progress    = (ic.bpm - 20).coerceAtLeast(0)
        independentPanel?.findViewWithTag<SeekBar>("indVel")?.progress    = ic.velocity
        // Update the RangeSlider (single per voice, replaces old two-seekbar pattern)
        val octSlider = if (panel == panelV2) octaveSliderV2 else octaveSliderV3
        octSlider?.values = listOf(ic.minOctave.toFloat().coerceIn(0f, 8f), ic.maxOctave.toFloat().coerceIn(0f, 8f))
        independentPanel?.findViewWithTag<TextView>("tvOctaveLabel")?.text =
            if (ic.minOctave == ic.maxOctave) "Octave: ${ic.minOctave}" else "Octave: ${ic.minOctave} \u2013 ${ic.maxOctave}"
        independentPanel?.findViewWithTag<SeekBar>("indMidiCh")?.progress  = ic.midiChannel
        independentPanel?.findViewWithTag<Spinner>("indScale")?.setSelection(ic.selectedScale)
        independentPanel?.findViewWithTag<Spinner>("indRoot")?.setSelection(ic.rootNote)
        independentPanel?.findViewWithTag<Spinner>("indStyle")?.setSelection(ic.style.ordinal)
        independentPanel?.findViewWithTag<Spinner>("indTiming")?.setSelection(ic.timingMode)
        independentPanel?.findViewWithTag<Spinner>("indDroneTiming")?.setSelection(ic.droneTiming.ordinal)
        independentPanel?.findViewWithTag<SeekBar>("droneMin")?.progress = (ic.droneMinBeats - 1).coerceAtLeast(0)
        independentPanel?.findViewWithTag<SeekBar>("droneMax")?.progress = (ic.droneMaxBeats - 1).coerceAtLeast(0)

        val ps = ic.proSettings
        val shared = ic.useSharedPro
        independentPanel?.findViewWithTag<RadioButton>("sharedPro")?.isChecked = shared
        independentPanel?.findViewWithTag<RadioButton>("customPro")?.isChecked = !shared
        independentPanel?.findViewWithTag<LinearLayout>("customProPanel")?.visibility = if (shared) View.GONE else View.VISIBLE
        independentPanel?.findViewWithTag<SeekBar>("jitAmt")?.progress = ps.jitterAmount
        independentPanel?.findViewWithTag<Spinner>("jitType")?.setSelection(ps.jitterType.ordinal)
        independentPanel?.findViewWithTag<Spinner>("velPattern")?.setSelection(ps.velocityPattern.ordinal)
        independentPanel?.findViewWithTag<Switch>("eEnabled")?.isChecked = ps.euclideanEnabled
        independentPanel?.findViewWithTag<LinearLayout>("eLayout")?.visibility = if (ps.euclideanEnabled) View.VISIBLE else View.GONE
        independentPanel?.findViewWithTag<SeekBar>("eSteps")?.progress   = (ps.euclideanSteps - 2).coerceAtLeast(0)
        independentPanel?.findViewWithTag<SeekBar>("eDensity")?.progress = (ps.euclideanDensity - 1).coerceAtLeast(0)
        independentPanel?.findViewWithTag<SeekBar>("eRot")?.progress     = ps.euclideanRotation
        independentPanel?.findViewWithTag<Switch>("mEnabled")?.isChecked = ps.markovEnabled
        independentPanel?.findViewWithTag<LinearLayout>("mLayout")?.visibility = if (ps.markovEnabled) View.VISIBLE else View.GONE
        independentPanel?.findViewWithTag<Spinner>("mStyle")?.setSelection(ps.melodicLogicStyle.ordinal)

        val enableSwitch = panel.findViewWithTag<Switch>("enable")
        val on = enableSwitch?.isChecked ?: false
        panel.findViewWithTag<LinearLayout>("modeRow")?.let {
            it.alpha = if (on) 1f else 0.4f
            setChildrenEnabled(it, on)
        }
        harmonyPanel?.let     { it.alpha = if (on) 1f else 0.4f; setChildrenEnabled(it, on) }
        independentPanel?.let { it.alpha = if (on) 1f else 0.4f; setChildrenEnabled(it, on) }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(0xFF393836.toInt())
    }

    private fun radioButton(ctx: Context, label: String, checked: Boolean) = RadioButton(ctx).apply {
        text = label; isChecked = checked
        setTextColor(0xFFE8E6E1.toInt())
        setPadding(0, 0, 24, 0)
    }

    private fun labeledSeekBar(
        ctx: Context, labelStr: String, minVal: Int, maxVal: Int, defaultVal: Int,
        tag: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0) }
        val tv  = TextView(ctx).apply { text = "$labelStr: $defaultVal"; setTextColor(0xFFE8E6E1.toInt()) }
        val sb  = SeekBar(ctx).apply {
            this.tag = tag
            max      = maxVal - minVal
            progress = defaultVal - minVal
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$labelStr: ${p + minVal}"
                if (fromUser && !isUpdatingFromSync) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(tv)
        row.addView(sb)
        return row
    }

    private fun setChildrenEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) view.children.forEach { setChildrenEnabled(it, enabled) }
    }

    private val ViewGroup.children: Sequence<View> get() = sequence { for (i in 0 until childCount) yield(getChildAt(i)) }

    private fun attachSyncListeners(
        panel: LinearLayout,
        onSync: () -> Unit,
        skipSeekBars: Boolean = false,
        skipTaggedSpinners: Set<String> = emptySet()
    ) {
        fun walk(vg: ViewGroup) {
            for (i in 0 until vg.childCount) {
                when (val child = vg.getChildAt(i)) {
                    is Switch   -> child.setOnCheckedChangeListener { _, _ -> if (!isUpdatingFromSync) onSync() }
                    is SeekBar  -> if (!skipSeekBars) child.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && !isUpdatingFromSync) onSync() }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                    is Spinner  -> if ((child.tag as? String) !in skipTaggedSpinners)
                        child.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { if (!isUpdatingFromSync) onSync() }
                            override fun onNothingSelected(p0: AdapterView<*>?) {}
                        }
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(panel)
    }
}
