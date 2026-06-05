package com.cuuper.sfpark.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max

enum class CurbClockUrgency {
    Comfortable,
    Watch,
    Tight
}

data class CurbClockSnapshot(
    val remainingMinutes: Long,
    val label: String,
    val urgency: CurbClockUrgency
)

fun ParkingCandidate.curbClockSnapshot(now: LocalDateTime = LocalDateTime.now()): CurbClockSnapshot {
    val remaining = max(0, Duration.between(now, legalUntil).toMinutes())
    val label = when {
        remaining >= 7 * 24 * 60 -> "7d+ clear"
        remaining >= 24 * 60 -> "${remaining / (24 * 60)}d ${(remaining % (24 * 60)) / 60}h clear"
        remaining >= 60 -> "${remaining / 60}h ${remaining % 60}m clear"
        else -> "${remaining}m clear"
    }
    val urgency = when {
        remaining <= 60 -> CurbClockUrgency.Tight
        remaining <= 180 -> CurbClockUrgency.Watch
        else -> CurbClockUrgency.Comfortable
    }
    return CurbClockSnapshot(remaining, label, urgency)
}
