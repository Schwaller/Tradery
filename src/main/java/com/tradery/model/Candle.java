package com.tradery.model;

/**
 * OHLCV candle data.
 * Stored as CSV in ~/.tradery/data/SYMBOL/RESOLUTION/
 */
public record Candle(
    long timestamp,
    double open,
    double high,
    double low,
    double close,
    double volume
) {
    /**
     * Parse a CSV line into a Candle
     * Format: timestamp,open,high,low,close,volume
     */
    public static Candle fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid CSV line: " + line);
        }
        return new Candle(
            Long.parseLong(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            Double.parseDouble(parts[3].trim()),
            Double.parseDouble(parts[4].trim()),
            Double.parseDouble(parts[5].trim())
        );
    }

    /**
     * Convert to CSV format
     */
    public String toCsv() {
        return String.format("%d,%.8f,%.8f,%.8f,%.8f,%.8f",
            timestamp, open, high, low, close, volume);
    }

    /**
     * Check if this is a bullish candle (close > open)
     */
    public boolean isBullish() {
        return close > open;
    }

    /**
     * Check if this is a bearish candle (close < open)
     */
    public boolean isBearish() {
        return close < open;
    }

    /**
     * Get the body size (absolute difference between open and close)
     */
    public double bodySize() {
        return Math.abs(close - open);
    }

    /**
     * Get the range (high - low)
     */
    public double range() {
        return high - low;
    }
}
