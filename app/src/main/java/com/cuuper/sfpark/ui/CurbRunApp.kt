package com.cuuper.sfpark.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuuper.sfpark.domain.CandidateConfidenceTier
import com.cuuper.sfpark.domain.CurbClockUrgency
import com.cuuper.sfpark.domain.ParkingCandidate
import com.cuuper.sfpark.domain.SearchAnchor
import com.cuuper.sfpark.domain.VehicleProfile
import com.cuuper.sfpark.domain.confidenceSnapshot
import com.cuuper.sfpark.domain.curbClockSnapshot
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.roundToInt

// ── CurbRun "night-ops" identity ──────────────────────────────────────────────
internal val Ink = Color(0xFF070A09)        // base
internal val Coal = Color(0xFF0E1512)       // deck
internal val Carbon = Color(0xFF141D19)     // cards
internal val CarbonHi = Color(0xFF1B2A23)   // selected/elevated
internal val Volt = Color(0xFFCBFF46)       // signature accent — free / legal / go
internal val Mint = Color(0xFF5FE7A4)       // secondary success
internal val Ember = Color(0xFFFF6A4D)      // tight / blocked
internal val Amber = Color(0xFFFFC24B)      // watch
internal val Chalk = Color(0xFFF0F5EC)      // primary text
internal val Ash = Color(0xFF8A968F)        // secondary text
internal val AshDim = Color(0xFF59635D)     // tertiary text
internal val Hairline = Color(0x14FFFFFF)   // borders
// Aliases kept for RealCurbMap.kt compatibility.
internal val Road = Coal
internal val Lime = Volt
internal val Steel = Ash
internal val Panel = Carbon

private val Mono = FontFamily.Monospace

@Composable
fun CurbRunApp(
    viewModel: ParkingViewModel,
    onNavigate: (ParkingCandidate) -> Unit,
    onNavigateRoute: (List<ParkingCandidate>) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Live clock so the hero countdown ticks down second-by-second.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalDateTime.now()
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            RealCurbMap(state = state, modifier = Modifier.fillMaxSize())

            TopBar(
                state = state,
                onRefresh = viewModel::refreshLocationAndRank,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )

            ControlDeck(
                state = state,
                now = now,
                onDuration = viewModel::setDuration,
                onRadius = viewModel::setRadius,
                onVehicle = viewModel::setVehicle,
                onSearchAnchor = viewModel::setSearchAnchor,
                onSelect = viewModel::select,
                onNavigate = onNavigate,
                onNavigateRoute = onNavigateRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f)
            )
        }
    }
}

// ── Top bar: wordmark + live status + locate ──────────────────────────────────
@Composable
private fun TopBar(state: ParkingUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.copy(alpha = 0.82f))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Volt)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CURBRUN",
                color = Chalk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                "${state.candidates.size} FREE CURBS · ${state.lastUpdatedLabel.uppercase()}",
                color = Ash,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LocateButton(active = state.isRefreshing) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRefresh()
        }
    }
}

