package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Range functions: HIGH_OF, LOW_OF, AVG_VOLUME.
 */
public final class RangeFunctions {

    private RangeFunctions() {} // Utility class

    // ========== HIGH_OF ==========

    /**
     * Highest high over period for all bars.
     */
    public static double[] highOf(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                max = Math.max(max, candles.get(j).high());
            }
            result[i] = max;
        }

        return result;
    }

    /**
     * Highest high over period at a specific bar.
     */
    public static double highOfAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double max = Double.NEGATIVE_INFINITY;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            max = Math.max(max, candles.get(j).high());
        }
        return max;
    }

    // ========== LOW_OF ==========

    /**
     * Lowest low over period for all bars.
     */
    public static double[] lowOf(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            double min = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                min = Math.min(min, candles.get(j).low());
            }
            result[i] = min;
        }

        return result;
    }

    /**
     * Lowest low over period at a specific bar.
     */
    public static double lowOfAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double min = Double.POSITIVE_INFINITY;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            min = Math.min(min, candles.get(j).low());
        }
        return min;
    }

    // ========== AVG_VOLUME ==========

    /**
     * Average volume over period for all bars.
     */
    public static double[] avgVolume(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period) {
            return result;
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).volume();
        }
        result[period - 1] = sum / period;

        for (int i = period; i < n; i++) {
            sum = sum - candles.get(i - period).volume() + candles.get(i).volume();
            result[i] = sum / period;
        }

        return result;
    }

    /**
     * Average volume over period at a specific bar.
     */
    public static double avgVolumeAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double sum = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            sum += candles.get(j).volume();
        }
        return sum / period;
    }
}
