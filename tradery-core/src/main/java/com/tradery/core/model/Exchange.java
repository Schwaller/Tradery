package com.tradery.core.model;

/**
 * Supported cryptocurrency exchanges for orderflow data.
 */
public enum Exchange {
    BINANCE("Binance", "binance", "BIN"),
    BYBIT("Bybit", "bybit", "BYB"),
    OKX("OKX", "okx", "OKX"),
    COINBASE("Coinbase", "coinbase", "CB"),
    KRAKEN("Kraken", "kraken", "KRK"),
    BITFINEX("Bitfinex", "bitfinex", "BFX"),
    HYPERLIQUID("Hyperliquid", "hyperliquid", "HL");

    private final String displayName;
    private final String configKey;
    private final String shortName;

    Exchange(String displayName, String configKey, String shortName) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.shortName = shortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getShortName() {
        return shortName;
    }

    /**
     * Parse exchange from config key (case-insensitive)
     */
    public static Exchange fromConfigKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        for (Exchange e : values()) {
            if (e.configKey.equals(lower)) {
                return e;
            }
        }
        return null;
    }
}
