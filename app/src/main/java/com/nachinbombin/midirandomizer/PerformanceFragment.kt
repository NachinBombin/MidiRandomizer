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
    private var modLocked   = false

    private var pitchLockBtn: ImageButton? = null
    private var modLockBtn:   ImageButton? = null

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

    override fun onStart() { super.onStart(); (activity as? MainActivity)?.addMidiListener(this) }
    override fun onStop()  { super.onStop();  (activity as? MainActivity)?.removeMidiListener(this) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val preset = ThemeManager.loadTheme(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            setBackgroundColor(preset.bgVoices)
        }

        // 1. Velocity row
        val velRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 16) }
        }
        val v1Ctl = createVelControl(getString(R.string.label_v1_vel, currentV1.velocity), currentV1.velocity) { p ->
            currentV1 = currentV1.copy(velocity = p)
            serviceProvider?.getMidiService()?.updateV1Parameters(currentV1)
        }
        sbV1 = v1Ctl.second; sbV1.max = 126; sbV1.progress = currentV1.velocity - 1; tvV1 = v1Ctl.first
        velRow.addView(v1Ctl.third)

        // IndependentConfig.velocity is used for V2/V3 velocity in Perform view
        val v2Vel = currentV2.independentConfig.velocity
        val v2Ctl = createVelControl(getString(R.string.label_v2_vel, v2Vel), v2Vel) { p ->
            currentV2 = currentV2.copy(independentConfig = currentV2.independentConfig.copy(velocity = p))
            serviceProvider?.getMidiService()?.updateVoice2Config(currentV2)
        }
        sbV2 = v2Ctl.second; tvV2 = v2Ctl.first
        velRow.addView(v2Ctl.third)

        val v3Vel = currentV3.independentConfig.velocity
        val v3Ctl = createVelControl(getString(R.string.label_v3_vel, v3Vel), v3Vel) { p ->
            currentV3 = currentV3.copy(independentConfig = currentV3.independentConfig.copy(velocity = p))
            serviceProvider?.getMidiService()?.updateVoice3Config(currentV3)
        }
        sbV3 = v3Ctl.second; tvV3 = v3Ctl.first
        velRow.addView(v3Ctl.third)
        root.addView(velRow)

        // 2. Link toggles
        val linkRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 16) }
        }
        linkRow.addView(createLinkToggle("Link V1", v1Link, preset) { v1Link = it })
        linkRow.addView(createLinkToggle("Link V2", v2Link, preset) { v2Link = it })
        linkRow.addView(createLinkToggle("Link V3", v3Link, preset) { v3Link = it })
        root.addView(linkRow)

        // 3. Wheels
        val wheelsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val (pWheel, pLock) = createWheelWithLock("PITCH BEND", 8192, preset, { pitchLocked }) { pitchLocked = it }
        pitchLockBtn = pLock
        pWheel.onValueChange = { value -> sendMidiToLinked(0xE0, value) }

        val (mWheel, mLock) = createWheelWithLock("MOD (CC 1)", 0, preset, { modLocked }) { modLocked = it }
        modLockBtn = mLock
        mWheel.onValueChange = { value -> sendMidiToLinked(0xB0, 1, value) }

        val pitchContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        pitchContainer.addView(buildWheelTitle("PITCH BEND", preset))
        pitchContainer.addView(pLock)
        pitchContainer.addView(pWheel)

        val modContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        modContainer.addView(buildWheelTitle("MOD (CC 1)", preset))
        modContainer.addView(mLock)
        modContainer.addView(mWheel)

        wheelsLayout.addView(pitchContainer)
        wheelsLayout.addView(modContainer)
        root.addView(wheelsLayout)

        // 4. Start/Stop
        btnStartStop = Button(requireContext()).apply {
            text = getString(R.string.btn_start)
            backgroundTintList = ColorStateList.valueOf(0xFF01696F.toInt())
            setTextColor(0xFFF7F6F2.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 16, 0, 0) }
            id = R.id.btnStartStop
            setOnClickListener { serviceProvider?.getMidiService()?.togglePlayback() }
        }
        root.addView(btnStartStop)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preset = ThemeManager.loadTheme(requireContext())
        ThemeManager.applyToView(view, preset, forVoices = true)
        restoreLockTints()
    }

    private fun restoreLockTints() {
        val lockedColor   = 0xFF8B0000.toInt()
        val unlockedColor = 0xFF797876.toInt()
        pitchLockBtn?.imageTintList = ColorStateList.valueOf(if (pitchLocked) lockedColor else unlockedColor)
        modLockBtn?.imageTintList   = ColorStateList.valueOf(if (modLocked)   lockedColor else unlockedColor)
    }

    // ── Wheel helpers ─────────────────────────────────────────────────────

    private fun buildWheelTitle(label: String, preset: ThemePreset) = TextView(requireContext()).apply {
        text = label; setTextColor(preset.accent); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 8, 0, 4) }
    }

    private fun createWheelWithLock(
        label: String, origin: Int, preset: ThemePreset,
        isLocked: () -> Boolean, onLockToggle: (Boolean) -> Unit
    ): Pair<VerticalWheelView, ImageButton> {
        val lockedColor   = 0xFF8B0000.toInt()
        val unlockedColor = 0xFF797876.toInt()

        val lockBtn = ImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_lock)
            background = null
            tag = "lockBtn"
            imageTintList = ColorStateList.valueOf(if (isLocked()) lockedColor else unlockedColor)
            setPadding(8, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(60, 60).apply { gravity = Gravity.CENTER_HORIZONTAL }
            setOnClickListener {
                val newState = !isLocked()
                onLockToggle(newState)
                imageTintList = ColorStateList.valueOf(if (newState) lockedColor else unlockedColor)
            }
        }
        val wheel = VerticalWheelView(requireContext()).apply {
            this.originValue  = origin
            this.currentValue = origin
            this.lockProvider = isLocked
            this.accentColor  = preset.accent
            this.surfaceColor = preset.bgElevated
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { setMargins(16, 8, 16, 8) }
        }
        return Pair(wheel, lockBtn)
    }

    // ── Velocity control ──────────────────────────────────────────────────

    private fun createVelControl(
        labelText: String, initialVel: Int, onProgress: (Int) -> Unit
    ): Triple<TextView, SeekBar, LinearLayout> {
        val ctx    = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8, 0, 8, 0)
        }
        val tv = TextView(ctx).apply {
            text = labelText; setTextColor(0xFFCDCCCA.toInt()); textSize = 12f; gravity = Gravity.CENTER
        }
        val sb = SeekBar(ctx).apply {
            max = 126; progress = initialVel - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (isUpdatingFromSync) return
                    if (fromUser) onProgress(p + 1)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(tv); layout.addView(sb)
        return Triple(tv, sb, layout)
    }

    private fun createLinkToggle(label: String, initial: Boolean, preset: ThemePreset, onToggle: (Boolean) -> Unit) =
        CheckBox(requireContext()).apply {
            text = label; isChecked = initial
            setTextColor(preset.textMuted); textSize = 12f
            buttonTintList = ColorStateList.valueOf(preset.accent)
            setPadding(16, 0, 16, 0)
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

    // ── MIDI send ─────────────────────────────────────────────────────────

    private fun sendMidiToLinked(status: Int, val1: Int, val2: Int? = null) {
        val svc = serviceProvider?.getMidiService() ?: return
        fun send(ch: Int) {
            val msg = if (val2 == null) {
                val lsb = val1 and 0x7F; val msb = (val1 ushr 7) and 0x7F
                byteArrayOf((status or ch).toByte(), lsb.toByte(), msb.toByte())
            } else {
                byteArrayOf((status or ch).toByte(), val1.toByte(), val2.toByte())
            }
            svc.sendRawMidi(msg)
        }
        if (v1Link) { if (currentV1.channel == 0) (0..15).forEach { send(it) } else send(currentV1.channel - 1) }
        if (v2Link) {
            val ch = if (currentV2.mode == VoiceMode.HARMONY) currentV2.harmonyConfig.midiChannel else currentV2.independentConfig.midiChannel
            if (ch == 0) (0..15).forEach { send(it) } else send(ch - 1)
        }
        if (v3Link) {
            val ch = if (currentV3.mode == VoiceMode.HARMONY) currentV3.harmonyConfig.midiChannel else currentV3.independentConfig.midiChannel
            if (ch == 0) (0..15).forEach { send(it) } else send(ch - 1)
        }
    }

    // ── MidiEventListener ─────────────────────────────────────────────────

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
        currentV1 = v1; currentV2 = v2; currentV3 = v3
        activity?.runOnUiThread {
            if (!::sbV1.isInitialized) return@runOnUiThread
            isUpdatingFromSync = true
            sbV1.progress = v1.velocity - 1
            tvV1.text = getString(R.string.label_v1_vel, v1.velocity)
            val v2v = currentV2.independentConfig.velocity
            sbV2.progress = v2v - 1; tvV2.text = getString(R.string.label_v2_vel, v2v)
            val v3v = currentV3.independentConfig.velocity
            sbV3.progress = v3v - 1; tvV3.text = getString(R.string.label_v3_vel, v3v)
            isUpdatingFromSync = false
        }
    }

    // ── VerticalWheelView ─────────────────────────────────────────────────

    inner class VerticalWheelView(context: Context) : View(context) {
        var originValue  = 8192
        var currentValue = 8192
        var lockProvider: (() -> Boolean)? = null
        var onValueChange: ((Int) -> Unit)? = null
        var accentColor  = 0xFF4F9AA5.toInt()
        var surfaceColor = 0xFF1C1B19.toInt()

        private val maxVal = 16383
        private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val y = event.y.coerceIn(0f, height.toFloat())
                    currentValue = ((1f - y / height.toFloat()) * maxVal).toInt()
                    onValueChange?.invoke(currentValue); invalidate(); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (lockProvider?.invoke() == false) {
                        currentValue = originValue; onValueChange?.invoke(currentValue); invalidate()
                    }
                    performClick(); return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onDraw(canvas: Canvas) {
            paint.color = surfaceColor
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 12f, 12f, paint)
            paint.color = accentColor
            val handleH = 80f
            val y = height.toFloat() * (1f - currentValue.toFloat() / maxVal.toFloat())
            canvas.drawRoundRect(0f, (y - handleH).coerceAtLeast(0f), width.toFloat(), (y + handleH).coerceAtMost(height.toFloat()), 12f, 12f, paint)
            if (originValue == 8192) {
                paint.color = 0x40FFFFFF; canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
            }
        }
    }
}
