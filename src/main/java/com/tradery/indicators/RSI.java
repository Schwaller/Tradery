package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Relative Strength Index indicator.
 */
public final class RSI {

    private RSI() {} // Utility class

    /**
     * Calculate RSI for all bars.
     * @return Array where index corresponds to bar index. Invalid values (warmup period) are Double.NaN.
     */
    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period + 1 || period <= 0) {
            return result;
        }

        // Calculate initial average gain and average loss
        double avgGain = 0;
        double avgLoss = 0;

        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }

        avgGain /= period;
        avgLoss /= period;

        // First RSI value
        if (avgLoss == 0) {
            result[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            result[period] = 100 - (100 / (1 + rs));
        }

        // Calculate remaining RSI values using Wilder's smoothing
        for (int i = period + 1; i < n; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            if (avgLoss == 0) {
                result[i] = 100;
            } else {
                double rs = avgGain / avgLoss;
                result[i] = 100 - (100 / (1 + rs));
            }
        }

        return result;
    }

    /**
     * Calculate RSI at a specific bar index.
     */
    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period) {
            return Double.NaN;
        }
        double[] rsiValues = calculate(candles.subList(0, barIndex + 1), period);
        return rsiValues[barIndex];
    }
}
