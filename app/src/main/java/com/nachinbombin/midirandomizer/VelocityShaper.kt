package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Produces a velocity value for each successive note according to the
 * selected [VelocityPattern].  The shaper keeps its own internal step
 * counter so the caller just calls [next] on each note event.
 *
 * VelocityPattern enum values: RANDOM, ACCENT, CRESCENDO, DECRESCENDO, FLAT
 *
 * All outputs are in the range [1, 127].
 */
class VelocityShaper(private val pattern: VelocityPattern, var baseVelocity: Int) {

    private var step = 0
    private val cycleLen = 8   // steps per full envelope cycle

    /** Returns the shaped velocity for the next note. */
    fun next(): Int {
        val v = when (pattern) {
            VelocityPattern.RANDOM -> humanize(baseVelocity)

            VelocityPattern.ACCENT -> {
                // Beat 1 of every 4 is accented, others softer
                if ((step % 4) == 0) {
                    (baseVelocity * 1.15).toInt().coerceAtMost(127)
                } else {
                    (baseVelocity * 0.80).toInt().coerceAtLeast(1)
                }
            }

            VelocityPattern.CRESCENDO -> {
                val lo    = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                val range = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                lo + (range * (step % cycleLen) / (cycleLen - 1))
            }

            VelocityPattern.DECRESCENDO -> {
                val hi    = baseVelocity
                val range = (baseVelocity * 0.5).toInt().coerceAtLeast(1)
                hi - (range * (step % cycleLen) / (cycleLen - 1))
            }

            VelocityPattern.FLAT -> baseVelocity
        }
        step++
        return v.coerceIn(1, 127)
    }

    fun reset() { step = 0 }

    /** Adds ±10 velocity points of micro-humanization */
    private fun humanize(base: Int): Int =
        (base - 10 + Random.nextInt(21)).coerceIn(1, 127)
}
