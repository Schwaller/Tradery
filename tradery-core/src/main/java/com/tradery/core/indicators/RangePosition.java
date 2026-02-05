package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * RANGE_POSITION(period, skip) - Normalized price position relative to historical range.
 *
 * Returns where current close is relative to the high/low of the last `period` bars,
 * skipping the most recent `skip` bars (skip=0 skips only the current bar).
 *
 * Values:
 *   -1.0 = at range low
 *    0.0 = at range midpoint
 *   +1.0 = at range high
 *   < -1 = below range (multiples of range width below)
 *   > +1 = above range (multiples of range width above)
 *
 * Example: RANGE_POSITION(20, 0) on a bar where:
 *   - Range high (bars 1-20) = 100
 *   - Range low (bars 1-20) = 90
 *   - Current close = 95
 *   - Result = ((95 - 90) / (100 - 90)) * 2 - 1 = 0.0 (midpoint)
 *
 * If current close = 110 (above range):
 *   - Result = ((110 - 90) / 10) * 2 - 1 = 4.0 - 1 = 3.0 (two range widths above midpoint)
 */
public final class RangePosition {

    private RangePosition() {} // Utility class

    /**
     * Calculate RANGE_POSITION for all bars.
     *
     * @param candles List of candles
     * @param period Number of bars for the range lookback
     * @param skip Number of recent bars to skip (0 = skip only current bar)
     * @return Array of range position values (NaN for insufficient data)
     */
    public static double[] calculate(List<Candle> candles, int period, int skip) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        // Need at least period + skip + 1 bars
        int minBars = period + skip + 1;
        if (n < minBars) {
            return result;
        }

        for (int i = minBars - 1; i < n; i++) {
            result[i] = calculateAt(candles, period, skip, i);
        }

        return result;
    }

    /**
     * Calculate RANGE_POSITION at a specific bar.
     *
     * @param candles List of candles
     * @param period Number of bars for the range lookback
     * @param skip Number of recent bars to skip (0 = skip only current bar)
     * @param barIndex Current bar index
     * @return Range position value, or NaN if insufficient data
     */
    public static double calculateAt(List<Candle> candles, int period, int skip, int barIndex) {
        // We look at bars from (barIndex - skip - period) to (barIndex - skip - 1)
        // barIndex is the "current" bar whose close we're comparing
        int rangeEnd = barIndex - skip - 1;
        int rangeStart = rangeEnd - period + 1;

        if (rangeStart < 0 || rangeEnd < 0) {
            return Double.NaN;
        }

        // Find high and low of the range
        double rangeHigh = Double.NEGATIVE_INFINITY;
        double rangeLow = Double.POSITIVE_INFINITY;

        for (int j = rangeStart; j <= rangeEnd; j++) {
            Candle c = candles.get(j);
            rangeHigh = Math.max(rangeHigh, c.high());
            rangeLow = Math.min(rangeLow, c.low());
        }

        double rangeWidth = rangeHigh - rangeLow;
        if (rangeWidth <= 0) {
            // Flat range - return 0 if at that price, else extend appropriately
            double close = candles.get(barIndex).close();
            if (close == rangeLow) {
                return 0.0;
            }
            // Can't normalize with zero width, return position relative to the flat level
            // Arbitrary scaling: treat 1% move as 1 unit
            return (close - rangeLow) / (rangeLow * 0.01);
        }

        double close = candles.get(barIndex).close();

        // Normalize: -1 at low, +1 at high, extends beyond for breakouts
        // Formula: ((close - rangeLow) / rangeWidth) * 2 - 1
        return ((close - rangeLow) / rangeWidth) * 2.0 - 1.0;
    }

    /**
     * Get the range high used for calculation at a specific bar.
     * Useful for charting/debugging.
     */
    public static double getRangeHighAt(List<Candle> candles, int period, int skip, int barIndex) {
        int rangeEnd = barIndex - skip - 1;
        int rangeStart = rangeEnd - period + 1;

        if (rangeStart < 0 || rangeEnd < 0) {
            return Double.NaN;
        }

        double rangeHigh = Double.NEGATIVE_INFINITY;
        for (int j = rangeStart; j <= rangeEnd; j++) {
            rangeHigh = Math.max(rangeHigh, candles.get(j).high());
        }
        return rangeHigh;
    }

    /**
     * Get the range low used for calculation at a specific bar.
     * Useful for charting/debugging.
     */
    public static double getRangeLowAt(List<Candle> candles, int period, int skip, int barIndex) {
        int rangeEnd = barIndex - skip - 1;
        int rangeStart = rangeEnd - period + 1;

        if (rangeStart < 0 || rangeEnd < 0) {
            return Double.NaN;
        }

        double rangeLow = Double.POSITIVE_INFINITY;
        for (int j = rangeStart; j <= rangeEnd; j++) {
            rangeLow = Math.min(rangeLow, candles.get(j).low());
        }
        return rangeLow;
    }
}
