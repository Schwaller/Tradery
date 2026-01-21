package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Relative Strength Index indicator.
 */
public final class RSI implements Indicator<double[]> {

    public static final RSI INSTANCE = new RSI();

    private RSI() {}

    @Override
    public String id() { return "RSI"; }

    @Override
    public String name() { return "Relative Strength Index"; }

    @Override
    public String description() { return "Momentum oscillator measuring speed of price changes (0-100)"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0] + 1; }

    @Override
    public String cacheKey(Object... params) { return "rsi:" + params[0]; }

    @Override
    public double[] compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0]);
    }

    @Override
    public double valueAt(double[] result, int barIndex) {
        if (result == null || barIndex < 0 || barIndex >= result.length) {
            return Double.NaN;
        }
        return result[barIndex];
    }

    @Override
    public Class<double[]> resultType() { return double[].class; }

    // ===== Static calculation methods =====

    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        Arrays.fill(result, Double.NaN);

        if (n < period + 1 || period <= 0) {
            return result;
        }

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

        if (avgLoss == 0) {
            result[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            result[period] = 100 - (100 / (1 + rs));
        }

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

    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period) {
            return Double.NaN;
        }
        double[] rsiValues = calculate(candles.subList(0, barIndex + 1), period);
        return rsiValues[barIndex];
    }
}
