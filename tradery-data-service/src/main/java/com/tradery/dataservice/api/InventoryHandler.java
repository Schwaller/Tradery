package com.tradery.dataservice.api;

import com.tradery.core.model.FearGreedIndex;
import com.tradery.dataservice.data.DataConfig;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.dao.*;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Handler for data inventory and deletion endpoints.
 * Provides a single API call to discover all stored data across all symbols.
 */
public class InventoryHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryHandler.class);

    private final SqliteDataStore dataStore;

    public InventoryHandler(SqliteDataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * GET /inventory
     * Returns comprehensive inventory of all stored data.
     */
    public void getInventory(Context ctx) {
        try {
            List<SymbolInventory> symbols = new ArrayList<>();

            for (String symbolName : dataStore.getAvailableSymbolNames()) {
                // Skip internal databases
                if (symbolName.startsWith("__")) continue;

                try {
                    symbols.add(buildSymbolInventory(symbolName));
                } catch (Exception e) {
                    LOG.warn("Failed to get inventory for {}: {}", symbolName, e.getMessage());
                }
            }

            // Fear & Greed (separate database)
            FearGreedInventory fearGreed = buildFearGreedInventory();

            // Disk usage
            long totalDiskUsage = calculateTotalDiskUsage();

            ctx.json(new InventoryResponse(symbols, fearGreed, totalDiskUsage));
        } catch (Exception e) {
            LOG.error("Failed to get inventory", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * GET /inventory/disk-usage
     * Returns disk usage breakdown by symbol.
     */
    public void getDiskUsage(Context ctx) {
        try {
            File dataDir = DataConfig.getInstance().getDataDir();
            Map<String, Long> bySymbol = new LinkedHashMap<>();
            Map<String, Long> byDataType = new LinkedHashMap<>();
            long total = 0;

            if (dataDir != null && dataDir.exists()) {
                // Scan subdirectories (new format: one dir per symbol)
                File[] dirs = dataDir.listFiles(File::isDirectory);
                if (dirs != null) {
                    Arrays.sort(dirs, Comparator.comparing(File::getName));
                    for (File dir : dirs) {
                        if (dir.getName().startsWith("__")) continue; // skip __global
                        long symbolTotal = 0;
                        // Per-DB-file sizes
                        File[] dbFiles = dir.listFiles((d, name) -> name.endsWith(".db"));
                        if (dbFiles != null) {
                            for (File db : dbFiles) {
                                long dbSize = db.length();
                                // Add WAL/SHM companion sizes
                                File wal = new File(dir, db.getName() + "-wal");
                                File shm = new File(dir, db.getName() + "-shm");
                                if (wal.exists()) dbSize += wal.length();
                                if (shm.exists()) dbSize += shm.length();
                                symbolTotal += dbSize;
                                // Key: "BTCUSDT:candles.db" -> size
                                byDataType.put(dir.getName() + ":" + db.getName(), dbSize);
                            }
                        }
                        if (symbolTotal > 0) {
                            bySymbol.put(dir.getName(), symbolTotal);
                            total += symbolTotal;
                        }
                    }
                }
                // Also include __global
                File globalDir = new File(dataDir, "__global");
                if (globalDir.isDirectory()) {
                    total += sumDbFileSizes(globalDir);
                }
            }

            long volumeFree = (dataDir != null && dataDir.exists()) ? dataDir.getUsableSpace() : 0;
            long volumeTotal = (dataDir != null && dataDir.exists()) ? dataDir.getTotalSpace() : 0;
            ctx.json(new DiskUsageResponse(total, bySymbol, byDataType, volumeFree, volumeTotal));
        } catch (Exception e) {
            LOG.error("Failed to get disk usage", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    private long sumDbFileSizes(File dir) {
        long size = 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db") || name.endsWith("-wal") || name.endsWith("-shm"));
        if (files != null) {
            for (File f : files) {
                size += f.length();
            }
        }
        return size;
    }

    /**
     * DELETE /data?symbol=X&dataType=Y&timeframe=Z&marketType=W&exchange=E&interval=I
     * Delete specific data.
     */
    public void deleteData(Context ctx) {
        try {
            String symbol = ctx.queryParam("symbol");
            String dataType = ctx.queryParam("dataType");

            if (dataType == null) {
                ctx.status(400).json(new ErrorResponse("dataType is required"));
                return;
            }

            long deletedRecords = 0;

            if ("fearGreed".equals(dataType)) {
                // Fear & Greed is global, no symbol needed
                deletedRecords = deleteFearGreed();
            } else {
                if (symbol == null) {
                    ctx.status(400).json(new ErrorResponse("symbol is required for this dataType"));
                    return;
                }

                deletedRecords = switch (dataType) {
                    case "candles" -> deleteCandles(symbol, ctx.queryParam("timeframe"), ctx.queryParam("marketType"));
                    case "aggTrades" -> deleteAggTrades(symbol, ctx.queryParam("exchange"));
                    case "funding" -> deleteFunding(symbol);
                    case "openInterest" -> deleteOpenInterest(symbol);
                    case "premiumIndex" -> deletePremiumIndex(symbol, ctx.queryParam("interval"));
                    case "spectrum" -> deleteSpectrum(symbol);
                    default -> {
                        ctx.status(400).json(new ErrorResponse("Unknown dataType: " + dataType));
                        yield -1;
                    }
                };
            }

            if (deletedRecords >= 0) {
                ctx.json(new DeleteResponse(deletedRecords));
            }
        } catch (Exception e) {
            LOG.error("Failed to delete data", e);
            ctx.status(500).json(new ErrorResponse(e.getMessage()));
        }
    }

    // ========== Inventory builders ==========

    /**
     * Build inventory from the data_coverage table (fast, no table scans).
     * Coverage entries tell us what data exists without querying huge data tables.
     */
    private SymbolInventory buildSymbolInventory(String symbol) throws Exception {
        SqliteDataStore.SymbolData data = dataStore.forSymbol(symbol);
        List<CoverageDao.CoverageEntry> allCoverage = data.getAllCoverage();

        List<CandleInventory> candles = new ArrayList<>();
        List<AggTradesInventory> aggTrades = new ArrayList<>();
        FundingInventory funding = null;
        OpenInterestInventory openInterest = null;
        List<PremiumIndexInventory> premiumIndex = new ArrayList<>();
        SpectrumInventory spectrum = null;

        for (CoverageDao.CoverageEntry entry : allCoverage) {
            String dt = entry.dataType();
            String subKey = entry.subKey();
            long start = entry.startTime();
            long end = entry.endTime();

            if (dt.equals("klines")) {
                // BinanceVisionClient format: data_type="klines", sub_key="1h:spot" or "1h:perp"
                String timeframe;
                String marketType;
                if (subKey.contains(":")) {
                    String[] parts = subKey.split(":", 2);
                    timeframe = parts[0];
                    marketType = parts[1];
                } else {
                    timeframe = subKey;
                    marketType = "perp";
                }
                int estimatedCount = estimateCandleCount(start, end, timeframe);
                candles.add(new CandleInventory(timeframe, marketType, "binance", start, end, estimatedCount));

            } else if (dt.startsWith("candles:")) {
                // Format: data_type="candles:perp", sub_key="1h"
                String marketType = dt.substring("candles:".length());
                String timeframe = subKey;
                int estimatedCount = estimateCandleCount(start, end, timeframe);
                candles.add(new CandleInventory(timeframe, marketType, "binance", start, end, estimatedCount));

            } else if (dt.equals("candles")) {
                // Legacy coverage without market type - assume perp
                String timeframe = subKey;
                int estimatedCount = estimateCandleCount(start, end, timeframe);
                candles.add(new CandleInventory(timeframe, "perp", "binance", start, end, estimatedCount));

            } else if (dt.equals("agg_trades")) {
                // AggTrades: use coverage for time range only (no table queries on huge DBs)
                String marketType = "perp";
                if (subKey.contains(":")) {
                    marketType = subKey.split(":", 2)[1];
                }
                aggTrades.add(new AggTradesInventory("binance", marketType, start, end, 0));

            } else if (dt.equals("funding_rates")) {
                int estimatedCount = (int) ((end - start) / (8 * 3_600_000L)) + 1;
                funding = new FundingInventory(start, end, estimatedCount);

            } else if (dt.equals("open_interest")) {
                int estimatedCount = (int) ((end - start) / (5 * 60_000L)) + 1;
                openInterest = new OpenInterestInventory(start, end, estimatedCount);

            } else if (dt.equals("premium_index")) {
                // sub_key may be "1m:perp" or just "1m"
                String interval = subKey.contains(":") ? subKey.split(":", 2)[0] : subKey;
                int estimatedCount = estimateCandleCount(start, end, interval);
                premiumIndex.add(new PremiumIndexInventory(interval, start, end, estimatedCount));

            } else if (dt.equals("spectrum")) {
                long estimatedCount = (end - start) / 10_000L; // ~1 row per 10s per active bucket
                spectrum = new SpectrumInventory(start, end, estimatedCount);
            }
        }

        // Volume profiles (query DAO directly — not in coverage table)
        // Scan available qualifier files on disk (e.g., volume_profiles_perp.db, volume_profiles_spot.db)
        List<VolumeProfileInventory> volumeProfiles = new ArrayList<>();
        try {
            List<String> vpQualifiers = dataStore.getAvailableQualifiers(symbol, com.tradery.dataservice.data.sqlite.DataStoreType.VOLUME_PROFILES);
            if (vpQualifiers.isEmpty()) vpQualifiers = List.of("perp"); // fallback
            for (String marketType : vpQualifiers) {
                VolumeProfileDao vpDao = data.profilesDao(marketType);
                for (String tf : vpDao.getAvailableTimeframes()) {
                    long count = vpDao.countAll(tf);
                    long[] range = vpDao.getTimeRange(tf);
                    if (count > 0 && range != null) {
                        volumeProfiles.add(new VolumeProfileInventory(tf, marketType, range[0], range[1], count));
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to get volume profile inventory for {}: {}", symbol, e.getMessage());
        }

        return new SymbolInventory(symbol, candles, aggTrades, funding, openInterest, premiumIndex, volumeProfiles, spectrum);
    }

    private int estimateCandleCount(long startMs, long endMs, String timeframe) {
        long intervalMs = switch (timeframe) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "2h" -> 7_200_000L;
            case "4h" -> 14_400_000L;
            case "6h" -> 21_600_000L;
            case "8h" -> 28_800_000L;
            case "12h" -> 43_200_000L;
            case "1d" -> 86_400_000L;
            case "3d" -> 259_200_000L;
            case "1w" -> 604_800_000L;
            case "1M" -> 2_592_000_000L;
            default -> 3_600_000L; // default to 1h
        };
        return (int) ((endMs - startMs) / intervalMs) + 1;
    }

    private FearGreedInventory buildFearGreedInventory() {
        try {
            FearGreedDao dao = getFearGreedDao();
            if (dao == null) return null;

            int count = dao.count();
            if (count == 0) return null;

            long[] range = dao.getTimeRange();
            FearGreedIndex latest = dao.getLatest();

            return new FearGreedInventory(
                range != null ? range[0] : 0,
                range != null ? range[1] : 0,
                count,
                latest != null ? latest.value() : 0
            );
        } catch (Exception e) {
            LOG.warn("Failed to get Fear & Greed inventory: {}", e.getMessage());
            return null;
        }
    }

    private FearGreedDao getFearGreedDao() {
        return dataStore.getFearGreedDao();
    }

    private long calculateTotalDiskUsage() {
        File dataDir = DataConfig.getInstance().getDataDir();
        if (dataDir == null || !dataDir.exists()) return 0;

        long total = 0;
        File[] dirs = dataDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                total += sumDbFileSizes(dir);
            }
        }
        return total;
    }

    // ========== Delete operations ==========

    private long deleteCandles(String symbol, String timeframe, String marketType) throws Exception {
        if (timeframe != null && marketType != null) {
            CandleDao dao = dataStore.forSymbol(symbol).candlesDao(marketType);
            int count = dao.count(timeframe);
            dao.deleteAll(timeframe);
            return count;
        } else {
            // Delete all candles across all market type DBs
            long count = 0;
            List<String> qualifiers = dataStore.getAvailableQualifiers(symbol, com.tradery.dataservice.data.sqlite.DataStoreType.CANDLES);
            if (qualifiers.isEmpty()) qualifiers = List.of("perp");
            for (String mt : qualifiers) {
                CandleDao dao = dataStore.forSymbol(symbol).candlesDao(mt);
                for (String tf : dao.getAvailableTimeframes()) {
                    count += dao.count(tf);
                }
                dao.deleteAll();
            }
            return count;
        }
    }

    private long deleteAggTrades(String symbol, String exchange) throws Exception {
        if (exchange != null) {
            // Delete all market type DBs for this exchange
            long count = 0;
            List<String> qualifiers = dataStore.getAvailableQualifiers(symbol, com.tradery.dataservice.data.sqlite.DataStoreType.AGG_TRADES);
            for (String q : qualifiers) {
                // qualifiers like "binance_perp", "bybit_perp" — filter by exchange prefix
                if (q.startsWith(exchange + "_")) {
                    AggTradesDao dao = dataStore.forSymbol(symbol).aggTradesDao(
                        q.substring(0, q.indexOf('_')),
                        q.substring(q.indexOf('_') + 1));
                    count += dao.count();
                    dao.deleteAll();
                }
            }
            return count;
        } else {
            // Delete all aggTrades DBs for this symbol
            long count = 0;
            List<String> qualifiers = dataStore.getAvailableQualifiers(symbol, com.tradery.dataservice.data.sqlite.DataStoreType.AGG_TRADES);
            if (qualifiers.isEmpty()) qualifiers = List.of("binance_perp");
            for (String q : qualifiers) {
                int sep = q.indexOf('_');
                String ex = sep > 0 ? q.substring(0, sep) : "binance";
                String mt = sep > 0 ? q.substring(sep + 1) : q;
                AggTradesDao dao = dataStore.forSymbol(symbol).aggTradesDao(ex, mt);
                count += dao.count();
                dao.deleteAll();
            }
            return count;
        }
    }

    private long deleteFunding(String symbol) throws Exception {
        FundingRateDao dao = dataStore.forSymbol(symbol).fundingRates();
        int count = dao.count();
        dao.deleteAll();
        return count;
    }

    private long deleteOpenInterest(String symbol) throws Exception {
        OpenInterestDao dao = dataStore.forSymbol(symbol).openInterest();
        int count = dao.count();
        dao.deleteAll();
        return count;
    }

    private long deletePremiumIndex(String symbol, String interval) throws Exception {
        PremiumIndexDao dao = dataStore.forSymbol(symbol).premiumIndex();
        if (interval != null) {
            int count = dao.count(interval);
            dao.deleteAll(interval);
            return count;
        } else {
            int count = 0;
            for (String i : dao.getAvailableIntervals()) {
                count += dao.count(i);
            }
            dao.deleteAll();
            return count;
        }
    }

    private long deleteSpectrum(String symbol) throws Exception {
        long count = 0;
        List<String> qualifiers = dataStore.getAvailableQualifiers(symbol, com.tradery.dataservice.data.sqlite.DataStoreType.SPECTRUM);
        if (qualifiers.isEmpty()) qualifiers = List.of("perp");
        for (String mt : qualifiers) {
            SpectrumDao dao = dataStore.forSymbol(symbol).spectrumDao(mt);
            count += dao.count();
            dao.deleteAll();
        }
        return count;
    }

    private long deleteFearGreed() throws Exception {
        FearGreedDao dao = getFearGreedDao();
        if (dao == null) return 0;
        int count = dao.count();
        dao.deleteAll();
        return count;
    }

    // ========== Response records ==========

    public record InventoryResponse(
        List<SymbolInventory> symbols,
        FearGreedInventory fearGreed,
        long totalDiskUsage
    ) {}

    public record SymbolInventory(
        String symbol,
        List<CandleInventory> candles,
        List<AggTradesInventory> aggTrades,
        FundingInventory funding,
        OpenInterestInventory openInterest,
        List<PremiumIndexInventory> premiumIndex,
        List<VolumeProfileInventory> volumeProfiles,
        SpectrumInventory spectrum
    ) {}

    public record CandleInventory(
        String timeframe, String marketType, String exchange,
        long startTime, long endTime, int recordCount
    ) {}

    public record AggTradesInventory(
        String exchange, String marketType,
        long startTime, long endTime, long recordCount
    ) {}

    public record FundingInventory(long startTime, long endTime, int recordCount) {}

    public record OpenInterestInventory(long startTime, long endTime, int recordCount) {}

    public record PremiumIndexInventory(String interval, long startTime, long endTime, int recordCount) {}

    public record FearGreedInventory(long startTime, long endTime, int recordCount, int latestValue) {}

    public record VolumeProfileInventory(String timeframe, String marketType, long startTime, long endTime, long recordCount) {}

    public record SpectrumInventory(long startTime, long endTime, long recordCount) {}

    public record DiskUsageResponse(long totalBytes, Map<String, Long> bySymbol, Map<String, Long> byDataType, long volumeFreeBytes, long volumeTotalBytes) {}

    public record DeleteResponse(long deletedRecords) {}

    public record ErrorResponse(String error) {}
}
