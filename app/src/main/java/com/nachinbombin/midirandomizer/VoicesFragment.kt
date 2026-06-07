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
 *   HARMONY     – follows V1 with interval offset + drift
 *   INDEPENDENT – own generative engine (Generative / Drone / Chords)
 *   MELODIC     – own melodic engine with optional chord-aware contrast controls
 *
 * The Independent panel now exposes the full ChordConfig when Style = Chords:
 *   Chord Type, Build Strategy, Tension, Inversion, Voicing Density,
 *   Plucking Style, Pluck Delay ms, Strum Length, Note Drop %, Mutation %, Rhythmic Figure.
 *
 * The Melodic panel exposes Contrast Depth + Contrast Mode (Counter-Motion /
 *   Rhythmic Complement / Register Contrast / Chord-Aware).
 *
 * Pro Settings for Independent/Melodic voices are managed entirely by the
 * embedded child ProSettingsFragment — there are NO duplicate inline
 * pro controls in this fragment.
 *
 * FIX: Each mode panel (Independent, Melodic) now has its OWN FrameLayout ID
 * for the ProSettingsFragment container:
 *   Independent → proFragContainerV2 / proFragContainerV3
 *   Melodic     → proFragContainerMelodicV2 / proFragContainerMelodicV3
 * This eliminates the duplicate View ID crash that occurred because both
 * panels exist in the hierarchy simultaneously (one hidden via View.GONE).
 * A single ProSettingsFragment instance per voice is shared between both
 * panels via fragment move transactions when the mode switches.
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

    /**
     * Container ID for the Independent panel's ProSettings FrameLayout.
     * Independent and Melodic panels each have their own unique IDs to
     * prevent duplicate-ID crashes (both panels coexist in the hierarchy).
     */
    private fun proFragContainerId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3

    /**
     * Container ID for the Melodic panel's ProSettings FrameLayout.
     * Separate from proFragContainerId to avoid duplicate View IDs.
     */
    private fun proFragContainerMelodicId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerMelodicV2 else R.id.proFragContainerMelodicV3

    /**
     * Attaches (or reattaches) the ProSettingsFragment for a voice.
     *
     * A single fragment instance is shared between the Independent and Melodic
     * panels. On initial attach we add it to the Independent container (default
     * mode). When the user switches to Melodic mode, moveProFragToMelodicContainer
     * detaches and re-attaches it into the Melodic container, and vice-versa.
     * This avoids the crash that occurred when both panels had the same container
     * ID and the fragment manager tried to add a fragment to an ambiguous view.
     */
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

    /**
     * Moves the ProSettingsFragment for [voiceId] into the Melodic panel container.
     * Called when the user switches to Melodic mode so the fragment renders inside
     * the correct (visible) FrameLayout.
     */
    private fun moveProFragToMelodicContainer(voiceId: Int) {
        val tag = "proFrag_v$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment ?: return
        val melodicContainerId = proFragContainerMelodicId(voiceId)
        childFragmentManager.beginTransaction()
            .detach(frag)
            .commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction()
            .attach(frag)
            // Move fragment to the melodic container by re-adding via replace into melodic container
            .remove(frag)
            .commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction()
            .add(melodicContainerId, frag, tag)
            .commitAllowingStateLoss()
    }

    /**
     * Moves the ProSettingsFragment for [voiceId] back into the Independent panel container.
     * Called when the user switches away from Melodic mode.
     */
    private fun moveProFragToIndependentContainer(voiceId: Int) {
        val tag = "proFrag_v$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment ?: return
        val independentContainerId = proFragContainerId(voiceId)
        childFragmentManager.beginTransaction()
            .remove(frag)
            .commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction()
            .add(independentContainerId, frag, tag)
            .commitAllowingStateLoss()
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

        // Track the last mode so we know when to move the pro fragment
        var lastMode: VoiceMode = VoiceMode.HARMONY

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
                enabled              = enableSwitch.isChecked,
                mode                 = mode,
                harmonyConfig        = readHarmonyConfig(harmonyPanel, voiceId),
                independentConfig    = readIndependentConfig(
                    if (mode == VoiceMode.MELODIC) melodicPanel else independentPanel,
                    voiceId
                ),
                melodicRelationConfig = readMelodicRelationConfig(melodicPanel)
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

            // Move the shared ProSettingsFragment into the correct container
            // only when transitioning to/from Melodic mode after the view is attached
            if (view != null) {
                when {
                    mode == VoiceMode.MELODIC && lastMode != VoiceMode.MELODIC ->
                        moveProFragToMelodicContainer(voiceId)
                    mode != VoiceMode.MELODIC && lastMode == VoiceMode.MELODIC ->
                        moveProFragToIndependentContainer(voiceId)
                }
            }
            lastMode = mode
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

    private fun labeledRangeSlider(
        ctx: Context, label: String, min: Int, max: Int, defMin: Int, defMax: Int,
        tag: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 4) }
        val tv  = TextView(ctx).apply {
            text = "$label: $defMin - $defMax"; setTextColor(0xFFE8E6E1.toInt())
        }
        val rs = com.google.android.material.slider.RangeSlider(ctx).apply {
            this.tag = tag; valueFrom = min.toFloat(); valueTo = max.toFloat()
            stepSize = 1.0f; values = listOf(defMin.toFloat(), defMax.toFloat())
            addOnChangeListener { _, _, fromUser ->
                val v = values
                tv.text = "$label: ${v[0].toInt()} - ${v[1].toInt()}"
                if (fromUser) onSync()
            }
        }
        row.addView(tv); row.addView(rs)
        return row
    }

    private fun labeledSeekBarFollow(
        ctx: Context, label: String, min: Int, max: Int, default: Int,
        tag: String, followLabel: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 4) }
        val tv  = TextView(ctx).apply {
            text = "$label: $default"; minWidth = 350; setTextColor(0xFFE8E6E1.toInt())
        }
        val sb  = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min + 1; progress = default - min
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val actualVal = p + min
                tv.text = if (actualVal > max) "$label: $followLabel" else "$label: $actualVal"
                if (fromUser) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(tv); row.addView(sb)
        return row
    }

    private fun buildFollowBpmGroup(
        ctx: Context, prefix: String, onSync: () -> Unit
    ): LinearLayout {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        
        val modeNames = listOf("Custom BPM", "Follow Main", "Fraction of Main")
        val spinner = spinnerRow(ctx, "BPM Mode", modeNames, "${prefix}BpmMode", null)
        root.addView(spinner)

        val customPanel = LinearLayout(ctx).apply { 
            orientation = LinearLayout.VERTICAL; visibility = View.VISIBLE; tag = "${prefix}CustomBpmPanel" 
        }
        customPanel.addView(labeledNumberInput(ctx, "BPM", 20, 300, 120, "${prefix}Bpm", onSync))
        root.addView(customPanel)

        val fractionPanel = LinearLayout(ctx).apply { 
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; tag = "${prefix}FractionBpmPanel" 
        }
        val fractionLabels = mapOf(
            0 to "1/8x", 1 to "1/4x", 2 to "1/2x", 3 to "1x", 4 to "1.5x", 5 to "2x"
        )
        val fractionValues = mapOf(
            0 to 0.125f, 1 to 0.25f, 2 to 0.5f, 3 to 1.0f, 4 to 1.5f, 5 to 2.0f
        )
        
        val fractionSlider = labeledSeekBarWithLabels(
            ctx, "BPM Fraction", 0, 5, 3, "${prefix}BpmFraction", fractionLabels, onSync
        )
        fractionPanel.addView(fractionSlider)
        root.addView(fractionPanel)

        root.findViewWithTag<Spinner>("${prefix}BpmMode")?.onItemSelectedListener = 
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    customPanel.visibility = if (pos == 0) View.VISIBLE else View.GONE
                    fractionPanel.visibility = if (pos == 2) View.VISIBLE else View.GONE
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        
        return root
    }

    private fun labeledNumberInput(
        ctx: Context, label: String, min: Int, max: Int, default: Int, tag: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 4) }
        val tvLabel = TextView(ctx).apply { text = "$label: "; setTextColor(0xFFE8E6E1.toInt()) }
        
        val tvValue = TextView(ctx).apply {
            text = default.toString(); textSize = 16f; setTextColor(0xFF4F9AA5.toInt())
            setPadding(16, 0, 16, 0); this.tag = "${tag}Value"
            setOnClickListener {
                showNumberInputDialog(ctx, label, min, max, this, onSync)
            }
        }

        val sb = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min; progress = default - min
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvValue.text = (p + min).toString()
                if (fromUser) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        row.addView(tvLabel); row.addView(tvValue); row.addView(sb)
        return row
    }

    private fun showNumberInputDialog(
        ctx: Context, label: String, min: Int, max: Int, target: TextView, onSync: () -> Unit
    ) {
        val et = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(target.text)
        }
        android.app.AlertDialog.Builder(ctx)
            .setTitle("Set $label ($min-$max)")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val newVal = et.text.toString().toIntOrNull()?.coerceIn(min, max)
                if (newVal != null) {
                    target.text = newVal.toString()
                    val tag = target.tag.toString().removeSuffix("Value")
                    // We need to find the specific panel to find the seekBar
                    var parent = target.parent
                    while (parent != null && parent !is LinearLayout) { parent = parent.parent }
                    (parent as? LinearLayout)?.findViewWithTag<SeekBar>(tag)?.progress = newVal - min
                    onSync()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun labeledSeekBarWithLabels(
        ctx: Context, label: String, min: Int, max: Int, default: Int, 
        tag: String, labels: Map<Int, String>, onSync: () -> Unit
    ): LinearLayout {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 4) }
        val header = TextView(ctx).apply {
            text = "$label: ${labels[default]}"; setTextColor(0xFFE8E6E1.toInt())
        }
        root.addView(header)

        val sb = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min; progress = default - min
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                header.text = "$label: ${labels[p + min]}"
                if (fromUser) onSync()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(sb)
        return root
    }

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
     * Independent panel — core parameters + Style spinner + full Chords sub-panel.
     * Chords sub-panel is visible only when Style = "Chords" (index 3).
     * Contains all 11 chord controls: Type, Build Strategy, Tension, Inversion,
     * Voicing Density, Plucking Style, Pluck Delay, Strum Length, Note Drop,
     * Mutation, Rhythmic Figure.
     */
    private fun buildIndependentPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(buildFollowBpmGroup(ctx, "ind", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",                0, 127,  90, "indVel",    onSync))
        
        val octRow = labeledRangeSlider(ctx, "Octave Range", 0, 8, 3, 5, "indOctave", onSync)
        panel.addView(octRow)
        
        panel.addView(labeledSeekBarFollow(ctx, "MIDI Channel", 0, 16, 0, "indMidiCh", "Follow Main", onSync))

        val scaleNames = listOf("Follow Main", "Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone",
            "Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")
            
        panel.addView(spinnerRow(ctx, "Scale", scaleNames, "indScaleValue", onSync))
        
        val rootNames = listOf("Follow Main", "C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        panel.addView(spinnerRow(ctx, "Root", rootNames, "indRootValue", onSync))
            
        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "indTiming", onSync))
        panel.addView(spinnerRow(ctx, "Style",  listOf("Generative","Single-Note Drone","Evolving Drone","Chords"), "indStyle", null))

        // ── Full Chords sub-panel ─────────────────────────────────────────
        val chordsPanel = buildChordsSubPanel(ctx, "ind", onSync)
            .apply { tag = "chordsPanel"; visibility = View.GONE }
        panel.addView(chordsPanel)

        // Wire Style spinner to show/hide chordsPanel
        panel.findViewWithTag<Spinner>("indStyle")?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    chordsPanel.visibility = if (pos == VoiceStyle.CHORDS.ordinal) View.VISIBLE else View.GONE
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }

        // ── Pro Settings toggle ───────────────────────────────────────────
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro", true).apply  { tag = "sharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro", false).apply { tag = "customPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 0)
            visibility  = View.GONE; tag = "customProPanel"
        }
        customProPanel.addView(TextView(ctx).apply {
            text = "Advanced Engine Settings:"; textSize = 13f
            setPadding(0, 8, 0, 4); setTextColor(0xFF797876.toInt())
        })
        // Independent panel gets its own unique container ID (proFragContainerV2/V3)
        customProPanel.addView(android.widget.FrameLayout(ctx).apply {
            id = proFragContainerId(voiceId); tag = "proFragContainer$voiceId"
        })
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
     * Melodic panel — core parameters + Contrast controls.
     * Contrast Depth + Contrast Mode are visible when the voice is MELODIC.
     *
     * FIX: Uses proFragContainerMelodicId() for its FrameLayout, which resolves
     * to proFragContainerMelodicV2/V3 — IDs that are distinct from the
     * Independent panel's proFragContainerV2/V3. This prevents the
     * IllegalStateException crash caused by two FrameLayouts sharing the same ID
     * when the fragment manager tries to commit a transaction against an
     * ambiguous container.
     */
    private fun buildMelodicPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(buildFollowBpmGroup(ctx, "mel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",                0, 127,  90, "melVel",    onSync))
        
        val octRow = labeledRangeSlider(ctx, "Octave Range", 0, 8, 3, 5, "melOctave", onSync)
        panel.addView(octRow)
        
        panel.addView(labeledSeekBarFollow(ctx, "MIDI Channel", 0, 16, 0, "melMidiCh", "Follow Main", onSync))

        if (voiceId == 3) {
            panel.addView(spinnerRow(ctx, "Reference Voice", listOf("Voice 1", "Voice 2"), "melRefVoice", onSync))
        }

        val scaleNames = listOf("Follow Main", "Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone",
            "Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")
            
        panel.addView(spinnerRow(ctx, "Scale", scaleNames, "melScaleValue", onSync))

        val rootNames = listOf("Follow Main", "C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        panel.addView(spinnerRow(ctx, "Root", rootNames, "melRootValue", onSync))
            
        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "melTiming", onSync))

        // ── Melodic Relation / Contrast controls ──────────────────────────
        val contrastHeader = TextView(ctx).apply {
            text = "Harmonic Contrast Settings"; textSize = 14f
            setPadding(0, 16, 0, 4)
            setTextColor(0xFFBDBBB6.toInt())
        }
        panel.addView(contrastHeader)

        val contrastDepthRow = LinearLayout(ctx)
        contrastDepthRow.orientation = LinearLayout.VERTICAL
        contrastDepthRow.tag = "contrastDepthRow"
        contrastDepthRow.addView(labeledSeekBar(ctx, "Contrast Depth", 0, 100, 50, "melContrastDepth", onSync))
        panel.addView(contrastDepthRow)

        panel.addView(spinnerRow(ctx, "Contrast Mode",
            listOf("Counter-Motion","Rhythmic Complement","Register Contrast","Chord-Aware"),
            "melContrastMode", onSync))

        // ── Pro Settings ──────────────────────────────────────────────────
        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro", true).apply  { tag = "melSharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro", false).apply { tag = "melCustomPro" }
        proRow.addView(sharedProBtn)
        proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 0)
            visibility  = View.GONE; tag = "melCustomProPanel"
        }
        customProPanel.addView(TextView(ctx).apply {
            text = "Melodic Engine Settings (Pro):"; textSize = 13f
            setPadding(0, 8, 0, 4); setTextColor(0xFF797876.toInt())
        })
        // FIX: Use the Melodic-specific container ID (proFragContainerMelodicV2/V3)
        // instead of proFragContainerId(voiceId) which is already used by buildIndependentPanel.
        // Both panels coexist in the layout hierarchy (one hidden via View.GONE), so
        // using the same ID caused duplicate-ID crashes during fragment transactions.
        customProPanel.addView(android.widget.FrameLayout(ctx).apply {
            id  = proFragContainerMelodicId(voiceId); tag = "melProFragContainer$voiceId"
        })
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
     * Builds the full chord configuration sub-panel.
     * [prefix] is "ind" for Independent or "mel" (unused for now, melodic voices
     * don't have their own chord engine — V1 handles that).
     * All 11 chord controls:
     *   Chord Type | Build Strategy | Tension Level | Inversion | Voicing Density
     *   Plucking Style | Pluck Delay | Strum Length | Note Drop % | Mutation % | Rhythmic Figure
     */
    private fun buildChordsSubPanel(ctx: Context, prefix: String, onSync: () -> Unit): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 0, 8)
        }

        val sectionLabel = TextView(ctx).apply {
            text = "Chord Configuration"; textSize = 13f
            setPadding(0, 4, 0, 8); setTextColor(0xFFBDBBB6.toInt())
        }
        panel.addView(sectionLabel)

        panel.addView(spinnerRow(ctx, "Chord Type",
            listOf("Triad","7th","9th","Sus2","Sus4","Power"),
            "${prefix}ChordType", onSync))

        panel.addView(spinnerRow(ctx, "Build Strategy",
            listOf("Diatonic Stack","Modal Snap"),
            "${prefix}BuildStrategy", onSync))

        panel.addView(spinnerRow(ctx, "Tension Level",
            listOf("Triad","7th","9th","11th/13th"),
            "${prefix}TensionLevel", onSync))

        panel.addView(spinnerRow(ctx, "Inversion",
            listOf("Root","1st","2nd","Auto (voice-leading)"),
            "${prefix}Inversion", onSync))

        panel.addView(spinnerRow(ctx, "Voicing Density",
            listOf("Full","Drop 5","Shell","Drop Root"),
            "${prefix}VoicingDensity", onSync))

        panel.addView(spinnerRow(ctx, "Plucking Style",
            listOf("Simultaneous","Ascending","Descending","Random","Percussive Up"),
            "${prefix}PluckStyle", onSync))

        panel.addView(labeledSeekBar(ctx, "Pluck Delay (ms)",  10, 200,  30, "${prefix}PluckDelay",   onSync))
        panel.addView(labeledSeekBar(ctx, "Strum Length (notes)", 1, 6,   2, "${prefix}StrumLen",     onSync))
        panel.addView(labeledSeekBar(ctx, "Note Drop %",          0,  50,  0, "${prefix}NoteDrop",     onSync))
        panel.addView(labeledSeekBar(ctx, "Mutation %",           0,  30,  0, "${prefix}Mutation",     onSync))

        panel.addView(spinnerRow(ctx, "Rhythmic Figure",
            listOf("Sustained","Re-attack","Broken","Ostinato"),
            "${prefix}RhythmicFigure", onSync))

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

    private fun readChordConfig(panel: LinearLayout, prefix: String): ChordConfig {
        fun sp(tag: String): Int = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0
        fun sb(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        return ChordConfig(
            chordType          = sp("${prefix}ChordType"),
            chordBuildStrategy = ChordBuildStrategy.entries.getOrElse(sp("${prefix}BuildStrategy")) { ChordBuildStrategy.DIATONIC_STACK },
            tensionLevel       = TensionLevel.entries.getOrElse(sp("${prefix}TensionLevel")) { TensionLevel.TRIAD },
            inversionMode      = InversionMode.entries.getOrElse(sp("${prefix}Inversion")) { InversionMode.ROOT },
            voicingDensity     = VoicingDensity.entries.getOrElse(sp("${prefix}VoicingDensity")) { VoicingDensity.FULL },
            pluckingStyle      = sp("${prefix}PluckStyle"),
            pluckingDelayMs    = (sb("${prefix}PluckDelay") + 10).toLong(),
            strumLength        = sb("${prefix}StrumLen") + 1,
            noteDropChance     = sb("${prefix}NoteDrop") / 100f,
            mutationChance     = sb("${prefix}Mutation") / 100f,
            rhythmicFigure     = RhythmicFigure.entries.getOrElse(sp("${prefix}RhythmicFigure")) { RhythmicFigure.SUSTAINED }
        )
    }

    private fun readMelodicRelationConfig(panel: LinearLayout): MelodicRelationConfig {
        val depth   = panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress ?: 50
        val mode    = MelodicRelationMode.entries.getOrElse(
            panel.findViewWithTag<Spinner>("melContrastMode")?.selectedItemPosition ?: 0
        ) { MelodicRelationMode.COUNTER_MOTION }
        val ref = (panel.findViewWithTag<Spinner>("melRefVoice")?.selectedItemPosition ?: 0) + 1
        return MelodicRelationConfig(enabled = true, contrastDepth = depth, mode = mode, referenceVoice = ref)
    }

    private fun readIndependentConfig(panel: LinearLayout, voiceId: Int): IndependentConfig {
        val isMelodic = panel.findViewWithTag<SeekBar>("melVel") != null
        val prefix = if (isMelodic) "mel" else "ind"

        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        fun sp(tag: String): Int      = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0
        fun rsVal(tag: String): List<Float> = panel.findViewWithTag<com.google.android.material.slider.RangeSlider>(tag)?.values ?: listOf(0f, 0f)

        val bpmMode = ParamFollowMode.entries.getOrElse(sp("${prefix}BpmMode")) { ParamFollowMode.CUSTOM }
        val bpm = seekVal("${prefix}Bpm") + 20
        val bpmFraction = when (seekVal("${prefix}BpmFraction")) {
            0 -> 0.125f; 1 -> 0.25f; 2 -> 0.5f; 3 -> 1.0f; 4 -> 1.5f; 5 -> 2.0f; else -> 1.0f
        }

        val scale = sp("${prefix}ScaleValue")
        val root = sp("${prefix}RootValue")

        val vel    = seekVal("${prefix}Vel")
        val octs   = rsVal("${prefix}Octave")
        val midiCh = seekVal("${prefix}MidiCh")
        val timing = sp("${prefix}Timing")
        
        val sharedTag = if (isMelodic) "melSharedPro" else "sharedPro"
        val shared = panel.findViewWithTag<RadioButton>(sharedTag)?.isChecked ?: true

        val style = VoiceStyle.entries.getOrElse(sp("indStyle")) { VoiceStyle.GENERATIVE }
        val chordCfg = if (!isMelodic && style == VoiceStyle.CHORDS) readChordConfig(panel, "ind")
                       else ChordConfig()

        val baseCustomPro = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        val resolvedPro: ProSettings = if (shared) ProSettings() else baseCustomPro

        return IndependentConfig(
            bpmMode       = bpmMode,
            bpm           = bpm,
            bpmFraction   = bpmFraction,
            selectedScale = scale,
            rootNote      = root,
            velocity      = vel,
            minOctave     = octs[0].toInt(),
            maxOctave     = octs[1].toInt(),
            midiChannel   = midiCh,
            timingMode    = timing,
            style         = if (isMelodic) VoiceStyle.GENERATIVE else style,
            useSharedPro  = shared,
            proSettings   = resolvedPro,
            chordConfig   = chordCfg
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
            VoiceMode.INDEPENDENT -> {
                val ic = cfg.independentConfig
                val prefix = "ind"
                panel.findViewWithTag<Spinner>("${prefix}BpmMode")?.setSelection(ic.bpmMode.ordinal)
                panel.findViewWithTag<SeekBar>("${prefix}Bpm")?.progress = ic.bpm - 20
                panel.findViewWithTag<TextView>("${prefix}BpmValue")?.text = ic.bpm.toString()
                val fracIdx = when (ic.bpmFraction) {
                    0.125f -> 0; 0.25f -> 1; 0.5f -> 2; 1.0f -> 3; 1.5f -> 4; 2.0f -> 5; else -> 3
                }
                panel.findViewWithTag<SeekBar>("${prefix}BpmFraction")?.progress = fracIdx
                
                // Set visibility based on mode
                panel.findViewWithTag<View>("${prefix}CustomBpmPanel")?.visibility = if (ic.bpmMode == ParamFollowMode.CUSTOM) View.VISIBLE else View.GONE
                panel.findViewWithTag<View>("${prefix}FractionBpmPanel")?.visibility = if (ic.bpmMode == ParamFollowMode.FRACTION_MAIN) View.VISIBLE else View.GONE
                
                panel.findViewWithTag<Spinner>("${prefix}ScaleValue")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("${prefix}RootValue")?.setSelection(ic.rootNote)

                panel.findViewWithTag<SeekBar>("${prefix}Vel")?.progress     = ic.velocity
                panel.findViewWithTag<com.google.android.material.slider.RangeSlider>("${prefix}Octave")?.values = listOf(ic.minOctave.toFloat(), ic.maxOctave.toFloat())
                panel.findViewWithTag<SeekBar>("${prefix}MidiCh")?.progress  = ic.midiChannel
                panel.findViewWithTag<Spinner>("${prefix}Style")?.setSelection(ic.style.ordinal)
                panel.findViewWithTag<Spinner>("${prefix}Timing")?.setSelection(ic.timingMode)

                panel.findViewWithTag<View>("chordsPanel")?.visibility =
                    if (ic.style == VoiceStyle.CHORDS) View.VISIBLE else View.GONE
                applyChordConfigToPanel(panel, ic.chordConfig, "ind")
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
            VoiceMode.MELODIC -> {
                val ic = cfg.independentConfig
                val prefix = "mel"
                panel.findViewWithTag<Spinner>("${prefix}BpmMode")?.setSelection(ic.bpmMode.ordinal)
                panel.findViewWithTag<SeekBar>("${prefix}Bpm")?.progress = ic.bpm - 20
                panel.findViewWithTag<TextView>("${prefix}BpmValue")?.text = ic.bpm.toString()
                val fracIdx = when (ic.bpmFraction) {
                    0.125f -> 0; 0.25f -> 1; 0.5f -> 2; 1.0f -> 3; 1.5f -> 4; 2.0f -> 5; else -> 3
                }
                panel.findViewWithTag<SeekBar>("${prefix}BpmFraction")?.progress = fracIdx
                
                // Set visibility based on mode
                panel.findViewWithTag<View>("${prefix}CustomBpmPanel")?.visibility = if (ic.bpmMode == ParamFollowMode.CUSTOM) View.VISIBLE else View.GONE
                panel.findViewWithTag<View>("${prefix}FractionBpmPanel")?.visibility = if (ic.bpmMode == ParamFollowMode.FRACTION_MAIN) View.VISIBLE else View.GONE

                panel.findViewWithTag<Spinner>("${prefix}ScaleValue")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("${prefix}RootValue")?.setSelection(ic.rootNote)

                panel.findViewWithTag<SeekBar>("${prefix}Vel")?.progress    = ic.velocity
                panel.findViewWithTag<com.google.android.material.slider.RangeSlider>("${prefix}Octave")?.values = listOf(ic.minOctave.toFloat(), ic.maxOctave.toFloat())
                panel.findViewWithTag<SeekBar>("${prefix}MidiCh")?.progress = ic.midiChannel
                panel.findViewWithTag<Spinner>("${prefix}Timing")?.setSelection(ic.timingMode)

                val mrc = cfg.melodicRelationConfig
                panel.findViewWithTag<Switch>("melContrastEnabled")?.isChecked = mrc.enabled
                panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress   = mrc.contrastDepth
                panel.findViewWithTag<Spinner>("melContrastMode")?.setSelection(mrc.mode.ordinal)
                panel.findViewWithTag<Spinner>("melRefVoice")?.setSelection(mrc.referenceVoice - 1)
                val melShared = ic.useSharedPro
                panel.findViewWithTag<RadioButton>("melSharedPro")?.isChecked = melShared
                panel.findViewWithTag<RadioButton>("melCustomPro")?.isChecked = !melShared
                panel.findViewWithTag<View>("melCustomProPanel")?.visibility  =
                    if (melShared) View.GONE else View.VISIBLE
                // FIX: also update the pro fragment when custom pro is active in melodic mode
                if (!melShared) {
                    val frag = if (panel === panelV2) proFragV2 else proFragV3
                    frag?.setInitialSettings(ic.proSettings)
                }
            }
        }
    }

    private fun applyChordConfigToPanel(panel: LinearLayout, cc: ChordConfig, prefix: String) {
        panel.findViewWithTag<Spinner>("${prefix}ChordType")?.setSelection(cc.chordType)
        panel.findViewWithTag<Spinner>("${prefix}BuildStrategy")?.setSelection(cc.chordBuildStrategy.ordinal)
        panel.findViewWithTag<Spinner>("${prefix}TensionLevel")?.setSelection(cc.tensionLevel.ordinal)
        panel.findViewWithTag<Spinner>("${prefix}Inversion")?.setSelection(cc.inversionMode.ordinal)
        panel.findViewWithTag<Spinner>("${prefix}VoicingDensity")?.setSelection(cc.voicingDensity.ordinal)
        panel.findViewWithTag<Spinner>("${prefix}PluckStyle")?.setSelection(cc.pluckingStyle)
        panel.findViewWithTag<SeekBar>("${prefix}PluckDelay")?.progress  = (cc.pluckingDelayMs - 10).toInt().coerceIn(0, 190)
        panel.findViewWithTag<SeekBar>("${prefix}StrumLen")?.progress    = (cc.strumLength - 1).coerceIn(0, 5)
        panel.findViewWithTag<SeekBar>("${prefix}NoteDrop")?.progress    = (cc.noteDropChance * 100).toInt().coerceIn(0, 50)
        panel.findViewWithTag<SeekBar>("${prefix}Mutation")?.progress    = (cc.mutationChance * 100).toInt().coerceIn(0, 30)
        panel.findViewWithTag<Spinner>("${prefix}RhythmicFigure")?.setSelection(cc.rhythmicFigure.ordinal)
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
            enabled              = enableSwitch?.isChecked ?: false,
            mode                 = mode,
            harmonyConfig        = readHarmonyConfig(harmonyPanel, voiceId),
            independentConfig    = readIndependentConfig(
                if (mode == VoiceMode.MELODIC) melodicPanel else independentPanel,
                voiceId
            ),
            melodicRelationConfig = readMelodicRelationConfig(melodicPanel)
        )
        if (voiceId == 2) { currentV2 = cfg; svc.updateVoice2Config(cfg) }
        else              { currentV3 = cfg; svc.updateVoice3Config(cfg) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun spinnerRow(
        ctx: Context, label: String, items: List<String>, tag: String, onSync: (() -> Unit)?
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        row.addView(TextView(ctx).apply { text = "$label: "; setTextColor(0xFFE8E6E1.toInt()) })
        val sp = Spinner(ctx).apply { this.tag = tag }
        sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        if (onSync != null) {
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, p: Int, id: Long) {
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        }
        row.addView(sp)
        return row
    }

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
