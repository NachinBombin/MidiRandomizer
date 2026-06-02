package com.nachinbombin.midirandomizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * Pro Settings bottom sheet / fragment.
 *
 * Hosts all Tier 1–3 controls and calls back to the host Activity
 * whenever settings change via [ProSettingsListener].
 */
class ProSettingsFragment : Fragment() {

    interface ProSettingsListener {
        fun onProSettingsChanged(settings: ProSettings)
    }

    private var listener: ProSettingsListener? = null

    // Current state (pre-populated from host)
    private var current = ProSettings()

    // ── Views ─────────────────────────────────────────────────────────────────
    // Tier 1
    private lateinit var seekJitter:       SeekBar
    private lateinit var tvJitter:         TextView
    private lateinit var spinnerJitterType: Spinner
    private lateinit var spinnerVelPattern: Spinner

    // Tier 2 – Euclidean
    private lateinit var switchEuclidean:  Switch
    private lateinit var layoutEuclidean:  View
    private lateinit var seekEucSteps:     SeekBar
    private lateinit var tvEucSteps:       TextView
    private lateinit var seekEucDensity:   SeekBar
    private lateinit var tvEucDensity:     TextView
    private lateinit var seekEucRotation:  SeekBar
    private lateinit var tvEucRotation:    TextView

    // Tier 2 – Markov
    private lateinit var switchMarkov:     Switch
    private lateinit var layoutMarkov:     View
    private lateinit var spinnerLogicStyle: Spinner

