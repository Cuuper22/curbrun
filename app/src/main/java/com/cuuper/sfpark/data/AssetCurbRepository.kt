package com.cuuper.sfpark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cuuper.sfpark.domain.CurbSegment
import com.cuuper.sfpark.domain.LatLng
import com.cuuper.sfpark.domain.ParkingRule
import com.cuuper.sfpark.domain.RuleKind
import com.cuuper.sfpark.domain.TimeWindow
import java.io.File
import java.time.DayOfWeek

data class CurbDataset(
    val segments: List<CurbSegment>,
    val label: String
)

class AssetCurbRepository(private val context: Context) {
    fun dataset(): CurbDataset {
        val fallback = CurbRepository().segments()
        val dbFile = copyAssetDb() ?: return CurbDataset(fallback, "Seed fallback")
        return runCatching { readSegments(dbFile) }
            .map { CurbDataset(it, "%,d curbs".format(it.size)) }
            .getOrElse { CurbDataset(fallback, "Seed fallback") }
            .let { if (it.segments.isEmpty()) CurbDataset(fallback, "Seed fallback") else it }
    }

    private fun copyAssetDb(): File? {
        val assetName = "curbrun.sqlite"
        val out = File(context.noBackupFilesDir, assetName)
        return runCatching {
            context.assets.open(assetName).use { input ->
                if (!out.exists() || out.length() != input.available().toLong()) {
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            out
        }.getOrNull()
    }

    private fun readSegments(file: File): List<CurbSegment> {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            val rulesBySegment = readRules(it)
            val cursor = it.rawQuery(
                """
                select id, street, cross_street, latitude, longitude, polyline_json,
                       parkable_feet, base_availability, traffic_pressure, parked_car_density, source
                from curb_segment
                """.trimIndent(),
                null
            )
            cursor.use {
                val rows = mutableListOf<CurbSegment>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val lat = cursor.getDouble(3)
                    val lng = cursor.getDouble(4)
                    rows += CurbSegment(
                        id = id,
                        street = cursor.getString(1) ?: "SF curb",
                        crossStreet = cursor.getString(2) ?: "nearby",
                        center = LatLng(lat, lng),
                        polyline = decodePolyline(cursor.getString(5), LatLng(lat, lng)),
                        parkableFeet = cursor.getInt(6).coerceAtLeast(8),
                        baseAvailability = cursor.getDouble(7),
                        trafficPressure = cursor.getDouble(8),
                        parkedCarDensity = cursor.getDouble(9),
                        rules = rulesBySegment[id] ?: listOf(ParkingRule(RuleKind.FreeParking, label = "Free curb policy.")),
                        source = cursor.getString(10) ?: "sfmta_digital_curb"
                    )
                }
                return rows
            }
        }
    }

    private fun readRules(db: SQLiteDatabase): Map<String, List<ParkingRule>> {
        val cursor = db.rawQuery(
            """
            select segment_id, kind, days_json, start_minute, end_minute, max_stay_minutes, label
            from curb_rule
            """.trimIndent(),
            null
        )
        cursor.use {
            val rules = linkedMapOf<String, MutableList<ParkingRule>>()
            while (cursor.moveToNext()) {
                val segmentId = cursor.getString(0)
                val kind = runCatching { RuleKind.valueOf(cursor.getString(1)) }.getOrDefault(RuleKind.FreeParking)
                val window = if (!cursor.isNull(3) && !cursor.isNull(4)) {
                    TimeWindow(
                        days = decodeDays(cursor.getString(2)),
                        startMinute = cursor.getInt(3),
                        endMinute = cursor.getInt(4)
                    )
                } else {
                    null
                }
                val maxStay = if (cursor.isNull(5)) null else cursor.getInt(5)
                val label = cursor.getString(6) ?: kind.name
                rules.getOrPut(segmentId) { mutableListOf() } += ParkingRule(kind, window, maxStay, label)
            }
            return rules
        }
    }

    private fun decodePolyline(raw: String?, fallback: LatLng): List<LatLng> {
        if (raw.isNullOrBlank()) return listOf(fallback)
        val matches = Regex("""\[\s*(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)\s*]""").findAll(raw)
        return matches.map {
            val lng = it.groupValues[1].toDouble()
            val lat = it.groupValues[2].toDouble()
            LatLng(lat, lng)
        }.toList().ifEmpty { listOf(fallback) }
    }

    private fun decodeDays(raw: String?): Set<DayOfWeek> {
        if (raw.isNullOrBlank()) return DayOfWeek.entries.toSet()
        val lower = raw.lowercase()
        val pairs = mapOf(
            "mon" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY
        )
        return pairs.filterKeys { it in lower }.values.toSet().ifEmpty { DayOfWeek.entries.toSet() }
    }
}
