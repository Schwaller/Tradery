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
     * Calculate trade count per candle.
     */
    public static double[] tradeCount(List<AggTrade> trades, List<Candle> candles, String resolution) {
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

            int count = 0;
            boolean hasData = false;

            while (tradeIdx < trades.size()) {
                AggTrade trade = trades.get(tradeIdx);
                if (trade.timestamp() < candleStart) {
                    tradeIdx++;
                } else if (trade.timestamp() < candleEnd) {
                    count++;
                    hasData = true;
                    tradeIdx++;
                } else {
                    break;
                }
            }

            result[i] = hasData ? count : Double.NaN;
        }

        return result;
    }

    // ========== Large Trade / Whale Detection ==========

    /**
     * Calculate whale delta - delta from trades above the notional threshold only.
     * Isolates institutional/large player activity from retail noise.
     *
     * @param trades     List of aggregated trades
     * @param candles    List of candles
     * @param resolution Timeframe resolution
     * @param threshold  Minimum notional value in USD (e.g., 50000 for $50K trades)
     * @param barIndex   Bar index to calculate
     * @return Delta from large trades only
     */
    public static double whaleDeltaAt(List<AggTrade> trades, List<Candle> candles,
                                       String resolution, double threshold, int barIndex) {
        if (trades == null || trades.isEmpty() || candles == null || candles.isEmpty()) {
            return Double.NaN;
        }
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        long candleStart = candles.get(barIndex).timestamp();
        long candleEnd = candleStart + getResolutionMs(resolution);

        double delta = 0;
        boolean hasData = false;

        for (AggTrade trade : trades) {
            if (trade.timestamp() >= candleStart && trade.timestamp() < candleEnd) {
                if (trade.notional() >= threshold) {
                    delta += trade.delta();
                    hasData = true;
                }
            } else if (trade.timestamp() >= candleEnd) {
                break; // Trades are sorted by time, no need to check further
            }
        }

        return hasData ? delta : 0; // Return 0 if no large trades (not NaN)
    }

    /**
     * Calculate whale buy volume - buy volume from trades above the threshold only.
     */
    public static double whaleBuyVolumeAt(List<AggTrade> trades, List<Candle> candles,
                                           String resolution, double threshold, int barIndex) {
        if (trades == null || trades.isEmpty() || candles == null || candles.isEmpty()) {
            return Double.NaN;
        }
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        long candleStart = candles.get(barIndex).timestamp();
        long candleEnd = candleStart + getResolutionMs(resolution);

        double volume = 0;

        for (AggTrade trade : trades) {
            if (trade.timestamp() >= candleStart && trade.timestamp() < candleEnd) {
                if (trade.notional() >= threshold) {
                    volume += trade.buyVolume();
                }
            } else if (trade.timestamp() >= candleEnd) {
                break;
            }
        }

        return volume;
    }

    /**
     * Calculate whale sell volume - sell volume from trades above the threshold only.
     */
    public static double whaleSellVolumeAt(List<AggTrade> trades, List<Candle> candles,
                                            String resolution, double threshold, int barIndex) {
        if (trades == null || trades.isEmpty() || candles == null || candles.isEmpty()) {
            return Double.NaN;
        }
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        long candleStart = candles.get(barIndex).timestamp();
        long candleEnd = candleStart + getResolutionMs(resolution);

        double volume = 0;

        for (AggTrade trade : trades) {
            if (trade.timestamp() >= candleStart && trade.timestamp() < candleEnd) {
                if (trade.notional() >= threshold) {
                    volume += trade.sellVolume();
                }
            } else if (trade.timestamp() >= candleEnd) {
                break;
            }
        }

        return volume;
    }

    /**
     * Count large trades - number of trades above the threshold in the candle.
     */
    public static double largeTradeCountAt(List<AggTrade> trades, List<Candle> candles,
                                            String resolution, double threshold, int barIndex) {
        if (trades == null || trades.isEmpty() || candles == null || candles.isEmpty()) {
            return Double.NaN;
        }
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        long candleStart = candles.get(barIndex).timestamp();
        long candleEnd = candleStart + getResolutionMs(resolution);

        int count = 0;

        for (AggTrade trade : trades) {
            if (trade.timestamp() >= candleStart && trade.timestamp() < candleEnd) {
                if (trade.notional() >= threshold) {
                    count++;
                }
            } else if (trade.timestamp() >= candleEnd) {
                break;
            }
        }

        return count;
    }

    // ========== Whale Detection Array Methods ==========

    /**
     * Calculate whale delta array for all candles.
     */
    public static double[] whaleDelta(List<AggTrade> trades, List<Candle> candles,
                                       String resolution, double threshold) {
        int n = candles.size();
        double[] result = new double[n];

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n; i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double delta = 0;

            // Skip trades before this candle
            while (tradeIdx < trades.size() && trades.get(tradeIdx).timestamp() < candleStart) {
                tradeIdx++;
            }

            // Process trades within this candle (without advancing tradeIdx beyond candle)
            int tempIdx = tradeIdx;
            while (tempIdx < trades.size() && trades.get(tempIdx).timestamp() < candleEnd) {
                AggTrade trade = trades.get(tempIdx);
                if (trade.notional() >= threshold) {
                    delta += trade.delta();
                }
                tempIdx++;
            }

            result[i] = delta;
        }

        return result;
    }

    /**
     * Calculate retail delta array for all candles (trades below threshold).
     */
    public static double[] retailDelta(List<AggTrade> trades, List<Candle> candles,
                                        String resolution, double threshold) {
        int n = candles.size();
        double[] result = new double[n];

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n; i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double delta = 0;

            // Skip trades before this candle
            while (tradeIdx < trades.size() && trades.get(tradeIdx).timestamp() < candleStart) {
                tradeIdx++;
            }

            // Process trades within this candle (trades below threshold)
            int tempIdx = tradeIdx;
            while (tempIdx < trades.size() && trades.get(tempIdx).timestamp() < candleEnd) {
                AggTrade trade = trades.get(tempIdx);
                if (trade.notional() < threshold) {
                    delta += trade.delta();
                }
                tempIdx++;
            }

            result[i] = delta;
        }

        return result;
    }

    /**
     * Calculate whale buy volume array for all candles.
     */
    public static double[] whaleBuyVolume(List<AggTrade> trades, List<Candle> candles,
                                           String resolution, double threshold) {
        int n = candles.size();
        double[] result = new double[n];

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n; i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double volume = 0;

            while (tradeIdx < trades.size() && trades.get(tradeIdx).timestamp() < candleStart) {
                tradeIdx++;
            }

            int tempIdx = tradeIdx;
            while (tempIdx < trades.size() && trades.get(tempIdx).timestamp() < candleEnd) {
                AggTrade trade = trades.get(tempIdx);
                if (trade.notional() >= threshold) {
                    volume += trade.buyVolume();
                }
                tempIdx++;
            }

            result[i] = volume;
        }

        return result;
    }

    /**
     * Calculate whale sell volume array for all candles.
     */
    public static double[] whaleSellVolume(List<AggTrade> trades, List<Candle> candles,
                                            String resolution, double threshold) {
        int n = candles.size();
        double[] result = new double[n];

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n; i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            double volume = 0;

            while (tradeIdx < trades.size() && trades.get(tradeIdx).timestamp() < candleStart) {
                tradeIdx++;
            }

            int tempIdx = tradeIdx;
            while (tempIdx < trades.size() && trades.get(tempIdx).timestamp() < candleEnd) {
                AggTrade trade = trades.get(tempIdx);
                if (trade.notional() >= threshold) {
                    volume += trade.sellVolume();
                }
                tempIdx++;
            }

            result[i] = volume;
        }

        return result;
    }

    /**
     * Calculate large trade count array for all candles.
     */
    public static double[] largeTradeCount(List<AggTrade> trades, List<Candle> candles,
                                            String resolution, double threshold) {
        int n = candles.size();
        double[] result = new double[n];

        if (trades == null || trades.isEmpty() || candles.isEmpty()) {
            java.util.Arrays.fill(result, Double.NaN);
            return result;
        }

        long resolutionMs = getResolutionMs(resolution);
        int tradeIdx = 0;

        for (int i = 0; i < n; i++) {
            long candleStart = candles.get(i).timestamp();
            long candleEnd = candleStart + resolutionMs;

            int count = 0;

            while (tradeIdx < trades.size() && trades.get(tradeIdx).timestamp() < candleStart) {
                tradeIdx++;
            }

            int tempIdx = tradeIdx;
            while (tempIdx < trades.size() && trades.get(tempIdx).timestamp() < candleEnd) {
                AggTrade trade = trades.get(tempIdx);
                if (trade.notional() >= threshold) {
                    count++;
                }
                tempIdx++;
            }

            result[i] = count;
        }

        return result;
    }

    // ========== Utilities ==========

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
