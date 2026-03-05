package com.tradery.dataservice.data.sqlite;

/**
 * Maps each data type to its dedicated .db filename.
 * Each symbol gets a subdirectory with one DB per data type.
 * Layout: ~/.tradery/data/{symbol}/{type.filename}
 */
public enum DataStoreType {
    CANDLES("candles.db"),
    AGG_TRADES("agg_trades.db"),
    FUNDING_RATES("funding_rates_perp.db"),
    OPEN_INTEREST("open_interest_perp.db"),
    PREMIUM_INDEX("premium_index_perp.db"),
    VOLUME_PROFILES("volume_profiles.db"),
    SPECTRUM("spectrum.db");

    private final String filename;

    DataStoreType(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * Get filename with qualifier suffix.
     * e.g., "agg_trades.db" + "binance_perp" → "agg_trades_binance_perp.db"
     * null/empty qualifier returns the base filename (for unsplit types).
     */
    public String qualifiedFilename(String qualifier) {
        if (qualifier == null || qualifier.isEmpty()) return filename;
        String base = filename.replace(".db", "");
        return base + "_" + qualifier + ".db";
    }

    /** True for AGG_TRADES — split by exchange × market_type. */
    public boolean isSplitByExchangeAndMarket() { return this == AGG_TRADES; }

    /** True for CANDLES, VOLUME_PROFILES, SPECTRUM — split by market_type only. */
    public boolean isSplitByMarket() { return this == CANDLES || this == VOLUME_PROFILES || this == SPECTRUM; }

    /** True if this type uses qualifier-based file splitting. */
    public boolean isSplit() { return isSplitByExchangeAndMarket() || isSplitByMarket(); }

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
            case "volume_profiles" -> VOLUME_PROFILES;
            case "spectrum" -> SPECTRUM;
            default -> CANDLES;
        };
    }
}
