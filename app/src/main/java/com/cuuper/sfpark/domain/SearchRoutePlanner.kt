package com.cuuper.sfpark.domain

class SearchRoutePlanner {
    fun plan(origin: LatLng, candidates: List<ParkingCandidate>, maxStops: Int = 7): List<SearchRouteStop> {
        val pool = candidates
            .take(24)
            .toMutableList()
        val stops = mutableListOf<SearchRouteStop>()
        var cursor = origin

        while (pool.isNotEmpty() && stops.size < maxStops) {
            val next = pool.minBy { candidate ->
                val leg = cursor.distanceMilesTo(candidate.segment.center)
                leg + ((1.0 - candidate.availabilityScore) * 0.22) - (candidate.estimatedSpaces * 0.015)
            }
            pool.remove(next)
            val legMiles = cursor.distanceMilesTo(next.segment.center)
            stops += SearchRouteStop(stops.size + 1, next, legMiles)
            cursor = next.segment.center
        }

        return stops
    }
}
