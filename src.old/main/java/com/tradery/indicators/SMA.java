package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Simple Moving Average indicator.
 */
public final class SMA implements Indicator<double[]> {

    public static final SMA INSTANCE = new SMA();

    private SMA() {}

    @Override
    public String id() { return "SMA"; }

    @Override
    public String name() { return "Simple Moving Average"; }

    @Override
    public String description() { return "Average of last N closing prices"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0]; }

    @Override
    public String cacheKey(Object... params) { return "sma:" + params[0]; }

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

    // ===== Static calculation methods (for direct use) =====

    public static double[] calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        Arrays.fill(result, Double.NaN);

        if (n < period || period <= 0) {
            return result;
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).close();
        }
        result[period - 1] = sum / period;

        for (int i = period; i < n; i++) {
            sum = sum - candles.get(i - period).close() + candles.get(i).close();
            result[i] = sum / period;
        }

        return result;
    }

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
