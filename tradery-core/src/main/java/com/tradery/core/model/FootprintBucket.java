package com.tradery.core.model;

import java.util.*;

/**
 * Single price-level bucket in a footprint chart with per-exchange breakdown.
 *
 * A footprint bucket represents trading activity at a specific price level,
 * showing buy/sell volume, delta, and imbalance metrics.
 */
public record FootprintBucket(
    double priceLevel,              // Center price of this bucket

    // Per-exchange volume breakdown
    Map<Exchange, Double> buyVolumeByExchange,
    Map<Exchange, Double> sellVolumeByExchange,

    // Aggregated totals
    double totalBuyVolume,
    double totalSellVolume,
    double totalDelta,              // buyVolume - sellVolume
    double imbalanceRatio,          // buyVolume / sellVolume (or inverse if sell dominant)

    // Exchange divergence metrics
    Set<Exchange> exchangesWithBuyImbalance,
    Set<Exchange> exchangesWithSellImbalance,
    double deltaSpread,             // max exchange delta - min exchange delta

    // Per-market-type volume breakdown
    Map<DataMarketType, Double> buyVolumeByMarketType,
    Map<DataMarketType, Double> sellVolumeByMarketType
) {
    /**
     * Minimum imbalance ratio to be considered significant.
     */
    public static final double SIGNIFICANT_IMBALANCE = 3.0;

    /**
     * Create an empty bucket at a price level.
     */
    /**
     * Get delta for a specific market type.
     */
    public double getDeltaForMarketType(DataMarketType marketType) {
        double buy = buyVolumeByMarketType.getOrDefault(marketType, 0.0);
        double sell = sellVolumeByMarketType.getOrDefault(marketType, 0.0);
        return buy - sell;
    }

    /**
     * Get total volume for a specific market type.
     */
    public double getVolumeForMarketType(DataMarketType marketType) {
        double buy = buyVolumeByMarketType.getOrDefault(marketType, 0.0);
        double sell = sellVolumeByMarketType.getOrDefault(marketType, 0.0);
        return buy + sell;
    }

    public static FootprintBucket empty(double priceLevel) {
        return new FootprintBucket(
            priceLevel,
            new EnumMap<>(Exchange.class),
            new EnumMap<>(Exchange.class),
            0, 0, 0, 1.0,
            EnumSet.noneOf(Exchange.class),
            EnumSet.noneOf(Exchange.class),
            0,
            new EnumMap<>(DataMarketType.class),
            new EnumMap<>(DataMarketType.class)
        );
    }

    /**
     * Check if this bucket has significant buy imbalance (3:1 or better).
     */
    public boolean hasBuyImbalance() {
        return imbalanceRatio >= SIGNIFICANT_IMBALANCE;
    }

    /**
     * Check if this bucket has significant sell imbalance (3:1 or better).
     */
    public boolean hasSellImbalance() {
        return imbalanceRatio <= 1.0 / SIGNIFICANT_IMBALANCE;
    }

    /**
     * Get the dominant direction (1 = buy, -1 = sell, 0 = neutral).
     */
    public int dominantDirection() {
        if (hasBuyImbalance()) return 1;
        if (hasSellImbalance()) return -1;
        return 0;
    }

    /**
     * Get total volume at this level.
     */
    public double totalVolume() {
        return totalBuyVolume + totalSellVolume;
    }

    /**
     * Get delta for a specific exchange.
     */
    public double getDeltaForExchange(Exchange exchange) {
        double buy = buyVolumeByExchange.getOrDefault(exchange, 0.0);
        double sell = sellVolumeByExchange.getOrDefault(exchange, 0.0);
        return buy - sell;
    }

    /**
     * Check if exchanges disagree on direction at this level.
     */
    public boolean hasExchangeDivergence() {
        return !exchangesWithBuyImbalance.isEmpty() && !exchangesWithSellImbalance.isEmpty();
    }

    /**
     * Builder for constructing FootprintBucket instances.
     */
    public static class Builder {
        private double priceLevel;
        private final Map<Exchange, Double> buyVolume = new EnumMap<>(Exchange.class);
        private final Map<Exchange, Double> sellVolume = new EnumMap<>(Exchange.class);
        private final Map<DataMarketType, Double> buyVolumeByMarket = new EnumMap<>(DataMarketType.class);
        private final Map<DataMarketType, Double> sellVolumeByMarket = new EnumMap<>(DataMarketType.class);

        public Builder(double priceLevel) {
            this.priceLevel = priceLevel;
        }

        public Builder addBuyVolume(Exchange exchange, double volume) {
            buyVolume.merge(exchange, volume, Double::sum);
            return this;
        }

        public Builder addBuyVolume(Exchange exchange, DataMarketType marketType, double volume) {
            buyVolume.merge(exchange, volume, Double::sum);
            if (marketType != null) buyVolumeByMarket.merge(marketType, volume, Double::sum);
            return this;
        }

        public Builder addSellVolume(Exchange exchange, double volume) {
            sellVolume.merge(exchange, volume, Double::sum);
            return this;
        }

        public Builder addSellVolume(Exchange exchange, DataMarketType marketType, double volume) {
            sellVolume.merge(exchange, volume, Double::sum);
            if (marketType != null) sellVolumeByMarket.merge(marketType, volume, Double::sum);
            return this;
        }

        public FootprintBucket build() {
            double totalBuy = buyVolume.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalSell = sellVolume.values().stream().mapToDouble(Double::doubleValue).sum();
            double delta = totalBuy - totalSell;

            // Calculate imbalance ratio (avoid division by zero)
            double ratio;
            if (totalSell == 0) {
                ratio = totalBuy > 0 ? Double.MAX_VALUE : 1.0;
            } else if (totalBuy == 0) {
                ratio = 0;
            } else {
                ratio = totalBuy / totalSell;
            }

            // Find exchanges with imbalances
            Set<Exchange> buyImbalances = EnumSet.noneOf(Exchange.class);
            Set<Exchange> sellImbalances = EnumSet.noneOf(Exchange.class);
            double minDelta = Double.MAX_VALUE;
            double maxDelta = Double.MIN_VALUE;

            for (Exchange ex : Exchange.values()) {
                double exBuy = buyVolume.getOrDefault(ex, 0.0);
                double exSell = sellVolume.getOrDefault(ex, 0.0);

                if (exBuy > 0 || exSell > 0) {
                    double exDelta = exBuy - exSell;
                    minDelta = Math.min(minDelta, exDelta);
                    maxDelta = Math.max(maxDelta, exDelta);

                    double exRatio = exSell > 0 ? exBuy / exSell : (exBuy > 0 ? Double.MAX_VALUE : 1.0);
                    if (exRatio >= SIGNIFICANT_IMBALANCE) {
                        buyImbalances.add(ex);
                    } else if (exRatio <= 1.0 / SIGNIFICANT_IMBALANCE) {
                        sellImbalances.add(ex);
                    }
                }
            }

            double deltaSpread = (maxDelta == Double.MIN_VALUE || minDelta == Double.MAX_VALUE)
                ? 0 : maxDelta - minDelta;

            return new FootprintBucket(
                priceLevel,
                Collections.unmodifiableMap(new EnumMap<>(buyVolume)),
                Collections.unmodifiableMap(new EnumMap<>(sellVolume)),
                totalBuy, totalSell, delta, ratio,
                buyImbalances.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(buyImbalances),
                sellImbalances.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(sellImbalances),
                deltaSpread,
                Collections.unmodifiableMap(new EnumMap<>(buyVolumeByMarket)),
                Collections.unmodifiableMap(new EnumMap<>(sellVolumeByMarket))
            );
        }
    }
}
