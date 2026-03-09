package com.tradery.dataservice.api;

import com.tradery.dataservice.data.ProfileStore;
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
 * Delegates coverage gap detection and computation to ProfileStore.
 */
public class ProfileHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileHandler.class);

    private final SqliteDataStore dataStore;
    private final ProfileStore profileStore;
    private final TickSizeResolver tickSizeResolver;
    private final VolumeProfileAnalyzer analyzer;

    public ProfileHandler(SqliteDataStore dataStore, ProfileStore profileStore,
                          TickSizeResolver tickSizeResolver, VolumeProfileAnalyzer analyzer) {
        this.dataStore = dataStore;
        this.profileStore = profileStore;
        this.tickSizeResolver = tickSizeResolver;
        this.analyzer = analyzer;
    }

    /**
     * GET /profile?symbol=X&timeframe=Y&start=Z&end=W&marketType=perp
     * Raw tick-level profile data.
     */
    public void getProfile(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            String marketType = ctx.queryParamAsClass("marketType", String.class).getOrDefault("perp");

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            profileStore.ensureCoverage(symbol, marketType, start, end);

            // Resolve to the best available pyramid timeframe
            String profileTimeframe = resolveProfileTimeframe(timeframe);
            List<ProfileRow> profiles = dataStore.getProfiles(symbol, marketType, profileTimeframe, start, end);
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
     * GET /profile/binned?symbol=X&timeframe=Y&start=Z&end=W&marketType=perp&mode=BIN_COUNT|PRICE_DELTA&binParam=96&valueAreaPct=70
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
            String marketType = ctx.queryParamAsClass("marketType", String.class).getOrDefault("perp");

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            profileStore.ensureCoverage(symbol, marketType, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, marketType, timeframe, start, end);
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
     * GET /profile/poc-series?symbol=X&timeframe=Y&start=Z&end=W&marketType=perp&compositeDays=N
     * POC time series, optionally with rolling composite.
     */
    public void getPocSeries(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String timeframe = ctx.queryParam("timeframe");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            Integer compositeDays = ctx.queryParamAsClass("compositeDays", Integer.class).getOrDefault(null);
            String marketType = ctx.queryParamAsClass("marketType", String.class).getOrDefault("perp");

            if (symbol == null || timeframe == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, timeframe, start, and end are required"));
                return;
            }

            profileStore.ensureCoverage(symbol, marketType, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, marketType, timeframe, start, end);
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
     * GET /profile/daily-levels?symbol=X&start=Z&end=W&marketType=perp
     * Returns POC/VAH/VAL for each day in the range, using "1d" timeframe profiles.
     */
    public void getDailyLevels(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            String marketType = ctx.queryParamAsClass("marketType", String.class).getOrDefault("perp");

            if (symbol == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, start, and end are required"));
                return;
            }

            profileStore.ensureCoverage(symbol, marketType, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, marketType, "1d", start, end);
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
     * GET /profile/daily-binned?symbol=X&start=Z&end=W&marketType=perp&binCount=24&valueAreaPct=70
     * Returns per-day binned histograms for the entire range in a single call.
     */
    public void getDailyBinned(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            Long start = ctx.queryParamAsClass("start", Long.class).getOrDefault(null);
            Long end = ctx.queryParamAsClass("end", Long.class).getOrDefault(null);
            int binCount = ctx.queryParamAsClass("binCount", Integer.class).getOrDefault(24);
            double valueAreaPct = ctx.queryParamAsClass("valueAreaPct", Double.class).getOrDefault(70.0);
            String marketType = ctx.queryParamAsClass("marketType", String.class).getOrDefault("perp");

            if (symbol == null || start == null || end == null) {
                ctx.status(400).json(Map.of("error", "symbol, start, and end are required"));
                return;
            }

            profileStore.ensureCoverage(symbol, marketType, start, end);

            List<ProfileRow> profiles = dataStore.getProfiles(symbol, marketType, "1d", start, end);
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
     * Resolve a chart timeframe to the best available profile pyramid level.
     * Profile pyramid: 10s, 1m, 5m, 30m, 1h, 4h, 1d.
     * Returns the largest pyramid level that fits within the requested timeframe.
     */
    static String resolveProfileTimeframe(String timeframe) {
        long ms = parseTimeframeMs(timeframe);
        // Pyramid levels in ascending order
        String[][] levels = {
            {"10s", "10000"}, {"1m", "60000"}, {"5m", "300000"},
            {"30m", "1800000"}, {"1h", "3600000"}, {"4h", "14400000"}, {"1d", "86400000"}
        };
        String best = "5m"; // default
        for (String[] level : levels) {
            long levelMs = Long.parseLong(level[1]);
            if (levelMs <= ms) {
                best = level[0];
            }
        }
        return best;
    }

    private static long parseTimeframeMs(String tf) {
        if (tf == null || tf.isEmpty()) return 300_000L; // 5m default
        char unit = tf.charAt(tf.length() - 1);
        long value;
        try {
            value = Long.parseLong(tf.substring(0, tf.length() - 1));
        } catch (NumberFormatException e) {
            return 300_000L;
        }
        return switch (unit) {
            case 's' -> value * 1_000L;
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            case 'd' -> value * 86_400_000L;
            case 'w' -> value * 604_800_000L;
            case 'M' -> value * 2_592_000_000L;
            default -> 300_000L;
        };
    }
}
