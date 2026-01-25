package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Aggregated trade data from cryptocurrency exchanges.
 * Stored in SQLite with exchange-aware schema.
 *
 * isBuyerMaker: true = the buyer is the maker (i.e., this is a sell/down-tick)
 *               false = the seller is the maker (i.e., this is a buy/up-tick)
 *
 * Multi-exchange fields:
 * - exchange: Source exchange (BINANCE, BYBIT, etc.)
 * - marketType: Market type (SPOT, FUTURES_PERP, FUTURES_DATED)
 * - rawSymbol: Original symbol as returned by exchange (for debugging)
 * - normalizedPrice: Price normalized to USD (for cross-exchange aggregation)
 */
public record AggTrade(
    long aggTradeId,
    double price,
    double quantity,
    long firstTradeId,
    long lastTradeId,
    long timestamp,
    boolean isBuyerMaker,
    // Multi-exchange fields (nullable for backward compatibility)
    Exchange exchange,
    DataMarketType marketType,
    String rawSymbol,
    double normalizedPrice
) {
    /**
     * Constructor for backward compatibility (single-exchange mode).
     * Defaults to BINANCE FUTURES_PERP with price = normalizedPrice.
     */
    public AggTrade(
        long aggTradeId,
        double price,
        double quantity,
        long firstTradeId,
        long lastTradeId,
        long timestamp,
        boolean isBuyerMaker
    ) {
        this(aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker,
             Exchange.BINANCE, DataMarketType.FUTURES_PERP, null, price);
    }

    /**
     * Create AggTrade with exchange info (normalized price defaults to raw price).
     */
    public static AggTrade withExchange(
        long aggTradeId,
        double price,
        double quantity,
        long firstTradeId,
        long lastTradeId,
        long timestamp,
        boolean isBuyerMaker,
        Exchange exchange,
        DataMarketType marketType,
        String rawSymbol
    ) {
        return new AggTrade(aggTradeId, price, quantity, firstTradeId, lastTradeId,
                           timestamp, isBuyerMaker, exchange, marketType, rawSymbol, price);
    }

    /**
     * Create a copy with normalized price set.
     */
    public AggTrade withNormalizedPrice(double normalizedPrice) {
        return new AggTrade(aggTradeId, price, quantity, firstTradeId, lastTradeId,
                           timestamp, isBuyerMaker, exchange, marketType, rawSymbol, normalizedPrice);
    }

    /**
     * Parse a CSV line into an AggTrade (legacy 7-field format).
     * Format: aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker
     * Defaults to BINANCE FUTURES_PERP.
     */
    public static AggTrade fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid CSV line: " + line);
        }
        return new AggTrade(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Long.parseLong(parts[3].trim()),
            Long.parseLong(parts[4].trim()),
            Long.parseLong(parts[5].trim()),
            Boolean.parseBoolean(parts[6].trim())
        );
    }

    /**
     * Parse a CSV line with extended format (11-field with exchange info).
     * Format: aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker,exchange,marketType,rawSymbol,normalizedPrice
     */
    public static AggTrade fromExtendedCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 11) {
            // Fall back to legacy format
            return fromCsv(line);
        }
        Exchange exchange = Exchange.fromConfigKey(parts[7].trim());
        DataMarketType marketType = DataMarketType.fromConfigKey(parts[8].trim());
        String rawSymbol = parts[9].trim();
        if (rawSymbol.isEmpty()) rawSymbol = null;
        double normalizedPrice = Double.parseDouble(parts[10].trim());

        return new AggTrade(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Long.parseLong(parts[3].trim()),
            Long.parseLong(parts[4].trim()),
            Long.parseLong(parts[5].trim()),
            Boolean.parseBoolean(parts[6].trim()),
            exchange != null ? exchange : Exchange.BINANCE,
            marketType != null ? marketType : DataMarketType.FUTURES_PERP,
            rawSymbol,
            normalizedPrice
        );
    }

    /**
     * Convert to CSV format (legacy 7-field).
     */
    public String toCsv() {
        return String.format("%d,%.8f,%.8f,%d,%d,%d,%b",
            aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker);
    }

    /**
     * Convert to extended CSV format (11-field with exchange info).
     */
    public String toExtendedCsv() {
        return String.format("%d,%.8f,%.8f,%d,%d,%d,%b,%s,%s,%s,%.8f",
            aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker,
            exchange != null ? exchange.getConfigKey() : "binance",
            marketType != null ? marketType.getConfigKey() : "perp",
            rawSymbol != null ? rawSymbol : "",
            normalizedPrice);
    }

    /**
     * Get the buy volume (0 if this is a sell)
     */
    @JsonIgnore
    public double buyVolume() {
        return isBuyerMaker ? 0 : quantity;
    }

    /**
     * Get the sell volume (0 if this is a buy)
     */
    @JsonIgnore
    public double sellVolume() {
        return isBuyerMaker ? quantity : 0;
    }

    /**
     * Get the delta contribution (positive for buy, negative for sell)
     */
    @JsonIgnore
    public double delta() {
        return isBuyerMaker ? -quantity : quantity;
    }

    /**
     * Get the notional value (price * quantity)
     */
    @JsonIgnore
    public double notional() {
        return price * quantity;
    }

    /**
     * Get the notional value using normalized price.
     */
    @JsonIgnore
    public double normalizedNotional() {
        return normalizedPrice * quantity;
    }

    /**
     * Get delta using normalized price for cross-exchange aggregation.
     */
    @JsonIgnore
    public double normalizedDelta() {
        return isBuyerMaker ? -normalizedNotional() : normalizedNotional();
    }

    /**
     * Check if this trade is from a specific exchange.
     */
    @JsonIgnore
    public boolean isFromExchange(Exchange ex) {
        return this.exchange == ex;
    }

    /**
     * Check if this trade is from futures market (perp or dated).
     */
    @JsonIgnore
    public boolean isFutures() {
        return marketType == DataMarketType.FUTURES_PERP || marketType == DataMarketType.FUTURES_DATED;
    }

    /**
     * Check if this trade is from spot market.
     */
    @JsonIgnore
    public boolean isSpot() {
        return marketType == DataMarketType.SPOT;
    }
}
