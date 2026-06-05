package com.cuuper.sfpark.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.cuuper.sfpark.domain.ParkingCandidate
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

@Composable
fun RealCurbMap(
    state: ParkingUiState,
    modifier: Modifier = Modifier
) {
    val mapState = remember { OsmdroidMapState() }
    Box(modifier = modifier.background(Road)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Live San Francisco curb map with ${state.candidates.size} free parking candidates"
                },
            factory = { context ->
                Configuration.getInstance().userAgentValue = "${context.packageName}/0.1.0"
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    minZoomLevel = 11.0
                    maxZoomLevel = 20.0
                    overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                    controller.setZoom(15.4)
                    controller.setCenter(GeoPoint(state.origin.latitude, state.origin.longitude))
                    mapState.map = this
                    applyCandidateOverlays(context, state)
                }
            },
            update = { map ->
                mapState.map = map
                map.applyCandidateOverlays(map.context, state)
                state.selected?.segment?.center?.let {
                    map.controller.animateTo(GeoPoint(it.latitude, it.longitude), 16.3, 650L)
                }
            }
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        Color(0xAA161A18),
                        Color.Transparent,
                        Color.Transparent,
                        Color(0x66161A18)
                    )
                )
            )
            drawCircle(
                color = Mint.copy(alpha = 0.14f),
                radius = 110f,
                center = Offset(size.width * 0.5f, size.height * 0.45f)
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { mapState.map?.onDetach() }
    }
    LaunchedEffect(Unit) {
        mapState.map?.onResume()
    }
}

private class OsmdroidMapState {
    var map: MapView? = null
}

private fun MapView.applyCandidateOverlays(context: Context, state: ParkingUiState) {
    val selectedId = state.selected?.segment?.id
    overlays.removeAll { it is Polyline || it is Marker }

    state.candidates.take(30).forEach { candidate ->
        val selected = candidate.segment.id == selectedId
        overlays += candidate.toPolyline(context, selected)
    }

    if (state.routeStops.isNotEmpty()) {
        overlays += Polyline().apply {
            outlinePaint.color = android.graphics.Color.rgb(202, 245, 107)
            outlinePaint.strokeWidth = 5f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f)
            outlinePaint.alpha = 210
            setPoints(
                listOf(GeoPoint(state.origin.latitude, state.origin.longitude)) +
                    state.routeStops.map { GeoPoint(it.candidate.segment.center.latitude, it.candidate.segment.center.longitude) }
            )
            title = "Search route"
        }
    }

    state.selected?.let { selected ->
        overlays += Marker(this).apply {
            position = GeoPoint(selected.segment.center.latitude, selected.segment.center.longitude)
            title = selected.segment.street
            subDescription = selected.segment.crossStreet
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
    overlays += Marker(this).apply {
        position = GeoPoint(state.origin.latitude, state.origin.longitude)
        title = "You"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
    invalidate()
}

private fun ParkingCandidate.toPolyline(context: Context, selected: Boolean): Polyline {
    return Polyline().apply {
        outlinePaint.color = android.graphics.Color.rgb(
            if (selected) 202 else 143,
            if (selected) 245 else 242,
            if (selected) 107 else 189
        )
        outlinePaint.strokeWidth = if (selected) 15f else 8f
        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        outlinePaint.alpha = if (selected) 245 else 180
        setPoints(segment.polyline.map { GeoPoint(it.latitude, it.longitude) })
        title = segment.street
        subDescription = "${distanceMiles.formatMiles()} mi • ${(availabilityScore * 100).toInt()} confidence"
    }
}