@Composable
private fun LocateButton(active: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "loc").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "locPulse"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Volt.copy(alpha = pulse) else Carbon)
            .clickable(
                onClickLabel = "Use my GPS location",
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (active) "LOCKING" else "LOCATE",
            color = if (active) Ink else Volt,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

// ── Control deck (bottom) ─────────────────────────────────────────────────────
@Composable
private fun ControlDeck(
    state: ParkingUiState,
    now: LocalDateTime,
    onDuration: (Double) -> Unit,
    onRadius: (Double) -> Unit,
    onVehicle: (VehicleProfile) -> Unit,
    onSearchAnchor: (SearchAnchor) -> Unit,
    onSelect: (ParkingCandidate) -> Unit,
    onNavigate: (ParkingCandidate) -> Unit,
    onNavigateRoute: (List<ParkingCandidate>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Coal)
            .border(1.dp, Hairline, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AshDim)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val selected = state.selected
                if (selected != null) {
                    HeroSpot(candidate = selected, now = now, onNavigate = { onNavigate(selected) })
                } else {
                    EmptyDeck(state = state, onDuration = onDuration, onRadius = onRadius)
                }
            }

            item {
                IntentSection(
                    durationHours = state.durationHours,
                    radiusMiles = state.radiusMiles,
                    vehicle = state.vehicleProfile,
                    onDuration = onDuration,
                    onRadius = onRadius,
                    onVehicle = onVehicle
                )
            }

            item {
                NeighborhoodRail(selectedLabel = state.originLabel, onSearchAnchor = onSearchAnchor)
            }

            if (state.routeStops.size >= 2) {
                item {
                    RouteStrip(
                        state = state,
                        onNavigateRoute = { onNavigateRoute(state.routeStops.map { it.candidate }) }
                    )
                }
            }

            if (state.candidates.isNotEmpty()) {
                item {
                    SectionLabel("LEGAL FREE CURBS", "${state.candidates.size}")
                }
                items(state.candidates, key = { it.segment.id }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        selected = candidate.segment.id == state.selected?.segment?.id,
                        now = now,
                        onClick = { onSelect(candidate) }
                    )
                }
            }

            item { DataSafetyFootnote() }
        }
    }
}

// ── Hero: the selected spot, with the giant live curb-clock ────────────────────
@Composable
private fun HeroSpot(candidate: ParkingCandidate, now: LocalDateTime, onNavigate: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val clock = candidate.curbClockSnapshot(now)
    val confidence = candidate.confidenceSnapshot()
    val accent = urgencyColor(clock.urgency)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(CarbonHi, Carbon))
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .semantics { contentDescription = candidate.accessibilitySummary(clock.label) }
            .padding(18.dp)
    ) {
        Text(
            "YOUR SPOT",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            candidate.segment.street,
            color = Chalk,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${candidate.segment.crossStreet} · ${candidate.distanceMiles.formatMiles()} mi away",
            color = Ash,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("FREE FOR", color = AshDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                BigClock(minutes = clock.remainingMinutes, accent = accent)
            }
            AvailabilityRing(score = candidate.availabilityScore)
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfidenceTag(label = confidence.label, tier = confidence.tier)
            candidate.segment.measuredSpaces?.let { Tag("~$it surveyed", Mint) }
            Tag(candidate.segment.source.provenanceLabel(), Ash)
        }

        candidate.nextRestrictionLabel?.let {
            Spacer(Modifier.height(10.dp))
            Text("Next: $it", color = Ash, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(16.dp))
        VoltButton(
            label = "NAVIGATE",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigate()
            }
        )
    }
}

