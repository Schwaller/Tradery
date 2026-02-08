package com.tradery.forge.data.sqlite;

/**
 * Maps each data type to its dedicated .db filename.
 * Each symbol gets a subdirectory with one DB per data type.
 * Layout: ~/.tradery/data/{symbol}/{type.filename}
 */
public enum DataStoreType {
    CANDLES("candles.db"),
    AGG_TRADES("agg_trades.db"),
    FUNDING_RATES("funding_rates.db"),
    OPEN_INTEREST("open_interest.db"),
    PREMIUM_INDEX("premium_index.db");

    private final String filename;

    DataStoreType(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * Route coverage data_type strings to the correct DB.
     * Coverage keys include: "klines", "candles", "candles:perp", "candles:spot",
     * "agg_trades", "funding_rates", "open_interest", "premium_index".
     */
    public static DataStoreType fromCoverageKey(String dataType) {
        if (dataType == null) {
            return CANDLES;
        }
        if (dataType.equals("klines") || dataType.equals("candles") || dataType.startsWith("candles:")) {
            return CANDLES;
        }
        return switch (dataType) {
            case "agg_trades" -> AGG_TRADES;
            case "funding_rates" -> FUNDING_RATES;
            case "open_interest" -> OPEN_INTEREST;
            case "premium_index" -> PREMIUM_INDEX;
            default -> CANDLES;
        };
    }
}
