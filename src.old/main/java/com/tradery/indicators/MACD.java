package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Moving Average Convergence Divergence indicator.
 */
public final class MACD implements Indicator<MACD.Result> {

    public static final MACD INSTANCE = new MACD();

    private MACD() {}

    /**
     * MACD result containing line, signal, and histogram.
     */
    public record Result(double[] line, double[] signal, double[] histogram) {}

    @Override
    public String id() { return "MACD"; }

    @Override
    public String name() { return "Moving Average Convergence Divergence"; }

    @Override
    public String description() { return "Trend-following momentum indicator showing relationship between two EMAs"; }

    @Override
    public int warmupBars(Object... params) {
        int slow = (int) params[1];
        int signal = (int) params[2];
        return slow + signal;
    }

    @Override
    public String cacheKey(Object... params) {
        return "macd:" + params[0] + ":" + params[1] + ":" + params[2];
    }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0], (int) params[1], (int) params[2]);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        if (result == null || result.line() == null || barIndex < 0 || barIndex >= result.line().length) {
            return Double.NaN;
        }
        return result.line()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        int n = candles.size();
        double[] line = new double[n];
        double[] signal = new double[n];
        double[] histogram = new double[n];
        Arrays.fill(line, Double.NaN);
        Arrays.fill(signal, Double.NaN);
        Arrays.fill(histogram, Double.NaN);

        if (n < slowPeriod) {
            return new Result(line, signal, histogram);
        }

        double[] fastEma = EMA.calculate(candles, fastPeriod);
        double[] slowEma = EMA.calculate(candles, slowPeriod);

        for (int i = slowPeriod - 1; i < n; i++) {
            if (!Double.isNaN(fastEma[i]) && !Double.isNaN(slowEma[i])) {
                line[i] = fastEma[i] - slowEma[i];
            }
        }

        double multiplier = 2.0 / (signalPeriod + 1);
        int signalStart = slowPeriod - 1 + signalPeriod - 1;

        if (signalStart < n) {
            double sum = 0;
            for (int i = slowPeriod - 1; i < signalStart; i++) {
                sum += line[i];
            }
            signal[signalStart] = sum / signalPeriod;
            histogram[signalStart] = line[signalStart] - signal[signalStart];

            for (int i = signalStart + 1; i < n; i++) {
                signal[i] = (line[i] - signal[i - 1]) * multiplier + signal[i - 1];
                histogram[i] = line[i] - signal[i];
            }
        }

        return new Result(line, signal, histogram);
    }
}
