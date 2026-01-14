package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Ichimoku Cloud (Ichimoku Kinko Hyo) indicator.
 *
 * Components:
 * - Tenkan-sen (Conversion Line): (highest high + lowest low) / 2 over conversion period
 * - Kijun-sen (Base Line): (highest high + lowest low) / 2 over base period
 * - Senkou Span A (Leading Span A): (Tenkan-sen + Kijun-sen) / 2, plotted displacement periods ahead
 * - Senkou Span B (Leading Span B): (highest high + lowest low) / 2 over span B period, plotted displacement periods ahead
 * - Chikou Span (Lagging Span): Close price plotted displacement periods back
 *
 * Default parameters: conversionPeriod=9, basePeriod=26, spanBPeriod=52, displacement=26
 */
public final class Ichimoku {

    // Default Ichimoku parameters
    public static final int DEFAULT_CONVERSION_PERIOD = 9;   // Tenkan-sen
    public static final int DEFAULT_BASE_PERIOD = 26;        // Kijun-sen
    public static final int DEFAULT_SPAN_B_PERIOD = 52;      // Senkou Span B
    public static final int DEFAULT_DISPLACEMENT = 26;       // Cloud shift

    private Ichimoku() {} // Utility class

    /**
     * Complete Ichimoku result containing all components.
     */
    public record Result(
        double[] tenkanSen,    // Conversion Line
        double[] kijunSen,     // Base Line
        double[] senkouSpanA,  // Leading Span A (already shifted forward)
        double[] senkouSpanB,  // Leading Span B (already shifted forward)
        double[] chikouSpan    // Lagging Span (already shifted back)
    ) {}

    /**
     * Calculate all Ichimoku components with default parameters.
     */
    public static Result calculate(List<Candle> candles) {
        return calculate(candles, DEFAULT_CONVERSION_PERIOD, DEFAULT_BASE_PERIOD,
                        DEFAULT_SPAN_B_PERIOD, DEFAULT_DISPLACEMENT);
    }

    /**
     * Calculate all Ichimoku components with custom parameters.
     */
    public static Result calculate(List<Candle> candles, int conversionPeriod, int basePeriod,
                                   int spanBPeriod, int displacement) {
        int size = candles.size();

        double[] tenkanSen = new double[size];
        double[] kijunSen = new double[size];
        double[] senkouSpanA = new double[size];
        double[] senkouSpanB = new double[size];
        double[] chikouSpan = new double[size];

        // Initialize with NaN
        java.util.Arrays.fill(tenkanSen, Double.NaN);
        java.util.Arrays.fill(kijunSen, Double.NaN);
        java.util.Arrays.fill(senkouSpanA, Double.NaN);
        java.util.Arrays.fill(senkouSpanB, Double.NaN);
        java.util.Arrays.fill(chikouSpan, Double.NaN);

        if (candles.isEmpty()) {
            return new Result(tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan);
        }

        // Calculate Tenkan-sen and Kijun-sen
        for (int i = 0; i < size; i++) {
            // Tenkan-sen (Conversion Line)
            if (i >= conversionPeriod - 1) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int j = i - conversionPeriod + 1; j <= i; j++) {
                    high = Math.max(high, candles.get(j).high());
                    low = Math.min(low, candles.get(j).low());
                }
                tenkanSen[i] = (high + low) / 2.0;
            }

            // Kijun-sen (Base Line)
            if (i >= basePeriod - 1) {
                double high = Double.MIN_VALUE;
                double low = Double.MAX_VALUE;
                for (int j = i - basePeriod + 1; j <= i; j++) {
                    high = Math.max(high, candles.get(j).high());
                    low = Math.min(low, candles.get(j).low());
                }
                kijunSen[i] = (high + low) / 2.0;
            }

