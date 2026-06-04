package com.nachinbombin.midirandomizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class ProSettingsFragment : Fragment() {

    interface ProSettingsListener {
        fun onProSettingsChanged(settings: ProSettings)
    }

    private var listener: ProSettingsListener? = null
    private var current = ProSettings()

    private lateinit var seekJitter:        SeekBar
    private lateinit var tvJitter:          TextView
    private lateinit var spinnerJitterType: Spinner
    private lateinit var spinnerVelPattern: Spinner
    private lateinit var switchEuclidean:   Switch
    private lateinit var layoutEuclidean:   View
    private lateinit var seekEucSteps:      SeekBar
    private lateinit var tvEucSteps:        TextView
    private lateinit var seekEucDensity:    SeekBar
    private lateinit var tvEucDensity:      TextView
    private lateinit var seekEucRotation:   SeekBar
    private lateinit var tvEucRotation:     TextView
    private lateinit var switchMarkov:      Switch
    private lateinit var layoutMarkov:      View
    private lateinit var spinnerLogicStyle: Spinner

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
        ThemeManager.applyToView(view, ThemeManager.loadTheme(requireContext()), forVoices = false)
    }

    override fun onResume() {
        super.onResume()
        view?.let { ThemeManager.applyToView(it, ThemeManager.loadTheme(requireContext()), forVoices = false) }
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
        // spinnerPreset removed — ProPreset enum does not exist; preset logic is deferred
    }

    private fun enumDisplayName(name: String): String =
        name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun populateSpinners() {
        spinnerJitterType.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            JitterType.entries.map { enumDisplayName(it.name) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerVelPattern.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            VelocityPattern.entries.map { enumDisplayName(it.name) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerLogicStyle.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            MelodicLogicStyle.entries.map { enumDisplayName(it.name) }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun restoreState() {
        seekJitter.progress    = current.jitterAmount
        tvJitter.text          = getString(R.string.label_jitter_amount, current.jitterAmount)
        spinnerJitterType.setSelection(current.jitterType.ordinal)
        spinnerVelPattern.setSelection(current.velocityPattern.ordinal)
        switchEuclidean.isChecked  = current.euclideanEnabled
        layoutEuclidean.visibility = if (current.euclideanEnabled) View.VISIBLE else View.GONE
        seekEucSteps.progress   = current.euclideanSteps - 1
        tvEucSteps.text         = "Steps: ${current.euclideanSteps}"
        seekEucDensity.progress = current.euclideanDensity - 1
        tvEucDensity.text       = "Density: ${current.euclideanDensity}"
        seekEucRotation.progress = current.euclideanRotation
        tvEucRotation.text      = "Rotation: ${current.euclideanRotation}"
        switchMarkov.isChecked  = current.markovEnabled
        layoutMarkov.visibility = if (current.markovEnabled) View.VISIBLE else View.GONE
        spinnerLogicStyle.setSelection(current.melodicLogicStyle.ordinal)
    }

    private fun setupListeners() {
        seekJitter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(jitterAmount = p)
            tvJitter.text = getString(R.string.label_jitter_amount, p)
            push()
        })
        spinnerJitterType.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(jitterType = JitterType.entries[pos]); push()
        }
        spinnerVelPattern.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(velocityPattern = VelocityPattern.entries[pos]); push()
        }
        switchEuclidean.setOnCheckedChangeListener { _, on ->
            current = current.copy(euclideanEnabled = on)
            layoutEuclidean.visibility = if (on) View.VISIBLE else View.GONE
            push()
        }
        seekEucSteps.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(euclideanSteps = p + 1)
            tvEucSteps.text = "Steps: ${p + 1}"; push()
        })
        seekEucDensity.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(euclideanDensity = p + 1)
            tvEucDensity.text = "Density: ${p + 1}"; push()
        })
        seekEucRotation.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(euclideanRotation = p)
            tvEucRotation.text = "Rotation: $p"; push()
        })
        switchMarkov.setOnCheckedChangeListener { _, on ->
            current = current.copy(markovEnabled = on)
            layoutMarkov.visibility = if (on) View.VISIBLE else View.GONE
            push()
        }
        spinnerLogicStyle.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(melodicLogicStyle = MelodicLogicStyle.entries[pos]); push()
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
