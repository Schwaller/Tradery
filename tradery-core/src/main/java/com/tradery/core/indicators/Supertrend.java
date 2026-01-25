package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Supertrend indicator - trend-following indicator based on ATR.
 */
public final class Supertrend implements Indicator<Supertrend.Result> {

    public static final Supertrend INSTANCE = new Supertrend();

    private Supertrend() {}

    public record Result(double[] upperBand, double[] lowerBand, double[] trend) {}

    @Override
    public String id() { return "SUPERTREND"; }

    @Override
    public String name() { return "Supertrend"; }

    @Override
    public String description() { return "ATR-based trend following indicator (1=up, -1=down)"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0] + 1; }

    @Override
    public String cacheKey(Object... params) {
        return "supertrend:" + params[0] + ":" + params[1];
    }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0], (double) params[1]);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        // Default to trend direction
        if (result == null || result.trend() == null || barIndex < 0 || barIndex >= result.trend().length) {
            return Double.NaN;
        }
        return result.trend()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles, int period, double multiplier) {
        int size = candles.size();
        double[] upperBand = new double[size];
        double[] lowerBand = new double[size];
        double[] trend = new double[size];

        double[] atr = ATR.calculate(candles, period);

        for (int i = 0; i < period; i++) {
            upperBand[i] = Double.NaN;
            lowerBand[i] = Double.NaN;
            trend[i] = Double.NaN;
        }

        if (size <= period) {
            return new Result(upperBand, lowerBand, trend);
        }

        Candle c = candles.get(period);
        double hl2 = (c.high() + c.low()) / 2;
        double basicUpper = hl2 + (multiplier * atr[period]);
        double basicLower = hl2 - (multiplier * atr[period]);

        upperBand[period] = basicUpper;
        lowerBand[period] = basicLower;
        trend[period] = c.close() > hl2 ? 1 : -1;

        for (int i = period + 1; i < size; i++) {
            c = candles.get(i);
            Candle prevCandle = candles.get(i - 1);

            hl2 = (c.high() + c.low()) / 2;
            basicUpper = hl2 + (multiplier * atr[i]);
            basicLower = hl2 - (multiplier * atr[i]);

            if (basicUpper < upperBand[i - 1] || prevCandle.close() > upperBand[i - 1]) {
                upperBand[i] = basicUpper;
            } else {
                upperBand[i] = upperBand[i - 1];
            }

            if (basicLower > lowerBand[i - 1] || prevCandle.close() < lowerBand[i - 1]) {
                lowerBand[i] = basicLower;
            } else {
                lowerBand[i] = lowerBand[i - 1];
            }

            if (trend[i - 1] == -1 && c.close() > upperBand[i - 1]) {
                trend[i] = 1;
            } else if (trend[i - 1] == 1 && c.close() < lowerBand[i - 1]) {
                trend[i] = -1;
            } else {
                trend[i] = trend[i - 1];
            }
        }

        return new Result(upperBand, lowerBand, trend);
    }

    public static double trendAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) return Double.NaN;
        return calculate(candles, period, multiplier).trend()[barIndex];
    }

    public static double upperAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) return Double.NaN;
        return calculate(candles, period, multiplier).upperBand()[barIndex];
    }

    public static double lowerAt(List<Candle> candles, int period, double multiplier, int barIndex) {
        if (candles == null || barIndex < period || barIndex >= candles.size()) return Double.NaN;
        return calculate(candles, period, multiplier).lowerBand()[barIndex];
    }
}
