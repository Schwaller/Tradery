package com.tradery.charts.core;

import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Interface for providing chart data.
 * Applications implement this to supply data to chart components.
 *
 * <p>This decouples charts from specific data sources (page managers,
 * databases, live feeds, etc.). Charts call methods on this interface
 * and don't need to know how the data is retrieved.</p>
 *
 * <h2>Implementation Examples</h2>
 *
 * <p><b>Forge:</b> ForgeDataProvider wraps IndicatorDataService + page managers</p>
 * <p><b>Desk:</b> DeskDataProvider wraps IndicatorEngine directly</p>
 *
 * <pre>{@code
 * // In tradery-forge
 * public class ForgeDataProvider implements ChartDataProvider {
 *     private final IndicatorDataService service;
 *     // Delegates to existing page manager infrastructure
 * }
 *
 * // In tradery-desk
 * public class DeskDataProvider implements ChartDataProvider {
 *     private final List<Candle> candles;
 *     private final IndicatorEngine engine;
 *     // Simple implementation for live data
 * }
 * }</pre>
 */
public interface ChartDataProvider {

    /**
     * Get the candle data.
     */
    List<Candle> getCandles();

    /**
     * Get the indicator engine for calculations.
     * @deprecated Use {@link #getIndicatorPool()} for async computation instead.
     */
    @Deprecated
    IndicatorEngine getIndicatorEngine();

    /**
     * Get the indicator pool for async computation.
     * Overlays subscribe to indicators via the pool for non-blocking computation.
     * Returns null if not yet available (fallback to getIndicatorEngine).
     */
    default IndicatorPool getIndicatorPool() {
        return null;
    }

    /**
     * Get the trading symbol (e.g., "BTCUSDT").
     */
    String getSymbol();

    /**
     * Get the timeframe (e.g., "1h", "4h", "1d").
     */
    String getTimeframe();

    /**
     * Get the start time of the data range (epoch ms).
     */
    long getStartTime();

    /**
     * Get the end time of the data range (epoch ms).
     */
    long getEndTime();

    /**
     * Check if candle data is available.
     */
    default boolean hasCandles() {
        List<Candle> candles = getCandles();
        return candles != null && !candles.isEmpty();
    }

    // ===== Optional: Orderflow data (null if not available) =====

    /**
     * Get delta (buy volume - sell volume) per bar.
     * Returns null if orderflow data is not available.
     */
    default double[] getDelta() {
        return null;
    }

    /**
     * Get cumulative delta per bar.
     * Returns null if orderflow data is not available.
     */
    default double[] getCumulativeDelta() {
        return null;
    }

    /**
     * Get whale delta (trades above threshold) per bar.
     * Returns null if orderflow data is not available.
     *
     * @param threshold Trade value threshold in quote currency
     */
    default double[] getWhaleDelta(double threshold) {
        return null;
    }

    // ===== Optional: External data (null if not available) =====

    /**
     * Get funding rate data per bar.
     * Returns null if funding data is not available.
     */
    default double[] getFunding() {
        return null;
    }

    /**
     * Get open interest data per bar.
     * Returns null if OI data is not available.
     */
    default double[] getOpenInterest() {
        return null;
    }

    // ===== Optional: Async indicator subscription =====

    /**
     * Subscribe to an indicator for async computation.
     * For expensive indicators, this allows precomputation.
     * Default implementation does nothing (sync calculation).
     */
    default void subscribeIndicator(IndicatorType type, int... params) {
        // Default: no-op, indicators calculated on demand
    }

    /**
     * Add a listener for data changes.
     * Called when new data is available or indicator computation completes.
     */
    default void addDataListener(Runnable onDataReady) {
        // Default: no-op
    }

    /**
     * Remove a data listener.
     */
    default void removeDataListener(Runnable listener) {
        // Default: no-op
    }
}
