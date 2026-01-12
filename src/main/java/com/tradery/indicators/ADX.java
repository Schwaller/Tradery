package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Average Directional Index with Directional Movement Indicators.
 * ADX measures trend strength (0-100), +DI/-DI measure direction.
 */
public final class ADX {

    private ADX() {} // Utility class

    /**
     * ADX result containing ADX, +DI, and -DI.
     */
    public record Result(double[] adx, double[] plusDI, double[] minusDI) {}

    /**
     * Calculate ADX and DI values for all bars.
     */
    public static Result calculate(List<Candle> candles, int period) {
        int n = candles.size();
        double[] adx = new double[n];
        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        java.util.Arrays.fill(adx, Double.NaN);
        java.util.Arrays.fill(plusDI, Double.NaN);
        java.util.Arrays.fill(minusDI, Double.NaN);

        if (n < period * 2) {
            return new Result(adx, plusDI, minusDI);
        }

        // Calculate True Range, +DM, -DM for each bar
        double[] tr = new double[n];
        double[] plusDM = new double[n];
        double[] minusDM = new double[n];

        tr[0] = candles.get(0).high() - candles.get(0).low();
        plusDM[0] = 0;
        minusDM[0] = 0;

        for (int i = 1; i < n; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            // True Range
            double highLow = curr.high() - curr.low();
            double highPrevClose = Math.abs(curr.high() - prev.close());
            double lowPrevClose = Math.abs(curr.low() - prev.close());
            tr[i] = Math.max(highLow, Math.max(highPrevClose, lowPrevClose));

            // Directional Movement
            double upMove = curr.high() - prev.high();
            double downMove = prev.low() - curr.low();

            if (upMove > downMove && upMove > 0) {
                plusDM[i] = upMove;
            } else {
                plusDM[i] = 0;
            }

            if (downMove > upMove && downMove > 0) {
                minusDM[i] = downMove;
            } else {
                minusDM[i] = 0;
            }
        }

        // Smooth TR, +DM, -DM using Wilder's smoothing
        double smoothedTR = 0;
        double smoothedPlusDM = 0;
        double smoothedMinusDM = 0;

        // First smoothed values are sums
        for (int i = 0; i < period; i++) {
            smoothedTR += tr[i];
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
        }

        // Calculate +DI and -DI starting at period-1
        double[] dx = new double[n];
        java.util.Arrays.fill(dx, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            if (i > period - 1) {
                // Wilder's smoothing: smoothed = prev - (prev/period) + current
                smoothedTR = smoothedTR - (smoothedTR / period) + tr[i];
                smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM[i];
                smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM[i];
            }

            if (smoothedTR > 0) {
                plusDI[i] = 100 * smoothedPlusDM / smoothedTR;
                minusDI[i] = 100 * smoothedMinusDM / smoothedTR;

                // Calculate DX
                double diSum = plusDI[i] + minusDI[i];
                if (diSum > 0) {
                    dx[i] = 100 * Math.abs(plusDI[i] - minusDI[i]) / diSum;
                }
            }
        }

        // Smooth DX to get ADX using Wilder's smoothing
        // First ADX is average of first 'period' DX values
        int adxStart = period * 2 - 2;
        if (adxStart < n) {
            double dxSum = 0;
            for (int i = period - 1; i < adxStart; i++) {
                if (!Double.isNaN(dx[i])) {
                    dxSum += dx[i];
                }
            }
            adx[adxStart] = dxSum / period;

            // Smooth remaining ADX values
            for (int i = adxStart + 1; i < n; i++) {
                if (!Double.isNaN(dx[i])) {
                    adx[i] = (adx[i - 1] * (period - 1) + dx[i]) / period;
                }
            }
        }

        return new Result(adx, plusDI, minusDI);
    }

    /**
     * Calculate ADX at a specific bar index.
     */
    public static double adxAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period * 2 - 2) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.adx()[barIndex];
    }

    /**
     * Calculate +DI at a specific bar index.
     */
    public static double plusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.plusDI()[barIndex];
    }

    /**
     * Calculate -DI at a specific bar index.
     */
    public static double minusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period);
        return result.minusDI()[barIndex];
    }
}
