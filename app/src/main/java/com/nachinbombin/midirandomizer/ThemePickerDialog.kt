package com.nachinbombin.midirandomizer

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheetDialogFragment that displays all [ThemePreset] options
 * as color-swatch cards in a 2-column grid.
 *
 * Usage:
 *
 *   ThemePickerDialog.newInstance().apply {
 *       onThemeSelected = { preset ->
 *           ThemeManager.saveTheme(requireContext(), preset)
 *           ThemeManager.applyToView(requireView(), preset)
 *       }
 *   }.show(childFragmentManager, "theme_picker")
 */
class ThemePickerDialog : BottomSheetDialogFragment() {

    /** Called with the chosen [ThemePreset] when the user taps a card. */
    var onThemeSelected: ((ThemePreset) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx     = requireContext()
        val current = ThemeManager.loadTheme(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(current.bgElevated)
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // Sheet title
        root.addView(TextView(ctx).apply {
            text     = "Choose Theme"
            textSize = 16f
            setTextColor(current.textPrimary)
            setPadding(dp(4), 0, 0, dp(14))
        })

        // Preset grid
        root.addView(RecyclerView(ctx).apply {
            layoutManager = GridLayoutManager(ctx, 2)
            adapter = Adapter(ctx, current.name) { preset ->
                onThemeSelected?.invoke(preset)
                dismiss()
            }
        })

        return root
    }

    private fun dp(v: Int) = (v * requireContext().resources.displayMetrics.density).toInt()

    companion object {
        fun newInstance() = ThemePickerDialog()
    }

    // ── RecyclerView Adapter ────────────────────────────────────────────────

    private class Adapter(
        private val ctx: Context,
        private val selectedName: String,   // name of the currently active preset
        private val onClick: (ThemePreset) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(
            val card:  View,
            val swatch: View,
            val label: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun getItemCount() = ThemePreset.ALL.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) }
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }

            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(28)
                ).also { it.bottomMargin = dp(8) }
            }

            val label = TextView(ctx).apply { textSize = 13f }

            card.addView(swatch)
            card.addView(label)
            return VH(card, swatch, label)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p          = ThemePreset.ALL[position]
            val isSelected = p.name == selectedName

            // Gradient swatch: bg → accent
            holder.swatch.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(p.bg, p.accent)
            ).apply { cornerRadius = dp(5).toFloat() }

            // Label — use accent colour when this card is the active theme
            holder.label.text = p.name
            holder.label.setTextColor(if (isSelected) p.accent else p.textMuted)

            // Card border — thicker + accent colour when selected, subtle otherwise
            holder.card.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(p.bgElevated)
                setStroke(
                    dp(if (isSelected) 2 else 1),
                    if (isSelected) p.accent else p.borderSubtle  // fix: p.borderSubtle
                )
            }

            holder.card.setOnClickListener { onClick(p) }
        }

        private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    }
}
