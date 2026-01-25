package com.tradery.indicators.registry;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Context passed to indicator computation containing all available data.
 * Immutable - create new instance when data changes.
 */
public record IndicatorContext(
    List<Candle> candles,
    List<AggTrade> aggTrades,
    List<FundingRate> funding,
    List<OpenInterest> openInterest,
    List<PremiumIndex> premium,
    String resolution
) {
    /**
     * Create context with only candles (most common case).
     */
    public static IndicatorContext ofCandles(List<Candle> candles, String resolution) {
        return new IndicatorContext(candles, null, null, null, null, resolution);
    }

    /**
     * Create context with candles and aggTrades.
     */
    public static IndicatorContext ofCandlesAndAggTrades(
            List<Candle> candles, List<AggTrade> aggTrades, String resolution) {
        return new IndicatorContext(candles, aggTrades, null, null, null, resolution);
    }

    /**
     * Check if candles are available.
     */
    public boolean hasCandles() {
        return candles != null && !candles.isEmpty();
    }

    /**
     * Check if aggTrades are available.
     */
    public boolean hasAggTrades() {
        return aggTrades != null && !aggTrades.isEmpty();
    }

    /**
     * Check if funding data is available.
     */
    public boolean hasFunding() {
        return funding != null && !funding.isEmpty();
    }

    /**
     * Check if open interest data is available.
     */
    public boolean hasOpenInterest() {
        return openInterest != null && !openInterest.isEmpty();
    }

    /**
     * Check if premium data is available.
     */
    public boolean hasPremium() {
        return premium != null && !premium.isEmpty();
    }

    /**
     * Check if all specified dependencies are met.
     */
    public boolean dependenciesMet(Set<DataDependency> deps) {
        for (DataDependency dep : deps) {
            boolean met = switch (dep) {
                case CANDLES -> hasCandles();
                case AGG_TRADES -> hasAggTrades();
                case FUNDING -> hasFunding();
                case OPEN_INTEREST -> hasOpenInterest();
                case PREMIUM -> hasPremium();
            };
            if (!met) return false;
        }
        return true;
    }

    /**
     * Get bar count from candles.
     */
    public int barCount() {
        return candles != null ? candles.size() : 0;
    }

    /**
     * Get candle at index (null-safe).
     */
    public Candle candleAt(int index) {
        if (candles == null || index < 0 || index >= candles.size()) {
            return null;
        }
        return candles.get(index);
    }

    /**
     * Return safe (non-null) candle list.
     */
    public List<Candle> safeCandles() {
        return candles != null ? candles : Collections.emptyList();
    }

    /**
     * Return safe (non-null) aggTrades list.
     */
    public List<AggTrade> safeAggTrades() {
        return aggTrades != null ? aggTrades : Collections.emptyList();
    }
}
