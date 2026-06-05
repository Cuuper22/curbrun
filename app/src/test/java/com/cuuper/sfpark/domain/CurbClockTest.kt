package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CurbClockTest {
    @Test
    fun formatsMultiHourClearWindow() {
        val candidate = candidateUntil("2026-06-01T14:45:00")

        val snapshot = candidate.curbClockSnapshot(LocalDateTime.parse("2026-06-01T09:15:00"))

        assertEquals(330, snapshot.remainingMinutes)
        assertEquals("5h 30m clear", snapshot.label)
        assertEquals(CurbClockUrgency.Comfortable, snapshot.urgency)
    }

    @Test
    fun flagsTightWindowAtOneHourOrLess() {
        val candidate = candidateUntil("2026-06-01T10:15:00")

        val snapshot = candidate.curbClockSnapshot(LocalDateTime.parse("2026-06-01T09:15:00"))

        assertEquals("1h 0m clear", snapshot.label)
        assertEquals(CurbClockUrgency.Tight, snapshot.urgency)
    }

    private fun candidateUntil(legalUntil: String): ParkingCandidate {
        val center = LatLng(37.0, -122.0)
        return ParkingCandidate(
            segment = CurbSegment(
                id = "clock",
                street = "Clock St",
                crossStreet = "near Test",
                center = center,
                polyline = listOf(center),
                parkableFeet = 90,
                baseAvailability = 0.7,
                trafficPressure = 0.2,
                parkedCarDensity = 0.2,
                rules = emptyList()
            ),
            distanceMiles = 0.0,
            estimatedSpaces = 3,
            availabilityScore = 0.72,
            legalUntil = LocalDateTime.parse(legalUntil),
            nextRestrictionLabel = null,
            reasons = emptyList(),
            riskFactors = emptyList()
        )
    }
}
