package com.tradery.core.indicators;

import com.tradery.core.model.Candle;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Computes Relative Volume (RVOL) by comparing current volume to historical volume
 * at the same time-of-day, with three comparison modes:
 * <ul>
 *   <li>ANY (0): compare to all days at same time</li>
 *   <li>DOW (1): compare to same day-of-week at same time</li>
 *   <li>DAYTYPE (2): compare to same day type (holiday/workday) at same time</li>
 * </ul>
 *
 * For intraday timeframes, candles are bucketed by their time-of-day slot.
 * For daily+ timeframes, the time component drops out.
 *
 * The optional smooth parameter averages volume over N consecutive bars before comparing.
 */
public class RvolComputer {

    public static final int MODE_ANY = 0;
    public static final int MODE_DOW = 1;
    public static final int MODE_DAYTYPE = 2;

    /**
     * Result of RVOL computation: parallel arrays for ratio and percentile.
     */
    public record RvolResult(double[] ratio, double[] percentile) {}

    /**
     * Compute RVOL for all bars in the candle list.
     *
     * @param candles         Backtest candles (the bars we compute RVOL for)
     * @param historicalCandles Optional extra candles before the backtest window (may be null)
     * @param resolution      Timeframe string (e.g., "1m", "5m", "1h", "4h", "1d")
     * @param lookbackWeeks   Number of weeks of history to use for the volume profile
     * @param mode            Comparison mode: 0=ANY, 1=DOW, 2=DAYTYPE
     * @param smooth          Number of consecutive bars to average for current volume (1=no smoothing)
     * @return RvolResult with ratio and percentile arrays
     */
    public static RvolResult compute(List<Candle> candles, List<Candle> historicalCandles,
                                     String resolution, int lookbackWeeks, int mode, int smooth) {
        int n = candles.size();
        double[] ratio = new double[n];
        double[] percentile = new double[n];
        Arrays.fill(ratio, Double.NaN);
        Arrays.fill(percentile, Double.NaN);

        if (n == 0) return new RvolResult(ratio, percentile);

        int resolutionMinutes = parseResolutionMinutes(resolution);
        boolean isDaily = resolutionMinutes >= 1440;
        long lookbackMs = lookbackWeeks * 7L * 24 * 3600 * 1000;

        // Build combined candle list: historical + backtest, sorted by timestamp
        List<Candle> allCandles = new ArrayList<>();
        if (historicalCandles != null) {
            allCandles.addAll(historicalCandles);
        }
        allCandles.addAll(candles);

        // Build bucketed index: key -> list of (timestamp, volume) sorted by timestamp
        Map<String, List<long[]>> buckets = buildBuckets(allCandles, resolutionMinutes, isDaily, mode);

        // Compute RVOL for each bar
        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);

            // Smooth current volume
            double smoothedVolume = smoothVolume(candles, i, smooth);
            if (Double.isNaN(smoothedVolume) || smoothedVolume == 0) continue;

            // Find matching historical volumes
            String key = bucketKey(c.timestamp(), resolutionMinutes, isDaily, mode);
            List<long[]> bucket = buckets.get(key);
            if (bucket == null) continue;

            long cutoff = c.timestamp() - lookbackMs;
            List<Double> historicalVolumes = collectVolumes(bucket, cutoff, c.timestamp());

            if (historicalVolumes.size() < 3) continue; // Need minimum samples

            // Sort for median/percentile
            Collections.sort(historicalVolumes);

            double median = median(historicalVolumes);
            if (median <= 0) continue;

