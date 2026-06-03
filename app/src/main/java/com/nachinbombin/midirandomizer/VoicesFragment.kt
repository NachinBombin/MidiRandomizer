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
        attachSyncListeners(independentPanel, { syncConfig() }, skipSeekBars = true)

        return panel
    }

    private fun buildHarmonyPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "Tone Step Offset", -7, 7, 2,   tag = "toneStep", onSync))
        panel.addView(labeledSeekBar(ctx, "Time Drift (ms)",   0, 45, 10,  tag = "timeDrift", onSync))
        panel.addView(labeledSeekBar(ctx, "Skip % (0-100)",    0, 100, 0,  tag = "skipPct", onSync))
        panel.addView(labeledSeekBar(ctx, "Master Velocity",   0, 127, 100,tag = "masterVel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity Drift ±",  0, 20, 8,   tag = "velDrift", onSync))
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

        panel.addView(labeledSeekBar(ctx, "BPM",            20,  300, 120, tag = "indBpm", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",        0,  127,  90, tag = "indVel", onSync))
        panel.addView(labeledSeekBar(ctx, "Min Octave",      0,    8,   3, tag = "indMinOct", onSync))
        panel.addView(labeledSeekBar(ctx, "Max Octave",      0,    8,   5, tag = "indMaxOct", onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)", 0, 16, voiceId + 1, tag = "indMidiCh", onSync))

        // Scale dropdown
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

        // Root note dropdown
        // Position 0 = "Follow Main" (rootNote stored as 0)
        // Positions 1-12 = C..B     (rootNote stored as 1..12; semitone = rootNote-1)
        val rootNames = listOf(
            "Follow Main",
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
        )
        val rootRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rootRow.addView(TextView(ctx).apply { text = "Root Note: "; setTextColor(0xFFE8E6E1.toInt()) })
        val rootSpinner = Spinner(ctx).apply { tag = "indRoot" }
        rootSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, rootNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rootRow.addView(rootSpinner)
        panel.addView(rootRow)

        // Timing dropdown
        val timingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        timingRow.addView(TextView(ctx).apply { text = "Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        val timingSpinner = Spinner(ctx).apply { tag = "indTiming" }
        timingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome", "Mixed", "Randomized", "Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timingRow.addView(timingSpinner)
        panel.addView(timingRow)

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
        val eLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(16,0,0,0); tag = "eLayout" }
        eLayout.addView(labeledSeekBar(ctx, "E-Steps",   2, 32, 16, "eSteps",   onSync))
        eLayout.addView(labeledSeekBar(ctx, "E-Density", 1, 16,  5, "eDensity", onSync))
        eLayout.addView(labeledSeekBar(ctx, "E-Rotation",0, 16,  0, "eRot",     onSync))
        customProPanel.addView(eLayout)
        eSwitch.setOnCheckedChangeListener { _, on -> eLayout.visibility = if (on) View.VISIBLE else View.GONE; if (!isUpdatingFromSync) onSync() }

        val mSwitch = Switch(ctx).apply { text = "Enable Markov"; tag = "mEnabled"; setTextColor(0xFFE8E6E1.toInt()) }
        customProPanel.addView(mSwitch)
        val mLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(16,0,0,0); tag = "mLayout" }
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
            toneStepOffset = seekVal("toneStep") - 7,
            timeDriftMs = seekVal("timeDrift").toLong(),
            skipProbability = seekVal("skipPct") / 100f,
            masterVelocity = seekVal("masterVel"),
            velocityDrift = seekVal("velDrift"),
            midiChannel = seekVal("midiCh"),
            referenceVoice = if (voiceId == 3) (panel.findViewWithTag<Spinner>("refVoice")?.selectedItemPosition ?: 0) + 1 else 1
        )
    }

    private fun readIndependentConfig(panel: LinearLayout): IndependentConfig {
        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        val shared = panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked ?: true
        // rootSpinner position 0 = Follow Main (stored as 0), positions 1-12 = C..B (stored as 1..12)
        val rootPos = panel.findViewWithTag<Spinner>("indRoot")?.selectedItemPosition ?: 0
        return IndependentConfig(
            bpm          = seekVal("indBpm") + 20,
            velocity     = seekVal("indVel"),
            minOctave    = seekVal("indMinOct"),
            maxOctave    = seekVal("indMaxOct"),
            midiChannel  = seekVal("indMidiCh"),
            selectedScale = panel.findViewWithTag<Spinner>("indScale")?.selectedItemPosition ?: 0,
            rootNote     = rootPos,
            timingMode   = panel.findViewWithTag<Spinner>("indTiming")?.selectedItemPosition ?: 0,
            useSharedPro = shared,
            proSettings  = ProSettings(
                jitterAmount    = seekVal("jitAmt"),
                jitterType      = JitterType.entries[panel.findViewWithTag<Spinner>("jitType")?.selectedItemPosition ?: 0],
                velocityPattern = VelocityPattern.entries[panel.findViewWithTag<Spinner>("velPattern")?.selectedItemPosition ?: 0],
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
        panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked     = cfg.mode == VoiceMode.HARMONY
        panel.findViewWithTag<RadioButton>("rbIndependent")?.isChecked = cfg.mode == VoiceMode.INDEPENDENT

        panel.findViewWithTag<View>("harmonyPanel")?.visibility     = if (cfg.mode == VoiceMode.HARMONY)      View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("independentPanel")?.visibility = if (cfg.mode == VoiceMode.INDEPENDENT)  View.VISIBLE else View.GONE

        if (cfg.mode == VoiceMode.HARMONY) {
            panel.findViewWithTag<SeekBar>("masterVel")?.progress = cfg.harmonyConfig.masterVelocity
            panel.findViewWithTag<SeekBar>("midiCh")?.progress    = cfg.harmonyConfig.midiChannel
        } else {
            val ic = cfg.independentConfig
            panel.findViewWithTag<SeekBar>("indVel")?.progress    = ic.velocity
            panel.findViewWithTag<SeekBar>("indMidiCh")?.progress = ic.midiChannel
            // Sync root spinner: rootNote 0 -> position 0; 1..12 -> positions 1..12
            panel.findViewWithTag<Spinner>("indRoot")?.setSelection(ic.rootNote.coerceIn(0, 12))
            panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked  = ic.useSharedPro
            panel.findViewWithTag<RadioButton>("customPro")?.isChecked  = !ic.useSharedPro
            panel.findViewWithTag<View>("customProPanel")?.visibility   = if (ic.useSharedPro) View.GONE else View.VISIBLE
        }
    }

    private fun radioButton(ctx: Context, text: String, checked: Boolean) =
        RadioButton(ctx).apply { this.text = text; isChecked = checked; setTextColor(0xFFE8E6E1.toInt()) }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).also { it.setMargins(0, 24, 0, 24) }
        setBackgroundColor(0xFF444444.toInt())
    }

    private fun labeledSeekBar(ctx: Context, label: String, min: Int, max: Int, default: Int, tag: String, onSync: () -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 4) }
        val tv = TextView(ctx).apply { text = "$label: $default"; minWidth = 350; setTextColor(0xFFE8E6E1.toInt()) }
        val sb = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min; this.progress = default - min
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    tv.text = "$label: ${p + min}"
                    if (f && !isUpdatingFromSync) onSync()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(tv); row.addView(sb)
        return row
    }

    private fun setChildrenEnabled(group: ViewGroup, enabled: Boolean) {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i); c.isEnabled = enabled
            if (c is ViewGroup) setChildrenEnabled(c, enabled)
        }
    }

    private fun attachSyncListeners(group: ViewGroup, onChange: () -> Unit, skipSeekBars: Boolean) {
        for (i in 0 until group.childCount) {
            when (val c = group.getChildAt(i)) {
                is SeekBar -> if (!skipSeekBars) c.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if (f) onChange() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                is Spinner -> c.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(a: AdapterView<*>?, v: View?, p: Int, id: Long) { if (!isUpdatingFromSync) onChange() }
                    override fun onNothingSelected(a: AdapterView<*>?) {}
                }
                is RadioButton -> c.setOnCheckedChangeListener { _, checked -> if (checked && !isUpdatingFromSync) onChange() }
                is ViewGroup -> attachSyncListeners(c, onChange, skipSeekBars)
            }
        }
    }
}
