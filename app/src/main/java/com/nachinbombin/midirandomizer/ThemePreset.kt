package com.nachinbombin.midirandomizer

import android.graphics.Color

/**
 * Represents an Opera GX-inspired color theme.
 *
 * Token roles:
 *   [bg]            App background — Main window & Pro window.
 *   [bgVoices]      Background for Voices window & Perform window.
 *                   Each theme gives this a distinct hue that pairs with [accent].
 *   [bgElevated]    Cards, panels, inner ViewGroup surfaces.
 *   [accent]        Primary accent: buttons, sliders, active borders.
 *   [accentSoft]    Secondary accent: hover fills, chip selected state, track active.
 *   [accentAlt]     Tertiary accent: small highlights, success badges, link color.
 *   [borderSubtle]  Low-contrast separators, slider track inactive.
 *   [textPrimary]   Main label / body text.
 *   [textMuted]     Secondary labels (BPM value, section headers).
 */
data class ThemePreset(
    val name: String,
    val bg: Int,
    val bgVoices: Int,
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
            bgVoices     = Color.parseColor("#111318"),   // original hard-coded value
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
         * VAPORWAVE — dark near-black blue / neon pink primary / cyan secondary / mint alt.
         * bgVoices: deep purple-navy — pairs with the neon pink accent.
         */
        val VAPORWAVE = ThemePreset(
            name         = "Vaporwave",
            bg           = Color.parseColor("#050813"),
            bgVoices     = Color.parseColor("#0D0A20"),
            bgElevated   = Color.parseColor("#11152A"),
            accent       = Color.parseColor("#FF71CE"),
            accentSoft   = Color.parseColor("#01CDFE"),
            accentAlt    = Color.parseColor("#05FFA1"),
            borderSubtle = Color.parseColor("#282B45"),
            textPrimary  = Color.parseColor("#F5F3FF"),
            textMuted    = Color.parseColor("#A4A3CF")
        )

        /**
         * PAY TO WIN — deep violet-black / GX red primary / warm gold secondary.
         * bgVoices: deep crimson-black — reinforces the "win" red accent.
         */
        val PAY_TO_WIN = ThemePreset(
            name         = "Pay To Win",
            bg           = Color.parseColor("#070713"),
            bgVoices     = Color.parseColor("#130A0A"),
            bgElevated   = Color.parseColor("#121222"),
            accent       = Color.parseColor("#FA1E4E"),
            accentSoft   = Color.parseColor("#FF9B4A"),
            accentAlt    = Color.parseColor("#FFDD6B"),
            borderSubtle = Color.parseColor("#26263B"),
            textPrimary  = Color.parseColor("#F8F5FF"),
            textMuted    = Color.parseColor("#A29FBF")
        )

        /**
         * FRUTTI DI MARE — deep teal-black / teal primary / bright aqua secondary.
         * bgVoices: deep sea-navy — cooler counterpart to the warm teal bg.
         */
        val FRUTTI_DI_MARE = ThemePreset(
            name         = "Frutti di Mare",
            bg           = Color.parseColor("#031017"),
            bgVoices     = Color.parseColor("#060F1A"),
            bgElevated   = Color.parseColor("#081C25"),
            accent       = Color.parseColor("#1FB2AA"),
            accentSoft   = Color.parseColor("#41E3C1"),
            accentAlt    = Color.parseColor("#3A7FFF"),
            borderSubtle = Color.parseColor("#16313D"),
            textPrimary  = Color.parseColor("#F0F7FF"),
            textMuted    = Color.parseColor("#9EB8C7")
        )

        /**
         * LAMBDA — neutral black / industrial orange primary / sci-fi blue alt.
         * bgVoices: dark charcoal with very faint amber warmth.
         */
        val LAMBDA = ThemePreset(
            name         = "Lambda",
            bg           = Color.parseColor("#050608"),
            bgVoices     = Color.parseColor("#0E0C09"),
            bgElevated   = Color.parseColor("#101215"),
            accent       = Color.parseColor("#FF9100"),
            accentSoft   = Color.parseColor("#FFB547"),
            accentAlt    = Color.parseColor("#00B8FF"),
            borderSubtle = Color.parseColor("#24262B"),
            textPrimary  = Color.parseColor("#F5F5F7"),
            textMuted    = Color.parseColor("#9B9DA4")
        )

        /**
         * ULTRA VIOLET — very dark indigo / saturated violet primary / cyan for focus.
         * bgVoices: near-black with a strong indigo cast.
         */
        val ULTRA_VIOLET = ThemePreset(
            name         = "Ultra Violet",
            bg           = Color.parseColor("#060513"),
            bgVoices     = Color.parseColor("#0C0A1E"),
            bgElevated   = Color.parseColor("#120F26"),
            accent       = Color.parseColor("#A855FF"),
            accentSoft   = Color.parseColor("#7C3AED"),
            accentAlt    = Color.parseColor("#22D3EE"),
            borderSubtle = Color.parseColor("#26233D"),
            textPrimary  = Color.parseColor("#F8F5FF"),
            textMuted    = Color.parseColor("#A5A1D5")
        )

        /**
         * AFTER EIGHT — almost-black green-hint / bright mint primary / ice-blue alt.
         * bgVoices: very dark forest green — gives depth behind the mint accent.
         */
        val AFTER_EIGHT = ThemePreset(
            name         = "After Eight",
            bg           = Color.parseColor("#050909"),
            bgVoices     = Color.parseColor("#070F0C"),
            bgElevated   = Color.parseColor("#101717"),
            accent       = Color.parseColor("#38F1B4"),
            accentSoft   = Color.parseColor("#24C79B"),
            accentAlt    = Color.parseColor("#7CF5FF"),
            borderSubtle = Color.parseColor("#223130"),
            textPrimary  = Color.parseColor("#F2FFFB"),
            textMuted    = Color.parseColor("#9FB9B3")
        )

        /**
         * ROSE QUARTZ — dark plum / soft neon rose primary / pastel pink secondary.
         * bgVoices: deep wine-plum — darker, richer counterpart to the bg.
         */
        val ROSE_QUARTZ = ThemePreset(
            name         = "Rose Quartz",
            bg           = Color.parseColor("#0B0710"),
            bgVoices     = Color.parseColor("#100810"),
            bgElevated   = Color.parseColor("#15101F"),
            accent       = Color.parseColor("#FF7DAB"),
            accentSoft   = Color.parseColor("#F9A8D4"),
            accentAlt    = Color.parseColor("#93C5FD"),
            borderSubtle = Color.parseColor("#2A2235"),
            textPrimary  = Color.parseColor("#FDF5FF"),
            textMuted    = Color.parseColor("#B7A9C5")
        )

        /**
         * PURPLE HAZE — dark neutral / warm magenta-purple primary / warm orange alt.
         * bgVoices: deep grape-black — intensifies the haze atmosphere.
         */
        val PURPLE_HAZE = ThemePreset(
            name         = "Purple Haze",
            bg           = Color.parseColor("#06060F"),
            bgVoices     = Color.parseColor("#0A0814"),
            bgElevated   = Color.parseColor("#141428"),
            accent       = Color.parseColor("#C04DF9"),
            accentSoft   = Color.parseColor("#8B5CF6"),
            accentAlt    = Color.parseColor("#F97316"),
            borderSubtle = Color.parseColor("#292847"),
            textPrimary  = Color.parseColor("#FAF5FF"),
            textMuted    = Color.parseColor("#A6A1D2")
        )

        /**
         * WHITE WOLF — light arctic theme. Very light cool-gray bg.
         * bgVoices: pure white — crisp contrast against the slightly gray main bg.
         * No glows. High-contrast near-black text.
         */
        val WHITE_WOLF = ThemePreset(
            name         = "White Wolf",
            bg           = Color.parseColor("#F4F7FB"),
            bgVoices     = Color.parseColor("#FFFFFF"),
            bgElevated   = Color.parseColor("#E8EDF5"),
            accent       = Color.parseColor("#38BDF8"),
            accentSoft   = Color.parseColor("#22C55E"),
            accentAlt    = Color.parseColor("#0EA5E9"),
            borderSubtle = Color.parseColor("#D0D7E3"),
            textPrimary  = Color.parseColor("#0F172A"),
            textMuted    = Color.parseColor("#6B7280")
        )

        /** Ordered list used by the theme picker. DEFAULT is always first. */
        val ALL: List<ThemePreset> = listOf(
            DEFAULT, VAPORWAVE, PAY_TO_WIN, FRUTTI_DI_MARE,
            LAMBDA, ULTRA_VIOLET, AFTER_EIGHT, ROSE_QUARTZ, PURPLE_HAZE, WHITE_WOLF
        )
    }
}
