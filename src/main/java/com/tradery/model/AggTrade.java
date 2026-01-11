package com.tradery.model;

/**
 * Aggregated trade data from Binance.
 * Stored as CSV in ~/.tradery/aggtrades/SYMBOL/
 *
 * isBuyerMaker: true = the buyer is the maker (i.e., this is a sell/down-tick)
 *               false = the seller is the maker (i.e., this is a buy/up-tick)
 */
public record AggTrade(
    long aggTradeId,
    double price,
    double quantity,
    long firstTradeId,
    long lastTradeId,
    long timestamp,
    boolean isBuyerMaker
) {
    /**
     * Parse a CSV line into an AggTrade
     * Format: aggTradeId,price,quantity,firstTradeId,lastTradeId,timestamp,isBuyerMaker
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
     * Convert to CSV format
     */
    public String toCsv() {
        return String.format("%d,%.8f,%.8f,%d,%d,%d,%b",
            aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker);
    }

    /**
     * Get the buy volume (0 if this is a sell)
     */
    public double buyVolume() {
        return isBuyerMaker ? 0 : quantity;
    }

    /**
     * Get the sell volume (0 if this is a buy)
     */
    public double sellVolume() {
        return isBuyerMaker ? quantity : 0;
    }

    /**
     * Get the delta contribution (positive for buy, negative for sell)
     */
    public double delta() {
        return isBuyerMaker ? -quantity : quantity;
    }

    /**
     * Get the notional value (price * quantity)
     */
    public double notional() {
        return price * quantity;
    }
}
