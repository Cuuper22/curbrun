package com.cuuper.sfpark.data

import com.cuuper.sfpark.domain.CurbSegment
import com.cuuper.sfpark.domain.LatLng
import com.cuuper.sfpark.domain.ParkingRule
import com.cuuper.sfpark.domain.RuleKind
import com.cuuper.sfpark.domain.TimeWindow
import java.time.DayOfWeek

class CurbRepository {
    fun segments(): List<CurbSegment> = seedSegments
}

private val weekdays = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private fun window(days: Set<DayOfWeek>, start: String, end: String): TimeWindow {
    fun parse(value: String): Int {
        val parts = value.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
    return TimeWindow(days, parse(start), parse(end))
}

private val seedSegments = listOf(
    CurbSegment(
        id = "sf-richmond-14th-balboa",
        street = "14th Ave",
        crossStreet = "near Balboa St",
        center = LatLng(37.77618, -122.47234),
        polyline = listOf(LatLng(37.77602, -122.47238), LatLng(37.77636, -122.47229)),
        parkableFeet = 186,
        baseAvailability = 0.78,
        trafficPressure = 0.34,
        parkedCarDensity = 0.47,
        rules = listOf(
            ParkingRule(RuleKind.FreeParking, label = "Unmetered free curb."),
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = window(setOf(DayOfWeek.MONDAY), "09:00", "11:00"),
                label = "Street cleaning Monday 9-11 AM."
            )
        )
    ),
    CurbSegment(
        id = "sf-sunset-lawton-30th",
        street = "Lawton St",
        crossStreet = "near 30th Ave",
        center = LatLng(37.75763, -122.48861),
        polyline = listOf(LatLng(37.75765, -122.48902), LatLng(37.75759, -122.48821)),
        parkableFeet = 242,
        baseAvailability = 0.86,
        trafficPressure = 0.22,
        parkedCarDensity = 0.36,
        rules = listOf(
            ParkingRule(RuleKind.FreeParking, label = "Long unmetered curb with low pressure."),
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = window(setOf(DayOfWeek.THURSDAY), "12:00", "14:00"),
                label = "Street cleaning Thursday 12-2 PM."
            )
        )
    ),
    CurbSegment(
        id = "sf-noe-elizabeth-diamond",
        street = "Elizabeth St",
        crossStreet = "near Diamond St",
        center = LatLng(37.75186, -122.43616),
        polyline = listOf(LatLng(37.75184, -122.43656), LatLng(37.7519, -122.43576)),
        parkableFeet = 118,
        baseAvailability = 0.52,
        trafficPressure = 0.58,
        parkedCarDensity = 0.69,
        rules = listOf(
            ParkingRule(RuleKind.FreeParking, label = "Free curb outside active sweep windows."),
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = window(setOf(DayOfWeek.WEDNESDAY), "08:00", "10:00"),
                label = "Street cleaning Wednesday 8-10 AM."
            ),
            ParkingRule(
                kind = RuleKind.TimeLimit,
                window = window(weekdays, "08:00", "18:00"),
                maxStayMinutes = 120,
                label = "2 hr non-permit limit weekdays."
            )
        )
    ),
    CurbSegment(
        id = "sf-mission-shotwell-24th",
        street = "Shotwell St",
        crossStreet = "near 24th St",
        center = LatLng(37.75249, -122.41518),
        polyline = listOf(LatLng(37.75221, -122.41527), LatLng(37.75278, -122.41509)),
        parkableFeet = 176,
        baseAvailability = 0.47,
        trafficPressure = 0.77,
        parkedCarDensity = 0.76,
        rules = listOf(
            ParkingRule(RuleKind.FreeParking, label = "Free curb, high competition."),
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = window(setOf(DayOfWeek.FRIDAY), "07:00", "09:00"),
                label = "Street cleaning Friday 7-9 AM."
            )
        )
    ),
    CurbSegment(
        id = "sf-soma-4th-brannan-metered",
        street = "4th St",
        crossStreet = "near Brannan St",
        center = LatLng(37.77964, -122.39823),
        polyline = listOf(LatLng(37.77945, -122.39846), LatLng(37.77982, -122.39802)),
        parkableFeet = 88,
        baseAvailability = 0.42,
        trafficPressure = 0.9,
        parkedCarDensity = 0.82,
        rules = listOf(
            ParkingRule(
                kind = RuleKind.PaidMeter,
                window = window(weekdays + DayOfWeek.SATURDAY, "09:00", "18:00"),
                label = "Paid meter active."
            )
        )
    ),
    CurbSegment(
        id = "sf-presidio-heights-locust",
        street = "Locust St",
        crossStreet = "near Sacramento St",
        center = LatLng(37.78788, -122.45135),
        polyline = listOf(LatLng(37.7877, -122.45143), LatLng(37.78805, -122.45128)),
        parkableFeet = 152,
        baseAvailability = 0.66,
        trafficPressure = 0.41,
        parkedCarDensity = 0.58,
        rules = listOf(
            ParkingRule(RuleKind.FreeParking, label = "Unmetered curb with moderate pressure."),
            ParkingRule(
                kind = RuleKind.StreetCleaning,
                window = window(setOf(DayOfWeek.TUESDAY), "10:00", "12:00"),
                label = "Street cleaning Tuesday 10 AM-12 PM."
            )
        )
    )
)
