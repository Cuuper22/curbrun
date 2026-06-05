package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SearchRoutePlannerTest {
    @Test
    fun plansGreedyRouteFromOriginWithStopNumbers() {
        val origin = LatLng(37.0, -122.0)
        val candidates = listOf(
            candidate("far", LatLng(37.02, -122.02), 0.9),
            candidate("near", LatLng(37.001, -122.001), 0.7),
            candidate("mid", LatLng(37.006, -122.006), 0.8)
        )

        val route = SearchRoutePlanner().plan(origin, candidates, maxStops = 3)

        assertEquals(listOf("near", "mid", "far"), route.map { it.candidate.segment.id })
        assertEquals(listOf(1, 2, 3), route.map { it.order })
        assertTrue(route.all { it.legMiles >= 0.0 })
    }

    private fun candidate(id: String, center: LatLng, availability: Double): ParkingCandidate {
        return ParkingCandidate(
            segment = CurbSegment(
                id = id,
                street = "$id St",
                crossStreet = "near Test",
                center = center,
                polyline = listOf(center),
                parkableFeet = 90,
                baseAvailability = availability,
                trafficPressure = 0.2,
                parkedCarDensity = 0.2,
                rules = emptyList()
            ),
            distanceMiles = 0.0,
            estimatedSpaces = 3,
            availabilityScore = availability,
            legalUntil = LocalDateTime.parse("2026-06-02T12:00:00"),
            nextRestrictionLabel = null,
            reasons = emptyList(),
            riskFactors = emptyList()
        )
    }
}
