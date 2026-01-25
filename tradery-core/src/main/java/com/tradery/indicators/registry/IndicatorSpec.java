package com.tradery.indicators.registry;

import java.util.Set;

/**
 * Specification for an indicator - defines everything needed to compute and access it.
 *
 * This is the single source of truth for each indicator. Both the async chart system
 * (IndicatorPageManager) and sync backtest system (IndicatorEngine) use these specs.
 *
 * Adding a new indicator = implement this interface and register it.
 *
 * @param <T> The result type (double[], MACDResult, etc.)
 */
public interface IndicatorSpec<T> {

    /**
     * Unique identifier for this indicator (e.g., "RSI", "MACD", "SUPERTREND").
     */
    String id();

    /**
     * Generate a cache key for the given parameters.
     * Must be unique for each parameter combination.
     *
     * @param params Indicator parameters (periods, multipliers, etc.)
     * @return Cache key like "rsi:14" or "macd:12:26:9"
     */
    String cacheKey(Object... params);

    /**
     * What data this indicator depends on.
     * Used to determine when computation can proceed.
     */
    Set<DataDependency> dependencies();

    /**
     * Compute the full indicator array.
     * Called once, result is cached.
     *
     * @param ctx Context containing all available data
     * @param params Indicator parameters
     * @return Computed result (double[] or composite type)
     */
    T compute(IndicatorContext ctx, Object... params);

    /**
     * Get a single value at a specific bar index.
     * Used by backtest engine for per-bar evaluation.
     *
     * Default implementation works for double[] results.
     * Override for composite types (MACD, BBands, etc.) to throw
     * or return a specific component.
     *
     * @param result The cached computation result
     * @param barIndex Bar index to retrieve
     * @return Value at the bar index, or NaN if invalid
     */
    default double valueAt(T result, int barIndex) {
        if (result instanceof double[] arr) {
            if (barIndex < 0 || barIndex >= arr.length) {
                return Double.NaN;
            }
            return arr[barIndex];
        }
        throw new UnsupportedOperationException(
            "valueAt() not supported for " + id() + " - use component accessor");
    }

    /**
     * Get the result type class.
     * Used for type-safe casting.
     */
    Class<T> resultType();

    /**
     * Parse parameters from a colon-separated string.
     * Default implementation splits on ":" and parses as integers/doubles.
     *
     * @param paramString e.g., "14" or "12:26:9" or "20:2.0"
     * @return Array of parsed parameters
     */
    default Object[] parseParams(String paramString) {
        if (paramString == null || paramString.isEmpty()) {
            return new Object[0];
        }
        String[] parts = paramString.split(":");
        Object[] result = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            // Try integer first, then double
            try {
                result[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                try {
                    result[i] = Double.parseDouble(part);
                } catch (NumberFormatException e2) {
                    result[i] = part; // Keep as string
                }
            }
        }
        return result;
    }
}
