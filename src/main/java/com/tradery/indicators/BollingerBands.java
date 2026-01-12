package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Bollinger Bands indicator.
 */
public final class BollingerBands {

    private BollingerBands() {} // Utility class

    /**
     * Bollinger Bands result containing upper, middle, and lower bands.
     */
    public record Result(double[] upper, double[] middle, double[] lower) {}

    /**
     * Calculate Bollinger Bands for all bars.
     */
    public static Result calculate(List<Candle> candles, int period, double stdDevMultiplier) {
        int n = candles.size();
        double[] upper = new double[n];
        double[] middle = new double[n];
        double[] lower = new double[n];
        java.util.Arrays.fill(upper, Double.NaN);
        java.util.Arrays.fill(middle, Double.NaN);
        java.util.Arrays.fill(lower, Double.NaN);

        if (n < period) {
            return new Result(upper, middle, lower);
        }

        double[] smaValues = SMA.calculate(candles, period);

        for (int i = period - 1; i < n; i++) {
            double mean = smaValues[i];
            middle[i] = mean;

            // Calculate standard deviation
            double sumSquaredDiff = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close() - mean;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);

            upper[i] = mean + stdDevMultiplier * stdDev;
            lower[i] = mean - stdDevMultiplier * stdDev;
        }

        return new Result(upper, middle, lower);
    }

    /**
     * Helper to calculate mean and standard deviation at a specific bar.
     */
    private static double[] statsAt(List<Candle> candles, int period, int barIndex) {
        double mean = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            mean += candles.get(j).close();
        }
        mean /= period;
        double sumSquaredDiff = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            double diff = candles.get(j).close() - mean;
            sumSquaredDiff += diff * diff;
        }
        return new double[] { mean, Math.sqrt(sumSquaredDiff / period) };
    }

    /**
     * Calculate upper band at specific bar.
     */
    public static double upperAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }
        double[] stats = statsAt(candles, period, barIndex);
        return stats[0] + stdDevMultiplier * stats[1];
    }

    /**
     * Calculate middle band at specific bar.
     */
    public static double middleAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        return SMA.calculateAt(candles, period, barIndex);
    }

    /**
     * Calculate lower band at specific bar.
     */
    public static double lowerAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }
        double[] stats = statsAt(candles, period, barIndex);
        return stats[0] - stdDevMultiplier * stats[1];
    }
}
