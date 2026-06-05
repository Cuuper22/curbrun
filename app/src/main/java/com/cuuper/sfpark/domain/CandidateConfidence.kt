package com.cuuper.sfpark.domain

enum class CandidateConfidenceTier {
    Strong,
    Caution,
    Competitive
}

data class CandidateConfidence(
    val tier: CandidateConfidenceTier,
    val label: String,
    val signCheck: String
)

fun ParkingCandidate.confidenceSnapshot(): CandidateConfidence {
    val tier = when {
        availabilityScore >= 0.68 && riskFactors.isEmpty() -> CandidateConfidenceTier.Strong
        availabilityScore < 0.48 || riskFactors.size >= 2 -> CandidateConfidenceTier.Competitive
        else -> CandidateConfidenceTier.Caution
    }
    return when (tier) {
        CandidateConfidenceTier.Strong -> CandidateConfidence(
            tier = tier,
            label = "Strong match",
            signCheck = "Still verify posted signs before leaving the car."
        )
        CandidateConfidenceTier.Caution -> CandidateConfidence(
            tier = tier,
            label = "Check signs",
            signCheck = "Rules look clear for your window, but confirm the curb sign."
        )
        CandidateConfidenceTier.Competitive -> CandidateConfidence(
            tier = tier,
            label = "High competition",
            signCheck = "Legal fit is weaker or crowded; inspect the block carefully."
        )
    }
}
