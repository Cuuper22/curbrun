package com.cuuper.sfpark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cuuper.sfpark.BuildConfig
import com.cuuper.sfpark.domain.CurbSegment
import com.cuuper.sfpark.domain.LatLng
import com.cuuper.sfpark.domain.ParkingRule
import com.cuuper.sfpark.domain.RuleKind
import com.cuuper.sfpark.domain.TimeWindow
import java.io.File

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

    /**
     * Extracts the bundled SQLite asset once per app version. A small version
     * marker makes the copy robust to APK compression (where the asset stream's
     * reported length can differ from the on-disk file) and guarantees a fresh
     * copy whenever a new build ships an updated database.
     */
    private fun copyAssetDb(): File? {
        val assetName = "curbrun.sqlite"
        val out = File(context.noBackupFilesDir, assetName)
        val marker = File(context.noBackupFilesDir, "$assetName.version")
        val version = BuildConfig.VERSION_NAME
        return runCatching {
            val cachedVersion = marker.takeIf { it.exists() }?.readText()
            if (!out.exists() || cachedVersion != version) {
                context.assets.open(assetName).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                marker.writeText(version)
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
                        polyline = CurbAssetParsing.decodePolyline(cursor.getString(5), LatLng(lat, lng)),
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
                        days = CurbAssetParsing.decodeDays(cursor.getString(2)),
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
}
