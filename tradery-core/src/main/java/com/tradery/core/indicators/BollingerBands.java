package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Bollinger Bands indicator.
 */
public final class BollingerBands implements Indicator<BollingerBands.Result> {

    public static final BollingerBands INSTANCE = new BollingerBands();

    private BollingerBands() {}

    /**
     * Bollinger Bands result containing upper, middle, lower, and width.
     */
    public record Result(double[] upper, double[] middle, double[] lower, double[] width) {}

    @Override
    public String id() { return "BBANDS"; }

    @Override
    public String name() { return "Bollinger Bands"; }

    @Override
    public String description() { return "Volatility bands placed above and below a moving average"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0]; }

    @Override
    public String cacheKey(Object... params) {
        return "bbands:" + params[0] + ":" + params[1];
    }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0], (double) params[1]);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        // Default to middle band
        if (result == null || result.middle() == null || barIndex < 0 || barIndex >= result.middle().length) {
            return Double.NaN;
        }
        return result.middle()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles, int period, double stdDevMultiplier) {
        int n = candles.size();
        double[] upper = new double[n];
        double[] middle = new double[n];
        double[] lower = new double[n];
        double[] width = new double[n];
        Arrays.fill(upper, Double.NaN);
        Arrays.fill(middle, Double.NaN);
        Arrays.fill(lower, Double.NaN);
        Arrays.fill(width, Double.NaN);

        if (n < period) {
            return new Result(upper, middle, lower, width);
        }

        double[] smaValues = SMA.calculate(candles, period);

        for (int i = period - 1; i < n; i++) {
            double mean = smaValues[i];
            middle[i] = mean;

            double sumSquaredDiff = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close() - mean;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);

            upper[i] = mean + stdDevMultiplier * stdDev;
            lower[i] = mean - stdDevMultiplier * stdDev;
            width[i] = upper[i] - lower[i];
        }

        return new Result(upper, middle, lower, width);
    }

    public static double upperAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) return Double.NaN;
        double[] stats = statsAt(candles, period, barIndex);
        return stats[0] + stdDevMultiplier * stats[1];
    }

    public static double middleAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        return SMA.calculateAt(candles, period, barIndex);
    }

    public static double lowerAt(List<Candle> candles, int period, double stdDevMultiplier, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) return Double.NaN;
        double[] stats = statsAt(candles, period, barIndex);
        return stats[0] - stdDevMultiplier * stats[1];
    }

    private static double[] statsAt(List<Candle> candles, int period, int barIndex) {
        double mean = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            mean += candles.get(j).close();
        }
        mean /= period;
        double sumSquaredDiff = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            double diff = candles.get(j).close() - mean;
            sumSquaredDiff += diff * diff;
        }
        return new double[] { mean, Math.sqrt(sumSquaredDiff / period) };
    }
}
