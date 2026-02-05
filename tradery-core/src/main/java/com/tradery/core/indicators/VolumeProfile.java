package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Volume Profile indicator with POC (Point of Control), VAH (Value Area High), and VAL (Value Area Low).
 */
public final class VolumeProfile {

    private VolumeProfile() {} // Utility class

    /**
     * Volume Profile result containing POC, VAH, VAL, price levels, and volumes.
     */
    public record Result(
        double poc,    // Point of Control - price with highest volume
        double vah,    // Value Area High
        double val,    // Value Area Low
        double[] priceLevels,  // Price bin centers
        double[] volumes       // Volume at each bin
    ) {}

    /**
     * Calculate Volume Profile over a lookback period.
     * Divides price range into bins and aggregates volume at each level.
     *
     * @param period         Lookback period in bars
     * @param numBins        Number of price bins (default 24)
     * @param valueAreaPct   Percentage of volume for value area (typically 70%)
     */
    public static Result calculate(List<Candle> candles, int period, int numBins, double valueAreaPct) {
        if (candles.isEmpty() || period <= 0) {
            return new Result(Double.NaN, Double.NaN, Double.NaN, new double[0], new double[0]);
        }

        // Get the range of candles to analyze
        int startIdx = Math.max(0, candles.size() - period);
        int endIdx = candles.size();

        // Find price range
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (int i = startIdx; i < endIdx; i++) {
            Candle c = candles.get(i);
            minPrice = Math.min(minPrice, c.low());
            maxPrice = Math.max(maxPrice, c.high());
        }

        if (maxPrice <= minPrice) {
            double price = (maxPrice + minPrice) / 2;
            return new Result(price, price, price, new double[]{price}, new double[]{1});
        }

        // Create price bins
        double binSize = (maxPrice - minPrice) / numBins;
        double[] priceLevels = new double[numBins];
        double[] volumes = new double[numBins];

        for (int i = 0; i < numBins; i++) {
            priceLevels[i] = minPrice + binSize * (i + 0.5);  // Center of bin
        }

        // Distribute volume across bins
        // For each candle, distribute its volume proportionally across price levels it touched
        for (int i = startIdx; i < endIdx; i++) {
            Candle c = candles.get(i);
            double typicalPrice = (c.high() + c.low() + c.close()) / 3.0;

            // Find which bin the typical price falls into
            int binIdx = (int) ((typicalPrice - minPrice) / binSize);
            binIdx = Math.max(0, Math.min(numBins - 1, binIdx));
            volumes[binIdx] += c.volume();
        }

        // Find POC (bin with highest volume)
        int pocIdx = 0;
        double maxVol = volumes[0];
        for (int i = 1; i < numBins; i++) {
            if (volumes[i] > maxVol) {
                maxVol = volumes[i];
                pocIdx = i;
            }
        }
        double poc = priceLevels[pocIdx];

        // Calculate Value Area (70% of volume centered on POC)
        double totalVolume = 0;
        for (double v : volumes) {
            totalVolume += v;
        }

        double targetVolume = totalVolume * valueAreaPct / 100.0;
        double areaVolume = volumes[pocIdx];
        int lowIdx = pocIdx;
        int highIdx = pocIdx;

        // Expand from POC until we capture target volume
        while (areaVolume < targetVolume && (lowIdx > 0 || highIdx < numBins - 1)) {
            double lowVol = lowIdx > 0 ? volumes[lowIdx - 1] : 0;
            double highVol = highIdx < numBins - 1 ? volumes[highIdx + 1] : 0;

            if (lowVol >= highVol && lowIdx > 0) {
                lowIdx--;
                areaVolume += volumes[lowIdx];
            } else if (highIdx < numBins - 1) {
                highIdx++;
                areaVolume += volumes[highIdx];
            } else if (lowIdx > 0) {
                lowIdx--;
                areaVolume += volumes[lowIdx];
            }
        }

        double val = priceLevels[lowIdx] - binSize / 2;   // Lower edge of lowest bin
        double vah = priceLevels[highIdx] + binSize / 2;  // Upper edge of highest bin

        return new Result(poc, vah, val, priceLevels, volumes);
    }

    /**
     * Get POC at a specific bar index.
     */
    public static double pocAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period, 24, 70.0);
        return result.poc();
    }

    /**
     * Get VAH (Value Area High) at a specific bar index.
     */
    public static double vahAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period, 24, 70.0);
        return result.vah();
    }

    /**
     * Get VAL (Value Area Low) at a specific bar index.
     */
    public static double valAt(List<Candle> candles, int period, int barIndex) {
        if (barIndex < period - 1 || barIndex >= candles.size()) {
            return Double.NaN;
        }
        Result result = calculate(candles.subList(0, barIndex + 1), period, 24, 70.0);
        return result.val();
    }
}
