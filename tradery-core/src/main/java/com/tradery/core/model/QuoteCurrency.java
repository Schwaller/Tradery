package com.tradery.core.model;

/**
 * Quote currencies used across exchanges.
 * Used for price normalization when aggregating data from multiple exchanges.
 */
public enum QuoteCurrency {
    USD("USD", true),
    USDT("USDT", true),
    USDC("USDC", true),
    EUR("EUR", false);

    private final String symbol;
    private final boolean usdDenominated;

    QuoteCurrency(String symbol, boolean usdDenominated) {
        this.symbol = symbol;
        this.usdDenominated = usdDenominated;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Whether this currency is USD-denominated (USD, USDT, USDC)
     */
    public boolean isUsdDenominated() {
        return usdDenominated;
    }

    /**
     * Parse quote currency from symbol suffix
     */
    public static QuoteCurrency fromSymbolSuffix(String symbol) {
        if (symbol == null) return null;
        String upper = symbol.toUpperCase();
        if (upper.endsWith("USDT")) return USDT;
        if (upper.endsWith("USDC")) return USDC;
        if (upper.endsWith("USD")) return USD;
        if (upper.endsWith("EUR")) return EUR;
        return null;
    }

    /**
     * Detect quote currency from exchange-specific symbol format
     */
    public static QuoteCurrency detect(String rawSymbol, Exchange exchange) {
        if (rawSymbol == null) return null;

        // OKX uses BTC-USDT-SWAP format
        if (exchange == Exchange.OKX) {
            if (rawSymbol.contains("-USDT")) return USDT;
            if (rawSymbol.contains("-USDC")) return USDC;
            if (rawSymbol.contains("-USD")) return USD;
        }

        // Most exchanges use suffix format (BTCUSDT, BTCUSD)
        return fromSymbolSuffix(rawSymbol);
    }
}