            ratio[i] = smoothedVolume / median;
            percentile[i] = computePercentile(historicalVolumes, smoothedVolume);
        }

        return new RvolResult(ratio, percentile);
    }

    /**
     * Compute RVOL percentile bands for chart rendering.
     * Returns percentile boundaries (P10, P25, P50, P75, P90) for each bar's time bucket.
     */
    public record RvolBands(double[] p10, double[] p25, double[] p50, double[] p75, double[] p90) {}

    public static RvolBands computeBands(List<Candle> candles, List<Candle> historicalCandles,
                                         String resolution, int lookbackWeeks, int mode) {
        int n = candles.size();
        double[] p10 = new double[n], p25 = new double[n], p50 = new double[n];
        double[] p75 = new double[n], p90 = new double[n];
        Arrays.fill(p10, Double.NaN);
        Arrays.fill(p25, Double.NaN);
        Arrays.fill(p50, Double.NaN);
        Arrays.fill(p75, Double.NaN);
        Arrays.fill(p90, Double.NaN);

        if (n == 0) return new RvolBands(p10, p25, p50, p75, p90);

        int resolutionMinutes = parseResolutionMinutes(resolution);
        boolean isDaily = resolutionMinutes >= 1440;
        long lookbackMs = lookbackWeeks * 7L * 24 * 3600 * 1000;

        List<Candle> allCandles = new ArrayList<>();
        if (historicalCandles != null) allCandles.addAll(historicalCandles);
        allCandles.addAll(candles);

        Map<String, List<long[]>> buckets = buildBuckets(allCandles, resolutionMinutes, isDaily, mode);

        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);
            String key = bucketKey(c.timestamp(), resolutionMinutes, isDaily, mode);
            List<long[]> bucket = buckets.get(key);
            if (bucket == null) continue;

            long cutoff = c.timestamp() - lookbackMs;
            List<Double> volumes = collectVolumes(bucket, cutoff, c.timestamp());
            if (volumes.size() < 5) continue;

            Collections.sort(volumes);
            p10[i] = percentileValue(volumes, 10);
            p25[i] = percentileValue(volumes, 25);
            p50[i] = percentileValue(volumes, 50);
            p75[i] = percentileValue(volumes, 75);
            p90[i] = percentileValue(volumes, 90);
        }

        return new RvolBands(p10, p25, p50, p75, p90);
    }

    // ========== Internal Helpers ==========

    private static Map<String, List<long[]>> buildBuckets(List<Candle> allCandles,
                                                           int resolutionMinutes, boolean isDaily, int mode) {
        Map<String, List<long[]>> buckets = new HashMap<>();

        for (Candle c : allCandles) {
            String key = bucketKey(c.timestamp(), resolutionMinutes, isDaily, mode);
            buckets.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new long[]{c.timestamp(), Double.doubleToLongBits(c.volume())});
        }

        // Sort each bucket by timestamp for binary search
        for (List<long[]> list : buckets.values()) {
            list.sort(Comparator.comparingLong(a -> a[0]));
        }

        return buckets;
    }

    private static String bucketKey(long timestamp, int resolutionMinutes, boolean isDaily, int mode) {
        ZonedDateTime dt = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);

        StringBuilder key = new StringBuilder();

        // Time bucket (skip for daily+)
        if (!isDaily) {
            int minuteOfDay = dt.getHour() * 60 + dt.getMinute();
            int bucket = minuteOfDay / resolutionMinutes;
            key.append(bucket);
        } else {
            key.append("d"); // all daily candles in same time bucket
        }

        // Mode filter
        switch (mode) {
            case MODE_DOW -> key.append(':').append(dt.getDayOfWeek().getValue());
            case MODE_DAYTYPE -> {
                boolean holiday = CalendarIndicators.isUSHoliday(timestamp);
                boolean weekend = dt.getDayOfWeek() == DayOfWeek.SATURDAY ||
                                  dt.getDayOfWeek() == DayOfWeek.SUNDAY;
                key.append(':').append(holiday || weekend ? "h" : "w");
            }
            // MODE_ANY: no additional key component
        }

        return key.toString();
    }

    /**
     * Collect volumes from a sorted bucket where timestamp is in [cutoff, beforeTimestamp).
     */
    private static List<Double> collectVolumes(List<long[]> bucket, long cutoff, long beforeTimestamp) {
        List<Double> volumes = new ArrayList<>();

        // Binary search for cutoff position
        int startIdx = lowerBound(bucket, cutoff);

        for (int i = startIdx; i < bucket.size(); i++) {
            long ts = bucket.get(i)[0];
            if (ts >= beforeTimestamp) break;
            if (ts < cutoff) continue;
            double vol = Double.longBitsToDouble(bucket.get(i)[1]);
            if (vol > 0) volumes.add(vol);
        }

        return volumes;
    }

    private static int lowerBound(List<long[]> list, long target) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid)[0] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static double smoothVolume(List<Candle> candles, int barIndex, int smooth) {
        if (smooth <= 1) return candles.get(barIndex).volume();

        int start = Math.max(0, barIndex - smooth + 1);
        double sum = 0;
        int count = 0;
        for (int i = start; i <= barIndex; i++) {
            double v = candles.get(i).volume();
            if (v > 0) {
                sum += v;
                count++;
            }
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    private static double median(List<Double> sorted) {
        int size = sorted.size();
        if (size == 0) return Double.NaN;
        if (size % 2 == 1) return sorted.get(size / 2);
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    private static double computePercentile(List<Double> sorted, double value) {
        int below = 0;
        for (double v : sorted) {
            if (v < value) below++;
        }
        return (below * 100.0) / sorted.size();
    }

    private static double percentileValue(List<Double> sorted, int p) {
        double idx = (p / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(idx);
        int upper = Math.min(lower + 1, sorted.size() - 1);
        double frac = idx - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    static int parseResolutionMinutes(String resolution) {
        if (resolution == null || resolution.isEmpty()) return 60;
        String num = resolution.substring(0, resolution.length() - 1);
        char unit = resolution.charAt(resolution.length() - 1);
        int value;
        try {
            value = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 60; // default
        }
        return switch (unit) {
            case 's' -> Math.max(1, value / 60); // sub-minute: treat as 1 min
            case 'm' -> value;
            case 'h' -> value * 60;
            case 'd' -> value * 1440;
            case 'w' -> value * 10080;
            case 'M' -> value * 43200;
            default -> 60;
        };
    }
}
