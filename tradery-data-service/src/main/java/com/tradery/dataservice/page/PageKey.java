package com.tradery.dataservice.page;

/**
 * Unique identifier for a data page.
 */
public record PageKey(
    String dataType,  // CANDLES, AGGTRADES, FUNDING, OI, PREMIUM
    String symbol,
    String timeframe, // null for AGGTRADES, FUNDING, OI
    long startTime,
    long endTime
) {
    /**
     * Create a string representation of the key for use in URLs and maps.
     */
    public String toKeyString() {
        if (timeframe == null) {
            return String.format("%s:%s:%d:%d", dataType, symbol, startTime, endTime);
        }
        return String.format("%s:%s:%s:%d:%d", dataType, symbol, timeframe, startTime, endTime);
    }

    /**
     * Parse a key string back into a PageKey.
     */
    public static PageKey fromKeyString(String keyString) {
        String[] parts = keyString.split(":");
        if (parts.length == 4) {
            return new PageKey(
                parts[0],
                parts[1],
                null,
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3])
            );
        } else if (parts.length == 5) {
            return new PageKey(
                parts[0],
                parts[1],
                parts[2],
                Long.parseLong(parts[3]),
                Long.parseLong(parts[4])
            );
        }
        throw new IllegalArgumentException("Invalid page key: " + keyString);
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
        return "AGGTRADES".equals(dataType);
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
        return "OI".equals(dataType);
    }

    /**
     * Check if this page key is for premium index data.
     */
    public boolean isPremium() {
        return "PREMIUM".equals(dataType);
    }
}
