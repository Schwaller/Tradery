package com.tradery.model;

/**
 * Modes for normalizing prices across different quote currencies.
 * Used when aggregating orderflow data from multiple exchanges.
 */
public enum PriceNormalizationMode {
    /**
     * Use original exchange prices without normalization.
     * Best for single-exchange analysis.
     */
    RAW("Raw Prices", "Use original prices from each exchange"),

    /**
     * Treat USDT and USDC as equivalent to $1.00 USD.
     * Simple and fast, suitable for most backtesting.
     */
    USDT_AS_USD("USDT as USD", "Treat stablecoins as $1.00"),

    /**
     * Normalize using live/historical USDT/USD rates.
     * More accurate during stablecoin depegs.
     */
    LIVE_RATE("Live Rate", "Use actual USDT/USD exchange rate"),

    /**
     * Align all prices to a reference exchange's price levels.
     * Best for visualizing cross-exchange orderflow.
     */
    REFERENCE_EXCHANGE("Reference Exchange", "Align to primary exchange prices");

    private final String displayName;
    private final String description;

    PriceNormalizationMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
