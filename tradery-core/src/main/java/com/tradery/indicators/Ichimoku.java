package com.tradery.indicators;

import com.tradery.indicators.registry.IndicatorContext;
import com.tradery.model.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Ichimoku Cloud (Ichimoku Kinko Hyo) indicator.
 */
public final class Ichimoku implements Indicator<Ichimoku.Result> {

    public static final Ichimoku INSTANCE = new Ichimoku();

    // Default Ichimoku parameters
    public static final int DEFAULT_CONVERSION_PERIOD = 9;
    public static final int DEFAULT_BASE_PERIOD = 26;
    public static final int DEFAULT_SPAN_B_PERIOD = 52;
    public static final int DEFAULT_DISPLACEMENT = 26;

    private Ichimoku() {}

    /**
     * Complete Ichimoku result containing all components.
     */
    public record Result(
        double[] tenkanSen,
        double[] kijunSen,
        double[] senkouSpanA,
        double[] senkouSpanB,
        double[] chikouSpan
    ) {}

    @Override
    public String id() { return "ICHIMOKU"; }

    @Override
    public String name() { return "Ichimoku Cloud"; }

    @Override
    public String description() { return "Japanese charting system with multiple trend/support/resistance lines"; }

    @Override
    public int warmupBars(Object... params) {
        int spanB = params.length > 2 ? (int) params[2] : DEFAULT_SPAN_B_PERIOD;
        int displacement = params.length > 3 ? (int) params[3] : DEFAULT_DISPLACEMENT;
        return spanB + displacement;
    }

    @Override
    public String cacheKey(Object... params) {
        int conv = params.length > 0 ? (int) params[0] : DEFAULT_CONVERSION_PERIOD;
        int base = params.length > 1 ? (int) params[1] : DEFAULT_BASE_PERIOD;
        int spanB = params.length > 2 ? (int) params[2] : DEFAULT_SPAN_B_PERIOD;
        int disp = params.length > 3 ? (int) params[3] : DEFAULT_DISPLACEMENT;
        return "ichimoku:" + conv + ":" + base + ":" + spanB + ":" + disp;
    }

    @Override
    public Result compute(IndicatorContext ctx, Object... params) {
        int conv = params.length > 0 ? (int) params[0] : DEFAULT_CONVERSION_PERIOD;
        int base = params.length > 1 ? (int) params[1] : DEFAULT_BASE_PERIOD;
        int spanB = params.length > 2 ? (int) params[2] : DEFAULT_SPAN_B_PERIOD;
        int disp = params.length > 3 ? (int) params[3] : DEFAULT_DISPLACEMENT;
        return calculate(ctx.candles(), conv, base, spanB, disp);
    }

    @Override
    public double valueAt(Result result, int barIndex) {
        // Default to tenkan-sen
        if (result == null || result.tenkanSen() == null || barIndex < 0 || barIndex >= result.tenkanSen().length) {
            return Double.NaN;
        }
        return result.tenkanSen()[barIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Result> resultType() { return Result.class; }

    // ===== Static calculation methods =====

    public static Result calculate(List<Candle> candles) {
        return calculate(candles, DEFAULT_CONVERSION_PERIOD, DEFAULT_BASE_PERIOD,
                        DEFAULT_SPAN_B_PERIOD, DEFAULT_DISPLACEMENT);
    }

    public static Result calculate(List<Candle> candles, int conversionPeriod, int basePeriod,
                                   int spanBPeriod, int displacement) {
        int size = candles.size();

        double[] tenkanSen = new double[size];
        double[] kijunSen = new double[size];
        double[] senkouSpanA = new double[size];
        double[] senkouSpanB = new double[size];
        double[] chikouSpan = new double[size];

        Arrays.fill(tenkanSen, Double.NaN);
        Arrays.fill(kijunSen, Double.NaN);
        Arrays.fill(senkouSpanA, Double.NaN);
        Arrays.fill(senkouSpanB, Double.NaN);
        Arrays.fill(chikouSpan, Double.NaN);

        if (candles.isEmpty()) {
            return new Result(tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan);
        }

        for (int i = 0; i < size; i++) {
            if (i >= conversionPeriod - 1) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int j = i - conversionPeriod + 1; j <= i; j++) {
                    high = Math.max(high, candles.get(j).high());
                    low = Math.min(low, candles.get(j).low());
                }
                tenkanSen[i] = (high + low) / 2.0;
            }

            if (i >= basePeriod - 1) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int j = i - basePeriod + 1; j <= i; j++) {
                    high = Math.max(high, candles.get(j).high());
                    low = Math.min(low, candles.get(j).low());
                }
                kijunSen[i] = (high + low) / 2.0;
            }

