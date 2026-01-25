package com.tradery.core.model;

/**
 * Progress information for data fetching operations.
 * Used to report progress during long-running API calls.
 */
public record FetchProgress(
    int fetchedCandles,     // Number of candles fetched so far
    int estimatedTotal,     // Estimated total candles to fetch
    String message          // Human-readable status message
) {
    /**
     * Calculate the percentage complete.
     */
    public int percentComplete() {
        if (estimatedTotal == 0) return 0;
        return Math.min(100, (fetchedCandles * 100) / estimatedTotal);
    }

    /**
     * Create a progress instance for starting a fetch.
     */
    public static FetchProgress starting(String symbol, String resolution) {
        return new FetchProgress(0, 0, "Starting fetch for " + symbol + " " + resolution + "...");
    }

    /**
     * Create a progress instance for completion.
     */
    public static FetchProgress complete(int totalFetched) {
        return new FetchProgress(totalFetched, totalFetched, "Complete");
    }

    /**
     * Create a progress instance for cancellation.
     */
    public static FetchProgress cancelled(int fetchedSoFar) {
        return new FetchProgress(fetchedSoFar, fetchedSoFar, "Cancelled");
    }
}
