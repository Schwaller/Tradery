package com.tradery.dataservice.data;

import com.tradery.core.model.SpectrumWindow;
import com.tradery.dataservice.data.sqlite.DataStoreType;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.CoverageDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Store for trade size spectrum data.
 * Unlike FundingRateStore, spectrum is derived from aggTrades — not fetched from an external API.
 * Gap-filling means "compute from existing aggTrades", not "fetch from Binance".
 *
 * Supports two modes via coverage sub-keys:
 *   - "raw" (default): each aggTrade bucketed individually
 *   - "taker_order": taker orders reconstructed before bucketing
 */
public class SpectrumStore {

    private static final Logger log = LoggerFactory.getLogger(SpectrumStore.class);

    private final SqliteDataStore dataStore;
    private final AggTradesStore aggTradesStore;
    private final SpectrumAggregator aggregator;
    private volatile BackfillCompletionCallback completionCallback;

    // Background executor for triggering aggTrades downloads without blocking spectrum responses
    private final ExecutorService backgroundDownloader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spectrum-aggtrades-downloader");
        t.setDaemon(true);
        return t;
    });

    public SpectrumStore(SqliteDataStore dataStore, AggTradesStore aggTradesStore) {
        this.dataStore = dataStore;
        this.aggTradesStore = aggTradesStore;
        this.aggregator = new SpectrumAggregator();
    }

    public void setCompletionCallback(BackfillCompletionCallback callback) {
        this.completionCallback = callback;
    }

    /**
     * Get spectrum windows for a symbol, time range, and mode.
     *
     * Two-phase approach (same pattern as ProfileStore):
     * 1. IMMEDIATE: Backfill from whatever aggTrades are already cached.
     * 2. BACKGROUND: Download missing aggTrades, then backfill for newly available ranges.
     */
    public List<SpectrumWindow> getSpectrum(String symbol, long startTime, long endTime, String mode) throws IOException {
        String coverageKey = "spectrum_" + mode;

        List<long[]> gaps = dataStore.findGaps(symbol, "spectrum", coverageKey, startTime, endTime);

        if (!gaps.isEmpty()) {
            // Phase 1: Backfill from currently available aggTrades
            backfillFromAvailableAggTrades(symbol, gaps, mode);

            // Phase 2: Download missing aggTrades in background, then backfill for new data
            // TODO: spectrum doesn't yet support spot aggTrades in the UI — hardcode "perp" for now
            backgroundDownloader.submit(() -> {
                try {
                    aggTradesStore.ensureCached(symbol, startTime, endTime, "perp");

                    // After download, re-check for spectrum gaps and backfill from new aggTrades
                    var postDownloadGaps = dataStore.findGaps(symbol, "spectrum", coverageKey, startTime, endTime);
                    if (!postDownloadGaps.isEmpty()) {
                        log.info("Post-download: {} spectrum gaps remain for {} (mode={}), backfilling from new aggTrades",
                            postDownloadGaps.size(), symbol, mode);
                        backfillFromAvailableAggTrades(symbol, postDownloadGaps, mode);
                    }

                    // Notify callback so pages can refresh
                    BackfillCompletionCallback cb = completionCallback;
                    if (cb != null) {
                        cb.onSpectrumBackfillComplete(symbol, startTime, endTime);
                    }
                } catch (Exception e) {
                    log.warn("Background aggTrades download/spectrum-backfill failed for {}: {}", symbol, e.getMessage());
                }
            });
        }

        return dataStore.getSpectrum(symbol, startTime, endTime, mode);
    }

    /**
     * Backfill spectrum for the intersection of gaps and available aggTrades coverage.
     */
    private void backfillFromAvailableAggTrades(String symbol, List<long[]> gaps, String mode) throws IOException {
        var aggRanges = dataStore.getCoverageRanges(symbol, "agg_trades", "default");

        for (long[] gap : gaps) {
            boolean anyOverlap = false;
            for (var aggRange : aggRanges) {
                long intersectStart = Math.max(gap[0], aggRange.rangeStart());
                long intersectEnd = Math.min(gap[1], aggRange.rangeEnd());

                if (intersectStart < intersectEnd) {
                    anyOverlap = true;
                    log.info("Backfilling spectrum (mode={}) for {} [{} - {}] from existing aggTrades",
                        mode, symbol, intersectStart, intersectEnd);
                    aggregator.backfill(symbol, intersectStart, intersectEnd, dataStore, mode);
                }
            }

            if (!anyOverlap) {
                log.debug("No aggTrades for spectrum gap [{} - {}] for {}", gap[0], gap[1], symbol);
            }
        }
    }

    /** Backward-compatible (defaults to 'raw' mode). */
    public List<SpectrumWindow> getSpectrum(String symbol, long startTime, long endTime) throws IOException {
        return getSpectrum(symbol, startTime, endTime, "raw");
    }

    /**
     * Get spectrum from cache only — no backfill.
     */
    public List<SpectrumWindow> getSpectrumCacheOnly(String symbol, long startTime, long endTime, String mode) throws IOException {
        return dataStore.getSpectrum(symbol, startTime, endTime, mode);
    }

    /** Backward-compatible (defaults to 'raw' mode). */
    public List<SpectrumWindow> getSpectrumCacheOnly(String symbol, long startTime, long endTime) throws IOException {
        return getSpectrumCacheOnly(symbol, startTime, endTime, "raw");
    }

    /**
     * Explicitly backfill spectrum for a time range and mode.
     * @return number of spectrum rows created
     */
    public long backfill(String symbol, long startTime, long endTime, String mode) throws IOException {
        return aggregator.backfill(symbol, startTime, endTime, dataStore, mode);
    }

    /** Backward-compatible (defaults to 'raw' mode). */
    public long backfill(String symbol, long startTime, long endTime) throws IOException {
        return backfill(symbol, startTime, endTime, "raw");
    }
}
