package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Produces a velocity value for each successive note according to the
 * selected [VelocityPattern].  The shaper keeps its own internal step
 * counter so the caller just calls [next] on each note event.
 *
 * All outputs are in the range [1, 127].
 */
class VelocityShaper(private val pattern: VelocityPattern, var baseVelocity: Int) {

    private var step = 0
    private val cycleLen = 8   // steps per full envelope cycle

    /** Returns the shaped velocity for the next note. */
    fun next(): Int {
        val v = when (pattern) {
            VelocityPattern.RANDOM -> humanise(baseVelocity)

            VelocityPattern.ASCENDING -> {
                val lo = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                val range = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                lo + (range * (step % cycleLen) / (cycleLen - 1))
            }

            VelocityPattern.DESCENDING -> {
                val hi = baseVelocity
                val range = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                hi - (range * (step % cycleLen) / (cycleLen - 1))
            }

            VelocityPattern.PEAK_CENTER -> {
                val lo = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                val range = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                val pos = step % cycleLen
                val half = cycleLen / 2
                if (pos <= half) lo + (range * pos / (half.coerceAtLeast(1)))
                else lo + (range * (cycleLen - pos) / (half.coerceAtLeast(1)))
            }

            VelocityPattern.ACCENT_BEATS -> {
                // Beat 1 of every 4 is accented, others are softer
                if (step % 4 == 0) {
                    (baseVelocity * 1.15).toInt().coerceAtMost(127)
                } else {
                    (baseVelocity * 0.80).toInt().coerceAtLeast(1)
                }
            }
        }
        step++
        return v.coerceIn(1, 127)
    }

    fun reset() { step = 0 }

    /** Adds ±10 velocity points of micro-humanisation */
    private fun humanise(base: Int): Int =
        (base - 10 + Random.nextInt(21)).coerceIn(1, 127)
}
