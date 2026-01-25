package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * OHLCV candle data with extended Binance kline fields.
 * Stored as CSV in ~/.tradery/data/SYMBOL/RESOLUTION/
 *
 * Extended fields from Binance klines API:
 * - tradeCount (index 8): Number of trades in the candle
 * - quoteVolume (index 7): Volume in quote asset (e.g., USD for BTCUSDT)
 * - takerBuyVolume (index 9): Aggressive buy volume (base asset)
 * - takerBuyQuoteVolume (index 10): Aggressive buy volume (quote asset)
 *
 * For backwards compatibility, extended fields default to -1 when not available.
 */
public record Candle(
    long timestamp,
    double open,
    double high,
    double low,
    double close,
    double volume,
    int tradeCount,
    double quoteVolume,
    double takerBuyVolume,
    double takerBuyQuoteVolume
) {
    /**
     * Constructor without extended fields (backwards compatibility).
     */
    public Candle(long timestamp, double open, double high, double low, double close, double volume) {
        this(timestamp, open, high, low, close, volume, -1, -1, -1, -1);
    }

    /**
     * Constructor with only trade count (backwards compatibility).
     */
    public Candle(long timestamp, double open, double high, double low, double close, double volume, int tradeCount) {
        this(timestamp, open, high, low, close, volume, tradeCount, -1, -1, -1);
    }

    /**
     * Check if trade count is available.
     */
    @JsonIgnore
    public boolean hasTradeCount() {
        return tradeCount >= 0;
    }

    /**
     * Check if extended volume data is available.
     */
    @JsonIgnore
    public boolean hasExtendedVolume() {
        return takerBuyVolume >= 0;
    }

    /**
     * Get taker sell volume (calculated from volume - takerBuyVolume).
     * Returns -1 if extended data not available.
     */
    @JsonIgnore
    public double takerSellVolume() {
        return hasExtendedVolume() ? volume - takerBuyVolume : -1;
    }

    /**
     * Get taker sell quote volume (calculated).
     * Returns -1 if extended data not available.
     */
    @JsonIgnore
    public double takerSellQuoteVolume() {
        return quoteVolume >= 0 && takerBuyQuoteVolume >= 0
            ? quoteVolume - takerBuyQuoteVolume : -1;
    }

    /**
     * Get delta (buy volume - sell volume) from OHLCV data.
     * Returns NaN if extended data not available.
     */
    @JsonIgnore
    public double delta() {
        if (!hasExtendedVolume()) return Double.NaN;
        return takerBuyVolume - takerSellVolume();
    }

    /**
     * Get buy/sell ratio (takerBuyVolume / volume).
     * Returns NaN if extended data not available.
     * Result is 0-1 where 0.5 = balanced, >0.5 = more buying, <0.5 = more selling.
     */
    @JsonIgnore
    public double buyRatio() {
        if (!hasExtendedVolume() || volume <= 0) return Double.NaN;
        return takerBuyVolume / volume;
    }

    /**
     * Parse a CSV line into a Candle.
     * Format: timestamp,open,high,low,close,volume[,tradeCount,quoteVolume,takerBuyVolume,takerBuyQuoteVolume]
     * Extended fields are optional for backwards compatibility.
     */
    public static Candle fromCsv(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid CSV line: " + line);
        }

        long timestamp = Long.parseLong(parts[0].trim());
        double open = Double.parseDouble(parts[1].trim());
        double high = Double.parseDouble(parts[2].trim());
        double low = Double.parseDouble(parts[3].trim());
        double close = Double.parseDouble(parts[4].trim());
        double volume = Double.parseDouble(parts[5].trim());

        // Extended fields (optional)
        int tradeCount = -1;
        double quoteVolume = -1;
        double takerBuyVolume = -1;
        double takerBuyQuoteVolume = -1;

        if (parts.length >= 7) {
            try {
                tradeCount = Integer.parseInt(parts[6].trim());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        if (parts.length >= 8) {
            try {
                quoteVolume = Double.parseDouble(parts[7].trim());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        if (parts.length >= 9) {
            try {
                takerBuyVolume = Double.parseDouble(parts[8].trim());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        if (parts.length >= 10) {
            try {
                takerBuyQuoteVolume = Double.parseDouble(parts[9].trim());
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return new Candle(timestamp, open, high, low, close, volume,
            tradeCount, quoteVolume, takerBuyVolume, takerBuyQuoteVolume);
    }

    /**
     * Convert to CSV format (includes extended fields if available).
     */
    public String toCsv() {
        // Always include all extended fields if any are available
        if (tradeCount >= 0 || quoteVolume >= 0 || takerBuyVolume >= 0) {
            return String.format("%d,%.8f,%.8f,%.8f,%.8f,%.8f,%d,%.8f,%.8f,%.8f",
                timestamp, open, high, low, close, volume,
                tradeCount,
                quoteVolume >= 0 ? quoteVolume : 0,
                takerBuyVolume >= 0 ? takerBuyVolume : 0,
                takerBuyQuoteVolume >= 0 ? takerBuyQuoteVolume : 0);
        }
        return String.format("%d,%.8f,%.8f,%.8f,%.8f,%.8f",
            timestamp, open, high, low, close, volume);
    }

    /**
     * Check if this is a bullish candle (close > open)
     */
    @JsonIgnore
    public boolean isBullish() {
        return close > open;
    }

    /**
     * Check if this is a bearish candle (close < open)
     */
    @JsonIgnore
    public boolean isBearish() {
        return close < open;
    }

    /**
     * Get the body size (absolute difference between open and close)
     */
    @JsonIgnore
    public double bodySize() {
        return Math.abs(close - open);
    }

    /**
     * Get the range (high - low)
     */
    @JsonIgnore
    public double range() {
        return high - low;
    }
}
