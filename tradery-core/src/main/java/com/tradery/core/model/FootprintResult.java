package com.tradery.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for all footprints across a series of candles.
 *
 * Provides access to individual candle footprints and aggregate statistics.
 */
public record FootprintResult(
    List<Footprint> footprints,     // One footprint per candle (indexed by barIndex)
    double tickSize,                 // Tick size used for bucketing
    String symbol,
    String timeframe,

    // Aggregate statistics
    int totalTrades,
    double avgDeltaPerBar,
    int maxStackedBuyImbalances,
    int maxStackedSellImbalances
) {
    /**
     * Get footprint for a specific bar index.
     */
    public Footprint getAtBar(int barIndex) {
        if (barIndex < 0 || barIndex >= footprints.size()) {
            return null;
        }
        return footprints.get(barIndex);
    }

    /**
     * Get footprint for a specific timestamp.
     */
    public Footprint getAtTimestamp(long timestamp) {
        return footprints.stream()
            .filter(f -> f.timestamp() == timestamp)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get footprints in a bar index range.
     */
    public List<Footprint> getRange(int startBar, int endBar) {
        int start = Math.max(0, startBar);
        int end = Math.min(footprints.size(), endBar + 1);
        if (start >= end) return Collections.emptyList();
        return footprints.subList(start, end);
    }

    /**
     * Get total delta across all footprints.
     */
    public double totalDelta() {
        return footprints.stream()
            .mapToDouble(Footprint::totalDelta)
            .sum();
    }

    /**
     * Get cumulative delta array (for charting).
     */
    public double[] cumulativeDelta() {
        double[] result = new double[footprints.size()];
        double cumulative = 0;
        for (int i = 0; i < footprints.size(); i++) {
            cumulative += footprints.get(i).totalDelta();
            result[i] = cumulative;
        }
        return result;
    }

    /**
     * Get POC prices for all bars.
     */
    public double[] getPocPrices() {
        return footprints.stream()
            .mapToDouble(Footprint::poc)
            .toArray();
    }

    /**
     * Get VAH prices for all bars.
     */
    public double[] getVahPrices() {
        return footprints.stream()
            .mapToDouble(Footprint::vah)
            .toArray();
    }

    /**
     * Get VAL prices for all bars.
     */
    public double[] getValPrices() {
        return footprints.stream()
            .mapToDouble(Footprint::val)
            .toArray();
    }

    /**
     * Find bars with stacked imbalances (n or more consecutive).
     */
    public List<Integer> findBarsWithStackedBuyImbalances(int minStacked) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < footprints.size(); i++) {
            if (footprints.get(i).stackedBuyImbalances() >= minStacked) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Find bars with stacked sell imbalances.
     */
    public List<Integer> findBarsWithStackedSellImbalances(int minStacked) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < footprints.size(); i++) {
            if (footprints.get(i).stackedSellImbalances() >= minStacked) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Find bars with exchange divergence.
     */
    public List<Integer> findBarsWithExchangeDivergence(double minScore) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < footprints.size(); i++) {
            if (footprints.get(i).exchangeDivergenceScore() >= minScore) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Builder for constructing FootprintResult instances.
     */
    public static class Builder {
        private final List<Footprint> footprints = new ArrayList<>();
        private double tickSize;
        private String symbol;
        private String timeframe;

        public Builder tickSize(double ts) { this.tickSize = ts; return this; }
        public Builder symbol(String s) { this.symbol = s; return this; }
        public Builder timeframe(String tf) { this.timeframe = tf; return this; }

        public Builder addFootprint(Footprint fp) {
            footprints.add(fp);
            return this;
        }

        public FootprintResult build() {
            // Calculate aggregate stats
            int totalTrades = 0;
            double totalDelta = 0;
            int maxBuy = 0, maxSell = 0;

            for (Footprint fp : footprints) {
                totalDelta += fp.totalDelta();
                maxBuy = Math.max(maxBuy, fp.stackedBuyImbalances());
                maxSell = Math.max(maxSell, fp.stackedSellImbalances());
            }

            double avgDelta = footprints.isEmpty() ? 0 : totalDelta / footprints.size();

            return new FootprintResult(
                Collections.unmodifiableList(new ArrayList<>(footprints)),
                tickSize,
                symbol,
                timeframe,
                totalTrades,
                avgDelta,
                maxBuy,
                maxSell
            );
        }
    }
}
