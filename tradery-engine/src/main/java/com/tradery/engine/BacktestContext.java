package com.tradery.engine;

import com.tradery.core.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates all data needed for a backtest run.
 * Separates data preparation (done by app layer) from execution (done by engine).
 *
 * This allows the engine module to be data-source agnostic.
 */
public record BacktestContext(
    // Core candles for the strategy timeframe
    List<Candle> candles,

    // Pre-evaluated phase states (phaseId -> boolean[] per bar)
    Map<String, boolean[]> phaseStates,

    // Pre-evaluated hoop pattern states (patternId -> boolean[] per bar)
    Map<String, boolean[]> hoopPatternStates,

    // Hoop patterns for accessing pattern details
    List<HoopPattern> hoopPatterns,

    // Orderflow data (optional)
    List<AggTrade> aggTrades,

    // Funding rate data (optional, for futures)
    List<FundingRate> fundingRates,

    // Open interest data (optional)
    List<OpenInterest> openInterest,

    // Premium index data (optional)
    List<PremiumIndex> premiumIndex
) {
    /**
     * Create a minimal context with just candles.
     */
    public static BacktestContext ofCandles(List<Candle> candles) {
        return new BacktestContext(
            candles,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyList(),
            null,
            null,
            null,
            null
        );
    }

    /**
     * Create context with candles and phases.
     */
    public static BacktestContext withPhases(List<Candle> candles, Map<String, boolean[]> phaseStates) {
        return new BacktestContext(
            candles,
            phaseStates,
            Collections.emptyMap(),
            Collections.emptyList(),
            null,
            null,
            null,
            null
        );
    }

    /**
     * Builder for creating complex contexts.
     */
    public static Builder builder(List<Candle> candles) {
        return new Builder(candles);
    }

    public static class Builder {
        private final List<Candle> candles;
        private Map<String, boolean[]> phaseStates = Collections.emptyMap();
        private Map<String, boolean[]> hoopPatternStates = Collections.emptyMap();
        private List<HoopPattern> hoopPatterns = Collections.emptyList();
        private List<AggTrade> aggTrades;
        private List<FundingRate> fundingRates;
        private List<OpenInterest> openInterest;
        private List<PremiumIndex> premiumIndex;

        private Builder(List<Candle> candles) {
            this.candles = candles;
        }

        public Builder phaseStates(Map<String, boolean[]> phaseStates) {
            this.phaseStates = phaseStates != null ? phaseStates : Collections.emptyMap();
            return this;
        }

        public Builder hoopPatternStates(Map<String, boolean[]> states) {
            this.hoopPatternStates = states != null ? states : Collections.emptyMap();
            return this;
        }

        public Builder hoopPatterns(List<HoopPattern> patterns) {
            this.hoopPatterns = patterns != null ? patterns : Collections.emptyList();
            return this;
        }

        public Builder aggTrades(List<AggTrade> aggTrades) {
            this.aggTrades = aggTrades;
            return this;
        }

        public Builder fundingRates(List<FundingRate> fundingRates) {
            this.fundingRates = fundingRates;
            return this;
        }

        public Builder openInterest(List<OpenInterest> openInterest) {
            this.openInterest = openInterest;
            return this;
        }

        public Builder premiumIndex(List<PremiumIndex> premiumIndex) {
            this.premiumIndex = premiumIndex;
            return this;
        }

        public BacktestContext build() {
            return new BacktestContext(
                candles,
                phaseStates,
                hoopPatternStates,
                hoopPatterns,
                aggTrades,
                fundingRates,
                openInterest,
                premiumIndex
            );
        }
    }
}
