package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Result of a backtest run.
 * Stored as JSON in ~/.tradery/results/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BacktestResult(
    String strategyId,
    String strategyName,
    BacktestConfig config,
    List<Trade> trades,
    PerformanceMetrics metrics,
    long startTime,
    long endTime,
    int barsProcessed,
    long duration,
    List<String> errors
) {
    /**
     * Check if the backtest completed successfully
     */
    public boolean isSuccessful() {
        return errors == null || errors.isEmpty();
    }

    /**
     * Get summary string
     */
    public String getSummary() {
        return String.format(
            "%s: %d trades, %.1f%% win rate, %.2f profit factor, %+.2f%% return",
            strategyName,
            metrics.totalTrades(),
            metrics.winRate(),
            metrics.profitFactor(),
            metrics.totalReturnPercent()
        );
    }
}
