package com.tradery.data.page;

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
    String exchange,           // "binance", "bybit", "okx", etc. (default: "binance")
    String symbol,
    String timeframe,          // null for AGGTRADES, FUNDING, OI
    String marketType,         // "spot" or "perp" (default: "perp")
    Long endTime,              // null = live (window moves with current time), else fixed end time
    long windowDurationMillis  // window duration in milliseconds
) {
    // Canonical constructor with defaults
    public PageKey {
        if (marketType == null) marketType = "perp";
        if (exchange == null) exchange = "binance";
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
     * Format: dataType:exchange:symbol[:timeframe]:marketType:endTime|LIVE:windowDurationMillis
     */
    public String toKeyString() {
        StringBuilder sb = new StringBuilder();
        sb.append(dataType).append(":");
        sb.append(exchange).append(":");
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
     *
     * Supports multiple formats for backward compatibility:
     * - New:  dataType:exchange:symbol[:timeframe]:marketType:endTime|LIVE:duration
     * - Old:  dataType:symbol[:timeframe]:marketType:endTime|LIVE:duration  (no exchange)
     * - Legacy: dataType:symbol[:timeframe]:startTime:endTime               (forge makeKey format)
     */
    public static PageKey fromKeyString(String keyString) {
        String[] parts = keyString.split(":");
        int idx = 0;

        String dataType = parts[idx++];

        // Determine if second part is exchange or symbol.
        // Exchange names are lowercase alpha (binance, bybit, okx).
        // Symbols are uppercase with digits (BTCUSDT, ETHUSDT).
        String exchange = "binance";
        String second = parts[idx];
        if (isExchangeName(second)) {
            exchange = parts[idx++];
        }

        String symbol = parts[idx++];

        String timeframe = null;
        String marketType = "perp";

        // Parse remaining parts
        int remaining = parts.length - idx;

        if (remaining == 4) {
            // timeframe:marketType:endTime:duration
            timeframe = parts[idx++];
            marketType = parts[idx++];
        } else if (remaining == 3) {
            String next = parts[idx];
            if (next.equals("spot") || next.equals("perp")) {
                // marketType:endTime:duration (no timeframe)
                marketType = parts[idx++];
            } else if (!next.equals("LIVE") && !isNumeric(next)) {
                // timeframe:endTime:duration (no marketType, old format)
                timeframe = parts[idx++];
                marketType = "perp";
            }
        } else if (remaining == 2) {
            // endTime:duration (no timeframe, no marketType, old format)
            marketType = "perp";
        }

        String endTimeStr = parts[idx++];
        Long endTime = endTimeStr.equals("LIVE") ? null : Long.parseLong(endTimeStr);

        long windowDurationMillis = Long.parseLong(parts[idx]);

        return new PageKey(dataType, exchange, symbol, timeframe, marketType, endTime, windowDurationMillis);
    }

    private static boolean isExchangeName(String s) {
        // Exchange names: lowercase alpha strings like "binance", "bybit", "okx"
        // Symbols: uppercase with digits like "BTCUSDT"
        if (s == null || s.isEmpty()) return false;
        // If it's all lowercase letters, it's an exchange name
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private static boolean isNumeric(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ========== Factory Methods ==========

    /**
     * Create a live candle page key (defaults to binance perp).
     */
    public static PageKey liveCandles(String symbol, String timeframe, long windowDurationMillis) {
        return liveCandles(symbol, timeframe, "perp", "binance", windowDurationMillis);
    }

    /**
     * Create a live candle page key with market type (defaults to binance).
     */
    public static PageKey liveCandles(String symbol, String timeframe, String marketType, long windowDurationMillis) {
        return liveCandles(symbol, timeframe, marketType, "binance", windowDurationMillis);
    }

    /**
     * Create a live candle page key with market type and exchange.
     */
    public static PageKey liveCandles(String symbol, String timeframe, String marketType, String exchange, long windowDurationMillis) {
        return new PageKey("CANDLES", exchange, symbol.toUpperCase(), timeframe, marketType, null, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key (defaults to binance perp).
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, long endTime, long windowDurationMillis) {
        return anchoredCandles(symbol, timeframe, "perp", "binance", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key with market type (defaults to binance).
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, String marketType, long endTime, long windowDurationMillis) {
        return anchoredCandles(symbol, timeframe, marketType, "binance", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored candle page key with market type and exchange.
     */
    public static PageKey anchoredCandles(String symbol, String timeframe, String marketType, String exchange, long endTime, long windowDurationMillis) {
        return new PageKey("CANDLES", exchange, symbol.toUpperCase(), timeframe, marketType, endTime, windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key (defaults to binance perp).
     */
    public static PageKey liveAggTrades(String symbol, long windowDurationMillis) {
        return liveAggTrades(symbol, "perp", "binance", windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key with market type (defaults to binance).
     */
    public static PageKey liveAggTrades(String symbol, String marketType, long windowDurationMillis) {
        return liveAggTrades(symbol, marketType, "binance", windowDurationMillis);
    }

    /**
     * Create a live aggTrades page key with market type and exchange.
     */
    public static PageKey liveAggTrades(String symbol, String marketType, String exchange, long windowDurationMillis) {
        return new PageKey("AGGTRADES", exchange, symbol.toUpperCase(), null, marketType, null, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key (defaults to binance perp).
     */
    public static PageKey anchoredAggTrades(String symbol, long endTime, long windowDurationMillis) {
        return anchoredAggTrades(symbol, "perp", "binance", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key with market type (defaults to binance).
     */
    public static PageKey anchoredAggTrades(String symbol, String marketType, long endTime, long windowDurationMillis) {
        return anchoredAggTrades(symbol, marketType, "binance", endTime, windowDurationMillis);
    }

    /**
     * Create an anchored aggTrades page key with market type and exchange.
     */
    public static PageKey anchoredAggTrades(String symbol, String marketType, String exchange, long endTime, long windowDurationMillis) {
        return new PageKey("AGGTRADES", exchange, symbol.toUpperCase(), null, marketType, endTime, windowDurationMillis);
    }

    // ========== Type Checks ==========

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
