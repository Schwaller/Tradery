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
     * Parse exchange from config key (case-insensitive).
     * Returns null for dynamic exchanges like "hl-xyz" — use {@link #formatDisplayName} instead.
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

    /**
     * Format any exchange config key to a user-friendly display name.
     * Handles both enum-defined exchanges and dynamic ones like "hl-xyz" → "Hyperliquid (XYZ)".
     */
    public static String formatDisplayName(String configKey) {
        if (configKey == null) return "";
        Exchange ex = fromConfigKey(configKey);
        if (ex != null) return ex.displayName;

        // Dynamic Hyperliquid deployed dexes: "hl-xyz" → "Hyperliquid (XYZ)"
        if (configKey.startsWith("hl-") && configKey.length() > 3) {
            String dex = configKey.substring(3).toUpperCase();
            return "Hyperliquid (" + dex + ")";
        }

        return configKey;
    }

    /**
     * Parse a display name back to a config key.
     * Handles both enum-defined exchanges and dynamic "Hyperliquid (XYZ)" → "hl-xyz".
     */
    public static String parseConfigKey(String displayName) {
        if (displayName == null) return null;
        for (Exchange e : values()) {
            if (e.displayName.equals(displayName)) return e.configKey;
        }

        // Dynamic: "Hyperliquid (XYZ)" → "hl-xyz"
        if (displayName.startsWith("Hyperliquid (") && displayName.endsWith(")")) {
            String dex = displayName.substring("Hyperliquid (".length(), displayName.length() - 1);
            return "hl-" + dex.toLowerCase();
        }

        return displayName;
    }
}
