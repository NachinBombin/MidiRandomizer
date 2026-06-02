package com.nachinbombin.midirandomizer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(),
    MainFragment.MainFragmentHost,
    ProSettingsFragment.ProSettingsListener {

    private lateinit var midiManager: MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var midiService: MidiService? = null
    private var isBound = false

    private lateinit var mainFragment: MainFragment
    private lateinit var proFragment:  ProSettingsFragment

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MidiService.LocalBinder
            midiService = binder.getService()
            isBound = true
            mainFragment.onServiceReady()
            midiService?.updateProSettings(proFragment.let {
                ProSettings()  // defaults until user changes them
            })
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            midiService = null; isBound = false
        }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            mainHandler.post { mainFragment.refreshDeviceList() }
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            mainHandler.post { mainFragment.refreshDeviceList() }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startMidiService()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        midiManager = getSystemService(MIDI_SERVICE) as MidiManager

        mainFragment = MainFragment()
        proFragment  = ProSettingsFragment().also { it.setListener(this) }

        val pager   = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        pager.adapter = object : FragmentStateAdapter(this as FragmentActivity) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) mainFragment else proFragment
        }

        TabLayoutMediator(tabLayout, pager) { tab, pos ->
            tab.text = if (pos == 0) "Main" else "⚙ Pro"
        }.attach()

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
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // ── MainFragmentHost ──────────────────────────────────────────────────────

    override fun getMidiService(): MidiService? = midiService
    override fun getMidiManager(): MidiManager  = midiManager

    // ── ProSettingsListener ───────────────────────────────────────────────────

    override fun onProSettingsChanged(settings: ProSettings) {
        midiService?.updateProSettings(settings)
    }

    // ── Lifecycle cleanup ─────────────────────────────────────────────────────

    override fun onDestroy() {
        if (isBound) { unbindService(connection); isBound = false }
        midiManager.unregisterDeviceCallback(deviceCallback)
        super.onDestroy()
    }
}
