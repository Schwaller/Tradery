package com.tradery.dataservice.page;

/**
 * Unique identifier for a data page.
 *
 * Pages can be:
 * - Anchored: endTime != null, fixed historical view
 * - Live: endTime == null, window slides forward with current time
 *
 * The window size is always windowDurationMillis. For live pages, start/end are computed dynamically.
 */
public record PageKey(
    String dataType,           // CANDLES, AGGTRADES, FUNDING, OI, PREMIUM
    String symbol,
    String timeframe,          // null for AGGTRADES, FUNDING, OI
    Long endTime,              // null = live (window moves with current time), else fixed end time
    long windowDurationMillis  // window duration in milliseconds
) {
    /**
     * Check if this is a live (moving) page.
     */
    public boolean isLive() {
        return endTime == null;
    }

    /**
     * Get the effective end time for this page.
     * For live pages, this is "now". For anchored pages, this is the endTime.
     */
    public long getEffectiveEndTime() {
        return endTime != null ? endTime : System.currentTimeMillis();
    }

    /**
     * Get the effective start time for this page.
     */
    public long getEffectiveStartTime() {
        return getEffectiveEndTime() - windowDurationMillis;
    }
    /**
     * Create a string representation of the key for use in URLs and maps.
     * Format: dataType:symbol[:timeframe]:endTime|LIVE:windowDurationMillis
     */
    public String toKeyString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(endTime != null ? endTime : "LIVE").append(":");
        sb.append(windowDurationMillis);
        return sb.toString();
    }

    /**
     * Parse a key string back into a PageKey.
     */
    public static PageKey fromKeyString(String keyString) {
        String[] parts = keyString.split(":");
        int idx = 0;

        String dataType = parts[idx++];
        String symbol = parts[idx++];

        String timeframe = null;
        // Check if next part is a timeframe (not LIVE or a number)
        if (idx < parts.length - 2) {
            String next = parts[idx];
            if (!next.equals("LIVE") && !isNumeric(next)) {
                timeframe = next;
                idx++;
            }
        }

        String endTimeStr = parts[idx++];
        Long endTime = endTimeStr.equals("LIVE") ? null : Long.parseLong(endTimeStr);

        long windowDurationMillis = Long.parseLong(parts[idx]);

        return new PageKey(dataType, symbol, timeframe, endTime, windowDurationMillis);
    }

    private static boolean isNumeric(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Create a live candle page key.
     */
    public static PageKey liveCandles(String symbol, String timeframe, long windowDurationMillis) {
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, null, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key.
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, long endTime, long windowDurationMillis) {
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, endTime, windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key.
     */
    public static PageKey liveAggTrades(String symbol, long windowDurationMillis) {
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, null, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key.
     */
    public static PageKey anchoredAggTrades(String symbol, long endTime, long windowDurationMillis) {
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, endTime, windowDurationMillis);
    }

    /**
     * Check if this page key is for candle data.
     */
    public boolean isCandles() {
        return "CANDLES".equals(dataType);
    }

    /**
     * Check if this page key is for aggregated trades data.
     */
    public boolean isAggTrades() {
        return "AGGTRADES".equals(dataType) || "AGG_TRADES".equals(dataType);
    }

    /**
     * Check if this page key is for funding rate data.
     */
    public boolean isFunding() {
        return "FUNDING".equals(dataType);
    }

    /**
     * Check if this page key is for open interest data.
     */
    public boolean isOpenInterest() {
        return "OI".equals(dataType) || "OPEN_INTEREST".equals(dataType);
    }

    /**
     * Check if this page key is for premium index data.
     */
    public boolean isPremium() {
        return "PREMIUM".equals(dataType) || "PREMIUM_INDEX".equals(dataType);
    }
}
