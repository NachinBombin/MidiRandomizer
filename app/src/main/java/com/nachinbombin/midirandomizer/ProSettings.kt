package com.nachinbombin.midirandomizer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Immutable data class that carries all Pro-tier parameters.
 */
@Parcelize
data class ProSettings(
    val jitterAmount: Int = 0,
    val jitterType: JitterType = JitterType.UNIFORM,
    val velocityPattern: VelocityPattern = VelocityPattern.RANDOM,
    val euclideanEnabled: Boolean = false,
    val euclideanSteps: Int = 16,
    val euclideanDensity: Int = 5,
    val euclideanRotation: Int = 0,
    val markovEnabled: Boolean = false,
    val melodicLogicStyle: MelodicLogicStyle = MelodicLogicStyle.STEPWISE,
    val activePreset: ProPreset = ProPreset.NONE
) : Parcelable

enum class JitterType(val label: String) {
    UNIFORM("Uniform"),
    GAUSSIAN("Gaussian"),
    EXPONENTIAL("Exponential")
}

enum class VelocityPattern(val label: String) {
    RANDOM("Random"),
    ASCENDING("Ascending"),
    DESCENDING("Descending"),
    PEAK_CENTER("Peak at Center"),
    ACCENT_BEATS("Accent Beats")
}

enum class MelodicLogicStyle(val label: String) {
    STEPWISE("Stepwise (Melody Flow)"),
    ARPEGGIATED("Arpeggiated"),
    WIDE_LEAPS("Wide Leaps (Experimental)")
}

enum class ProPreset(val label: String) {
    NONE("— Manual —"),
    AMBIENT_TEXTURE("Ambient Texture"),
    EXPERIMENTAL_SOUNDSCAPE("Experimental Soundscape")
}
