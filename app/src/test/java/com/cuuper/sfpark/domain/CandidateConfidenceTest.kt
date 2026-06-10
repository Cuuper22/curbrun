package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CandidateConfidenceTest {
    @Test
    fun strongMatchRequiresHighAvailabilityAndNoRisks() {
        val confidence = candidate(0.78).confidenceSnapshot()

        assertEquals(CandidateConfidenceTier.Strong, confidence.tier)
        assertEquals("Strong match", confidence.label)
    }

    @Test
    fun riskFactorsLowerConfidenceToCaution() {
        val confidence = candidate(0.78, listOf("Modeled curb density suggests this block is usually tight.")).confidenceSnapshot()

        assertEquals(CandidateConfidenceTier.Caution, confidence.tier)
    }

    @Test
    fun lowAvailabilityIsCompetitive() {
        val confidence = candidate(0.42).confidenceSnapshot()

        assertEquals(CandidateConfidenceTier.Competitive, confidence.tier)
    }

    private fun candidate(score: Double, risks: List<String> = emptyList()): ParkingCandidate {
        val center = LatLng(37.0, -122.0)
        return ParkingCandidate(
            segment = CurbSegment(
                id = "confidence",
                street = "Confidence St",
                crossStreet = "near Test",
                center = center,
                polyline = listOf(center),
                parkableFeet = 90,
                baseAvailability = score,
                trafficPressure = 0.2,
                parkedCarDensity = 0.2,
                rules = emptyList()
            ),
            distanceMiles = 0.0,
            estimatedSpaces = 3,
            availabilityScore = score,
            legalUntil = LocalDateTime.parse("2026-06-02T12:00:00"),
            nextRestrictionLabel = null,
            reasons = emptyList(),
            riskFactors = risks
        )
    }
}
