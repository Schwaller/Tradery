package com.tradery.indicators;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Orderflow indicators that require aggregated trade data.
 * These provide buy/sell pressure analysis using actual trade-level data.
 */
public final class OrderflowIndicators {

    private OrderflowIndicators() {} // Utility class

    /**
     * Calculate delta (buy volume - sell volume) for each candle.
     * Aggregates trades into the candle they belong to by timestamp.
     *
     * @param trades     List of aggregated trades
     * @param candles    List of candles
     * @param resolution Timeframe resolution (e.g., "1h", "4h")
     * @return Array of delta values per candle
     */
    public static double[] delta(List<AggTrade> trades, List<Candle> candles, String resolution) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, 0);

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);

        // Create a map of candle start times for quick lookup
        // Each candle covers [timestamp, timestamp + resolution)
        int tradeIdx = 0;
        for (int i = 0; i < n && tradeIdx < trades.size(); i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double candleDelta = 0;
            boolean hasData = false;

            // Aggregate all trades within this candle's time window
            while (tradeIdx < trades.size()) {
                AggTrade trade = trades.get(tradeIdx);
                if (trade.timestamp() < candleStart) {
                    // Trade is before this candle, skip
                    tradeIdx++;
                } else if (trade.timestamp() < candleEnd) {
                    // Trade is within this candle
                    candleDelta += trade.delta();
                    hasData = true;
                    tradeIdx++;
                } else {
                    // Trade is for a future candle
                    break;
                }
            }

            result[i] = hasData ? candleDelta : Double.NaN;
        }

        return result;
    }

    /**
     * Get delta at a specific bar index.
     */
    public static double deltaAt(List<AggTrade> trades, List<Candle> candles, String resolution, int barIndex) {
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        double[] deltas = delta(trades, candles, resolution);
        return deltas[barIndex];
    }

    /**
     * Calculate cumulative delta (running sum of delta).
     * Shows overall buying/selling pressure over time.
     *
     * @param trades     List of aggregated trades
     * @param candles    List of candles
     * @param resolution Timeframe resolution
     * @return Array of cumulative delta values per candle
     */
    public static double[] cumulativeDelta(List<AggTrade> trades, List<Candle> candles, String resolution) {
        double[] deltas = delta(trades, candles, resolution);
        int n = deltas.length;
        double[] result = new double[n];

        double running = 0;
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(deltas[i])) {
                running += deltas[i];
            }
            result[i] = running;
        }

        return result;
    }

    /**
     * Get cumulative delta at a specific bar index.
     */
    public static double cumulativeDeltaAt(List<AggTrade> trades, List<Candle> candles, String resolution, int barIndex) {
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        double[] cumDeltas = cumulativeDelta(trades, candles, resolution);
        return cumDeltas[barIndex];
    }

    /**
     * Calculate buy volume per candle.
     */
    public static double[] buyVolume(List<AggTrade> trades, List<Candle> candles, String resolution) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, 0);

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n && tradeIdx < trades.size(); i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double volume = 0;
            boolean hasData = false;

            while (tradeIdx < trades.size()) {
                AggTrade trade = trades.get(tradeIdx);
                if (trade.timestamp() < candleStart) {
                    tradeIdx++;
                } else if (trade.timestamp() < candleEnd) {
                    volume += trade.buyVolume();
                    hasData = true;
                    tradeIdx++;
                } else {
                    break;
                }
            }

            result[i] = hasData ? volume : Double.NaN;
        }

        return result;
    }

    /**
     * Calculate sell volume per candle.
     */
    public static double[] sellVolume(List<AggTrade> trades, List<Candle> candles, String resolution) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, 0);

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n && tradeIdx < trades.size(); i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double volume = 0;
            boolean hasData = false;

            while (tradeIdx < trades.size()) {
                AggTrade trade = trades.get(tradeIdx);
                if (trade.timestamp() < candleStart) {
                    tradeIdx++;
                } else if (trade.timestamp() < candleEnd) {
                    volume += trade.sellVolume();
                    hasData = true;
                    tradeIdx++;
                } else {
                    break;
                }
            }

            result[i] = hasData ? volume : Double.NaN;
        }

        return result;
    }

    /**
     * Get resolution in milliseconds.
     */
    private static long getResolutionMs(String resolution) {
        return switch (resolution) {
            case "1m" -> 60_000L;
            case "3m" -> 3 * 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "2h" -> 2 * 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "6h" -> 6 * 60 * 60_000L;
            case "8h" -> 8 * 60 * 60_000L;
            case "12h" -> 12 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "3d" -> 3 * 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            default -> 60 * 60_000L; // Default to 1h
        };
    }
}
