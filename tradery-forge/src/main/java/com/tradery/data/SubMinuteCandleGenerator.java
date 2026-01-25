package com.tradery.data;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates sub-minute OHLCV candles from aggregated trade data.
 * Supports arbitrary time intervals (e.g., 10s, 15s, 30s).
 */
public class SubMinuteCandleGenerator {

    /**
     * Generate candles from aggTrades at the specified interval.
     *
     * @param trades List of aggregated trades (must be sorted by timestamp)
     * @param intervalSeconds Candle interval in seconds (e.g., 10, 15, 30)
     * @param startTime Start time in milliseconds (will be rounded down to interval boundary)
     * @param endTime End time in milliseconds
     * @return List of candles covering the time range
     */
    public List<Candle> generate(List<AggTrade> trades, int intervalSeconds, long startTime, long endTime) {
        if (trades == null || trades.isEmpty()) {
            return List.of();
        }

        long intervalMs = intervalSeconds * 1000L;

        // Round start time down to interval boundary
        long bucketStart = (startTime / intervalMs) * intervalMs;

        List<Candle> candles = new ArrayList<>();
        int tradeIndex = 0;

        // Track last close for carrying forward
        double lastClose = trades.get(0).price();

        while (bucketStart < endTime) {
            long bucketEnd = bucketStart + intervalMs;

            // Collect trades in this bucket
            double open = -1;
            double high = Double.MIN_VALUE;
            double low = Double.MAX_VALUE;
            double close = -1;
            double volume = 0;

            while (tradeIndex < trades.size()) {
                AggTrade trade = trades.get(tradeIndex);
                long ts = trade.timestamp();

                if (ts < bucketStart) {
                    // Trade before this bucket - skip but remember price
                    lastClose = trade.price();
                    tradeIndex++;
                    continue;
                }

                if (ts >= bucketEnd) {
                    // Trade is in next bucket - stop
                    break;
                }

                // Trade is in this bucket
                double price = trade.price();

                if (open < 0) {
                    open = price;
                }
                high = Math.max(high, price);
                low = Math.min(low, price);
                close = price;
                volume += trade.quantity(); // Base currency volume

                tradeIndex++;
            }

            // Create candle for this bucket
            if (open < 0) {
                // No trades in this bucket - carry forward
                open = lastClose;
                high = lastClose;
                low = lastClose;
                close = lastClose;
                volume = 0;
            } else {
                lastClose = close;
            }

            candles.add(new Candle(bucketStart, open, high, low, close, volume));
            bucketStart = bucketEnd;
        }

        return candles;
    }

    /**
     * Generate candles using the full time range of the provided trades.
     */
    public List<Candle> generate(List<AggTrade> trades, int intervalSeconds) {
        if (trades == null || trades.isEmpty()) {
            return List.of();
        }

        long startTime = trades.get(0).timestamp();
        long endTime = trades.get(trades.size() - 1).timestamp() + 1;

        return generate(trades, intervalSeconds, startTime, endTime);
    }

    /**
     * Parse a timeframe string to get the interval in seconds.
     * Returns -1 if not a sub-minute timeframe.
     *
     * @param timeframe Timeframe string (e.g., "10s", "15s", "1m", "1h")
     * @return Interval in seconds, or -1 if not sub-minute
     */
    public static int parseSubMinuteInterval(String timeframe) {
        if (timeframe == null) return -1;

        timeframe = timeframe.toLowerCase().trim();

        if (timeframe.endsWith("s")) {
            try {
                int seconds = Integer.parseInt(timeframe.substring(0, timeframe.length() - 1));
                if (seconds > 0 && seconds < 60) {
                    return seconds;
                }
            } catch (NumberFormatException e) {
                // Not a valid number
            }
        }

        return -1;
    }

    /**
     * Check if a timeframe is sub-minute (requires aggTrades).
     */
    public static boolean isSubMinute(String timeframe) {
        return parseSubMinuteInterval(timeframe) > 0;
    }
}
