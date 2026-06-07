package com.nachinbombin.midirandomizer

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Builds and shows a [BottomSheetDialog] (not a Fragment) so the
 * [onThemeSelected] lambda is never lost to fragment recreation.
 *
 * Usage:
 *   ThemePickerDialog.show(requireContext(), ThemeManager.loadTheme(requireContext())) { preset ->
 *       ThemeManager.saveTheme(requireContext(), preset)
 *       ThemeManager.applyToView(requireView(), preset)
 *   }
 */
object ThemePickerDialog {

    fun show(
        context: Context,
        current: ThemePreset,
        onSelected: (ThemePreset) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(current.bgElevated)
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 32))
        }

        root.addView(TextView(context).apply {
            text     = "Choose Theme"
            textSize = 16f
            setTextColor(current.textPrimary)
            setPadding(dp(context, 4), 0, 0, dp(context, 14))
        })

        root.addView(RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = Adapter(context, current.name) { preset ->
                onSelected(preset)
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    // ── RecyclerView Adapter ─────────────────────────────────────────

    private class Adapter(
        private val ctx: Context,
        private val selectedName: String,
        private val onClick: (ThemePreset) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        class VH(val card: View, val swatch: View, val label: TextView)
            : RecyclerView.ViewHolder(card)

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

            holder.swatch.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(p.bg, p.accent)
            ).apply { cornerRadius = dp(5).toFloat() }

            holder.label.text = p.name
            holder.label.setTextColor(if (isSelected) p.accent else p.textMuted)

            holder.card.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(p.bgElevated)
                setStroke(
                    dp(if (isSelected) 2 else 1),
                    if (isSelected) p.accent else p.borderSubtle
                )
            }
            holder.card.setOnClickListener { onClick(p) }
        }

        private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    }
}
