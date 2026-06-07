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

    // ── Lifecycle ─────────────────────────────────────────────────────

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

    // ── Child ProSettingsFragment management ────────────────────────────────────────────

    private fun proFragContainerId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3

    private fun proFragContainerMelodicId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerMelodicV2 else R.id.proFragContainerMelodicV3

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

    private fun moveProFragToMelodicContainer(voiceId: Int) {
        val tag = "proFrag_v$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment ?: return
        val melodicContainerId = proFragContainerMelodicId(voiceId)
        childFragmentManager.beginTransaction().detach(frag).commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction().add(melodicContainerId, frag, tag).commitAllowingStateLoss()
    }

    private fun moveProFragToIndependentContainer(voiceId: Int) {
        val tag = "proFrag_v$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment ?: return
        val independentContainerId = proFragContainerId(voiceId)
        childFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction().add(independentContainerId, frag, tag).commitAllowingStateLoss()
    }

    // ── Voice panel builder ───────────────────────────────────────────────────

    private fun buildVoicePanel(voiceId: Int, label: String): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            tag = "root$voiceId"
        }

        val enableSwitch = Switch(ctx).apply {
            text = label; isChecked = false; textSize = 18f
            setTextColor(0xFFE8E6E1.toInt()); tag = "enable"
        }
        panel.addView(enableSwitch)

        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 0); tag = "modeRow"
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true ).apply { tag = "rbHarmony" }
        val independentBtn = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        val melodicBtn     = radioButton(ctx, "Melodic",     false).apply { tag = "rbMelodic" }
        modeRow.addView(harmonyBtn); modeRow.addView(independentBtn); modeRow.addView(melodicBtn)
        panel.addView(modeRow)

        var syncConfigAction: (() -> Unit)? = null

        val harmonyPanel     = buildHarmonyPanel(voiceId)     { syncConfigAction?.invoke() }.apply { tag = "harmonyPanel" }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }.apply { tag = "independentPanel" }
        val melodicPanel     = buildMelodicPanel(voiceId)     { syncConfigAction?.invoke() }.apply { tag = "melodicPanel" }

        harmonyPanel.visibility     = View.VISIBLE
        independentPanel.visibility = View.GONE
        melodicPanel.visibility     = View.GONE

        panel.addView(harmonyPanel)
        panel.addView(independentPanel)
        panel.addView(melodicPanel)

        var lastMode = VoiceMode.HARMONY

        fun syncConfig() { pushConfigToService(voiceId) }
        syncConfigAction = ::syncConfig

        fun switchMode(mode: VoiceMode) {
            harmonyBtn.isChecked     = mode == VoiceMode.HARMONY
            independentBtn.isChecked = mode == VoiceMode.INDEPENDENT
            melodicBtn.isChecked     = mode == VoiceMode.MELODIC
            harmonyPanel.visibility     = if (mode == VoiceMode.HARMONY)     View.VISIBLE else View.GONE
            independentPanel.visibility = if (mode == VoiceMode.INDEPENDENT) View.VISIBLE else View.GONE
            melodicPanel.visibility     = if (mode == VoiceMode.MELODIC)     View.VISIBLE else View.GONE
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
        val fractionSlider = labeledSeekBarWithLabels(
            ctx, "BPM Fraction", 0, 5, 3, "${prefix}BpmFraction", fractionLabels, onSync
        )
        fractionPanel.addView(fractionSlider)
        root.addView(fractionPanel)

        root.findViewWithTag<Spinner>("${prefix}BpmMode")?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    customPanel.visibility   = if (pos == 0) View.VISIBLE else View.GONE
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
            setOnClickListener { showNumberInputDialog(ctx, label, min, max, this, onSync) }
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
        panel.addView(labeledSeekBar(ctx, "Tone Step Offset",  -7, 7,   2,  "toneStep",  onSync))
        panel.addView(labeledSeekBar(ctx, "Time Drift (ms)",    0, 45, 10,  "timeDrift", onSync))
        panel.addView(labeledSeekBar(ctx, "Skip % (0-100)",     0, 100, 0,  "skipPct",   onSync))
        panel.addView(labeledSeekBar(ctx, "Master Velocity",    0, 127, 90, "masterVel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity Drift",     0, 30,  10, "velDrift",  onSync))
        panel.addView(labeledSeekBarFollow(ctx, "MIDI Channel", 0, 16, 0, "midiCh", "Follow Main", onSync))
        if (voiceId == 3) {
            panel.addView(spinnerRow(ctx, "Reference Voice", listOf("Voice 1", "Voice 2"), "refVoice", onSync))
        }
        return panel
    }

    /**
     * Independent panel — core parameters + Style spinner + full Chords sub-panel.
     * Chords sub-panel is visible only when Style = "Chords" (index 3).
     * Contains all 11 chord controls.
     */
    private fun buildIndependentPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(buildFollowBpmGroup(ctx, "ind", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",           0, 127,  90, "indVel",   onSync))
        panel.addView(labeledRangeSlider(ctx, "Octave Range",   0, 8, 3, 5, "indOctave", onSync))
        panel.addView(labeledSeekBarFollow(ctx, "MIDI Channel", 0, 16, 0, "indMidiCh", "Follow Main", onSync))

        val scaleNames = listOf("Follow Main", "Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone",
            "Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")
        panel.addView(spinnerRow(ctx, "Scale", scaleNames, "indScaleValue", onSync))

        val rootNames = listOf("Follow Main", "C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        panel.addView(spinnerRow(ctx, "Root", rootNames, "indRootValue", onSync))

        // Timing spinner — wired manually to show/hide euclidean panel
        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "indTiming", null))

        // Euclidean compact sub-panel
        val indEucPanel = buildEuclideanCompactPanel(ctx, "ind", onSync)
            .apply { tag = "indEucPanel"; visibility = View.GONE }
        panel.addView(indEucPanel)

        panel.findViewWithTag<Spinner>("indTiming")?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    indEucPanel.visibility = if (pos == 3) View.VISIBLE else View.GONE
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }

        panel.addView(spinnerRow(ctx, "Style", listOf("Generative","Single-Note Drone","Evolving Drone","Chords"), "indStyle", null))

        val chordsPanel = buildChordsSubPanel(ctx, "ind", onSync)
            .apply { tag = "chordsPanel"; visibility = View.GONE }
        panel.addView(chordsPanel)

        panel.findViewWithTag<Spinner>("indStyle")?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    chordsPanel.visibility = if (pos == VoiceStyle.CHORDS.ordinal) View.VISIBLE else View.GONE
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }

        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro", true).apply  { tag = "sharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro", false).apply { tag = "customPro" }
        proRow.addView(sharedProBtn); proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 0)
            visibility = View.GONE; tag = "customProPanel"
        }
        customProPanel.addView(TextView(ctx).apply {
            text = "Advanced Engine Settings:"; textSize = 13f
            setPadding(0, 8, 0, 4); setTextColor(0xFF797876.toInt())
        })
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
     */
    private fun buildMelodicPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(buildFollowBpmGroup(ctx, "mel", onSync))
        panel.addView(labeledSeekBar(ctx, "Velocity",           0, 127,  90, "melVel",   onSync))
        panel.addView(labeledRangeSlider(ctx, "Octave Range",   0, 8, 3, 5, "melOctave", onSync))
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

        // Timing spinner — wired manually to show/hide euclidean panel
        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "melTiming", null))

        // Euclidean compact sub-panel
        val melEucPanel = buildEuclideanCompactPanel(ctx, "mel", onSync)
            .apply { tag = "melEucPanel"; visibility = View.GONE }
        panel.addView(melEucPanel)

        panel.findViewWithTag<Spinner>("melTiming")?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    melEucPanel.visibility = if (pos == 3) View.VISIBLE else View.GONE
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }

        // Harmonic Contrast controls
        panel.addView(TextView(ctx).apply {
            text = "Harmonic Contrast Settings"; textSize = 14f
            setPadding(0, 16, 0, 4); setTextColor(0xFFBDBBB6.toInt())
        })
        panel.addView(labeledSeekBar(ctx, "Contrast Depth", 0, 100, 50, "melContrastDepth", onSync))
        panel.addView(spinnerRow(ctx, "Contrast Mode",
            listOf("Counter-Motion","Rhythmic Complement","Register Contrast","Chord-Aware"),
            "melContrastMode", onSync))

        val proRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val sharedProBtn = radioButton(ctx, "Shared Pro", true).apply  { tag = "melSharedPro" }
        val customProBtn = radioButton(ctx, "Custom Pro", false).apply { tag = "melCustomPro" }
        proRow.addView(sharedProBtn); proRow.addView(customProBtn)
        panel.addView(proRow)

        val customProPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 0)
            visibility = View.GONE; tag = "melCustomProPanel"
        }
        customProPanel.addView(TextView(ctx).apply {
            text = "Melodic Engine Settings (Pro):"; textSize = 13f
            setPadding(0, 8, 0, 4); setTextColor(0xFF797876.toInt())
        })
        customProPanel.addView(android.widget.FrameLayout(ctx).apply {
            id = proFragContainerMelodicId(voiceId); tag = "melProFragContainer$voiceId"
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
     * Compact Euclidean rhythm sub-panel — Steps / Density / Rotation.
     * Spawns inline below the Timing spinner when "Euclidean" (pos 3) is selected.
     * [prefix] is "ind" or "mel".
     */
    private fun buildEuclideanCompactPanel(ctx: Context, prefix: String, onSync: () -> Unit): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 4, 0, 4)
        }
        panel.addView(TextView(ctx).apply {
            text = "Euclidean Rhythm"; textSize = 12f
            setPadding(0, 4, 0, 4); setTextColor(0xFFBDBBB6.toInt())
        })
        panel.addView(labeledSeekBar(ctx, "Steps",    1, 32,  8, "${prefix}EucSteps",    onSync))
        panel.addView(labeledSeekBar(ctx, "Density",  1, 32,  3, "${prefix}EucDensity",  onSync))
        panel.addView(labeledSeekBar(ctx, "Rotation", 0, 31,  0, "${prefix}EucRotation", onSync))
        return panel
    }

    /**
     * Full Chord configuration sub-panel (11 controls).
     */
    private fun buildChordsSubPanel(ctx: Context, prefix: String, onSync: () -> Unit): LinearLayout {
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 8) }
        panel.addView(TextView(ctx).apply {
            text = "Chord Configuration"; textSize = 13f
            setPadding(0, 4, 0, 8); setTextColor(0xFFBDBBB6.toInt())
        })
        panel.addView(spinnerRow(ctx, "Chord Type",
            listOf("Triad","7th","9th","Sus2","Sus4","Power"), "${prefix}ChordType", onSync))
        panel.addView(spinnerRow(ctx, "Build Strategy",
            listOf("Diatonic Stack","Modal Snap"), "${prefix}BuildStrategy", onSync))
        panel.addView(spinnerRow(ctx, "Tension Level",
            listOf("Triad","7th","9th","11th/13th"), "${prefix}TensionLevel", onSync))
        panel.addView(spinnerRow(ctx, "Inversion",
            listOf("Root","1st","2nd","Auto (voice-leading)"), "${prefix}Inversion", onSync))
        panel.addView(spinnerRow(ctx, "Voicing Density",
            listOf("Full","Drop 5","Shell","Drop Root"), "${prefix}VoicingDensity", onSync))
        panel.addView(spinnerRow(ctx, "Plucking Style",
            listOf("Simultaneous","Ascending","Descending","Random","Percussive Up"), "${prefix}PluckStyle", onSync))
        panel.addView(labeledSeekBar(ctx, "Pluck Delay (ms)",     10, 200, 30, "${prefix}PluckDelay",    onSync))
        panel.addView(labeledSeekBar(ctx, "Strum Length (notes)",  1,   6,  2, "${prefix}StrumLen",      onSync))
        panel.addView(labeledSeekBar(ctx, "Note Drop %",           0,  50,  0, "${prefix}NoteDrop",      onSync))
        panel.addView(labeledSeekBar(ctx, "Mutation %",            0,  30,  0, "${prefix}Mutation",      onSync))
        panel.addView(spinnerRow(ctx, "Rhythmic Figure",
            listOf("Sustained","Re-attack","Broken","Ostinato"), "${prefix}RhythmicFigure", onSync))
        return panel
    }

    // ── Config read helpers ──────────────────────────────────────────────────

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
        val depth = panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress ?: 50
        val mode  = MelodicRelationMode.entries.getOrElse(
            panel.findViewWithTag<Spinner>("melContrastMode")?.selectedItemPosition ?: 0
        ) { MelodicRelationMode.COUNTER_MOTION }
        val ref = (panel.findViewWithTag<Spinner>("melRefVoice")?.selectedItemPosition ?: 0) + 1
        return MelodicRelationConfig(enabled = true, contrastDepth = depth, mode = mode, referenceVoice = ref)
    }

    private fun readIndependentConfig(panel: LinearLayout, voiceId: Int): IndependentConfig {
        val isMelodic = panel.findViewWithTag<SeekBar>("melVel") != null
        val prefix    = if (isMelodic) "mel" else "ind"

        fun seekVal(tag: String): Int = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        fun sp(tag: String): Int      = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0
        fun rsVal(tag: String): List<Float> =
            panel.findViewWithTag<com.google.android.material.slider.RangeSlider>(tag)?.values ?: listOf(0f, 0f)

        val bpmMode     = ParamFollowMode.entries.getOrElse(sp("${prefix}BpmMode")) { ParamFollowMode.CUSTOM }
        val bpm         = seekVal("${prefix}Bpm") + 20
        val bpmFraction = when (seekVal("${prefix}BpmFraction")) {
            0 -> 0.125f; 1 -> 0.25f; 2 -> 0.5f; 3 -> 1.0f; 4 -> 1.5f; 5 -> 2.0f; else -> 1.0f
        }
        val scale  = sp("${prefix}ScaleValue")
        val root   = sp("${prefix}RootValue")
        val vel    = seekVal("${prefix}Vel")
        val octs   = rsVal("${prefix}Octave")
        val midiCh = seekVal("${prefix}MidiCh")
        val timing = sp("${prefix}Timing")

        // Read euclidean compact panel values
        val eucSteps    = (panel.findViewWithTag<SeekBar>("${prefix}EucSteps")?.progress    ?: 7) + 1
        val eucDensity  = (panel.findViewWithTag<SeekBar>("${prefix}EucDensity")?.progress  ?: 2) + 1
        val eucRotation =  panel.findViewWithTag<SeekBar>("${prefix}EucRotation")?.progress ?: 0

        val sharedTag = if (isMelodic) "melSharedPro" else "sharedPro"
        val shared    = panel.findViewWithTag<RadioButton>(sharedTag)?.isChecked ?: true

        val style    = VoiceStyle.entries.getOrElse(sp("indStyle")) { VoiceStyle.GENERATIVE }
        val chordCfg = if (!isMelodic && style == VoiceStyle.CHORDS) readChordConfig(panel, "ind")
                       else ChordConfig()

        val baseCustomPro = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        val resolvedPro: ProSettings = (if (shared) ProSettings() else baseCustomPro).let { base ->
            if (timing == 3) base.copy(
                euclideanEnabled  = true,
                euclideanSteps    = eucSteps,
                euclideanDensity  = eucDensity,
                euclideanRotation = eucRotation
            ) else base
        }

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

    // ── Sync from service ───────────────────────────────────────────────────

    fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!::panelV2.isInitialized) return
        isUpdatingFromSync = true
        currentV2 = v2; currentV3 = v3
        applyToPanel(panelV2, v2)
        applyToPanel(panelV3, v3)
        isUpdatingFromSync = false
    }

    private fun applyToPanel(panel: LinearLayout, cfg: VoiceConfig) {
        panel.findViewWithTag<Switch>("enable")?.isChecked                = cfg.enabled
        panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked        = cfg.mode == VoiceMode.HARMONY
        panel.findViewWithTag<RadioButton>("rbIndependent")?.isChecked    = cfg.mode == VoiceMode.INDEPENDENT
        panel.findViewWithTag<RadioButton>("rbMelodic")?.isChecked        = cfg.mode == VoiceMode.MELODIC
        panel.findViewWithTag<View>("harmonyPanel")?.visibility     = if (cfg.mode == VoiceMode.HARMONY)     View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("independentPanel")?.visibility  = if (cfg.mode == VoiceMode.INDEPENDENT) View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("melodicPanel")?.visibility      = if (cfg.mode == VoiceMode.MELODIC)     View.VISIBLE else View.GONE

        when (cfg.mode) {
            VoiceMode.HARMONY -> {
                panel.findViewWithTag<SeekBar>("masterVel")?.progress = cfg.harmonyConfig.masterVelocity
                panel.findViewWithTag<SeekBar>("midiCh")?.progress    = cfg.harmonyConfig.midiChannel
            }
            VoiceMode.INDEPENDENT -> {
                val ic     = cfg.independentConfig
                val prefix = "ind"
                panel.findViewWithTag<Spinner>("${prefix}BpmMode")?.setSelection(ic.bpmMode.ordinal)
                panel.findViewWithTag<SeekBar>("${prefix}Bpm")?.progress = ic.bpm - 20
                panel.findViewWithTag<TextView>("${prefix}BpmValue")?.text = ic.bpm.toString()
                val fracIdx = when (ic.bpmFraction) {
                    0.125f -> 0; 0.25f -> 1; 0.5f -> 2; 1.0f -> 3; 1.5f -> 4; 2.0f -> 5; else -> 3
                }
                panel.findViewWithTag<SeekBar>("${prefix}BpmFraction")?.progress = fracIdx
                panel.findViewWithTag<View>("${prefix}CustomBpmPanel")?.visibility  =
                    if (ic.bpmMode == ParamFollowMode.CUSTOM) View.VISIBLE else View.GONE
                panel.findViewWithTag<View>("${prefix}FractionBpmPanel")?.visibility =
                    if (ic.bpmMode == ParamFollowMode.FRACTION) View.VISIBLE else View.GONE
                panel.findViewWithTag<SeekBar>("${prefix}Vel")?.progress = ic.velocity
                panel.findViewWithTag<com.google.android.material.slider.RangeSlider>("${prefix}Octave")?.values =
                    listOf(ic.minOctave.toFloat(), ic.maxOctave.toFloat())
                panel.findViewWithTag<SeekBar>("${prefix}MidiCh")?.progress = ic.midiChannel
                panel.findViewWithTag<Spinner>("${prefix}Timing")?.setSelection(ic.timingMode)
                // Sync euclidean compact panel
                val eucPanelInd = panel.findViewWithTag<View>("indEucPanel")
                if (eucPanelInd != null) {
                    eucPanelInd.visibility = if (ic.timingMode == 3) View.VISIBLE else View.GONE
                    val ps = ic.proSettings
                    panel.findViewWithTag<SeekBar>("indEucSteps")?.progress    = (ps.euclideanSteps    - 1).coerceIn(0, 31)
                    panel.findViewWithTag<SeekBar>("indEucDensity")?.progress  = (ps.euclideanDensity  - 1).coerceIn(0, 31)
                    panel.findViewWithTag<SeekBar>("indEucRotation")?.progress = ps.euclideanRotation.coerceIn(0, 31)
                }
                panel.findViewWithTag<Spinner>("${prefix}ScaleValue")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("${prefix}RootValue")?.setSelection(ic.rootNote)
                panel.findViewWithTag<Spinner>("indStyle")?.setSelection(ic.style.ordinal)
                panel.findViewWithTag<View>("chordsPanel")?.visibility =
                    if (ic.style == VoiceStyle.CHORDS) View.VISIBLE else View.GONE
                if (ic.style == VoiceStyle.CHORDS) applyChordConfigToPanel(panel, ic.chordConfig, "ind")
                val indShared = ic.useSharedPro
                panel.findViewWithTag<RadioButton>("sharedPro")?.isChecked    = indShared
                panel.findViewWithTag<RadioButton>("customPro")?.isChecked    = !indShared
                panel.findViewWithTag<View>("customProPanel")?.visibility     =
                    if (indShared) View.GONE else View.VISIBLE
                if (!indShared) {
                    val frag = if (panel === panelV2) proFragV2 else proFragV3
                    frag?.setInitialSettings(ic.proSettings)
                }
            }
            VoiceMode.MELODIC -> {
                val ic        = cfg.independentConfig
                val melPrefix = "mel"
                panel.findViewWithTag<Spinner>("${melPrefix}BpmMode")?.setSelection(ic.bpmMode.ordinal)
                panel.findViewWithTag<SeekBar>("${melPrefix}Bpm")?.progress = ic.bpm - 20
                panel.findViewWithTag<TextView>("${melPrefix}BpmValue")?.text = ic.bpm.toString()
                val fracIdx = when (ic.bpmFraction) {
                    0.125f -> 0; 0.25f -> 1; 0.5f -> 2; 1.0f -> 3; 1.5f -> 4; 2.0f -> 5; else -> 3
                }
                panel.findViewWithTag<SeekBar>("${melPrefix}BpmFraction")?.progress = fracIdx
                panel.findViewWithTag<View>("${melPrefix}CustomBpmPanel")?.visibility  =
                    if (ic.bpmMode == ParamFollowMode.CUSTOM) View.VISIBLE else View.GONE
                panel.findViewWithTag<View>("${melPrefix}FractionBpmPanel")?.visibility =
                    if (ic.bpmMode == ParamFollowMode.FRACTION) View.VISIBLE else View.GONE
                panel.findViewWithTag<SeekBar>("${melPrefix}Vel")?.progress = ic.velocity
                panel.findViewWithTag<com.google.android.material.slider.RangeSlider>("${melPrefix}Octave")?.values =
                    listOf(ic.minOctave.toFloat(), ic.maxOctave.toFloat())
                panel.findViewWithTag<SeekBar>("${melPrefix}MidiCh")?.progress = ic.midiChannel
                panel.findViewWithTag<Spinner>("${melPrefix}Timing")?.setSelection(ic.timingMode)
                // Sync euclidean compact panel
                val eucPanelMel = panel.findViewWithTag<View>("melEucPanel")
                if (eucPanelMel != null) {
                    eucPanelMel.visibility = if (ic.timingMode == 3) View.VISIBLE else View.GONE
                    val ps = ic.proSettings
                    panel.findViewWithTag<SeekBar>("melEucSteps")?.progress    = (ps.euclideanSteps    - 1).coerceIn(0, 31)
                    panel.findViewWithTag<SeekBar>("melEucDensity")?.progress  = (ps.euclideanDensity  - 1).coerceIn(0, 31)
                    panel.findViewWithTag<SeekBar>("melEucRotation")?.progress = ps.euclideanRotation.coerceIn(0, 31)
                }
                panel.findViewWithTag<Spinner>("${melPrefix}ScaleValue")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("${melPrefix}RootValue")?.setSelection(ic.rootNote)
                val melRelCfg = cfg.melodicRelationConfig
                panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress = melRelCfg.contrastDepth
                panel.findViewWithTag<Spinner>("melContrastMode")?.setSelection(melRelCfg.mode.ordinal)
                val melShared = ic.useSharedPro
                panel.findViewWithTag<RadioButton>("melSharedPro")?.isChecked = melShared
                panel.findViewWithTag<RadioButton>("melCustomPro")?.isChecked = !melShared
                panel.findViewWithTag<View>("melCustomProPanel")?.visibility  =
                    if (melShared) View.GONE else View.VISIBLE
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

    // ── Push config to service ─────────────────────────────────────────────────

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
            enabled               = enableSwitch?.isChecked ?: false,
            mode                  = mode,
            harmonyConfig         = readHarmonyConfig(harmonyPanel, voiceId),
            independentConfig     = readIndependentConfig(
                if (mode == VoiceMode.MELODIC) melodicPanel else independentPanel,
                voiceId
            ),
            melodicRelationConfig = readMelodicRelationConfig(melodicPanel)
        )
        if (voiceId == 2) { currentV2 = cfg; svc.updateVoice2Config(cfg) }
        else              { currentV3 = cfg; svc.updateVoice3Config(cfg) }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

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
