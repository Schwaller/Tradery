package com.tradery.dataclient.page;

/**
 * Supported data types for the remote page system.
 * Each type corresponds to a specific kind of market data from data-service.
 */
public enum DataType {
    /**
     * OHLCV candle data.
     * Requires timeframe specification (e.g., "1h", "1d").
     */
    CANDLES("Candles"),

    /**
     * Funding rate data (8-hour settlements from Binance Futures).
     * Does not require timeframe - rates are recorded at settlement times.
     */
    FUNDING("Funding"),

    /**
     * Open interest data (5-minute resolution from Binance Futures).
     * Does not require timeframe - stored at fixed 5m intervals.
     */
    OPEN_INTEREST("OI"),

    /**
     * Premium index klines (futures vs spot spread).
     * Requires timeframe to match strategy resolution.
     */
    PREMIUM_INDEX("Premium"),

    /**
     * Aggregated trade data (tick-level trades for orderflow analysis).
     * Does not require timeframe - raw trade data is stored.
     */
    AGG_TRADES("AggTrades");

    private final String displayName;

    DataType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get human-readable display name for UI.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this data type requires a timeframe parameter.
     */
    public boolean requiresTimeframe() {
        return this == CANDLES || this == PREMIUM_INDEX;
    }

    /**
     * Get the wire format name used in data-service communication.
     */
    public String toWireFormat() {
        return switch (this) {
            case CANDLES -> "CANDLES";
            case FUNDING -> "FUNDING";
            case OPEN_INTEREST -> "OI";
            case PREMIUM_INDEX -> "PREMIUM";
            case AGG_TRADES -> "AGGTRADES";
        };
    }

    /**
     * Parse from wire format name.
     */
    public static DataType fromWireFormat(String wire) {
        return switch (wire.toUpperCase()) {
            case "CANDLES" -> CANDLES;
            case "FUNDING" -> FUNDING;
            case "OI", "OPEN_INTEREST" -> OPEN_INTEREST;
            case "PREMIUM", "PREMIUM_INDEX" -> PREMIUM_INDEX;
            case "AGGTRADES", "AGG_TRADES" -> AGG_TRADES;
            default -> throw new IllegalArgumentException("Unknown data type: " + wire);
        };
    }
}
