package com.tradery.core.indicators;

import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating Ray Trendlines - automatic descending/ascending trendline detection.
 *
 * Resistance rays: Start from ATH, rotate clockwise to find successive peaks
 * Support rays: Start from ATL, rotate counter-clockwise to find successive troughs
 *
 * Each ray connects two peaks/troughs and can be used to detect breakouts.
 */
public final class RotatingRays {

    private RotatingRays() {} // Utility class

    // ===== Indicator instances for registry =====
    public static final Indicator<RaySet> RESISTANCE_RAYS = new ResistanceRaysIndicator();
    public static final Indicator<RaySet> SUPPORT_RAYS = new SupportRaysIndicator();

    // ===== ResistanceRays Indicator =====
    private static class ResistanceRaysIndicator implements Indicator<RaySet> {
        @Override public String id() { return "RESISTANCE_RAYS"; }
        @Override public String name() { return "Resistance Rays"; }
        @Override public String description() { return "Descending trendlines from ATH"; }
        @Override public int warmupBars(Object... params) { return (int) params[0]; }
        @Override public String cacheKey(Object... params) { return "resistance_rays:" + params[0] + ":" + params[1]; }

        @Override
        public RaySet compute(IndicatorContext ctx, Object... params) {
            int lookback = (int) params[0];
            int skip = (int) params[1];
            return calculateResistanceRays(ctx.candles(), lookback, skip);
        }

        @Override
        public double valueAt(RaySet result, int barIndex) {
            return Double.NaN; // Use through RayFunctions
        }

        @Override
        public Class<RaySet> resultType() { return RaySet.class; }
    }

    // ===== SupportRays Indicator =====
    private static class SupportRaysIndicator implements Indicator<RaySet> {
        @Override public String id() { return "SUPPORT_RAYS"; }
        @Override public String name() { return "Support Rays"; }
        @Override public String description() { return "Ascending trendlines from ATL"; }
        @Override public int warmupBars(Object... params) { return (int) params[0]; }
        @Override public String cacheKey(Object... params) { return "support_rays:" + params[0] + ":" + params[1]; }

        @Override
        public RaySet compute(IndicatorContext ctx, Object... params) {
            int lookback = (int) params[0];
            int skip = (int) params[1];
            return calculateSupportRays(ctx.candles(), lookback, skip);
        }

        @Override
        public double valueAt(RaySet result, int barIndex) {
            return Double.NaN; // Use through RayFunctions
        }

        @Override
        public Class<RaySet> resultType() { return RaySet.class; }
    }

    /**
     * A single ray connecting two price points.
     */
    public record Ray(
        int startBar,      // Bar index of ray start
        double startPrice, // Price at ray start
        int endBar,        // Bar index of ray end (next peak/trough)
        double endPrice,   // Price at ray end
        double slope       // Price change per bar
    ) {
        /**
         * Calculate the ray's price at any bar index (extrapolates beyond endpoints).
         */
        public double priceAt(int bar) {
            return startPrice + slope * (bar - startBar);
        }

        /**
         * Get the ray's age in bars from start.
         */
        public int length() {
            return endBar - startBar;
        }
    }

    /**
     * Collection of rays from an anchor point (ATH or ATL).
     */
    public record RaySet(
        List<Ray> rays,       // List of rays, ray[0] is from anchor
        int anchorBar,        // Bar index of ATH/ATL
        double anchorPrice,   // Price at ATH/ATL
        boolean isResistance  // true = resistance rays (from ATH), false = support rays (from ATL)
    ) {
        /**
         * Get ray by number (1-indexed, ray 1 = from anchor).
         */
        public Ray getRay(int rayNum) {
            if (rayNum < 1 || rayNum > rays.size()) {
                return null;
            }
            return rays.get(rayNum - 1);
        }

        /**
         * Get total number of rays.
         */
        public int count() {
            return rays.size();
        }
    }

