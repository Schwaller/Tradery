package com.tradery.dataservice.api;

import com.tradery.core.model.SpectrumMode;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.dataservice.data.SpectrumStore;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.SpectrumDao;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * HTTP endpoints for trade size spectrum queries.
 *
 * GET /spectrum?symbol=BTCUSDT&from=...&to=...&timeframe=1h&mode=VOLUME&bucketMode=raw
 * POST /spectrum/backfill?symbol=BTCUSDT&from=...&to=...&bucketMode=raw
 */
public class SpectrumHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SpectrumHandler.class);

    private static final Map<String, Long> TIMEFRAME_MS = Map.of(
        "1m", 60_000L,
        "5m", 300_000L,
        "15m", 900_000L,
        "30m", 1_800_000L,
        "1h", 3_600_000L,
        "4h", 14_400_000L,
        "1d", 86_400_000L
    );

    private final SpectrumStore spectrumStore;
    private final SqliteDataStore dataStore;

    public SpectrumHandler(SpectrumStore spectrumStore, SqliteDataStore dataStore) {
        this.spectrumStore = spectrumStore;
        this.dataStore = dataStore;
    }

    /**
     * GET /spectrum?symbol=BTCUSDT&from=...&to=...&timeframe=1h&mode=VOLUME&bucketMode=raw
     *
     * Without timeframe: returns flat distribution (collapsed across time)
     * With timeframe: returns time-series of bucket arrays
     * bucketMode: "raw" (default) or "taker_order"
     */
    public void getSpectrum(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long from = ctx.queryParamAsClass("from", Long.class).getOrDefault(null);
            Long to = ctx.queryParamAsClass("to", Long.class).getOrDefault(null);
            String timeframe = ctx.queryParam("timeframe");
            String bucketMode = ctx.queryParamAsClass("bucketMode", String.class).getOrDefault("raw");

            if (symbol == null || from == null || to == null) {
                ctx.status(400).json(Map.of("error", "symbol, from, and to are required"));
                return;
            }

            // Ensure spectrum coverage (triggers backfill from aggTrades + background download)
            spectrumStore.getSpectrum(symbol, from, to, bucketMode);

            if (timeframe != null) {
                // Time-series response
                Long windowMs = TIMEFRAME_MS.get(timeframe);
                if (windowMs == null) {
                    ctx.status(400).json(Map.of("error", "Invalid timeframe: " + timeframe));
                    return;
                }

                List<SpectrumDao.AggregatedBucket> buckets = dataStore.getSpectrumAggregated(
                    symbol, from, to, windowMs, bucketMode);

                // Group by period
                Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
                for (SpectrumDao.AggregatedBucket b : buckets) {
                    grouped.computeIfAbsent(b.periodStart(), k -> new ArrayList<>()).add(Map.of(
                        "bucketIndex", b.bucketIndex(),
                        "label", SpectrumWindow.bucketLabel(b.bucketIndex()),
                        "tradeCount", b.tradeCount(),
                        "totalVolume", b.totalVolume(),
                        "buyVolume", b.buyVolume(),
                        "sellVolume", b.sellVolume(),
                        "delta", b.delta()
                    ));
                }

                List<Map<String, Object>> windows = new ArrayList<>();
                for (var entry : grouped.entrySet()) {
                    windows.add(Map.of(
                        "timestamp", entry.getKey(),
                        "buckets", entry.getValue()
                    ));
                }

                ctx.json(Map.of("windows", windows));
            } else {
                // Flat distribution response
                List<SpectrumDao.FlatBucket> flat = dataStore.getSpectrumFlat(symbol, from, to, bucketMode);

                List<Map<String, Object>> buckets = new ArrayList<>();
                for (SpectrumDao.FlatBucket b : flat) {
                    buckets.add(Map.of(
                        "bucketIndex", b.bucketIndex(),
                        "label", b.label(),
                        "tradeCount", b.tradeCount(),
                        "totalVolume", b.totalVolume(),
                        "buyVolume", b.buyVolume(),
                        "sellVolume", b.sellVolume(),
                        "delta", b.delta()
                    ));
                }

                ctx.json(Map.of("buckets", buckets));
            }
        } catch (Exception e) {
            LOG.error("Failed to get spectrum", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /spectrum/backfill?symbol=BTCUSDT&from=...&to=...&bucketMode=raw
     *
     * Explicitly backfill spectrum from existing aggTrades.
     */
    public void backfill(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long from = ctx.queryParamAsClass("from", Long.class).getOrDefault(null);
            Long to = ctx.queryParamAsClass("to", Long.class).getOrDefault(null);
            String bucketMode = ctx.queryParamAsClass("bucketMode", String.class).getOrDefault("raw");

            if (symbol == null || from == null || to == null) {
                ctx.status(400).json(Map.of("error", "symbol, from, and to are required"));
                return;
            }

            long startMs = System.currentTimeMillis();
            long rowsCreated = spectrumStore.backfill(symbol, from, to, bucketMode);
            long durationMs = System.currentTimeMillis() - startMs;

            ctx.json(Map.of(
                "rowsCreated", rowsCreated,
                "durationMs", durationMs
            ));
        } catch (Exception e) {
            LOG.error("Failed to backfill spectrum", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
