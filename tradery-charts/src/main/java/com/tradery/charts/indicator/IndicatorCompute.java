package com.tradery.charts.indicator;

import com.tradery.core.indicators.IndicatorEngine;

/**
 * Self-contained indicator computation.
 * Each subclass knows how to compute one indicator type using a shared IndicatorEngine.
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
     * The engine is fully configured with candles and external data (funding, OI, etc.).
     *
     * @param engine The shared IndicatorEngine
     * @return The computed result
     */
    public abstract T compute(IndicatorEngine engine);
}
