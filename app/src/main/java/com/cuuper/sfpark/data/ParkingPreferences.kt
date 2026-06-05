package com.cuuper.sfpark.data

import android.content.Context
import com.cuuper.sfpark.domain.SearchAnchor
import com.cuuper.sfpark.domain.VehicleProfile

data class SavedParkingPreferences(
    val durationHours: Double,
    val radiusMiles: Double,
    val vehicleProfile: VehicleProfile,
    val anchor: SearchAnchor
)

class ParkingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("parking_preferences", Context.MODE_PRIVATE)

    fun load(): SavedParkingPreferences {
        val duration = prefs.getFloat(KEY_DURATION_HOURS, 4.0f).toDouble().coerceIn(0.5, 72.0)
        val radius = prefs.getFloat(KEY_RADIUS_MILES, 6.0f).toDouble().coerceIn(0.5, 8.0)
        val vehicle = runCatching {
            VehicleProfile.valueOf(prefs.getString(KEY_VEHICLE_PROFILE, VehicleProfile.Normal.name) ?: VehicleProfile.Normal.name)
        }.getOrDefault(VehicleProfile.Normal)
        val anchor = SearchAnchor.byId(prefs.getString(KEY_ANCHOR_ID, SearchAnchor.sfCenter.id))
        return SavedParkingPreferences(duration, radius, vehicle, anchor)
    }

    fun saveDuration(hours: Double) {
        prefs.edit().putFloat(KEY_DURATION_HOURS, hours.coerceIn(0.5, 72.0).toFloat()).apply()
    }

    fun saveRadius(miles: Double) {
        prefs.edit().putFloat(KEY_RADIUS_MILES, miles.coerceIn(0.5, 8.0).toFloat()).apply()
    }

    fun saveVehicle(profile: VehicleProfile) {
        prefs.edit().putString(KEY_VEHICLE_PROFILE, profile.name).apply()
    }

    fun saveAnchor(anchor: SearchAnchor) {
        prefs.edit().putString(KEY_ANCHOR_ID, anchor.id).apply()
    }

    companion object {
        private const val KEY_DURATION_HOURS = "duration_hours"
        private const val KEY_RADIUS_MILES = "radius_miles"
        private const val KEY_VEHICLE_PROFILE = "vehicle_profile"
        private const val KEY_ANCHOR_ID = "anchor_id"
    }
}