            if (i + displacement < size) {
                chikouSpan[i] = candles.get(i + displacement).close();
            }
        }

        for (int i = 0; i < size; i++) {
            int sourceIndex = i - displacement;

            if (sourceIndex >= 0 && !Double.isNaN(tenkanSen[sourceIndex]) && !Double.isNaN(kijunSen[sourceIndex])) {
                senkouSpanA[i] = (tenkanSen[sourceIndex] + kijunSen[sourceIndex]) / 2.0;
            }

            if (sourceIndex >= spanBPeriod - 1) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int j = sourceIndex - spanBPeriod + 1; j <= sourceIndex; j++) {
                    high = Math.max(high, candles.get(j).high());
                    low = Math.min(low, candles.get(j).low());
                }
                senkouSpanB[i] = (high + low) / 2.0;
            }
        }

        return new Result(tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan);
    }

    // Point-in-time calculations
    public static double tenkanSenAt(List<Candle> candles, int conversionPeriod, int barIndex) {
        if (barIndex < conversionPeriod - 1 || barIndex >= candles.size()) return Double.NaN;
        double high = Double.MIN_VALUE, low = Double.MAX_VALUE;
        for (int j = barIndex - conversionPeriod + 1; j <= barIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    public static double kijunSenAt(List<Candle> candles, int basePeriod, int barIndex) {
        if (barIndex < basePeriod - 1 || barIndex >= candles.size()) return Double.NaN;
        double high = Double.MIN_VALUE, low = Double.MAX_VALUE;
        for (int j = barIndex - basePeriod + 1; j <= barIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    public static double senkouSpanAAt(List<Candle> candles, int conversionPeriod, int basePeriod, int displacement, int barIndex) {
        int sourceIndex = barIndex - displacement;
        if (sourceIndex < 0) return Double.NaN;
        double tenkan = tenkanSenAt(candles, conversionPeriod, sourceIndex);
        double kijun = kijunSenAt(candles, basePeriod, sourceIndex);
        if (Double.isNaN(tenkan) || Double.isNaN(kijun)) return Double.NaN;
        return (tenkan + kijun) / 2.0;
    }

    public static double senkouSpanBAt(List<Candle> candles, int spanBPeriod, int displacement, int barIndex) {
        int sourceIndex = barIndex - displacement;
        if (sourceIndex < spanBPeriod - 1 || sourceIndex >= candles.size()) return Double.NaN;
        double high = Double.MIN_VALUE, low = Double.MAX_VALUE;
        for (int j = sourceIndex - spanBPeriod + 1; j <= sourceIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    public static double chikouSpanAt(List<Candle> candles, int displacement, int barIndex) {
        int futureIndex = barIndex + displacement;
        if (futureIndex >= candles.size()) return Double.NaN;
        return candles.get(futureIndex).close();
    }

    // Convenience with defaults
    public static double tenkanSenAt(List<Candle> candles, int barIndex) {
        return tenkanSenAt(candles, DEFAULT_CONVERSION_PERIOD, barIndex);
    }

    public static double kijunSenAt(List<Candle> candles, int barIndex) {
        return kijunSenAt(candles, DEFAULT_BASE_PERIOD, barIndex);
    }

    public static double senkouSpanAAt(List<Candle> candles, int barIndex) {
        return senkouSpanAAt(candles, DEFAULT_CONVERSION_PERIOD, DEFAULT_BASE_PERIOD, DEFAULT_DISPLACEMENT, barIndex);
    }

    public static double senkouSpanBAt(List<Candle> candles, int barIndex) {
        return senkouSpanBAt(candles, DEFAULT_SPAN_B_PERIOD, DEFAULT_DISPLACEMENT, barIndex);
    }

    public static double chikouSpanAt(List<Candle> candles, int barIndex) {
        return chikouSpanAt(candles, DEFAULT_DISPLACEMENT, barIndex);
    }
}
