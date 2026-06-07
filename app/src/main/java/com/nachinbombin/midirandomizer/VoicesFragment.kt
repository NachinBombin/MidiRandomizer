package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment manages the V2 and V3 voice panels.
 * Each panel supports Harmony, Independent, and Melodic modes.
 * When Timing = Euclidean (index 3) in Independent or Melodic mode,
 * a compact Euclidean sub-panel spawns inline under the Timing spinner.
 * No toggle switch is used — the Timing selection itself is the enable.
 */
class VoicesFragment : Fragment() {

    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    private var serviceProvider: ServiceProvider? = null
    private var syncConfigAction: (() -> Unit)? = null

    private lateinit var panelV2: LinearLayout
    private lateinit var panelV3: LinearLayout

    var currentV2: VoiceConfig = VoiceConfig()
    var currentV3: VoiceConfig = VoiceConfig()

    private var isUpdatingFromSync = false

    // Pro fragment references for V2/V3 independent panels
    private var proFragV2: ProSettingsFragment? = null
    private var proFragV3: ProSettingsFragment? = null
    // Pro fragment references for V2/V3 melodic panels
    private var proFragMelodicV2: ProSettingsFragment? = null
    private var proFragMelodicV3: ProSettingsFragment? = null

    private var customProSettingsV2: ProSettings = ProSettings()
    private var customProSettingsV3: ProSettings = ProSettings()

