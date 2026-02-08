package com.tradery.core.model;

/**
 * Market type for data source classification.
 * Identifies which market type the orderflow/trade data originated from.
 *
 * Note: This is different from {@link MarketType} which is used for backtest
 * simulation (determining holding costs). This enum classifies data sources.
 */
public enum DataMarketType {
    /**
     * Spot market data (direct asset trading)
     */
    SPOT("Spot", "spot", "SPOT"),

    /**
     * Perpetual futures (no expiration)
     */
    FUTURES_PERP("Perpetual Futures", "perp", "PERP"),

    /**
     * Dated/quarterly futures (with expiration)
     */
    FUTURES_DATED("Dated Futures", "dated", "DATED");

    private final String displayName;
    private final String configKey;
    private final String shortName;

    DataMarketType(String displayName, String configKey, String shortName) {
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
     * Parse market type from config key (case-insensitive)
     */
    public static DataMarketType fromConfigKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        for (DataMarketType m : values()) {
            if (m.configKey.equals(lower)) {
                return m;
            }
        }
        // Handle alternative names
        return switch (lower) {
            case "futures_perp", "perpetual", "swap" -> FUTURES_PERP;
            case "futures_dated", "quarterly", "delivery" -> FUTURES_DATED;
            case "futures" -> FUTURES_PERP; // Default futures to perpetual
            default -> null;
        };
    }

    /**
     * Detect market type from exchange-specific symbol
     */
    public static DataMarketType detect(String rawSymbol, Exchange exchange) {
        if (rawSymbol == null || exchange == null) return null;
        String upper = rawSymbol.toUpperCase();

        return switch (exchange) {
            case BINANCE -> {
                // Binance uses fapi for futures, api for spot
                // Symbol names are same (BTCUSDT) but endpoint differs
                // For aggTrades, we assume futures perp by default
                yield FUTURES_PERP;
            }
            case BYBIT -> {
                // Bybit: BTCUSDT (perp), BTCUSDC (perp), spot uses /v5/market/trades
                yield FUTURES_PERP;
            }
            case OKX -> {
                // OKX: BTC-USDT-SWAP (perp), BTC-USDT (spot), BTC-USD-240329 (dated)
                if (upper.contains("-SWAP")) yield FUTURES_PERP;
                if (upper.matches(".*-\\d{6}$")) yield FUTURES_DATED;
                yield SPOT;
            }
            case COINBASE -> SPOT; // Coinbase primarily spot
            case KRAKEN -> SPOT;   // Kraken primarily spot for this use case
            case BITFINEX -> SPOT;
            case HYPERLIQUID -> FUTURES_PERP;
        };
    }
}
