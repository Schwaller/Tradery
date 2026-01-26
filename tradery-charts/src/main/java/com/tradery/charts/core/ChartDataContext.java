package com.tradery.charts.core;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Immutable data context for chart updates.
 * Contains all data needed to render a chart.
 *
 * <p>This record is used to pass data to chart components during updates,
 * ensuring thread-safe data access.</p>
 */
public record ChartDataContext(
    List<Candle> candles,
    IndicatorEngine indicatorEngine,
    String symbol,
    String timeframe,
    long startTime,
    long endTime
) {
    /**
     * Create a context from a ChartDataProvider.
     */
    public static ChartDataContext from(ChartDataProvider provider) {
        return new ChartDataContext(
            provider.getCandles(),
            provider.getIndicatorEngine(),
            provider.getSymbol(),
            provider.getTimeframe(),
            provider.getStartTime(),
            provider.getEndTime()
        );
    }

    /**
     * Check if this context has valid candle data.
     */
    public boolean hasCandles() {
        return candles != null && !candles.isEmpty();
    }

    /**
     * Get the number of candles.
     */
    public int candleCount() {
        return candles != null ? candles.size() : 0;
    }

    /**
     * Get interval in milliseconds based on timeframe.
     */
    public long getIntervalMs() {
        return switch (timeframe) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "2h" -> 7_200_000L;
            case "4h" -> 14_400_000L;
            case "6h" -> 21_600_000L;
            case "8h" -> 28_800_000L;
            case "12h" -> 43_200_000L;
            case "1d" -> 86_400_000L;
            default -> 3_600_000L;
        };
    }
}
