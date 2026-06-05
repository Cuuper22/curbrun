package com.cuuper.sfpark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.cuuper.sfpark.domain.ParkingCandidate
import com.cuuper.sfpark.ui.CurbRunApp
import com.cuuper.sfpark.ui.ParkingViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ParkingViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshLocationAndRank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationIfNeeded()
        setContent {
            CurbRunApp(
                viewModel = viewModel,
                onNavigate = ::openNavigation,
                onNavigateRoute = ::openSearchRoute
            )
        }
    }

    private fun requestLocationIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun openNavigation(candidate: ParkingCandidate) {
        val lat = candidate.segment.center.latitude
        val lng = candidate.segment.center.longitude
        val uri = "google.navigation:q=$lat,$lng&mode=d".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        val fallback = Intent(
            Intent.ACTION_VIEW,
            "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving".toUri()
        )
        runCatching { startActivity(intent) }.getOrElse { startActivity(fallback) }
    }

    private fun openSearchRoute(candidates: List<ParkingCandidate>) {
        if (candidates.isEmpty()) return
        val destination = candidates.last().segment.center
        val waypoints = candidates.dropLast(1).take(8).joinToString("|") {
            "${it.segment.center.latitude},${it.segment.center.longitude}"
        }
        val uri = Uri.Builder()
            .scheme("https")
            .authority("www.google.com")
            .path("maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("travelmode", "driving")
            .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
            .apply {
                if (waypoints.isNotBlank()) appendQueryParameter("waypoints", waypoints)
            }
            .build()
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
