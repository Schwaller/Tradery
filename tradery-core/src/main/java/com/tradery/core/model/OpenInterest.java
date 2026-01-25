package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an open interest data point from Binance Futures.
 * Open interest is available at 5-minute resolution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenInterest(
    String symbol,              // e.g., "BTCUSDT"
    long timestamp,             // Unix timestamp in milliseconds
    double openInterest,        // Open interest in contracts
    double openInterestValue    // Open interest value in USDT
) {

    /**
     * Parse from CSV line (symbol,timestamp,openInterest,openInterestValue)
     */
    public static OpenInterest fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid CSV format: " + line);
        }

        String symbol = parts[0].trim();
        long timestamp = Long.parseLong(parts[1].trim());
        double openInterest = Double.parseDouble(parts[2].trim());
        double openInterestValue = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.0;

        return new OpenInterest(symbol, timestamp, openInterest, openInterestValue);
    }

    /**
     * Convert to CSV line
     */
    public String toCsv() {
        return String.format("%s,%d,%s,%s",
            symbol,
            timestamp,
            openInterest,
            openInterestValue);
    }

    /**
     * Get open interest value in millions of USDT for display
     */
    @JsonIgnore
    public double openInterestValueMillions() {
        return openInterestValue / 1_000_000.0;
    }

    /**
     * Get open interest value in billions of USDT for display
     */
    @JsonIgnore
    public double openInterestValueBillions() {
        return openInterestValue / 1_000_000_000.0;
    }
}