@Composable
private fun BigClock(minutes: Long, accent: Color) {
    val (big, small) = clockParts(minutes)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(big, color = accent, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
        if (small.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(small, color = Chalk, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun AvailabilityRing(score: Double) {
    val sweep by animateFloatAsState(
        targetValue = score.toFloat().coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ring"
    )
    val ringColor = when {
        score >= 0.66 -> Volt
        score >= 0.5 -> Mint
        else -> Amber
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8f
            drawArc(
                color = Hairline,
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - stroke * 2, size.height - stroke * 2)
            )
            drawArc(
                color = ringColor,
                startAngle = 130f,
                sweepAngle = 280f * sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke, stroke),
                size = Size(size.width - stroke * 2, size.height - stroke * 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(score * 100).roundToInt()}", color = Chalk, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
            Text("OPEN", color = AshDim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyDeck(state: ParkingUiState, onDuration: (Double) -> Unit, onRadius: (Double) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Carbon)
            .border(1.dp, Ember.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text("NO LEGAL FREE CURB", color = Ember, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            state.error ?: "Nothing clears your whole window in this radius. Loosen the search:",
            color = Ash,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChunkyAction("HALF THE TIME", Modifier.weight(1f)) {
                onDuration((state.durationHours / 2.0).coerceAtLeast(0.5))
            }
            ChunkyAction("WIDER RADIUS", Modifier.weight(1f)) {
                onRadius((state.radiusMiles + 1.0).coerceAtMost(8.0))
            }
        }
    }
}

// ── Intent: duration / radius / vehicle ───────────────────────────────────────
@Composable
private fun IntentSection(
    durationHours: Double,
    radiusMiles: Double,
    vehicle: VehicleProfile,
    onDuration: (Double) -> Unit,
    onRadius: (Double) -> Unit,
    onVehicle: (VehicleProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Carbon)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            RowLabel("PARK FOR", durationHours.formatDurationLong())
            Spacer(Modifier.height(10.dp))
            DurationRail(selectedHours = durationHours, onDuration = onDuration)
        }
        Column {
            RowLabel("SEARCH RADIUS", "${radiusMiles.formatMiles()} mi")
            Slider(
                value = radiusMiles.toFloat(),
                onValueChange = { onRadius(it.toDouble()) },
                valueRange = 0.5f..8f,
                modifier = Modifier.semantics { contentDescription = "Search radius ${radiusMiles.formatMiles()} miles" },
                colors = SliderDefaults.colors(
                    thumbColor = Volt,
                    activeTrackColor = Volt,
                    inactiveTrackColor = Hairline
                )
            )
        }
        Column {
            RowLabel("VEHICLE", vehicle.label.uppercase())
            Spacer(Modifier.height(10.dp))
            VehicleToggle(selectedProfile = vehicle, onVehicle = onVehicle)
        }
    }
}

private val durationPresets = listOf(1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 24.0, 48.0, 72.0)

@Composable
private fun DurationRail(selectedHours: Double, onDuration: (Double) -> Unit) {
    val haptic = LocalHapticFeedback.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(durationPresets) { preset ->
            val isOn = kotlin.math.abs(preset - selectedHours) < 0.01
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOn) Volt else CarbonHi)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDuration(preset)
                        }
                    )
                    .semantics { if (isOn) selected = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    preset.formatDurationShort(),
                    color = if (isOn) Ink else Chalk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = Mono
                )
            }
        }
    }
}

@Composable
private fun VehicleToggle(selectedProfile: VehicleProfile, onVehicle: (VehicleProfile) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonHi)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VehicleProfile.entries.forEach { profile ->
            val isOn = profile == selectedProfile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOn) Volt else Color.Transparent)
                    .clickable(
                        role = Role.RadioButton,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onVehicle(profile)
                        }
                    )
                    .semantics {
                        selected = isOn
                        stateDescription = if (isOn) "Selected" else "Not selected"
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    profile.label.uppercase(),
                    color = if (isOn) Ink else Ash,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Neighborhood quick-jump ───────────────────────────────────────────────────
@Composable
private fun NeighborhoodRail(selectedLabel: String, onSearchAnchor: (SearchAnchor) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val anchors = listOf(SearchAnchor.sfCenter) + SearchAnchor.neighborhoods
    Column {
        SectionLabel("JUMP TO", null)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(anchors) { anchor ->
                val isOn = anchor.label == selectedLabel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isOn) Mint.copy(alpha = 0.18f) else CarbonHi)
                        .border(1.dp, if (isOn) Mint else Hairline, RoundedCornerShape(20.dp))
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSearchAnchor(anchor)
                            }
                        )
                        .semantics { if (isOn) selected = true }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        anchor.label,
                        color = if (isOn) Mint else Chalk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Route sweep strip ─────────────────────────────────────────────────────────
