package com.cuuper.sfpark.domain

import kotlin.math.max
import kotlin.math.min

class CandidateRanker(
    private val evaluator: RuleEvaluator = RuleEvaluator()
) {
    fun rank(segments: List<CurbSegment>, query: ParkingQuery): List<ParkingCandidate> {
        return segments.mapNotNull { segment ->
            val distance = query.origin.distanceMilesTo(segment.center)
            if (distance > query.radiusMiles) return@mapNotNull null

            when (val eligibility = evaluator.evaluate(segment, query)) {
                is Eligibility.Blocked -> null
                is Eligibility.Allowed -> {
                    val availability = computeAvailability(segment, distance)
                    ParkingCandidate(
                        segment = segment,
                        distanceMiles = distance,
                        estimatedSpaces = eligibility.estimatedSpaces,
                        availabilityScore = availability,
                        legalUntil = eligibility.legalUntil,
                        nextRestrictionLabel = eligibility.nextRestrictionLabel,
                        reasons = eligibility.reasons,
                        riskFactors = eligibility.riskFactors
                    )
                }
            }
        }.sortedWith(
            compareBy<ParkingCandidate> { it.distanceMiles + ((1.0 - it.availabilityScore) * 0.18) }
                .thenByDescending { it.availabilityScore }
                .thenByDescending { it.estimatedSpaces }
        )
    }

    private fun computeAvailability(segment: CurbSegment, distanceMiles: Double): Double {
        val capacityBonus = segment.measuredSpaces?.let { min(0.08, it * 0.002) } ?: 0.0
        val raw = segment.baseAvailability + capacityBonus -
            (segment.trafficPressure * 0.22) -
            (segment.parkedCarDensity * 0.28) -
            (max(0.0, distanceMiles - 0.2) * 0.04)
        return min(0.98, max(0.32, raw))
    }
}
