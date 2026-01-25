package com.tradery.data;

import java.time.Instant;

/**
 * Immutable request for background data preloading.
 * Comparable by priority (higher priority first), then by queue time (older first).
 */
public record PreloadRequest(
    DataType type,
    String symbol,
    String timeframe,  // Only for CANDLES, null otherwise
    long startTime,
    long endTime,
    Priority priority,
    Instant queuedAt
) implements Comparable<PreloadRequest> {

    public enum DataType {
        CANDLES,
        AGGTRADES,
        FUNDING,
        OI
    }

    public enum Priority {
        URGENT(0),   // User waiting (not used for preload)
        HIGH(1),     // Strategy just opened, likely to run soon
        NORMAL(2),   // Common symbols
        LOW(3);      // Background fill

        private final int order;

        Priority(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    /**
     * Create a candle preload request.
     */
    public static PreloadRequest candles(String symbol, String timeframe, long start, long end, Priority priority) {
        return new PreloadRequest(DataType.CANDLES, symbol, timeframe, start, end, priority, Instant.now());
    }

    /**
     * Create an aggTrades preload request.
     */
    public static PreloadRequest aggTrades(String symbol, long start, long end, Priority priority) {
        return new PreloadRequest(DataType.AGGTRADES, symbol, null, start, end, priority, Instant.now());
    }

    /**
     * Create a funding preload request.
     */
    public static PreloadRequest funding(String symbol, long start, long end, Priority priority) {
        return new PreloadRequest(DataType.FUNDING, symbol, null, start, end, priority, Instant.now());
    }

    /**
     * Create an OI preload request.
     */
    public static PreloadRequest oi(String symbol, long start, long end, Priority priority) {
        return new PreloadRequest(DataType.OI, symbol, null, start, end, priority, Instant.now());
    }

    /**
     * Compare by priority (higher first), then by queue time (older first).
     */
    @Override
    public int compareTo(PreloadRequest other) {
        int priorityCompare = Integer.compare(this.priority.getOrder(), other.priority.getOrder());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return this.queuedAt.compareTo(other.queuedAt);
    }

    /**
     * Get a unique key for deduplication.
     */
    public String getDedupeKey() {
        if (type == DataType.CANDLES) {
            return type + ":" + symbol + ":" + timeframe + ":" + startTime + "-" + endTime;
        }
        return type + ":" + symbol + ":" + startTime + "-" + endTime;
    }

    /**
     * Get duration in milliseconds.
     */
    public long getDurationMs() {
        return endTime - startTime;
    }

    /**
     * Get duration in hours.
     */
    public double getDurationHours() {
        return getDurationMs() / 3600000.0;
    }

    @Override
    public String toString() {
        String duration = String.format("%.1fh", getDurationHours());
        if (type == DataType.CANDLES) {
            return String.format("Preload[%s %s/%s %s %s]", priority, symbol, timeframe, type, duration);
        }
        return String.format("Preload[%s %s %s %s]", priority, symbol, type, duration);
    }
}
