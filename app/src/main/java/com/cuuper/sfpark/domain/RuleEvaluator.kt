package com.cuuper.sfpark.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max

class RuleEvaluator {
    fun evaluate(segment: CurbSegment, query: ParkingQuery): Eligibility {
        val end = query.start.plus(query.duration)
        val reasons = mutableListOf<String>()
        val risks = mutableListOf<String>()
        val nextRestrictions = mutableListOf<NextRestriction>()

        if (segment.parkableFeet < query.vehicleProfile.minimumFeet) {
            return Eligibility.Blocked("Curb fragment is too short for ${query.vehicleProfile.label}.")
        }

        val estimatedSpaces = max(1, segment.parkableFeet / query.vehicleProfile.minimumFeet)
        reasons += "${segment.parkableFeet} ft usable curb, about $estimatedSpaces ${query.vehicleProfile.label.lowercase()} space(s)."

        for (rule in segment.rules) {
            val active = rule.window?.overlaps(query.start, end) ?: true
            if (!active) continue
            when (rule.kind) {
                RuleKind.PaidMeter -> return Eligibility.Blocked("Paid meter is active during your parking window.")
                RuleKind.StreetCleaning -> return Eligibility.Blocked("Street cleaning overlaps your requested ${query.duration.toHoursClean()} window.")
                RuleKind.NoParking -> return Eligibility.Blocked("No-parking rule is active.")
                RuleKind.TowAway -> return Eligibility.Blocked("Tow-away restriction overlaps your parking window.")
                RuleKind.Loading -> return Eligibility.Blocked("Loading/color-curb restriction overlaps your parking window.")
                RuleKind.TimeLimit, RuleKind.ResidentialPermit -> {
                    val maxStay = rule.maxStayMinutes
                    if (maxStay != null && query.duration.toMinutes() > maxStay) {
                        return Eligibility.Blocked("${rule.label} allows only ${maxStay / 60.0} hr for non-permit parking.")
                    }
                    risks += rule.label
                }
                RuleKind.FreeParking -> reasons += rule.label
            }
        }

        for (rule in segment.rules) {
            when (rule.kind) {
                RuleKind.PaidMeter,
                RuleKind.StreetCleaning,
                RuleKind.NoParking,
                RuleKind.TowAway,
                RuleKind.Loading -> {
                    rule.window?.nextStartAfter(query.start)?.let { start ->
                        if (!start.isBefore(end)) nextRestrictions += NextRestriction(start, rule.label)
                    }
                }
                RuleKind.TimeLimit,
                RuleKind.ResidentialPermit -> {
                    if (rule.window?.overlaps(query.start, end) == true && rule.maxStayMinutes != null) {
                        nextRestrictions += NextRestriction(
                            query.start.plusMinutes(rule.maxStayMinutes.toLong()),
                            rule.label
                        )
                    } else {
                        rule.window?.nextStartAfter(query.start)?.let { start ->
                            if (!start.isBefore(end)) nextRestrictions += NextRestriction(start, rule.label)
                        }
                    }
                }
                RuleKind.FreeParking -> Unit
            }
        }

        if (segment.parkedCarDensity > 0.72) risks += "Street View density suggests this block is usually tight."
        if (segment.trafficPressure > 0.7) risks += "High traffic pressure for this time/day."
        if (query.duration.toHours() >= 48) risks += "Long stay: re-check signs before leaving the car."

        val nextRestriction = nextRestrictions.minByOrNull { it.startsAt }
        val legalUntil = nextRestriction?.startsAt ?: query.start.plusDays(7)
        if (nextRestriction == null) {
            reasons += "No upcoming restriction found in the bundled 7-day curb clock."
        }

        return Eligibility.Allowed(
            estimatedSpaces = estimatedSpaces,
            legalUntil = legalUntil,
            nextRestrictionLabel = nextRestriction?.label,
            reasons = reasons,
            riskFactors = risks
        )
    }
}

sealed class Eligibility {
    data class Allowed(
        val estimatedSpaces: Int,
        val legalUntil: LocalDateTime,
        val nextRestrictionLabel: String?,
        val reasons: List<String>,
        val riskFactors: List<String>
    ) : Eligibility()

    data class Blocked(val reason: String) : Eligibility()
}

private data class NextRestriction(
    val startsAt: LocalDateTime,
    val label: String
)

private fun Duration.toHoursClean(): String {
    val minutes = toMinutes()
    return if (minutes % 60L == 0L) "${minutes / 60L} hr" else "${minutes / 60L} hr ${(minutes % 60L)} min"
}
