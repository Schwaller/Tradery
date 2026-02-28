package com.tradery.dataservice.profile;

import com.tradery.core.model.AggTrade;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.VolumeProfileDao;
import com.tradery.dataservice.data.sqlite.dao.VolumeProfileDao.ProfileRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Computes volume profiles from aggTrades data.
 * Reads raw trades, buckets them into 10-second canonical profiles at tick-level resolution,
 * then aggregates into a time pyramid (10s → 1m → 5m → 30m → 1h → 4h → 1d).
 */
public class VolumeProfileComputer {

    private static final Logger log = LoggerFactory.getLogger(VolumeProfileComputer.class);

    private static final long TEN_SECONDS_MS = 10_000L;
    private static final int CHUNK_SIZE = 10_000;
    private static final int FLUSH_BATCH_SIZE = 1000;

    /** Pyramid levels: child → parent, with multiplier */
    private static final List<PyramidLevel> PYRAMID = List.of(
        new PyramidLevel("10s", "1m",  6,  60_000L),
        new PyramidLevel("1m",  "5m",  5,  300_000L),
        new PyramidLevel("5m",  "30m", 6,  1_800_000L),
        new PyramidLevel("30m", "1h",  2,  3_600_000L),
        new PyramidLevel("1h",  "4h",  4,  14_400_000L),
        new PyramidLevel("4h",  "1d",  6,  86_400_000L)
    );

    private final SqliteDataStore dataStore;
    private final TickSizeResolver tickSizeResolver;

    public VolumeProfileComputer(SqliteDataStore dataStore, TickSizeResolver tickSizeResolver) {
        this.dataStore = dataStore;
        this.tickSizeResolver = tickSizeResolver;
    }

    /**
     * Compute volume profiles for a symbol and time range.
     * Produces 10s base profiles from aggTrades, then aggregates up the pyramid.
     */
    public void compute(String symbol, long startTime, long endTime) throws IOException {
        double tickSize = tickSizeResolver.getTickSize(symbol);
        if (tickSize <= 0) {
            log.warn("Invalid tick size {} for {}, skipping profile computation", tickSize, symbol);
            return;
        }

        log.info("Computing volume profiles for {} [{} - {}] tickSize={}", symbol, startTime, endTime, tickSize);

        // Phase 1: Stream aggTrades and produce 10s profiles
        long baseProfileCount = computeBaseProfiles(symbol, startTime, endTime, tickSize);
        log.info("Produced {} base (10s) profiles for {}", baseProfileCount, symbol);

        // Phase 2: Aggregate up the pyramid
        for (PyramidLevel level : PYRAMID) {
            long parentStart = alignToWindow(startTime, level.parentIntervalMs);
            long parentEnd = endTime;
            int count = aggregateUp(symbol, level.childTimeframe, level.parentTimeframe,
                level.parentIntervalMs, parentStart, parentEnd, tickSize);
            log.info("Aggregated {} {} profiles from {} for {}", count, level.parentTimeframe, level.childTimeframe, symbol);
        }

        log.info("Volume profile computation complete for {} [{} - {}]", symbol, startTime, endTime);
    }

