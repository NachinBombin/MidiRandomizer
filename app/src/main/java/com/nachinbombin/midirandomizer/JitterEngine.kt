package com.nachinbombin.midirandomizer

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Computes a timing jitter offset in milliseconds.
 *
 * @param baseInterval  The nominal interval in ms before jitter.
 * @param jitterAmount  0–100 % of the base interval as maximum deviation.
 * @param type          Statistical distribution to use.
 * @return              The adjusted interval (always >= 1 ms).
 */
object JitterEngine {

    fun applyJitter(baseInterval: Long, jitterAmount: Int, type: JitterType): Long {
        if (jitterAmount == 0) return baseInterval
        val maxDeviation = baseInterval * jitterAmount / 100.0
        val offset = when (type) {
            JitterType.UNIFORM -> uniform(maxDeviation)
            JitterType.GAUSSIAN -> gaussian(maxDeviation)
            JitterType.EXPONENTIAL -> exponential(maxDeviation)
        }
        return (baseInterval + offset).toLong().coerceAtLeast(1L)
    }

    // ── Distributions ─────────────────────────────────────────────────────────

    /** Uniform: offset in [-max, +max] */
    private fun uniform(max: Double): Double =
        (Random.nextDouble() * 2.0 - 1.0) * max

    /**
     * Gaussian (Box-Muller): mean=0, sigma = max/3 so ~99.7 % stays in [-max, max].
     * Clipped to [-max, +max] to avoid very long gaps.
     */
    private fun gaussian(max: Double): Double {
        val sigma = max / 3.0
        val u1 = Random.nextDouble().coerceAtLeast(1e-10)
        val u2 = Random.nextDouble()
        val z = sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        return (z * sigma).coerceIn(-max, max)
    }

    /**
     * Exponential: positive-only skew (models "late" feel).
     * lambda chosen so mean = max/3.
     */
    private fun exponential(max: Double): Double {
        val lambda = 3.0 / max.coerceAtLeast(1.0)
        val u = Random.nextDouble().coerceAtLeast(1e-10)
        val sample = -ln(u) / lambda
        // Mix positive and negative directions at 70/30 for slight late bias
        val sign = if (Random.nextFloat() < 0.70f) 1.0 else -1.0
        return (sign * sample).coerceIn(-max, max)
    }
}
