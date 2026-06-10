package com.cuuper.sfpark.data

import com.cuuper.sfpark.domain.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class CurbAssetParsingTest {
    private val fallback = LatLng(37.7749, -122.4194)

    @Test
    fun decodePolylineParsesGeoJsonLngLatPairsIntoLatLng() {
        val points = CurbAssetParsing.decodePolyline("[[-122.41,37.77],[-122.42,37.78]]", fallback)

        assertEquals(2, points.size)
        assertEquals(37.77, points[0].latitude, 1e-9)
        assertEquals(-122.41, points[0].longitude, 1e-9)
        assertEquals(37.78, points[1].latitude, 1e-9)
        assertEquals(-122.42, points[1].longitude, 1e-9)
    }

    @Test
    fun decodePolylineFallsBackWhenBlankOrUnparseable() {
        assertEquals(listOf(fallback), CurbAssetParsing.decodePolyline(null, fallback))
        assertEquals(listOf(fallback), CurbAssetParsing.decodePolyline("", fallback))
        assertEquals(listOf(fallback), CurbAssetParsing.decodePolyline("not-a-polyline", fallback))
    }

    @Test
    fun decodeDaysParsesKnownTokens() {
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            CurbAssetParsing.decodeDays("[\"mon\",\"fri\"]")
        )
        assertEquals(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            CurbAssetParsing.decodeDays("sat,sun")
        )
    }

    @Test
    fun decodeDaysFallsBackToEveryDayWhenBlankOrUnknown() {
        val everyDay = DayOfWeek.entries.toSet()
        assertEquals(everyDay, CurbAssetParsing.decodeDays(null))
        assertEquals(everyDay, CurbAssetParsing.decodeDays(""))
        assertEquals(everyDay, CurbAssetParsing.decodeDays("holiday"))
    }
}
