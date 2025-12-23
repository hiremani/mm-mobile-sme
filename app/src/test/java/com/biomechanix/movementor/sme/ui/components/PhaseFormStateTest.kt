package com.biomechanix.movementor.sme.ui.components

import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseFormStateTest {

    @Test
    fun `toActiveCuesJson converts list to JSON array`() {
        val cues = listOf("Keep back straight", "Breathe out")
        val json = cues.toActiveCuesJson()

        assertEquals("[\"Keep back straight\",\"Breathe out\"]", json)
    }

    @Test
    fun `toActiveCuesJson returns null for empty list`() {
        val cues = emptyList<String>()
        val json = cues.toActiveCuesJson()

        assertNull(json)
    }

    @Test
    fun `toKeyPosesJson converts list to JSON array`() {
        val keyPoses = listOf(10, 25, 40)
        val json = keyPoses.toKeyPosesJson()

        assertEquals("[10,25,40]", json)
    }

    @Test
    fun `toKeyPosesJson returns null for empty list`() {
        val keyPoses = emptyList<Int>()
        val json = keyPoses.toKeyPosesJson()

        assertNull(json)
    }

    @Test
    fun `toCorrectionCuesJson converts map to JSON object`() {
        val corrections = mapOf(
            "KNEE_VALGUS" to "Push knees out",
            "BACK_ROUNDING" to "Keep chest up"
        )
        val json = corrections.toCorrectionCuesJson()

        assertEquals("{\"KNEE_VALGUS\":\"Push knees out\",\"BACK_ROUNDING\":\"Keep chest up\"}", json)
    }

    @Test
    fun `toCorrectionCuesJson returns null for empty map`() {
        val corrections = emptyMap<String, String>()
        val json = corrections.toCorrectionCuesJson()

        assertNull(json)
    }

    @Test
    fun `toFormState converts entity to form state correctly`() {
        val entity = PhaseAnnotationEntity(
            id = "phase-1",
            sessionId = "session-1",
            phaseName = "Test Phase",
            phaseIndex = 0,
            startFrame = 10,
            endFrame = 50,
            source = "MANUAL",
            confidence = 0.9,
            description = "Test description",
            keyPosesJson = "[15,30,45]",
            complianceThreshold = 0.85,
            holdDurationMs = 2000,
            entryCue = "Start here",
            activeCuesJson = "[\"Keep steady\",\"Breathe\"]",
            exitCue = "End here",
            correctionCuesJson = "{\"KNEE_VALGUS\":\"Fix knee\"}",
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        val formState = entity.toFormState()

        assertEquals("Test Phase", formState.phaseName)
        assertEquals(10, formState.startFrame)
        assertEquals(50, formState.endFrame)
        assertEquals("Test description", formState.description)
        assertEquals(listOf(15, 30, 45), formState.keyPoses)
        assertEquals(0.85, formState.complianceThreshold, 0.01)
        assertEquals(2000, formState.holdDurationMs)
        assertEquals("Start here", formState.entryCue)
        assertEquals(listOf("Keep steady", "Breathe"), formState.activeCues)
        assertEquals("End here", formState.exitCue)
        assertEquals(mapOf("KNEE_VALGUS" to "Fix knee"), formState.correctionCues)
    }

    @Test
    fun `toFormState handles null values`() {
        val entity = PhaseAnnotationEntity(
            id = "phase-1",
            sessionId = "session-1",
            phaseName = "Simple Phase",
            phaseIndex = 0,
            startFrame = 0,
            endFrame = 30,
            source = "MANUAL",
            confidence = null,
            description = null,
            keyPosesJson = null,
            complianceThreshold = null,
            holdDurationMs = null,
            entryCue = null,
            activeCuesJson = null,
            exitCue = null,
            correctionCuesJson = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        val formState = entity.toFormState()

        assertEquals("Simple Phase", formState.phaseName)
        assertEquals("", formState.description)
        assertEquals(emptyList<Int>(), formState.keyPoses)
        assertEquals(0.7, formState.complianceThreshold, 0.01) // default
        assertEquals(0, formState.holdDurationMs)
        assertEquals("", formState.entryCue)
        assertEquals(emptyList<String>(), formState.activeCues)
        assertEquals("", formState.exitCue)
        assertEquals(emptyMap<String, String>(), formState.correctionCues)
    }

    @Test
    fun `toFormState handles empty JSON strings`() {
        val entity = PhaseAnnotationEntity(
            id = "phase-1",
            sessionId = "session-1",
            phaseName = "Phase",
            phaseIndex = 0,
            startFrame = 0,
            endFrame = 30,
            source = "MANUAL",
            confidence = null,
            description = null,
            keyPosesJson = "[]",
            complianceThreshold = 0.7,
            holdDurationMs = null,
            entryCue = null,
            activeCuesJson = "[]",
            exitCue = null,
            correctionCuesJson = "{}",
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        val formState = entity.toFormState()

        assertEquals(emptyList<Int>(), formState.keyPoses)
        assertEquals(emptyList<String>(), formState.activeCues)
        assertEquals(emptyMap<String, String>(), formState.correctionCues)
    }

    @Test
    fun `PhaseFormState default values are correct`() {
        val formState = PhaseFormState()

        assertEquals("", formState.phaseName)
        assertEquals(0, formState.startFrame)
        assertEquals(0, formState.endFrame)
        assertEquals("", formState.description)
        assertEquals(emptyList<Int>(), formState.keyPoses)
        assertEquals(0.7, formState.complianceThreshold, 0.01)
        assertEquals(0, formState.holdDurationMs)
        assertEquals("", formState.entryCue)
        assertEquals(emptyList<String>(), formState.activeCues)
        assertEquals("", formState.exitCue)
        assertEquals(emptyMap<String, String>(), formState.correctionCues)
    }
}