    /**
     * Compute base 10-second profiles from aggTrades.
     */
    private long computeBaseProfiles(String symbol, long startTime, long endTime, double tickSize) throws IOException {
        // Accumulator for current 10s window
        TreeMap<Integer, double[]> currentWindow = new TreeMap<>();
        long[] currentWindowStart = {alignToWindow(startTime, TEN_SECONDS_MS)};
        List<ProfileRow> pendingRows = new ArrayList<>();
        long[] totalRows = {0};

        dataStore.streamAggTrades(symbol, startTime, endTime, CHUNK_SIZE, chunk -> {
            for (AggTrade trade : chunk) {
                long windowStart = alignToWindow(trade.timestamp(), TEN_SECONDS_MS);

                // If we've crossed into a new window, flush the current one
                while (currentWindowStart[0] < windowStart) {
                    if (!currentWindow.isEmpty()) {
                        try {
                            pendingRows.add(createProfileRow("10s", currentWindowStart[0], tickSize, currentWindow));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to serialize profile", e);
                        }
                        currentWindow.clear();
                    }
                    currentWindowStart[0] += TEN_SECONDS_MS;

                    // Batch flush
                    if (pendingRows.size() >= FLUSH_BATCH_SIZE) {
                        try {
                            dataStore.saveProfiles(symbol, pendingRows);
                            totalRows[0] += pendingRows.size();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to save profiles", e);
                        }
                        pendingRows.clear();
                    }
                }

                // Bucket the trade
                int priceTick = (int) Math.round(trade.price() / tickSize);
                double volume = trade.price() * trade.quantity();
                // isBuyerMaker == true means the buyer's order was the maker (resting),
                // so the taker was a seller → sell volume
                double[] vols = currentWindow.computeIfAbsent(priceTick, k -> new double[2]);
                if (trade.isBuyerMaker()) {
                    vols[1] += volume; // sell
                } else {
                    vols[0] += volume; // buy
                }
            }
        });

        // Flush remaining window
        if (!currentWindow.isEmpty()) {
            try {
                pendingRows.add(createProfileRow("10s", currentWindowStart[0], tickSize, currentWindow));
            } catch (IOException e) {
                throw new IOException("Failed to serialize final profile", e);
            }
        }

        // Save remaining batch
        if (!pendingRows.isEmpty()) {
            dataStore.saveProfiles(symbol, pendingRows);
            totalRows[0] += pendingRows.size();
        }

        return totalRows[0];
    }

    /**
     * Aggregate child-level profiles into parent-level profiles.
     * E.g., merge 6 × 10s profiles into each 1m profile.
     */
    private int aggregateUp(String symbol, String childTimeframe, String parentTimeframe,
                            long parentIntervalMs, long startTime, long endTime, double tickSize) throws IOException {
        List<ProfileRow> parentRows = new ArrayList<>();
        long windowStart = alignToWindow(startTime, parentIntervalMs);

        while (windowStart <= endTime) {
            long windowEnd = windowStart + parentIntervalMs - 1;

            // Query all child profiles within this parent window
            List<ProfileRow> children = dataStore.getProfiles(symbol, childTimeframe, windowStart, windowEnd);

            if (!children.isEmpty()) {
                // Merge child tick maps
                TreeMap<Integer, double[]> merged = new TreeMap<>();
                for (ProfileRow child : children) {
                    Map<Integer, double[]> childMap = ProfileSerializer.deserialize(child.profileData());
                    ProfileSerializer.mergeInto(merged, childMap);
                }

                parentRows.add(createProfileRow(parentTimeframe, windowStart, tickSize, merged));

                if (parentRows.size() >= FLUSH_BATCH_SIZE) {
                    dataStore.saveProfiles(symbol, parentRows);
                    parentRows.clear();
                }
            }

            windowStart += parentIntervalMs;
        }

        if (!parentRows.isEmpty()) {
            dataStore.saveProfiles(symbol, parentRows);
        }

        return parentRows.size();
    }

    private ProfileRow createProfileRow(String timeframe, long windowStart, double tickSize,
                                        Map<Integer, double[]> tickMap) throws IOException {
        double totalBuy = 0, totalSell = 0;
        for (double[] vols : tickMap.values()) {
            totalBuy += vols[0];
            totalSell += vols[1];
        }
        byte[] data = ProfileSerializer.serialize(tickMap);
        return new ProfileRow(timeframe, windowStart, tickSize, totalBuy, totalSell, tickMap.size(), data);
    }

    /**
     * Align a timestamp to a window boundary.
     */
    static long alignToWindow(long timestamp, long intervalMs) {
        return (timestamp / intervalMs) * intervalMs;
    }

    private record PyramidLevel(String childTimeframe, String parentTimeframe, int multiplier, long parentIntervalMs) {}
}
