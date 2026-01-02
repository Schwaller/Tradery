package com.tradery.model;

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
    String positionSizingType,   // "fixed_percent", "fixed_amount"
    double positionSizingValue,  // Percent or dollar amount
    double commission            // Per-trade commission
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
            "fixed_percent",
            10.0,
            0.001  // 0.1% commission
        );
    }
}
