package com.nachinbombin.midirandomizer

import android.graphics.Color

/**
 * Represents an Opera GX-inspired color theme.
 *
 * Token roles:
 *   [bg]            App background (deepest layer).
 *   [bgElevated]    Cards, panels, ListView backgrounds.
 *   [accent]        Primary accent: buttons, sliders, active tab borders, Start/Stop tint.
 *   [accentSoft]    Secondary accent: hover fills, chip selected state, RangeSlider track active.
 *   [accentAlt]     Tertiary accent: small highlights, success badges, link color.
 *   [borderSubtle]  Low-contrast separators and RangeSlider track inactive.
 *   [textPrimary]   Main label / body text color.
 *   [textMuted]     Secondary labels (BPM value, section headers).
 */
data class ThemePreset(
    val name: String,
    val bg: Int,
    val bgElevated: Int,
    val accent: Int,
    val accentSoft: Int,
    val accentAlt: Int,
    val borderSubtle: Int,
    val textPrimary: Int,
    val textMuted: Int
) {
    companion object {

        /** Original app theme — kept 100% untouched. Always the first entry. */
        val DEFAULT = ThemePreset(
            name         = "Default",
            bg           = Color.parseColor("#171614"),
            bgElevated   = Color.parseColor("#1C1B19"),
            accent       = Color.parseColor("#4F9AA5"),
            accentSoft   = Color.parseColor("#01696F"),
            accentAlt    = Color.parseColor("#6DAA45"),
            borderSubtle = Color.parseColor("#393836"),
            textPrimary  = Color.parseColor("#CDCCCA"),
            textMuted    = Color.parseColor("#7A7974")
        )

        // ── Opera GX themes ──────────────────────────────────────────────────

        /**
         * VAPORWAVE
         * Dark near-black blue base. Neon pink primary, cyan secondary, mint tertiary.
         * Use horizontal/diagonal gradients for top bars.
         * Soft outer glow on interactive elements (blurred box-shadow equivalent).
         */
        val VAPORWAVE = ThemePreset(
            name         = "Vaporwave",
            bg           = Color.parseColor("#050813"),
            bgElevated   = Color.parseColor("#11152A"),
            accent       = Color.parseColor("#FF71CE"),
            accentSoft   = Color.parseColor("#01CDFE"),
            accentAlt    = Color.parseColor("#05FFA1"),
            borderSubtle = Color.parseColor("#282B45"),
            textPrimary  = Color.parseColor("#F5F3FF"),
            textMuted    = Color.parseColor("#A4A3CF")
        )

        /**
         * PAY TO WIN
         * Deep neutral violet-black base. GX red primary, warm gold secondary, lighter gold tertiary.
         * Red for primary CTAs. Gold gradient top border on "premium" cards.
         */
        val PAY_TO_WIN = ThemePreset(
            name         = "Pay To Win",
            bg           = Color.parseColor("#070713"),
            bgElevated   = Color.parseColor("#121222"),
            accent       = Color.parseColor("#FA1E4E"),
            accentSoft   = Color.parseColor("#FF9B4A"),
            accentAlt    = Color.parseColor("#FFDD6B"),
            borderSubtle = Color.parseColor("#26263B"),
            textPrimary  = Color.parseColor("#F8F5FF"),
            textMuted    = Color.parseColor("#A29FBF")
        )

        /**
         * FRUTTI DI MARE
         * Deep teal-black base. Teal primary, bright aqua secondary, sea-blue tertiary.
         * Glassy panels with thin aqua borders. Avoid heavy neon glows.
         */
        val FRUTTI_DI_MARE = ThemePreset(
            name         = "Frutti di Mare",
            bg           = Color.parseColor("#031017"),
            bgElevated   = Color.parseColor("#081C25"),
            accent       = Color.parseColor("#1FB2AA"),
            accentSoft   = Color.parseColor("#41E3C1"),
            accentAlt    = Color.parseColor("#3A7FFF"),
            borderSubtle = Color.parseColor("#16313D"),
            textPrimary  = Color.parseColor("#F0F7FF"),
            textMuted    = Color.parseColor("#9EB8C7")
        )

        /**
         * LAMBDA
         * Neutral black base. Industrial orange primary, softer orange secondary, sci-fi blue alt.
         * Sharp edges, 1px borders, minimal glows.
         * Angled accent strip on major headers (left border in accent color).
         */
        val LAMBDA = ThemePreset(
            name         = "Lambda",
            bg           = Color.parseColor("#050608"),
            bgElevated   = Color.parseColor("#101215"),
            accent       = Color.parseColor("#FF9100"),
            accentSoft   = Color.parseColor("#FFB547"),
            accentAlt    = Color.parseColor("#00B8FF"),
            borderSubtle = Color.parseColor("#24262B"),
            textPrimary  = Color.parseColor("#F5F5F7"),
            textMuted    = Color.parseColor("#9B9DA4")
        )

        /**
         * ULTRA VIOLET
         * Very dark indigo base. Saturated violet primary, deeper violet for hovers, cyan for focus rings.
         * Strong purple glows around active/focus states.
         * Reserve cyan for small highlights only — do not flood surfaces.
         */
        val ULTRA_VIOLET = ThemePreset(
            name         = "Ultra Violet",
            bg           = Color.parseColor("#060513"),
            bgElevated   = Color.parseColor("#120F26"),
            accent       = Color.parseColor("#A855FF"),
            accentSoft   = Color.parseColor("#7C3AED"),
            accentAlt    = Color.parseColor("#22D3EE"),
            borderSubtle = Color.parseColor("#26233D"),
            textPrimary  = Color.parseColor("#F8F5FF"),
            textMuted    = Color.parseColor("#A5A1D5")
        )

        /**
         * AFTER EIGHT
         * Almost-black with green hint base. Bright mint primary, darker mint secondary, ice-blue alt.
         * Chill, clean vibe. Panel depth via slight top-edge lightness, no aggressive glows.
         * Mint for progress/success feedback; pair with calm animations.
         */
        val AFTER_EIGHT = ThemePreset(
            name         = "After Eight",
            bg           = Color.parseColor("#050909"),
            bgElevated   = Color.parseColor("#101717"),
            accent       = Color.parseColor("#38F1B4"),
            accentSoft   = Color.parseColor("#24C79B"),
            accentAlt    = Color.parseColor("#7CF5FF"),
            borderSubtle = Color.parseColor("#223130"),
            textPrimary  = Color.parseColor("#F2FFFB"),
            textMuted    = Color.parseColor("#9FB9B3")
        )

        /**
         * ROSE QUARTZ
         * Dark plum base. Soft neon rose primary, pastel pink secondary, light periwinkle alt.
         * Use more rounded corners and softer shadows than harsher themes.
         * Reserve pink for interactive/focus states — avoid flooding large backgrounds.
         */
        val ROSE_QUARTZ = ThemePreset(
            name         = "Rose Quartz",
            bg           = Color.parseColor("#0B0710"),
            bgElevated   = Color.parseColor("#15101F"),
            accent       = Color.parseColor("#FF7DAB"),
            accentSoft   = Color.parseColor("#F9A8D4"),
            accentAlt    = Color.parseColor("#93C5FD"),
            borderSubtle = Color.parseColor("#2A2235"),
            textPrimary  = Color.parseColor("#FDF5FF"),
            textMuted    = Color.parseColor("#B7A9C5")
        )

        /**
         * PURPLE HAZE
         * Dark neutral base. Warm magenta-purple primary, soft purple hover, warm orange accent.
         * Use blurred background gradients for the "haze" effect on major panels.
         * Let accent dominate; use accentAlt (orange) only for rare special highlights.
         */
        val PURPLE_HAZE = ThemePreset(
            name         = "Purple Haze",
            bg           = Color.parseColor("#06060F"),
            bgElevated   = Color.parseColor("#141428"),
            accent       = Color.parseColor("#C04DF9"),
            accentSoft   = Color.parseColor("#8B5CF6"),
            accentAlt    = Color.parseColor("#F97316"),
            borderSubtle = Color.parseColor("#292847"),
            textPrimary  = Color.parseColor("#FAF5FF"),
            textMuted    = Color.parseColor("#A6A1D2")
        )

        /**
         * WHITE WOLF
         * Very light cool-gray base — this is a LIGHT theme, unlike all others.
         * Bright cold blue primary, crisp green secondary, slightly darker blue for hovers.
         * No glows. Crisp 1px borders, high-contrast near-black text.
         * On hover: light fill with darker accent text, no heavy background changes.
         */
        val WHITE_WOLF = ThemePreset(
            name         = "White Wolf",
            bg           = Color.parseColor("#F4F7FB"),
            bgElevated   = Color.parseColor("#FFFFFF"),
            accent       = Color.parseColor("#38BDF8"),
            accentSoft   = Color.parseColor("#22C55E"),
            accentAlt    = Color.parseColor("#0EA5E9"),
            borderSubtle = Color.parseColor("#D0D7E3"),
            textPrimary  = Color.parseColor("#0F172A"),
            textMuted    = Color.parseColor("#6B7280")
        )

        /** Ordered list used by the theme picker. DEFAULT is always first. */
        val ALL: List<ThemePreset> = listOf(
            DEFAULT,
            VAPORWAVE,
            PAY_TO_WIN,
            FRUTTI_DI_MARE,
            LAMBDA,
            ULTRA_VIOLET,
            AFTER_EIGHT,
            ROSE_QUARTZ,
            PURPLE_HAZE,
            WHITE_WOLF
        )
    }
}
