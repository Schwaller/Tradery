package com.tradery.charts.indicator;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Self-contained indicator computation.
 * Each subclass knows how to compute one indicator type independently.
 * No centralized facade - each indicator is autonomous.
 *
 * @param <T> The result type (e.g., double[] for SMA, MacdResult for MACD)
 */
public abstract class IndicatorCompute<T> {

    /**
     * Unique cache key for deduplication (e.g., "sma:20", "macd:12:26:9").
     * If two overlays need the same indicator, the pool computes it once.
     */
    public abstract String key();

    /**
     * Compute the indicator values. Called on a background thread.
     * Each implementation creates its own IndicatorEngine instance.
     *
     * @param candles   The candle data
     * @param timeframe The timeframe string (e.g., "1h")
     * @return The computed result
     */
    public abstract T compute(List<Candle> candles, String timeframe);
}
