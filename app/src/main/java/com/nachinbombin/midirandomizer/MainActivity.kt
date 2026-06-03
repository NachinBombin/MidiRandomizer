package com.nachinbombin.midirandomizer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(),
    MidiService.MidiEventListener,
    MainFragment.MainFragmentHost,
    ProSettingsFragment.ProSettingsListener,
    VoicesFragment.ServiceProvider {

    private lateinit var midiManager: MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var midiService: MidiService? = null
    private var bound = false

    private val fragmentListeners = mutableSetOf<MidiService.MidiEventListener>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as MidiService.LocalBinder).getService()
            midiService = svc
            svc.setListener(this@MainActivity)
            bound = true
            
            mainHandler.post {
                val v1 = svc.getV1Params()
                val v2 = svc.getV2Config()
                val v3 = svc.getV3Config()
                val playing = svc.isMidiPlaying()
                
                fragmentListeners.forEach { 
                    it.onPlaybackStateChanged(playing)
                    it.onVoiceParamsChanged(v1, v2, v3)
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            midiService = null; bound = false
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            mainHandler.post { 
                fragmentListeners.filterIsInstance<MainFragment>().forEach { it.refreshDeviceList() }
            }
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            mainHandler.post { 
                fragmentListeners.filterIsInstance<MainFragment>().forEach { it.refreshDeviceList() }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startMidiService()
    }

    // ── Fragment Registration ────────────────────────────────────────────────
    fun addMidiListener(l: MidiService.MidiEventListener) {
        fragmentListeners.add(l)
        midiService?.let {
            l.onPlaybackStateChanged(it.isMidiPlaying())
            l.onVoiceParamsChanged(it.getV1Params(), it.getV2Config(), it.getV3Config())
        }
    }

    fun removeMidiListener(l: MidiService.MidiEventListener) {
        fragmentListeners.remove(l)
    }

    // ── Interfaces ────────────────────────────────────────────────────────────
    override fun getMidiService(): MidiService? = midiService
    override fun getMidiManager(): MidiManager? = if (::midiManager.isInitialized) midiManager else null

    override fun onProSettingsChanged(settings: ProSettings) {
        midiService?.updateProSettings(settings)
    }

    // ── Activity lifecycle ────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        midiManager = getSystemService(MIDI_SERVICE) as MidiManager

        val tabs   = TabLayout(this).apply { 
            id = View.generateViewId()
            setBackgroundColor(0xFF1C1B19.toInt())
            setTabTextColors(0xFF797876.toInt(), 0xFF4F9AA5.toInt())
            setSelectedTabIndicatorColor(0xFF4F9AA5.toInt())
        }
        val pager  = ViewPager2(this).apply { id = View.generateViewId() }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabs)
            addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            setBackgroundColor(0xFF111318.toInt())
        }
        setContentView(layout)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0    -> MainFragment()
                1    -> VoicesFragment()
                2    -> ProSettingsFragment()
                3    -> PerformanceFragment()
                else -> MainFragment()
            }
        }
        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "▶  Main"
                1 -> "🎵  Voices"
                2 -> "⚙  Pro"
                3 -> "🎛  Perform"
                else -> ""
            }
        }.attach()

        @Suppress("DEPRECATION")
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        checkPermissionsAndStartService()
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startMidiService()
    }

    private fun startMidiService() {
        val intent = Intent(this, MidiService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (bound) { unbindService(connection); bound = false }
        if (::midiManager.isInitialized) midiManager.unregisterDeviceCallback(deviceCallback)
        super.onDestroy()
    }

    // ── MidiEventListener (Broadcast to Registered Fragments) ─────────────────
    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {
        mainHandler.post {
            fragmentListeners.forEach { it.onNotePlayed(noteName, midiNote, velocity) }
        }
    }
    override fun onStatusChanged(status: String) {
        mainHandler.post {
            fragmentListeners.forEach { it.onStatusChanged(status) }
        }
    }
    override fun onPlaybackStateChanged(playing: Boolean) {
        mainHandler.post {
            fragmentListeners.forEach { it.onPlaybackStateChanged(playing) }
        }
    }
    override fun onVoiceParamsChanged(v1: MidiService.Voice1Params, v2: VoiceConfig, v3: VoiceConfig) {
        mainHandler.post {
            fragmentListeners.forEach { it.onVoiceParamsChanged(v1, v2, v3) }
        }
    }
}
