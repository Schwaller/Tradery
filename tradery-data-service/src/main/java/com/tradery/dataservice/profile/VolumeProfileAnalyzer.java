package com.tradery.dataservice.profile;

import com.tradery.dataservice.data.sqlite.dao.VolumeProfileDao.ProfileRow;

import java.io.IOException;
import java.util.*;

/**
 * Query-time derivation logic for volume profiles.
 * Computes POC/VAH/VAL, binned histograms, and POC time series from stored profile data.
 */
public class VolumeProfileAnalyzer {

    /**
     * Metrics derived from a composite tick map.
     */
    public record ProfileMetrics(
        double poc,       // Point of Control (price with highest volume)
        double vah,       // Value Area High
        double val,       // Value Area Low
        double delta,     // Total buy volume - total sell volume
        double totalVolume
    ) {}

    /**
     * Binned histogram representation.
     */
    public record BinnedProfile(
        double poc,
        double vah,
        double val,
        double delta,
        double[] priceLevels,
        double[] buyVolumes,
        double[] sellVolumes
    ) {}

    /**
     * A single point in a POC time series.
     */
    public record PocPoint(
        long timestamp,
        double poc,
        double volume
    ) {}

    /**
     * Compute POC, VAH, VAL from a tick map.
     *
     * @param tickMap Map of price ticks to [buyVolume, sellVolume]
     * @param tickSize The tick size used to compute prices
     * @param valueAreaPct The percentage of volume that defines the value area (typically 70)
     */
    public ProfileMetrics computeMetrics(Map<Integer, double[]> tickMap, double tickSize, double valueAreaPct) {
        if (tickMap.isEmpty()) {
            return new ProfileMetrics(0, 0, 0, 0, 0);
        }

        // Find POC (tick with highest total volume)
        int pocTick = 0;
        double pocVolume = 0;
        double totalVolume = 0;
        double totalBuy = 0;
        double totalSell = 0;

        for (var entry : tickMap.entrySet()) {
            double vol = entry.getValue()[0] + entry.getValue()[1];
            totalBuy += entry.getValue()[0];
            totalSell += entry.getValue()[1];
            totalVolume += vol;
            if (vol > pocVolume) {
                pocVolume = vol;
                pocTick = entry.getKey();
            }
        }

        double poc = pocTick * tickSize;

        // Compute Value Area: expand outward from POC until valueAreaPct of volume is captured
        double targetVolume = totalVolume * (valueAreaPct / 100.0);
        double capturedVolume = pocVolume;

        // Get sorted ticks as array for bidirectional expansion
        List<Integer> sortedTicks = new ArrayList<>(tickMap.keySet());
        Collections.sort(sortedTicks);

        int pocIndex = Collections.binarySearch(sortedTicks, pocTick);
        int lowIdx = pocIndex;
        int highIdx = pocIndex;

        while (capturedVolume < targetVolume && (lowIdx > 0 || highIdx < sortedTicks.size() - 1)) {
            double expandLow = 0;
            double expandHigh = 0;

            if (lowIdx > 0) {
                int nextLow = sortedTicks.get(lowIdx - 1);
                double[] v = tickMap.get(nextLow);
                expandLow = v[0] + v[1];
            }
            if (highIdx < sortedTicks.size() - 1) {
                int nextHigh = sortedTicks.get(highIdx + 1);
                double[] v = tickMap.get(nextHigh);
                expandHigh = v[0] + v[1];
            }

            // Expand toward higher volume
            if (expandLow >= expandHigh && lowIdx > 0) {
                lowIdx--;
                capturedVolume += expandLow;
            } else if (highIdx < sortedTicks.size() - 1) {
                highIdx++;
                capturedVolume += expandHigh;
            } else if (lowIdx > 0) {
                lowIdx--;
                capturedVolume += expandLow;
            } else {
                break;
            }
        }

        double val = sortedTicks.get(lowIdx) * tickSize;
        double vah = sortedTicks.get(highIdx) * tickSize;
        double delta = totalBuy - totalSell;

        return new ProfileMetrics(poc, vah, val, delta, totalVolume);
    }