    /**
     * Calculate resistance rays from ATH (rotating clockwise).
     *
     * @param candles Price data
     * @param lookback Number of bars to look back for ATH
     * @param skip Number of recent bars to skip (allows price to be above rays)
     * @return RaySet containing all resistance rays
     */
    public static RaySet calculateResistanceRays(List<Candle> candles, int lookback, int skip) {
        if (candles == null || candles.isEmpty()) {
            return new RaySet(List.of(), -1, Double.NaN, true);
        }

        int size = candles.size();
        int effectiveEnd = Math.max(0, size - skip);
        // lookback=0 means no limit (use all data)
        int effectiveStart = (lookback <= 0) ? 0 : Math.max(0, effectiveEnd - lookback);

        if (effectiveEnd <= effectiveStart) {
            return new RaySet(List.of(), -1, Double.NaN, true);
        }

        // Find ATH in the lookback window (excluding skip zone)
        int athBar = effectiveStart;
        double athPrice = candles.get(effectiveStart).high();

        for (int i = effectiveStart + 1; i < effectiveEnd; i++) {
            double high = candles.get(i).high();
            if (high > athPrice) {
                athPrice = high;
                athBar = i;
            }
        }

        // Build rays by rotating clockwise from ATH
        List<Ray> rays = new ArrayList<>();
        int currentBar = athBar;
        double currentPrice = athPrice;

        while (currentBar < effectiveEnd - 1) {
            // Find next peak by rotating clockwise (find flattest valid slope)
            int nextBar = -1;
            double nextPrice = Double.NaN;
            double bestSlope = Double.NEGATIVE_INFINITY; // Looking for flattest (least negative)

            for (int i = currentBar + 1; i < effectiveEnd; i++) {
                double candidatePrice = candles.get(i).high();
                double candidateSlope = (candidatePrice - currentPrice) / (i - currentBar);

                // Check if this ray is valid (no price data crosses above it)
                boolean valid = true;
                for (int j = currentBar + 1; j < i; j++) {
                    double rayPrice = currentPrice + candidateSlope * (j - currentBar);
                    if (candles.get(j).high() > rayPrice + 0.0001) { // Small tolerance
                        valid = false;
                        break;
                    }
                }

                if (valid && candidateSlope > bestSlope) {
                    bestSlope = candidateSlope;
                    nextBar = i;
                    nextPrice = candidatePrice;
                }
            }

            if (nextBar < 0) {
                // No more valid peaks found, create final ray to end of data
                break;
            }

            // Create ray from current to next peak
            rays.add(new Ray(currentBar, currentPrice, nextBar, nextPrice, bestSlope));

            // Move to next peak
            currentBar = nextBar;
            currentPrice = nextPrice;
        }

        return new RaySet(rays, athBar, athPrice, true);
    }

    /**
     * Calculate support rays from ATL (rotating counter-clockwise).
     *
     * @param candles Price data
     * @param lookback Number of bars to look back for ATL
     * @param skip Number of recent bars to skip (allows price to be below rays)
     * @return RaySet containing all support rays
     */
    public static RaySet calculateSupportRays(List<Candle> candles, int lookback, int skip) {
        if (candles == null || candles.isEmpty()) {
            return new RaySet(List.of(), -1, Double.NaN, false);
        }

        int size = candles.size();
        int effectiveEnd = Math.max(0, size - skip);
        // lookback=0 means no limit (use all data)
        int effectiveStart = (lookback <= 0) ? 0 : Math.max(0, effectiveEnd - lookback);

        if (effectiveEnd <= effectiveStart) {
            return new RaySet(List.of(), -1, Double.NaN, false);
        }

        // Find ATL in the lookback window (excluding skip zone)
        int atlBar = effectiveStart;
        double atlPrice = candles.get(effectiveStart).low();

        for (int i = effectiveStart + 1; i < effectiveEnd; i++) {
            double low = candles.get(i).low();
            if (low < atlPrice) {
                atlPrice = low;
                atlBar = i;
            }
        }

        // Build rays by rotating counter-clockwise from ATL
        List<Ray> rays = new ArrayList<>();
        int currentBar = atlBar;
        double currentPrice = atlPrice;

        while (currentBar < effectiveEnd - 1) {
            // Find next trough by rotating counter-clockwise (find flattest valid slope)
            int nextBar = -1;
            double nextPrice = Double.NaN;
            double bestSlope = Double.POSITIVE_INFINITY; // Looking for flattest (least positive)

            for (int i = currentBar + 1; i < effectiveEnd; i++) {
                double candidatePrice = candles.get(i).low();
                double candidateSlope = (candidatePrice - currentPrice) / (i - currentBar);

                // Check if this ray is valid (no price data crosses below it)
                boolean valid = true;
                for (int j = currentBar + 1; j < i; j++) {
                    double rayPrice = currentPrice + candidateSlope * (j - currentBar);
                    if (candles.get(j).low() < rayPrice - 0.0001) { // Small tolerance
                        valid = false;
                        break;
                    }
                }

                if (valid && candidateSlope < bestSlope) {
                    bestSlope = candidateSlope;
                    nextBar = i;
                    nextPrice = candidatePrice;
                }
            }

            if (nextBar < 0) {
                // No more valid troughs found
                break;
            }

            // Create ray from current to next trough
            rays.add(new Ray(currentBar, currentPrice, nextBar, nextPrice, bestSlope));

            // Move to next trough
            currentBar = nextBar;
            currentPrice = nextPrice;
        }

        return new RaySet(rays, atlBar, atlPrice, false);
    }

