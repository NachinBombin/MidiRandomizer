package com.nachinbombin.midirandomizer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

class PerformanceFragment : Fragment(), MidiService.MidiEventListener {

    private var serviceProvider: VoicesFragment.ServiceProvider? = null

    private var currentV1 = MidiService.Voice1Params()
    private var currentV2 = VoiceConfig()
    private var currentV3 = VoiceConfig()

    private var v1Link = true
    private var v2Link = true
    private var v3Link = true

    private var pitchLocked = false
    private var modLocked = false

    private lateinit var btnStartStop: Button
    private lateinit var sbV1: SeekBar
    private lateinit var sbV2: SeekBar
    private lateinit var sbV3: SeekBar
    private lateinit var tvV1: TextView
    private lateinit var tvV2: TextView
    private lateinit var tvV3: TextView

    private var isUpdatingFromSync = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        serviceProvider = context as? VoicesFragment.ServiceProvider
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.addMidiListener(this)
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.removeMidiListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            setBackgroundColor(0xFF111318.toInt())
        }

        // 1. Top row: Velocity Sliders
        val velRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        
        val v1Control = createVelControl("V1 VEL", currentV1.velocity) { p ->
            currentV1 = currentV1.copy(velocity = p)
            serviceProvider?.getMidiService()?.updateV1Parameters(currentV1)
        }
        sbV1 = v1Control.second
        sbV1.max = 126
        sbV1.progress = currentV1.velocity - 1
        tvV1 = v1Control.first
        velRow.addView(v1Control.third)

        val v2Control = createVelControl("V2 VEL", getVel(currentV2)) { p ->
            currentV2 = setVel(currentV2, p)
            serviceProvider?.getMidiService()?.updateVoice2Config(currentV2)
        }
        sbV2 = v2Control.second
        tvV2 = v2Control.first
        velRow.addView(v2Control.third)

        val v3Control = createVelControl("V3 VEL", getVel(currentV3)) { p ->
            currentV3 = setVel(currentV3, p)
            serviceProvider?.getMidiService()?.updateVoice3Config(currentV3)
        }
        sbV3 = v3Control.second
        tvV3 = v3Control.first
        velRow.addView(v3Control.third)
        
        root.addView(velRow)

        // 2. Link Toggles
        val linkRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        linkRow.addView(createLinkToggle("Link V1", v1Link) { v1Link = it })
        linkRow.addView(createLinkToggle("Link V2", v2Link) { v2Link = it })
        linkRow.addView(createLinkToggle("Link V3", v3Link) { v3Link = it })
        root.addView(linkRow)

        // 3. Wheels
        val wheelsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val pitchWheel = createWheelWithLock("PITCH BEND", 8192, { pitchLocked }) { isLocked ->
            pitchLocked = isLocked
        }
        val pWheelView = pitchWheel.first as VerticalWheelView
        pWheelView.onValueChange = { value -> sendMidiToLinked(0xE0, value) }

        val modWheel = createWheelWithLock("MOD (CC 1)", 0, { modLocked }) { isLocked ->
            modLocked = isLocked
        }
        val mWheelView = modWheel.first as VerticalWheelView
        mWheelView.onValueChange = { value -> sendMidiToLinked(0xB0, 1, (value * 127 / 16383)) }

        wheelsLayout.addView(pitchWheel.second)
        wheelsLayout.addView(modWheel.second)
        root.addView(wheelsLayout)

        // 4. Bottom: Start/Stop toggle
        btnStartStop = Button(requireContext()).apply {
            text = getString(R.string.btn_start)
            textSize = 20f
            setTextColor(0xFFF7F6F2.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF01696F.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 180).apply {
                setMargins(0, 16, 0, 0)
            }
        }
        btnStartStop.setOnClickListener {
            serviceProvider?.getMidiService()?.togglePlayback()
        }
        root.addView(btnStartStop)

        return root
    }

    private fun getVel(cfg: VoiceConfig): Int = 
        if (cfg.mode == VoiceMode.HARMONY) cfg.harmonyConfig.masterVelocity else cfg.independentConfig.velocity

    private fun setVel(cfg: VoiceConfig, v: Int): VoiceConfig =
        if (cfg.mode == VoiceMode.HARMONY) cfg.copy(harmonyConfig = cfg.harmonyConfig.copy(masterVelocity = v))
        else cfg.copy(independentConfig = cfg.independentConfig.copy(velocity = v))

    private fun createVelControl(label: String, initial: Int, onProgress: (Int) -> Unit): Triple<TextView, SeekBar, View> {
        val labelResId = when (label) {
            "V1 VEL" -> R.string.label_v1_vel
            "V2 VEL" -> R.string.label_v2_vel
            "V3 VEL" -> R.string.label_v3_vel
            else -> 0
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tv = TextView(requireContext()).apply {
            text = if (labelResId != 0) getString(labelResId, initial) else "$label\n$initial"
            setTextColor(0xFFE8E6E1.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
        }
        val sb = SeekBar(requireContext()).apply {
            max = 126
            progress = initial - 1
            progressTintList = ColorStateList.valueOf(0xFF4F9AA5.toInt())
            thumbTintList = ColorStateList.valueOf(0xFF4F9AA5.toInt())
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (isUpdatingFromSync) return
                        val v = p + 1
                        tv.text = if (labelResId != 0) getString(labelResId, v) else "$label\n$v"
                        if (fromUser) onProgress(v)
                    }

                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                }
            )
        }
        layout.addView(tv)
        layout.addView(sb)
        return Triple(tv, sb, layout)
    }

    private fun createLinkToggle(label: String, initial: Boolean, onToggle: (Boolean) -> Unit) = CheckBox(requireContext()).apply {
        text = label
        isChecked = initial
        setTextColor(0xFF797876.toInt())
        textSize = 12f
        buttonTintList = ColorStateList.valueOf(0xFF4F9AA5.toInt())
        setPadding(16, 0, 16, 0)
        setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
    }

    private fun createWheelWithLock(label: String, origin: Int, isLocked: () -> Boolean, onLockToggle: (Boolean) -> Unit): Pair<View, View> {
        val frame = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val title = TextView(requireContext()).apply {
            text = label
            setTextColor(0xFF4F9AA5.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        val lockBtn = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_lock)
            background = null
            imageTintList = ColorStateList.valueOf(if (isLocked()) 0xFF8B0000.toInt() else 0xFF797876.toInt())
            setPadding(8, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(60, 60)
            setOnClickListener {
                val newState = !isLocked()
                onLockToggle(newState)
                imageTintList = ColorStateList.valueOf(if (newState) 0xFF8B0000.toInt() else 0xFF797876.toInt())
            }
        }

        header.addView(title)
        header.addView(lockBtn)
        frame.addView(header)

        val wheel = VerticalWheelView(requireContext()).apply {
            this.originValue = origin
            this.currentValue = origin
            this.lockProvider = isLocked
            layoutParams = LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(16, 8, 16, 8)
            }
        }
        frame.addView(wheel)

        return Pair(wheel, frame)
    }

    private class VerticalWheelView(context: Context) : View(context) {
        var originValue = 8192
        var currentValue = 8192
        var lockProvider: (() -> Boolean)? = null
        var onValueChange: ((Int) -> Unit)? = null
        
        private val maxVal = 16383
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val y = event.y.coerceIn(0f, height.toFloat())
                    val percent = 1f - (y / height.toFloat())
                    currentValue = (percent * maxVal).toInt()
                    onValueChange?.invoke(currentValue)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (lockProvider?.invoke() == false) {
                        currentValue = originValue
                        onValueChange?.invoke(currentValue)
                        invalidate()
                    }
                    performClick()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = 0xFF1C1B19.toInt()
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 12f, 12f, paint)
            
            paint.color = 0xFF4F9AA5.toInt()
            val handleHeight = 80f
            val y = height.toFloat() * (1f - (currentValue.toFloat() / maxVal.toFloat()))
            canvas.drawRoundRect(0f, (y - handleHeight).coerceAtLeast(0f), width.toFloat(), (y + handleHeight).coerceAtMost(height.toFloat()), 12f, 12f, paint)
            
            if (originValue == 8192) {
                paint.color = 0x40FFFFFF
                canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
            }
        }
    }

    private fun sendMidiToLinked(status: Int, val1: Int, val2: Int? = null) {
        val svc = serviceProvider?.getMidiService() ?: return
        
        fun send(ch: Int) {
            val msg = if (val2 == null) {
                val lsb = val1 and 0x7F
                val msb = (val1 ushr 7) and 0x7F
                byteArrayOf((status or ch).toByte(), lsb.toByte(), msb.toByte())
            } else {
                byteArrayOf((status or ch).toByte(), val1.toByte(), val2.toByte())
            }
            svc.sendRawMidi(msg)
        }

        if (v1Link) {
            if (currentV1.channel == 0) (0..15).forEach { send(it) } else send(currentV1.channel - 1)
        }
        if (v2Link) {
            val ch = if (currentV2.mode == VoiceMode.HARMONY) currentV2.harmonyConfig.midiChannel else currentV2.independentConfig.midiChannel
            if (ch == 0) (0..15).forEach { send(it) } else send(ch - 1)
        }
        if (v3Link) {
            val ch = if (currentV3.mode == VoiceMode.HARMONY) currentV3.harmonyConfig.midiChannel else currentV3.independentConfig.midiChannel
            if (ch == 0) (0..15).forEach { send(it) } else send(ch - 1)
        }
    }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {}

    override fun onStatusChanged(status: String) {}

    override fun onPlaybackStateChanged(playing: Boolean) {
        activity?.runOnUiThread {
            if (!::btnStartStop.isInitialized) return@runOnUiThread
            btnStartStop.text = if (playing) getString(R.string.btn_stop) else getString(R.string.btn_start)
            btnStartStop.backgroundTintList = ColorStateList.valueOf(
                if (playing) 0xFF8B0000.toInt() else 0xFF01696F.toInt()
            )
        }
    }

    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        syncFromService(v1, v2, v3)
    }

    fun syncFromService(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        currentV1 = v1
        currentV2 = v2
        currentV3 = v3
        
        activity?.runOnUiThread {
            if (!::sbV1.isInitialized) return@runOnUiThread
            isUpdatingFromSync = true
            
            sbV1.progress = v1.velocity - 1
            tvV1.text = getString(R.string.label_v1_vel, v1.velocity)

            val v2v = getVel(v2)
            sbV2.progress = v2v - 1
            tvV2.text = getString(R.string.label_v2_vel, v2v)

            val v3v = getVel(v3)
            sbV3.progress = v3v - 1
            tvV3.text = getString(R.string.label_v3_vel, v3v)

            isUpdatingFromSync = false
        }
    }
}
