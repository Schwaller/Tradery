package com.tradery.indicators;

import com.tradery.indicators.registry.DataDependency;
import com.tradery.indicators.registry.IndicatorContext;

import java.util.Set;

/**
 * Core interface for all indicators.
 *
 * Each indicator implements this directly - no separate "spec" classes.
 * Registration is simply: registry.register(SMA.INSTANCE)
 *
 * @param <T> Result type (double[], MACDResult, etc.)
 */
public interface Indicator<T> {

    /**
     * Unique identifier (e.g., "SMA", "RSI", "MACD").
     */
    String id();

    /**
     * Human-readable name (e.g., "Simple Moving Average").
     */
    String name();

    /**
     * Brief description of what this indicator does.
     */
    String description();

    /**
     * Data dependencies required for computation.
     */
    default Set<DataDependency> dependencies() {
        return Set.of(DataDependency.CANDLES);
    }

    /**
     * Number of bars needed before indicator produces valid values.
     */
    int warmupBars(Object... params);

    /**
     * Generate cache key for given parameters.
     */
    String cacheKey(Object... params);

    /**
     * Compute the indicator.
     */
    T compute(IndicatorContext ctx, Object... params);

    /**
     * Get value at specific bar index.
     */
    double valueAt(T result, int barIndex);

    /**
     * Result type class.
     */
    Class<T> resultType();

    /**
     * Parse parameters from colon-separated string.
     */
    default Object[] parseParams(String paramString) {
        if (paramString == null || paramString.isEmpty()) {
            return new Object[0];
        }
        String[] parts = paramString.split(":");
        Object[] result = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            try {
                result[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                try {
                    result[i] = Double.parseDouble(part);
                } catch (NumberFormatException e2) {
                    result[i] = part;
                }
            }
        }
        return result;
    }
}
