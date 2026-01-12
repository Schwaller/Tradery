package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Average True Range indicator.
 */
public final class ATR {

    private ATR() {} // Utility class

    /**
     * Calculate ATR for all bars.
     * @return Array where index corresponds to bar index. Invalid values (warmup period) are Double.NaN.
     */
    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period + 1) {
            return result;
        }

        // Calculate True Range for each bar
        double[] tr = new double[n];
        tr[0] = candles.get(0).high() - candles.get(0).low();

        for (int i = 1; i < n; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double highLow = curr.high() - curr.low();
            double highPrevClose = Math.abs(curr.high() - prev.close());
            double lowPrevClose = Math.abs(curr.low() - prev.close());

            tr[i] = Math.max(highLow, Math.max(highPrevClose, lowPrevClose));
        }

        // First ATR is SMA of TR
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += tr[i];
        }
        result[period - 1] = sum / period;

        // Smoothed ATR (Wilder's smoothing)
        for (int i = period; i < n; i++) {
            result[i] = (result[i - 1] * (period - 1) + tr[i]) / period;
        }

        return result;
    }

    /**
     * Calculate ATR at a specific bar index.
     */
    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        double[] atrValues = calculate(candles.subList(0, barIndex + 1), period);
        return atrValues[barIndex];
    }
}
