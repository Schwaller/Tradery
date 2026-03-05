package com.tradery.dataservice.data;

import com.tradery.dataservice.data.sqlite.DataStoreType;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.CoverageDao;
import com.tradery.dataservice.profile.VolumeProfileComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for volume profile gap detection and computation.
 *
 * Two-phase approach:
 * 1. IMMEDIATE: Compute profiles from whatever aggTrades are already cached.
 *    This serves the current request with best-available data.
 * 2. BACKGROUND: Download missing aggTrades, then compute profiles for newly
 *    available ranges. This ensures the NEXT request gets complete data.
 *
 * Coverage is only stamped AFTER profile computation completes (compute() is
 * synchronous — all batch saves finish before it returns).
 */
public class ProfileStore {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileStore.class);

    private final SqliteDataStore dataStore;
    private final AggTradesStore aggTradesStore;
    private final VolumeProfileComputer computer;
    private volatile BackfillCompletionCallback completionCallback;

    private final ExecutorService backgroundDownloader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "profile-aggtrades-downloader");
        t.setDaemon(true);
        return t;
    });

    public ProfileStore(SqliteDataStore dataStore, AggTradesStore aggTradesStore, VolumeProfileComputer computer) {
        this.dataStore = dataStore;
        this.aggTradesStore = aggTradesStore;
        this.computer = computer;
    }

    public void setCompletionCallback(BackfillCompletionCallback callback) {
        this.completionCallback = callback;
    }

    /**
     * Ensure profile coverage for a symbol/marketType/range.
     * Phase 1: compute from available aggTrades (synchronous).
     * Phase 2: download missing aggTrades then re-compute (background).
     */
    public void ensureCoverage(String symbol, String marketType, long start, long end) throws Exception {
        var profileCoverage = dataStore.forSymbol(symbol).coverageFor(DataStoreType.VOLUME_PROFILES, marketType);
        var gaps = profileCoverage.findGaps("volume_profiles", marketType, start, end);

        if (gaps.isEmpty()) return;

        LOG.info("Profile coverage gaps for {} [{}]: {} gaps in [{} - {}]",
            symbol, marketType, gaps.size(), start, end);

        // Phase 1: Compute profiles from currently available aggTrades (synchronous)
        computeFromAvailableAggTrades(symbol, marketType, gaps, profileCoverage);

        // Phase 2: Download missing aggTrades in background, then compute profiles for new data
        backgroundDownloader.submit(() -> {
            try {
                aggTradesStore.ensureCached(symbol, start, end, marketType);

                // After download completes, re-check for profile gaps and compute.
                var postDownloadGaps = profileCoverage.findGaps("volume_profiles", marketType, start, end);
                if (!postDownloadGaps.isEmpty()) {
                    LOG.info("Post-download: {} profile gaps remain for {} [{}], computing from new aggTrades",
                        postDownloadGaps.size(), symbol, marketType);
                    computeFromAvailableAggTrades(symbol, marketType, postDownloadGaps, profileCoverage);
                }

                // Notify callback so pages can refresh
                BackfillCompletionCallback cb = completionCallback;
                if (cb != null) {
                    cb.onProfileBackfillComplete(symbol, marketType, start, end);
                }
            } catch (Exception e) {
                LOG.warn("Background aggTrades download/profile-compute failed for {} [{}]: {}",
                    symbol, marketType, e.getMessage());
            }
        });
    }

    /**
     * Compute profiles for the intersection of profile gaps and available aggTrades coverage.
     * Only stamps profile coverage for ranges where profiles were actually computed.
     */
    void computeFromAvailableAggTrades(String symbol, String marketType,
            List<long[]> gaps, CoverageDao profileCoverage) throws Exception {
        String aggTradesSubKey = "spot".equals(marketType) ? "spot" : "default";
        String aggTradesQualifier = "binance_" + marketType;
        var aggTradesCoverage = dataStore.forSymbol(symbol).coverageFor(DataStoreType.AGG_TRADES, aggTradesQualifier);
        var aggRanges = aggTradesCoverage.getCoverageRanges("agg_trades", aggTradesSubKey);

        for (long[] gap : gaps) {
            for (var aggRange : aggRanges) {
                long intersectStart = Math.max(gap[0], aggRange.rangeStart());
                long intersectEnd = Math.min(gap[1], aggRange.rangeEnd());

                if (intersectStart < intersectEnd) {
                    LOG.info("Computing volume profiles for {} [{}] [{} - {}] (aggTrades available)",
                        symbol, marketType, intersectStart, intersectEnd);
                    computer.compute(symbol, marketType, intersectStart, intersectEnd);
                    profileCoverage.addCoverage("volume_profiles", marketType, intersectStart, intersectEnd, true);
                }
            }
        }
    }
}
