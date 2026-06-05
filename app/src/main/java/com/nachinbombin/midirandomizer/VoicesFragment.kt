package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment – manages V2 and V3 panels.
 *
 * Each voice can run in HARMONY mode (follows V1) or INDEPENDENT mode (own engine).
 * In INDEPENDENT mode the user can optionally attach custom Pro Settings via an
 * embedded child ProSettingsFragment.
 */
class VoicesFragment : Fragment(), MidiService.MidiEventListener {

    // ── Host interface (implemented by MainActivity) ──────────────────────
    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    private var serviceProvider: ServiceProvider? = null

    // ── Per-voice full VoiceConfig state ─────────────────────────────────
    private var currentV2 = VoiceConfig()
    private var currentV3 = VoiceConfig()
    private var isUpdatingFromSync = false

    // ── Child ProSettingsFragments for custom-pro panels ─────────────────
    // proFragV2/V3 are kept for future resync (e.g. setInitialSettings on restore).
    private var proFragV2: ProSettingsFragment? = null
    private var proFragV3: ProSettingsFragment? = null
    private var customProSettingsV2: ProSettings = ProSettings()
    private var customProSettingsV3: ProSettings = ProSettings()

    private lateinit var panelV2: LinearLayout
    private lateinit var panelV3: LinearLayout

    // ── Lifecycle ─────────────────────────────────────────────────────────

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

