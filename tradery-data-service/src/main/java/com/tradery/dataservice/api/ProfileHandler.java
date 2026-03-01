package com.tradery.dataservice.api;

import com.tradery.dataservice.data.sqlite.DataStoreType;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.VolumeProfileDao.ProfileRow;
import com.tradery.dataservice.profile.*;
import com.tradery.dataservice.profile.VolumeProfileAnalyzer.*;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * HTTP endpoints for volume profile queries.
 */
public class ProfileHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileHandler.class);

    private final SqliteDataStore dataStore;
    private final TickSizeResolver tickSizeResolver;
    private final VolumeProfileComputer computer;
    private final VolumeProfileAnalyzer analyzer;

    public ProfileHandler(SqliteDataStore dataStore, TickSizeResolver tickSizeResolver,
                          VolumeProfileComputer computer, VolumeProfileAnalyzer analyzer) {
        this.dataStore = dataStore;
        this.tickSizeResolver = tickSizeResolver;
        this.computer = computer;
        this.analyzer = analyzer;
    }

    /**
     * GET /profile?symbol=X&timeframe=Y&start=Z&end=W
     * Raw tick-level profile data.
     */
    public void getProfile(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            ensureCoverage(symbol, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, timeframe, start, end);
            double tickSize = tickSizeResolver.getTickSize(symbol);

            List<Map<String, Object>> result = new ArrayList<>();
            for (ProfileRow row : profiles) {
                Map<Integer, double[]> tickMap = ProfileSerializer.deserialize(row.profileData());

                Map<String, double[]> levels = new LinkedHashMap<>();
                for (var entry : tickMap.entrySet()) {
                    levels.put(String.valueOf(entry.getKey()), entry.getValue());
                }

                result.add(Map.of(
                    "windowStart", row.windowStart(),
                    "tickSize", row.tickSize(),
                    "totalBuyVolume", row.totalBuyVolume(),
                    "totalSellVolume", row.totalSellVolume(),
                    "levels", levels
                ));
            }

            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to get profile", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /profile/binned?symbol=X&timeframe=Y&start=Z&end=W&mode=BIN_COUNT|PRICE_DELTA&binParam=96&valueAreaPct=70
     * Derived histogram with POC/VAH/VAL.
     */
    public void getBinnedProfile(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            String mode = ctx.queryParamAsClass("mode", String.class).getOrDefault("BIN_COUNT");
            double binParam = ctx.queryParamAsClass("binParam", Double.class).getOrDefault(96.0);
            double valueAreaPct = ctx.queryParamAsClass("valueAreaPct", Double.class).getOrDefault(70.0);

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            ensureCoverage(symbol, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, timeframe, start, end);
            double tickSize = tickSizeResolver.getTickSize(symbol);

            // Merge all profiles into a composite tick map
            Map<Integer, double[]> composite = new TreeMap<>();
            for (ProfileRow row : profiles) {
                Map<Integer, double[]> tickMap = ProfileSerializer.deserialize(row.profileData());
                ProfileSerializer.mergeInto(composite, tickMap);
            }

            BinnedProfile binned;
            if ("PRICE_DELTA".equalsIgnoreCase(mode)) {
                binned = analyzer.toBinnedByPrice(composite, tickSize, binParam);
            } else {
                binned = analyzer.toBinnedByCount(composite, tickSize, (int) binParam);
            }

            ctx.json(Map.of(
                "poc", binned.poc(),
                "vah", binned.vah(),
                "val", binned.val(),
                "delta", binned.delta(),
                "bins", Map.of(
                    "priceLevels", binned.priceLevels(),
                    "buyVolumes", binned.buyVolumes(),
                    "sellVolumes", binned.sellVolumes()
                )
            ));
        } catch (Exception e) {
            LOG.error("Failed to get binned profile", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /profile/poc-series?symbol=X&timeframe=Y&start=Z&end=W&compositeDays=N
     * POC time series, optionally with rolling composite.
     */
    public void getPocSeries(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            Integer compositeDays = ctx.queryParamAsClass("compositeDays", Integer.class).getOrDefault(null);

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            ensureCoverage(symbol, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, timeframe, start, end);
            double tickSize = tickSizeResolver.getTickSize(symbol);

            List<PocPoint> series;
            if (compositeDays != null && compositeDays > 0) {
                series = analyzer.compositePocSeries(profiles, tickSize, compositeDays);
            } else {
                series = analyzer.pocSeries(profiles, tickSize);
            }

            ctx.json(series);
        } catch (Exception e) {
            LOG.error("Failed to get POC series", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /profile/daily-levels?symbol=X&start=Z&end=W
     * Returns POC/VAH/VAL for each day in the range, using "1d" timeframe profiles.
     */
    public void getDailyLevels(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);

            if (symbol == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, start, and end are required"));
                return;
            }

            ensureCoverage(symbol, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, "1d", start, end);
            double tickSize = tickSizeResolver.getTickSize(symbol);

            List<Map<String, Object>> result = new ArrayList<>();
            for (ProfileRow row : profiles) {
                Map<Integer, double[]> tickMap = ProfileSerializer.deserialize(row.profileData());
                ProfileMetrics metrics = analyzer.computeMetrics(tickMap, tickSize, 70.0);

                result.add(Map.of(
                    "dayStart", row.windowStart(),
                    "poc", metrics.poc(),
                    "vah", metrics.vah(),
                    "val", metrics.val(),
                    "delta", metrics.delta(),
                    "totalVolume", metrics.totalVolume()
                ));
            }

            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to get daily levels", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /profile/daily-binned?symbol=X&start=Z&end=W&binCount=24&valueAreaPct=70
     * Returns per-day binned histograms for the entire range in a single call.
     * Does ensureCoverage once for the full range, then fetches all "1d" profiles
     * and bins each one individually. Much more efficient than N calls to /profile/binned.
     */
    public void getDailyBinned(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            int binCount = ctx.queryParamAsClass("binCount", Integer.class).getOrDefault(24);
            double valueAreaPct = ctx.queryParamAsClass("valueAreaPct", Double.class).getOrDefault(70.0);

            if (symbol == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, start, and end are required"));
                return;
            }

            ensureCoverage(symbol, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, "1d", start, end);
            double tickSize = tickSizeResolver.getTickSize(symbol);

            List<Map<String, Object>> result = new ArrayList<>();
            for (ProfileRow row : profiles) {
                Map<Integer, double[]> tickMap = ProfileSerializer.deserialize(row.profileData());
                if (tickMap.isEmpty()) continue;

                BinnedProfile binned = analyzer.toBinnedByCount(tickMap, tickSize, binCount);
                ProfileMetrics metrics = analyzer.computeMetrics(tickMap, tickSize, valueAreaPct);

                result.add(Map.of(
                    "dayStart", row.windowStart(),
                    "poc", metrics.poc(),
                    "vah", metrics.vah(),
                    "val", metrics.val(),
                    "delta", metrics.delta(),
                    "totalVolume", metrics.totalVolume(),
                    "bins", Map.of(
                        "priceLevels", binned.priceLevels(),
                        "buyVolumes", binned.buyVolumes(),
                        "sellVolumes", binned.sellVolumes()
                    )
                ));
            }

            ctx.json(result);
        } catch (Exception e) {
            LOG.error("Failed to get daily binned profiles", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check coverage and compute profiles for any gaps.
     * Only computes for ranges where aggTrades actually exist — stamps coverage
     * only for those intersections so we don't falsely claim coverage for empty ranges.
     */
    private void ensureCoverage(String symbol, long start, long end) throws Exception {
        var profileCoverage = dataStore.forSymbol(symbol).coverageFor(DataStoreType.VOLUME_PROFILES);
        var gaps = profileCoverage.findGaps("volume_profiles", "", start, end);

        if (gaps.isEmpty()) return;

        // Get aggTrades coverage to know where raw data actually exists
        var aggTradesCoverage = dataStore.forSymbol(symbol).coverageFor(DataStoreType.AGG_TRADES);
        var aggRanges = aggTradesCoverage.getCoverageRanges("agg_trades", "default");

        for (long[] gap : gaps) {
            // Find the intersection of this gap with aggTrades coverage
            for (var aggRange : aggRanges) {
                long intersectStart = Math.max(gap[0], aggRange.rangeStart());
                long intersectEnd = Math.min(gap[1], aggRange.rangeEnd());

                if (intersectStart <= intersectEnd) {
                    LOG.info("Computing volume profiles for {} [{} - {}] (aggTrades available)",
                        symbol, intersectStart, intersectEnd);
                    computer.compute(symbol, intersectStart, intersectEnd);
                    profileCoverage.addCoverage("volume_profiles", "", intersectStart, intersectEnd, true);
                }
            }
        }
    }
}
