package com.cuuper.sfpark.data

import com.cuuper.sfpark.domain.LatLng
import java.time.DayOfWeek

/**
 * Pure parsing helpers for the bundled curb SQLite asset.
 *
 * These are intentionally free of Android dependencies so they can be unit
 * tested on the JVM (see CurbAssetParsingTest) instead of only through an
 * instrumented database read.
 */
internal object CurbAssetParsing {
    private val polylinePointRegex =
        Regex("""\[\s*(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)\s*]""")

    private val dayTokens = mapOf(
        "mon" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )

    /**
     * Decodes a GeoJSON-style `[[lng,lat],...]` polyline string into ordered
     * [LatLng] points. Falls back to a single [fallback] point when the value
     * is blank or contains no parseable coordinate pairs.
     */
    fun decodePolyline(raw: String?, fallback: LatLng): List<LatLng> {
        if (raw.isNullOrBlank()) return listOf(fallback)
        return polylinePointRegex.findAll(raw).map {
            val lng = it.groupValues[1].toDouble()
            val lat = it.groupValues[2].toDouble()
            LatLng(lat, lng)
        }.toList().ifEmpty { listOf(fallback) }
    }

    /**
     * Decodes a day-of-week token blob (e.g. `["mon","fri"]`) into a set of
     * [DayOfWeek]. Unknown or blank input is treated as "every day" so a rule
     * is never silently dropped.
     */
    fun decodeDays(raw: String?): Set<DayOfWeek> {
        if (raw.isNullOrBlank()) return DayOfWeek.entries.toSet()
        val lower = raw.lowercase()
        return dayTokens.filterKeys { it in lower }.values.toSet()
            .ifEmpty { DayOfWeek.entries.toSet() }
    }
}
