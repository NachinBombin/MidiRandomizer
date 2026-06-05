package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * VoicesFragment – manages V1/V2/V3 panels.
 *
 * V1 uses the shared Pro Settings screen (Activity-level ProSettingsListener).
 * V2 and V3 each embed a ProSettingsFragment child so all engine UIs
 * (Markov, Narmour, Gesture, NRT-Melodic, PWG, L-System, Cell-Automata)
 * are visible in their custom-pro panels.
 */
class VoicesFragment : Fragment() {

    // ── Per-voice independent config state ───────────────────────────────
    private var independentV2 = IndependentVoiceConfig()
    private var independentV3 = IndependentVoiceConfig()

    // Child ProSettingsFragments for V2 and V3
    private var proFragV2: ProSettingsFragment? = null
    private var proFragV3: ProSettingsFragment? = null
    private var customProSettingsV2: ProSettings = ProSettings()
    private var customProSettingsV3: ProSettings = ProSettings()

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollView = android.widget.ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val innerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(ctx, 12)
            setPadding(pad, pad, pad, pad)
        }

        innerLayout.addView(buildVoicePanel(ctx, voiceId = 2, config = independentV2))
        innerLayout.addView(buildDivider(ctx))
        innerLayout.addView(buildVoicePanel(ctx, voiceId = 3, config = independentV3))

        scrollView.addView(innerLayout)
        root.addView(scrollView)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Attach child ProSettingsFragments into their FrameLayout containers
        attachProFragment(voiceId = 2)
        attachProFragment(voiceId = 3)
    }

    // ── Child fragment management ─────────────────────────────────────────

    private fun proFragContainerId(voiceId: Int) =
        if (voiceId == 2) R.id.proFragContainerV2 else R.id.proFragContainerV3

    private fun attachProFragment(voiceId: Int) {
        val containerId = proFragContainerId(voiceId)
        val tag = "proFrag_v$voiceId"

        // Reuse if already added (config change)
        val existing = childFragmentManager.findFragmentByTag(tag) as? ProSettingsFragment
        val frag = existing ?: ProSettingsFragment()

        val initSettings = if (voiceId == 2) customProSettingsV2 else customProSettingsV3
        frag.setInitialSettings(initSettings)
        frag.setListener { settings ->
            if (voiceId == 2) customProSettingsV2 = settings
            else customProSettingsV3 = settings
            onIndependentProSettingsChanged(voiceId)
        }

        if (existing == null) {
            childFragmentManager.beginTransaction()
                .add(containerId, frag, tag)
                .commitAllowingStateLoss()
        }
        if (voiceId == 2) proFragV2 = frag else proFragV3 = frag
    }

    // ── Voice panel builder ──────────────────────────────────────────────

    private fun buildVoicePanel(ctx: Context, voiceId: Int, config: IndependentVoiceConfig): View {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(ctx, 8)
            setPadding(0, pad, 0, pad)
        }

        // Header label
        panel.addView(TextView(ctx).apply {
            text = "Voice $voiceId"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(ctx, 6))
        })

        // ── Independent toggle ──────────────────────────────────────────
        val switchIndependent = Switch(ctx).apply {
            text = "Independent settings"
            isChecked = config.isIndependent
        }
        panel.addView(switchIndependent)

        // Container that shows when independent is ON
        val independentContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (config.isIndependent) View.VISIBLE else View.GONE
        }

        // ── Basic settings (key / scale / octave / BPM overrides) ──────
        independentContainer.addView(buildBasicSettings(ctx, voiceId, config))

        // ── Custom Pro panel toggle ─────────────────────────────────────
        val switchCustomPro = Switch(ctx).apply {
            text = "Custom Pro settings"
            isChecked = config.useCustomProSettings
            setPadding(0, dpToPx(ctx, 8), 0, 0)
        }
        independentContainer.addView(switchCustomPro)

        // ── Custom Pro panel: embed ProSettingsFragment via FrameLayout ─
        // The FrameLayout has a stable resource ID so childFragmentManager can host it.
        val proFragContainer = android.widget.FrameLayout(ctx).apply {
            id = proFragContainerId(voiceId)
            visibility = if (config.isIndependent && config.useCustomProSettings)
                View.VISIBLE else View.GONE
        }
        independentContainer.addView(proFragContainer)

        panel.addView(independentContainer)

        // ── Wire switches ───────────────────────────────────────────────
        switchIndependent.setOnCheckedChangeListener { _, on ->
            independentContainer.visibility = if (on) View.VISIBLE else View.GONE
            updateConfig(voiceId) { it.copy(isIndependent = on) }
        }
        switchCustomPro.setOnCheckedChangeListener { _, on ->
            proFragContainer.visibility = if (on) View.VISIBLE else View.GONE
            updateConfig(voiceId) { it.copy(useCustomProSettings = on) }
        }

        return panel
    }

    // ── Basic independent settings (key, scale, octave) ──────────────────

    private fun buildBasicSettings(ctx: Context, voiceId: Int, config: IndependentVoiceConfig): View {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(ctx, 4)
            setPadding(0, pad, 0, pad)
        }

        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setPadding(0, dpToPx(ctx, 6), 0, 2)
        }

        fun spinner(entries: Array<String>, selected: Int, onChange: (Int) -> Unit): Spinner {
            val sp = Spinner(ctx)
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, entries).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            sp.setSelection(selected)
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onChange(pos)
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            return sp
        }

        // Key
        val keys = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        layout.addView(label("Root Key"))
        layout.addView(spinner(keys, config.rootKey) { pos ->
            updateConfig(voiceId) { it.copy(rootKey = pos) }
        })

        // Scale
        val scaleNames = ScaleType.entries.map { it.displayName }.toTypedArray()
        layout.addView(label("Scale"))
        layout.addView(spinner(scaleNames, config.scaleType.ordinal) { pos ->
            updateConfig(voiceId) { it.copy(scaleType = ScaleType.entries[pos]) }
        })

        // Octave range
        layout.addView(label("Octave (min)"))
        layout.addView(spinner(
            (0..7).map { "Oct $it" }.toTypedArray(),
            config.octaveMin
        ) { pos -> updateConfig(voiceId) { it.copy(octaveMin = pos) } })

        layout.addView(label("Octave (max)"))
        layout.addView(spinner(
            (0..7).map { "Oct $it" }.toTypedArray(),
            config.octaveMax
        ) { pos -> updateConfig(voiceId) { it.copy(octaveMax = pos) } })

        return layout
    }

    // ── Config mutation helpers ───────────────────────────────────────────

    private fun updateConfig(voiceId: Int, mutation: (IndependentVoiceConfig) -> IndependentVoiceConfig) {
        if (voiceId == 2) independentV2 = mutation(independentV2)
        else independentV3 = mutation(independentV3)
        notifyHost()
    }

    private fun onIndependentProSettingsChanged(voiceId: Int) {
        // ProSettings for this voice changed via child fragment — update the config wrapper and notify
        if (voiceId == 2) {
            independentV2 = independentV2.copy(proSettings = customProSettingsV2)
        } else {
            independentV3 = independentV3.copy(proSettings = customProSettingsV3)
        }
        notifyHost()
    }

    private fun notifyHost() {
        (activity as? VoicesListener)?.onVoiceConfigChanged(
            v2 = independentV2,
            v3 = independentV3
        )
    }

    // ── Public API (called by Activity to restore saved state) ────────────

    fun applyConfigs(v2: IndependentVoiceConfig, v3: IndependentVoiceConfig) {
        independentV2 = v2
        independentV3 = v3
        customProSettingsV2 = v2.proSettings
        customProSettingsV3 = v3.proSettings
        // If child fragments are already attached, push the restored settings into them
        proFragV2?.setInitialSettings(v2.proSettings)
        proFragV3?.setInitialSettings(v3.proSettings)
    }

    // ── Host interface ────────────────────────────────────────────────────

    interface VoicesListener {
        fun onVoiceConfigChanged(v2: IndependentVoiceConfig, v3: IndependentVoiceConfig)
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private fun buildDivider(ctx: Context): View = View(ctx).apply {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(ctx, 1)
        )
        lp.setMargins(0, dpToPx(ctx, 12), 0, dpToPx(ctx, 12))
        layoutParams = lp
        setBackgroundColor(0x1A000000)
    }

    private fun dpToPx(ctx: Context, dp: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt()
}
