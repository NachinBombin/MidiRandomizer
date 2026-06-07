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

    // ── Timing / Rhythm ───────────────────────────────────────────────────
    private lateinit var seekJitter:        SeekBar
    private lateinit var tvJitter:          TextView
    private lateinit var spinnerJitterType: Spinner
    private lateinit var spinnerVelPattern: Spinner

    // ── Melodic Engine ────────────────────────────────────────────────────
    private lateinit var spinnerMelodicEngine: Spinner

    // ── Markov panel ─────────────────────────────────────────────────────
    private lateinit var layoutMarkov:       View
    private lateinit var spinnerLogicStyle:  Spinner
    private lateinit var switchSecondOrder:  Switch
    // Narmour
    private lateinit var switchNarmour:      Switch
    private lateinit var layoutNarmour:      View
    private lateinit var seekNarmourProcess: SeekBar
    private lateinit var tvNarmourProcess:   TextView
    private lateinit var seekNarmourReturn:  SeekBar
    private lateinit var tvNarmourReturn:    TextView
    private lateinit var seekNarmourLeap:    SeekBar
    private lateinit var tvNarmourLeap:      TextView
    // Contour Gravity
    private lateinit var switchGravity:          Switch
    private lateinit var layoutGravity:          View
    private lateinit var seekGravityThreshold:   SeekBar
    private lateinit var tvGravityThreshold:     TextView
    private lateinit var seekGravityStrength:    SeekBar
    private lateinit var tvGravityStrength:      TextView
    // Gesture Curves
    private lateinit var switchGesture:          Switch
    private lateinit var layoutGesture:          View
    private lateinit var seekGestureDepth:       SeekBar
    private lateinit var tvGestureDepth:         TextView
    private lateinit var spinnerGesturePitch:    Spinner
    private lateinit var spinnerGestureRegister: Spinner
    private lateinit var spinnerGestureDensity:  Spinner
    private lateinit var spinnerGestureVelocity: Spinner

    // ── NRT Melodic panel ────────────────────────────────────────────────
    private lateinit var layoutNrtMelodic: View
    private lateinit var spinnerNrtCycle:  Spinner
    private lateinit var seekNrtP:         SeekBar
    private lateinit var tvNrtPWeight:     TextView
    private lateinit var seekNrtL:         SeekBar
    private lateinit var tvNrtLWeight:     TextView
    private lateinit var seekNrtR:         SeekBar
    private lateinit var tvNrtRWeight:     TextView

    // ── PWG panel ────────────────────────────────────────────────────────
    private lateinit var layoutPwg:       View
    private lateinit var spinnerPwgMotif: Spinner
    private lateinit var seekPwgPhrase:   SeekBar
    private lateinit var tvPwgPhrase:     TextView
    private lateinit var seekPwgDir:      SeekBar
    private lateinit var tvPwgDirection:  TextView

    // ── L-System panel ───────────────────────────────────────────────────
    private lateinit var layoutLSystem:       View
    private lateinit var spinnerLSystemAxiom: Spinner
    private lateinit var seekLSystemIter:     SeekBar
    private lateinit var tvLSystemIter:       TextView
    private lateinit var seekLSystemVariance: SeekBar
    private lateinit var tvLSystemVariance:   TextView

    // ── Cell Automata panel ──────────────────────────────────────────────
    private lateinit var layoutCellAuto:  View
    private lateinit var seekCaSurvMin:   SeekBar
    private lateinit var tvCaSurvMin:     TextView
    private lateinit var seekCaSurvMax:   SeekBar
    private lateinit var tvCaSurvMax:     TextView
    private lateinit var seekCaBirth:     SeekBar
    private lateinit var tvCaBirth:       TextView
    private lateinit var seekCaMutation:  SeekBar
    private lateinit var tvCaMutation:    TextView

    // ── Lifecycle ────────────────────────────────────────────────────────

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

    /** Allow non-Activity hosts (e.g. child fragment in VoicesFragment) to receive callbacks. */
    fun setListener(cb: (ProSettings) -> Unit) {
        listener = object : ProSettingsListener {
            override fun onProSettingsChanged(settings: ProSettings) = cb(settings)
        }
    }

    // ── Bind ─────────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        seekJitter        = v.findViewById(R.id.seekJitter)
        tvJitter          = v.findViewById(R.id.tvJitterAmount)
        spinnerJitterType = v.findViewById(R.id.spinnerJitterType)
        spinnerVelPattern = v.findViewById(R.id.spinnerVelPattern)

        spinnerMelodicEngine = v.findViewById(R.id.spinnerMelodicEngine)

        layoutMarkov      = v.findViewById(R.id.layoutMarkov)
        spinnerLogicStyle = v.findViewById(R.id.spinnerLogicStyle)
        switchSecondOrder = v.findViewById(R.id.switchSecondOrder)
        switchNarmour     = v.findViewById(R.id.switchNarmour)
        layoutNarmour     = v.findViewById(R.id.layoutNarmour)
        seekNarmourProcess= v.findViewById(R.id.seekNarmourProcess)
        tvNarmourProcess  = v.findViewById(R.id.tvNarmourProcess)
        seekNarmourReturn = v.findViewById(R.id.seekNarmourReturn)
        tvNarmourReturn   = v.findViewById(R.id.tvNarmourReturn)
        seekNarmourLeap   = v.findViewById(R.id.seekNarmourLeap)
        tvNarmourLeap     = v.findViewById(R.id.tvNarmourLeap)
        switchGravity     = v.findViewById(R.id.switchGravity)
        layoutGravity     = v.findViewById(R.id.layoutGravity)
        seekGravityThreshold = v.findViewById(R.id.seekGravityThreshold)
        tvGravityThreshold   = v.findViewById(R.id.tvGravityThreshold)
        seekGravityStrength  = v.findViewById(R.id.seekGravityStrength)
        tvGravityStrength    = v.findViewById(R.id.tvGravityStrength)
        switchGesture        = v.findViewById(R.id.switchGesture)
        layoutGesture        = v.findViewById(R.id.layoutGesture)
        seekGestureDepth     = v.findViewById(R.id.seekGestureDepth)
        tvGestureDepth       = v.findViewById(R.id.tvGestureDepth)
        spinnerGesturePitch    = v.findViewById(R.id.spinnerGesturePitch)
        spinnerGestureRegister = v.findViewById(R.id.spinnerGestureRegister)
        spinnerGestureDensity  = v.findViewById(R.id.spinnerGestureDensity)
        spinnerGestureVelocity = v.findViewById(R.id.spinnerGestureVelocity)

        layoutNrtMelodic = v.findViewById(R.id.layoutNrtMelodic)
        spinnerNrtCycle  = v.findViewById(R.id.spinnerNrtCycle)
        seekNrtP         = v.findViewById(R.id.seekNrtP)
        tvNrtPWeight     = v.findViewById(R.id.tvNrtPWeight)
        seekNrtL         = v.findViewById(R.id.seekNrtL)
        tvNrtLWeight     = v.findViewById(R.id.tvNrtLWeight)
        seekNrtR         = v.findViewById(R.id.seekNrtR)
        tvNrtRWeight     = v.findViewById(R.id.tvNrtRWeight)

        layoutPwg       = v.findViewById(R.id.layoutPwg)
        spinnerPwgMotif = v.findViewById(R.id.spinnerPwgMotif)
        seekPwgPhrase   = v.findViewById(R.id.seekPwgPhrase)
        tvPwgPhrase     = v.findViewById(R.id.tvPwgPhrase)
        seekPwgDir      = v.findViewById(R.id.seekPwgDirection)
        tvPwgDirection  = v.findViewById(R.id.tvPwgDirection)

        layoutLSystem       = v.findViewById(R.id.layoutLSystem)
        spinnerLSystemAxiom = v.findViewById(R.id.spinnerLSystemAxiom)
        seekLSystemIter     = v.findViewById(R.id.seekLSystemIter)
        tvLSystemIter       = v.findViewById(R.id.tvLSystemIter)
        seekLSystemVariance = v.findViewById(R.id.seekLSystemVariance)
        tvLSystemVariance   = v.findViewById(R.id.tvLSystemVariance)

        layoutCellAuto = v.findViewById(R.id.layoutCellAuto)
        seekCaSurvMin  = v.findViewById(R.id.seekCaSurvMin)
        tvCaSurvMin    = v.findViewById(R.id.tvCaSurvMin)
        seekCaSurvMax  = v.findViewById(R.id.seekCaSurvMax)
        tvCaSurvMax    = v.findViewById(R.id.tvCaSurvMax)
        seekCaBirth    = v.findViewById(R.id.seekCaBirth)
        tvCaBirth      = v.findViewById(R.id.tvCaBirth)
        seekCaMutation = v.findViewById(R.id.seekCaMutation)
        tvCaMutation   = v.findViewById(R.id.tvCaMutation)
    }

    // ── Spinners ─────────────────────────────────────────────────────────

    private fun populateSpinners() {
        fun arr(vararg items: String) = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, items.toList()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerJitterType.adapter    = arr(*JitterType.entries.map { it.name }.toTypedArray())
        spinnerVelPattern.adapter    = arr(*VelocityPattern.entries.map { it.name }.toTypedArray())
        spinnerMelodicEngine.adapter = arr(*MelodicEngine.entries.map { it.name }.toTypedArray())
        spinnerLogicStyle.adapter    = arr(*MelodicLogicStyle.entries.map { it.name }.toTypedArray())

        val curvePres = arrayOf("Flat","Rising arch","Falling arch","Sawtooth","Reverse sawtooth","S-curve")
        spinnerGesturePitch.adapter    = arr(*curvePres)
        spinnerGestureRegister.adapter = arr(*curvePres)
        spinnerGestureDensity.adapter  = arr(*curvePres)
        spinnerGestureVelocity.adapter = arr(*curvePres)

        spinnerNrtCycle.adapter = arr("Random PLR","Hexatonic (LPPL)","Octatonic (PRRP)","Wagner (LRRL)","Flat (no cycle)")
        spinnerPwgMotif.adapter = arr("Step","Arch","Wave","Zigzag","Custom")
        spinnerLSystemAxiom.adapter = arr("U","D","UDU","DUD","UUDD")
    }

    // ── State ─────────────────────────────────────────────────────────────

    private fun rest