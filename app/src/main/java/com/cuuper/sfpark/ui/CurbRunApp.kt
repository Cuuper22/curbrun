package com.cuuper.sfpark.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuuper.sfpark.domain.CandidateConfidenceTier
import com.cuuper.sfpark.domain.CurbClockUrgency
import com.cuuper.sfpark.domain.ParkingCandidate
import com.cuuper.sfpark.domain.SearchAnchor
import com.cuuper.sfpark.domain.VehicleProfile
import com.cuuper.sfpark.domain.confidenceSnapshot
import com.cuuper.sfpark.domain.curbClockSnapshot
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

internal val Ink = Color(0xFF161A18)
internal val Road = Color(0xFF303936)
internal val Mint = Color(0xFF8FF2BD)
internal val Lime = Color(0xFFCAF56B)
internal val Ember = Color(0xFFFF845E)
internal val Steel = Color(0xFF7A8580)
internal val Panel = Color(0xFFFAFCF6)

@Composable
fun CurbRunApp(
    viewModel: ParkingViewModel,
    onNavigate: (ParkingCandidate) -> Unit,
    onNavigateRoute: (List<ParkingCandidate>) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            RealCurbMap(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
            Header(
                state = state,
                onRefresh = viewModel::refreshLocationAndRank,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(14.dp)
            )
            CommandSheet(
                state = state,
                onDuration = viewModel::setDuration,
                onRadius = viewModel::setRadius,
                onVehicle = viewModel::setVehicle,
                onSearchAnchor = viewModel::setSearchAnchor,
                onSelect = viewModel::select,
                onNavigate = onNavigate,
                onNavigateRoute = onNavigateRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun Header(state: ParkingUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Panel.copy(alpha = 0.96f))
            .border(1.dp, Color(0x22303936), RoundedCornerShape(26.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Ink),
            contentAlignment = Alignment.Center
        ) {
            Text("CR", color = Mint, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CurbRun", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "${state.candidates.size} free routes • ${state.lastUpdatedLabel}",
                color = Steel,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PulseButton(
            label = if (state.isRefreshing) "Locking" else "Refresh",
            active = state.isRefreshing,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            }
        )
    }
}

@Composable
private fun CurbMap(state: ParkingUiState, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "map pulse").animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val selectedId = state.selected?.segment?.id
    Canvas(modifier = modifier.background(Road)) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF43534A), Road),
                center = Offset(size.width * 0.55f, size.height * 0.2f),
                radius = size.maxDimension
            )
        )
        val roadPaint = Color(0xFF51615B)
        for (i in 0..9) {
            val y = size.height * (0.12f + i * 0.09f)
            drawLine(
                color = roadPaint.copy(alpha = 0.34f),
                start = Offset(0f, y),
                end = Offset(size.width, y + ((i % 3) - 1) * 62f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0..7) {
            val x = size.width * (0.08f + i * 0.13f)
            drawLine(
                color = roadPaint.copy(alpha = 0.24f),
                start = Offset(x, 0f),
                end = Offset(x + ((i % 2) * 90f), size.height),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        val candidates = state.candidates.take(20)
        val points = candidates.map { it.segment.center } + state.origin
        val minLat = points.minOfOrNull { it.latitude } ?: state.origin.latitude
        val maxLat = points.maxOfOrNull { it.latitude } ?: state.origin.latitude
        val minLng = points.minOfOrNull { it.longitude } ?: state.origin.longitude
        val maxLng = points.maxOfOrNull { it.longitude } ?: state.origin.longitude
        fun project(lat: Double, lng: Double): Offset {
            val lngSpan = max(0.0008, maxLng - minLng)
            val latSpan = max(0.0008, maxLat - minLat)
            val x = ((lng - minLng) / lngSpan).toFloat()
            val y = (1.0 - ((lat - minLat) / latSpan)).toFloat()
            return Offset(
                x = size.width * (0.14f + x * 0.72f),
                y = size.height * (0.16f + y * 0.58f)
            )
        }
        candidates.forEachIndexed { index, candidate ->
            val isSelected = candidate.segment.id == selectedId
            val center = project(candidate.segment.center.latitude, candidate.segment.center.longitude)
            val x = center.x
            val y = center.y
            val color = if (isSelected) Lime else Mint
            val width = if (isSelected) 13f else 8f
            drawLine(
                color = color.copy(alpha = if (isSelected) 0.96f else 0.62f),
                start = Offset(x - 56f, y + 24f),
                end = Offset(x + 92f, y - 34f),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
            if (isSelected) {
                drawCircle(
                    color = Lime.copy(alpha = 0.16f * (1f - pulse)),
                    radius = 72f + pulse * 64f,
                    center = Offset(x + 92f, y - 34f)
                )
                drawCircle(color = Lime, radius = 13f, center = Offset(x + 92f, y - 34f))
            }
        }
        val origin = project(state.origin.latitude, state.origin.longitude)
        drawCircle(
            color = Color(0xFFEBFFF4).copy(alpha = 0.18f * (1f - pulse)),
            radius = 80f + pulse * 80f,
            center = origin
        )
        drawCircle(Color(0xFFEBFFF4), radius = 16f, center = origin)
        drawCircle(Mint, radius = 7f, center = origin)
    }
}

@Composable
private fun CommandSheet(
    state: ParkingUiState,
    onDuration: (Double) -> Unit,
    onRadius: (Double) -> Unit,
    onVehicle: (VehicleProfile) -> Unit,
    onSearchAnchor: (SearchAnchor) -> Unit,
    onSelect: (ParkingCandidate) -> Unit,
    onNavigate: (ParkingCandidate) -> Unit,
    onNavigateRoute: (List<ParkingCandidate>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Panel)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 42.dp, height = 5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFD3DBD1))
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Park free for", color = Steel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                AnimatedContent(state.durationHours, label = "duration") { hours ->
                    Text(
                        if (hours % 1.0 == 0.0) "${hours.toInt()} hours" else "${hours.toInt()}h 30m",
                        color = Ink,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            state.selected?.let {
                Button(
                    modifier = Modifier.semantics {
                        contentDescription = "Start multi-stop search route with ${state.routeStops.size} legal free parking stops"
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateRoute(state.routeStops.map { stop -> stop.candidate })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Mint),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Start route", fontWeight = FontWeight.Bold)
                }
            }
        }
        Slider(
            value = state.durationHours.toFloat(),
            onValueChange = { onDuration((it * 2).toInt() / 2.0) },
            valueRange = 0.5f..72f,
            steps = 142,
            colors = SliderDefaults.colors(
                thumbColor = Mint,
                activeTrackColor = Mint,
                inactiveTrackColor = Color(0xFFDCE8D5),
                activeTickColor = Ink.copy(alpha = 0.28f),
                inactiveTickColor = Ink.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Parking duration"
                    stateDescription = "Park free for ${state.durationHours.formatDurationForSpeech()}"
                }
        )
        VehicleChips(state.vehicleProfile, onVehicle)
        Spacer(Modifier.height(10.dp))
        RadiusControl(state.radiusMiles, onRadius)
        Spacer(Modifier.height(10.dp))
        SearchAnchorChips(state.originLabel, onSearchAnchor)
        Spacer(Modifier.height(10.dp))
        DataSafetyNotice()
        Spacer(Modifier.height(10.dp))
        state.selected?.let {
            SelectedCandidateBand(candidate = it, onNavigate = { onNavigate(it) })
            Spacer(Modifier.height(10.dp))
        }
        if (state.routeStops.isNotEmpty()) {
            RouteQueue(state = state, onSelect = onSelect, onNavigateRoute = onNavigateRoute)
            Spacer(Modifier.height(10.dp))
        }
        AnimatedVisibility(
            visible = state.error != null && state.candidates.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Text(
                text = state.error ?: "",
                color = Ember,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.height(240.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.candidates.isEmpty()) {
                item("empty-results") {
                    EmptyResultsCard(
                        durationHours = state.durationHours,
                        radiusMiles = state.radiusMiles,
                        onShorten = { onDuration(state.durationHours.shorterRecoveryWindow()) },
                        onWiden = { onRadius(state.radiusMiles.widerRecoveryRadius()) }
                    )
                }
            } else {
                items(state.candidates, key = { it.segment.id }) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        selected = candidate.segment.id == state.selected?.segment?.id,
                        onClick = { onSelect(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResultsCard(
    durationHours: Double,
    radiusMiles: Double,
    onShorten: () -> Unit,
    onWiden: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shorterWindow = durationHours.shorterRecoveryWindow()
    val widerRadius = radiusMiles.widerRecoveryRadius()
    val canShorten = shorterWindow < durationHours
    val canWiden = widerRadius > radiusMiles
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFF3EA), Color(0xFFF3FAEA))
                )
            )
            .border(1.dp, Ember.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .semantics {
                contentDescription = "No legal free curb segments found. Try shortening the parking window or widening the search radius."
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Ember.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = Ember, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("No free curb found", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    "This window is too constrained nearby.",
                    color = Steel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "For ${durationHours.formatDurationForSpeech()} within ${radiusMiles.formatRadius()}, every matched segment has an active restriction or is outside range.",
            color = Steel,
            fontSize = 12.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.semantics {
                    contentDescription = "Shorten parking window and search again"
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShorten()
                },
                enabled = canShorten,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Mint),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (canShorten) "Try ${shorterWindow.formatDurationShort()}" else "Min time",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
            Button(
                modifier = Modifier.semantics {
                    contentDescription = "Widen search radius and search again"
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onWiden()
                },
                enabled = canWiden,
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (canWiden) "Widen to ${widerRadius.formatRadius()}" else "Max radius",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DataSafetyNotice() {
    Text(
        text = "Local ranking. Location and saved choices stay on this device; posted curb signs override app guidance.",
        color = Steel,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEFF4EA))
            .semantics {
                contentDescription = "Privacy and accuracy notice. Location and saved choices stay on this device. Posted curb signs override app guidance."
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun RadiusControl(radiusMiles: Double, onRadius: (Double) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Search radius"
                stateDescription = "Search within ${radiusMiles.formatRadius()}"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Search radius", color = Steel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(radiusMiles.formatRadius(), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = radiusMiles.toFloat(),
            onValueChange = { onRadius(((it * 2).toInt() / 2.0).coerceIn(0.5, 8.0)) },
            valueRange = 0.5f..8.0f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = Ink,
                activeTrackColor = Steel,
                inactiveTrackColor = Color(0xFFDCE8D5),
                activeTickColor = Mint.copy(alpha = 0.35f),
                inactiveTickColor = Ink.copy(alpha = 0.10f)
            ),
            modifier = Modifier
                .height(28.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun SearchAnchorChips(selectedLabel: String, onSearchAnchor: (SearchAnchor) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column {
        Text("Search near", color = Steel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchAnchor.neighborhoods, key = { it.id }) { anchor ->
                val active = selectedLabel == anchor.label
                val bg by animateColorAsState(if (active) Ink else Color(0xFFE8EDE3), label = "anchor bg")
                val fg by animateColorAsState(if (active) Mint else Ink, label = "anchor fg")
                Text(
                    text = anchor.label,
                    color = fg,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(bg)
                        .semantics {
                            contentDescription = "Search near ${anchor.label}"
                            role = Role.Button
                            selected = active
                            stateDescription = if (active) "Selected" else "Not selected"
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearchAnchor(anchor)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RouteQueue(
    state: ParkingUiState,
    onSelect: (ParkingCandidate) -> Unit,
    onNavigateRoute: (List<ParkingCandidate>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEAF3E4))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Search route", color = Ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    "${state.routeStops.size} stops • ${state.routeStops.sumOf { it.legMiles }.formatMiles()} mi sweep",
                    color = Steel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                modifier = Modifier.semantics {
                    contentDescription = "Run multi-stop route through ${state.routeStops.size} legal free parking stops"
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateRoute(state.routeStops.map { it.candidate })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Run", fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            state.routeStops.take(5).forEach { stop ->
                val selected = state.selected?.segment?.id == stop.candidate.segment.id
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selected) Ink else Panel)
                        .border(1.dp, if (selected) Mint else Color(0xFFD5DFCF), CircleShape)
                        .semantics {
                            contentDescription = "Route stop ${stop.order}, ${stop.candidate.segment.street}, ${stop.candidate.distanceMiles.formatMiles()} miles away"
                            role = Role.Button
                            this.selected = selected
                            stateDescription = if (selected) "Selected" else "Not selected"
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(stop.candidate)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stop.order.toString(),
                        color = if (selected) Mint else Ink,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedCandidateBand(candidate: ParkingCandidate, onNavigate: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val clock = candidate.curbClockSnapshot()
    val confidence = candidate.confidenceSnapshot()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink)
            .semantics {
                contentDescription = "Selected parking candidate. ${candidate.accessibilitySummary(clock.label)}"
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                candidate.segment.street,
                color = Panel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${candidate.distanceMiles.formatMiles()} mi • ${(candidate.availabilityScore * 100).toInt()}% confidence • ${candidate.estimatedSpaces.spacesLabel()}",
                color = Mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Free until ${candidate.legalUntil.format(DateTimeFormatter.ofPattern("EEE h:mm a"))}",
                color = Lime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ClockBadge(clock.label, clock.urgency)
                    ConfidenceBadge(confidence.label, confidence.tier)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    EvidenceBadge("free")
                    EvidenceBadge(candidate.segment.source.provenanceLabel())
                    EvidenceBadge("${candidate.riskFactors.size} risk")
                }
            }
            Spacer(Modifier.height(6.dp))
            val notes = (
                listOfNotNull(candidate.nextRestrictionLabel?.let { "Next: $it" }) +
                    candidate.riskFactors +
                    candidate.reasons
                ).take(2).joinToString("  ")
            if (notes.isNotBlank()) {
                Text(
                    notes,
                    color = Color(0xFFD9E4D5),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                confidence.signCheck,
                color = Color(0xFFBFCBC5),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Button(
            modifier = Modifier.semantics {
                contentDescription = "Navigate to selected parking candidate on ${candidate.segment.street}"
            },
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigate()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Go", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ConfidenceBadge(label: String, tier: CandidateConfidenceTier) {
    val color = when (tier) {
        CandidateConfidenceTier.Strong -> Mint
        CandidateConfidenceTier.Caution -> Lime
        CandidateConfidenceTier.Competitive -> Ember
    }
    Text(
        text = label,
        color = Ink,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ClockBadge(label: String, urgency: CurbClockUrgency) {
    val color = when (urgency) {
        CurbClockUrgency.Comfortable -> Mint
        CurbClockUrgency.Watch -> Lime
        CurbClockUrgency.Tight -> Ember
    }
    Text(
        text = label,
        color = Ink,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun EvidenceBadge(label: String) {
    Text(
        text = label,
        color = Mint,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFF26332D))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun VehicleChips(selected: VehicleProfile, onVehicle: (VehicleProfile) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleProfile.entries.forEach { profile ->
            val active = profile == selected
            val bg by animateColorAsState(if (active) Ink else Color(0xFFE8EDE3), label = "chip")
            val fg by animateColorAsState(if (active) Mint else Ink, label = "chip text")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .semantics {
                        contentDescription = "Vehicle size ${profile.label}"
                        role = Role.Button
                        this.selected = active
                        stateDescription = if (active) "Selected" else "Not selected"
                    }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onVehicle(profile)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CarGlyph(active)
                Spacer(Modifier.width(7.dp))
                Text(profile.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CarGlyph(active: Boolean) {
    Canvas(Modifier.size(22.dp, 15.dp)) {
        val color = if (active) Mint else Steel
        drawRoundRect(color = color, topLeft = Offset(2f, 5f), size = androidx.compose.ui.geometry.Size(18f, 7f))
        drawCircle(color = Ink.copy(alpha = if (active) 1f else 0.7f), radius = 2.3f, center = Offset(6f, 13f))
        drawCircle(color = Ink.copy(alpha = if (active) 1f else 0.7f), radius = 2.3f, center = Offset(16f, 13f))
    }
}

@Composable
private fun CandidateCard(candidate: ParkingCandidate, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 1f else 0.985f, animationSpec = tween(220), label = "card scale")
    val border = if (selected) Lime else Color(0xFFDCE3D8)
    val clock = candidate.curbClockSnapshot()
    val confidence = candidate.confidenceSnapshot()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color(0xFFEDFCD8) else Color(0xFFF7FAF2))
            .border(1.dp, border, RoundedCornerShape(22.dp))
            .semantics {
                contentDescription = candidate.accessibilitySummary(clock.label)
                role = Role.Button
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvailabilityDial(candidate.availabilityScore)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.segment.street, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(
                candidate.segment.crossStreet,
                color = Steel,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${confidence.label} • ${clock.label} • ${candidate.segment.source.provenanceLabel()} • ${candidate.distanceMiles.formatMiles()} mi • ${candidate.estimatedSpaces.spacesLabel()} • until ${
                    candidate.legalUntil.format(DateTimeFormatter.ofPattern("EEE h:mm a"))
                }",
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            val explanation = candidate.nextRestrictionLabel?.let { "Next: $it" }
                ?: candidate.riskFactors.firstOrNull()
                ?: candidate.reasons.firstOrNull()
            if (explanation != null) {
                Text(
                    explanation,
                    color = if (candidate.riskFactors.isNotEmpty()) Ember else Steel,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text("›", color = Ink, fontSize = (30 * scale).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AvailabilityDial(score: Double) {
    val sweep by animateFloatAsState(score.toFloat() * 300f, tween(520, easing = FastOutSlowInEasing), label = "dial")
    Box(
        Modifier
            .size(54.dp)
            .semantics {
                contentDescription = "Availability confidence"
                stateDescription = "${(score * 100).toInt()} percent"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFFD6DED2),
                startAngle = 150f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 7f, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(8f))
            )
            drawArc(
                color = if (score > 0.65) Mint else if (score > 0.45) Lime else Ember,
                startAngle = 150f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
        }
        Text("${(score * 100).toInt()}", color = Ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun PulseButton(label: String, active: Boolean, onClick: () -> Unit) {
    val alpha by rememberInfiniteTransition(label = "button pulse").animateFloat(
        0.45f,
        1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Button(
        modifier = Modifier.semantics {
            contentDescription = if (active) "Refreshing location and parking results" else "Refresh location and parking results"
        },
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Mint.copy(alpha = alpha) else Ink,
            contentColor = if (active) Ink else Mint
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

internal fun Double.formatMiles(): String = if (this < 0.1) "<0.1" else "%.1f".format(this)

private fun Double.formatRadius(): String = if (this < 1.0) "%.1f mi".format(this) else "%.1f mi".format(this)

private fun Double.shorterRecoveryWindow(): Double = (this / 2.0).coerceAtLeast(0.5)

private fun Double.widerRecoveryRadius(): Double = (this + 1.0).coerceAtMost(8.0)

private fun Double.formatDurationShort(): String {
    val hours = toInt()
    val minutes = ((this - hours) * 60).toInt()
    return when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

private fun Int.spacesLabel(): String = if (this == 1) "1 est. space" else "$this est. spaces"

private fun Double.formatDurationForSpeech(): String {
    val minutes = (this * 60).toInt()
    return if (minutes % 60 == 0) {
        "${minutes / 60} hours"
    } else {
        "${minutes / 60} hours ${minutes % 60} minutes"
    }
}

private fun ParkingCandidate.accessibilitySummary(clockLabel: String): String {
    val confidence = confidenceSnapshot()
    val risk = if (riskFactors.isEmpty()) "no flagged risks" else "${riskFactors.size} risk factors"
    val next = nextRestrictionLabel?.let { " Next restriction: $it." } ?: ""
    return "${segment.street} near ${segment.crossStreet}. ${confidence.label}. $clockLabel. ${distanceMiles.formatMiles()} miles away. " +
        "${(availabilityScore * 100).toInt()} percent confidence. ${estimatedSpaces.spacesLabel()}. " +
        "Source: ${segment.source.provenanceLabel()}. $risk. ${confidence.signCheck}$next"
}

private fun String.provenanceLabel(): String {
    return when (this) {
        "sfmta_digital_curb" -> "SFMTA"
        "local_seed" -> "seed"
        else -> split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { char -> char.uppercase() } }
            .ifBlank { "source" }
    }
}
