package com.nachinbombin.midirandomizer

import org.junit.Assert.*
import org.junit.Test

class DiatonicHarmonyTest {

    @Test
    fun testApplyMelodicRelation_ChordAware() {
        val candidateNotes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72) // C Major scale
        // Reference voice is playing a C Major triad: C (60), E (64), G (67)
        val context = listOf(MidiService.MelodicEvent(listOf(60, 64, 67)))
        val config = MelodicRelationConfig(
            enabled = true,
            mode = MelodicRelationMode.CHORD_AWARE,
            contrastDepth = 100
        )

        val filtered = DiatonicHarmony.applyMelodicRelation(candidateNotes, context, config)
        
        // Should only contain notes from the C Major triad (60, 64, 67, 72)
        val filteredSet = filtered.toSet()
        assertTrue(filteredSet.contains(60))
        assertTrue(filteredSet.contains(64))
        assertTrue(filteredSet.contains(67))
        assertTrue(filteredSet.contains(72))
        assertFalse(filteredSet.contains(62))
        assertFalse(filteredSet.contains(65))
    }

    @Test
    fun testApplyMelodicRelation_CounterMotion_Up() {
        val candidateNotes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72)
        // Reference voice moved from C (60) to G (67) -> Upwards
        val context = listOf(
            MidiService.MelodicEvent(listOf(60)),
            MidiService.MelodicEvent(listOf(67))
        )
        val config = MelodicRelationConfig(
            enabled = true,
            mode = MelodicRelationMode.COUNTER_MOTION,
            contrastDepth = 100
        )

        val filtered = DiatonicHarmony.applyMelodicRelation(candidateNotes, context, config)
        
        // Reference moved up, so we should prefer notes BELOW the current reference pitch (67)
        val filteredList = filtered.toList()
        assertTrue(filteredList.all { it < 67 })
    }

    @Test
    fun testApplyMelodicRelation_CounterMotion_Down() {
        val candidateNotes = intArrayOf(60, 62, 64, 65, 67, 69, 71, 72)
        // Reference voice moved from G (67) to D (62) -> Downwards
        val context = listOf(
            MidiService.MelodicEvent(listOf(67)),
            MidiService.MelodicEvent(listOf(62))
        )
        val config = MelodicRelationConfig(
            enabled = true,
            mode = MelodicRelationMode.COUNTER_MOTION,
            contrastDepth = 100
        )

        val filtered = DiatonicHarmony.applyMelodicRelation(candidateNotes, context, config)
        
        // Reference moved down, so we should prefer notes ABOVE the current reference pitch (62)
        val filteredList = filtered.toList()
        assertTrue(filteredList.all { it > 62 })
    }

    @Test
    fun testApplyMelodicRelation_RegisterContrast_Above() {
        val candidateNotes = intArrayOf(48, 50, 52, 60, 62, 64, 72, 74, 76)
        // Reference voice is around middle C (60)
        val context = listOf(MidiService.MelodicEvent(listOf(60)))
        val config = MelodicRelationConfig(
            enabled = true,
            mode = MelodicRelationMode.REGISTER_CONTRAST,
            contrastDepth = 100
        )

        val filtered = DiatonicHarmony.applyMelodicRelation(candidateNotes, context, config)
        
        // Should avoid notes near 60 (+/- 5 semitones)
        val filteredSet = filtered.toSet()
        assertFalse(filteredSet.contains(60))
        assertFalse(filteredSet.contains(62))
        assertFalse(filteredSet.contains(64))
        assertTrue(filteredSet.contains(48))
        assertTrue(filteredSet.contains(76))
    }
}