    /**
     * Create a binned profile with N equal-width bins across the price range.
     */
    public BinnedProfile toBinnedByCount(Map<Integer, double[]> tickMap, double tickSize, int binCount) {
        if (tickMap.isEmpty() || binCount <= 0) {
            return new BinnedProfile(0, 0, 0, 0, new double[0], new double[0], new double[0]);
        }

        int minTick = Collections.min(tickMap.keySet());
        int maxTick = Collections.max(tickMap.keySet());
        double range = (maxTick - minTick + 1);
        double binWidth = Math.max(range / binCount, 1);

        double[] priceLevels = new double[binCount];
        double[] buyVolumes = new double[binCount];
        double[] sellVolumes = new double[binCount];

        for (int i = 0; i < binCount; i++) {
            priceLevels[i] = (minTick + (i + 0.5) * binWidth) * tickSize;
        }

        for (var entry : tickMap.entrySet()) {
            int idx = (int) ((entry.getKey() - minTick) / binWidth);
            idx = Math.min(idx, binCount - 1);
            buyVolumes[idx] += entry.getValue()[0];
            sellVolumes[idx] += entry.getValue()[1];
        }

        ProfileMetrics metrics = computeMetrics(tickMap, tickSize, 70);
        return new BinnedProfile(metrics.poc, metrics.vah, metrics.val, metrics.delta,
            priceLevels, buyVolumes, sellVolumes);
    }

    /**
     * Create a binned profile with fixed-width price bins.
     */
    public BinnedProfile toBinnedByPrice(Map<Integer, double[]> tickMap, double tickSize, double binWidth) {
        if (tickMap.isEmpty() || binWidth <= 0) {
            return new BinnedProfile(0, 0, 0, 0, new double[0], new double[0], new double[0]);
        }

        int minTick = Collections.min(tickMap.keySet());
        int maxTick = Collections.max(tickMap.keySet());
        double minPrice = minTick * tickSize;
        double maxPrice = maxTick * tickSize;

        int binCount = (int) Math.ceil((maxPrice - minPrice) / binWidth) + 1;
        binCount = Math.max(1, Math.min(binCount, 10000)); // Safety cap

        double[] priceLevels = new double[binCount];
        double[] buyVolumes = new double[binCount];
        double[] sellVolumes = new double[binCount];

        double binStart = Math.floor(minPrice / binWidth) * binWidth;
        for (int i = 0; i < binCount; i++) {
            priceLevels[i] = binStart + (i + 0.5) * binWidth;
        }

        for (var entry : tickMap.entrySet()) {
            double price = entry.getKey() * tickSize;
            int idx = (int) ((price - binStart) / binWidth);
            idx = Math.min(idx, binCount - 1);
            idx = Math.max(idx, 0);
            buyVolumes[idx] += entry.getValue()[0];
            sellVolumes[idx] += entry.getValue()[1];
        }

        ProfileMetrics metrics = computeMetrics(tickMap, tickSize, 70);
        return new BinnedProfile(metrics.poc, metrics.vah, metrics.val, metrics.delta,
            priceLevels, buyVolumes, sellVolumes);
    }

    /**
     * Compute POC time series from a list of profile rows.
     */
    public List<PocPoint> pocSeries(List<ProfileRow> profiles, double tickSize) throws IOException {
        List<PocPoint> points = new ArrayList<>();

        for (ProfileRow row : profiles) {
            Map<Integer, double[]> tickMap = ProfileSerializer.deserialize(row.profileData());

            // Find POC tick
            int pocTick = 0;
            double pocVolume = 0;
            double totalVolume = 0;

            for (var entry : tickMap.entrySet()) {
                double vol = entry.getValue()[0] + entry.getValue()[1];
                totalVolume += vol;
                if (vol > pocVolume) {
                    pocVolume = vol;
                    pocTick = entry.getKey();
                }
            }

            if (totalVolume > 0) {
                points.add(new PocPoint(row.windowStart(), pocTick * tickSize, totalVolume));
            }
        }

        return points;
    }

    /**
     * Compute POC series from composite (rolling N-day) profiles.
     */
    public List<PocPoint> compositePocSeries(List<ProfileRow> profiles, double tickSize, int compositeDays)
            throws IOException {
        long compositeWindowMs = compositeDays * 86_400_000L;
        List<PocPoint> points = new ArrayList<>();

        for (int i = 0; i < profiles.size(); i++) {
            ProfileRow current = profiles.get(i);
            long cutoff = current.windowStart() - compositeWindowMs;

            // Merge profiles within composite window
            Map<Integer, double[]> composite = new TreeMap<>();
            for (int j = i; j >= 0; j--) {
                ProfileRow p = profiles.get(j);
                if (p.windowStart() < cutoff) break;
                ProfileSerializer.mergeInto(composite, ProfileSerializer.deserialize(p.profileData()));
            }

            if (!composite.isEmpty()) {
                int pocTick = 0;
                double pocVolume = 0;
                double totalVolume = 0;

                for (var entry : composite.entrySet()) {
                    double vol = entry.getValue()[0] + entry.getValue()[1];
                    totalVolume += vol;
                    if (vol > pocVolume) {
                        pocVolume = vol;
                        pocTick = entry.getKey();
                    }
                }

                points.add(new PocPoint(current.windowStart(), pocTick * tickSize, totalVolume));
            }
        }

        return points;
    }
}
