package com.nachinbombin.midirandomizer

import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(),
    MidiService.MidiEventListener,
    VoicesFragment.ServiceProvider {

    private var midiService: MidiService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as MidiService.LocalBinder).getService()
            midiService = svc
            svc.setListener(this@MainActivity)
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            midiService = null; bound = false
        }
    }

    // ── ServiceProvider impl ──────────────────────────────────────────────────
    override fun getMidiService(): MidiService? = midiService

    // ── Activity lifecycle ────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tabs   = TabLayout(this)
        val pager  = ViewPager2(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabs)
            addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(layout)

        pager.adapter = PagerAdapter(this)
        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "▶  Main"
                1 -> "⚙  Pro"
                2 -> "🎵  Voices"
                else -> ""
            }
        }.attach()

        val intent = Intent(this, MidiService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (bound) { unbindService(connection); bound = false }
        super.onDestroy()
    }

    // ── MidiEventListener ─────────────────────────────────────────────────────
    override fun onNotePlayed(noteName: String, midiNote: Int, velocity: Int) {}
    override fun onStatusChanged(status: String) {}
    override fun onPlaybackStateChanged(playing: Boolean) {}

    // ── Pager adapter ─────────────────────────────────────────────────────────
    private class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0    -> MainFragment()
            1    -> ProSettingsFragment()
            else -> VoicesFragment()
        }
    }
}
