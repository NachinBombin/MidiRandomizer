package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment – manages V2 and V3 panels.
 *
 * Each voice can run in three modes:
 *   HARMONY    – follows V1 with interval offset + drift
 *   INDEPENDENT – own generative engine (Generative / Drone / Chords)
 *   MELODIC    – own melodic engine (shares Independent params but uses
 *                Melodic mode label; reserved for chord-aware melody engine)
 *
 * Pro Settings for Independent/Melodic voices are managed entirely by the
 * embedded child ProSettingsFragment — there are NO duplicate inline
 * pro controls in this fragment.
 */
class VoicesFragment : Fragment(), MidiService.MidiEventListener {

    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    private var serviceProvider: ServiceProvider? = null

    private var currentV2 = VoiceConfig()
    private var currentV3 = VoiceConfig()
    private var isUpdatingFromSync = false

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

        // Mode radio row: Harmony | Independent | Melodic
        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
            tag = "modeRow"
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true ).apply { tag = "rbHarmony" }
        val independentBtn = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        val melodicBtn     = radioButton(ctx, "Melodic",     false).apply { tag = "rbMelodic" }
        modeRow.addView(harmonyBtn)
        modeRow.addView(independentBtn)
        modeRow.addView(melodicBtn)
        panel.addView(modeRow)

        var syncConfigAction: (() -> Unit)? = null

        val harmonyPanel     = buildHarmonyPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "harmonyPanel" }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "independentPanel"; visibility = View.GONE }
        val melodicPanel     = buildMelodicPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "melodicPanel"; visibility = View.GONE }

        panel.addView(harmonyPanel)
        panel.addView(independentPanel)
        panel.addView(melodicPanel)

        fun setEnabledState(on: Boolean) {
            modeRow.alpha          = if (on) 1f else 0.4f
            harmonyPanel.alpha     = if (on) 1f else 0.4f
            independentPanel.alpha = if (on) 1f else 0.4f
            melodicPanel.alpha     = if (on) 1f else 0.4f
            setChildrenEnabled(modeRow, on)
            setChildrenEnabled(harmonyPanel, on)
            setChildrenEnabled(independentPanel, on)
            setChildrenEnabled(melodicPanel, on)
        }
        setEnabledState(false)

        fun syncConfig() {
            if (isUpdatingFromSync) return
            val svc = serviceProvider?.getMidiService() ?: return
            val mode = when {
                harmonyBtn.isChecked     -> VoiceMode.HARMONY
                independentBtn.isChecked -> VoiceMode.INDEPENDENT
                else                     -> VoiceMode.MELODIC
            }
            val cfg = VoiceConfig(
                enabled           = enableSwitch.isChecked,
                mode              = mode,
                harmonyConfig     = readHarmonyConfig(harmonyPanel, voiceId),
                independentConfig = readIndependentConfig(
                    if (mode == VoiceMode.MELODIC) melodicPanel else independentPanel,
                    voiceId
                )
            )
            if (voiceId == 2) { currentV2 = cfg; svc.updateVoice2Config(cfg) }
            else              { currentV3 = cfg; svc.updateVoice3Config(cfg) }
        }
        syncConfigAction = { syncConfig() }

        enableSwitch.setOnCheckedChangeListener { _, on ->
            setEnabledState(on)
            syncConfig()
        }

        fun switchMode(mode: VoiceMode) {
            harmonyBtn.isChecked     = mode == VoiceMode.HARMONY
            independentBtn.isChecked = mode == VoiceMode.INDEPENDENT
            melodicBtn.isChecked     = mode == VoiceMode.MELODIC
            harmonyPanel.visibility     = if (mode == VoiceMode.HARMONY)     View.VISIBLE else View.GONE
            independentPanel.visibility = if (mode == VoiceMode.INDEPENDENT) View.VISIBLE else View.GONE
            melodicPanel.visibility     = if (mode == VoiceMode.MELODIC)     View.VISIBLE else View.GONE
            syncConfig()
        }

        harmonyBtn.setOnClickListener     { switchMode(VoiceMode.HARMONY) }
        independentBtn.setOnClickListener { switchMode(VoiceMode.INDEPENDENT) }
        melodicBtn.setOnClickListener     { switchMode(VoiceMode.MELODIC) }

        attachSyncListeners(harmonyPanel,     { syncConfig() }, skipSeekBars = false)
        attachSyncListeners(independentPanel, { syncConfig() }, skipSeekBars = false)
        attachSyncListeners(melodicPanel,     { syncConfig() }, skipSeekBars = false)

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

    /**
     * Independent panel — core parameters + Style spinner (Generative / Drone / Evolving Drone / Chords)
     * + conditional Chords sub-panel.
     * ALL advanced Pro Settings are handled exclusively by the embedded
     * ProSettingsFragment (attached in onViewCreated → attachProFragment).
     * There are intentionally NO duplicate Jitter / Euclidean / Markov controls here.
     */
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
            listOf("Generative","Single-Note Drone","Evolving Drone","Chords"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        styleRow.addView(styleSpinner)
        panel.addView(styleRow)

        // Chords sub-panel — visible only when Style = "Chords" (index 3)
        val chordsPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 4, 0, 0)
            visibility = View.GONE
            tag = "chordsPanel"
        }
        val chordTypeRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 0) }
        chordTypeRow.addView(TextView(ctx).apply { text = "Chord Type: "; setTextColor(0xFF797876.toInt()) })
        val chordTypeSpinner = Spinner(ctx).apply { tag = "chordType" }
        chordTypeSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Triad","7th","Suspended","Power Chord"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        chordTypeRow.addView(chordTypeSpinner)
        chordsPanel.addView(chordTypeRow)

        val chordRhythmRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 0) }
        chordRhythmRow.addView(TextView(ctx).apply { text = "Chord Rhythm: "; setTextColor(0xFF797876.toInt()) })
        val chordRhythmSpinner = Spinner(ctx).apply { tag = "chordRhythm" }
        chordRhythmSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("On Beat","Syncopated","Arpeggio"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        chordRhythmRow.addView(chordRhythmSpinner)
        chordsPanel.addView(chordRhythmRow)
        panel.addView(chordsPanel)

        styleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                chordsPanel.visibility = if (pos == VoiceStyle.CHORDS.ordinal) View.VISIBLE else View.GONE
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }

        val rootRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rootRow.addView(TextView(ctx).apply { text = "Root: "; setTextColor(0xFFE8E6E1.toInt()) })
        val rootSpinner = Spinner(ctx).apply { tag = "indRoot" }
        rootSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rootRow.addView(rootSpinner)
        panel.addView(rootRow)

        // ── Pro Settings: toggle between Shared and Custom ────────────────
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro Settings", true).apply  { tag = "sharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro Settings", false).apply { tag = "customPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        // Custom pro panel: ONLY the ProSettingsFragment container — no duplicates
        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 0)
            visibility  = View.GONE
            tag = "customProPanel"
        }
        val advancedLabel = TextView(ctx).apply {
            text = "Advanced Engine Settings:"
            textSize = 13f
            setPadding(0, 8, 0, 4)
            setTextColor(0xFF797876.toInt())
        }
        customProPanel.addView(advancedLabel)

        val proFragContainer = android.widget.FrameLayout(ctx).apply {
            id = proFragContainerId(voiceId)
            tag = "proFragContainer$voiceId"
        }
        customProPanel.addView(proFragContainer)
        panel.addView(customProPanel)

        fun togglePro(shared: Boolean) {
            sharedProBtn.isChecked    = shared
            customProBtn.isChecked    = !shared
            customProPanel.visibility = if (shared) View.GONE else View.VISIBLE
            if (!isUpdatingFromSync) onSync()
        }
        sharedProBtn.setOnClickListener { togglePro(true)  }
        customProBtn.setOnClickListener { togglePro(false) }

        return panel
    }

    /**
     * Melodic panel — same core parameters as Independent, but tagged for
     * VoiceMode.MELODIC. Uses the same Pro Settings fragment container.
     * Chord-aware melody engine will be wired here in the chord update phase.
     */
    private fun buildMelodicPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "BPM",                    20, 300, 120, "melBpm",    onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",                0, 127,  90, "melVel",    onSync))
        panel.addView(labeledSeekBar(ctx, "Min Octave",              0, 8,     3, "melMinOct", onSync))
        panel.addView(labeledSeekBar(ctx, "Max Octave",              0, 8,     5, "melMaxOct", onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)",   0, 16,    0, "melMidiCh", onSync))

        val scaleNames = listOf("Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone",
            "Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")
        val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        scaleRow.addView(TextView(ctx).apply { text = "Scale: "; setTextColor(0xFFE8E6E1.toInt()) })
        val scaleSpinner = Spinner(ctx).apply { tag = "melScale" }
        scaleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, scaleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scaleRow.addView(scaleSpinner)
        panel.addView(scaleRow)

        val timingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        timingRow.addView(TextView(ctx).apply { text = "Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        val timingSpinner = Spinner(ctx).apply { tag = "melTiming" }
        timingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome","Mixed","Randomized","Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timingRow.addView(timingSpinner)
        panel.addView(timingRow)

        val rootRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rootRow.addView(TextView(ctx).apply { text = "Root: "; setTextColor(0xFFE8E6E1.toInt()) })
        val rootSpinner = Spinner(ctx).apply { tag = "melRoot" }
        rootSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rootRow.addView(rootSpinner)
        panel.addView(rootRow)

        // Pro Settings toggle (shared vs custom) — same fragment container
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro Settings", true).apply  { tag = "melSharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro Settings", false).apply { tag = "melCustomPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 0)
            visibility  = View.GONE
            tag = "melCustomProPanel"
        }
        val advancedLabel = TextView(ctx).apply {
            text = "Melodic Engine Settings (Pro):"
            textSize = 13f
            setPadding(0, 8, 0, 4)
            setTextColor(0xFF797876.toInt())
        }
        customProPanel.addView(advancedLabel)
        // Note: the ProSettingsFragment container is the same as Independent mode
        // (proFragContainerId). This panel is only shown when mode==MELODIC, so
        // there is never a visibility conflict.
        val proFragRef = android.widget.FrameLayout(ctx).apply {
            id = proFragContainerId(voiceId)
            tag = "melProFragContainer$voiceId"
        }
        customProPanel.addView(proFragRef)
        panel.addView(customProPanel)

        fun togglePro(shared: Boolean) {
            sharedProBtn.isChecked    = shared
            customProBtn.isChecked    = !shared
            customProPanel.visibility = if (shared) View.GONE else View.VISIBLE
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
        // Determine if this panel belongs to MELODIC mode by checking tag prefix
        val isMelodic = (panel.tag as? String)?.startsWith("melod") == true
            || panel.findViewWithTag<SeekBar>("melBpm") != null

        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        fun sp(tag: String): Int = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0

        val bpm       = if (isMelodic) seekVal("melBpm") + 20   else seekVal("indBpm") + 20
        val vel       = if (isMelodic) seekVal("melVel")         else seekVal("indVel")
        val minOct    = if (isMelodic) seekVal("melMinOct")      else seekVal("indMinOct")
        val maxOct    = if (isMelodic) seekVal("melMaxOct")      else seekVal("indMaxOct")
        val midiCh    = if (isMelodic) seekVal("melMidiCh")      else seekVal("indMidiCh")
        val scale     = if (isMelodic) sp("melScale")            else sp("indScale")
        val timing    = if (isMelodic) sp("melTiming")           else sp("indTiming")
        val root      = if (isMelodic) sp("melRoot")             else sp("indRoot")
        val sharedTag = if (isMelodic) "melSharedPro"            else "sharedPro"
        val shared    = panel.findViewWithTag<RadioButton>(sharedTag)?.isChecked ?: true

        val style = VoiceStyle.entries.getOrElse(sp("indStyle")) { VoiceStyle.GENERATIVE }
        val chordsType   = sp("chordType")
        val chordsRhythm = sp("chordRhythm")

        val baseCustomPro = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        val resolvedPro: ProSettings = if (shared) ProSettings() else baseCustomPro

        return IndependentConfig(
            bpm           = bpm,
            velocity      = vel,
            minOctave     = minOct,
            maxOctave     = maxOct,
            midiChannel   = midiCh,
            selectedScale = scale,
            timingMode    = timing,
            style         = if (isMelodic) VoiceStyle.GENERATIVE else style,
            rootNote      = root,
            useSharedPro  = shared,
            proSettings   = resolvedPro,
            chordsType    = chordsType,
            chordsRhythm  = chordsRhythm
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
        panel.findViewWithTag<RadioButton>("rbMelodic")?.isChecked        = cfg.mode == VoiceMode.MELODIC
        panel.findViewWithTag<View>("harmonyPanel")?.visibility            =
            if (cfg.mode == VoiceMode.HARMONY)     View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("independentPanel")?.visibility        =
            if (cfg.mode == VoiceMode.INDEPENDENT) View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("melodicPanel")?.visibility            =
            if (cfg.mode == VoiceMode.MELODIC)     View.VISIBLE else View.GONE

        when (cfg.mode) {
            VoiceMode.HARMONY -> {
                panel.findViewWithTag<SeekBar>("masterVel")?.progress = cfg.harmonyConfig.masterVelocity
                panel.findViewWithTag<SeekBar>("midiCh")?.progress    = cfg.harmonyConfig.midiChannel
            }
            VoiceMode.INDEPENDENT, VoiceMode.MELODIC -> {
                val ic = cfg.independentConfig
                val isMelodic = cfg.mode == VoiceMode.MELODIC
                if (isMelodic) {
                    panel.findViewWithTag<SeekBar>("melVel")?.progress    = ic.velocity
                    panel.findViewWithTag<SeekBar>("melMidiCh")?.progress = ic.midiChannel
                    panel.findViewWithTag<Spinner>("melRoot")?.setSelection(ic.rootNote)
                    panel.findViewWithTag<Spinner>("melScale")?.setSelection(ic.selectedScale)
                    val melShared = ic.useSharedPro
                    panel.findViewWithTag<RadioButton>("melSharedPro")?.isChecked = melShared
                    panel.findViewWithTag<RadioButton>("melCustomPro")?.isChecked = !melShared
                    panel.findViewWithTag<View>("melCustomProPanel")?.visibility  =
                        if (melShared) View.GONE else View.VISIBLE
                } else {
                    panel.findViewWithTag<SeekBar>("indVel")?.progress     = ic.velocity
                    panel.findViewWithTag<SeekBar>("indMidiCh")?.progress  = ic.midiChannel
                    panel.findViewWithTag<Spinner>("indStyle")?.setSelection(ic.style.ordinal)
                    panel.findViewWithTag<Spinner>("indRoot")?.setSelection(ic.rootNote)
                    panel.findViewWithTag<Spinner>("indScale")?.setSelection(ic.selectedScale)
                    panel.findViewWithTag<View>("chordsPanel")?.visibility =
                        if (ic.style == VoiceStyle.CHORDS) View.VISIBLE else View.GONE
                    panel.findViewWithTag<Spinner>("chordType")?.setSelection(ic.chordsType)
                    panel.findViewWithTag<Spinner>("chordRhythm")?.setSelection(ic.chordsRhythm)
                    val shared = ic.useSharedPro
                    panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked = shared
                    panel.findViewWithTag<RadioButton>("customPro")?.isChecked = !shared
                    panel.findViewWithTag<View>("customProPanel")?.visibility  =
                        if (shared) View.GONE else View.VISIBLE
                    if (!shared) {
                        val frag = if (panel === panelV2) proFragV2 else proFragV3
                        frag?.setInitialSettings(ic.proSettings)
                    }
                }
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
        val melodicBtn       = panel.findViewWithTag<RadioButton>("rbMelodic")
        val harmonyPanel     = panel.findViewWithTag<LinearLayout>("harmonyPanel") ?: return
        val independentPanel = panel.findViewWithTag<LinearLayout>("independentPanel") ?: return
        val melodicPanel     = panel.findViewWithTag<LinearLayout>("melodicPanel") ?: return
        val mode = when {
            harmonyBtn?.isChecked == true -> VoiceMode.HARMONY
            melodicBtn?.isChecked == true -> VoiceMode.MELODIC
            else                          -> VoiceMode.INDEPENDENT
        }
        val cfg = VoiceConfig(
            enabled           = enableSwitch?.isChecked ?: false,
            mode              = mode,
            harmonyConfig     = readHarmonyConfig(harmonyPanel, voiceId),
            independentConfig = readIndependentConfig(
                if (mode == VoiceMode.MELODIC) melodicPanel else independentPanel,
                voiceId
            )
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
