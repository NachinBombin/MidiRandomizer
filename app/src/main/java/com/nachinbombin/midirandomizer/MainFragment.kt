package com.nachinbombin.midirandomizer

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.RangeSlider

class MainFragment : Fragment(), MidiService.MidiEventListener {

    private var serviceProvider: ServiceProvider? = null
    private var isUpdatingFromSync = false

    private lateinit var spinnerStyle: Spinner
    private lateinit var spinnerTiming: Spinner
    private lateinit var spinnerDroneTiming: Spinner
    private lateinit var spinnerScale: Spinner
    private lateinit var sbBpm: SeekBar
    private lateinit var tvBpm: TextView
    private lateinit var sbVelocity: SeekBar
    private lateinit var tvVelocity: TextView
    private lateinit var tvOctave: TextView
    private lateinit var rangeOctave: RangeSlider
    private lateinit var sbChannel: SeekBar
    private lateinit var tvChannel: TextView
    private lateinit var rowTiming: LinearLayout
    private lateinit var rowDroneTiming: LinearLayout
    private lateinit var rowDroneRange: LinearLayout
    private lateinit var rowBpm: LinearLayout
    private lateinit var sbDroneMin: SeekBar
    private lateinit var sbDroneMax: SeekBar
    private lateinit var tvDroneRange: TextView

    private val rootButtons = mutableListOf<RadioButton>()
    private var rootNoteGrid: LinearLayout? = null

    interface ServiceProvider {
        fun getMidiService(): MidiService?
    }

    fun onServiceReady() {
        val svc = serviceProvider?.getMidiService() ?: return
        if (!isAdded || view == null) return
        isUpdatingFromSync = true
        syncUIFromParams(svc.getV1Params())
        isUpdatingFromSync = false
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        serviceProvider = context as? ServiceProvider
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.addMidiListener(this)
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.removeMidiListener(this)
    }

    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {}
    override fun onStatusChanged(status: String) {}
    override fun onPlaybackStateChanged(playing: Boolean) {}

    override fun onVoiceParamsChanged(
        v1: MidiService.Voice1Params,
        v2: VoiceConfig,
        v3: VoiceConfig
    ) {
        if (!isAdded || view == null) return
        isUpdatingFromSync = true
        syncUIFromParams(v1)
        isUpdatingFromSync = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF111318.toInt())
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        root.addView(col)

        // BPM
        rowBpm = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        tvBpm = TextView(requireContext()).apply { text = "BPM: 120"; setTextColor(0xFFE8E6E1.toInt()) }
        sbBpm = SeekBar(requireContext()).apply { max = 480; progress = 100 }
        rowBpm.addView(tvBpm)
        rowBpm.addView(sbBpm)
        col.addView(rowBpm)

        // Velocity
        val velRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        tvVelocity = TextView(requireContext()).apply { text = "Velocity: 100"; setTextColor(0xFFE8E6E1.toInt()) }
        sbVelocity = SeekBar(requireContext()).apply { max = 127; progress = 100 }
        velRow.addView(tvVelocity)
        velRow.addView(sbVelocity)
        col.addView(velRow)

        // Octave RangeSlider
        val octRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        tvOctave = TextView(requireContext()).apply { text = "Octave: 3 \u2013 5"; setTextColor(0xFFE8E6E1.toInt()) }
        rangeOctave = RangeSlider(requireContext()).apply {
            valueFrom = 0f; valueTo = 8f; stepSize = 1f
            values = listOf(3f, 5f)
            setLabelFormatter { it.toInt().toString() }
        }
        rangeOctave.addOnChangeListener { slider, _, fromUser ->
            val lo = slider.values[0].toInt(); val hi = slider.values[1].toInt()
            tvOctave.text = if (lo == hi) "Octave: $lo" else "Octave: $lo \u2013 $hi"
            if (fromUser && !isUpdatingFromSync) pushParams()
        }
        octRow.addView(tvOctave)
        octRow.addView(rangeOctave)
        col.addView(octRow)

        // Channel
        val chRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        tvChannel = TextView(requireContext()).apply { text = "MIDI Channel: Omni"; setTextColor(0xFFE8E6E1.toInt()) }
        sbChannel = SeekBar(requireContext()).apply { max = 16; progress = 0 }
        chRow.addView(tvChannel)
        chRow.addView(sbChannel)
        col.addView(chRow)

