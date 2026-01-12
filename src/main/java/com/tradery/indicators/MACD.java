package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Moving Average Convergence Divergence indicator.
 */
public final class MACD {

    private MACD() {} // Utility class

    /**
     * MACD result containing line, signal, and histogram.
     */
    public record Result(double[] line, double[] signal, double[] histogram) {}

    /**
     * Calculate MACD for all bars.
     * @return Result containing line, signal, and histogram arrays.
     */
    public static Result calculate(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        int n = candles.size();
        double[] line = new double[n];
        double[] signal = new double[n];
        double[] histogram = new double[n];
        java.util.Arrays.fill(line, Double.NaN);
        java.util.Arrays.fill(signal, Double.NaN);
        java.util.Arrays.fill(histogram, Double.NaN);

        if (n < slowPeriod) {
            return new Result(line, signal, histogram);
        }

        double[] fastEma = EMA.calculate(candles, fastPeriod);
        double[] slowEma = EMA.calculate(candles, slowPeriod);

        // Calculate MACD line
        for (int i = slowPeriod - 1; i < n; i++) {
            if (!Double.isNaN(fastEma[i]) && !Double.isNaN(slowEma[i])) {
                line[i] = fastEma[i] - slowEma[i];
            }
        }

        // Calculate signal line (EMA of MACD line)
        double multiplier = 2.0 / (signalPeriod + 1);
        int signalStart = slowPeriod - 1 + signalPeriod - 1;

        if (signalStart < n) {
            // First signal is SMA of MACD line
            double sum = 0;
            for (int i = slowPeriod - 1; i < signalStart; i++) {
                sum += line[i];
            }
            signal[signalStart] = sum / signalPeriod;
            histogram[signalStart] = line[signalStart] - signal[signalStart];

            // Calculate remaining signal values
            for (int i = signalStart + 1; i < n; i++) {
                signal[i] = (line[i] - signal[i - 1]) * multiplier + signal[i - 1];
                histogram[i] = line[i] - signal[i];
            }
        }

        return new Result(line, signal, histogram);
    }
}
