package com.nachinbombin.midirandomizer

/**
 * Euclidean rhythm generator.
 *
 * Distributes pulses onsets as evenly as possible across steps slots,
 * then rotates the result by rotation positions.
 *
 * Returns a BooleanArray of length [steps] where `true` = onset.
 */
object EuclideanRhythm {

    /**
     * Generates a Euclidean rhythm using a Bresenham-based approach.
     * @param steps Total number of slots (n).
     * @param pulses Number of onsets (k).
     * @param rotation Offset to shift the pattern.
     */
    fun generate(steps: Int, pulses: Int, rotation: Int = 0): BooleanArray {
        if (steps <= 0) return BooleanArray(0)
        val k = pulses.coerceIn(0, steps)
        val pattern = BooleanArray(steps)
        
        var accumulator = 0
        for (i in 0 until steps) {
            accumulator += k
            if (accumulator >= steps) {
                pattern[i] = true
                accumulator -= steps
            } else {
                pattern[i] = false
            }
        }

        // Apply rotation
        if (rotation == 0) return pattern
        val rotated = BooleanArray(steps)
        val offset = ((rotation % steps) + steps) % steps
        for (i in 0 until steps) {
            rotated[(i + offset) % steps] = pattern[i]
        }
        return rotated
    }
}
