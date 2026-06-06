package com.cuuper.sfpark.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuuper.sfpark.data.AssetCurbRepository
import com.cuuper.sfpark.data.LocationProvider
import com.cuuper.sfpark.data.ParkingPreferences
import com.cuuper.sfpark.domain.CandidateRanker
import com.cuuper.sfpark.domain.LatLng
import com.cuuper.sfpark.domain.ParkingCandidate
import com.cuuper.sfpark.domain.ParkingQuery
import com.cuuper.sfpark.domain.SearchAnchor
import com.cuuper.sfpark.domain.SearchRoutePlanner
import com.cuuper.sfpark.domain.SearchRouteStop
import com.cuuper.sfpark.domain.VehicleProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

data class ParkingUiState(
    val origin: LatLng = LocationProvider.defaultSfLocation,
    val originLabel: String = SearchAnchor.sfCenter.label,
    val durationHours: Double = 4.0,
    val radiusMiles: Double = 6.0,
    val vehicleProfile: VehicleProfile = VehicleProfile.Normal,
    val candidates: List<ParkingCandidate> = emptyList(),
    val routeStops: List<SearchRouteStop> = emptyList(),
    val selected: ParkingCandidate? = null,
    val isRefreshing: Boolean = false,
    val lastUpdatedLabel: String = "Bundled DB",
    val error: String? = null
)

class ParkingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AssetCurbRepository(application)
    private val preferences = ParkingPreferences(application)
    private val ranker = CandidateRanker()
    private val routePlanner = SearchRoutePlanner()
    private val locationProvider = LocationProvider(application)
    private val dataset by lazy { repository.dataset() }

    private val savedPreferences = preferences.load()
    private val _uiState = MutableStateFlow(
        ParkingUiState(
            origin = savedPreferences.anchor.center,
            originLabel = savedPreferences.anchor.label,
            durationHours = savedPreferences.durationHours,
            radiusMiles = savedPreferences.radiusMiles,
            vehicleProfile = savedPreferences.vehicleProfile
        )
    )
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    private var rerankJob: Job? = null

    init {
        rerank(preserveSelection = false)
        startLiveCurbClock()
    }

    fun setDuration(hours: Double) {
        val normalized = hours.coerceIn(0.5, 72.0)
        preferences.saveDuration(normalized)
        _uiState.update { it.copy(durationHours = normalized) }
        rerank(preserveSelection = true)
    }

    fun setRadius(miles: Double) {
        val normalized = miles.coerceIn(0.5, 8.0)
        preferences.saveRadius(normalized)
        _uiState.update { it.copy(radiusMiles = normalized) }
        rerank(preserveSelection = true)
    }

    fun setVehicle(profile: VehicleProfile) {
        preferences.saveVehicle(profile)
        _uiState.update { it.copy(vehicleProfile = profile) }
        rerank(preserveSelection = true)
    }

    fun select(candidate: ParkingCandidate) {
        _uiState.update { it.copy(selected = candidate) }
    }

    fun setSearchAnchor(anchor: SearchAnchor) {
        preferences.saveAnchor(anchor)
        _uiState.update { it.copy(origin = anchor.center, originLabel = anchor.label, error = null) }
        rerank(preserveSelection = false)
    }

    fun refreshLocationAndRank() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val origin = runCatching { locationProvider.currentOrDefault() }
                .getOrElse { LocationProvider.defaultSfLocation }
            _uiState.update {
                it.copy(
                    origin = origin,
                    originLabel = "GPS",
                    isRefreshing = false,
                    lastUpdatedLabel = "${dataset.label} • live curb clock"
                )
            }
            rerank(preserveSelection = true)
        }
    }

    private fun startLiveCurbClock() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                rerank(preserveSelection = true)
            }
        }
    }

    /**
     * Reranks candidates for the current query. The legality/ranking pass and
     * the first (lazy) SQLite load run on [Dispatchers.Default] so the main
     * thread stays free; the previous in-flight pass is cancelled so rapid
     * slider changes and the live clock tick can't post stale results.
     */
    private fun rerank(preserveSelection: Boolean) {
        val snapshot = _uiState.value
        val query = ParkingQuery(
            origin = snapshot.origin,
            start = LocalDateTime.now(),
            duration = Duration.ofMinutes((snapshot.durationHours * 60).toLong()),
            vehicleProfile = snapshot.vehicleProfile,
            radiusMiles = snapshot.radiusMiles
        )
        rerankJob?.cancel()
        rerankJob = viewModelScope.launch {
            val (candidates, routeStops) = withContext(Dispatchers.Default) {
                val ranked = ranker.rank(dataset.segments, query)
                ranked to routePlanner.plan(snapshot.origin, ranked)
            }
            val selected = if (preserveSelection) {
                candidates.firstOrNull { it.segment.id == snapshot.selected?.segment?.id } ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            }
            _uiState.update {
                it.copy(
                    candidates = candidates,
                    routeStops = routeStops,
                    selected = selected,
                    lastUpdatedLabel = "${dataset.label} • live curb clock",
                    error = if (candidates.isEmpty()) "No legal free curb segments found in this radius." else null
                )
            }
        }
    }
}
