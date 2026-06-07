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
        spinnerLogicStyle.adapter    = arr(*MarkovLogicStyle.entries.map { it.name }.toTypedArray())
        spinnerNrtCycle.adapter      = arr(*NrtCycle.entries.map { it.name }.toTypedArray())
        spinnerPwgMotif.adapter      = arr(*PwgMotif.entries.map { it.name }.toTypedArray())
        spinnerLSystemAxiom.adapter  = arr(*LSystemAxiom.entries.map { it.name }.toTypedArray())
        spinnerGesturePitch.adapter    = arr(*GesturePitchShape.entries.map { it.name }.toTypedArray())
        spinnerGestureRegister.adapter = arr(*GestureRegisterTendency.entries.map { it.name }.toTypedArray())
        spinnerGestureDensity.adapter  = arr(*GestureDensityProfile.entries.map { it.name }.toTypedArray())
        spinnerGestureVelocity.adapter = arr(*GestureVelocityProfile.entries.map { it.name }.toTypedArray())
    }

    // ── Restore ───────────────────────────────────────────────────────────

    private fun restoreState() {
        val ps = current
        fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE

        seekJitter.progress        = ps.jitterAmount
        tvJitter.text              = "Jitter: ${ps.jitterAmount} ms"
        spinnerJitterType.setSelection(ps.jitterType.ordinal)
        spinnerVelPattern.setSelection(ps.velocityPattern.ordinal)

        spinnerMelodicEngine.setSelection(ps.melodicEngine.ordinal)

        layoutMarkov.visibility    = vis(ps.melodicEngine == MelodicEngine.MARKOV)
        spinnerLogicStyle.setSelection(ps.markovLogicStyle.ordinal)
        switchSecondOrder.isChecked = ps.markovSecondOrder

        switchNarmour.isChecked    = ps.narmourEnabled
        layoutNarmour.visibility   = vis(ps.narmourEnabled)
        seekNarmourProcess.progress = ps.narmourProcessWeight
        tvNarmourProcess.text      = "Process: ${ps.narmourProcessWeight}"
        seekNarmourReturn.progress  = ps.narmourReturnWeight
        tvNarmourReturn.text       = "Return: ${ps.narmourReturnWeight}"
        seekNarmourLeap.progress    = ps.narmourLeapThreshold
        tvNarmourLeap.text         = "Leap ≥: ${ps.narmourLeapThreshold} st"

        switchGravity.isChecked    = ps.gravityEnabled
        layoutGravity.visibility   = vis(ps.gravityEnabled)
        seekGravityThreshold.progress = ps.gravityThreshold
        tvGravityThreshold.text    = "Threshold: ${ps.gravityThreshold}"
        seekGravityStrength.progress  = ps.gravityStrength
        tvGravityStrength.text     = "Strength: ${ps.gravityStrength}"

        switchGesture.isChecked    = ps.gestureEnabled
        layoutGesture.visibility   = vis(ps.gestureEnabled)
        seekGestureDepth.progress  = ps.gestureDepth
        tvGestureDepth.text        = "Depth: ${ps.gestureDepth}"
        spinnerGesturePitch.setSelection(ps.gesturePitchShape.ordinal)
        spinnerGestureRegister.setSelection(ps.gestureRegister.ordinal)
        spinnerGestureDensity.setSelection(ps.gestureDensity.ordinal)
        spinnerGestureVelocity.setSelection(ps.gestureVelocity.ordinal)

        layoutNrtMelodic.visibility = vis(ps.melodicEngine == MelodicEngine.NRT)
        spinnerNrtCycle.setSelection(ps.nrtCycle.ordinal)
        seekNrtP.progress           = ps.nrtPWeight
        tvNrtPWeight.text           = "P: ${ps.nrtPWeight}"
        seekNrtL.progress           = ps.nrtLWeight
        tvNrtLWeight.text           = "L: ${ps.nrtLWeight}"
        seekNrtR.progress           = ps.nrtRWeight
        tvNrtRWeight.text           = "R: ${ps.nrtRWeight}"

        layoutPwg.visibility       = vis(ps.melodicEngine == MelodicEngine.PWG)
        spinnerPwgMotif.setSelection(ps.pwgMotif.ordinal)
        seekPwgPhrase.progress     = ps.pwgPhraseLen - 2
        tvPwgPhrase.text           = "Phrase: ${ps.pwgPhraseLen} bars"
        seekPwgDir.progress        = ps.pwgDirectionBias + 4
        tvPwgDirection.text        = "Dir bias: ${ps.pwgDirectionBias}"

        layoutLSystem.visibility   = vis(ps.melodicEngine == MelodicEngine.LSYSTEM)
        spinnerLSystemAxiom.setSelection(ps.lSystemAxiom.ordinal)
        seekLSystemIter.progress   = ps.lSystemIterations - 1
        tvLSystemIter.text         = "Iterations: ${ps.lSystemIterations}"
        seekLSystemVariance.progress = ps.lSystemVariance
        tvLSystemVariance.text     = "Variance: ${ps.lSystemVariance}"

        layoutCellAuto.visibility  = vis(ps.melodicEngine == MelodicEngine.CELLULAR)
        seekCaSurvMin.progress     = ps.caSurvMin
        tvCaSurvMin.text           = "Surv min: ${ps.caSurvMin}"
        seekCaSurvMax.progress     = ps.caSurvMax
        tvCaSurvMax.text           = "Surv max: ${ps.caSurvMax}"
        seekCaBirth.progress       = ps.caBirth
        tvCaBirth.text             = "Birth: ${ps.caBirth}"
        seekCaMutation.progress    = ps.caMutation
        tvCaMutation.text          = "Mutation: ${ps.caMutation}"
    }

    // ── Listeners ─────────────────────────────────────────────────────────

    private fun setupListeners() {
        fun simpleSeek(block: (Int) -> Unit) = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) block(p) }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        }

        seekJitter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(jitterAmount = p)
            tvJitter.text = "Jitter: $p ms"; push()
        })
        spinnerJitterType.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(jitterType = JitterType.entries[pos]); push()
        }
        spinnerVelPattern.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(velocityPattern = VelocityPattern.entries[pos]); push()
        }

        spinnerMelodicEngine.onItemSelectedListener = spinnerListener { pos ->
            val eng = MelodicEngine.entries[pos]
            current = current.copy(melodicEngine = eng)
            layoutMarkov.visibility    = if (eng == MelodicEngine.MARKOV)   View.VISIBLE else View.GONE
            layoutNrtMelodic.visibility = if (eng == MelodicEngine.NRT)     View.VISIBLE else View.GONE
            layoutPwg.visibility       = if (eng == MelodicEngine.PWG)      View.VISIBLE else View.GONE
            layoutLSystem.visibility   = if (eng == MelodicEngine.LSYSTEM)  View.VISIBLE else View.GONE
            layoutCellAuto.visibility  = if (eng == MelodicEngine.CELLULAR) View.VISIBLE else View.GONE
            push()
        }

        switchSecondOrder.setOnCheckedChangeListener { _, on ->
            current = current.copy(markovSecondOrder = on); push()
        }
        spinnerLogicStyle.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(markovLogicStyle = MarkovLogicStyle.entries[pos]); push()
        }

        switchNarmour.setOnCheckedChangeListener { _, on ->
            current = current.copy(narmourEnabled = on)
            layoutNarmour.visibility = if (on) View.VISIBLE else View.GONE; push()
        }
        seekNarmourProcess.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourProcessWeight = p)
            tvNarmourProcess.text = "Process: $p"; push()
        })
        seekNarmourReturn.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourReturnWeight = p)
            tvNarmourReturn.text = "Return: $p"; push()
        })
        seekNarmourLeap.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourLeapThreshold = p)
            tvNarmourLeap.text = "Leap ≥: $p st"; push()
        })

        switchGravity.setOnCheckedChangeListener { _, on ->
            current = current.copy(gravityEnabled = on)
            layoutGravity.visibility = if (on) View.VISIBLE else View.GONE; push()
        }
        seekGravityThreshold.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gravityThreshold = p)
            tvGravityThreshold.text = "Threshold: $p"; push()
        })
        seekGravityStrength.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gravityStrength = p)
            tvGravityStrength.text = "Strength: $p"; push()
        })

        switchGesture.setOnCheckedChangeListener { _, on ->
            current = current.copy(gestureEnabled = on)
            layoutGesture.visibility = if (on) View.VISIBLE else View.GONE; push()
        }
        seekGestureDepth.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gestureDepth = p)
            tvGestureDepth.text = "Depth: $p"; push()
        })
        spinnerGesturePitch.onItemSelectedListener    = spinnerListener { pos -> current = current.copy(gesturePitchShape = GesturePitchShape.entries[pos]); push() }
        spinnerGestureRegister.onItemSelectedListener = spinnerListener { pos -> current = current.copy(gestureRegister = GestureRegisterTendency.entries[pos]); push() }
        spinnerGestureDensity.onItemSelectedListener  = spinnerListener { pos -> current = current.copy(gestureDensity = GestureDensityProfile.entries[pos]); push() }
        spinnerGestureVelocity.onItemSelectedListener = spinnerListener { pos -> current = current.copy(gestureVelocity = GestureVelocityProfile.entries[pos]); push() }

        spinnerNrtCycle.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(nrtCycle = NrtCycle.entries[pos]); push()
        }
        seekNrtP.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(nrtPWeight = p); tvNrtPWeight.text = "P: $p"; push() })
        seekNrtL.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(nrtLWeight = p); tvNrtLWeight.text = "L: $p"; push() })
        seekNrtR.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(nrtRWeight = p); tvNrtRWeight.text = "R: $p"; push() })

        spinnerPwgMotif.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(pwgMotif = PwgMotif.entries[pos]); push()
        }
        seekPwgPhrase.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(pwgPhraseLen = p + 2)
            tvPwgPhrase.text = "Phrase: ${p + 2} bars"; push()
        })
        seekPwgDir.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(pwgDirectionBias = p - 4)
            tvPwgDirection.text = "Dir bias: ${p - 4}"; push()
        })

        spinnerLSystemAxiom.onItemSelectedListener = spinnerListener { pos ->
            current = current.copy(lSystemAxiom = LSystemAxiom.entries[pos]); push()
        }
        seekLSystemIter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemIterations = p + 1)
            tvLSystemIter.text = "Iterations: ${p + 1}"; push()
        })
        seekLSystemVariance.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemVariance = p)
            tvLSystemVariance.text = "Variance: $p"; push()
        })

        seekCaSurvMin.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(caSurvMin = p); tvCaSurvMin.text = "Surv min: $p"; push() })
        seekCaSurvMax.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(caSurvMax = p); tvCaSurvMax.text = "Surv max: $p"; push() })
        seekCaBirth.setOnSeekBarChangeListener(simpleSeek   { p -> current = current.copy(caBirth = p);   tvCaBirth.text   = "Birth: $p";    push() })
        seekCaMutation.setOnSeekBarChangeListener(simpleSeek { p -> current = current.copy(caMutation = p); tvCaMutation.text = "Mutation: $p"; push() })
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun spinnerListener(block: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    private fun push() { listener?.onProSettingsChanged(current) }

    // ── Public API ────────────────────────────────────────────────────────

    /** Called from MainFragment when Euclidean timing is selected/deselected or seekbar params change.
     *  Updates euclidean fields in ProSettings and pushes without needing UI controls here. */
    fun setEuclideanParams(enabled: Boolean, steps: Int, density: Int, rotation: Int) {
        current = current.copy(
            euclideanEnabled  = enabled,
            euclideanSteps    = steps,
            euclideanDensity  = density,
            euclideanRotation = rotation
        )
        push()
    }
}
