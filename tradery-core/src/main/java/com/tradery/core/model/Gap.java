package com.tradery.core.model;

/**
 * Represents a contiguous range of missing candle data.
 */
public record Gap(
    long startTimestamp,  // First missing candle timestamp
    long endTimestamp,    // Last missing candle timestamp
    int missingCount      // Number of candles in this gap
) {
    /**
     * Get the duration of this gap in milliseconds.
     */
    public long durationMs() {
        return endTimestamp - startTimestamp;
    }
}
