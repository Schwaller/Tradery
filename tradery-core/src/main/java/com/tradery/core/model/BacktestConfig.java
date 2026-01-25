package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for a backtest run
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BacktestConfig(
    String symbol,
    String resolution,
    long startDate,
    long endDate,
    double initialCapital,
    PositionSizingType positionSizingType,
    double positionSizingValue,
    double commission,
    MarketType marketType,
    double marginInterestHourly  // Hourly rate in percent (e.g., 0.00042 = 0.00042%/hr)
) {
    /**
     * Create default config
     */
    public static BacktestConfig defaults(String symbol, String resolution) {
        long now = System.currentTimeMillis();
        long oneYearAgo = now - (365L * 24 * 60 * 60 * 1000);

        return new BacktestConfig(
            symbol,
            resolution,
            oneYearAgo,
            now,
            10000.0,
            PositionSizingType.FIXED_PERCENT,
            10.0,
            0.001,
            MarketType.SPOT,
            0.00042  // ~0.00042%/hr default
        );
    }
}
