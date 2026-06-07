package com.nachinbombin.midirandomizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class ProSettingsFragment : Fragment() {

    interface OnSettingsChangedListener {
        fun onProSettingsChanged(settings: ProSettings)
    }

    private var listener: OnSettingsChangedListener? = null
    private var current: ProSettings = ProSettings()

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var seekJitter:            SeekBar
    private lateinit var tvJitterAmount:        TextView
    private lateinit var spinnerJitterType:     Spinner
    private lateinit var spinnerVelPattern:     Spinner
    private lateinit var spinnerMelodicEngine:  Spinner
    private lateinit var layoutMarkov:          View
    private lateinit var spinnerLogicStyle:     Spinner
    private lateinit var switchSecondOrder:     Switch
    private lateinit var switchNarmour:         Switch
    private lateinit var layoutNarmour:         View
    private lateinit var seekNarmourProcess:    SeekBar
    private lateinit var tvNarmourProcess:      TextView
    private lateinit var seekNarmourReturn:     SeekBar
    private lateinit var tvNarmourReturn:       TextView
    private lateinit var seekNarmourLeap:       SeekBar
    private lateinit var tvNarmourLeap:         TextView
    private lateinit var switchGravity:         Switch
    private lateinit var layoutGravity:         View
    private lateinit var seekGravityThreshold:  SeekBar
    private lateinit var tvGravityThreshold:    TextView
    private lateinit var seekGravityStrength:   SeekBar
    private lateinit var tvGravityStrength:     TextView
    private lateinit var switchGesture:         Switch
    private lateinit var layoutGesture:         View
    private lateinit var seekGestureDepth:      SeekBar
    private lateinit var tvGestureDepth:        TextView
    private lateinit var spinnerGesturePitch:   Spinner
    private lateinit var spinnerGestureRegister:Spinner
    private lateinit var spinnerGestureDensity: Spinner
    private lateinit var spinnerGestureVelocity:Spinner
    private lateinit var layoutNrtMelodic:      View
    private lateinit var spinnerNrtCycle:       Spinner
    private lateinit var seekNrtP:              SeekBar
    private lateinit var tvNrtPWeight:          TextView
    private lateinit var seekNrtL:              SeekBar
    private lateinit var tvNrtLWeight:          TextView
    private lateinit var seekNrtR:              SeekBar
    private lateinit var tvNrtRWeight:          TextView
    private lateinit var layoutPwg:             View
    private lateinit var spinnerPwgMotif:       Spinner
    private lateinit var seekPwgPhrase:         SeekBar
    private lateinit var tvPwgPhrase:           TextView
    private lateinit var seekPwgDirection:      SeekBar
    private lateinit var tvPwgDirection:        TextView
    private lateinit var layoutLSystem:         View
    private lateinit var spinnerLSystemAxiom:   Spinner
    private lateinit var seekLSystemIter:       SeekBar
    private lateinit var tvLSystemIter:         TextView
    private lateinit var seekLSystemVariance:   SeekBar
    private lateinit var tvLSystemVariance:     TextView
    private lateinit var layoutCellAuto:        View
    private lateinit var seekCaSurvMin:         SeekBar
    private lateinit var tvCaSurvMin:           TextView
    private lateinit var seekCaSurvMax:         SeekBar
    private lateinit var tvCaSurvMax:           TextView
    private lateinit var seekCaBirth:           SeekBar
    private lateinit var tvCaBirth:             TextView
    private lateinit var seekCaMutation:        SeekBar
    private lateinit var tvCaMutation:          TextView

    fun setListener(l: OnSettingsChangedListener) { listener = l }

    fun setInitialSettings(ps: ProSettings) {
        current = ps
        if (view != null) restoreState(ps)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_pro_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        restoreState(current)
        setupListeners()
    }

    // ── Bind ──────────────────────────────────────────────────────────────
    private fun bindViews(v: View) {
        seekJitter            = v.findViewById(R.id.seekJitter)
        tvJitterAmount        = v.findViewById(R.id.tvJitterAmount)
        spinnerJitterType     = v.findViewById(R.id.spinnerJitterType)
        spinnerVelPattern     = v.findViewById(R.id.spinnerVelPattern)
        spinnerMelodicEngine  = v.findViewById(R.id.spinnerMelodicEngine)
        layoutMarkov          = v.findViewById(R.id.layoutMarkov)
        spinnerLogicStyle     = v.findViewById(R.id.spinnerLogicStyle)
        switchSecondOrder     = v.findViewById(R.id.switchSecondOrder)
        switchNarmour         = v.findViewById(R.id.switchNarmour)
        layoutNarmour         = v.findViewById(R.id.layoutNarmour)
        seekNarmourProcess    = v.findViewById(R.id.seekNarmourProcess)
        tvNarmourProcess      = v.findViewById(R.id.tvNarmourProcess)
        seekNarmourReturn     = v.findViewById(R.id.seekNarmourReturn)
        tvNarmourReturn       = v.findViewById(R.id.tvNarmourReturn)
        seekNarmourLeap       = v.findViewById(R.id.seekNarmourLeap)
        tvNarmourLeap         = v.findViewById(R.id.tvNarmourLeap)
        switchGravity         = v.findViewById(R.id.switchGravity)
        layoutGravity         = v.findViewById(R.id.layoutGravity)
        seekGravityThreshold  = v.findViewById(R.id.seekGravityThreshold)
        tvGravityThreshold    = v.findViewById(R.id.tvGravityThreshold)
        seekGravityStrength   = v.findViewById(R.id.seekGravityStrength)
        tvGravityStrength     = v.findViewById(R.id.tvGravityStrength)
        switchGesture         = v.findViewById(R.id.switchGesture)
        layoutGesture         = v.findViewById(R.id.layoutGesture)
        seekGestureDepth      = v.findViewById(R.id.seekGestureDepth)
        tvGestureDepth        = v.findViewById(R.id.tvGestureDepth)
        spinnerGesturePitch   = v.findViewById(R.id.spinnerGesturePitch)
        spinnerGestureRegister= v.findViewById(R.id.spinnerGestureRegister)
        spinnerGestureDensity = v.findViewById(R.id.spinnerGestureDensity)
        spinnerGestureVelocity= v.findViewById(R.id.spinnerGestureVelocity)
        layoutNrtMelodic      = v.findViewById(R.id.layoutNrtMelodic)
        spinnerNrtCycle       = v.findViewById(R.id.spinnerNrtCycle)
        seekNrtP              = v.findViewById(R.id.seekNrtP)
        tvNrtPWeight          = v.findViewById(R.id.tvNrtPWeight)
        seekNrtL              = v.findViewById(R.id.seekNrtL)
        tvNrtLWeight          = v.findViewById(R.id.tvNrtLWeight)
        seekNrtR              = v.findViewById(R.id.seekNrtR)
        tvNrtRWeight          = v.findViewById(R.id.tvNrtRWeight)
        layoutPwg             = v.findViewById(R.id.layoutPwg)
        spinnerPwgMotif       = v.findViewById(R.id.spinnerPwgMotif)
        seekPwgPhrase         = v.findViewById(R.id.seekPwgPhrase)
        tvPwgPhrase           = v.findViewById(R.id.tvPwgPhrase)
        seekPwgDirection      = v.findViewById(R.id.seekPwgDirection)
        tvPwgDirection        = v.findViewById(R.id.tvPwgDirection)
        layoutLSystem         = v.findViewById(R.id.layoutLSystem)
        spinnerLSystemAxiom   = v.findViewById(R.id.spinnerLSystemAxiom)
        seekLSystemIter       = v.findViewById(R.id.seekLSystemIter)
        tvLSystemIter         = v.findViewById(R.id.tvLSystemIter)
        seekLSystemVariance   = v.findViewById(R.id.seekLSystemVariance)
        tvLSystemVariance     = v.findViewById(R.id.tvLSystemVariance)
        layoutCellAuto        = v.findViewById(R.id.layoutCellAuto)
        seekCaSurvMin         = v.findViewById(R.id.seekCaSurvMin)
        tvCaSurvMin           = v.findViewById(R.id.tvCaSurvMin)
        seekCaSurvMax         = v.findViewById(R.id.seekCaSurvMax)
        tvCaSurvMax           = v.findViewById(R.id.tvCaSurvMax)
        seekCaBirth           = v.findViewById(R.id.seekCaBirth)
        tvCaBirth             = v.findViewById(R.id.tvCaBirth)
        seekCaMutation        = v.findViewById(R.id.seekCaMutation)
        tvCaMutation          = v.findViewById(R.id.tvCaMutation)

        // ── Spinner adapters ─────────────────────────────────────────────
        fun spinner(view: Spinner, items: List<String>) {
            view.adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        spinner(spinnerJitterType,      listOf("Gaussian","Uniform","Triangular","Exponential"))
        spinner(spinnerVelPattern,      listOf("Flat","Accent 1-3","Accent 1-2-3","Swell","Random Walk","Human Groove"))
        spinner(spinnerMelodicEngine,   listOf("Markov","NRT Melodic","Phrase Grammar","L-System","Cell Automata"))
        spinner(spinnerLogicStyle,      listOf("Stepwise","Leap","Chromatic Walk","Zigzag","Free"))
        spinner(spinnerGesturePitch,    listOf("Linear Ascent","Linear Descent","Arch","Valley","Plateau","Random"))
        spinner(spinnerGestureRegister, listOf("Expanding","Contracting","High","Low","Mid","Wander"))
        spinner(spinnerGestureDensity,  listOf("Even","Accelerando","Ritardando","Sparse-Dense","Dense-Sparse"))
        spinner(spinnerGestureVelocity, listOf("Crescendo","Decrescendo","Swell","Flat","Terraced"))
        spinner(spinnerNrtCycle,        listOf("Chromatic (12)","Diatonic (7)","Pentatonic (5)","Octatonic (8)","Custom"))
        spinner(spinnerPwgMotif,        listOf("Step","Skip","Leap","Mixed","Chromatic","Arch","Wave"))
        spinner(spinnerLSystemAxiom,    listOf("F","F+F","F-F+F","FF","F[+F]F[-F]F"))
    }

    // ── Restore state ─────────────────────────────────────────────────────
    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE

    private fun restoreState(ps: ProSettings) {
        seekJitter.progress            = ps.jitterAmount
        tvJitterAmount.text            = "Jitter: ${ps.jitterAmount}ms"
        spinnerJitterType.setSelection(ps.jitterType)
        spinnerVelPattern.setSelection(ps.velocityPattern)

        val engineIdx = ps.melodicEngine.ordinal.coerceIn(0, 4)
        spinnerMelodicEngine.setSelection(engineIdx)
        layoutMarkov.visibility     = vis(engineIdx == 0)
        layoutNrtMelodic.visibility = vis(engineIdx == 1)
        layoutPwg.visibility        = vis(engineIdx == 2)
        layoutLSystem.visibility    = vis(engineIdx == 3)
        layoutCellAuto.visibility   = vis(engineIdx == 4)

        switchSecondOrder.isChecked = ps.markovSecondOrder
        switchNarmour.isChecked     = ps.narmourEnabled
        layoutNarmour.visibility    = vis(ps.narmourEnabled)
        seekNarmourProcess.progress = ps.narmourProcessBias
        tvNarmourProcess.text       = "Process vs Reversal: ${ps.narmourProcessBias}%"
        seekNarmourReturn.progress  = ps.narmourReturnBias
        tvNarmourReturn.text        = "Post-leap return bias: ${ps.narmourReturnBias}%"
        seekNarmourLeap.progress    = ps.narmourLeapPenalty
        tvNarmourLeap.text          = "Leap penalty: ${ps.narmourLeapPenalty}%"

        switchGravity.isChecked     = ps.gravityEnabled
        layoutGravity.visibility    = vis(ps.gravityEnabled)
        seekGravityThreshold.progress = ps.gravityThreshold
        tvGravityThreshold.text     = "Threshold: ${ps.gravityThreshold} steps"
        seekGravityStrength.progress  = ps.gravityStrength
        tvGravityStrength.text      = "Strength: ${ps.gravityStrength}"

        switchGesture.isChecked     = ps.gestureEnabled
        layoutGesture.visibility    = vis(ps.gestureEnabled)
        seekGestureDepth.progress   = ps.gestureDepth
        tvGestureDepth.text         = "Gesture depth: ${ps.gestureDepth}%"
        spinnerGesturePitch.setSelection(ps.gesturePitchCurve)
        spinnerGestureRegister.setSelection(ps.gestureRegisterCurve)
        spinnerGestureDensity.setSelection(ps.gestureDensityCurve)
        spinnerGestureVelocity.setSelection(ps.gestureVelocityCurve)

        spinnerNrtCycle.setSelection(ps.nrtCyclePreset)
        val nrtP = (ps.nrtPWeight * 5).toInt().coerceIn(0, 10)
        val nrtL = (ps.nrtLWeight * 5).toInt().coerceIn(0, 10)
        val nrtR = (ps.nrtRWeight * 5).toInt().coerceIn(0, 10)
        seekNrtP.progress    = nrtP; tvNrtPWeight.text = "P weight: ${nrtP / 5.0f}"
        seekNrtL.progress    = nrtL; tvNrtLWeight.text = "L weight: ${nrtL / 5.0f}"
        seekNrtR.progress    = nrtR; tvNrtRWeight.text = "R weight: ${nrtR / 5.0f}"

        spinnerPwgMotif.setSelection(ps.pwgMotifVocab)
        seekPwgPhrase.progress    = (ps.pwgMotifsPerPhrase - 1).coerceIn(0, 7)
        tvPwgPhrase.text          = "Motifs per phrase: ${ps.pwgMotifsPerPhrase}"
        seekPwgDirection.progress = (ps.pwgDirectionBias + 10).coerceIn(0, 20)
        tvPwgDirection.text       = "Direction: ${when {
            ps.pwgDirectionBias > 2  -> "Ascending"
            ps.pwgDirectionBias < -2 -> "Descending"
            else                     -> "Neutral"
        }}"

        spinnerLSystemAxiom.setSelection(ps.lSystemAxiom)
        seekLSystemIter.progress     = (ps.lSystemIterations - 1).coerceIn(0, 3)
        tvLSystemIter.text           = "Iterations: ${ps.lSystemIterations}"
        seekLSystemVariance.progress = ps.lSystemVariance
        tvLSystemVariance.text       = "Rule variance: ${ps.lSystemVariance}%"

        seekCaSurvMin.progress  = ps.caSurvivalMin.coerceIn(0, 4)
        tvCaSurvMin.text        = "Survival min: ${ps.caSurvivalMin}"
        seekCaSurvMax.progress  = ps.caSurvivalMax.coerceIn(0, 4)
        tvCaSurvMax.text        = "Survival max: ${ps.caSurvivalMax}"
        seekCaBirth.progress    = ps.caBirthCount.coerceIn(0, 4)
        tvCaBirth.text          = "Birth count: ${ps.caBirthCount}"
        seekCaMutation.progress = ps.caMutationRate
        tvCaMutation.text       = "Mutation rate: ${ps.caMutationRate}%"
    }

    // ── Push helper ───────────────────────────────────────────────────────
    private fun push() { listener?.onProSettingsChanged(current) }

    private fun simpleSeek(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) action(p) }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    // ── Listeners ─────────────────────────────────────────────────────────
    private fun setupListeners() {
        seekJitter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(jitterAmount = p)
            tvJitterAmount.text = "Jitter: ${p}ms"; push()
        })
        spinnerJitterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(jitterType = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerVelPattern.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(velocityPattern = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerMelodicEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val engine = MelodicEngineType.entries.getOrElse(pos) { MelodicEngineType.MARKOV }
                current = current.copy(melodicEngine = engine)
                layoutMarkov.visibility     = vis(pos == 0)
                layoutNrtMelodic.visibility = vis(pos == 1)
                layoutPwg.visibility        = vis(pos == 2)
                layoutLSystem.visibility    = vis(pos == 3)
                layoutCellAuto.visibility   = vis(pos == 4)
                push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        switchSecondOrder.setOnCheckedChangeListener { _, on ->
            current = current.copy(markovSecondOrder = on); push()
        }
        switchNarmour.setOnCheckedChangeListener { _, on ->
            current = current.copy(narmourEnabled = on)
            layoutNarmour.visibility = vis(on); push()
        }
        seekNarmourProcess.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourProcessBias = p)
            tvNarmourProcess.text = "Process vs Reversal: $p%"; push()
        })
        seekNarmourReturn.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourReturnBias = p)
            tvNarmourReturn.text = "Post-leap return bias: $p%"; push()
        })
        seekNarmourLeap.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(narmourLeapPenalty = p)
            tvNarmourLeap.text = "Leap penalty: $p%"; push()
        })
        switchGravity.setOnCheckedChangeListener { _, on ->
            current = current.copy(gravityEnabled = on)
            layoutGravity.visibility = vis(on); push()
        }
        seekGravityThreshold.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gravityThreshold = p)
            tvGravityThreshold.text = "Threshold: $p steps"; push()
        })
        seekGravityStrength.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gravityStrength = p)
            tvGravityStrength.text = "Strength: $p"; push()
        })
        switchGesture.setOnCheckedChangeListener { _, on ->
            current = current.copy(gestureEnabled = on)
            layoutGesture.visibility = vis(on); push()
        }
        seekGestureDepth.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(gestureDepth = p)
            tvGestureDepth.text = "Gesture depth: $p%"; push()
        })
        spinnerGesturePitch.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(gesturePitchCurve = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerGestureRegister.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(gestureRegisterCurve = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerGestureDensity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(gestureDensityCurve = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerGestureVelocity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(gestureVelocityCurve = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        spinnerNrtCycle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(nrtCyclePreset = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        seekNrtP.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtPWeight = p / 5.0f)
            tvNrtPWeight.text = "P weight: ${p / 5.0f}"; push()
        })
        seekNrtL.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtLWeight = p / 5.0f)
            tvNrtLWeight.text = "L weight: ${p / 5.0f}"; push()
        })
        seekNrtR.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(nrtRWeight = p / 5.0f)
            tvNrtRWeight.text = "R weight: ${p / 5.0f}"; push()
        })
        spinnerPwgMotif.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(pwgMotifVocab = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        seekPwgPhrase.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(pwgMotifsPerPhrase = p + 1)
            tvPwgPhrase.text = "Motifs per phrase: ${p + 1}"; push()
        })
        seekPwgDirection.setOnSeekBarChangeListener(simpleSeek { p ->
            val bias = p - 10
            current = current.copy(pwgDirectionBias = bias)
            tvPwgDirection.text = "Direction: ${when { bias > 2 -> "Ascending"; bias < -2 -> "Descending"; else -> "Neutral" }}"
            push()
        })
        spinnerLSystemAxiom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                current = current.copy(lSystemAxiom = pos); push()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        seekLSystemIter.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemIterations = p + 1)
            tvLSystemIter.text = "Iterations: ${p + 1}"; push()
        })
        seekLSystemVariance.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(lSystemVariance = p)
            tvLSystemVariance.text = "Rule variance: $p%"; push()
        })
        seekCaSurvMin.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(caSurvivalMin = p)
            tvCaSurvMin.text = "Survival min: $p"; push()
        })
        seekCaSurvMax.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(caSurvivalMax = p)
            tvCaSurvMax.text = "Survival max: $p"; push()
        })
        seekCaBirth.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(caBirthCount = p)
            tvCaBirth.text = "Birth count: $p"; push()
        })
        seekCaMutation.setOnSeekBarChangeListener(simpleSeek { p ->
            current = current.copy(caMutationRate = p)
            tvCaMutation.text = "Mutation rate: $p%"; push()
        })
    }
}
