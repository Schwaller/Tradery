package com.tradery.model;

import java.util.*;

/**
 * Represents mapping configuration for a single canonical symbol (e.g., BTC, ETH).
 * Maps to exchange-specific symbols across different market types and quote currencies.
 *
 * Structure:
 * <pre>
 * BTC:
 *   displayName: Bitcoin
 *   aliases: [XBT]
 *   binance:
 *     perp:
 *       USDT: BTCUSDT
 *       USDC: BTCUSDC
 *     spot:
 *       USDT: BTCUSDT
 *   okx:
 *     perp:
 *       USDT: BTC-USDT-SWAP
 * </pre>
 */
public class SymbolMapping {

    private String id;
    private String displayName;
    private List<String> aliases = new ArrayList<>();

    // Exchange -> MarketType -> QuoteCurrency -> ExchangeSymbol
    private Map<Exchange, Map<DataMarketType, Map<QuoteCurrency, String>>> mappings = new EnumMap<>(Exchange.class);

    public SymbolMapping() {
    }

    public SymbolMapping(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases != null ? new ArrayList<>(aliases) : new ArrayList<>();
    }

    public Map<Exchange, Map<DataMarketType, Map<QuoteCurrency, String>>> getMappings() {
        return mappings;
    }

    public void setMappings(Map<Exchange, Map<DataMarketType, Map<QuoteCurrency, String>>> mappings) {
        this.mappings = mappings != null ? mappings : new EnumMap<>(Exchange.class);
    }

    /**
     * Get the exchange-specific symbol for a given combination.
     *
     * @param exchange Target exchange
     * @param marketType Market type (perp, spot, dated)
     * @param quoteCurrency Quote currency (USDT, USDC, USD)
     * @return Exchange-specific symbol or null if not mapped
     */
    public String getSymbol(Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency) {
        Map<DataMarketType, Map<QuoteCurrency, String>> exchangeMappings = mappings.get(exchange);
        if (exchangeMappings == null) return null;

        Map<QuoteCurrency, String> marketMappings = exchangeMappings.get(marketType);
        if (marketMappings == null) return null;

        return marketMappings.get(quoteCurrency);
    }

    /**
     * Set the exchange-specific symbol for a given combination.
     *
     * @param exchange Target exchange
     * @param marketType Market type
     * @param quoteCurrency Quote currency
     * @param symbol Exchange-specific symbol
     */
    public void setSymbol(Exchange exchange, DataMarketType marketType, QuoteCurrency quoteCurrency, String symbol) {
        mappings.computeIfAbsent(exchange, k -> new EnumMap<>(DataMarketType.class))
                .computeIfAbsent(marketType, k -> new EnumMap<>(QuoteCurrency.class))
                .put(quoteCurrency, symbol);
    }

    /**
     * Check if any mapping exists for the given exchange.
     */
    public boolean hasExchange(Exchange exchange) {
        return mappings.containsKey(exchange) && !mappings.get(exchange).isEmpty();
    }

    /**
     * Get all exchanges that have at least one mapping defined.
     */
    public Set<Exchange> getMappedExchanges() {
        Set<Exchange> result = EnumSet.noneOf(Exchange.class);
        for (Map.Entry<Exchange, Map<DataMarketType, Map<QuoteCurrency, String>>> entry : mappings.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get all market types that have mappings for a given exchange.
     */
    public Set<DataMarketType> getMappedMarketTypes(Exchange exchange) {
        Map<DataMarketType, Map<QuoteCurrency, String>> exchangeMappings = mappings.get(exchange);
        if (exchangeMappings == null) return EnumSet.noneOf(DataMarketType.class);
        return EnumSet.copyOf(exchangeMappings.keySet());
    }

    /**
     * Get all quote currencies that have mappings for a given exchange and market type.
     */
    public Set<QuoteCurrency> getMappedQuoteCurrencies(Exchange exchange, DataMarketType marketType) {
        Map<DataMarketType, Map<QuoteCurrency, String>> exchangeMappings = mappings.get(exchange);
        if (exchangeMappings == null) return EnumSet.noneOf(QuoteCurrency.class);

        Map<QuoteCurrency, String> marketMappings = exchangeMappings.get(marketType);
        if (marketMappings == null) return EnumSet.noneOf(QuoteCurrency.class);

        return EnumSet.copyOf(marketMappings.keySet());
    }

    @Override
    public String toString() {
        return "SymbolMapping{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", aliases=" + aliases +
                ", mappings=" + mappings.size() + " exchanges" +
                '}';
    }
}
