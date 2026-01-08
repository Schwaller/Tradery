package com.tradery.indicators;

import com.tradery.model.Candle;
import java.util.List;

/**
 * Technical indicator calculations.
 * All methods return arrays where index corresponds to bar index.
 * Invalid values (warmup period) are Double.NaN.
 */
public final class Indicators {

    private Indicators() {} // Utility class

    // ========== SMA ==========

    /**
     * Simple Moving Average
     */
    public static double[] sma(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period || period <= 0) {
            return result;
        }

        // Calculate initial sum
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).close();
        }
        result[period - 1] = sum / period;

        // Slide the window
        for (int i = period; i < n; i++) {
            sum = sum - candles.get(i - period).close() + candles.get(i).close();
            result[i] = sum / period;
        }

        return result;
    }

    public static double smaAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || period <= 0) {
            return Double.NaN;
        }

        double sum = 0;
        for (int i = barIndex - period + 1; i <= barIndex; i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }

    // ========== EMA ==========

    /**
     * Exponential Moving Average
     */
    public static double[] ema(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period || period <= 0) {
            return result;
        }

        double multiplier = 2.0 / (period + 1);

        // First EMA is SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).close();
        }
        result[period - 1] = sum / period;

        // Calculate remaining EMAs
        for (int i = period; i < n; i++) {
            result[i] = (candles.get(i).close() - result[i - 1]) * multiplier + result[i - 1];
        }

        return result;
    }

    public static double emaAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || period <= 0) {
            return Double.NaN;
        }

        // Calculate all EMA values up to barIndex
        double[] emaValues = ema(candles.subList(0, barIndex + 1), period);
        return emaValues[barIndex];
    }

    // ========== RSI ==========

    /**
     * Relative Strength Index
     */
    public static double[] rsi(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period + 1 || period <= 0) {
            return result;
        }

        // Calculate initial average gain and average loss
        double avgGain = 0;
        double avgLoss = 0;

        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }

        avgGain /= period;
        avgLoss /= period;

        // First RSI value
        if (avgLoss == 0) {
            result[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            result[period] = 100 - (100 / (1 + rs));
        }

        // Calculate remaining RSI values using Wilder's smoothing
        for (int i = period + 1; i < n; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            if (avgLoss == 0) {
                result[i] = 100;
            } else {
                double rs = avgGain / avgLoss;
                result[i] = 100 - (100 / (1 + rs));
            }
        }

        return result;
    }

    public static double rsiAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period) {
            return Double.NaN;
        }
        double[] rsiValues = rsi(candles.subList(0, barIndex + 1), period);
        return rsiValues[barIndex];
    }

    // ========== MACD ==========

    /**
     * MACD result containing line, signal, and histogram
     */
    public record MACDResult(double[] line, double[] signal, double[] histogram) {}

    /**
     * Moving Average Convergence Divergence
     */
    public static MACDResult macd(List<Candle> candles, int fastPeriod, int slowPeriod, int signalPeriod) {
        int n = candles.size();
        double[] line = new double[n];
        double[] signal = new double[n];
        double[] histogram = new double[n];
        java.util.Arrays.fill(line, Double.NaN);
        java.util.Arrays.fill(signal, Double.NaN);
        java.util.Arrays.fill(histogram, Double.NaN);

        if (n < slowPeriod) {
            return new MACDResult(line, signal, histogram);
        }

        double[] fastEma = ema(candles, fastPeriod);
        double[] slowEma = ema(candles, slowPeriod);

        // Calculate MACD line
        for (int i = slowPeriod - 1; i < n; i++) {
            if (!Double.isNaN(fastEma[i]) && !Double.isNaN(slowEma[i])) {
                line[i] = fastEma[i] - slowEma[i];
            }
        }

        // Calculate signal line (EMA of MACD line)
        double multiplier = 2.0 / (signalPeriod + 1);
        int signalStart = slowPeriod - 1 + signalPeriod - 1;

        if (signalStart < n) {
            // First signal is SMA of MACD line
            double sum = 0;
            for (int i = slowPeriod - 1; i < signalStart; i++) {
                sum += line[i];
            }
            signal[signalStart] = sum / signalPeriod;
            histogram[signalStart] = line[signalStart] - signal[signalStart];

            // Calculate remaining signal values
            for (int i = signalStart + 1; i < n; i++) {
                signal[i] = (line[i] - signal[i - 1]) * multiplier + signal[i - 1];
                histogram[i] = line[i] - signal[i];
            }
        }

        return new MACDResult(line, signal, histogram);
    }

    // ========== Bollinger Bands ==========

    /**
     * Bollinger Bands result
     */
    public record BollingerResult(double[] upper, double[] middle, double[] lower) {}

    /**
     * Bollinger Bands
     */
    public static BollingerResult bollingerBands(List<Candle> candles, int period, double stdDevMultiplier) {
        int n = candles.size();
        double[] upper = new double[n];
        double[] middle = new double[n];
        double[] lower = new double[n];
        java.util.Arrays.fill(upper, Double.NaN);
        java.util.Arrays.fill(middle, Double.NaN);
        java.util.Arrays.fill(lower, Double.NaN);

        if (n < period) {
            return new BollingerResult(upper, middle, lower);
        }

        double[] smaValues = sma(candles, period);

        for (int i = period - 1; i < n; i++) {
            double mean = smaValues[i];
            middle[i] = mean;

            // Calculate standard deviation
            double sumSquaredDiff = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close() - mean;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);

            upper[i] = mean + stdDevMultiplier * stdDev;
            lower[i] = mean - stdDevMultiplier * stdDev;
        }

        return new BollingerResult(upper, middle, lower);
    }

    // ========== ATR ==========

    /**
     * Average True Range
     */
    public static double[] atr(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period + 1) {
            return result;
        }

        // Calculate True Range for each bar
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

        // First ATR is SMA of TR
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += tr[i];
        }
        result[period - 1] = sum / period;

        // Smoothed ATR (Wilder's smoothing)
        for (int i = period; i < n; i++) {
            result[i] = (result[i - 1] * (period - 1) + tr[i]) / period;
        }

        return result;
    }

    public static double atrAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        double[] atrValues = atr(candles.subList(0, barIndex + 1), period);
        return atrValues[barIndex];
    }

    // ========== Range Functions ==========

    /**
     * Highest high over period
     */
    public static double[] highOf(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                max = Math.max(max, candles.get(j).high());
            }
            result[i] = max;
        }

        return result;
    }

    public static double highOfAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double max = Double.NEGATIVE_INFINITY;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            max = Math.max(max, candles.get(j).high());
        }
        return max;
    }

    /**
     * Lowest low over period
     */
    public static double[] lowOf(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        for (int i = period - 1; i < n; i++) {
            double min = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                min = Math.min(min, candles.get(j).low());
            }
            result[i] = min;
        }

        return result;
    }

    public static double lowOfAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double min = Double.POSITIVE_INFINITY;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            min = Math.min(min, candles.get(j).low());
        }
        return min;
    }

    // ========== Volume ==========

    /**
     * Average volume over period
     */
    public static double[] avgVolume(List<Candle> candles, int period) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n < period) {
            return result;
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += candles.get(i).volume();
        }
        result[period - 1] = sum / period;

        for (int i = period; i < n; i++) {
            sum = sum - candles.get(i - period).volume() + candles.get(i).volume();
            result[i] = sum / period;
        }

        return result;
    }

    public static double avgVolumeAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }

        double sum = 0;
        for (int j = barIndex - period + 1; j <= barIndex; j++) {
            sum += candles.get(j).volume();
        }
        return sum / period;
    }

    // ========== ADX / DMI ==========

    /**
     * ADX result containing ADX, +DI, and -DI
     */
    public record ADXResult(double[] adx, double[] plusDI, double[] minusDI) {}

    /**
     * Average Directional Index with Directional Movement Indicators
     * ADX measures trend strength (0-100), +DI/-DI measure direction
     */
    public static ADXResult adx(List<Candle> candles, int period) {
        int n = candles.size();
        double[] adx = new double[n];
        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        java.util.Arrays.fill(adx, Double.NaN);
        java.util.Arrays.fill(plusDI, Double.NaN);
        java.util.Arrays.fill(minusDI, Double.NaN);

        if (n < period * 2) {
            return new ADXResult(adx, plusDI, minusDI);
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

        return new ADXResult(adx, plusDI, minusDI);
    }

    public static double adxAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period * 2 - 2) {
            return Double.NaN;
        }
        ADXResult result = adx(candles.subList(0, barIndex + 1), period);
        return result.adx()[barIndex];
    }

    public static double plusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        ADXResult result = adx(candles.subList(0, barIndex + 1), period);
        return result.plusDI()[barIndex];
    }

    public static double minusDIAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1) {
            return Double.NaN;
        }
        ADXResult result = adx(candles.subList(0, barIndex + 1), period);
        return result.minusDI()[barIndex];
    }
}
