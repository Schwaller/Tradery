package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Supertrend indicator calculation.
 * Supertrend is a trend-following indicator based on ATR.
 *
 * Formula:
 * - Basic Upper Band = (High + Low) / 2 + (Multiplier * ATR)
 * - Basic Lower Band = (High + Low) / 2 - (Multiplier * ATR)
 * - Final Upper Band = If current Basic Upper Band < previous Final Upper Band OR previous close > previous Final Upper Band,
 *                      then current Basic Upper Band, else previous Final Upper Band
 * - Final Lower Band = If current Basic Lower Band > previous Final Lower Band OR previous close < previous Final Lower Band,
 *                      then current Basic Lower Band, else previous Final Lower Band
 * - Supertrend = If close > previous Final Upper Band, then Final Lower Band (bullish)
 *               If close < previous Final Lower Band, then Final Upper Band (bearish)
 */
public final class Supertrend {

    private Supertrend() {} // Utility class

    public record Result(double[] upperBand, double[] lowerBand, double[] trend) {}

    /**
     * Calculate Supertrend for all bars.
     * @param candles Price data
     * @param period ATR period (typically 10)
     * @param multiplier ATR multiplier (typically 3.0)
     * @return Result containing upper band, lower band, and trend (1 = up, -1 = down)
     */
    public static Result calculate(List<Candle> candles, int period, double multiplier) {
        int size = candles.size();
        double[] upperBand = new double[size];
        double[] lowerBand = new double[size];
        double[] trend = new double[size];

        // Calculate ATR first
        double[] atr = ATR.calculate(candles, period);

        // Initialize with NaN for warmup period
        for (int i = 0; i < period; i++) {
            upperBand[i] = Double.NaN;
            lowerBand[i] = Double.NaN;
            trend[i] = Double.NaN;
        }

        if (size <= period) {
            return new Result(upperBand, lowerBand, trend);
        }

        // Calculate first valid Supertrend value
        Candle c = candles.get(period);
        double hl2 = (c.high() + c.low()) / 2;
        double basicUpper = hl2 + (multiplier * atr[period]);
        double basicLower = hl2 - (multiplier * atr[period]);

        upperBand[period] = basicUpper;
        lowerBand[period] = basicLower;
        // Start with uptrend if close is above the midpoint
        trend[period] = c.close() > hl2 ? 1 : -1;

        // Calculate subsequent values
        for (int i = period + 1; i < size; i++) {
            c = candles.get(i);
            Candle prevCandle = candles.get(i - 1);

            hl2 = (c.high() + c.low()) / 2;
            basicUpper = hl2 + (multiplier * atr[i]);
            basicLower = hl2 - (multiplier * atr[i]);

            // Final Upper Band calculation
            if (basicUpper < upperBand[i - 1] || prevCandle.close() > upperBand[i - 1]) {
                upperBand[i] = basicUpper;
            } else {
                upperBand[i] = upperBand[i - 1];
            }

            // Final Lower Band calculation
            if (basicLower > lowerBand[i - 1] || prevCandle.close() < lowerBand[i - 1]) {
                lowerBand[i] = basicLower;
            } else {
                lowerBand[i] = lowerBand[i - 1];
            }

            // Trend calculation
            if (trend[i - 1] == -1 && c.close() > upperBand[i - 1]) {
                // Trend change from down to up
                trend[i] = 1;
            } else if (trend[i - 1] == 1 && c.close() < lowerBand[i - 1]) {
                // Trend change from up to down
                trend[i] = -1;
            } else {
                // Trend continues
                trend[i] = trend[i - 1];
            }
        }

        return new Result(upperBand, lowerBand, trend);
    }

    /**
     * Calculate Supertrend trend direction at a specific bar.
     * @return 1 for uptrend, -1 for downtrend, NaN if insufficient data
     */
    public static double trendAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) {
            return Double.NaN;
        }

        Result result = calculate(candles, period, multiplier);
        return result.trend()[barIndex];
    }

    /**
     * Calculate Supertrend upper band at a specific bar.
     */
    public static double upperAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) {
            return Double.NaN;
        }

        Result result = calculate(candles, period, multiplier);
        return result.upperBand()[barIndex];
    }

    /**
     * Calculate Supertrend lower band at a specific bar.
     */
    public static double lowerAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) {
            return Double.NaN;
        }

        Result result = calculate(candles, period, multiplier);
        return result.lowerBand()[barIndex];
    }
}
