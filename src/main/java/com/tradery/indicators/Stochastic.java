package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Stochastic Oscillator - measures momentum by comparing close to high-low range.
 *
 * %K = (Close - Lowest Low) / (Highest High - Lowest Low) * 100
 * %D = SMA(%K, smoothing period)
 *
 * Values:
 *   0 = at lowest low of period
 *   50 = midpoint of range
 *   100 = at highest high of period
 *
 * Traditional interpretation:
 *   < 20 = oversold
 *   > 80 = overbought
 *
 * Note: Unlike RANGE_POSITION, Stochastic is clamped to 0-100.
 */
public final class Stochastic {

    private Stochastic() {} // Utility class

    /**
     * Full Stochastic result with %K and %D lines.
     */
    public record Result(double[] k, double[] d) {}

    /**
     * Calculate Stochastic %K and %D for all bars.
     *
     * @param candles List of candles
     * @param kPeriod Lookback period for %K (typically 14)
     * @param dPeriod Smoothing period for %D (typically 3)
     * @return Result with %K and %D arrays
     */
    public static Result calculate(List<Candle> candles, int kPeriod, int dPeriod) {
        int n = candles.size();
        double[] k = new double[n];
        double[] d = new double[n];
        java.util.Arrays.fill(k, Double.NaN);
        java.util.Arrays.fill(d, Double.NaN);

        if (n < kPeriod) {
            return new Result(k, d);
        }

        // Calculate %K
        for (int i = kPeriod - 1; i < n; i++) {
            k[i] = calculateKAt(candles, kPeriod, i);
        }

        // Calculate %D (SMA of %K)
        if (n >= kPeriod + dPeriod - 1) {
            for (int i = kPeriod + dPeriod - 2; i < n; i++) {
                double sum = 0;
                int count = 0;
                for (int j = i - dPeriod + 1; j <= i; j++) {
                    if (!Double.isNaN(k[j])) {
                        sum += k[j];
                        count++;
                    }
                }
                if (count == dPeriod) {
                    d[i] = sum / dPeriod;
                }
            }
        }

        return new Result(k, d);
    }

    /**
     * Calculate Stochastic %K at a specific bar.
     *
     * @param candles List of candles
     * @param period Lookback period
     * @param barIndex Current bar index
     * @return %K value (0-100), or NaN if insufficient data
     */
    public static double calculateKAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;

        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            Candle c = candles.get(j);
            highestHigh = Math.max(highestHigh, c.high());
            lowestLow = Math.min(lowestLow, c.low());
        }

        double range = highestHigh - lowestLow;
        if (range <= 0) {
            return 50.0; // Flat range, return midpoint
        }

        double close = candles.get(barIndex).close();
        return ((close - lowestLow) / range) * 100.0;
    }

    /**
     * Calculate Stochastic %D at a specific bar.
     *
     * @param candles List of candles
     * @param kPeriod Lookback period for %K
     * @param dPeriod Smoothing period for %D
     * @param barIndex Current bar index
     * @return %D value (0-100), or NaN if insufficient data
     */
    public static double calculateDAt(List<Candle> candles, int kPeriod, int dPeriod, int barIndex) {
        if (barIndex < kPeriod + dPeriod - 2) {
            return Double.NaN;
        }

        double sum = 0;
        for (int i = barIndex - dPeriod + 1; i <= barIndex; i++) {
            double kVal = calculateKAt(candles, kPeriod, i);
            if (Double.isNaN(kVal)) {
                return Double.NaN;
            }
            sum += kVal;
        }

        return sum / dPeriod;
    }
}