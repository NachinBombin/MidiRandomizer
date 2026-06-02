package com.nachinbombin.midirandomizer

/**
 * Bjorklund / Euclidean rhythm generator.
 *
 * Distributes [pulses] onsets as evenly as possible across [steps] slots,
 * then rotates the result by [rotation] positions.
 *
 * Returns a BooleanArray of length [steps] where `true` = onset.
 */
object EuclideanRhythm {

    fun generate(steps: Int, pulses: Int, rotation: Int = 0): BooleanArray {
        require(steps >= 1)
        val p = pulses.coerceIn(0, steps)
        if (p == 0) return BooleanArray(steps) { false }
        if (p == steps) return BooleanArray(steps) { true }

        // Bjorklund algorithm
        val pattern = bjorklund(steps, p)

        // Apply rotation (positive = right-shift)
        val rot = ((rotation % steps) + steps) % steps
        return BooleanArray(steps) { i -> pattern[(i + rot) % steps] }
    }

    private fun bjorklund(steps: Int, pulses: Int): BooleanArray {
        var pattern = Array(steps) { if (it < pulses) mutableListOf(true) else mutableListOf(false) }
        var remainder = steps - pulses
        var dividend = pulses

        while (remainder > 1) {
            val times = dividend / remainder
            val newRemainder = dividend % remainder
            val nextPattern = mutableListOf<MutableList<Boolean>>()

            for (i in 0 until remainder) {
                val group = pattern[i].toMutableList()
                repeat(times) { group.addAll(pattern[dividend - 1 - (i % (dividend - remainder))]) }
                nextPattern.add(group)
            }
            for (i in 0 until newRemainder) {
                nextPattern[i].addAll(pattern[remainder + i])
            }

            pattern = nextPattern.toTypedArray()
            dividend = remainder
            remainder = newRemainder
        }

        return BooleanArray(steps) { i ->
            var flat = 0
            for (group in pattern) {
                if (i < flat + group.size) return@BooleanArray group[i - flat]
                flat += group.size
            }
            false
        }
    }
}
