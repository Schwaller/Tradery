package com.tradery.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a premium index kline data point from Binance Futures.
 * Premium = (Futures Price - Index Price) / Index Price
 *
 * This measures the spread between futures and spot markets.
 * Positive premium = futures trading above spot (bullish sentiment)
 * Negative premium = futures trading below spot (bearish sentiment)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PremiumIndex(
    long openTime,           // Kline open time in milliseconds
    double open,             // Premium at open (as decimal, e.g., 0.0001 = 0.01%)
    double high,             // Highest premium in period
    double low,              // Lowest premium in period
    double close,            // Premium at close
    long closeTime           // Kline close time
) {

    /**
     * Parse from Binance API response array.
     * Format: [openTime, open, high, low, close, ignored, closeTime, ...]
     */
    public static PremiumIndex fromBinanceArray(Object[] arr) {
        return new PremiumIndex(
            ((Number) arr[0]).longValue(),           // openTime
            Double.parseDouble(arr[1].toString()),   // open
            Double.parseDouble(arr[2].toString()),   // high
            Double.parseDouble(arr[3].toString()),   // low
            Double.parseDouble(arr[4].toString()),   // close
            ((Number) arr[6]).longValue()            // closeTime
        );
    }

    /**
     * Parse from CSV line (openTime,open,high,low,close,closeTime)
     */
    public static PremiumIndex fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid CSV format: " + line);
        }

        return new PremiumIndex(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Double.parseDouble(parts[3].trim()),
            Double.parseDouble(parts[4].trim()),
            Long.parseLong(parts[5].trim())
        );
    }

    /**
     * Convert to CSV line
     */
    public String toCsv() {
        return String.format("%d,%s,%s,%s,%s,%d",
            openTime, open, high, low, close, closeTime);
    }

    /**
     * Get premium close value as percentage (e.g., 0.0001 -> 0.01%)
     */
    @JsonIgnore
    public double closePercent() {
        return close * 100;
    }

    /**
     * Get premium high as percentage
     */
    @JsonIgnore
    public double highPercent() {
        return high * 100;
    }

    /**
     * Get premium low as percentage
     */
    @JsonIgnore
    public double lowPercent() {
        return low * 100;
    }

    /**
     * Check if premium is positive (futures above spot)
     */
    @JsonIgnore
    public boolean isPositive() {
        return close > 0;
    }

    /**
     * Check if premium is negative (futures below spot)
     */
    @JsonIgnore
    public boolean isNegative() {
        return close < 0;
    }
}
