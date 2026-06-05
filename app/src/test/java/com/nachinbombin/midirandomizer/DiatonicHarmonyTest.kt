package com.nachinbombin.midirandomizer

import org.junit.Assert.*
import org.junit.Test

class DiatonicHarmonyTest {

    @Test
    fun testAllowedNotes() {
        val scale = listOf(0, 2, 4, 5, 7, 9, 11) // Major
        val notes = DiatonicHarmony.allowedNotes(scale, 0)
        assertTrue(notes.contains(60)) // C4
        assertFalse(notes.contains(61)) // C#4
    }

    @Test
    fun testApplyMelodicRelation_EmptyChord() {
        val candidates = intArrayOf(60, 62, 64)
        val config = MelodicRelationConfig(enabled = true, mode = MelodicRelationMode.CHORD_AWARE)
        val result = DiatonicHarmony.applyMelodicRelation(candidates, emptyList(), 60, config)
        assertArrayEquals(candidates, result)
    }

    @Test
    fun testApplyMelodicRelation_ChordAware() {
        val candidates = intArrayOf(60, 61, 62, 63, 64)
        val chord = listOf(60, 64, 67) // C Major
        val config = MelodicRelationConfig(enabled = true, mode = MelodicRelationMode.CHORD_AWARE, contrastDepth = 100)
        val result = DiatonicHarmony.applyMelodicRelation(candidates, chord, 60, config)
        
        // Should only contain notes from the chord (octave independent)
        // 60 % 12 = 0 (C)
        // 64 % 12 = 4 (E)
        // 67 % 12 = 7 (G)
        // Candidates: 60(0), 61(1), 62(2), 63(3), 64(4)
        // Expected result for depth=100: 60, 64
        val expected = intArrayOf(60, 64)
        assertArrayEquals(expected, result)
    }

    @Test
    fun testApplyMelodicRelation_RegisterContrast() {
        val candidates = intArrayOf(48, 50, 72, 74)
        val chord = listOf(60, 64, 67) // C4 range
        val config = MelodicRelationConfig(enabled = true, mode = MelodicRelationMode.REGISTER_CONTRAST)
        val result = DiatonicHarmony.applyMelodicRelation(candidates, chord, 60, config)
        
        // v1Min = 60, v1Max = 67
        // Prefer < 60-5 (55) or > 67+5 (72)
        // Expected: 48, 50, 74 (72 is NOT > 72, it's equal)
        assertTrue(result.contains(48))
        assertTrue(result.contains(50))
        assertTrue(result.contains(74))
        assertFalse(result.contains(72))
    }

    @Test
    fun testApplyMelodicRelation_EmptyCandidates() {
        val candidates = intArrayOf()
        val chord = listOf(60, 64, 67)
        val config = MelodicRelationConfig(enabled = true, mode = MelodicRelationMode.CHORD_AWARE)
        val result = DiatonicHarmony.applyMelodicRelation(candidates, chord, 60, config)
        assertEquals(0, result.size)
    }

    @Test
    fun testApplyMelodicRelation_CounterMotion() {
        val candidates = intArrayOf(40, 50, 70, 80)
        val chord = listOf(72, 76, 79) // High C Major (avg > 60)
        val config = MelodicRelationConfig(enabled = true, mode = MelodicRelationMode.COUNTER_MOTION)
        val result = DiatonicHarmony.applyMelodicRelation(candidates, chord, 60, config)
        
        // v1Mid > 60, prefer < 60
        assertTrue(result.all { it < 60 })
        assertEquals(2, result.size)
    }
}