    fun setServiceProvider(sp: ServiceProvider) { serviceProvider = sp }
    fun setSyncAction(action: () -> Unit) { syncConfigAction = action }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        panelV2 = buildVoicePanel(2)
        panelV3 = buildVoicePanel(3)
        root.addView(panelV2)
        root.addView(panelV3)
        return root
    }

    // ── Top-level voice panel ─────────────────────────────────────────────

    private fun buildVoicePanel(voiceId: Int): LinearLayout {
        val ctx = requireContext()
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        // Header row: enable switch + label
        val headerRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val enableSwitch = Switch(ctx).apply {
            tag = "enable"; isChecked = false
            thumbTint = android.content.res.ColorStateList.valueOf(0xFF4F9AA5.toInt())
            trackTint = android.content.res.ColorStateList.valueOf(0xFF262523.toInt())
        }
        val label = TextView(ctx).apply {
            text = "Voice $voiceId"; textSize = 15f
            setTextColor(0xFFE8E6E1.toInt())
            setPadding(12, 0, 0, 0)
        }
        headerRow.addView(enableSwitch)
        headerRow.addView(label)
        outer.addView(headerRow)

        // Mode selector
        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 8)
        }
        val rbHarmony     = radioButton(ctx, "Harmony",     true ).apply { tag = "rbHarmony"     }
        val rbIndependent = radioButton(ctx, "Independent", false).apply { tag = "rbIndependent" }
        val rbMelodic     = radioButton(ctx, "Melodic",     false).apply { tag = "rbMelodic"     }
        modeRow.addView(rbHarmony)
        modeRow.addView(rbIndependent)
        modeRow.addView(rbMelodic)
        outer.addView(modeRow)

        // Mode panels
        val harmonyPanel     = buildHarmonyPanel(ctx, voiceId).apply     { tag = "harmonyPanel";     visibility = View.VISIBLE }
        val independentPanel = buildIndependentPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "independentPanel"; visibility = View.GONE }
        val melodicPanel     = buildMelodicPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "melodicPanel"; visibility = View.GONE }

        outer.addView(harmonyPanel)
        outer.addView(independentPanel)
        outer.addView(melodicPanel)

        // Enable toggle
        enableSwitch.setOnCheckedChangeListener { _, _ ->
            if (!isUpdatingFromSync) syncConfigAction?.invoke()
        }

        // Mode switching
        fun switchMode(harmony: Boolean, independent: Boolean, melodic: Boolean) {
            rbHarmony.isChecked     = harmony
            rbIndependent.isChecked = independent
            rbMelodic.isChecked     = melodic
            harmonyPanel.visibility     = if (harmony)     View.VISIBLE else View.GONE
            independentPanel.visibility = if (independent) View.VISIBLE else View.GONE
            melodicPanel.visibility     = if (melodic)     View.VISIBLE else View.GONE
            if (!isUpdatingFromSync) syncConfigAction?.invoke()
        }
        rbHarmony.setOnClickListener     { switchMode(true,  false, false) }
        rbIndependent.setOnClickListener { switchMode(false, true,  false) }
        rbMelodic.setOnClickListener     { switchMode(false, false, true ) }

        return outer
    }

    // ── Harmony panel ─────────────────────────────────────────────────────

    private fun buildHarmonyPanel(ctx: Context, voiceId: Int): LinearLayout {
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        panel.addView(labeledSeekBar(ctx, "Velocity", 0, 127, 80, "masterVel") { syncConfigAction?.invoke() })
        panel.addView(labeledSeekBar(ctx, "MIDI Ch",  0, 15,   0, "midiCh")    { syncConfigAction?.invoke() })
        return panel
    }

    // ── Independent panel ─────────────────────────────────────────────────

    private fun buildIndependentPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        panel.addView(labeledSeekBar(ctx, "Velocity",  0, 127, 80,  "indVel",    onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Ch",   0,  15,  0,  "indMidiCh", onSync))
        panel.addView(rangeSeekBar(ctx,   "Octave",    0,   8,  3, 6, "indOctave", onSync))

        val scaleNames = listOf(
            "Chromatic","Major","Natural Minor","Harmonic Minor","Melodic Minor",
            "Dorian","Phrygian","Lydian","Mixolydian","Locrian",
            "Pentatonic Major","Pentatonic Minor","Blues","Whole Tone","Diminished",
            "Augmented","Hungarian Minor","Persian","Hirajoshi","In-Sen",
            "Yo","Iwato","Kumoi","Pelog","Slendro",
            "Arabian","Spanish Phrygian","Gypsy","Ukrainian Dorian","Neapolitan",
            "Neapolitan Major","Romanian Minor","Double Harmonic","Eight-Tone Spanish",
            "Enigmatic","Leading Whole-Tone","Lydian Minor","Lydian Diminished",
            "Nine-Tone","Prometheus","Prometheus Neapolitan","Symmetrical",
            "Super Locrian","Overtone","Six-Tone Symmetrical","Balinese",
            "Javanese","Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")

        panel.addView(spinnerRow(ctx, "Scale", scaleNames, "indScaleValue", onSync))

        val rootNames = listOf("Follow Main", "C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        panel.addView(spinnerRow(ctx, "Root", rootNames, "indRootValue", onSync))

        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "indTiming", null))

        // Compact Euclidean sub-panel — visible only when Timing = Euclidean; no toggle needed
        val indEucPanel = buildCompactEuclideanPanel(ctx, "ind", onSync)
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

        panel.addView(spinnerRow(ctx, "Style",  listOf("Generative","Single-Note Drone","Evolving Drone","Chords"), "indStyle", null))

        // Full Chords sub-panel
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

        // Pro Settings toggle
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

    // ── Melodic panel ─────────────────────────────────────────────────────

    private fun buildMelodicPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        panel.addView(labeledSeekBar(ctx, "Velocity",  0, 127, 80,  "melVel",    onSync))
        panel.addView(labeledSeekBar(ctx, "MIDI Ch",   0,  15,  0,  "melMidiCh", onSync))
        panel.addView(rangeSeekBar(ctx,   "Octave",    0,   8,  3, 6, "melOctave", onSync))

        val scaleNames = listOf(
            "Chromatic","Major","Natural Minor","Harmonic Minor","Melodic Minor",
            "Dorian","Phrygian","Lydian","Mixolydian","Locrian",
            "Pentatonic Major","Pentatonic Minor","Blues","Whole Tone","Diminished",
            "Augmented","Hungarian Minor","Persian","Hirajoshi","In-Sen",
            "Yo","Iwato","Kumoi","Pelog","Slendro",
            "Arabian","Spanish Phrygian","Gypsy","Ukrainian Dorian","Neapolitan",
            "Neapolitan Major","Romanian Minor","Double Harmonic","Eight-Tone Spanish",
            "Enigmatic","Leading Whole-Tone","Lydian Minor","Lydian Diminished",
            "Nine-Tone","Prometheus","Prometheus Neapolitan","Symmetrical",
            "Super Locrian","Overtone","Six-Tone Symmetrical","Balinese",
            "Javanese","Kurd (Annaziska / Aeolian)","Celtic Minor (Amara)","Pygmy","SaBye / SaByeD",
            "Aegean (Lydian)","Hijaz","Akebono")

        panel.addView(spinnerRow(ctx, "Scale", scaleNames, "melScaleValue", onSync))

        val rootNames = listOf("Follow Main", "C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        panel.addView(spinnerRow(ctx, "Root", rootNames, "melRootValue", onSync))

        panel.addView(spinnerRow(ctx, "Timing", listOf("Metronome","Mixed","Randomized","Euclidean"), "melTiming", null))

        // Compact Euclidean sub-panel — visible only when Timing = Euclidean; no toggle needed
        val melEucPanel = buildCompactEuclideanPanel(ctx, "mel", onSync)
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

        // Melodic Relation / Contrast controls
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

        // Pro Settings
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

    /** Compact Euclidean sub-panel spawned inline under the Timing spinner.
     *  No toggle switch — Timing spinner selection IS the enable. */
    private fun buildCompactEuclideanPanel(ctx: Context, prefix: String, onSync: () -> Unit): LinearLayout {
        val p = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 6, 0, 6)
            setBackgroundColor(0xFF1A1D24.toInt())
        }
        p.addView(TextView(ctx).apply {
            text = "EUCLIDEAN SETTINGS"; textSize = 11f
            setTextColor(0xFF4F9AA5.toInt()); letterSpacing = 0.12f
            setPadding(0, 4, 0, 8)
        })
        p.addView(labeledSeekBar(ctx, "Steps",    1, 32, 16, "${prefix}EucSteps",    onSync))
        p.addView(labeledSeekBar(ctx, "Density",  1, 32,  8, "${prefix}EucDensity",  onSync))
        p.addView(labeledSeekBar(ctx, "Rotation", 0, 31,  0, "${prefix}EucRotation", onSync))
        return p
    }

    // ── Read independent config from panel ────────────────────────────────

    private fun readIndependentConfig(panel: LinearLayout, voiceId: Int): IndependentConfig {
        fun seekVal(tag: String)  = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        fun sp(tag: String)       = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0
        fun rsVal(tag: String): IntArray {
            val rs = panel.findViewWithTag<com.crystal.crystalrangeseekbar.widget.CrystalRangeSeekbar>(tag)
            return if (rs != null) intArrayOf(rs.selectedMinValue.toInt(), rs.selectedMaxValue.toInt())
            else intArrayOf(3, 6)
        }

        val isMelodic = panel.tag?.toString()?.startsWith("mel") == true ||
                        panel.findViewWithTag<View>("melSharedPro") != null
        val prefix = if (isMelodic) "mel" else "ind"

        val bpmMode = 0
        val bpm = 120
        val bpmFraction = when (0) {
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
        val eucSteps    = (panel.findViewWithTag<SeekBar>("${prefix}EucSteps")?.progress ?: 15) + 1
        val eucDensity  = (panel.findViewWithTag<SeekBar>("${prefix}EucDensity")?.progress ?: 7) + 1
        val eucRotation = panel.findViewWithTag<SeekBar>("${prefix}EucRotation")?.progress ?: 0
        val finalPro = resolvedPro.copy(
            euclideanEnabled  = timing == 3,
            euclideanSteps    = eucSteps,
            euclideanDensity  = eucDensity,
            euclideanRotation = eucRotation
        )

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
            proSettings   = finalPro,
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
                panel.findViewWithTag<SeekBar>("indVel")?.progress     = ic.velocity
                panel.findViewWithTag<SeekBar>("indMidiCh")?.progress  = ic.midiChannel
                panel.findViewWithTag<Spinner>("indStyle")?.setSelection(ic.style.ordinal)
                panel.findViewWithTag<Spinner>("indRoot")?.setSelection(ic.rootNote)
                panel.findViewWithTag<Spinner>("indScale")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("indTiming")?.setSelection(ic.timingMode)
                panel.findViewWithTag<View>("indEucPanel")?.visibility =
                    if (ic.timingMode == 3) View.VISIBLE else View.GONE
                panel.findViewWithTag<SeekBar>("indEucSteps")?.progress    = (ic.proSettings.euclideanSteps - 1).coerceAtLeast(0)
                panel.findViewWithTag<SeekBar>("indEucDensity")?.progress  = (ic.proSettings.euclideanDensity - 1).coerceAtLeast(0)
                panel.findViewWithTag<SeekBar>("indEucRotation")?.progress = ic.proSettings.euclideanRotation
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
                panel.findViewWithTag<SeekBar>("melVel")?.progress    = ic.velocity
                panel.findViewWithTag<SeekBar>("melMidiCh")?.progress = ic.midiChannel
                panel.findViewWithTag<Spinner>("melRoot")?.setSelection(ic.rootNote)
                panel.findViewWithTag<Spinner>("melScale")?.setSelection(ic.selectedScale)
                panel.findViewWithTag<Spinner>("melTiming")?.setSelection(ic.timingMode)
                panel.findViewWithTag<View>("melEucPanel")?.visibility =
                    if (ic.timingMode == 3) View.VISIBLE else View.GONE
                panel.findViewWithTag<SeekBar>("melEucSteps")?.progress    = (ic.proSettings.euclideanSteps - 1).coerceAtLeast(0)
                panel.findViewWithTag<SeekBar>("melEucDensity")?.progress  = (ic.proSettings.euclideanDensity - 1).coerceAtLeast(0)
                panel.findViewWithTag<SeekBar>("melEucRotation")?.progress = ic.proSettings.euclideanRotation
                val mrc = cfg.melodicRelationConfig
                panel.findViewWithTag<Switch>("melContrastEnabled")?.isChecked = mrc.enabled
                panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress   = mrc.contrastDepth
                panel.findViewWithTag<Spinner>("melContrastMode")?.setSelection(mrc.mode.ordinal)
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

    // ── Push current config to service ────────────────────────────────────

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

    fun onProSettingsChanged(voiceId: Int, settings: ProSettings) {
        if (voiceId == 2) customProSettingsV2 = settings
        else              customProSettingsV3 = settings
        pushConfigToService(voiceId)
    }

    // ── Fragment lifecycle: attach Pro fragments ──────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        attachProFragment(2, false)
        attachProFragment(3, false)
        attachProFragment(2, true)
        attachProFragment(3, true)
    }

    private fun attachProFragment(voiceId: Int, isMelodic: Boolean) {
        val containerId = if (isMelodic) proFragContainerMelodicId(voiceId) else proFragContainerId(voiceId)
        val tag = if (isMelodic) "proFragMelodic$voiceId" else "proFrag$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment
            ?: ProSettingsFragment().also {
                childFragmentManager.beginTransaction()
                    .replace(containerId, it, tag)
                    .commit()
            }
        frag.setListener(object : ProSettingsFragment.OnSettingsChangedListener {
            override fun onProSettingsChanged(settings: ProSettings) {
                onProSettingsChanged(voiceId, settings)
            }
        })
        if (isMelodic) {
            if (voiceId == 2) proFragMelodicV2 = frag else proFragMelodicV3 = frag
        } else {
            if (voiceId == 2) proFragV2 = frag else proFragV3 = frag
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun readHarmonyConfig(panel: LinearLayout, voiceId: Int): HarmonyConfig {
        return HarmonyConfig(
            masterVelocity = panel.findViewWithTag<SeekBar>("masterVel")?.progress ?: 80,
            midiChannel    = panel.findViewWithTag<SeekBar>("midiCh")?.progress ?: 0
        )
    }

    private fun readMelodicRelationConfig(panel: LinearLayout): MelodicRelationConfig {
        val enabled = panel.findViewWithTag<Switch>("melContrastEnabled")?.isChecked ?: false
        val depth   = panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress ?: 50
        val mode    = ContrastMode.entries.getOrElse(
            panel.findViewWithTag<Spinner>("melContrastMode")?.selectedItemPosition ?: 0
        ) { ContrastMode.COUNTER_MOTION }
        return MelodicRelationConfig(enabled = enabled, contrastDepth = depth, mode = mode)
    }

    private fun readChordConfig(panel: LinearLayout, prefix: String): ChordConfig {
        fun sp(tag: String)  = panel.findViewWithTag<Spinner>(tag)?.selectedItemPosition ?: 0
        fun sk(tag: String)  = panel.findViewWithTag<SeekBar>(tag)?.progress ?: 0
        return ChordConfig(
            chordType          = sp("${prefix}ChordType"),
            chordBuildStrategy = ChordBuildStrategy.entries.getOrElse(sp("${prefix}BuildStrategy")) { ChordBuildStrategy.STACK_THIRDS },
            tensionLevel       = TensionLevel.entries.getOrElse(sp("${prefix}TensionLevel")) { TensionLevel.NONE },
            inversionMode      = InversionMode.entries.getOrElse(sp("${prefix}Inversion")) { InversionMode.ROOT },
            voicingDensity     = VoicingDensity.entries.getOrElse(sp("${prefix}VoicingDensity")) { VoicingDensity.FULL },
            pluckingStyle      = sp("${prefix}PluckStyle"),
            pluckingDelayMs    = (sk("${prefix}PluckDelay") + 10).toLong(),
            strumLength        = sk("${prefix}StrumLen") + 1,
            noteDropChance     = sk("${prefix}NoteDrop") / 100f,
            mutationChance     = sk("${prefix}Mutation") / 100f,
            rhythmicFigure     = RhythmicFigure.entries.getOrElse(sp("${prefix}RhythmicFigure")) { RhythmicFigure.STRAIGHT }
        )
    }

    /** Builds the full chord configuration sub-panel. */
    private fun buildChordsSubPanel(ctx: Context, prefix: String, onSync: () -> Unit): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        val header = TextView(ctx).apply {
            text = "CHORD SETTINGS"; textSize = 11f
            setTextColor(0xFF4F9AA5.toInt()); letterSpacing = 0.12f
            setPadding(0, 4, 0, 8)
        }
        panel.addView(header)
        panel.addView(spinnerRow(ctx, "Chord Type",
            listOf("Triad","Seventh","Ninth","Suspended","Power","Diminished","Augmented","Add9"),
            "${prefix}ChordType", onSync))
        panel.addView(spinnerRow(ctx, "Build Strategy",
            listOf("Stack Thirds","Drop 2","Drop 3","Close","Spread","Quartal"),
            "${prefix}BuildStrategy", onSync))
        panel.addView(spinnerRow(ctx, "Tension",
            listOf("None","Low","Medium","High","Max"),
            "${prefix}TensionLevel", onSync))
        panel.addView(spinnerRow(ctx, "Inversion",
            listOf("Root","First","Second","Third","Auto"),
            "${prefix}Inversion", onSync))
        panel.addView(spinnerRow(ctx, "Voicing Density",
            listOf("Full","Sparse","Shell","Open"),
            "${prefix}VoicingDensity", onSync))
        panel.addView(spinnerRow(ctx, "Pluck Style",
            listOf("Simultaneous","Up","Down","Random","Outside-In","Inside-Out"),
            "${prefix}PluckStyle", onSync))
        panel.addView(labeledSeekBar(ctx, "Pluck Delay",  0, 190, 20,  "${prefix}PluckDelay",  onSync))
        panel.addView(labeledSeekBar(ctx, "Strum Length", 0,   5,  2,  "${prefix}StrumLen",    onSync))
        panel.addView(labeledSeekBar(ctx, "Note Drop %",  0,  50,  0,  "${prefix}NoteDrop",    onSync))
        panel.addView(labeledSeekBar(ctx, "Mutation %",   0,  30,  0,  "${prefix}Mutation",    onSync))
        panel.addView(spinnerRow(ctx, "Rhythmic Figure",
            listOf("Straight","Swing","Dotted","Triplet","Syncopated","Polyrhythm"),
            "${prefix}RhythmicFigure", onSync))
        return panel
    }

    private fun proFragContainerId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3

    private fun proFragContainerMelodicId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerMelodicV2 else R.id.proFragContainerMelodicV3

    private fun radioButton(ctx: Context, text: String, checked: Boolean) =
        RadioButton(ctx).apply {
            this.text = text; isChecked = checked
            setTextColor(0xFFE8E6E1.toInt()); textSize = 13f
            buttonTintList = android.content.res.ColorStateList.valueOf(0xFF4F9AA5.toInt())
            setPadding(0, 0, 16, 0)
        }

    private fun labeledSeekBar(
        ctx: Context, label: String, min: Int, max: Int, default: Int,
        tag: String, onSync: (() -> Unit)?
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 4) }
        val tv = TextView(ctx).apply {
            text = "$label: $default"; textSize = 13f
            setTextColor(0xFFE8E6E1.toInt()); setPadding(0, 0, 0, 2)
        }
        val sb = SeekBar(ctx).apply {
            this.tag = tag; this.max = max - min; progress = default - min
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF4F9AA5.toInt())
            thumbTintList    = android.content.res.ColorStateList.valueOf(0xFF4F9AA5.toInt())
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                tv.text = "$label: ${p + min}"
                if (fromUser) onSync?.invoke()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        row.addView(tv)
        row.addView(sb)
        return row
    }

    private fun spinnerRow(
        ctx: Context, label: String, items: List<String>,
        tag: String, onSync: (() -> Unit)?
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 4) }
        val tv = TextView(ctx).apply {
            text = label; textSize = 13f
            setTextColor(0xFF797876.toInt()); minWidth = 160
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val sp = Spinner(ctx).apply {
            this.tag = tag
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setBackgroundColor(0xFF1C1B19.toInt())
        }
        if (onSync != null) {
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        }
        row.addView(tv)
        row.addView(sp)
        return row
    }

    private fun rangeSeekBar(
        ctx: Context, label: String, min: Int, max: Int,
        defaultMin: Int, defaultMax: Int, tag: String, onSync: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 4) }
        val tv = TextView(ctx).apply {
            text = "$label: $defaultMin – $defaultMax"; textSize = 13f
            setTextColor(0xFFE8E6E1.toInt()); setPadding(0, 0, 0, 2)
        }
        val rs = com.crystal.crystalrangeseekbar.widget.CrystalRangeSeekbar(ctx).apply {
            this.tag = tag
            setMinValue(min.toFloat()); setMaxValue(max.toFloat())
            setMinStartValue(defaultMin.toFloat()); setMaxStartValue(defaultMax.toFloat())
            setOnRangeSeekbarChangeListener { minVal, maxVal ->
                tv.text = "$label: ${minVal.toInt()} – ${maxVal.toInt()}"
                if (!isUpdatingFromSync) onSync()
            }
        }
        row.addView(tv)
        row.addView(rs)
        return row
    }

    private fun setChildrenEnabled(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        if (v is ViewGroup) for (i in 0 until v.childCount) setChildrenEnabled(v.getChildAt(i), enabled)
    }
}
