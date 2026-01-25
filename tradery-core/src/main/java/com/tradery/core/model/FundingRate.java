package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a funding rate data point from Binance Futures.
 * Funding rates are settled every 8 hours.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FundingRate(
    String symbol,           // e.g., "BTCUSDT"
    double fundingRate,      // e.g., 0.0001 = 0.01%
    long fundingTime,        // Unix timestamp in milliseconds
    double markPrice         // Mark price at funding time (optional)
) {

    /**
     * Parse from CSV line (symbol,fundingRate,fundingTime,markPrice)
     */
    public static FundingRate fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid CSV format: " + line);
        }

        String symbol = parts[0].trim();
        double fundingRate = Double.parseDouble(parts[1].trim());
        long fundingTime = Long.parseLong(parts[2].trim());
        double markPrice = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.0;

        return new FundingRate(symbol, fundingRate, fundingTime, markPrice);
    }

    /**
     * Convert to CSV line
     */
    public String toCsv() {
        return String.format("%s,%s,%d,%s",
            symbol,
            fundingRate,
            fundingTime,
            markPrice);
    }

    /**
     * Get funding rate as percentage (e.g., 0.0001 -> 0.01)
     */
    @JsonIgnore
    public double fundingRatePercent() {
        return fundingRate * 100;
    }

    /**
     * Check if this is a positive (longs pay shorts) funding rate
     */
    @JsonIgnore
    public boolean isPositive() {
        return fundingRate > 0;
    }

    /**
     * Check if this is a negative (shorts pay longs) funding rate
     */
    @JsonIgnore
    public boolean isNegative() {
        return fundingRate < 0;
    }
}
