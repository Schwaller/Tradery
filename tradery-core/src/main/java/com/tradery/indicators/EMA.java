package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Exponential Moving Average indicator.
 */
public final class EMA implements Indicator<double[]> {

    public static final EMA INSTANCE = new EMA();

    private EMA() {}

    @Override
    public String id() { return "EMA"; }

    @Override
    public String name() { return "Exponential Moving Average"; }

    @Override
    public String description() { return "Weighted average giving more weight to recent prices"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0]; }

    @Override
    public String cacheKey(Object... params) { return "ema:" + params[0]; }

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

    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || period <= 0) {
            return Double.NaN;
        }
        double[] emaValues = calculate(candles.subList(0, barIndex + 1), period);
        return emaValues[barIndex];
    }
}
