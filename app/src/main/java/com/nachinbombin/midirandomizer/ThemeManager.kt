package com.nachinbombin.midirandomizer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.*
import android.view.View
import android.view.ViewGroup
import com.google.android.material.slider.RangeSlider

/**
 * Handles persistence and runtime application of [ThemePreset].
 *
 * ## Window rules
 * | Window      | Background token | Call                              |
 * |-------------|-----------------|-----------------------------------|
 * | Main        | [ThemePreset.bg]        | applyToView(root, p)              |
 * | Pro         | [ThemePreset.bg]        | applyToView(root, p)              |
 * | Voices      | [ThemePreset.bgVoices]  | applyToView(root, p, voices=true) |
 * | Performance | [ThemePreset.bgVoices]  | applyToView(root, p, voices=true) |
 *
 * ## Lock button rule
 * Lock icons in PerformanceFragment are ALWAYS red when locked, regardless of theme.
 * applyToView intentionally skips ImageButtons tagged "lockBtn" — PerformanceFragment
 * manages their tint itself.
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
     * Applies [preset] to every eligible widget in [root]'s tree.
     *
     * @param root        The fragment's root view.
     * @param preset      The theme to apply.
     * @param forVoices   true → use [ThemePreset.bgVoices] as the window background
     *                    (Voices and Performance windows).
     */
    fun applyToView(root: View, preset: ThemePreset, forVoices: Boolean = false) {
        val windowBg = if (forVoices) preset.bgVoices else preset.bg
        root.setBackgroundColor(windowBg)
        applyNode(root, preset, windowBg)
    }

    // ── Recursive node painter ────────────────────────────────────────────

    private fun applyNode(view: View, p: ThemePreset, windowBg: Int) {
        when {
            // Lock buttons in PerformanceFragment — SKIP, they manage their own tint
            view is ImageButton && view.tag == "lockBtn" -> return

            // Theme selector button — accent text, transparent bg
            view is Button && view.id == R.id.btnTheme -> {
                view.setTextColor(p.accent)
                view.backgroundTintList = ColorStateList.valueOf(p.borderSubtle)
            }

            // Start/Stop button — managed by playback state; skip
            view is Button && view.id == R.id.btnStartStop -> { /* leave alone */ }

            // All other Buttons
            view is Button -> {
                view.backgroundTintList = ColorStateList.valueOf(p.accentSoft)
                view.setTextColor(p.textPrimary)
            }

            // SeekBars
            view is SeekBar -> {
                view.progressTintList           = ColorStateList.valueOf(p.accent)
                view.thumbTintList              = ColorStateList.valueOf(p.accent)
                view.progressBackgroundTintList = ColorStateList.valueOf(p.borderSubtle)
            }

            // RangeSliders (Material)
            view is RangeSlider -> {
                view.thumbTintList         = ColorStateList.valueOf(p.accent)
                view.trackActiveTintList   = ColorStateList.valueOf(p.accent)
                view.trackInactiveTintList = ColorStateList.valueOf(p.borderSubtle)
                view.haloTintList          = ColorStateList.valueOf(p.accentSoft)
            }

            // Switches
            view is Switch -> {
                view.thumbTintList  = ColorStateList.valueOf(p.accent)
                view.trackTintList  = ColorStateList.valueOf(p.accentSoft)
                view.setTextColor(p.textPrimary)
            }

            // CheckBoxes (Link toggles in PerformanceFragment)
            view is CheckBox -> {
                view.buttonTintList = ColorStateList.valueOf(p.accent)
                view.setTextColor(p.textMuted)
            }

            // RadioButtons — used in root-note chip rows and timing rows
            // Leave background drawable untouched; only recolor text
            view is RadioButton -> {
                view.setTextColor(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(p.textPrimary, p.textMuted)
                    )
                )
            }

            // Status / last-note TextViews
            view is TextView && view.id == R.id.tvStatus -> {
                view.setTextColor(p.accent)
            }

            // Section-header TextViews (all-caps, muted)
            view is TextView && view.tag == "sectionHeader" -> {
                view.setTextColor(p.textMuted)
            }

            // Generic TextViews — primary text, transparent background
            view is TextView -> {
                view.setTextColor(p.textPrimary)
            }

            // Elevated panels (cards, inner LinearLayouts tagged "panel")
            view is LinearLayout && view.tag == "panel" -> {
                view.setBackgroundColor(p.bgElevated)
            }

            // Dividers tagged "divider"
            view is View && view.tag == "divider" -> {
                view.setBackgroundColor(p.borderSubtle)
            }

            // ListViews
            view is ListView -> {
                view.setBackgroundColor(p.bgElevated)
            }
        }

        // Recurse into children
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyNode(view.getChildAt(i), p, windowBg)
        }
    }
}
