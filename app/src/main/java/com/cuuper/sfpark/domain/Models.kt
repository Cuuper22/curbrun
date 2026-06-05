package com.cuuper.sfpark.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double)

enum class VehicleProfile(val label: String, val minimumFeet: Int) {
    Compact("Compact", 14),
    Normal("Normal", 18),
    Large("Large", 22)
}

enum class RuleKind {
    PaidMeter,
    StreetCleaning,
    NoParking,
    TowAway,
    Loading,
    TimeLimit,
    ResidentialPermit,
    FreeParking
}

data class TimeWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int
) {
    fun overlaps(queryStart: LocalDateTime, queryEnd: LocalDateTime): Boolean {
        val normalizedStartMinute = startMinute.normalizedClockMinute()
        val normalizedEndMinute = endMinute.normalizedClockMinute()
        var cursor = queryStart.toLocalDate().atStartOfDay()
        val last = queryEnd.toLocalDate().plusDays(1).atStartOfDay()
        while (cursor.isBefore(last)) {
            val day = cursor.dayOfWeek
            if (day in days) {
                val start = cursor.plusMinutes(normalizedStartMinute.toLong())
                val end = if (normalizedEndMinute <= normalizedStartMinute) {
                    cursor.plusDays(1).plusMinutes(normalizedEndMinute.toLong())
                } else {
                    cursor.plusMinutes(normalizedEndMinute.toLong())
                }
                if (start < queryEnd && end > queryStart) return true
            }
            cursor = cursor.plusDays(1)
        }
        return false
    }

    fun nextStartAfter(queryStart: LocalDateTime, horizonDays: Long = 7): LocalDateTime? {
        val normalizedStartMinute = startMinute.normalizedClockMinute()
        var cursor = queryStart.toLocalDate().atStartOfDay()
        val last = queryStart.toLocalDate().plusDays(horizonDays + 1).atStartOfDay()
        var best: LocalDateTime? = null

        while (cursor.isBefore(last)) {
            if (cursor.dayOfWeek in days) {
                val start = cursor.plusMinutes(normalizedStartMinute.toLong())
                if (!start.isBefore(queryStart) && (best == null || start < best)) {
                    best = start
                }
            }
            cursor = cursor.plusDays(1)
        }

        return best
    }
}

data class ParkingRule(
    val kind: RuleKind,
    val window: TimeWindow? = null,
    val maxStayMinutes: Int? = null,
    val label: String
)

data class CurbSegment(
    val id: String,
    val street: String,
    val crossStreet: String,
    val center: LatLng,
    val polyline: List<LatLng>,
    val parkableFeet: Int,
    val baseAvailability: Double,
    val trafficPressure: Double,
    val parkedCarDensity: Double,
    val rules: List<ParkingRule>,
    val source: String = "local_seed"
)

data class ParkingQuery(
    val origin: LatLng,
    val start: LocalDateTime,
    val duration: Duration,
    val vehicleProfile: VehicleProfile,
    val radiusMiles: Double
)

data class ParkingCandidate(
    val segment: CurbSegment,
    val distanceMiles: Double,
    val estimatedSpaces: Int,
    val availabilityScore: Double,
    val legalUntil: LocalDateTime,
    val nextRestrictionLabel: String?,
    val reasons: List<String>,
    val riskFactors: List<String>
)

data class SearchRouteStop(
    val order: Int,
    val candidate: ParkingCandidate,
    val legMiles: Double
)

fun LatLng.distanceMilesTo(other: LatLng): Double {
    val earthRadiusMiles = 3958.7613
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMiles * c
}

private fun Int.normalizedClockMinute(): Int = if (this == 24 * 60) 0 else this