    override fun onVoiceParamsChanged(
        v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig
    ) = syncFromService(v1, v2, v3)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF111318.toInt())
        }
        val column = LinearLayout(ctx).apply {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        attachProFragment(2)
        attachProFragment(3)
        ThemeManager.applyToView(view, ThemeManager.loadTheme(requireContext()), forVoices = true)
    }

    override fun onResume() {
        super.onResume()
        view?.let { ThemeManager.applyToView(it, ThemeManager.loadTheme(requireContext()), forVoices = true) }
    }

    // ── Child ProSettingsFragment management ──────────────────────────────

    private fun proFragContainerId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3

    private fun attachProFragment(voiceId: Int) {
        val containerId = proFragContainerId(voiceId)
        val tag = "proFrag_v$voiceId"
        val existing = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment
        val frag = existing ?: ProSettingsFragment()

        // FIX Bug 3: assign the ref BEFORE calling setInitialSettings so the
        // variable is valid if anything reads it during initialisation.
        if (voiceId == 2) proFragV2 = frag else proFragV3 = frag

        val initSettings = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        frag.setInitialSettings(initSettings)
        frag.setListener { settings ->
            if (voiceId == 2) customProSettingsV2 = settings else customProSettingsV3 = settings
            pushConfigToService(voiceId)
        }

        if (existing == null) {
            childFragmentManager.beginTransaction()
                .add(containerId, frag, tag)
                .commitAllowingStateLoss()
        }
    }

    // ── Voice panel builder ───────────────────────────────────────────────

    private fun buildVoicePanel(voiceId: Int, label: String): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            tag = "root$voiceId"
        }

        // Header + enable switch
        val enableSwitch = Switch(ctx).apply {
            text = label
            isChecked = false
            textSize  = 18f
            setTextColor(0xFFE8E6E1.toInt())
            tag = "enable"
        }
        panel.addView(enableSwitch)

        // Mode radio row
        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
            tag = "modeRow"
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true ).apply { tag = "rbHarmony" }
        val independentBtn = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        modeRow.addView(harmonyBtn)
        modeRow.addView(independentBtn)
        panel.addView(modeRow)

        var syncConfigAction: (() -> Unit)? = null

        val harmonyPanel = buildHarmonyPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "harmonyPanel" }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "independentPanel"; visibility = View.GONE }

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
                enabled           = enableSwitch.isChecked,
                mode              = if (harmonyBtn.isChecked) VoiceMode.HARMONY else VoiceMode.INDEPENDENT,
                harmonyConfig     = readHarmonyConfig(harmonyPanel, voiceId),
                independentConfig = readIndependentConfig(independentPanel, voiceId)
            )
            if (voiceId == 2) { currentV2 = cfg; svc.updateVoice2Config(cfg) }
            else              { currentV3 = cfg; svc.updateVoice3Config(cfg) }
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

        // FIX Bug 1: was skipSeekBars=true on both calls, which caused the
        // condition `view is Spinner && !skipSeekBars` to always be false,
        // so ALL spinner changes were silently ignored. Changed to false so
        // scale / timing / style / root spinners fire syncConfig correctly.
        attachSyncListeners(harmonyPanel,     { syncConfig() }, skipSeekBars = false)
        attachSyncListeners(independentPanel, { syncConfig() }, skipSeekBars = false)

        return panel
    }

    // ── Panel builders ────────────────────────────────────────────────────

    private fun buildHarmonyPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "Tone Step Offset",      -7, 7,   2,   "toneStep",  onSync))
        panel.addView(labeledSeekBar(ctx, "Time Drift (ms)",         0, 45, 10,  "timeDrift", onSync))
        panel.addView(labeledSeekBar(ctx, "Skip % (0-100)",          0, 100, 0,  "skipPct",   onSync))
        panel.addView(labeledSeekBar(ctx, "Master Velocity",         0, 127, 100,"masterVel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity Drift ±",        0, 20,  8,  "velDrift",  onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)",   0, 16,  0,  "midiCh",   onSync))

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

        panel.addView(labeledSeekBar(ctx, "BPM",                    20, 300, 120, "indBpm",    onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",                0, 127,  90, "indVel",    onSync))
        panel.addView(labeledSeekBar(ctx, "Min Octave",              0, 8,     3, "indMinOct", onSync))
        panel.addView(labeledSeekBar(ctx, "Max Octave",              0, 8,     5, "indMaxOct", onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)",   0, 16,    0, "indMidiCh", onSync))

        val scaleNames = listOf("Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone",
            "Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")
        val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        scaleRow.addView(TextView(ctx).apply { text = "Scale: "; setTextColor(0xFFE8E6E1.toInt()) })
        val scaleSpinner = Spinner(ctx).apply { tag = "indScale" }
        scaleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, scaleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scaleRow.addView(scaleSpinner)
        panel.addView(scaleRow)

        val timingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        timingRow.addView(TextView(ctx).apply { text = "Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        val timingSpinner = Spinner(ctx).apply { tag = "indTiming" }
        timingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome","Mixed","Randomized","Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timingRow.addView(timingSpinner)
        panel.addView(timingRow)

        val styleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        styleRow.addView(TextView(ctx).apply { text = "Style: "; setTextColor(0xFFE8E6E1.toInt()) })
        val styleSpinner = Spinner(ctx).apply { tag = "indStyle" }
        styleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Generative","Single-Note Drone","Evolving Drone"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        styleRow.addView(styleSpinner)
        panel.addView(styleRow)

        val rootRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rootRow.addView(TextView(ctx).apply { text = "Root: "; setTextColor(0xFFE8E6E1.toInt()) })
        val rootSpinner = Spinner(ctx).apply { tag = "indRoot" }
        rootSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rootRow.addView(rootSpinner)
        panel.addView(rootRow)

        // ── Pro Settings section ────────────────────────────────────────
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro Settings", true).apply  { tag = "sharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro Settings", false).apply { tag = "customPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        // Custom-pro panel: inline basic controls + embedded ProSettingsFragment for advanced engines
        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 4, 0, 0)
            visibility  = View.GONE
            tag = "customProPanel"
        }
        customProPanel.addView(labeledSeekBar(ctx, "Jitter %",        0, 100, 0,  "jitAmt",  onSync))

        val jitRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 0) }
        jitRow.addView(TextView(ctx).apply { text = "Jitter Type: "; setTextColor(0xFF797876.toInt()) })
        val jitSpinner = Spinner(ctx).apply { tag = "jitType" }
        jitSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            JitterType.entries.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        jitRow.addView(jitSpinner)
        customProPanel.addView(jitRow)

        val velRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 0) }
        velRow.addView(TextView(ctx).apply { text = "Velocity Pattern: "; setTextColor(0xFF797876.toInt()) })
        val velSpinner = Spinner(ctx).apply { tag = "velPattern" }
        velSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            VelocityPattern.entries.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        velRow.addView(velSpinner)
        customProPanel.addView(velRow)

        val eSwitch = Switch(ctx).apply { text = "Enable Euclidean"; tag = "eEnabled"; setTextColor(0xFFE8E6E1.toInt()) }
        customProPanel.addView(eSwitch)
        val eLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        eLayout.addView(labeledSeekBar(ctx, "Steps (2-32)",    0, 30, 14, "eSteps",   onSync))
        eLayout.addView(labeledSeekBar(ctx, "Density (1-32)",  0, 31,  7, "eDensity", onSync))
        eLayout.addView(labeledSeekBar(ctx, "Rotation",        0, 31,  0, "eRot",     onSync))
        customProPanel.addView(eLayout)

        eSwitch.setOnCheckedChangeListener { _, on ->
            eLayout.visibility = if (on) View.VISIBLE else View.GONE
            if (!isUpdatingFromSync) onSync()
        }

        val mSwitch = Switch(ctx).apply { text = "Enable Markov"; tag = "mEnabled"; setTextColor(0xFFE8E6E1.toInt()) }
        customProPanel.addView(mSwitch)
        val mLayout = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        mLayout.addView(TextView(ctx).apply { text = "Melodic Style: "; setTextColor(0xFF797876.toInt()) })
        val mSpinner = Spinner(ctx).apply { tag = "mStyle" }
        mSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            MelodicLogicStyle.entries.map { it.name })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        mLayout.addView(mSpinner)
        customProPanel.addView(mLayout)

        mSwitch.setOnCheckedChangeListener { _, on ->
            mLayout.visibility = if (on) View.VISIBLE else View.GONE
            if (!isUpdatingFromSync) onSync()
        }

        // ── Child ProSettingsFragment container (for advanced engine UIs) ──
        val advancedLabel = TextView(ctx).apply {
            text = "Advanced Engine Settings:"
            textSize = 13f
            setPadding(0, 16, 0, 4)
            setTextColor(0xFF797876.toInt())
            tag = "advancedLabel"
            visibility = View.GONE
        }
        customProPanel.addView(advancedLabel)

        val proFragContainer = android.widget.FrameLayout(ctx).apply {
            id = proFragContainerId(voiceId)
            visibility = View.GONE
            tag = "proFragContainer$voiceId"
        }
        customProPanel.addView(proFragContainer)

        panel.addView(customProPanel)

        fun togglePro(shared: Boolean) {
            sharedProBtn.isChecked = shared
            customProBtn.isChecked = !shared
            customProPanel.visibility   = if (shared) View.GONE else View.VISIBLE
            proFragContainer.visibility = if (shared) View.GONE else View.VISIBLE
            advancedLabel.visibility    = if (shared) View.GONE else View.VISIBLE
            if (!isUpdatingFromSync) onSync()
        }
        sharedProBtn.setOnClickListener { togglePro(true)  }
        customProBtn.setOnClickListener { togglePro(false) }

        return panel
    }

    // ── Config read helpers ───────────────────────────────────────────────

    private fun readHarmonyConfig(panel: LinearLayout, voiceId: Int): HarmonyConfig {
        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        return HarmonyConfig(
            toneStepOffset  = seekVal("toneStep") - 7,
            timeDriftMs     = seekVal("timeDrift").toLong(),
            skipProbability = seekVal("skipPct") / 100f,
            masterVelocity  = seekVal("masterVel"),
            velocityDrift   = seekVal("velDrift"),
            midiChannel     = seekVal("midiCh"),
            referenceVoice  = if (voiceId == 3)
                (panel.findViewWithTag<Spinner>("refVoice")?.selectedItemPosition ?: 0) + 1
            else 1
        )
    }

    private fun readIndependentConfig(panel: LinearLayout, voiceId: Int): IndependentConfig {
        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        val shared = panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked ?: true

        // FIX Bug 2: previously this rebuilt a full inline ProSettings() even when
        // custom pro was on, discarding all advanced engine settings (Narmour,
        // Gesture curves, NRT, L-System, etc.) stored by the child
        // ProSettingsFragment in customProSettingsV2/V3.
        //
        // Now when !shared we start from the child fragment's full ProSettings
        // and only override the fields that the inline basic controls own
        // (jitter, euclidean, markov, velocity pattern).  This merges both
        // sources correctly without losing any advanced engine config.
        val baseCustomPro = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        val resolvedPro: ProSettings = if (shared) {
            ProSettings()
        } else {
            baseCustomPro.copy(
                jitterAmount      = seekVal("jitAmt"),
                jitterType        = JitterType.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("jitType")?.selectedItemPosition ?: 0
                ) { JitterType.NONE },
                velocityPattern   = VelocityPattern.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("velPattern")?.selectedItemPosition ?: 0
                ) { VelocityPattern.RANDOM },
                euclideanEnabled  = panel.findViewWithTag<Switch>("eEnabled")?.isChecked ?: false,
                euclideanSteps    = seekVal("eSteps") + 2,
                euclideanDensity  = seekVal("eDensity") + 1,
                euclideanRotation = seekVal("eRot"),
                markovEnabled     = panel.findViewWithTag<Switch>("mEnabled")?.isChecked ?: false,
                melodicLogicStyle = MelodicLogicStyle.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("mStyle")?.selectedItemPosition ?: 0
                ) { MelodicLogicStyle.STEPWISE }
            )
        }

        return IndependentConfig(
            bpm           = seekVal("indBpm") + 20,
            velocity      = seekVal("indVel"),
            minOctave     = seekVal("indMinOct"),
            maxOctave     = seekVal("indMaxOct"),
            midiChannel   = seekVal("indMidiCh"),
            selectedScale = panel.findViewWithTag<Spinner>("indScale")?.selectedItemPosition ?: 0,
            timingMode    = panel.findViewWithTag<Spinner>("indTiming")?.selectedItemPosition ?: 0,
            style         = VoiceStyle.entries.getOrElse(
                panel.findViewWithTag<Spinner>("indStyle")?.selectedItemPosition ?: 0
            ) { VoiceStyle.GENERATIVE },
            rootNote      = panel.findViewWithTag<Spinner>("indRoot")?.selectedItemPosition ?: 0,
            useSharedPro  = shared,
            proSettings   = resolvedPro
        )
    }

    // ── Sync from service ─────────────────────────────────────────────────

    fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!::panelV2.isInitialized) return
        isUpdatingFromSync = true
        currentV2 = v2; currentV3 = v3
        applyToPanel(panelV2, v2)
        applyToPanel(panelV3, v3)
        isUpdatingFromSync = false
    }

    private fun applyToPanel(panel: LinearLayout, cfg: VoiceConfig) {
        panel.findViewWithTag<Switch>("enable")?.isChecked               = cfg.enabled
        panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked        = cfg.mode == VoiceMode.HARMONY
        panel.findViewWithTag<RadioButton>("rbIndependent")?.isChecked    = cfg.mode == VoiceMode.INDEPENDENT
        panel.findViewWithTag<View>("harmonyPanel")?.visibility            =
            if (cfg.mode == VoiceMode.HARMONY)     View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("independentPanel")?.visibility        =
            if (cfg.mode == VoiceMode.INDEPENDENT) View.VISIBLE else View.GONE

        if (cfg.mode == VoiceMode.HARMONY) {
            panel.findViewWithTag<SeekBar>("masterVel")?.progress = cfg.harmonyConfig.masterVelocity
            panel.findViewWithTag<SeekBar>("midiCh")?.progress    = cfg.harmonyConfig.midiChannel
        } else {
            val ic = cfg.independentConfig
            panel.findViewWithTag<SeekBar>("indVel")?.progress     = ic.velocity
            panel.findViewWithTag<SeekBar>("indMidiCh")?.progress  = ic.midiChannel
            panel.findViewWithTag<Spinner>("indStyle")?.setSelection(ic.style.ordinal)
            panel.findViewWithTag<Spinner>("indRoot")?.setSelection(ic.rootNote)
            panel.findViewWithTag<Spinner>("indScale")?.setSelection(ic.selectedScale)
            panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked = ic.useSharedPro
            panel.findViewWithTag<RadioButton>("customPro")?.isChecked = !ic.useSharedPro
            panel.findViewWithTag<View>("customProPanel")?.visibility  =
                if (ic.useSharedPro) View.GONE else View.VISIBLE
            // Push stored custom pro settings back into the child fragment on resync
            if (!ic.useSharedPro) {
                val frag = if (panel === panelV2) proFragV2 else proFragV3
                frag?.setInitialSettings(ic.proSettings)
            }
        }
    }

    // ── Push current config to service (called by child ProSettings) ──────

    private fun pushConfigToService(voiceId: Int) {
        if (isUpdatingFromSync) return
        val svc   = serviceProvider?.getMidiService() ?: return
        val panel = if (voiceId == 2) panelV2 else panelV3
        val enableSwitch     = panel.findViewWithTag<Switch>("enable")
        val harmonyBtn       = panel.findViewWithTag<RadioButton>("rbHarmony")
        val harmonyPanel     = panel.findViewWithTag<LinearLayout>("harmonyPanel") ?: return
        val independentPanel = panel.findViewWithTag<LinearLayout>("independentPanel") ?: return
        val cfg = VoiceConfig(
            enabled           = enableSwitch?.isChecked ?: false,
            mode              = if (harmonyBtn?.isChecked == true) VoiceMode.HARMONY else VoiceMode.INDEPENDENT,
            harmonyConfig     = readHarmonyConfig(harmonyPanel, voiceId),
            independentConfig = readIndependentConfig(independentPanel, voiceId)
        )
        if (voiceId == 2) { currentV2 = cfg; svc.updateVoice2Config(cfg) }
        else              { currentV3 = cfg; svc.updateVoice3Config(cfg) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun radioButton(ctx: Context, text: String, checked: Boolean) =
        RadioButton(ctx).apply { this.text = text; isChecked = checked; setTextColor(0xFFE8E6E1.toInt()) }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
            .also { it.setMargins(0, 24, 0, 24) }
        setBackgroundColor(0xFF444444.toInt())
    }

    private fun labeledSeekBar(
        ctx: Context, label: String, min: Int, max: Int, default: Int,
        tag: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 4) }
        val tv  = TextView(ctx).apply {
            text = "$label: $default"; minWidth = 350; setTextColor(0xFFE8E6E1.toInt())
        }
        val sb  = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min; progress = default - min
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${p + min}"
                if (fromUser) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?)  {}
        })
        row.addView(tv); row.addView(sb)
        return row
    }

    private fun attachSyncListeners(
        view: View, onSync: () -> Unit, skipSeekBars: Boolean
    ) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) attachSyncListeners(view.getChildAt(i), onSync, skipSeekBars)
        }
        when {
            // skipSeekBars=false means spinners DO get listeners (used for harmony + independent panels).
            // The parameter name is intentionally kept to match the original signature.
            view is Spinner && !skipSeekBars -> view.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(a: AdapterView<*>?, v: View?, p: Int, id: Long) {
                        if (!isUpdatingFromSync) onSync()
                    }
                    override fun onNothingSelected(a: AdapterView<*>?) {}
                }
            view is Switch -> view.setOnCheckedChangeListener { _, _ ->
                if (!isUpdatingFromSync) onSync()
            }
        }
    }

    private fun setChildrenEnabled(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        if (v is ViewGroup) for (i in 0 until v.childCount) setChildrenEnabled(v.getChildAt(i), enabled)
    }
}
