package com.nachinbombin.midirandomizer

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment — hosts the UI for Voice 2 and Voice 3.
 *
 * Each voice section has:
 *   • Enable toggle
 *   • Mode selector (Harmony / Independent)
 *   • [Harmony]     sub-panel: Tone Step, Time Drift, Skip %, Master Velocity, MIDI Ch, Reference (V3 only)
 *   • [Independent] sub-panel: same controls as Voice 1 (BPM, Scale, Octave, Timing, MIDI Ch, Velocity, Pro toggle)
 *
 * Changes are propagated to MidiService via the shared ViewModel.
 */
class VoicesFragment : Fragment() {

    private lateinit var serviceProvider: ServiceProvider

    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        serviceProvider = context as ServiceProvider
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        root.addView(column)

        column.addView(buildVoicePanel(2, "Voice 2"))
        column.addView(divider())
        column.addView(buildVoicePanel(3, "Voice 3"))
        return root
    }

    // ── Per-voice panel builder ────────────────────────────────────────────────

    private fun buildVoicePanel(voiceId: Int, label: String): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 16) }

        // ── Header row: enable switch + title
        val headerRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val enableSwitch = Switch(ctx).apply { text = label; isChecked = false; textSize = 18f }
        headerRow.addView(enableSwitch)
        panel.addView(headerRow)

        // ── Mode toggle row
        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true)
        val independentBtn = radioButton(ctx, "Independent", false)
        modeRow.addView(harmonyBtn)
        modeRow.addView(independentBtn)
        panel.addView(modeRow)

        // ── Sub-panels
        val harmonyPanel     = buildHarmonyPanel(voiceId)
        val independentPanel = buildIndependentPanel(voiceId)
        independentPanel.visibility = View.GONE
        panel.addView(harmonyPanel)
        panel.addView(independentPanel)

        // ── All views disabled until switch enabled
        fun setEnabled(on: Boolean) {
            modeRow.alpha          = if (on) 1f else 0.4f
            harmonyPanel.alpha     = if (on) 1f else 0.4f
            independentPanel.alpha = if (on) 1f else 0.4f
            setChildrenEnabled(modeRow, on)
            setChildrenEnabled(harmonyPanel, on)
            setChildrenEnabled(independentPanel, on)
        }
        setEnabled(false)

        fun syncConfig() {
            if (!enableSwitch.isChecked) {
                pushConfig(voiceId, VoiceConfig(enabled = false))
                return
            }
            val mode = if (harmonyBtn.isChecked) VoiceMode.HARMONY else VoiceMode.INDEPENDENT
            val cfg  = VoiceConfig(
                enabled           = true,
                mode              = mode,
                harmonyConfig     = readHarmonyConfig(harmonyPanel, voiceId),
                independentConfig = readIndependentConfig(independentPanel)
            )
            pushConfig(voiceId, cfg)
        }

        enableSwitch.setOnCheckedChangeListener { _, on ->
            setEnabled(on)
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

        // Attach sync listener to all sliders/spinners inside sub-panels
        attachSyncListeners(harmonyPanel)     { syncConfig() }
        attachSyncListeners(independentPanel) { syncConfig() }

        return panel
    }

    // ── Harmony sub-panel ─────────────────────────────────────────────────────

    private fun buildHarmonyPanel(voiceId: Int): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "Tone Step Offset", -7, 7, 2,   tag = "toneStep"))
        panel.addView(labeledSeekBar(ctx, "Time Drift (ms)",   0, 45, 10,  tag = "timeDrift"))
        panel.addView(labeledSeekBar(ctx, "Skip % (0-100)",    0, 100, 0,  tag = "skipPct"))
        panel.addView(labeledSeekBar(ctx, "Master Velocity",   0, 127, 100,tag = "masterVel"))
        panel.addView(labeledSeekBar(ctx, "Velocity Drift ±",  0, 20, 8,   tag = "velDrift"))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)", 0, 16, voiceId, tag = "midiCh"))

        if (voiceId == 3) {
            val refRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            refRow.addView(TextView(ctx).apply { text = "Reference: " })
            val refSpinner = Spinner(ctx).apply { tag = "refVoice" }
            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, listOf("Voice 1", "Voice 2"))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            refSpinner.adapter = adapter
            refRow.addView(refSpinner)
            panel.addView(refRow)
        }
        return panel
    }

    // ── Independent sub-panel ─────────────────────────────────────────────────

    private fun buildIndependentPanel(voiceId: Int): LinearLayout {
        val ctx   = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 0, 0) }

        panel.addView(labeledSeekBar(ctx, "BPM",            20,  300, 120, tag = "indBpm"))
        panel.addView(labeledSeekBar(ctx, "Velocity",        0,  127,  90, tag = "indVel"))
        panel.addView(labeledSeekBar(ctx, "Min Octave",      0,    8,   3, tag = "indMinOct"))
        panel.addView(labeledSeekBar(ctx, "Max Octave",      0,    8,   5, tag = "indMaxOct"))
        panel.addView(labeledSeekBar(ctx, "MIDI Channel (0=Omni)", 0, 16, voiceId + 1, tag = "indMidiCh"))

        // Scale spinner
        val scaleNames = listOf("Chromatic","Major","Minor Natural","Minor Harmonic",
            "Pentatonic Maj","Pentatonic Min","Blues","Dorian","Mixolydian","Whole Tone")
        val scaleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        scaleRow.addView(TextView(ctx).apply { text = "Scale: " })
        val scaleSpinner = Spinner(ctx).apply { tag = "indScale" }
        scaleSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, scaleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scaleRow.addView(scaleSpinner)
        panel.addView(scaleRow)

        // Timing mode spinner
        val timingRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        timingRow.addView(TextView(ctx).apply { text = "Timing: " })
        val timingSpinner = Spinner(ctx).apply { tag = "indTiming" }
        timingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome","Mixed","Randomized","Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        timingRow.addView(timingSpinner)
        panel.addView(timingRow)

        return panel
    }

    // ── Config readers ────────────────────────────────────────────────────────

    private fun readHarmonyConfig(panel: LinearLayout, voiceId: Int): HarmonyConfig {
        fun seekVal(tag: String, default: Int = 0): Int =
            (panel.findViewWithTag<SeekBar>(tag)?.progress ?: default)
        val refVoice = if (voiceId == 3) {
            ((panel.findViewWithTag<Spinner>("refVoice")?.selectedItemPosition ?: 0) + 1)
        } else 1
        return HarmonyConfig(
            toneStepOffset  = seekVal("toneStep", 2) - 7,  // offset: slider 0-14 → -7 to +7
            timeDriftMs     = seekVal("timeDrift", 10).toLong(),
            skipProbability = seekVal("skipPct", 0) / 100f,
            masterVelocity  = seekVal("masterVel", 100),
            velocityDrift   = seekVal("velDrift", 8),
            midiChannel     = seekVal("midiCh", voiceId),
            referenceVoice  = refVoice
        )
    }

    private fun readIndependentConfig(panel: LinearLayout): IndependentConfig {
        fun seekVal(tag: String, default: Int = 0): Int =
            (panel.findViewWithTag<SeekBar>(tag)?.progress ?: default)
        return IndependentConfig(
            bpm           = seekVal("indBpm", 120).coerceAtLeast(20),
            velocity      = seekVal("indVel", 90),
            minOctave     = seekVal("indMinOct", 3),
            maxOctave     = seekVal("indMaxOct", 5),
            midiChannel   = seekVal("indMidiCh", 3),
            selectedScale = panel.findViewWithTag<Spinner>("indScale")?.selectedItemPosition ?: 1,
            timingMode    = panel.findViewWithTag<Spinner>("indTiming")?.selectedItemPosition ?: 0,
            proSettings   = ProSettings()
        )
    }

    // ── Propagate to service ──────────────────────────────────────────────────

    private fun pushConfig(voiceId: Int, cfg: VoiceConfig) {
        val svc = serviceProvider.getMidiService() ?: return
        when (voiceId) {
            2 -> svc.updateVoice2Config(cfg)
            3 -> svc.updateVoice3Config(cfg)
        }
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private fun radioButton(ctx: android.content.Context, text: String, checked: Boolean) =
        RadioButton(ctx).apply { this.text = text; isChecked = checked; setPadding(0, 0, 32, 0) }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).also { it.setMargins(0, 24, 0, 24) }
        setBackgroundColor(0xFF444444.toInt())
    }

    /**
     * Seek bar with a min-value offset (SeekBar.min requires API 26, so we emulate it).
     * The SeekBar range is always 0..[max-min]; caller subtracts [min] when reading.
     * The SeekBar is tagged with [tag] for lookup.
     */
    private fun labeledSeekBar(
        ctx: android.content.Context,
        label: String,
        min: Int, max: Int, default: Int,
        tag: String
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 4) }
        val tv  = TextView(ctx).apply { text = "$label: $default"; minWidth = 220; textSize = 13f }
        val sb  = SeekBar(ctx).apply {
            this.tag      = tag
            this.max      = max - min
            this.progress = (default - min).coerceIn(0, max - min)
            layoutParams  = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                tv.text = "$label: ${progress + min}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?)  {}
        })
        row.addView(tv)
        row.addView(sb)
        return row
    }

    private fun setChildrenEnabled(group: ViewGroup, enabled: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            child.isEnabled = enabled
            if (child is ViewGroup) setChildrenEnabled(child, enabled)
        }
    }

    /** Attach a no-arg lambda to every SeekBar and Spinner inside [group]. */
    private fun attachSyncListeners(group: ViewGroup, onChange: () -> Unit) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is SeekBar -> child.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?)  {}
                })
                is Spinner -> child.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { onChange() }
                    override fun onNothingSelected(a: AdapterView<*>?) {}
                }
                is ViewGroup -> attachSyncListeners(child, onChange)
            }
        }
    }
}
