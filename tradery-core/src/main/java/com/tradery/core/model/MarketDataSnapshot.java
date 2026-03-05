package com.tradery.core.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all market data needed for indicator computation.
 * Used by both chart rendering and backtest engine to ensure a single
 * computation path for all metrics.
 */
public record MarketDataSnapshot(
    String symbol,
    String timeframe,
    List<Candle> candles,
    List<AggTrade> aggTrades,
    List<FundingRate> fundingRates,
    List<OpenInterest> openInterest,
    List<PremiumIndex> premiumIndex,
    List<FearGreedIndex> fearGreedIndex,
    Map<Long, double[]> dailyProfiles,
    List<SpectrumWindow> spectrumWindows
) implements MarketData {

    public static Builder builder(String symbol, String timeframe, List<Candle> candles) {
        return new Builder(symbol, timeframe, candles);
    }

    public static class Builder {
        private final String symbol;
        private final String timeframe;
        private final List<Candle> candles;
        private List<AggTrade> aggTrades;
        private List<FundingRate> fundingRates;
        private List<OpenInterest> openInterest;
        private List<PremiumIndex> premiumIndex;
        private List<FearGreedIndex> fearGreedIndex;
        private Map<Long, double[]> dailyProfiles;
        private List<SpectrumWindow> spectrumWindows;

        private Builder(String symbol, String timeframe, List<Candle> candles) {
            this.symbol = symbol;
            this.timeframe = timeframe;
            this.candles = candles;
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

        public Builder fearGreedIndex(List<FearGreedIndex> fearGreedIndex) {
            this.fearGreedIndex = fearGreedIndex;
            return this;
        }

        public Builder dailyProfiles(Map<Long, double[]> dailyProfiles) {
            this.dailyProfiles = dailyProfiles;
            return this;
        }

        public Builder spectrumWindows(List<SpectrumWindow> spectrumWindows) {
            this.spectrumWindows = spectrumWindows;
            return this;
        }

        public MarketDataSnapshot build() {
            return new MarketDataSnapshot(
                symbol,
                timeframe,
                List.copyOf(candles),
                aggTrades,
                fundingRates,
                openInterest,
                premiumIndex,
                fearGreedIndex,
                dailyProfiles != null ? Map.copyOf(dailyProfiles) : null,
                spectrumWindows
            );
        }
    }
}
