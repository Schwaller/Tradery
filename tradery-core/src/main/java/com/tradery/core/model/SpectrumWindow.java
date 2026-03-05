package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A 10-second aggregation window containing trade size distribution across log10 buckets.
 *
 * Bucket index = clamp(0, 8, floor(log10(notional))):
 *   0=$1, 1=$10, 2=$100, 3=$1K, 4=$10K, 5=$100K, 6=$1M, 7=$10M, 8=$100M+
 *
 * Windows merge by summing bucket stats (time pyramid: 10s → 1m → 5m → 1h → 1d).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpectrumWindow(
    long windowStart,       // 10s-aligned epoch ms
    SizeBucket[] buckets    // always length BUCKET_COUNT (9), indexed 0-8
) {

    public static final int MAX_BUCKET = 8;
    public static final int BUCKET_COUNT = MAX_BUCKET + 1;  // 9
    public static final long WINDOW_SIZE_MS = 10_000;        // 10 seconds

    /**
     * Compute bucket index from trade notional (USD).
     * Sub-dollar trades clip to 0, >$100M clips to 8.
     */
    public static int bucketIndex(double notional) {
        if (notional < 1.0) return 0;
        return Math.min(MAX_BUCKET, (int) Math.floor(Math.log10(notional)));
    }

    /**
     * Human-readable label for a bucket index.
     */
    public static String bucketLabel(int index) {
        return switch (index) {
            case 0 -> "$1";
            case 1 -> "$10";
            case 2 -> "$100";
            case 3 -> "$1K";
            case 4 -> "$10K";
            case 5 -> "$100K";
            case 6 -> "$1M";
            case 7 -> "$10M";
            case 8 -> "$100M";
            default -> "$?";
        };
    }

    /**
     * Align a timestamp to its 10-second window start.
     */
    public static long alignToWindow(long timestamp) {
        return (timestamp / WINDOW_SIZE_MS) * WINDOW_SIZE_MS;
    }

    /**
     * Create an empty window with all-zero buckets.
     */
    public static SpectrumWindow empty(long windowStart) {
        SizeBucket[] buckets = new SizeBucket[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = SizeBucket.EMPTY;
        }
        return new SpectrumWindow(windowStart, buckets);
    }

    /**
     * Merge two windows by summing all buckets. Windows should share the same windowStart
     * (or represent the same aggregated period).
     */
    public SpectrumWindow merge(SpectrumWindow other) {
        SizeBucket[] merged = new SizeBucket[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            merged[i] = this.buckets[i].merge(other.buckets[i]);
        }
        return new SpectrumWindow(windowStart, merged);
    }

    @JsonIgnore
    public int totalTradeCount() {
        int total = 0;
        for (SizeBucket b : buckets) {
            total += b.tradeCount();
        }
        return total;
    }

    @JsonIgnore
    public double totalVolume() {
        double total = 0;
        for (SizeBucket b : buckets) {
            total += b.totalVolume();
        }
        return total;
    }

    @JsonIgnore
    public double totalDelta() {
        double total = 0;
        for (SizeBucket b : buckets) {
            total += b.delta();
        }
        return total;
    }
}
