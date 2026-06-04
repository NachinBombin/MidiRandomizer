package com.nachinbombin.midirandomizer

import android.content.Context
import android.content.res.ColorStateList
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
 *
 * ## RootChip rule
 * RadioButtons whose tag is a numeric string ("0".."11") or "-1" are note-grid
 * chips using @drawable/root_chip_bg (a state-list drawable) and
 * @color/root_chip_text (a state-list color). The walker MUST NOT touch their
 * background or textColor — doing so collapses the state-list and makes every
 * chip appear permanently selected ("all lit").
 *
 * ## Spinner rule
 * Spinners inflate internal RadioButton/TextView children on some API levels.
 * The walker skips the interior of any AdapterView to avoid corrupting adapters
 * or inflated dropdown items.
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
            // ── SKIP: Lock buttons in PerformanceFragment manage their own tint ──
            view is ImageButton && view.tag == "lockBtn" -> return

            // ── SKIP: RootChip RadioButtons — numeric semitone tags ("0".."11")
            //    and the FREE chip tag "-1". These use @drawable/root_chip_bg
            //    (state-list) and @color/root_chip_text (state-list). Touching
            //    their background or textColor collapses the state and makes
            //    all chips appear permanently selected.
            view is RadioButton && isRootChipTag(view.tag) -> return

            // ── SKIP interior of Spinners / AdapterViews entirely.
            //    Spinners inflate internal child views on some API levels;
            //    walking into them corrupts the adapter and dropdown items.
            view is AdapterView<*> -> {
                // Paint the Spinner's own background but do NOT recurse.
                view.setBackgroundColor(p.bgElevated)
                return
            }

            // Theme selector button — accent text, subtle bg
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

            // RadioButtons (non-chip) — text color only, leave background alone
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

            // Status TextView
            view is TextView && view.id == R.id.tvStatus -> {
                view.setTextColor(p.accent)
            }

            // Section-header TextViews
            view is TextView && view.tag == "sectionHeader" -> {
                view.setTextColor(p.textMuted)
            }

            // Generic TextViews
            view is TextView -> {
                view.setTextColor(p.textPrimary)
            }

            // Elevated panels
            view is LinearLayout && view.tag == "panel" -> {
                view.setBackgroundColor(p.bgElevated)
            }

            // Dividers
            view is View && view.tag == "divider" -> {
                view.setBackgroundColor(p.borderSubtle)
            }

            // ListViews
            view is ListView -> {
                view.setBackgroundColor(p.bgElevated)
            }
        }

        // Recurse into children (AdapterView already returned above)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyNode(view.getChildAt(i), p, windowBg)
        }
    }

    /**
     * Returns true if [tag] identifies a RootChip RadioButton.
     * Valid tags: the strings "0".."11" (semitone index) and "-1" (FREE).
     */
    private fun isRootChipTag(tag: Any?): Boolean {
        if (tag !is String) return false
        val n = tag.toIntOrNull() ?: return false
        return n in -1..11
    }
}
