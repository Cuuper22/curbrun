package com.cuuper.sfpark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CandidateSelectionTest {
    @Test
    fun keepsPreviousSelectionWhenStillPresent() {
        val candidates = listOf(candidate("a"), candidate("b"))

        val selected = resolveSelectedCandidate(candidates, previousSelectedId = "b", preserveSelection = true)

        assertEquals("b", selected?.segment?.id)
    }

    @Test
    fun fallsBackToClosestWhenPreviousSelectionDisappears() {
        val candidates = listOf(candidate("a"), candidate("b"))

        val selected = resolveSelectedCandidate(candidates, previousSelectedId = "gone", preserveSelection = true)

        assertEquals("a", selected?.segment?.id)
    }

    @Test
    fun selectsClosestWhenNotPreserving() {
        val candidates = listOf(candidate("a"), candidate("b"))

        val selected = resolveSelectedCandidate(candidates, previousSelectedId = "b", preserveSelection = false)

        assertEquals("a", selected?.segment?.id)
    }

    @Test
    fun returnsNullWhenThereAreNoCandidates() {
        assertNull(resolveSelectedCandidate(emptyList(), previousSelectedId = "a", preserveSelection = true))
    }

    private fun candidate(id: String): ParkingCandidate {
        val center = LatLng(37.0, -122.0)
        return ParkingCandidate(
            segment = CurbSegment(id, "St", "near", center, listOf(center), 100, 0.7, 0.2, 0.2, emptyList()),
            distanceMiles = 0.0,
            estimatedSpaces = 2,
            availabilityScore = 0.7,
            legalUntil = LocalDateTime.parse("2026-06-02T12:00:00"),
            nextRestrictionLabel = null,
            reasons = emptyList(),
            riskFactors = emptyList()
        )
    }
}
