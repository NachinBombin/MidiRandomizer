package com.nachinbombin.midirandomizer

import android.content.Context
import android.media.midi.MidiManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.RangeSlider

/**
 * VoicesFragment — hosts the UI for Voice 2 and Voice 3.
 * Background uses [ThemePreset.bgVoices].
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
        fun getMidiManager(): MidiManager?
    }

    fun onServiceReady() {}

    override fun onAttach(context: Context) {
        super.onAttach(context)
        serviceProvider = context as? ServiceProvider
    }

    override fun onStart() { super.onStart(); (activity as? MainActivity)?.addMidiListener(this) }
    override fun onStop()  { super.onStop();  (activity as? MainActivity)?.removeMidiListener(this) }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {}
    override fun onStatusChanged(status: String) {}
    override fun onPlaybackStateChanged(playing: Boolean) {}

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded || view == null) return
        syncFromService(v1, v2, v3)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val preset = ThemeManager.loadTheme(requireContext())
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(preset.bgVoices)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.applyToView(view, ThemeManager.loadTheme(requireContext()), forVoices = true)
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
            text = label; isChecked = false; textSize = 18f
            setTextColor(0xFFE8E6E1.toInt()); tag = "enable"
        }
        headerRow.addView(enableSwitch)
        panel.addView(headerRow)

        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 0); tag = "modeRow"
        }
        val harmonyBtn     = radioButton(ctx, "Harmony",     true).apply  { tag = "rbHarmony" }
        val independentBtn = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        modeRow.addView(harmonyBtn); modeRow.addView(independentBtn)
        panel.addView(modeRow)

        var syncConfigAction: (() -> Unit)? = null
        val harmonyPanel     = buildHarmonyPanel(voiceId)     { syncConfigAction?.invoke() }.apply { tag = "harmonyPanel" }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }.apply { tag = "independentPanel" }
        independentPanel.visibility = View.GONE
        panel.addView(harmonyPanel); panel.addView(independentPanel)

        fun setEnabledState(on: Boolean) {
            modeRow.visibility          = if (on) View.VISIBLE else View.GONE
            harmonyPanel.visibility     = if (on) View.VISIBLE else View.GONE
            independentPanel.visibility = View.GONE
        }

        enableSwitch.setOnCheckedChangeListener { _, on -> setEnabledState(on); syncConfig(voiceId) }

        fun setMode(harmony: Boolean) {
            harmonyBtn.isChecked = harmony; independentBtn.isChecked = !harmony
            harmonyPanel.visibility     = if (harmony)  View.VISIBLE else View.GONE
            independentPanel.visibility = if (!harmony) View.VISIBLE else View.GONE
            syncConfig(voiceId)
        }
        harmonyBtn.setOnClickListener     { setMode(true)  }
        independentBtn.setOnClickListener { setMode(false) }
        syncConfigAction = { syncConfig(voiceId) }
        setEnabledState(false)
        return panel
    }

    private fun syncConfig(voiceId: Int) {
        if (isUpdatingFromSync || view == null) return
        val panel  = if (voiceId == 2) panelV2 else panelV3
        val enable = panel.findViewWithTag<Switch>("enable")?.isChecked ?: false
        if (!enable) {
            val cfg = if (voiceId == 2) currentV2 else currentV3
            val disabled = cfg.copy(enabled = false)
            if (voiceId == 2) { currentV2 = disabled; serviceProvider?.getMidiService()?.updateVoice2Config(disabled) }
            else              { currentV3 = disabled; serviceProvider?.getMidiService()?.updateVoice3Config(disabled) }
            return
        }
        val isHarmony = panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked ?: true
        val config = if (isHarmony) readHarmonyConfig(panel, voiceId) else readIndependentConfig(panel, voiceId)
        if (voiceId == 2) { currentV2 = config; serviceProvider?.getMidiService()?.updateVoice2Config(config) }
        else              { currentV3 = config; serviceProvider?.getMidiService()?.updateVoice3Config(config) }
    }

    // ── Harmony panel ──────────────────────────────────────────────────────

    private fun buildHarmonyPanel(voiceId: Int, onChanged: () -> Unit): LinearLayout {
        val ctx    = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0) }

        layout.addView(sectionLabel("INTERVAL"))
        val intervals = listOf("-24","-19","-17","-15","-14","-12","-10","-9","-8",
            "-7","-5","-4","-3","-2","-1","0","+1","+2","+3","+4","+5","+7","+8",
            "+9","+10","+12","+14","+15","+17","+19","+24")
        val spinnerInterval = Spinner(ctx).apply { tag = "harmInterval" }
        spinnerInterval.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, intervals)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerInterval.setSelection(intervals.indexOf("+7").coerceAtLeast(0))
        spinnerInterval.onItemSelectedListener = simpleSpinner { onChanged() }
        layout.addView(spinnerInterval)

        layout.addView(sectionLabel("MIDI CHANNEL"))
        val chLabels = (0..16).map { if (it == 0) "Same as V1" else "Ch $it" }
        val spinnerCh = Spinner(ctx).apply { tag = "harmChannel" }
        spinnerCh.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, chLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerCh.onItemSelectedListener = simpleSpinner { onChanged() }
        layout.addView(spinnerCh)
        return layout
    }

    private fun readHarmonyConfig(panel: LinearLayout, voiceId: Int): VoiceConfig {
        val hp = panel.findViewWithTag<View>("harmonyPanel") as? LinearLayout ?: return VoiceConfig()
        val intervalIdx = hp.findViewWithTag<Spinner>("harmInterval")?.selectedItemPosition ?: 15
        val intervals   = listOf(-24,-19,-17,-15,-14,-12,-10,-9,-8,-7,-5,-4,-3,-2,-1,0,1,2,3,4,5,7,8,9,10,12,14,15,17,19,24)
        val semitones   = intervals.getOrElse(intervalIdx) { 7 }
        val channel     = hp.findViewWithTag<Spinner>("harmChannel")?.selectedItemPosition ?: 0
        val existing    = if (voiceId == 2) currentV2 else currentV3
        return existing.copy(
            enabled       = true,
            mode          = VoiceMode.HARMONY,
            harmonyConfig = existing.harmonyConfig.copy(
                toneStepOffset = semitones,
                midiChannel    = channel
            )
        )
    }

    // ── Independent panel ─────────────────────────────────────────────────

    private fun buildIndependentPanel(voiceId: Int, onChanged: () -> Unit): LinearLayout {
        val ctx    = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0) }

        val scales = listOf(
            "Chromatic","Major","Minor (Natural)","Minor (Harmonic)",
            "Pentatonic Major","Pentatonic Minor","Blues","Dorian",
            "Mixolydian","Whole Tone","Kurd (Annaziska / Aeolian)",
            "Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono"
        )
        val styles = listOf("Generative", "Single note Drone", "Evolving Drone")

        layout.addView(sectionLabel("SCALE"))
        val spinnerScale = Spinner(ctx).apply { tag = "indScale" }
        spinnerScale.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, scales)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerScale.onItemSelectedListener = simpleSpinner { onChanged() }
        layout.addView(spinnerScale)

        layout.addView(sectionLabel("STYLE"))
        val spinnerStyle = Spinner(ctx).apply { tag = "indStyle" }
        spinnerStyle.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, styles)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        layout.addView(spinnerStyle)

        layout.addView(sectionLabel("ROOT NOTE"))
        val rootLabels = listOf(
            "Follow Global",
            "C","C#","D","D#","E","F",
            "F#","G","G#","A","A#","B"
        )
        val spinnerRoot = Spinner(ctx).apply { tag = "indRoot" }
        spinnerRoot.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, rootLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerRoot.onItemSelectedListener = simpleSpinner { onChanged() }
        layout.addView(spinnerRoot)

        layout.addView(sectionLabel("MIDI CHANNEL"))
        val chLabels = (0..16).map { if (it == 0) "Ch Omni (0)" else "Ch $it" }
        val spinnerCh = Spinner(ctx).apply { tag = "indChannel" }
        spinnerCh.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, chLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerCh.onItemSelectedListener = simpleSpinner { onChanged() }
        layout.addView(spinnerCh)

        layout.addView(sectionLabel("OCTAVE RANGE"))
        val tvOctave = TextView(ctx).apply {
            tag = "tvOctaveRange"; setTextColor(0xFFCDCCCA.toInt()); textSize = 13f; text = "Octave Range: 3 - 5"
        }
        layout.addView(tvOctave)
        val rangeOctave = RangeSlider(ctx).apply {
            tag = "rangeOctave"; valueFrom = 0f; valueTo = 8f
            values = listOf(3f, 5f); stepSize = 1f
        }
        rangeOctave.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val v = slider.values
            tvOctave.text = "Octave Range: ${v[0].toInt()} - ${v[1].toInt()}"
            onChanged()
        }
        layout.addView(rangeOctave)

        // Single-note drone octave controls
        val droneSingleGroup = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; tag = "droneSingleGroup"; visibility = View.GONE
        }
        layout.addView(droneSingleGroup)
        droneSingleGroup.addView(sectionLabel("DRONE OCTAVE"))
        val tvDroneOctave = TextView(ctx).apply {
            tag = "tvDroneOctave"; setTextColor(0xFFCDCCCA.toInt()); textSize = 13f; text = "Octave Range: 3 - 5"
        }
        droneSingleGroup.addView(tvDroneOctave)
        val rangeDroneOctave = RangeSlider(ctx).apply {
            tag = "rangeDroneOctave"; valueFrom = 0f; valueTo = 8f
            values = listOf(3f, 5f); stepSize = 1f
        }
        rangeDroneOctave.addOnChangeListener { slider, _, fromUser ->
            if (!fromUser) return@addOnChangeListener
            val v = slider.values
            tvDroneOctave.text = "Octave Range: ${v[0].toInt()} - ${v[1].toInt()}"
            onChanged()
        }
        droneSingleGroup.addView(rangeDroneOctave)

        // Wire style spinner AFTER all sub-views exist.
        // Guard with isUpdatingFromSync so setSelection() during sync never
        // collapses the octave/drone controls to the Generative defaults.
        spinnerStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (isUpdatingFromSync) return
                rangeOctave.visibility      = if (pos != 1) View.VISIBLE else View.GONE
                droneSingleGroup.visibility = if (pos == 1) View.VISIBLE else View.GONE
                onChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        return layout
    }

    private fun readIndependentConfig(panel: LinearLayout, voiceId: Int): VoiceConfig {
        val ip       = panel.findViewWithTag<View>("independentPanel") as? LinearLayout ?: return VoiceConfig()
        val scalePos = ip.findViewWithTag<Spinner>("indScale")?.selectedItemPosition ?: 0
        val stylePos = ip.findViewWithTag<Spinner>("indStyle")?.selectedItemPosition ?: 0
        val rootPos  = ip.findViewWithTag<Spinner>("indRoot")?.selectedItemPosition  ?: 0
        val channel  = ip.findViewWithTag<Spinner>("indChannel")?.selectedItemPosition ?: 0
        val range    = ip.findViewWithTag<RangeSlider>("rangeOctave")?.values ?: listOf(3f, 5f)
        val droneRange = ip.findViewWithTag<RangeSlider>("rangeDroneOctave")?.values ?: listOf(3f, 5f)
        val existing = if (voiceId == 2) currentV2 else currentV3
        return existing.copy(
            enabled = true,
            mode    = VoiceMode.INDEPENDENT,
            independentConfig = existing.independentConfig.copy(
                selectedScale  = scalePos,
                style          = VoiceStyle.entries[stylePos],
                rootNote       = rootPos,   // 0 = follow global, 1..12 = C..B
                midiChannel    = channel,
                minOctave      = range[0].toInt(),
                maxOctave      = range[1].toInt(),
                droneOctaveMin = droneRange[0].toInt(),
                droneOctaveMax = droneRange[1].toInt()
            )
        )
    }

    // ── Sync from service ─────────────────────────────────────────────────

    fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded || view == null) return
        isUpdatingFromSync = true
        currentV2 = v2; currentV3 = v3
        syncPanelFromConfig(panelV2, v2)
        syncPanelFromConfig(panelV3, v3)
        isUpdatingFromSync = false
    }

    private fun syncPanelFromConfig(panel: LinearLayout, cfg: VoiceConfig) {
        panel.findViewWithTag<Switch>("enable")?.isChecked = cfg.enabled
        if (!cfg.enabled) return
        val isHarmony = cfg.mode == VoiceMode.HARMONY
        panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked     = isHarmony
        panel.findViewWithTag<RadioButton>("rbIndependent")?.isChecked = !isHarmony
        panel.findViewWithTag<View>("harmonyPanel")?.visibility     = if (isHarmony)  View.VISIBLE else View.GONE
        panel.findViewWithTag<View>("independentPanel")?.visibility = if (!isHarmony) View.VISIBLE else View.GONE
        if (isHarmony) {
            val intervals = listOf(-24,-19,-17,-15,-14,-12,-10,-9,-8,-7,-5,-4,-3,-2,-1,0,1,2,3,4,5,7,8,9,10,12,14,15,17,19,24)
            val idx = intervals.indexOf(cfg.harmonyConfig.toneStepOffset).coerceAtLeast(0)
            val hp = panel.findViewWithTag<View>("harmonyPanel") as? LinearLayout
            hp?.findViewWithTag<Spinner>("harmInterval")?.setSelection(idx)
            hp?.findViewWithTag<Spinner>("harmChannel")?.setSelection(cfg.harmonyConfig.midiChannel)
        } else {
            val ip = panel.findViewWithTag<View>("independentPanel") as? LinearLayout
            ip?.findViewWithTag<Spinner>("indScale")?.setSelection(cfg.independentConfig.selectedScale)
            ip?.findViewWithTag<Spinner>("indStyle")?.setSelection(cfg.independentConfig.style.ordinal)
            ip?.findViewWithTag<Spinner>("indRoot")?.setSelection(cfg.independentConfig.rootNote)
            ip?.findViewWithTag<Spinner>("indChannel")?.setSelection(cfg.independentConfig.midiChannel)
            // Restore visibility based on saved style
            val stylePos = cfg.independentConfig.style.ordinal
            ip?.findViewWithTag<RangeSlider>("rangeOctave")?.let { slider ->
                slider.visibility = if (stylePos != 1) View.VISIBLE else View.GONE
                slider.values = listOf(
                    cfg.independentConfig.minOctave.toFloat(),
                    cfg.independentConfig.maxOctave.toFloat()
                )
            }
            ip?.findViewWithTag<LinearLayout>("droneSingleGroup")?.visibility =
                if (stylePos == 1) View.VISIBLE else View.GONE
            ip?.findViewWithTag<RangeSlider>("rangeDroneOctave")?.values =
                listOf(cfg.independentConfig.droneOctaveMin.toFloat(), cfg.independentConfig.droneOctaveMax.toFloat())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun sectionLabel(text: String) = TextView(requireContext()).apply {
        this.text = text; textSize = 11f
        setTextColor(0xFF7A7974.toInt()); setPadding(0, 16, 0, 4); tag = "sectionHeader"
    }

    private fun radioButton(ctx: Context, label: String, checked: Boolean) = RadioButton(ctx).apply {
        text = label; isChecked = checked
        buttonTintList = android.content.res.ColorStateList.valueOf(0xFF4F9AA5.toInt())
        setTextColor(0xFFCDCCCA.toInt()); setPadding(0, 0, 24, 0)
    }

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, 16, 0, 16) }
        setBackgroundColor(0xFF444444.toInt()); tag = "divider"
    }

    private fun simpleSpinner(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) { if (!isUpdatingFromSync) block(pos) }
        override fun onNothingSelected(p: AdapterView<*>) {}
    }
}
