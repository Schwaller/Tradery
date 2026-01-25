package com.tradery.core.model;

/**
 * Analysis result for a single phase showing performance metrics
 * when the phase is active vs inactive at trade entry.
 */
public record PhaseAnalysisResult(
    String phaseId,
    String phaseName,
    String phaseCategory,

    // Trades when phase was active at entry
    int tradesInPhase,
    int winsInPhase,
    double winRateInPhase,
    double totalReturnInPhase,
    double profitFactorInPhase,

    // Trades when phase was NOT active at entry
    int tradesOutOfPhase,
    int winsOutOfPhase,
    double winRateOutOfPhase,
    double totalReturnOutOfPhase,
    double profitFactorOutOfPhase,

    // Recommendation based on comparison
    Recommendation recommendation,
    double confidenceScore  // 0-1 based on sample size and metric difference
) {

    public enum Recommendation {
        REQUIRE,   // Performance significantly better when phase is active
        EXCLUDE,   // Performance significantly worse when phase is active
        NEUTRAL    // No significant difference
    }

    /**
     * Get win rate difference (in-phase minus out-of-phase).
     * Positive means phase is beneficial.
     */
    public double winRateDifference() {
        return winRateInPhase - winRateOutOfPhase;
    }

    /**
     * Get return difference (in-phase minus out-of-phase).
     * Positive means phase is beneficial.
     */
    public double returnDifference() {
        return totalReturnInPhase - totalReturnOutOfPhase;
    }

    /**
     * Get profit factor difference (in-phase minus out-of-phase).
     * Positive means phase is beneficial.
     */
    public double profitFactorDifference() {
        return profitFactorInPhase - profitFactorOutOfPhase;
    }
}
