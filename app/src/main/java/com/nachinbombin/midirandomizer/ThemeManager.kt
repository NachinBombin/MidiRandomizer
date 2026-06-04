package com.nachinbombin.midirandomizer

import android.content.Context
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import com.google.android.material.slider.RangeSlider

/**
 * Handles persistence and runtime application of [ThemePreset].
 *
 * ── Saving / Loading ────────────────────────────────────────────────────────
 *   ThemeManager.saveTheme(context, ThemePreset.VAPORWAVE)
 *   val preset = ThemeManager.loadTheme(context)
 *
 * ── Applying to a View tree ──────────────────────────────────────────────────
 *   Override onViewCreated or onResume and call:
 *
 *   val preset = ThemeManager.loadTheme(requireContext())
 *   ThemeManager.applyToView(requireView(), preset)
 *
 * ── Wiring the picker button ─────────────────────────────────────────────────
 *   binding.btnTheme.setOnClickListener {
 *       ThemePickerDialog.newInstance().apply {
 *           onThemeSelected = { preset ->
 *               ThemeManager.saveTheme(requireContext(), preset)
 *               ThemeManager.applyToView(requireView(), preset)
 *           }
 *       }.show(childFragmentManager, "theme_picker")
 *   }
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
     * Recursively walks [root] and re-tints every known widget type
     * using the provided [preset] color tokens.
     */
    fun applyToView(root: View, preset: ThemePreset) {
        applyRecursive(root, preset)
    }

    private fun applyRecursive(view: View, p: ThemePreset) {
        when (view) {
            is Button -> {
                view.backgroundTintList = ColorStateList.valueOf(p.accent)
                view.setTextColor(p.textPrimary)
            }
            is RangeSlider -> {
                view.thumbTintList         = ColorStateList.valueOf(p.accent)
                view.trackActiveTintList   = ColorStateList.valueOf(p.accent)
                view.trackInactiveTintList = ColorStateList.valueOf(p.borderSubtle)
            }
            is SeekBar -> {
                view.progressTintList           = ColorStateList.valueOf(p.accent)
                view.thumbTintList              = ColorStateList.valueOf(p.accent)
                view.progressBackgroundTintList = ColorStateList.valueOf(p.borderSubtle)
            }
            is ListView -> {
                view.setBackgroundColor(p.bgElevated)
            }
            is ViewGroup -> {
                view.setBackgroundColor(p.bg)
                for (i in 0 until view.childCount) applyRecursive(view.getChildAt(i), p)
                return // children already visited above
            }
            is TextView -> {
                view.setTextColor(p.textPrimary)
            }
        }
        // Walk children for non-ViewGroup compound widgets
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyRecursive(view.getChildAt(i), p)
        }
    }
}
