package com.tradery.model;

import java.util.*;

/**
 * Complete footprint for one candle, containing price-level buckets.
 *
 * A footprint shows the distribution of buy/sell volume across price levels
 * within a single candle, enabling analysis of orderflow and absorption.
 */
public record Footprint(
    long timestamp,                 // Candle timestamp
    int barIndex,                   // Bar index in the candle series
    double high,                    // Candle high
    double low,                     // Candle low
    double tickSize,                // Price level bucket size

    // Buckets sorted by price level (low to high)
    List<FootprintBucket> buckets,

    // Per-exchange delta summary
    Map<Exchange, Double> deltaByExchange,

    // Aggregated metrics
    double poc,                     // Point of Control price
    double vah,                     // Value Area High
    double val,                     // Value Area Low
    double totalDelta,              // Sum of delta across all buckets
    int stackedBuyImbalances,       // Consecutive buckets with buy imbalance
    int stackedSellImbalances,      // Consecutive buckets with sell imbalance

    // Exchange divergence metrics
    double exchangeDivergenceScore, // 0-1 score of how much exchanges disagree
    Exchange dominantExchange       // Exchange with highest absolute delta
) {
    /**
     * Get bucket at or nearest to a price level.
     */
    public FootprintBucket getBucketAt(double price) {
        if (buckets.isEmpty()) return null;

        // Binary search for nearest bucket
        int lo = 0, hi = buckets.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            double midPrice = buckets.get(mid).priceLevel();
            if (Math.abs(midPrice - price) < tickSize / 2) {
                return buckets.get(mid);
            }
            if (midPrice < price) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return buckets.get(lo);
    }

    /**
     * Get bucket at the POC.
     */
    public FootprintBucket getPocBucket() {
        return getBucketAt(poc);
    }

    /**
     * Get bucket at VAH.
     */
    public FootprintBucket getVahBucket() {
        return getBucketAt(vah);
    }

    /**
     * Get bucket at VAL.
     */
    public FootprintBucket getValBucket() {
        return getBucketAt(val);
    }

    /**
     * Get total volume in this footprint.
     */
    public double totalVolume() {
        return buckets.stream()
            .mapToDouble(FootprintBucket::totalVolume)
            .sum();
    }

    /**
     * Get volume above POC.
     */
    public double volumeAbovePoc() {
        return buckets.stream()
            .filter(b -> b.priceLevel() > poc)
            .mapToDouble(FootprintBucket::totalVolume)
            .sum();
    }

    /**
     * Get volume below POC.
     */
    public double volumeBelowPoc() {
        return buckets.stream()
            .filter(b -> b.priceLevel() < poc)
            .mapToDouble(FootprintBucket::totalVolume)
            .sum();
    }

    /**
     * Get the imbalance ratio at POC.
     */
    public double imbalanceAtPoc() {
        FootprintBucket pocBucket = getPocBucket();
        return pocBucket != null ? pocBucket.imbalanceRatio() : 1.0;
    }

    /**
     * Count high volume nodes (buckets with volume above threshold).
     */
    public int countHighVolumeNodes(double volumeThreshold) {
        return (int) buckets.stream()
            .filter(b -> b.totalVolume() >= volumeThreshold)
            .count();
    }

    /**
     * Check if this footprint shows absorption (high volume, small price movement).
     */
    public boolean isAbsorption(double volumeThreshold, double maxMovementPercent) {
        double totalVol = totalVolume();
        double priceMovement = (high - low) / low * 100;
        return totalVol >= volumeThreshold && priceMovement <= maxMovementPercent;
    }

    /**
     * Get delta for a specific exchange.
     */
    public double getDeltaForExchange(Exchange exchange) {
        return deltaByExchange.getOrDefault(exchange, 0.0);
    }

    /**
     * Builder for constructing Footprint instances.
     */
    public static class Builder {
        private long timestamp;
        private int barIndex;
        private double high;
        private double low;
        private double tickSize;
        private final List<FootprintBucket> buckets = new ArrayList<>();
        private final Map<Exchange, Double> deltaByExchange = new EnumMap<>(Exchange.class);

        public Builder timestamp(long ts) { this.timestamp = ts; return this; }
        public Builder barIndex(int idx) { this.barIndex = idx; return this; }
        public Builder high(double h) { this.high = h; return this; }
        public Builder low(double l) { this.low = l; return this; }
        public Builder tickSize(double ts) { this.tickSize = ts; return this; }

        public Builder addBucket(FootprintBucket bucket) {
            buckets.add(bucket);
            return this;
        }

        public Builder addDelta(Exchange exchange, double delta) {
            deltaByExchange.merge(exchange, delta, Double::sum);
            return this;
        }

        public Footprint build() {
            // Sort buckets by price
            buckets.sort(Comparator.comparingDouble(FootprintBucket::priceLevel));

            // Calculate POC (highest volume level)
            double poc = low;
            double maxVolume = 0;
            for (FootprintBucket bucket : buckets) {
                if (bucket.totalVolume() > maxVolume) {
                    maxVolume = bucket.totalVolume();
                    poc = bucket.priceLevel();
                }
            }

            // Calculate VAH/VAL (70% of volume)
            double totalVolume = buckets.stream().mapToDouble(FootprintBucket::totalVolume).sum();
            double targetVolume = totalVolume * 0.70;
            double cumulativeVolume = 0;
            int pocIndex = 0;
            for (int i = 0; i < buckets.size(); i++) {
                if (buckets.get(i).priceLevel() == poc) {
                    pocIndex = i;
                    break;
                }
            }

            // Expand outward from POC
            int lo = pocIndex, hi = pocIndex;
            cumulativeVolume = buckets.get(pocIndex).totalVolume();
            while (cumulativeVolume < targetVolume && (lo > 0 || hi < buckets.size() - 1)) {
                double loVol = lo > 0 ? buckets.get(lo - 1).totalVolume() : 0;
                double hiVol = hi < buckets.size() - 1 ? buckets.get(hi + 1).totalVolume() : 0;
                if (loVol >= hiVol && lo > 0) {
                    lo--;
                    cumulativeVolume += loVol;
                } else if (hi < buckets.size() - 1) {
                    hi++;
                    cumulativeVolume += hiVol;
                } else {
                    break;
                }
            }
            double val = lo >= 0 && lo < buckets.size() ? buckets.get(lo).priceLevel() : low;
            double vah = hi >= 0 && hi < buckets.size() ? buckets.get(hi).priceLevel() : high;

            // Calculate total delta
            double totalDelta = buckets.stream().mapToDouble(FootprintBucket::totalDelta).sum();

            // Count stacked imbalances
            int stackedBuy = countMaxConsecutiveImbalances(buckets, true);
            int stackedSell = countMaxConsecutiveImbalances(buckets, false);

            // Calculate exchange divergence score
            double divergenceScore = calculateDivergenceScore(deltaByExchange);

            // Find dominant exchange
            Exchange dominant = deltaByExchange.entrySet().stream()
                .max(Comparator.comparingDouble(e -> Math.abs(e.getValue())))
                .map(Map.Entry::getKey)
                .orElse(Exchange.BINANCE);

            return new Footprint(
                timestamp, barIndex, high, low, tickSize,
                Collections.unmodifiableList(new ArrayList<>(buckets)),
                Collections.unmodifiableMap(new EnumMap<>(deltaByExchange)),
                poc, vah, val, totalDelta,
                stackedBuy, stackedSell,
                divergenceScore, dominant
            );
        }

        private int countMaxConsecutiveImbalances(List<FootprintBucket> buckets, boolean buyImbalance) {
            int max = 0, current = 0;
            for (FootprintBucket b : buckets) {
                if (buyImbalance ? b.hasBuyImbalance() : b.hasSellImbalance()) {
                    current++;
                    max = Math.max(max, current);
                } else {
                    current = 0;
                }
            }
            return max;
        }

        private double calculateDivergenceScore(Map<Exchange, Double> deltas) {
            if (deltas.size() <= 1) return 0;

            // Count exchanges with positive vs negative delta
            long positive = deltas.values().stream().filter(d -> d > 0).count();
            long negative = deltas.values().stream().filter(d -> d < 0).count();

            // Score is 0 when all agree, 1 when split evenly
            double total = positive + negative;
            if (total == 0) return 0;

            double minority = Math.min(positive, negative);
            return minority / (total / 2);
        }
    }
}
