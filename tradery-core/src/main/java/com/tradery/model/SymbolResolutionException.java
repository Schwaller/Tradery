package com.tradery.model;

/**
 * Exception thrown when symbol resolution fails in strict mode.
 * This occurs when:
 * - No mapping exists for the requested canonical symbol
 * - No mapping exists for the requested exchange/market/quote combination
 * - Derivation is disabled (strict mode) and fallback would be needed
 */
public class SymbolResolutionException extends RuntimeException {

    private final String canonicalSymbol;
    private final Exchange exchange;
    private final DataMarketType marketType;
    private final QuoteCurrency quoteCurrency;

    public SymbolResolutionException(String canonicalSymbol, Exchange exchange,
                                      DataMarketType marketType, QuoteCurrency quoteCurrency) {
        super(buildMessage(canonicalSymbol, exchange, marketType, quoteCurrency));
        this.canonicalSymbol = canonicalSymbol;
        this.exchange = exchange;
        this.marketType = marketType;
        this.quoteCurrency = quoteCurrency;
    }

    public SymbolResolutionException(String canonicalSymbol, Exchange exchange,
                                      DataMarketType marketType, QuoteCurrency quoteCurrency,
                                      String additionalInfo) {
        super(buildMessage(canonicalSymbol, exchange, marketType, quoteCurrency) + " - " + additionalInfo);
        this.canonicalSymbol = canonicalSymbol;
        this.exchange = exchange;
        this.marketType = marketType;
        this.quoteCurrency = quoteCurrency;
    }

    private static String buildMessage(String canonicalSymbol, Exchange exchange,
                                        DataMarketType marketType, QuoteCurrency quoteCurrency) {
        return String.format(
                "No symbol mapping found for %s on %s (%s/%s). " +
                "Add mapping to exchanges.yaml or enable derivation.",
                canonicalSymbol,
                exchange != null ? exchange.getDisplayName() : "unknown exchange",
                marketType != null ? marketType.getConfigKey() : "unknown market",
                quoteCurrency != null ? quoteCurrency.getSymbol() : "unknown quote"
        );
    }

    public String getCanonicalSymbol() {
        return canonicalSymbol;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public DataMarketType getMarketType() {
        return marketType;
    }

    public QuoteCurrency getQuoteCurrency() {
        return quoteCurrency;
    }
}
