package com.cuuper.sfpark.data

import android.annotation.SuppressLint
import android.content.Context
import com.cuuper.sfpark.domain.LatLng
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {
    @SuppressLint("MissingPermission")
    suspend fun currentOrDefault(): LatLng = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation
            .addOnSuccessListener { location ->
                cont.resume(
                    if (location != null && location.isNearSanFrancisco()) {
                        LatLng(location.latitude, location.longitude)
                    }
                    else defaultSfLocation
                )
            }
            .addOnFailureListener { cont.resume(defaultSfLocation) }
    }

    companion object {
        val defaultSfLocation = LatLng(37.7749, -122.4194)
    }
}

private fun android.location.Location.isNearSanFrancisco(): Boolean {
    return latitude in 37.68..37.84 && longitude in -122.54..-122.34
}
