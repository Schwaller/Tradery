package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aggregated trade statistics for a single log10 notional bucket.
 * Immutable value object — merge via merge() to combine windows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SizeBucket(
    int tradeCount,
    double totalVolume,
    double buyVolume,
    double sellVolume
) {

    public static final SizeBucket EMPTY = new SizeBucket(0, 0, 0, 0);

    @JsonIgnore
    public double delta() {
        return buyVolume - sellVolume;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return tradeCount == 0;
    }

    public SizeBucket merge(SizeBucket other) {
        return new SizeBucket(
            tradeCount + other.tradeCount,
            totalVolume + other.totalVolume,
            buyVolume + other.buyVolume,
            sellVolume + other.sellVolume
        );
    }
}