            // Chikou Span (Lagging Span) - close price plotted displacement periods back
            // This means at index i, we store the close from i + displacement
            if (i + displacement < size) {
                chikouSpan[i] = candles.get(i + displacement).close();
            }
        }

        // Calculate Senkou Span A and B (shifted forward by displacement)
        for (int i = 0; i < size; i++) {
            int sourceIndex = i - displacement;

            // Senkou Span A = (Tenkan + Kijun) / 2, calculated at sourceIndex and plotted at i
            if (sourceIndex >= 0 && !Double.isNaN(tenkanSen[sourceIndex]) && !Double.isNaN(kijunSen[sourceIndex])) {
                senkouSpanA[i] = (tenkanSen[sourceIndex] + kijunSen[sourceIndex]) / 2.0;
            }

            // Senkou Span B = mid of spanBPeriod high/low, calculated at sourceIndex and plotted at i
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

    // ========== Point-in-time calculations (for DSL) ==========

    /**
     * Calculate Tenkan-sen (Conversion Line) at a specific bar.
     */
    public static double tenkanSenAt(List<Candle> candles, int conversionPeriod, int barIndex) {
        if (barIndex < conversionPeriod - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int j = barIndex - conversionPeriod + 1; j <= barIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    /**
     * Calculate Kijun-sen (Base Line) at a specific bar.
     */
    public static double kijunSenAt(List<Candle> candles, int basePeriod, int barIndex) {
        if (barIndex < basePeriod - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int j = barIndex - basePeriod + 1; j <= barIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    /**
     * Calculate Senkou Span A at a specific bar (already accounting for displacement).
     * Returns the value that would be displayed at barIndex.
     */
    public static double senkouSpanAAt(List<Candle> candles, int conversionPeriod, int basePeriod,
                                       int displacement, int barIndex) {
        int sourceIndex = barIndex - displacement;
        if (sourceIndex < 0) {
            return Double.NaN;
        }

        double tenkan = tenkanSenAt(candles, conversionPeriod, sourceIndex);
        double kijun = kijunSenAt(candles, basePeriod, sourceIndex);

        if (Double.isNaN(tenkan) || Double.isNaN(kijun)) {
            return Double.NaN;
        }

        return (tenkan + kijun) / 2.0;
    }

    /**
     * Calculate Senkou Span B at a specific bar (already accounting for displacement).
     * Returns the value that would be displayed at barIndex.
     */
    public static double senkouSpanBAt(List<Candle> candles, int spanBPeriod, int displacement, int barIndex) {
        int sourceIndex = barIndex - displacement;
        if (sourceIndex < spanBPeriod - 1 || sourceIndex >= candles.size()) {
            return Double.NaN;
        }

        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int j = sourceIndex - spanBPeriod + 1; j <= sourceIndex; j++) {
            high = Math.max(high, candles.get(j).high());
            low = Math.min(low, candles.get(j).low());
        }
        return (high + low) / 2.0;
    }

    /**
     * Calculate Chikou Span at a specific bar.
     * Returns the close price from displacement bars in the future.
     */
    public static double chikouSpanAt(List<Candle> candles, int displacement, int barIndex) {
        int futureIndex = barIndex + displacement;
        if (futureIndex >= candles.size()) {
            return Double.NaN;
        }
        return candles.get(futureIndex).close();
    }

    // ========== Convenience methods with default parameters ==========

    public static double tenkanSenAt(List<Candle> candles, int barIndex) {
        return tenkanSenAt(candles, DEFAULT_CONVERSION_PERIOD, barIndex);
    }

    public static double kijunSenAt(List<Candle> candles, int barIndex) {
        return kijunSenAt(candles, DEFAULT_BASE_PERIOD, barIndex);
    }

    public static double senkouSpanAAt(List<Candle> candles, int barIndex) {
        return senkouSpanAAt(candles, DEFAULT_CONVERSION_PERIOD, DEFAULT_BASE_PERIOD,
                            DEFAULT_DISPLACEMENT, barIndex);
    }

    public static double senkouSpanBAt(List<Candle> candles, int barIndex) {
        return senkouSpanBAt(candles, DEFAULT_SPAN_B_PERIOD, DEFAULT_DISPLACEMENT, barIndex);
    }

    public static double chikouSpanAt(List<Candle> candles, int barIndex) {
        return chikouSpanAt(candles, DEFAULT_DISPLACEMENT, barIndex);
    }
}
