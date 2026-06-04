package com.nachinbombin.midirandomizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

/**
 * Pro Settings fragment — same bg token as the Main window.
 */
class ProSettingsFragment : Fragment() {

    interface ProSettingsListener {
        fun onProSettingsChanged(settings: ProSettings)
    }

    private var listener: ProSettingsListener? = null
    private var current = ProSettings()

    private lateinit var seekJitter:       SeekBar
    private lateinit var tvJitter:         TextView
    private lateinit var spinnerJitterType: Spinner
    private lateinit var spinnerVelPattern: Spinner
    private lateinit var switchEuclidean:  Switch
    private lateinit var layoutEuclidean:  View
    private lateinit var seekEucSteps:     SeekBar
    private lateinit var tvEucSteps:       TextView
    private lateinit var seekEucDensity:   SeekBar
    private lateinit var tvEucDensity:     TextView
    private lateinit var seekEucRotation:  SeekBar
    private lateinit var tvEucRotation:    TextView
    private lateinit var switchMarkov:     Switch
    private lateinit var layoutMarkov:     View
    private lateinit var spinnerLogicStyle: Spinner
    private lateinit var spinnerPreset:    Spinner

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        listener = context as? ProSettingsListener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_pro_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        populateSpinners()
        restoreState()
        setupListeners()
        // Apply current theme — Pro uses the main bg token (forVoices = false)
        ThemeManager.applyToView(view, ThemeManager.loadTheme(requireContext()), forVoices = false)
    }

    fun setInitialSettings(s: ProSettings) { current = s }

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
        val jitterTypes = listOf("Uniform", "Gaussian", "Exponential", "Swing")
        spinnerJitterType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, jitterTypes)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val velPatterns = listOf("Flat", "Accent Beat 1", "Accent Beats 1&3", "Random Accent", "Crescendo", "Decrescendo", "Wave")
        spinnerVelPattern.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, velPatterns)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val logicStyles = listOf("None", "Markov Chain", "L-System", "Cellular Automata")
        spinnerLogicStyle.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, logicStyles)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val presets = listOf("None", "Ambient Pad", "Melodic Run", "Bass Drone", "Random Walk", "Pentatonic Flow")
        spinnerPreset.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presets)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun restoreState() {
        seekJitter.progress = current.jitterPercent
        tvJitter.text       = getString(R.string.label_jitter_amount, current.jitterPercent)
        spinnerJitterType.setSelection(current.jitterType)
        spinnerVelPattern.setSelection(current.velocityPattern)
        switchEuclidean.isChecked = current.euclideanEnabled
        layoutEuclidean.visibility = if (current.euclideanEnabled) View.VISIBLE else View.GONE
        seekEucSteps.progress   = current.eucSteps - 1
        tvEucSteps.text         = "Steps: ${current.eucSteps}"
        seekEucDensity.progress = current.eucDensity - 1
        tvEucDensity.text       = "Density: ${current.eucDensity}"
        seekEucRotation.progress = current.eucRotation
        tvEucRotation.text      = "Rotation: ${current.eucRotation}"
        switchMarkov.isChecked  = current.markovEnabled
        layoutMarkov.visibility = if (current.markovEnabled) View.VISIBLE else View.GONE
        spinnerLogicStyle.setSelection(current.logicStyle)
        spinnerPreset.setSelection(current.presetIndex)
    }

    private fun setupListeners() {
        seekJitter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(jitterPercent = p)
            tvJitter.text = getString(R.string.label_jitter_amount, p)
            push()
        })
        spinnerJitterType.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(jitterType = pos); push()
        }
        spinnerVelPattern.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(velocityPattern = pos); push()
        }
        switchEuclidean.setOnCheckedChangeListener { _, on ->
            current = current.copy(euclideanEnabled = on)
            layoutEuclidean.visibility = if (on) View.VISIBLE else View.GONE
            push()
        }
        seekEucSteps.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(eucSteps = p + 1)
            tvEucSteps.text = "Steps: ${p + 1}"; push()
        })
        seekEucDensity.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(eucDensity = p + 1)
            tvEucDensity.text = "Density: ${p + 1}"; push()
        })
        seekEucRotation.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(eucRotation = p)
            tvEucRotation.text = "Rotation: $p"; push()
        })
        switchMarkov.setOnCheckedChangeListener { _, on ->
            current = current.copy(markovEnabled = on)
            layoutMarkov.visibility = if (on) View.VISIBLE else View.GONE
            push()
        }
        spinnerLogicStyle.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(logicStyle = pos); push()
        }
        spinnerPreset.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(presetIndex = pos); push()
        }
    }

    private fun push() { listener?.onProSettingsChanged(current) }

    private fun simpleSeek(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) block(p) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
    private fun simpleSpinner(block: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) { block(pos) }
        override fun onNothingSelected(p: android.widget.AdapterView<*>) {}
    }
}
