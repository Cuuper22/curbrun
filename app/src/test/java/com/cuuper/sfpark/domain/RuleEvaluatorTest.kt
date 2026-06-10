package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

class RuleEvaluatorTest {
    private val evaluator = RuleEvaluator()

    @Test
    fun blocksStreetCleaningAcrossWholeRequestedWindow() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 9 * 60, 11 * 60),
                label = "Street cleaning Monday 9-11 AM."
            )
        )
        val query = queryAt("2026-06-01T08:30:00", hours = 3.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Blocked)
    }

    @Test
    fun allowsFreeCurbWhenCleaningDoesNotOverlap() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = TimeWindow(setOf(DayOfWeek.TUESDAY), 9 * 60, 11 * 60),
                label = "Street cleaning Tuesday 9-11 AM."
            )
        )
        val query = queryAt("2026-06-01T08:30:00", hours = 3.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
    }

    @Test
    fun blocksTimeLimitWhenDurationExceedsNonPermitLimit() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.TimeLimit,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 8 * 60, 18 * 60),
                maxStayMinutes = 120,
                label = "2 hr non-permit limit."
            )
        )
        val query = queryAt("2026-06-01T10:00:00", hours = 3.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Blocked)
    }

    @Test
    fun blocksPaidMeterWhenMeterWindowOverlaps() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.PaidMeter,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 9 * 60, 18 * 60),
                label = "Paid meter."
            )
        )
        val query = queryAt("2026-06-01T17:30:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Blocked)
    }

    @Test
    fun blocksOvernightWindowThatWrapsPastMidnight() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.NoParking,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 6 * 60),
                label = "No overnight parking."
            )
        )
        val query = queryAt("2026-06-01T21:30:00", hours = 2.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Blocked)
    }

    @Test
    fun treatsTwentyFourHundredAsMidnight() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.NoParking,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 24 * 60, 6 * 60),
                label = "No oversized vehicles midnight-6 AM."
            )
        )
        val query = queryAt("2026-06-01T01:00:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Blocked)
    }

    @Test
    fun reportsNextRestrictionAfterRequestedWindow() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 13 * 60, 15 * 60),
                label = "Street cleaning Monday 1-3 PM."
            )
        )
        val query = queryAt("2026-06-01T08:00:00", hours = 2.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        val allowed = result as Eligibility.Allowed
        assertEquals(LocalDateTime.parse("2026-06-01T13:00:00"), allowed.legalUntil)
        assertEquals("Street cleaning Monday 1-3 PM.", allowed.nextRestrictionLabel)
    }

    @Test
    fun reportsTimeLimitAsLegalClockWhenAllowed() {
        val segment = segmentWith(
            ParkingRule(
                kind = RuleKind.TimeLimit,
                window = TimeWindow(setOf(DayOfWeek.MONDAY), 8 * 60, 18 * 60),
                maxStayMinutes = 120,
                label = "2 hr non-permit limit."
            )
        )
        val query = queryAt("2026-06-01T09:15:00", hours = 1.5)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        val allowed = result as Eligibility.Allowed
        assertEquals(LocalDateTime.parse("2026-06-01T11:15:00"), allowed.legalUntil)
        assertEquals("2 hr non-permit limit.", allowed.nextRestrictionLabel)
        assertNotNull(allowed.riskFactors.firstOrNull())
    }

    @Test
    fun flagsTightBlockWhenModeledDensityIsHigh() {
        val segment = segmentWithDensity(0.7)
        val query = queryAt("2026-06-01T08:00:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        val allowed = result as Eligibility.Allowed
        assertTrue(allowed.riskFactors.any { it.contains("Modeled curb density") })
    }

    @Test
    fun doesNotFlagDensityRiskBelowThreshold() {
        val segment = segmentWithDensity(0.6)
        val query = queryAt("2026-06-01T08:00:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        val allowed = result as Eligibility.Allowed
        assertTrue(allowed.riskFactors.none { it.contains("Modeled curb density") })
    }

    @Test
    fun surfacesMeasuredCensusCapacityWhenPresent() {
        val segment = segmentWithDensity(0.2).copy(measuredSpaces = 24)
        val query = queryAt("2026-06-01T08:00:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        assertTrue((result as Eligibility.Allowed).reasons.any { it.contains("surveyed space") })
    }

    @Test
    fun omitsCensusReasonWhenCapacityAbsent() {
        val segment = segmentWithDensity(0.2)
        val query = queryAt("2026-06-01T08:00:00", hours = 1.0)

        val result = evaluator.evaluate(segment, query)

        assertTrue(result is Eligibility.Allowed)
        assertTrue((result as Eligibility.Allowed).reasons.none { it.contains("surveyed space") })
    }

    private fun segmentWithDensity(density: Double, vararg rules: ParkingRule): CurbSegment {
        return CurbSegment(
            id = "density",
            street = "Density St",
            crossStreet = "near Test Ave",
            center = LatLng(37.0, -122.0),
            polyline = listOf(LatLng(37.0, -122.0), LatLng(37.0001, -122.0001)),
            parkableFeet = 100,
            baseAvailability = 0.7,
            trafficPressure = 0.2,
            parkedCarDensity = density,
            rules = rules.toList()
        )
    }

    private fun segmentWith(vararg rules: ParkingRule): CurbSegment {
        return CurbSegment(
            id = "test",
            street = "Test St",
            crossStreet = "near Test Ave",
            center = LatLng(37.0, -122.0),
            polyline = listOf(LatLng(37.0, -122.0), LatLng(37.0001, -122.0001)),
            parkableFeet = 100,
            baseAvailability = 0.7,
            trafficPressure = 0.2,
            parkedCarDensity = 0.2,
            rules = rules.toList()
        )
    }

    private fun queryAt(start: String, hours: Double): ParkingQuery {
        return ParkingQuery(
            origin = LatLng(37.0, -122.0),
            start = LocalDateTime.parse(start),
            duration = Duration.ofMinutes((hours * 60).toLong()),
            vehicleProfile = VehicleProfile.Normal,
            radiusMiles = 1.0
        )
    }
}