@Composable
private fun RouteStrip(state: ParkingUiState, onNavigateRoute: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val miles = state.routeStops.sumOf { it.legMiles }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Carbon)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("SWEEP ROUTE", color = Volt, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                "${state.routeStops.size} stops · ${miles.formatMiles()} mi",
                color = Chalk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonHi)
                .clickable(role = Role.Button, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateRoute()
                })
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text("RUN", color = Volt, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

// ── Candidate row ─────────────────────────────────────────────────────────────
@Composable
private fun CandidateRow(candidate: ParkingCandidate, selected: Boolean, now: LocalDateTime, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val clock = candidate.curbClockSnapshot(now)
    val border by animateColorAsState(
        if (selected) urgencyColor(clock.urgency) else Hairline,
        label = "rowBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) CarbonHi else Carbon)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .semantics { contentDescription = candidate.accessibilitySummary(clock.label) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // distance chip
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Ink)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(candidate.distanceMiles.formatMiles(), color = Volt, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
            Text("MI", color = AshDim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.segment.street, color = Chalk, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniClock(label = clock.label, urgency = clock.urgency)
                Text(
                    candidate.spacesLabel(),
                    color = Ash,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        AvailabilityBar(score = candidate.availabilityScore)
    }
}

@Composable
private fun AvailabilityBar(score: Double) {
    val pct by animateFloatAsState(score.toFloat().coerceIn(0f, 1f), tween(500), label = "bar")
    val color = when {
        score >= 0.66 -> Volt
        score >= 0.5 -> Mint
        else -> Amber
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${(score * 100).roundToInt()}", color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(44.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(Hairline)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ── Small shared components ───────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ash, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Volt)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(trailing, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
            }
        }
    }
}

@Composable
private fun RowLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ash, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(value, color = Volt, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfidenceTag(label: String, tier: CandidateConfidenceTier) {
    val color = when (tier) {
        CandidateConfidenceTier.Strong -> Volt
        CandidateConfidenceTier.Caution -> Amber
        CandidateConfidenceTier.Competitive -> Ember
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun MiniClock(label: String, urgency: CurbClockUrgency) {
    val color = urgencyColor(urgency)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
    }
}

@Composable
private fun VoltButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Volt)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun ChunkyAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonHi)
            .border(1.dp, Volt.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Volt, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun DataSafetyFootnote() {
    Text(
        "Modeled availability from open SF data — not live occupancy. Always verify the posted curb sign before you leave the car.",
        color = AshDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ── helpers ───────────────────────────────────────────────────────────────────
private fun urgencyColor(urgency: CurbClockUrgency): Color = when (urgency) {
    CurbClockUrgency.Comfortable -> Volt
    CurbClockUrgency.Watch -> Amber
    CurbClockUrgency.Tight -> Ember
}

internal fun Double.formatMiles(): String = "%.1f".format(this)

private fun clockParts(minutes: Long): Pair<String, String> {
    if (minutes >= 7L * 24 * 60) return "7d+" to ""
    return when {
        minutes >= 24 * 60 -> "${minutes / (24 * 60)}d" to "${(minutes % (24 * 60)) / 60}h"
        minutes >= 60 -> "${minutes / 60}h" to "${minutes % 60}m"
        else -> "${minutes}m" to ""
    }
}

private fun Double.formatDurationShort(): String {
    val hours = this
    return when {
        hours >= 24 && hours % 24.0 == 0.0 -> "${(hours / 24).toInt()}d"
        hours < 1.0 -> "${(hours * 60).toInt()}m"
        hours % 1.0 == 0.0 -> "${hours.toInt()}h"
        else -> "%.1fh".format(hours)
    }
}

private fun Double.formatDurationLong(): String {
    val minutes = (this * 60).roundToInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h hr"
        else -> "$h hr $m min"
    }
}

private fun ParkingCandidate.spacesLabel(): String =
    if (estimatedSpaces == 1) "1 spot" else "$estimatedSpaces spots"

private fun String.provenanceLabel(): String = when (this) {
    "sfmta_digital_curb" -> "SFMTA curb"
    "local_seed" -> "Seed data"
    else -> "Curb data"
}

private fun ParkingCandidate.accessibilitySummary(clockLabel: String): String {
    val confidence = confidenceSnapshot()
    val spaces = if (estimatedSpaces == 1) "1 estimated space" else "$estimatedSpaces estimated spaces"
    return buildString {
        append(segment.street)
        append(", ${distanceMiles.formatMiles()} miles away. ")
        append("Free $clockLabel. ")
        append("${(availabilityScore * 100).roundToInt()} percent open, $spaces. ")
        append("${confidence.label}. ${confidence.signCheck}")
    }
}