    // Tier 3 – Presets
    private lateinit var spinnerPreset:    Spinner

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_pro_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        populateSpinners()
        restoreState()
        setupListeners()
    }

    fun setListener(l: ProSettingsListener) { listener = l }
    fun setInitialSettings(s: ProSettings) { current = s }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        seekJitter        = v.findViewById(R.id.seekJitter)
        tvJitter          = v.findViewById(R.id.tvJitterAmount)
        spinnerJitterType = v.findViewById(R.id.spinnerJitterType)
        spinnerVelPattern = v.findViewById(R.id.spinnerVelPattern)

        switchEuclidean   = v.findViewById(R.id.switchEuclidean)
        layoutEuclidean   = v.findViewById(R.id.layoutEuclidean)
        seekEucSteps      = v.findViewById(R.id.seekEucSteps)
        tvEucSteps        = v.findViewById(R.id.tvEucSteps)
        seekEucDensity    = v.findViewById(R.id.seekEucDensity)
        tvEucDensity      = v.findViewById(R.id.tvEucDensity)
        seekEucRotation   = v.findViewById(R.id.seekEucRotation)
        tvEucRotation     = v.findViewById(R.id.tvEucRotation)

        switchMarkov      = v.findViewById(R.id.switchMarkov)
        layoutMarkov      = v.findViewById(R.id.layoutMarkov)
        spinnerLogicStyle = v.findViewById(R.id.spinnerLogicStyle)

        spinnerPreset     = v.findViewById(R.id.spinnerPreset)
    }

    private fun populateSpinners() {
        spinnerJitterType.adapter = simpleAdapter(JitterType.values().map { it.label })
        spinnerVelPattern.adapter = simpleAdapter(VelocityPattern.values().map { it.label })
        spinnerLogicStyle.adapter = simpleAdapter(MelodicLogicStyle.values().map { it.label })
        spinnerPreset.adapter     = simpleAdapter(ProPreset.values().map { it.label })
    }

    private fun restoreState() {
        seekJitter.progress       = current.jitterAmount
        tvJitter.text             = jitterLabel(current.jitterAmount)
        spinnerJitterType.setSelection(current.jitterType.ordinal)
        spinnerVelPattern.setSelection(current.velocityPattern.ordinal)

        switchEuclidean.isChecked = current.euclideanEnabled
        layoutEuclidean.visibility = if (current.euclideanEnabled) View.VISIBLE else View.GONE
        seekEucSteps.max          = 30           // 2..32 displayed as 0..30 offset
        seekEucSteps.progress     = current.euclideanSteps - 2
        tvEucSteps.text           = eucStepsLabel(current.euclideanSteps)
        seekEucDensity.max        = (current.euclideanSteps - 1).coerceAtLeast(1)
        seekEucDensity.progress   = current.euclideanDensity - 1
        tvEucDensity.text         = eucDensityLabel(current.euclideanDensity, current.euclideanSteps)
        seekEucRotation.max       = current.euclideanSteps - 1
        seekEucRotation.progress  = current.euclideanRotation
        tvEucRotation.text        = eucRotLabel(current.euclideanRotation)

        switchMarkov.isChecked    = current.markovEnabled
        layoutMarkov.visibility   = if (current.markovEnabled) View.VISIBLE else View.GONE
        spinnerLogicStyle.setSelection(current.melodicLogicStyle.ordinal)

        spinnerPreset.setSelection(current.activePreset.ordinal)
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Jitter amount
        seekJitter.setOnSeekBarChangeListener(simpleSeekListener { p ->
            tvJitter.text = jitterLabel(p)
            notify()
        })

        // Jitter type
        spinnerJitterType.onItemSelectedListener = simpleSpinnerListener { notify() }

        // Velocity pattern
        spinnerVelPattern.onItemSelectedListener = simpleSpinnerListener { notify() }

        // Euclidean toggle
        switchEuclidean.setOnCheckedChangeListener { _, on ->
            layoutEuclidean.visibility = if (on) View.VISIBLE else View.GONE
            notify()
        }

        // Euclidean steps
        seekEucSteps.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val steps = p + 2
            tvEucSteps.text = eucStepsLabel(steps)
            // Clamp density
            if (seekEucDensity.progress >= steps) seekEucDensity.progress = steps - 1
            seekEucDensity.max = steps - 1
            seekEucRotation.max = steps - 1
            notify()
        })

        // Euclidean density
        seekEucDensity.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val steps = seekEucSteps.progress + 2
            tvEucDensity.text = eucDensityLabel(p + 1, steps)
            notify()
        })

        // Euclidean rotation
        seekEucRotation.setOnSeekBarChangeListener(simpleSeekListener { p ->
            tvEucRotation.text = eucRotLabel(p)
            notify()
        })

        // Markov toggle
        switchMarkov.setOnCheckedChangeListener { _, on ->
            layoutMarkov.visibility = if (on) View.VISIBLE else View.GONE
            notify()
        }

        // Logic style
        spinnerLogicStyle.onItemSelectedListener = simpleSpinnerListener { notify() }

        // Preset
        spinnerPreset.onItemSelectedListener = simpleSpinnerListener { applyPreset() }
    }

    // ── Preset logic ──────────────────────────────────────────────────────────

    private fun applyPreset() {
        val preset = ProPreset.values().getOrNull(spinnerPreset.selectedItemPosition)
            ?: ProPreset.NONE
        when (preset) {
            ProPreset.AMBIENT_TEXTURE -> {
                // Slow Euclidean, low density, Markov stepwise, soft velocity
                switchEuclidean.isChecked = true
                seekEucSteps.progress     = 14           // 16 steps
                seekEucDensity.progress   = 2            // 3 pulses
                seekEucRotation.progress  = 0
                switchMarkov.isChecked    = true
                spinnerLogicStyle.setSelection(MelodicLogicStyle.STEPWISE.ordinal)
                seekJitter.progress       = 25
                spinnerJitterType.setSelection(JitterType.GAUSSIAN.ordinal)
                spinnerVelPattern.setSelection(VelocityPattern.PEAK_CENTER.ordinal)
            }
            ProPreset.EXPERIMENTAL_SOUNDSCAPE -> {
                // High jitter, wide leaps, accent beats
                switchEuclidean.isChecked = false
                switchMarkov.isChecked    = true
                spinnerLogicStyle.setSelection(MelodicLogicStyle.WIDE_LEAPS.ordinal)
                seekJitter.progress       = 70
                spinnerJitterType.setSelection(JitterType.EXPONENTIAL.ordinal)
                spinnerVelPattern.setSelection(VelocityPattern.ACCENT_BEATS.ordinal)
            }
            ProPreset.NONE -> { /* manual — don't override */ }
        }
        notify()
    }

    // ── Build & emit current settings ─────────────────────────────────────────

    private fun notify() {
        val settings = buildSettings()
        current = settings
        listener?.onProSettingsChanged(settings)
    }

    private fun buildSettings(): ProSettings {
        val steps    = seekEucSteps.progress + 2
        val density  = (seekEucDensity.progress + 1).coerceIn(1, steps)
        val rotation = seekEucRotation.progress
        return ProSettings(
            jitterAmount      = seekJitter.progress,
            jitterType        = JitterType.values().getOrElse(spinnerJitterType.selectedItemPosition) { JitterType.UNIFORM },
            velocityPattern   = VelocityPattern.values().getOrElse(spinnerVelPattern.selectedItemPosition) { VelocityPattern.RANDOM },
            euclideanEnabled  = switchEuclidean.isChecked,
            euclideanSteps    = steps,
            euclideanDensity  = density,
            euclideanRotation = rotation,
            markovEnabled     = switchMarkov.isChecked,
            melodicLogicStyle = MelodicLogicStyle.values().getOrElse(spinnerLogicStyle.selectedItemPosition) { MelodicLogicStyle.STEPWISE },
            activePreset      = ProPreset.values().getOrElse(spinnerPreset.selectedItemPosition) { ProPreset.NONE }
        )
    }

    // ── Adapter / listener helpers ────────────────────────────────────────────

    private fun simpleAdapter(items: List<String>) =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun simpleSeekListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { onChange(p) }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        }

    private fun simpleSpinnerListener(onSelect: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

    // ── Label helpers ─────────────────────────────────────────────────────────
    private fun jitterLabel(v: Int)             = "Jitter: $v %"
    private fun eucStepsLabel(s: Int)           = "Steps: $s"
    private fun eucDensityLabel(d: Int, s: Int) = "Density: $d / $s pulses"
    private fun eucRotLabel(r: Int)             = "Rotation: $r"
}
