package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Exponential Moving Average indicator.
 */
public final class EMA {

    private EMA() {} // Utility class

    /**
     * Calculate EMA for all bars.
     * @return Array where index corresponds to bar index. Invalid values (warmup period) are Double.NaN.
     */
    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period || period <= 0) {
            return result;
        }

        double multiplier = 2.0 / (period + 1);

        // First EMA is SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).close();
        }
        result[period - 1] = sum / period;

        // Calculate remaining EMAs
        for (int i = period; i < n; i++) {
            result[i] = (candles.get(i).close() - result[i - 1]) * multiplier + result[i - 1];
        }

        return result;
    }

    /**
     * Calculate EMA at a specific bar index.
     */
    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || period <= 0) {
            return Double.NaN;
        }

        // Calculate all EMA values up to barIndex
        double[] emaValues = calculate(candles.subList(0, barIndex + 1), period);
        return emaValues[barIndex];
    }
}
