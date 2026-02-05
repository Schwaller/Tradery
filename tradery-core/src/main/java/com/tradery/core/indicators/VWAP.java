package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Volume Weighted Average Price indicator.
 * Formula: Sum(Typical Price * Volume) / Sum(Volume)
 * Typical Price = (High + Low + Close) / 3
 */
public final class VWAP {

    private VWAP() {} // Utility class

    /**
     * Calculate session VWAP - cumulative from start of data.
     * @return Array where index corresponds to bar index.
     */
    public static double[] calculate(List<Candle> candles) {
        int n = candles.size();
        double[] result = new double[n];
        java.util.Arrays.fill(result, Double.NaN);

        if (n == 0) {
            return result;
        }

        double cumulativeTPV = 0;  // Cumulative (Typical Price * Volume)
        double cumulativeVolume = 0;

        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);
            double typicalPrice = (c.high() + c.low() + c.close()) / 3.0;
            cumulativeTPV += typicalPrice * c.volume();
            cumulativeVolume += c.volume();

            if (cumulativeVolume > 0) {
                result[i] = cumulativeTPV / cumulativeVolume;
            }
        }

        return result;
    }

    /**
     * Calculate VWAP at a specific bar index.
     */
    public static double calculateAt(List<Candle> candles, int barIndex) {
        if (barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        double cumulativeTPV = 0;
        double cumulativeVolume = 0;

        for (int i = 0; i <= barIndex; i++) {
            Candle c = candles.get(i);
            double typicalPrice = (c.high() + c.low() + c.close()) / 3.0;
            cumulativeTPV += typicalPrice * c.volume();
            cumulativeVolume += c.volume();
        }

        return cumulativeVolume > 0 ? cumulativeTPV / cumulativeVolume : Double.NaN;
    }
}
