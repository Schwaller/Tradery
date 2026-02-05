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
    String marketType,         // "spot" or "perp" (default: "perp")
    Long endTime,              // null = live (window moves with current time), else fixed end time
    long windowDurationMillis  // window duration in milliseconds
) {
    // Canonical constructor with default marketType
    public PageKey {
        if (marketType == null) marketType = "perp";
    }
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
     * Check if this is a spot market page.
     */
    public boolean isSpot() {
        return "spot".equalsIgnoreCase(marketType);
    }

    /**
     * Check if this is a perpetual/futures market page.
     */
    public boolean isPerp() {
        return !"spot".equalsIgnoreCase(marketType);
    }

    /**
     * Create a string representation of the key for use in URLs and maps.
     * Format: dataType:symbol[:timeframe]:marketType:endTime|LIVE:windowDurationMillis
     */
    public String toKeyString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(marketType).append(":");
        sb.append(endTime != null ? endTime : "LIVE").append(":");
        sb.append(windowDurationMillis);
        return sb.toString();
    }

    /**
     * Parse a key string back into a PageKey.
     * Format: dataType:symbol[:timeframe]:marketType:endTime|LIVE:windowDurationMillis
     */
    public static PageKey fromKeyString(String keyString) {
        String[] parts = keyString.split(":");
        int idx = 0;

        String dataType = parts[idx++];
        String symbol = parts[idx++];

        String timeframe = null;
        String marketType = "perp";

        // Parse remaining parts: [timeframe]:marketType:endTime:duration
        // We need to figure out which parts are timeframe vs marketType
        int remaining = parts.length - idx;

        if (remaining == 4) {
            // Has timeframe: timeframe:marketType:endTime:duration
            timeframe = parts[idx++];
            marketType = parts[idx++];
        } else if (remaining == 3) {
            // Could be: marketType:endTime:duration (no timeframe)
            // Or old format without marketType: endTime:duration (need backwards compat)
            String next = parts[idx];
            if (next.equals("spot") || next.equals("perp")) {
                marketType = parts[idx++];
            } else if (!next.equals("LIVE") && !isNumeric(next)) {
                // Old format: timeframe:endTime:duration (no marketType)
                timeframe = parts[idx++];
                marketType = "perp"; // default for old keys
            }
        } else if (remaining == 2) {
            // Old format: endTime:duration (no timeframe, no marketType)
            marketType = "perp";
        }

        String endTimeStr = parts[idx++];
        Long endTime = endTimeStr.equals("LIVE") ? null : Long.parseLong(endTimeStr);

        long windowDurationMillis = Long.parseLong(parts[idx]);

        return new PageKey(dataType, symbol, timeframe, marketType, endTime, windowDurationMillis);
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
        return liveCandles(symbol, timeframe, "perp", windowDurationMillis);
    }

    /**
     * Create a live candle page key with market type.
     */
    public static PageKey liveCandles(String symbol, String timeframe, String marketType, long windowDurationMillis) {
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, marketType, null, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key.
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, long endTime, long windowDurationMillis) {
        return anchoredCandles(symbol, timeframe, "perp", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key with market type.
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, String marketType, long endTime, long windowDurationMillis) {
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, marketType, endTime, windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key.
     */
    public static PageKey liveAggTrades(String symbol, long windowDurationMillis) {
        return liveAggTrades(symbol, "perp", windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key with market type.
     */
    public static PageKey liveAggTrades(String symbol, String marketType, long windowDurationMillis) {
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, marketType, null, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key.
     */
    public static PageKey anchoredAggTrades(String symbol, long endTime, long windowDurationMillis) {
        return anchoredAggTrades(symbol, "perp", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key with market type.
     */
    public static PageKey anchoredAggTrades(String symbol, String marketType, long endTime, long windowDurationMillis) {
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, marketType, endTime, windowDurationMillis);
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
