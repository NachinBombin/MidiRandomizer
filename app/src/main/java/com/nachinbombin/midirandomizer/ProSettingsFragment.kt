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
    private lateinit var switchEuclidean:   Switch
    private lateinit var layoutEuclidean:   View
    private lateinit var seekEucSteps:      SeekBar
    private lateinit var tvEucSteps:        TextView
    private lateinit var seekEucDensity:    SeekBar
    private lateinit var tvEucDensity:      TextView
    private lateinit var seekEucRotation:   SeekBar
    private lateinit var tvEucRotation:     TextView

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
        switchEuclidean   = v.findViewById(R.id.switchEuclidean)
        layoutEuclidean   = v.findViewById(R.id.layoutEuclidean)
        seekEucSteps      = v.findViewById(R.id.seekEucSteps)
        tvEucSteps        = v.findViewById(R.id.tvEucSteps)
        seekEucDensity    = v.findViewById(R.id.seekEucDensity)
        tvEucDensity      = v.findViewById(R.id.tvEucDensity)
        seekEucRotation   = v.findViewById(R.id.seekEucRotation)
        tvEucRotation     = v.findViewById(R.id.tvEucRotation)

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

        spinnerJitterType.adapter    = arr(*JitterType.entries.map { it.displayName }.toTypedArray())
        spinnerVelPattern.adapter    = arr(*VelocityPattern.entries.map { it.displayName }.toTypedArray())
        spinnerMelodicEngine.adapter = arr(*MelodicEngine.entries.map { it.displayName }.toTypedArray())
        spinnerLogicStyle.adapter    = arr(*MelodicLogicStyle.entries.map { it.displayName }.toTypedArray())

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

    private fun restoreState() {
        val ps = current
        seekJitter.progress       = ps.jitterAmount
        tvJitter.text             = getString(R.string.label_jitter_amount, ps.jitterAmount)
        spinnerJitterType.setSelection(ps.jitterType.ordinal)
        spinnerVelPattern.setSelection(ps.velocityPattern.ordinal)

        switchEuclidean.isChecked  = ps.euclideanEnabled
        layoutEuclidean.visibility = vis(ps.euclideanEnabled)
        seekEucSteps.progress   = ps.euclideanSteps - 1
        tvEucSteps.text         = "Steps: ${ps.euclideanSteps}"
        seekEucDensity.progress = ps.euclideanDensity - 1
        tvEucDensity.text       = "Density: ${ps.euclideanDensity}"
        seekEucRotation.progress = ps.euclideanRotation
        tvEucRotation.text      = "Rotation: ${ps.euclideanRotation}"

        spinnerMelodicEngine.setSelection(ps.melodicEngine.ordinal)
        showEnginePanel(ps.melodicEngine)

        spinnerLogicStyle.setSelection(ps.melodicLogicStyle.ordinal)
        switchSecondOrder.isChecked = ps.secondOrderMarkov

        val nc = ps.narmourConfig
        switchNarmour.isChecked   = nc.enabled
        layoutNarmour.visibility  = vis(nc.enabled)
        seekNarmourProcess.progress = (nc.processVsReversal * 100).toInt()
        tvNarmourProcess.text = "Process vs Reversal: ${seekNarmourProcess.progress}%"
        seekNarmourReturn.progress  = (nc.returnBias * 100).toInt()
        tvNarmourReturn.text = "Post-leap return bias: ${seekNarmourReturn.progress}%"
        seekNarmourLeap.progress    = (nc.maxLeapPenalty * 100).toInt()
        tvNarmourLeap.text = "Leap penalty: ${seekNarmourLeap.progress}%"

        val gc = ps.contourGravityConfig
        switchGravity.isChecked     = gc.enabled
        layoutGravity.visibility    = vis(gc.enabled)
        seekGravityThreshold.progress = gc.threshold
        tvGravityThreshold.text = "Threshold: ${gc.threshold} steps"
        seekGravityStrength.progress  = gc.strength.toInt()
        tvGravityStrength.text = "Strength: ${gc.strength.toInt()}"

        val gec = ps.gestureCurveConfig
        val gestureOn = gec.gestureDepth > 0f
        switchGesture.isChecked    = gestureOn
        layoutGesture.visibility   = vis(gestureOn)
        seekGestureDepth.progress  = (gec.gestureDepth * 100).toInt()
        tvGestureDepth.text = "Gesture depth: ${seekGestureDepth.progress}%"
        spinnerGesturePitch.setSelection(gec.pitchCurvePreset)
        spinnerGestureRegister.setSelection(gec.registerCurvePreset)
        spinnerGestureDensity.setSelection(gec.densityCurvePreset)
        spinnerGestureVelocity.setSelection(gec.velocityCurvePreset)

        val nrt = ps.nrtMelodicConfig
        spinnerNrtCycle.setSelection(nrt.cyclePreset)
        seekNrtP.progress = (nrt.pWeight * 5f).toInt().coerceIn(0, 10)
        tvNrtPWeight.text = "P weight: ${nrt.pWeight}"
        seekNrtL.progress = (nrt.lWeight * 5f).toInt().coerceIn(0, 10)
        tvNrtLWeight.text = "L weight: ${nrt.lWeight}"
        seekNrtR.progress = (nrt.rWeight * 5f).toInt().coerceIn(0, 10)
        tvNrtRWeight.text = "R weight: ${nrt.rWeight}"

        val pwg = ps.pwgConfig
        spinnerPwgMotif.setSelection(pwg.motifSetIndex)
        seekPwgPhrase.progress = (pwg.phraseLengthMotifs - 1).coerceIn(0, 7)
        tvPwgPhrase.text = "Motifs per phrase: ${pwg.phraseLengthMotifs}"
        seekPwgDir.progress = ((pwg.directionBias + 1f) * 10f).toInt().coerceIn(0, 20)
        tvPwgDirection.text = directionLabel(pwg.directionBias)

        val ls = ps.lSystemConfig
        spinnerLSystemAxiom.setSelection(ls.axiomIndex)
        seekLSystemIter.progress = (ls.iterations - 1).coerceIn(0, 3)
        tvLSystemIter.text = "Iterations: ${ls.iterations}"
        seekLSystemVariance.progress = (ls.ruleVariance * 100f).toInt()
        tvLSystemVariance.text = "Rule variance: ${seekLSystemVariance.progress}%"

        val ca = ps.cellAutomataConfig
        seekCaSurvMin.progress = ca.survivalMin
        tvCaSurvMin.text = "Survival min: ${ca.survivalMin}"
        seekCaSurvMax.progress = ca.survivalMax
        tvCaSurvMax.text = "Survival max: ${ca.survivalMax}"
        seekCaBirth.progress = ca.birthCount
        tvCaBirth.text = "Birth count: ${ca.birthCount}"
        seekCaMutation.progress = (ca.mutationRate * 100f).toInt()
        tvCaMutation.text = "Mutation rate: ${seekCaMutation.progress}%"
    }

    // ── Listeners ────────────────────────────────────────────────────────

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
            layoutEuclidean.visibility = vis(on); push()
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

        spinnerMelodicEngine.onItemSelectedListener = simpleSpinner { pos ->
            val eng = MelodicEngine.entries[pos]
            current = current.copy(
                melodicEngine  = eng,
                markovEnabled  = (eng == MelodicEngine.MARKOV)
            )
            showEnginePanel(eng); push()
        }

        spinnerLogicStyle.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(melodicLogicStyle = MelodicLogicStyle.entries[pos]); push()
        }
        switchSecondOrder.setOnCheckedChangeListener { _, on ->
            current = current.copy(secondOrderMarkov = on); push()
        }

        switchNarmour.setOnCheckedChangeListener { _, on ->
            current = current.copy(narmourConfig = current.narmourConfig.copy(enabled = on))
            layoutNarmour.visibility = vis(on); push()
        }
        seekNarmourProcess.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourConfig = current.narmourConfig.copy(processVsReversal = p / 100f))
            tvNarmourProcess.text = "Process vs Reversal: $p%"; push()
        })
        seekNarmourReturn.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourConfig = current.narmourConfig.copy(returnBias = p / 100f))
            tvNarmourReturn.text = "Post-leap return bias: $p%"; push()
        })
        seekNarmourLeap.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourConfig = current.narmourConfig.copy(maxLeapPenalty = p / 100f))
            tvNarmourLeap.text = "Leap penalty: $p%"; push()
        })

        switchGravity.setOnCheckedChangeListener { _, on ->
            current = current.copy(contourGravityConfig = current.contourGravityConfig.copy(enabled = on))
            layoutGravity.visibility = vis(on); push()
        }
        seekGravityThreshold.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(contourGravityConfig = current.contourGravityConfig.copy(threshold = p))
            tvGravityThreshold.text = "Threshold: $p steps"; push()
        })
        seekGravityStrength.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(contourGravityConfig = current.contourGravityConfig.copy(strength = p.toFloat()))
            tvGravityStrength.text = "Strength: $p"; push()
        })

        switchGesture.setOnCheckedChangeListener { _, on ->
            val depth = if (on) (seekGestureDepth.progress / 100f).coerceAtLeast(0.01f) else 0f
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(gestureDepth = depth))
            layoutGesture.visibility = vis(on); push()
        }
        seekGestureDepth.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(gestureDepth = p / 100f))
            tvGestureDepth.text = "Gesture depth: $p%"; push()
        })
        spinnerGesturePitch.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(pitchCurvePreset = pos)); push()
        }
        spinnerGestureRegister.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(registerCurvePreset = pos)); push()
        }
        spinnerGestureDensity.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(densityCurvePreset = pos)); push()
        }
        spinnerGestureVelocity.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(gestureCurveConfig = current.gestureCurveConfig.copy(velocityCurvePreset = pos)); push()
        }

        spinnerNrtCycle.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(nrtMelodicConfig = current.nrtMelodicConfig.copy(cyclePreset = pos)); push()
        }
        seekNrtP.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtMelodicConfig = current.nrtMelodicConfig.copy(pWeight = p / 5f))
            tvNrtPWeight.text = "P weight: ${p / 5f}"; push()
        })
        seekNrtL.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtMelodicConfig = current.nrtMelodicConfig.copy(lWeight = p / 5f))
            tvNrtLWeight.text = "L weight: ${p / 5f}"; push()
        })
        seekNrtR.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtMelodicConfig = current.nrtMelodicConfig.copy(rWeight = p / 5f))
            tvNrtRWeight.text = "R weight: ${p / 5f}"; push()
        })

        spinnerPwgMotif.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(pwgConfig = current.pwgConfig.copy(motifSetIndex = pos)); push()
        }
        seekPwgPhrase.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(pwgConfig = current.pwgConfig.copy(phraseLengthMotifs = p + 1))
            tvPwgPhrase.text = "Motifs per phrase: ${p + 1}"; push()
        })
        seekPwgDir.setOnSeekBarChangeListener(simpleSeek { p ->
            val bias = (p / 10f) - 1f
            current = current.copy(pwgConfig = current.pwgConfig.copy(directionBias = bias))
            tvPwgDirection.text = directionLabel(bias); push()
        })

        spinnerLSystemAxiom.onItemSelectedListener = simpleSpinner { pos ->
            current = current.copy(lSystemConfig = current.lSystemConfig.copy(axiomIndex = pos)); push()
        }
        seekLSystemIter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemConfig = current.lSystemConfig.copy(iterations = p + 1))
            tvLSystemIter.text = "Iterations: ${p + 1}"; push()
        })
        seekLSystemVariance.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemConfig = current.lSystemConfig.copy(ruleVariance = p / 100f))
            tvLSystemVariance.text = "Rule variance: $p%"; push()
        })

        seekCaSurvMin.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(cellAutomataConfig = current.cellAutomataConfig.copy(survivalMin = p))
            tvCaSurvMin.text = "Survival min: $p"; push()
        })
        seekCaSurvMax.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(cellAutomataConfig = current.cellAutomataConfig.copy(survivalMax = p))
            tvCaSurvMax.text = "Survival max: $p"; push()
        })
        seekCaBirth.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(cellAutomataConfig = current.cellAutomataConfig.copy(birthCount = p))
            tvCaBirth.text = "Birth count: $p"; push()
        })
        seekCaMutation.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(cellAutomataConfig = current.cellAutomataConfig.copy(mutationRate = p / 100f))
            tvCaMutation.text = "Mutation rate: $p%"; push()
        })
    }

    // ── Engine panel visibility ───────────────────────────────────────────

    private fun showEnginePanel(engine: MelodicEngine) {
        layoutMarkov.visibility    = vis(engine == MelodicEngine.MARKOV)
        layoutNrtMelodic.visibility = vis(engine == MelodicEngine.NRT_MELODIC)
        layoutPwg.visibility       = vis(engine == MelodicEngine.PWG)
        layoutLSystem.visibility   = vis(engine == MelodicEngine.L_SYSTEM)
        layoutCellAuto.visibility  = vis(engine == MelodicEngine.CELL_AUTOMATA)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun push() = listener?.onProSettingsChanged(current)

    private fun vis(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private fun directionLabel(bias: Float) = when {
        bias < -0.5f -> "Direction: Descending"
        bias >  0.5f -> "Direction: Ascending"
        else         -> "Direction: Neutral"
    }

    private fun simpleSeek(block: (Int) -> Unit) = object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) block(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun simpleSpinner(block: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = block(pos)
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
}
