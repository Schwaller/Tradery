package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Average True Range indicator.
 */
public final class ATR implements Indicator<double[]> {

    public static final ATR INSTANCE = new ATR();

    private ATR() {}

    @Override
    public String id() { return "ATR"; }

    @Override
    public String name() { return "Average True Range"; }

    @Override
    public String description() { return "Volatility indicator based on true range"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0]; }

    @Override
    public String cacheKey(Object... params) { return "atr:" + params[0]; }

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

        if (n < period + 1) {
            return result;
        }

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

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += tr[i];
        }
        result[period - 1] = sum / period;

        for (int i = period; i < n; i++) {
            result[i] = (result[i - 1] * (period - 1) + tr[i]) / period;
        }

        return result;
    }

    public static double calculateAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        double[] atrValues = calculate(candles.subList(0, barIndex + 1), period);
        return atrValues[barIndex];
    }
}
