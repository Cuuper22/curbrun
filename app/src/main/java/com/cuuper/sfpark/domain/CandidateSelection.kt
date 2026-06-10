package com.cuuper.sfpark.domain

/**
 * Chooses which candidate stays selected after a rerank.
 *
 * When [preserveSelection] is set and the previously selected segment is still
 * legal (still present in [candidates]), it is kept so the curb the user is
 * driving toward doesn't jump around as the curb clock ticks. Otherwise the
 * closest-ranked candidate is selected. Returns null when there are no
 * candidates.
 */
fun resolveSelectedCandidate(
    candidates: List<ParkingCandidate>,
    previousSelectedId: String?,
    preserveSelection: Boolean
): ParkingCandidate? {
    if (preserveSelection) {
        return candidates.firstOrNull { it.segment.id == previousSelectedId }
            ?: candidates.firstOrNull()
    }
    return candidates.firstOrNull()
}
