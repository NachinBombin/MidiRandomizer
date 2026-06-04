package com.nachinbombin.midirandomizer

import kotlin.random.Random

/**
 * L-System fractal melodic engine — Tier-2 replacement.
 *
 * A Lindenmayer system rewrites a seed symbol string iteratively.
 * Symbols map to scale-degree delta operations. The resulting string
 * is consumed note-by-note; when exhausted a new string is generated
 * (optionally with slight rule variance for musical variety).
 *
 * This produces self-similar, recursively structured melodies.
 */
class LSystemMelody(
    private val scaleSize: Int,
    private val cfg: LSystemConfig
) {

    // ── symbol definitions ────────────────────────────────────────────────────

    /** Symbols: U=up step, D=down step, L=leap up, l=leap down, H=hold */
    enum class Sym { U, D, Lp, Ls, H }  // Lp=leap-up, Ls=leap-down

    /** Rewrite rules: each symbol expands to a list of symbols. */
    private val baseRules: Map<Sym, List<Sym>> = mapOf(
        Sym.U  to listOf(Sym.U,  Sym.D,  Sym.U),
        Sym.D  to listOf(Sym.D,  Sym.Lp, Sym.D),
        Sym.Lp to listOf(Sym.U,  Sym.U,  Sym.D),
        Sym.Ls to listOf(Sym.D,  Sym.D,  Sym.U),
        Sym.H  to listOf(Sym.H,  Sym.U,  Sym.H)
    )

    /** Delta (scale-degree steps) associated with each symbol. */
    private val symDelta: Map<Sym, Int> = mapOf(
        Sym.U  to +1,
        Sym.D  to -1,
        Sym.Lp to +3,
        Sym.Ls to -3,
        Sym.H  to  0
    )

    companion object {
        /** Named axioms (seed strings). Indexed by LSystemConfig.axiomIndex. */
        val AXIOMS: List<List<LSystemMelody.Sym>> = listOf(
            listOf(LSystemMelody.Sym.U),
            listOf(LSystemMelody.Sym.D),
            listOf(LSystemMelody.Sym.Lp),
            listOf(LSystemMelody.Sym.U, LSystemMelody.Sym.D),
            listOf(LSystemMelody.Sym.U, LSystemMelody.Sym.Lp, LSystemMelody.Sym.D)
        )
    }

    // ── state ─────────────────────────────────────────────────────────────────

    private val buffer: ArrayDeque<Int> = ArrayDeque()
    private var currentDegree: Int = Random.nextInt(scaleSize)

    // ── public API ────────────────────────────────────────────────────────────

    fun reset() {
        buffer.clear()
        currentDegree = Random.nextInt(scaleSize)
    }

    fun nextDegree(): Int {
        if (buffer.isEmpty()) generateString()
        val delta = buffer.removeFirst()
        currentDegree = wrapDegree(currentDegree + delta)
        return currentDegree
    }

    // ── L-System expansion ────────────────────────────────────────────────────

    private fun generateString() {
        val axiom  = AXIOMS.getOrElse(cfg.axiomIndex) { AXIOMS[0] }
        var string = axiom.toMutableList()
        val iters  = cfg.iterations.coerceIn(1, 4)
        repeat(iters) {
            string = string.flatMap { sym -> rewrite(sym) }.toMutableList()
        }
        // Cap to prevent excessively long buffers (4^4 = 256 max)
        val deltas = string.take(256).map { sym -> symDelta[sym] ?: 0 }
        buffer.addAll(deltas)
    }

    private fun rewrite(sym: Sym): List<Sym> {
        val rule = baseRules[sym] ?: listOf(sym)
        return if (cfg.ruleVariance > 0f && Random.nextFloat() < cfg.ruleVariance) {
            // Mutate: randomly swap one symbol in this rule's output
            val mutated = rule.toMutableList()
            val idx = Random.nextInt(mutated.size)
            mutated[idx] = Sym.values().random()
            mutated
        } else rule
    }

    private fun wrapDegree(d: Int): Int = ((d % scaleSize) + scaleSize) % scaleSize
}
