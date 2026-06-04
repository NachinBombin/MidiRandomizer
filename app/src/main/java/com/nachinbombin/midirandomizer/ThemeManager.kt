package com.nachinbombin.midirandomizer

import android.content.Context
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.google.android.material.slider.RangeSlider

/**
 * Handles persistence and runtime application of [ThemePreset].
 *
 * Call [applyToView] with the fragment's root view after selecting a theme.
 * It paints ONLY the scroll background, sliders, seekbars, the start/stop
 * button, and status/last-note text — intentionally skipping RadioButton
 * chips and inner ViewGroups so their drawable-based backgrounds are preserved.
 *
 * Usage:
 *   ThemeManager.saveTheme(context, ThemePreset.VAPORWAVE)
 *   ThemeManager.applyToView(requireView(), ThemeManager.loadTheme(context))
 */
object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME  = "selected_theme_name"

    fun saveTheme(context: Context, preset: ThemePreset) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, preset.name).apply()
    }

    fun loadTheme(context: Context): ThemePreset {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemePreset.DEFAULT.name)
        return ThemePreset.ALL.firstOrNull { it.name == name } ?: ThemePreset.DEFAULT
    }

    /**
     * Applies [preset] colors to the fragment view.
     * Only touches the scroll background, known-ID widgets, and sliders.
     * Does NOT recurse into ViewGroups generically to avoid clobbering
     * drawable-backed widgets (chips, radio buttons, spinners, etc.).
     */
    fun applyToView(root: View, preset: ThemePreset) {
        // 1. Page background (ScrollView)
        if (root is ScrollView) root.setBackgroundColor(preset.bg)

        // 2. Walk the full tree but only paint leaf widgets we know about
        applyLeaves(root, preset)
    }

    private fun applyLeaves(view: View, p: ThemePreset) {
        when {
            // ── Start/Stop button — keep its red/green state, only tint Theme btn
            view is Button && view.id == R.id.btnTheme -> {
                // Outlined button: just update stroke color to match accent
                view.setTextColor(p.accent)
            }

            // ── SeekBars (BPM, Velocity)
            view is SeekBar -> {
                view.progressTintList           = ColorStateList.valueOf(p.accent)
                view.thumbTintList              = ColorStateList.valueOf(p.accent)
                view.progressBackgroundTintList = ColorStateList.valueOf(p.borderSubtle)
            }

            // ── RangeSliders (Octave, DroneBeats)
            view is RangeSlider -> {
                view.thumbTintList         = ColorStateList.valueOf(p.accent)
                view.trackActiveTintList   = ColorStateList.valueOf(p.accent)
                view.trackInactiveTintList = ColorStateList.valueOf(p.borderSubtle)
            }

            // ── Status / last note TextViews — keep their semantic colors
            view is TextView && view.id == R.id.tvStatus -> {
                view.setTextColor(p.accent)
            }

            // ── Don't touch RadioButtons, RadioGroups, Spinners, ListViews,
            //    other TextViews, or generic ViewGroups — they have their own
            //    drawable backgrounds or hard-coded semantic colors.
        }

        // Recurse into children
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyLeaves(view.getChildAt(i), p)
        }
    }
}