    // ========== Helper methods for DSL evaluation ==========

    /**
     * Check if price is above/below a specific ray at a given bar.
     * For resistance: returns true if price is ABOVE the ray (broken)
     * For support: returns true if price is BELOW the ray (broken)
     */
    public static boolean isRayBroken(RaySet raySet, int rayNum, List<Candle> candles, int barIndex) {
        if (raySet == null || raySet.rays().isEmpty() || barIndex < 0 || barIndex >= candles.size()) {
            return false;
        }

        Ray ray = raySet.getRay(rayNum);
        if (ray == null) {
            return false;
        }

        double rayPrice = ray.priceAt(barIndex);
        double price = candles.get(barIndex).close();

        if (raySet.isResistance()) {
            return price > rayPrice;
        } else {
            return price < rayPrice;
        }
    }

    /**
     * Check if price crossed through a ray at a specific bar (event detection).
     * For resistance: returns true if price crossed ABOVE the ray this bar
     * For support: returns true if price crossed BELOW the ray this bar
     */
    public static boolean didRayCross(RaySet raySet, int rayNum, List<Candle> candles, int barIndex) {
        if (raySet == null || raySet.rays().isEmpty() || barIndex < 1 || barIndex >= candles.size()) {
            return false;
        }

        Ray ray = raySet.getRay(rayNum);
        if (ray == null) {
            return false;
        }

        double rayPriceCurrent = ray.priceAt(barIndex);
        double rayPricePrev = ray.priceAt(barIndex - 1);
        double priceCurrent = candles.get(barIndex).close();
        double pricePrev = candles.get(barIndex - 1).close();

        if (raySet.isResistance()) {
            // Cross above: was below, now above
            return pricePrev <= rayPricePrev && priceCurrent > rayPriceCurrent;
        } else {
            // Cross below: was above, now below
            return pricePrev >= rayPricePrev && priceCurrent < rayPriceCurrent;
        }
    }

    /**
     * Get distance from price to a specific ray as percentage.
     * Positive = above ray, Negative = below ray
     */
    public static double getRayDistance(RaySet raySet, int rayNum, List<Candle> candles, int barIndex) {
        if (raySet == null || raySet.rays().isEmpty() || barIndex < 0 || barIndex >= candles.size()) {
            return Double.NaN;
        }

        Ray ray = raySet.getRay(rayNum);
        if (ray == null) {
            return Double.NaN;
        }

        double rayPrice = ray.priceAt(barIndex);
        double price = candles.get(barIndex).close();

        if (rayPrice == 0) {
            return Double.NaN;
        }

        return ((price - rayPrice) / rayPrice) * 100.0;
    }

    /**
     * Count how many rays are currently broken.
     * For resistance: counts rays where price is ABOVE
     * For support: counts rays where price is BELOW
     */
    public static int countBrokenRays(RaySet raySet, List<Candle> candles, int barIndex) {
        if (raySet == null || raySet.rays().isEmpty() || barIndex < 0 || barIndex >= candles.size()) {
            return 0;
        }

        int count = 0;
        for (int i = 1; i <= raySet.count(); i++) {
            if (isRayBroken(raySet, i, candles, barIndex)) {
                count++;
            }
        }
        return count;
    }

    // ========== Optimized Historic Ray Computation ==========

    /**
     * A snapshot of rays at a specific bar, used for historic visualization.
     */
    public record HistoricRaySnapshot(
        int barIndex,      // The bar where this snapshot was taken
        RaySet raySet      // The rays at that point
    ) {}

