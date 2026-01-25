package com.tradery.core.model;

import java.time.YearMonth;
import java.util.List;

/**
 * Health status for a single month of candle data.
 * Used to track data completeness and identify gaps.
 */
public record DataHealth(
    String symbol,
    String resolution,
    YearMonth month,
    int expectedCandles,    // Based on resolution + days in month
    int actualCandles,      // What we have cached
    List<Gap> gaps,         // Specific missing ranges
    DataStatus status       // Overall status
) {
    /**
     * Calculate the percentage of data that is complete.
     */
    public double completenessPercent() {
        if (expectedCandles == 0) return 0.0;
        return (actualCandles * 100.0) / expectedCandles;
    }

    /**
     * Check if data is 100% complete.
     */
    public boolean isComplete() {
        return status == DataStatus.COMPLETE;
    }

    /**
     * Get total number of missing candles across all gaps.
     */
    public int totalMissingCandles() {
        return gaps.stream().mapToInt(Gap::missingCount).sum();
    }

    /**
     * Check if this is the current (possibly incomplete) month.
     */
    public boolean isCurrentMonth() {
        return month.equals(YearMonth.now());
    }
}
