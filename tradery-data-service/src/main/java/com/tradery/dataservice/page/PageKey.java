package com.tradery.dataservice.page;

/**
 * Unique identifier for a data page.
 *
 * Pages can be:
 * - Anchored: Fixed startTime/endTime, static historical view
 * - Live: anchor=null with duration, window slides forward with current time
 *
 * For live pages, startTime/endTime are computed from duration at request time.
 */
public record PageKey(
    String dataType,  // CANDLES, AGGTRADES, FUNDING, OI, PREMIUM
    String symbol,
    String timeframe, // null for AGGTRADES, FUNDING, OI
    long startTime,
    long endTime,
    Long anchor,      // null = live (window moves with current time), else fixed end time
    long duration     // window duration in milliseconds (0 for legacy anchored pages)
) {
    /**
     * Legacy constructor for anchored pages with fixed time range.
     */
    public PageKey(String dataType, String symbol, String timeframe, long startTime, long endTime) {
        this(dataType, symbol, timeframe, startTime, endTime, endTime, endTime - startTime);
    }

    /**
     * Check if this is a live (moving) page.
     */
    public boolean isLive() {
        return anchor == null;
    }

    /**
     * Get the effective end time for this page.
     * For live pages, this is "now". For anchored pages, this is the anchor/endTime.
     */
    public long getEffectiveEndTime() {
        return anchor != null ? anchor : System.currentTimeMillis();
    }

    /**
     * Get the effective start time for this page.
     */
    public long getEffectiveStartTime() {
        return getEffectiveEndTime() - duration;
    }
    /**
     * Create a string representation of the key for use in URLs and maps.
     * Format: dataType:symbol[:timeframe]:anchor|LIVE:duration
     */
    public String toKeyString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(anchor != null ? anchor : "LIVE").append(":");
        sb.append(duration);
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

        String anchorStr = parts[idx++];
        Long anchor = anchorStr.equals("LIVE") ? null : Long.parseLong(anchorStr);

        long duration = Long.parseLong(parts[idx]);

        // Compute startTime/endTime from anchor and duration
        long endTime = anchor != null ? anchor : System.currentTimeMillis();
        long startTime = endTime - duration;

        return new PageKey(dataType, symbol, timeframe, startTime, endTime, anchor, duration);
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
    public static PageKey liveCandles(String symbol, String timeframe, long duration) {
        long now = System.currentTimeMillis();
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, now - duration, now, null, duration);
    }

    /**
     * Create an anchored candle page key.
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, long anchorTime, long duration) {
        return new PageKey("CANDLES", symbol.toUpperCase(), timeframe, anchorTime - duration, anchorTime, anchorTime, duration);
    }

    /**
     * Create a live aggTrades page key.
     */
    public static PageKey liveAggTrades(String symbol, long duration) {
        long now = System.currentTimeMillis();
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, now - duration, now, null, duration);
    }

    /**
     * Create an anchored aggTrades page key.
     */
    public static PageKey anchoredAggTrades(String symbol, long anchorTime, long duration) {
        return new PageKey("AGGTRADES", symbol.toUpperCase(), null, anchorTime - duration, anchorTime, anchorTime, duration);
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