        // Scale
        val scaleNames = listOf(
            "Chromatic", "Major", "Minor Natural", "Minor Harmonic",
            "Pentatonic Maj", "Pentatonic Min", "Blues", "Dorian", "Mixolydian", "Whole Tone",
            "Kurd (Annaziska)", "Celtic Minor (Amara)", "Pygmy",
            "SaBye / SaByeD", "Aegean (Lydian)", "Hijaz", "Akebono"
        )
        val scaleRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        scaleRow.addView(TextView(requireContext()).apply { text = "Scale: "; setTextColor(0xFFE8E6E1.toInt()) })
        spinnerScale = Spinner(requireContext())
        spinnerScale.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, scaleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        scaleRow.addView(spinnerScale)
        col.addView(scaleRow)

        // Root note grid
        val rootNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val rootLabel = TextView(requireContext()).apply { text = "Root Note:"; setTextColor(0xFF797876.toInt()); setPadding(0, 12, 0, 4) }
        col.addView(rootLabel)
        val gridOuter = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        rootNoteGrid = gridOuter
        for (rowStart in listOf(0, 6)) {
            val r = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in rowStart until minOf(rowStart + 6, rootNames.size)) {
                val rb = RadioButton(requireContext()).apply {
                    text  = rootNames[i]
                    tag   = i
                    setTextColor(0xFFE8E6E1.toInt())
                    setPadding(4, 4, 8, 4)
                }
                rootButtons.add(rb)
                r.addView(rb)
            }
            gridOuter.addView(r)
        }
        col.addView(gridOuter)

        // Style spinner
        val styleRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        styleRow.addView(TextView(requireContext()).apply { text = "Style: "; setTextColor(0xFFE8E6E1.toInt()) })
        spinnerStyle = Spinner(requireContext())
        spinnerStyle.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            listOf("Generative", "Single note Drone", "Evolving Drone"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        styleRow.addView(spinnerStyle)
        col.addView(styleRow)

        // Timing spinner
        rowTiming = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
        rowTiming.addView(TextView(requireContext()).apply { text = "Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        spinnerTiming = Spinner(requireContext())
        spinnerTiming.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
            listOf("Metronome", "Mixed", "Randomized", "Euclidean"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rowTiming.addView(spinnerTiming)
        col.addView(rowTiming)

        // Drone timing spinner
        rowDroneTiming = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0); visibility = View.GONE }
        rowDroneTiming.addView(TextView(requireContext()).apply { text = "Drone Timing: "; setTextColor(0xFFE8E6E1.toInt()) })
        spinnerDroneTiming = Spinner(requireContext())
        spinnerDroneTiming.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("Constant", "Random"))
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rowDroneTiming.addView(spinnerDroneTiming)
        col.addView(rowDroneTiming)

        // Drone beat range
        rowDroneRange = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 0); visibility = View.GONE }
        tvDroneRange = TextView(requireContext()).apply { text = "Drone beat range: 16 - 64"; setTextColor(0xFFE8E6E1.toInt()) }
        rowDroneRange.addView(tvDroneRange)
        sbDroneMin = SeekBar(requireContext()).apply { max = 127; progress = 15 }
        sbDroneMax = SeekBar(requireContext()).apply { max = 127; progress = 63 }
        fun updateDroneRangeText() {
            tvDroneRange.text = "Drone beat range: ${sbDroneMin.progress + 1} - ${sbDroneMax.progress + 1}"
        }
        sbDroneMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (sbDroneMax.progress < p) sbDroneMax.progress = p
                updateDroneRangeText()
                if (f && !isUpdatingFromSync) pushParams()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbDroneMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (sbDroneMin.progress > p) sbDroneMin.progress = p
                updateDroneRangeText()
                if (f && !isUpdatingFromSync) pushParams()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        rowDroneRange.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(requireContext()).apply { text = "Min: "; setTextColor(0xFF797876.toInt()) })
            addView(sbDroneMin, LinearLayout.LayoutParams(0, -2, 1f))
        })
        rowDroneRange.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(requireContext()).apply { text = "Max: "; setTextColor(0xFF797876.toInt()) })
            addView(sbDroneMax, LinearLayout.LayoutParams(0, -2, 1f))
        })
        col.addView(rowDroneRange)

        // Seekbar listeners
        sbBpm.setOnSeekBarChangeListener(seekListener { pushParams() })
        sbVelocity.setOnSeekBarChangeListener(seekListener { pushParams() })
        sbChannel.setOnSeekBarChangeListener(seekListener {
            tvChannel.text = if (sbChannel.progress == 0) "MIDI Channel: Omni" else "MIDI Channel: ${sbChannel.progress}"
            pushParams()
        })

        // Root buttons
        rootButtons.forEach { rb ->
            rb.setOnClickListener {
                if (!isUpdatingFromSync) {
                    selectRoot(rb.tag as Int)
                    pushParams()
                }
            }
        }

        // Style visibility
        spinnerStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                updateStyleVisibility()
                if (!isUpdatingFromSync) pushParams()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerTiming.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!isUpdatingFromSync) pushParams() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerScale.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!isUpdatingFromSync) pushParams() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerDroneTiming.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                updateStyleVisibility()
                if (!isUpdatingFromSync) pushParams()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        return root
    }

    private fun updateStyleVisibility() {
        val style = VoiceStyle.entries.getOrElse(spinnerStyle.selectedItemPosition) { VoiceStyle.GENERATIVE }
        val isSingleNote = style == VoiceStyle.SINGLE_NOTE_DRONE
        val isEvolving   = style == VoiceStyle.EVOLVING_DRONE

        rowBpm.visibility = if (isSingleNote) View.GONE else View.VISIBLE

        // Octave slider stays visible for Single Note Drone — user selects drone octave/range
        tvOctave.visibility   = View.VISIBLE
        rangeOctave.visibility = View.VISIBLE

        rowTiming.visibility      = if (isSingleNote || isEvolving) View.GONE else View.VISIBLE
        rowDroneTiming.visibility = if (isEvolving) View.VISIBLE else View.GONE
        rowDroneRange.visibility  = if (isEvolving && spinnerDroneTiming.selectedItemPosition == 1) View.VISIBLE else View.GONE
    }

    private fun selectRoot(index: Int) {
        rootButtons.forEachIndexed { i, rb -> rb.isChecked = (i == index) }
    }

    private fun pushParams() {
        if (isUpdatingFromSync) return
        val svc = serviceProvider?.getMidiService() ?: return
        val lo = rangeOctave.values[0].toInt()
        val hi = rangeOctave.values[1].toInt()
        val rootIdx = rootButtons.indexOfFirst { it.isChecked }
        val safeBpm = sbBpm.progress + 20
        svc.updateV1Parameters(
            MidiService.Voice1Params(
                bpm        = safeBpm,
                velocity   = sbVelocity.progress,
                minOctave  = lo,
                maxOctave  = hi,
                channel    = sbChannel.progress,
                scale      = spinnerScale.selectedItemPosition,
                rootNote   = if (rootIdx >= 0) rootIdx + 1 else 0,
                timingMode = spinnerTiming.selectedItemPosition,
                proSettings = svc.getV1Params().proSettings,
                style = VoiceStyle.entries.getOrElse(spinnerStyle.selectedItemPosition) { VoiceStyle.GENERATIVE },
                droneTiming = DroneTimingMode.entries.getOrElse(spinnerDroneTiming.selectedItemPosition) { DroneTimingMode.CONSTANT },
                droneMinBeats = sbDroneMin.progress + 1,
                droneMaxBeats = sbDroneMax.progress + 1
            )
        )
    }

    private fun syncUIFromParams(v1: MidiService.Voice1Params) {
        sbBpm.progress      = (v1.bpm - 20).coerceAtLeast(0)
        tvBpm.text          = "BPM: ${v1.bpm}"
        sbVelocity.progress = v1.velocity
        tvVelocity.text     = "Velocity: ${v1.velocity}"

        val lo = v1.minOctave.toFloat().coerceIn(0f, 8f)
        val hi = v1.maxOctave.toFloat().coerceIn(lo, 8f)
        rangeOctave.values = listOf(lo, hi)
        tvOctave.text = if (lo == hi) "Octave: ${lo.toInt()}" else "Octave: ${lo.toInt()} \u2013 ${hi.toInt()}"

        sbChannel.progress  = v1.channel
        tvChannel.text      = if (v1.channel == 0) "MIDI Channel: Omni" else "MIDI Channel: ${v1.channel}"
        spinnerScale.setSelection(v1.scale)

        // selectRoot MUST run inside the isUpdatingFromSync guard so RadioButton
        // listeners don’t escape and call push() with stale V1 params, which
        // would interrupt an active SINGLE_NOTE_DRONE or reset the noteLoop.
        if (v1.rootNote != 0) {
            selectRoot(v1.rootNote - 1)
        } else {
            selectRoot(-1)
        }

        spinnerStyle.setSelection(v1.style.ordinal)
        spinnerTiming.setSelection(v1.timingMode)
        spinnerDroneTiming.setSelection(v1.droneTiming.ordinal)
        sbDroneMin.progress = (v1.droneMinBeats - 1).coerceAtLeast(0)
        sbDroneMax.progress = (v1.droneMaxBeats - 1).coerceAtLeast(0)
        tvDroneRange.text   = "Drone beat range: ${v1.droneMinBeats} - ${v1.droneMaxBeats}"

        updateStyleVisibility()
    }

    private fun seekListener(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && !isUpdatingFromSync) onChange() }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
