package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Stochastic Oscillator - measures momentum by comparing close to high-low range.
 */
public final class Stochastic implements Indicator<Stochastic.Result> {

    public static final Stochastic INSTANCE = new Stochastic();

    private Stochastic() {}

    /**
     * Full Stochastic result with %K and %D lines.
     */
    public record Result(double[] k, double[] d) {}

    @Override
    public String id() { return "STOCHASTIC"; }

    @Override
    public String name() { return "Stochastic Oscillator"; }

    @Override
    public String description() { return "Momentum indicator comparing close to high-low range (0-100)"; }

    @Override
    public int warmupBars(Object... params) {
        int kPeriod = (int) params[0];
        int dPeriod = (int) params[1];
        return kPeriod + dPeriod;
    }

    @Override
    public String cacheKey(Object... params) {
        return "stochastic:" + params[0] + ":" + params[1];
    }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0], (int) params[1]);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        // Default to %K
        if (result == null || result.k() == null || barIndex < 0 || barIndex >= result.k().length) {
            return Double.NaN;
        }
        return result.k()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles, int kPeriod, int dPeriod) {
        int n = candles.size();
        double[] k = new double[n];
        double[] d = new double[n];
        Arrays.fill(k, Double.NaN);
        Arrays.fill(d, Double.NaN);

        if (n < kPeriod) {
            return new Result(k, d);
        }

        for (int i = kPeriod - 1; i < n; i++) {
            k[i] = calculateKAt(candles, kPeriod, i);
        }

        if (n >= kPeriod + dPeriod - 1) {
            for (int i = kPeriod + dPeriod - 2; i < n; i++) {
                double sum = 0;
                int count = 0;
                for (int j = i - dPeriod + 1; j <= i; j++) {
                    if (!Double.isNaN(k[j])) {
                        sum += k[j];
                        count++;
                    }
                }
                if (count == dPeriod) {
                    d[i] = sum / dPeriod;
                }
            }
        }

        return new Result(k, d);
    }

    public static double calculateKAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;

        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            Candle c = candles.get(j);
            highestHigh = Math.max(highestHigh, c.high());
            lowestLow = Math.min(lowestLow, c.low());
        }

        double range = highestHigh - lowestLow;
        if (range <= 0) {
            return 50.0;
        }

        double close = candles.get(barIndex).close();
        return ((close - lowestLow) / range) * 100.0;
    }

    public static double calculateDAt(List<Candle> candles, int kPeriod, int dPeriod, int barIndex) {
        if (barIndex < kPeriod + dPeriod - 2) {
            return Double.NaN;
        }

        double sum = 0;
        for (int i = barIndex - dPeriod + 1; i <= barIndex; i++) {
            double kVal = calculateKAt(candles, kPeriod, i);
            if (Double.isNaN(kVal)) {
                return Double.NaN;
            }
            sum += kVal;
        }

        return sum / dPeriod;
    }
}
