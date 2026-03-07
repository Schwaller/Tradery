package com.tradery.core.model;

/**
 * Single price-level bucket in a footprint chart.
 *
 * A footprint bucket represents trading activity at a specific price level,
 * showing buy/sell volume, delta, and imbalance metrics.
 */
public record FootprintBucket(
    double priceLevel,              // Center price of this bucket
    double totalBuyVolume,
    double totalSellVolume,
    double totalDelta,              // buyVolume - sellVolume
    double imbalanceRatio           // buyVolume / sellVolume (or inverse if sell dominant)
) {
    /**
     * Minimum imbalance ratio to be considered significant.
     */
    public static final double SIGNIFICANT_IMBALANCE = 3.0;

    /**
     * Create an empty bucket at a price level.
     */
    public static FootprintBucket empty(double priceLevel) {
        return new FootprintBucket(priceLevel, 0, 0, 0, 1.0);
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
     * Builder for constructing FootprintBucket instances.
     */
    public static class Builder {
        private final double priceLevel;
        private double buyVolume;
        private double sellVolume;

        public Builder(double priceLevel) {
            this.priceLevel = priceLevel;
        }

        public Builder addBuyVolume(double volume) {
            buyVolume += volume;
            return this;
        }

        public Builder addSellVolume(double volume) {
            sellVolume += volume;
            return this;
        }

        public FootprintBucket build() {
            double delta = buyVolume - sellVolume;

            // Calculate imbalance ratio (avoid division by zero)
            double ratio;
            if (sellVolume == 0) {
                ratio = buyVolume > 0 ? Double.MAX_VALUE : 1.0;
            } else if (buyVolume == 0) {
                ratio = 0;
            } else {
                ratio = buyVolume / sellVolume;
            }

            return new FootprintBucket(priceLevel, buyVolume, sellVolume, delta, ratio);
        }
    }
}
