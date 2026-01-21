package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Average Directional Index with Directional Movement Indicators.
 * ADX measures trend strength (0-100), +DI/-DI measure direction.
 */
public final class ADX implements Indicator<ADX.Result> {

    public static final ADX INSTANCE = new ADX();

    // Component indicators for +DI and -DI
    public static final Indicator<double[]> PLUS_DI = new PlusDI();
    public static final Indicator<double[]> MINUS_DI = new MinusDI();

    private ADX() {}

    /**
     * ADX result containing ADX, +DI, and -DI.
     */
    public record Result(double[] adx, double[] plusDI, double[] minusDI) {}

    @Override
    public String id() { return "ADX"; }

    @Override
    public String name() { return "Average Directional Index"; }

    @Override
    public String description() { return "Trend strength indicator (0-100) with +DI/-DI direction"; }

    @Override
    public int warmupBars(Object... params) { return (int) params[0] * 2; }

    @Override
    public String cacheKey(Object... params) { return "adx:" + params[0]; }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        return calculate(ctx.candles(), (int) params[0]);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        if (result == null || result.adx() == null || barIndex < 0 || barIndex >= result.adx().length) {
            return Double.NaN;
        }
        return result.adx()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] adx = new double[n];
        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        Arrays.fill(adx, Double.NaN);
        Arrays.fill(plusDI, Double.NaN);
        Arrays.fill(minusDI, Double.NaN);

        if (n < period * 2) {
            return new Result(adx, plusDI, minusDI);
        }

        double[] tr = new double[n];
        double[] plusDM = new double[n];
        double[] minusDM = new double[n];

        tr[0] = candles.get(0).high() - candles.get(0).low();
        plusDM[0] = 0;
        minusDM[0] = 0;

        for (int i = 1; i < n; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double highLow = curr.high() - curr.low();
            double highPrevClose = Math.abs(curr.high() - prev.close());
            double lowPrevClose = Math.abs(curr.low() - prev.close());
            tr[i] = Math.max(highLow, Math.max(highPrevClose, lowPrevClose));

            double upMove = curr.high() - prev.high();
            double downMove = prev.low() - curr.low();

            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }

        double smoothedTR = 0;
        double smoothedPlusDM = 0;
        double smoothedMinusDM = 0;

        for (int i = 0; i < period; i++) {
            smoothedTR += tr[i];
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
        }

        double[] dx = new double[n];
        Arrays.fill(dx, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            if (i > period - 1) {
                smoothedTR = smoothedTR - (smoothedTR / period) + tr[i];
                smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i];
                smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i];
            }

            if (smoothedTR > 0) {
                plusDI[i] = 100 * smoothedPlusDM / smoothedTR;
                minusDI[i] = 100 * smoothedMinusDM / smoothedTR;

                double diSum = plusDI[i] + minusDI[i];
                if (diSum > 0) {
                    dx[i] = 100 * Math.abs(plusDI[i] - minusDI[i]) / diSum;
                }
            }
        }

        int adxStart = period * 2 - 2;
        if (adxStart < n) {
            double dxSum = 0;
            for (int i = period - 1; i < adxStart; i++) {
                if (!Double.isNaN(dx[i])) {
                    dxSum += dx[i];
                }
            }
            adx[adxStart] = dxSum / period;

            for (int i = adxStart + 1; i < n; i++) {
                if (!Double.isNaN(dx[i])) {
                    adx[i] = (adx[i - 1] * (period - 1) + dx[i]) / period;
                }
            }
        }

        return new Result(adx, plusDI, minusDI);
    }

    // ===== Component Indicators =====

    private static class PlusDI implements Indicator<double[]> {
        @Override public String id() { return "PLUS_DI"; }
        @Override public String name() { return "Plus Directional Indicator"; }
        @Override public String description() { return "Bullish directional movement component of ADX"; }
        @Override public int warmupBars(Object... params) { return (int) params[0]; }
        @Override public String cacheKey(Object... params) { return "plus_di:" + params[0]; }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            return ADX.calculate(ctx.candles(), (int) params[0]).plusDI();
        }

        @Override
        public double valueAt(double[] result, int barIndex) {
            if (result == null || barIndex < 0 || barIndex >= result.length) return Double.NaN;
            return result[barIndex];
        }

        @Override public Class<double[]> resultType() { return double[].class; }
    }

    private static class MinusDI implements Indicator<double[]> {
        @Override public String id() { return "MINUS_DI"; }
        @Override public String name() { return "Minus Directional Indicator"; }
        @Override public String description() { return "Bearish directional movement component of ADX"; }
        @Override public int warmupBars(Object... params) { return (int) params[0]; }
        @Override public String cacheKey(Object... params) { return "minus_di:" + params[0]; }

        @Override
        public double[] compute(IndicatorContext ctx, Object... params) {
            return ADX.calculate(ctx.candles(), (int) params[0]).minusDI();
        }

        @Override
        public double valueAt(double[] result, int barIndex) {
            if (result == null || barIndex < 0 || barIndex >= result.length) return Double.NaN;
            return result[barIndex];
        }

        @Override public Class<double[]> resultType() { return double[].class; }
    }

    // ===== Point-in-time convenience methods =====

    public static double adxAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period * 2 - 2) return Double.NaN;
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.adx()[barIndex];
    }

    public static double plusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) return Double.NaN;
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.plusDI()[barIndex];
    }

    public static double minusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) return Double.NaN;
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.minusDI()[barIndex];
    }
}
