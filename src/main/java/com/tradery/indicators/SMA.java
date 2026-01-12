package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Simple Moving Average indicator.
 */
public final class SMA {

    private SMA() {} // Utility class

    /**
     * Calculate SMA for all bars.
     * @return Array where index corresponds to bar index. Invalid values (warmup period) are Double.NaN.
     */
    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period || period <= 0) {
            return result;
        }

        // Calculate initial sum
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).close();
        }
        result[period - 1] = sum / period;

        // Slide the window
        for (int i = period; i < n; i++) {
            sum = sum - candles.get(i - period).close() + candles.get(i).close();
            result[i] = sum / period;
        }

        return result;
    }

    /**
     * Calculate SMA at a specific bar index.
     */
    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || period <= 0) {
            return Double.NaN;
        }

        double sum = 0;
        for (int i = barIndex - period + 1; i <= barIndex; i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }
}
