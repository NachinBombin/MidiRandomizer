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
 * FIX: When the Timing spinner selects "Euclidean", the corresponding
 * ProSettingsFragment is notified via notifyTimingMode(true) so euclideanEnabled
 * is forced ON immediately — no second toggle in the PRO panel required.
 * Switching away from Euclidean forces it back OFF.
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
        childFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        childFragmentManager.beginTransaction()
            .add(melodicContainerId, frag, tag)
            .commitAllowingStateLoss()
    }

    private fun moveProFragToIndependentContainer(voiceId: Int) {
        val tag = "proFrag_v$voiceId"
        val frag = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment ?: return
        val independentContainerId = proFragContainerId(voiceId)
        childFragmentManager.beginTransaction().remove(frag).commitAllowingStateLoss()
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

        val enableSwitch = Switch(ctx).apply {
            text = label
            isChecked = false
            textSize  = 18f
            setTextColor(0xFFE8E6E1.toInt())
            tag = "enable"
        }
        panel.addView(enableSwitch)

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
            .apply { tag = "independentPanel" }
        val melodicPanel     = buildMelodicPanel(voiceId) { syncConfigAction?.invoke() }
            .apply { tag = "melodicPanel" }

        panel.addView(harmonyPanel)
        panel.addView(independentPanel)
        panel.addView(melodicPanel)

        independentPanel.visibility = View.GONE
        melodicPanel.visibility     = View.GONE

        fun showPanel(mode: VoiceMode) {
            harmonyPanel.visibility     = if (mode == VoiceMode.HARMONY)      View.VISIBLE else View.GONE
            independentPanel.visibility = if (mode == VoiceMode.INDEPENDENT)  View.VISIBLE else View.GONE
            melodicPanel.visibility     = if (mode == VoiceMode.MELODIC)      View.VISIBLE else View.GONE
            when (mode) {
                VoiceMode.MELODIC -> moveProFragToMelodicContainer(voiceId)
                else              -> moveProFragToIndependentContainer(voiceId)
            }
        }

        harmonyBtn.setOnCheckedChangeListener { _, checked ->
            if (checked && !isUpdatingFromSync) { showPanel(VoiceMode.HARMONY); syncConfigAction?.invoke() }
        }
        independentBtn.setOnCheckedChangeListener { _, checked ->
            if (checked && !isUpdatingFromSync) { showPanel(VoiceMode.INDEPENDENT); syncConfigAction?.invoke() }
        }
        melodicBtn.setOnCheckedChangeListener { _, checked ->
            if (checked && !isUpdatingFromSync) { showPanel(VoiceMode.MELODIC); syncConfigAction?.invoke() }
        }

        enableSwitch.setOnCheckedChangeListener { _, _ ->
            if (!isUpdatingFromSync) syncConfigAction?.invoke()
        }

        syncConfigAction = { pushConfigToService(voiceId) }

        return panel
    }

    // ── Harmony panel ─────────────────────────────────────────────────────

    private fun buildHarmonyPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionLabel(ctx, "Harmony Settings"))
            addView(spinnerRow(ctx, "Interval", listOf(
                "Unison","Minor 2nd","Major 2nd","Minor 3rd","Major 3rd","Perfect 4th",
                "Tritone","Perfect 5th","Minor 6th","Major 6th","Minor 7th","Major 7th","Octave"
            ), "harmInterval", onSync))
            addView(sliderRow(ctx, "Drift Amount", "harmDrift", 0, 12, onSync))
            addView(sliderRow(ctx, "Offset Semitones", "harmOffset", 0, 24, onSync))
            addView(spinnerRow(ctx, "Inversion", listOf("Root","1st","2nd","3rd"), "harmInversion", onSync))
        }
    }

    // ── Independent panel ─────────────────────────────────────────────────

    private fun buildIndependentPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        panel.addView(sectionLabel(ctx, "Independent Settings"))

        // Timing spinner — wired to notifyTimingMode on the Pro fragment
        val timingSpinner = Spinner(ctx).apply { tag = "indTiming" }
        ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome","Mixed","Randomized","Euclidean")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            timingSpinner.adapter = it
        }
        timingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val frag = if (voiceId == 2) proFragV2 else proFragV3
                frag?.notifyTimingMode(pos == MidiService.TIMING_EUCLIDEAN)
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        panel.addView(labelledRow(ctx, "Timing", timingSpinner))

        panel.addView(spinnerRow(ctx, "Style",
            listOf("Generative","Drone","Chords"), "indStyle", onSync))
        panel.addView(sliderRow(ctx, "Note Range", "indRange", 12, 48, onSync))
        panel.addView(sliderRow(ctx, "Density", "indDensity", 0, 100, onSync))
        panel.addView(sliderRow(ctx, "Velocity", "indVelocity", 0, 127, onSync))

        // Chord sub-panel
        val chordPanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            tag = "chordPanel"
            visibility = View.GONE
        }
        chordPanel.addView(spinnerRow(ctx, "Chord Type",
            listOf("Triad","Seventh","Ninth","Sus2","Sus4","Power"),
            "chordType", onSync))
        chordPanel.addView(spinnerRow(ctx, "Build Strategy",
            listOf("Diatonic Stack","Modal Snap"), "chordBuild", onSync))
        chordPanel.addView(spinnerRow(ctx, "Tension",
            listOf("Triad","Seventh","Ninth","Eleventh/Thirteenth"), "chordTension", onSync))
        chordPanel.addView(spinnerRow(ctx, "Inversion",
            listOf("Root","1st","2nd","Auto"), "chordInversion", onSync))
        chordPanel.addView(spinnerRow(ctx, "Voicing Density",
            listOf("Full","Drop 5th","Shell","Drop Root"), "chordVoicingDensity", onSync))
        chordPanel.addView(spinnerRow(ctx, "Plucking Style",
            listOf("Simultaneous","Asc","Desc","Random","Percussive Up"), "chordPlucking", onSync))
        chordPanel.addView(sliderRow(ctx, "Pluck Delay ms", "chordPluckDelay", 0, 200, onSync))
        chordPanel.addView(sliderRow(ctx, "Strum Length", "chordStrumLength", 1, 6, onSync))
        chordPanel.addView(sliderRow(ctx, "Note Drop %", "chordNoteDrop", 0, 100, onSync))
        chordPanel.addView(sliderRow(ctx, "Mutation %", "chordMutation", 0, 100, onSync))
        chordPanel.addView(spinnerRow(ctx, "Rhythmic Figure",
            listOf("Sustained","Reattack","Broken","Ostinato"),
            "chordRhythm", onSync))
        panel.addView(chordPanel)

        // Show/hide chord panel based on style spinner
        panel.findViewWithTag<Spinner>("indStyle")
            ?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                chordPanel.visibility = if (pos == 2) View.VISIBLE else View.GONE
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }

        // PRO settings container (Independent)
        val proContainer = FrameLayout(ctx).apply {
            id = if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        panel.addView(proContainer)

        return panel
    }

    // ── Melodic panel ─────────────────────────────────────────────────────

    private fun buildMelodicPanel(voiceId: Int, onSync: () -> Unit): LinearLayout {
        val ctx = requireContext()
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        panel.addView(sectionLabel(ctx, "Melodic Settings"))

        // Timing spinner — wired to notifyTimingMode on the Pro fragment
        val timingSpinner = Spinner(ctx).apply { tag = "melTiming" }
        ArrayAdapter(ctx, android.R.layout.simple_spinner_item,
            listOf("Metronome","Mixed","Randomized","Euclidean")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            timingSpinner.adapter = it
        }
        timingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val frag = if (voiceId == 2) proFragV2 else proFragV3
                frag?.notifyTimingMode(pos == MidiService.TIMING_EUCLIDEAN)
                if (!isUpdatingFromSync) onSync()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        panel.addView(labelledRow(ctx, "Timing", timingSpinner))

        panel.addView(sliderRow(ctx, "Note Range", "melRange", 12, 48, onSync))
        panel.addView(sliderRow(ctx, "Density", "melDensity", 0, 100, onSync))
        panel.addView(sliderRow(ctx, "Velocity", "melVelocity", 0, 127, onSync))
        panel.addView(sliderRow(ctx, "Contrast Depth", "melContrastDepth", 0, 100, onSync))
        panel.addView(spinnerRow(ctx, "Contrast Mode",
            listOf("Counter-Motion","Rhythmic Complement","Register Contrast","Chord-Aware"),
            "melContrastMode", onSync))

        // PRO settings container (Melodic — separate ID to avoid duplicate crash)
        val proContainer = FrameLayout(ctx).apply {
            id = if (voiceId == 2) R.id.proFragContainerMelodicV2 else R.id.proFragContainerMelodicV3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        panel.addView(proContainer)

        return panel
    }

    // ── Push config to service ────────────────────────────────────────────

    private fun pushConfigToService(voiceId: Int) {
        val panel   = if (voiceId == 2) panelV2 else panelV3
        val current = if (voiceId == 2) currentV2 else currentV3

        val enabled = panel.findViewWithTag<Switch>("enable")?.isChecked ?: false
        val harmony = panel.findViewWithTag<RadioButton>("rbHarmony")?.isChecked ?: true
        val melodic = panel.findViewWithTag<RadioButton>("rbMelodic")?.isChecked ?: false

        val mode = when {
            harmony -> VoiceMode.HARMONY
            melodic -> VoiceMode.MELODIC
            else    -> VoiceMode.INDEPENDENT
        }

        // ── Harmony config ────────────────────────────────────────────────
        val harmInterval  = panel.findViewWithTag<Spinner>("harmInterval")?.selectedItemPosition ?: 0
        val harmDrift     = panel.findViewWithTag<SeekBar>("harmDrift")?.progress ?: 0
        val harmOffset    = panel.findViewWithTag<SeekBar>("harmOffset")?.progress ?: 0
        val harmInversion = panel.findViewWithTag<Spinner>("harmInversion")?.selectedItemPosition ?: 0

        val newHarmonyConfig = current.harmonyConfig.copy(
            toneStepOffset = harmInterval,
            timeDriftMs    = harmDrift.toLong(),
            masterVelocity = harmOffset,
            velocityDrift  = harmInversion
        )

        // ── Independent / Melodic timing ──────────────────────────────────
        val timingMode = when (mode) {
            VoiceMode.MELODIC     -> panel.findViewWithTag<Spinner>("melTiming")?.selectedItemPosition ?: 0
            VoiceMode.INDEPENDENT -> panel.findViewWithTag<Spinner>("indTiming")?.selectedItemPosition ?: 0
            else                  -> current.independentConfig.timingMode
        }

        val prefix    = if (mode == VoiceMode.MELODIC) "mel" else "ind"
        val noteRange = (panel.findViewWithTag<SeekBar>("${prefix}Range")?.progress ?: 12) + 12
        val velocity  = panel.findViewWithTag<SeekBar>("${prefix}Velocity")?.progress ?: 80

        val indStylePos = panel.findViewWithTag<Spinner>("indStyle")?.selectedItemPosition ?: 0
        val style = when (indStylePos) {
            1    -> VoiceStyle.EVOLVING_DRONE
            2    -> VoiceStyle.CHORDS
            else -> VoiceStyle.GENERATIVE
        }

        // ── Chord config ──────────────────────────────────────────────────
        val chordConfig = if (style == VoiceStyle.CHORDS) {
            current.independentConfig.chordConfig.copy(
                chordType          = panel.findViewWithTag<Spinner>("chordType")?.selectedItemPosition ?: 0,
                chordBuildStrategy = ChordBuildStrategy.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("chordBuild")?.selectedItemPosition ?: 0
                ) { ChordBuildStrategy.DIATONIC_STACK },
                tensionLevel       = TensionLevel.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("chordTension")?.selectedItemPosition ?: 0
                ) { TensionLevel.TRIAD },
                inversionMode      = InversionMode.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("chordInversion")?.selectedItemPosition ?: 0
                ) { InversionMode.ROOT },
                voicingDensity     = VoicingDensity.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("chordVoicingDensity")?.selectedItemPosition ?: 0
                ) { VoicingDensity.FULL },
                pluckingStyle      = panel.findViewWithTag<Spinner>("chordPlucking")?.selectedItemPosition ?: 0,
                pluckingDelayMs    = (panel.findViewWithTag<SeekBar>("chordPluckDelay")?.progress ?: 0).toLong(),
                strumLength        = (panel.findViewWithTag<SeekBar>("chordStrumLength")?.progress ?: 0) + 1,
                noteDropChance     = (panel.findViewWithTag<SeekBar>("chordNoteDrop")?.progress ?: 0) / 100f,
                mutationChance     = (panel.findViewWithTag<SeekBar>("chordMutation")?.progress ?: 0) / 100f,
                rhythmicFigure     = RhythmicFigure.entries.getOrElse(
                    panel.findViewWithTag<Spinner>("chordRhythm")?.selectedItemPosition ?: 0
                ) { RhythmicFigure.SUSTAINED }
            )
        } else {
            current.independentConfig.chordConfig
        }

        // ── ProSettings — euclidean driven entirely by timing spinner ─────
        val proSettings = (if (voiceId == 2) customProSettingsV2 else customProSettingsV3)
            .copy(euclideanEnabled = timingMode == MidiService.TIMING_EUCLIDEAN)

        val newIndependentConfig = current.independentConfig.copy(
            timingMode  = timingMode,
            proSettings = proSettings,
            velocity    = velocity,
            minOctave   = ((noteRange / 2) - 1).coerceIn(0, 8),
            maxOctave   = ((noteRange / 2) + 1).coerceIn(1, 9),
            style       = style,
            chordConfig = chordConfig
        )

        // ── Melodic relation config ───────────────────────────────────────
        val contrastDepth = panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress ?: 50
        val contrastMode  = MelodicRelationMode.entries.getOrElse(
            panel.findViewWithTag<Spinner>("melContrastMode")?.selectedItemPosition ?: 0
        ) { MelodicRelationMode.COUNTER_MOTION }

        val newMelodicRelationConfig = current.melodicRelationConfig.copy(
            contrastDepth = contrastDepth,
            mode          = contrastMode
        )

        val updated = current.copy(
            enabled               = enabled,
            mode                  = mode,
            harmonyConfig         = newHarmonyConfig,
            independentConfig     = newIndependentConfig,
            melodicRelationConfig = newMelodicRelationConfig
        )

        if (voiceId == 2) currentV2 = updated else currentV3 = updated

        serviceProvider?.getMidiService()?.let { svc ->
            if (voiceId == 2) svc.updateVoice2Config(updated) else svc.updateVoice3Config(updated)
        }
    }

    // ── Sync from service ─────────────────────────────────────────────────

    private fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            isUpdatingFromSync = true
            applyToPanel(panelV2, v2, 2)
            applyToPanel(panelV3, v3, 3)
            currentV2 = v2
            currentV3 = v3
            isUpdatingFromSync = false
        }
    }

    private fun applyToPanel(panel: LinearLayout, vc: VoiceConfig, voiceId: Int) {
        panel.findViewWithTag<Switch>("enable")?.isChecked = vc.enabled

        val rb = when (vc.mode) {
            VoiceMode.HARMONY     -> "rbHarmony"
            VoiceMode.INDEPENDENT -> "rbIndependent"
            VoiceMode.MELODIC     -> "rbMelodic"
        }
        panel.findViewWithTag<RadioButton>(rb)?.isChecked = true

        val hc = vc.harmonyConfig
        panel.findViewWithTag<Spinner>("harmInterval")?.setSelection(hc.toneStepOffset.coerceIn(0, 12))
        panel.findViewWithTag<SeekBar>("harmDrift")?.progress    = hc.timeDriftMs.toInt().coerceIn(0, 12)
        panel.findViewWithTag<SeekBar>("harmOffset")?.progress   = hc.masterVelocity.coerceIn(0, 24)
        panel.findViewWithTag<Spinner>("harmInversion")?.setSelection(hc.velocityDrift.coerceIn(0, 3))

        val ic = vc.independentConfig

        // Independent timing — drive ProSettings
        panel.findViewWithTag<Spinner>("indTiming")?.setSelection(ic.timingMode)
        if (vc.mode == VoiceMode.INDEPENDENT) {
            val frag = if (voiceId == 2) proFragV2 else proFragV3
            frag?.notifyTimingMode(ic.timingMode == MidiService.TIMING_EUCLIDEAN)
        }

        // Melodic timing — drive ProSettings
        panel.findViewWithTag<Spinner>("melTiming")?.setSelection(ic.timingMode)
        if (vc.mode == VoiceMode.MELODIC) {
            val frag = if (voiceId == 2) proFragV2 else proFragV3
            frag?.notifyTimingMode(ic.timingMode == MidiService.TIMING_EUCLIDEAN)
        }

        val noteRangeProgress = ((ic.maxOctave - ic.minOctave) * 12).coerceIn(0, 36)
        panel.findViewWithTag<SeekBar>("indRange")?.progress    = noteRangeProgress
        panel.findViewWithTag<SeekBar>("indDensity")?.progress  = 50
        panel.findViewWithTag<SeekBar>("indVelocity")?.progress = ic.velocity.coerceIn(0, 127)

        val stylePos = when (ic.style) {
            VoiceStyle.EVOLVING_DRONE, VoiceStyle.SINGLE_NOTE_DRONE -> 1
            VoiceStyle.CHORDS                                        -> 2
            else                                                     -> 0
        }
        panel.findViewWithTag<Spinner>("indStyle")?.setSelection(stylePos)

        val cc = ic.chordConfig
        panel.findViewWithTag<Spinner>("chordType")?.setSelection(cc.chordType)
        panel.findViewWithTag<Spinner>("chordBuild")?.setSelection(cc.chordBuildStrategy.ordinal)
        panel.findViewWithTag<Spinner>("chordTension")?.setSelection(cc.tensionLevel.ordinal)
        panel.findViewWithTag<Spinner>("chordInversion")?.setSelection(cc.inversionMode.ordinal)
        panel.findViewWithTag<Spinner>("chordVoicingDensity")?.setSelection(cc.voicingDensity.ordinal)
        panel.findViewWithTag<Spinner>("chordPlucking")?.setSelection(cc.pluckingStyle)
        panel.findViewWithTag<SeekBar>("chordPluckDelay")?.progress    = cc.pluckingDelayMs.toInt().coerceIn(0, 200)
        panel.findViewWithTag<SeekBar>("chordStrumLength")?.progress   = (cc.strumLength - 1).coerceIn(0, 5)
        panel.findViewWithTag<SeekBar>("chordNoteDrop")?.progress      = (cc.noteDropChance * 100).toInt()
        panel.findViewWithTag<SeekBar>("chordMutation")?.progress      = (cc.mutationChance * 100).toInt()
        panel.findViewWithTag<Spinner>("chordRhythm")?.setSelection(cc.rhythmicFigure.ordinal)

        panel.findViewWithTag<SeekBar>("melRange")?.progress    = noteRangeProgress
        panel.findViewWithTag<SeekBar>("melDensity")?.progress  = 50
        panel.findViewWithTag<SeekBar>("melVelocity")?.progress = ic.velocity.coerceIn(0, 127)

        val mrc = vc.melodicRelationConfig
        panel.findViewWithTag<SeekBar>("melContrastDepth")?.progress = mrc.contrastDepth
        panel.findViewWithTag<Spinner>("melContrastMode")?.setSelection(mrc.mode.ordinal)

        val frag = if (voiceId == 2) proFragV2 else proFragV3
        frag?.setInitialSettings(ic.proSettings)

        if (voiceId == 2) customProSettingsV2 = ic.proSettings
        else              customProSettingsV3 = ic.proSettings
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun divider() = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2
        ).also { it.setMargins(0, 24, 0, 24) }
        setBackgroundColor(0x33FFFFFF)
    }

    private fun sectionLabel(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize  = 16f
        setTextColor(0xFF9E9B94.toInt())
        setPadding(0, 20, 0, 8)
    }

    private fun radioButton(ctx: Context, text: String, checked: Boolean) =
        RadioButton(ctx).apply {
            this.text = text
            isChecked = checked
            setTextColor(0xFFE8E6E1.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    private fun labelledRow(ctx: Context, label: String, view: View): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
            addView(TextView(ctx).apply {
                text = label
                setTextColor(0xFFE8E6E1.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(view.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun spinnerRow(
        ctx: Context, label: String, items: List<String>, tag: String, onSync: () -> Unit
    ): LinearLayout {
        val spinner = Spinner(ctx).apply {
            this.tag = tag
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (!isUpdatingFromSync) onSync()
                }
                override fun onNothingSelected(a: AdapterView<*>?) {}
            }
        }
        return labelledRow(ctx, label, spinner)
    }

    private fun sliderRow(
        ctx: Context, label: String, tag: String, min: Int, max: Int, onSync: () -> Unit
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = label
                setTextColor(0xFFE8E6E1.toInt())
                setPadding(0, 8, 0, 0)
            })
            addView(SeekBar(ctx).apply {
                this.tag = tag
                this.max = max - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) onSync()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }
    }
}
