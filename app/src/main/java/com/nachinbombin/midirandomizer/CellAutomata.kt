package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * Pitch-class cellular automaton — Tier-2 replacement melodic engine.
 *
 * Operates on a 12-element boolean ring (one slot per pitch class 0–11).
 * Each generation step evolves the ring using survival/birth rules similar
 * to Conway's Game of Life.
 *
 * Notes are triggered by the "newly alive" pitch classes in each generation
 * that also belong to the current scale. Returns scale-degree indices.
 *
 * This engine generates pitch-SET evolution — the harmonic texture changes
 * organically over time, ideal for drone/texture voices and spectral writing.
 */
class CellAutomata(
    private val scaleIntervals: List<Int>,   // e.g. [0,2,4,5,7,9,11] for major
    private val cfg: CellAutomataConfig
) {
    private val PC_COUNT = 12

    // ── state ─────────────────────────────────────────────────────────────────

    private var ring: BooleanArray = BooleanArray(PC_COUNT)  // true = alive
    private val noteQueue: ArrayDeque<Int> = ArrayDeque()    // scale-degree indices

    init { initRing() }

    // ── public API ────────────────────────────────────────────────────────────

    fun reset() {
        initRing()
        noteQueue.clear()
    }

    /** Returns the next scale-degree index. Evolves the CA when the queue is empty. */
    fun nextDegree(context: List<MidiService.MelodicEvent> = emptyList()): Int {
        if (noteQueue.isEmpty()) evolveAndEnqueue()
        if (noteQueue.isEmpty()) {
            // Fallback if evolution produces no in-scale notes
            return Random.nextInt(scaleIntervals.size)
        }
        return noteQueue.removeFirst()
    }

    // ── initialisation ────────────────────────────────────────────────────────

    /** Seed the ring: alive cells = pitch classes present in the scale. */
    private fun initRing() {
        ring = BooleanArray(PC_COUNT) { pc -> scaleIntervals.contains(pc) }
    }

    // ── evolution ─────────────────────────────────────────────────────────────

    private fun evolveAndEnqueue() {
        val prev  = ring.clone()
        val next  = BooleanArray(PC_COUNT)
        for (i in 0 until PC_COUNT) {
            val neighbors = countLivingNeighbors(prev, i)
            next[i] = if (prev[i]) {
                neighbors in cfg.survivalMin..cfg.survivalMax
            } else {
                neighbors == cfg.birthCount
            }
        }
        // Mutation: random bit-flips
        for (i in 0 until PC_COUNT) {
            if (Random.nextFloat() < cfg.mutationRate) next[i] = !next[i]
        }
        // If all cells die, re-seed to avoid silence
        if (next.none { it }) initRing().also { return }

        ring = next

        // Enqueue newly alive PCs that belong to the scale, as degree indices
        for (i in 0 until PC_COUNT) {
            if (next[i] && !prev[i]) {
                val degreeIdx = scaleIntervals.indexOf(i)
                if (degreeIdx >= 0) noteQueue.add(degreeIdx)
            }
        }
        // If no new cells born in scale, enqueue all alive in-scale cells
        if (noteQueue.isEmpty()) {
            for (i in 0 until PC_COUNT) {
                if (next[i]) {
                    val degreeIdx = scaleIntervals.indexOf(i)
                    if (degreeIdx >= 0) noteQueue.add(degreeIdx)
                }
            }
        }
        noteQueue.shuffle()
    }

    private fun countLivingNeighbors(state: BooleanArray, i: Int): Int {
        val left  = (i - 1 + PC_COUNT) % PC_COUNT
        val right = (i + 1) % PC_COUNT
        return (if (state[left]) 1 else 0) + (if (state[right]) 1 else 0)
    }
}
