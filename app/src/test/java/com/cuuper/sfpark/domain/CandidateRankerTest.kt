package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

class CandidateRankerTest {
    private val ranker = CandidateRanker()
    private val origin = LatLng(37.7749, -122.4194)

    @Test
    fun filtersBlockedPaidMetersAndSegmentsOutsideRadius() {
        val segments = listOf(
            segment("free-near", origin.copy(latitude = origin.latitude + 0.001)),
            segment(
                id = "paid",
                center = origin.copy(latitude = origin.latitude + 0.002),
                rules = listOf(
                    ParkingRule(
                        kind = RuleKind.PaidMeter,
                        window = TimeWindow(setOf(DayOfWeek.MONDAY), 9 * 60, 18 * 60),
                        label = "Paid meter."
                    )
                )
            ),
            segment("too-far", LatLng(37.90, -122.4194))
        )

        val results = ranker.rank(segments, queryAt("2026-06-01T10:00:00", radiusMiles = 1.0))

        assertEquals(listOf("free-near"), results.map { it.segment.id })
    }

    @Test
    fun ranksClosestLegalCandidateAheadOfFartherCandidateWhenAvailabilityIsSimilar() {
        val near = segment("near", origin.copy(latitude = origin.latitude + 0.001), baseAvailability = 0.72)
        val far = segment("far", origin.copy(latitude = origin.latitude + 0.006), baseAvailability = 0.74)

        val results = ranker.rank(listOf(far, near), queryAt("2026-06-01T10:00:00"))

        assertEquals(listOf("near", "far"), results.map { it.segment.id })
        assertTrue(results.first().distanceMiles < results.last().distanceMiles)
    }

    @Test
    fun availabilityCanBreakVeryCloseCandidateTies() {
        val tight = segment(
            id = "tight",
            center = origin.copy(latitude = origin.latitude + 0.001),
            baseAvailability = 0.42,
            trafficPressure = 0.9,
            parkedCarDensity = 0.9
        )
        val open = segment(
            id = "open",
            center = origin.copy(latitude = origin.latitude + 0.00105),
            baseAvailability = 0.9,
            trafficPressure = 0.1,
            parkedCarDensity = 0.1
        )

        val results = ranker.rank(listOf(tight, open), queryAt("2026-06-01T10:00:00"))

        assertEquals("open", results.first().segment.id)
        assertTrue(results.first().availabilityScore > results.last().availabilityScore)
    }

    private fun queryAt(start: String, radiusMiles: Double = 3.0): ParkingQuery {
        return ParkingQuery(
            origin = origin,
            start = LocalDateTime.parse(start),
            duration = Duration.ofHours(4),
            vehicleProfile = VehicleProfile.Normal,
            radiusMiles = radiusMiles
        )
    }

    private fun segment(
        id: String,
        center: LatLng,
        baseAvailability: Double = 0.74,
        trafficPressure: Double = 0.2,
        parkedCarDensity: Double = 0.2,
        rules: List<ParkingRule> = listOf(ParkingRule(RuleKind.FreeParking, label = "Free curb."))
    ): CurbSegment {
        return CurbSegment(
            id = id,
            street = "$id St",
            crossStreet = "near Test Ave",
            center = center,
            polyline = listOf(center),
            parkableFeet = 120,
            baseAvailability = baseAvailability,
            trafficPressure = trafficPressure,
            parkedCarDensity = parkedCarDensity,
            rules = rules,
            source = "test"
        )
    }
}