    /**
     * Compute historic resistance rays efficiently, only returning snapshots when the anchor (ATH) changes.
     * This is much more efficient than computing rays for every bar.
     *
     * @param candles Full candle list
     * @param lookback Number of bars to look back for ATH (0 = unlimited)
     * @param skip Number of recent bars to skip
     * @param startBar First bar to consider
     * @return List of snapshots, one for each time the ATH changed
     */
    public static List<HistoricRaySnapshot> computeHistoricResistanceRays(
            List<Candle> candles, int lookback, int skip, int startBar) {

        List<HistoricRaySnapshot> snapshots = new ArrayList<>();
        if (candles == null || candles.isEmpty()) {
            return snapshots;
        }

        int prevAnchorBar = -1;
        double prevAnchorPrice = Double.NaN;

        // Start from at least startBar, but need minimum 10 bars for meaningful computation
        int minStart = Math.max(startBar, 10);

        for (int barIndex = minStart; barIndex < candles.size(); barIndex++) {
            // Find ATH for this bar's view
            int effectiveEnd = Math.max(0, barIndex + 1 - skip);
            int effectiveStart = (lookback <= 0) ? 0 : Math.max(0, effectiveEnd - lookback);

            if (effectiveEnd <= effectiveStart) continue;

            // Find ATH in window
            int athBar = effectiveStart;
            double athPrice = candles.get(effectiveStart).high();
            for (int i = effectiveStart + 1; i < effectiveEnd; i++) {
                double high = candles.get(i).high();
                if (high > athPrice) {
                    athPrice = high;
                    athBar = i;
                }
            }

            // Only compute rays if anchor changed
            if (athBar != prevAnchorBar || athPrice != prevAnchorPrice) {
                List<Candle> availableCandles = candles.subList(0, barIndex + 1);
                RaySet raySet = calculateResistanceRays(availableCandles, lookback, skip);
                if (raySet != null && raySet.count() > 0) {
                    snapshots.add(new HistoricRaySnapshot(barIndex, raySet));
                }
                prevAnchorBar = athBar;
                prevAnchorPrice = athPrice;
            }
        }

        return snapshots;
    }

    /**
     * Compute historic support rays efficiently, only returning snapshots when the anchor (ATL) changes.
     * This is much more efficient than computing rays for every bar.
     *
     * @param candles Full candle list
     * @param lookback Number of bars to look back for ATL (0 = unlimited)
     * @param skip Number of recent bars to skip
     * @param startBar First bar to consider
     * @return List of snapshots, one for each time the ATL changed
     */
    public static List<HistoricRaySnapshot> computeHistoricSupportRays(
            List<Candle> candles, int lookback, int skip, int startBar) {

        List<HistoricRaySnapshot> snapshots = new ArrayList<>();
        if (candles == null || candles.isEmpty()) {
            return snapshots;
        }

        int prevAnchorBar = -1;
        double prevAnchorPrice = Double.NaN;

        // Start from at least startBar, but need minimum 10 bars for meaningful computation
        int minStart = Math.max(startBar, 10);

        for (int barIndex = minStart; barIndex < candles.size(); barIndex++) {
            // Find ATL for this bar's view
            int effectiveEnd = Math.max(0, barIndex + 1 - skip);
            int effectiveStart = (lookback <= 0) ? 0 : Math.max(0, effectiveEnd - lookback);

            if (effectiveEnd <= effectiveStart) continue;

            // Find ATL in window
            int atlBar = effectiveStart;
            double atlPrice = candles.get(effectiveStart).low();
            for (int i = effectiveStart + 1; i < effectiveEnd; i++) {
                double low = candles.get(i).low();
                if (low < atlPrice) {
                    atlPrice = low;
                    atlBar = i;
                }
            }

            // Only compute rays if anchor changed
            if (atlBar != prevAnchorBar || atlPrice != prevAnchorPrice) {
                List<Candle> availableCandles = candles.subList(0, barIndex + 1);
                RaySet raySet = calculateSupportRays(availableCandles, lookback, skip);
                if (raySet != null && raySet.count() > 0) {
                    snapshots.add(new HistoricRaySnapshot(barIndex, raySet));
                }
                prevAnchorBar = atlBar;
                prevAnchorPrice = atlPrice;
            }
        }

        return snapshots;
    }
}
