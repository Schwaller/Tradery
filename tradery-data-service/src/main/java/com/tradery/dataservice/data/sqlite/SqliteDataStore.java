package com.tradery.dataservice.data.sqlite;

import com.tradery.core.model.*;
import com.tradery.dataservice.data.DataConfig;
import com.tradery.dataservice.data.sqlite.dao.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main facade for SQLite data storage.
 * Provides unified access to all DAOs with lazy initialization per symbol.
 *
 * Split file layout:
 *   agg_trades_{exchange}_{marketType}.db   — split by exchange × market_type
 *   candles_{marketType}.db                 — split by market_type
 *   volume_profiles_{marketType}.db         — split by market_type
 *   spectrum_{marketType}.db                — split by market_type
 *   funding_rates.db                        — no split (inherently perp-only)
 *   open_interest.db                        — no split
 *   premium_index.db                        — no split
 */
public class SqliteDataStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataStore.class);

    // Lazy-loaded DAOs per symbol
    private final Map<String, SymbolData> symbolDataMap = new ConcurrentHashMap<>();

    /**
     * Get or create DAO container for a symbol.
     * Initializes schema on first access for each DB.
     */
    public SymbolData forSymbol(String symbol) {
        return symbolDataMap.computeIfAbsent(symbol, sym -> new SymbolData(sym));
    }

    // ========== Candle Methods ==========

    /**
     * Get candles for a symbol, market type, and timeframe.
     */
    public List<Candle> getCandles(String symbol, String marketType, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).candlesDao(marketType).query(timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting candles: " + e.getMessage(), e);
        }
    }

    /**
     * Save candles (insert or update).
     */
    public void saveCandles(String symbol, String marketType, String timeframe, List<Candle> candles) throws IOException {
        try {
            forSymbol(symbol).candlesDao(marketType).insertBatch(timeframe, candles);

            // Record coverage so inventory/coverage APIs reflect stored data
            if (!candles.isEmpty()) {
                long start = candles.stream().mapToLong(Candle::timestamp).min().getAsLong();
                long end = candles.stream().mapToLong(Candle::timestamp).max().getAsLong();
                String subKey = timeframe + ":" + marketType;
                forSymbol(symbol).coverageFor(DataStoreType.CANDLES, marketType).addCoverage("klines", subKey, start, end, true);
            }
        } catch (SQLException e) {
            throw new IOException("SQLite error saving candles: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest candle.
     */
    public Candle getLatestCandle(String symbol, String marketType, String timeframe) throws IOException {
        try {
            return forSymbol(symbol).candlesDao(marketType).getLatest(timeframe);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest candle: " + e.getMessage(), e);
        }
    }

    /**
     * Check if candle data exists for a range.
     */
    public boolean hasCandleData(String symbol, String marketType, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            String coverageKey = "candles:" + marketType;
            return forSymbol(symbol).coverageFor(DataStoreType.CANDLES, marketType).isFullyCovered(coverageKey, timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error checking candle coverage: " + e.getMessage(), e);
        }
    }

    // ========== Fear & Greed Index Methods ==========

    private FearGreedDao fearGreedDao;

    /**
     * Get or create the FearGreedDao (lazy, uses dedicated global DB).
     */
    public synchronized FearGreedDao getFearGreedDao() {
        if (fearGreedDao == null) {
            try {
                SqliteConnection conn = SqliteConnection.forGlobal("fear_greed.db");
                fearGreedDao = new FearGreedDao(conn);
                fearGreedDao.createTable();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to initialize Fear & Greed DB", e);
            }
        }
        return fearGreedDao;
    }

    /**
     * Get Fear & Greed Index data for a time range.
     */
    public List<FearGreedIndex> getFearGreed(long startTime, long endTime) throws IOException {
        try {
            return getFearGreedDao().query(startTime, endTime);
        } catch (java.sql.SQLException e) {
            throw new IOException("SQLite error getting Fear & Greed data: " + e.getMessage(), e);
        }
    }

    /**
     * Get Fear & Greed Index data with lookback for averaging.
     */
    public List<FearGreedIndex> getFearGreedWithLookback(long startTime, long endTime, int lookbackDays) throws IOException {
        try {
            return getFearGreedDao().queryWithLookback(startTime, endTime, lookbackDays);
        } catch (java.sql.SQLException e) {
            throw new IOException("SQLite error getting Fear & Greed data: " + e.getMessage(), e);
        }
    }

    /**
     * Save Fear & Greed Index data.
     */
    public void saveFearGreed(List<FearGreedIndex> data) throws IOException {
        try {
            getFearGreedDao().insertBatch(data);
        } catch (java.sql.SQLException e) {
            throw new IOException("SQLite error saving Fear & Greed data: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest Fear & Greed record.
     */
    public FearGreedIndex getLatestFearGreed() throws IOException {
        try {
            return getFearGreedDao().getLatest();
        } catch (java.sql.SQLException e) {
            throw new IOException("SQLite error getting latest Fear & Greed: " + e.getMessage(), e);
        }
    }

    // ========== Funding Rate Methods ==========

    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).fundingRates().queryWithLookback(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting funding rates: " + e.getMessage(), e);
        }
    }

    public void saveFundingRates(String symbol, List<FundingRate> rates) throws IOException {
        try {
            forSymbol(symbol).fundingRates().insertBatch(rates);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving funding rates: " + e.getMessage(), e);
        }
    }

    // ========== Open Interest Methods ==========

    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).openInterest().query(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting open interest: " + e.getMessage(), e);
        }
    }

    public void saveOpenInterest(String symbol, List<OpenInterest> data) throws IOException {
        try {
            forSymbol(symbol).openInterest().insertBatch(data);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving open interest: " + e.getMessage(), e);
        }
    }

    // ========== Premium Index Methods ==========

    public List<PremiumIndex> getPremiumIndex(String symbol, String interval, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).premiumIndex().query(interval, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting premium index: " + e.getMessage(), e);
        }
    }

    public void savePremiumIndex(String symbol, String interval, List<PremiumIndex> data) throws IOException {
        try {
            forSymbol(symbol).premiumIndex().insertBatch(interval, data);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving premium index: " + e.getMessage(), e);
        }
    }

    // ========== Aggregated Trades Methods ==========

    /**
     * Get aggregated trades for a symbol (defaults to binance/perp).
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime) throws IOException {
        return getAggTrades(symbol, "binance", "perp", startTime, endTime);
    }

    /**
     * Get aggregated trades for a symbol, exchange, and market type.
     */
    public List<AggTrade> getAggTrades(String symbol, String exchange, String marketType, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(exchange, marketType).query(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Save aggregated trades.
     */
    public void saveAggTrades(String symbol, List<AggTrade> trades) throws IOException {
        if (trades.isEmpty()) return;
        // Route by the exchange/marketType of the first trade (all trades in a batch should match)
        AggTrade first = trades.get(0);
        String exchange = first.exchange() != null ? first.exchange().getConfigKey() : "binance";
        String marketType = first.marketType() != null ? first.marketType().getConfigKey() : "perp";
        saveAggTrades(symbol, exchange, marketType, trades);
    }

    /**
     * Save aggregated trades to a specific exchange/marketType DB.
     */
    public void saveAggTrades(String symbol, String exchange, String marketType, List<AggTrade> trades) throws IOException {
        try {
            forSymbol(symbol).aggTradesDao(exchange, marketType).insertBatch(trades);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest aggregated trade (defaults to binance/perp).
     */
    public AggTrade getLatestAggTrade(String symbol) throws IOException {
        return getLatestAggTrade(symbol, "binance", "perp");
    }

    /**
     * Get the latest aggregated trade for a specific exchange/marketType.
     */
    public AggTrade getLatestAggTrade(String symbol, String exchange, String marketType) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(exchange, marketType).getLatest();
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest agg trade: " + e.getMessage(), e);
        }
    }

    /**
     * Count aggregated trades in a time range (defaults to binance/perp).
     */
    public long countAggTrades(String symbol, long startTime, long endTime) throws IOException {
        return countAggTrades(symbol, "binance", "perp", startTime, endTime);
    }

    /**
     * Count aggregated trades in a time range for a specific exchange/marketType.
     */
    public long countAggTrades(String symbol, String exchange, String marketType, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(exchange, marketType).countInRange(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error counting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Stream aggregated trades in chunks (defaults to binance/perp).
     */
    public int streamAggTrades(String symbol, long startTime, long endTime, int chunkSize,
                               java.util.function.Consumer<List<AggTrade>> chunkConsumer) throws IOException {
        return streamAggTrades(symbol, "binance", "perp", startTime, endTime, chunkSize, chunkConsumer);
    }

    /**
     * Stream aggregated trades in chunks for a specific exchange/marketType.
     */
    public int streamAggTrades(String symbol, String exchange, String marketType, long startTime, long endTime,
                               int chunkSize, java.util.function.Consumer<List<AggTrade>> chunkConsumer) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(exchange, marketType).streamQuery(startTime, endTime, chunkSize, chunkConsumer);
        } catch (SQLException e) {
            throw new IOException("SQLite error streaming agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Stream aggregated trades filtered by DataMarketType enum.
     * Routes to the correct split file (defaults exchange to "binance").
     */
    public int streamAggTrades(String symbol, DataMarketType marketType, long startTime, long endTime, int chunkSize,
                               java.util.function.Consumer<List<AggTrade>> chunkConsumer) throws IOException {
        String mt = marketType != null ? marketType.getConfigKey() : "perp";
        return streamAggTrades(symbol, "binance", mt, startTime, endTime, chunkSize, chunkConsumer);
    }

    // ========== Volume Profile Methods ==========

    /**
     * Get volume profiles for a symbol, market type, timeframe, and time range.
     */
    public List<VolumeProfileDao.ProfileRow> getProfiles(String symbol, String marketType, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).profilesDao(marketType).query(timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting profiles: " + e.getMessage(), e);
        }
    }

    /**
     * Get volume profiles (defaults to "perp" market type).
     */
    public List<VolumeProfileDao.ProfileRow> getProfiles(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        return getProfiles(symbol, "perp", timeframe, startTime, endTime);
    }

    /**
     * Save volume profile rows. Routes by the marketType of the first row.
     */
    public void saveProfiles(String symbol, List<VolumeProfileDao.ProfileRow> rows) throws IOException {
        if (rows.isEmpty()) return;
        String marketType = rows.get(0).marketType() != null ? rows.get(0).marketType() : "perp";
        saveProfiles(symbol, marketType, rows);
    }

    /**
     * Save volume profile rows to a specific marketType DB.
     */
    public void saveProfiles(String symbol, String marketType, List<VolumeProfileDao.ProfileRow> rows) throws IOException {
        try {
            forSymbol(symbol).profilesDao(marketType).upsertBatch(rows);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving profiles: " + e.getMessage(), e);
        }
    }

    /**
     * Stream volume profiles in chunks.
     */
    public int streamProfiles(String symbol, String marketType, String timeframe, long startTime, long endTime,
                              int chunkSize, java.util.function.Consumer<List<VolumeProfileDao.ProfileRow>> consumer)
            throws IOException {
        try {
            return forSymbol(symbol).profilesDao(marketType).streamQuery(timeframe, startTime, endTime, chunkSize, consumer);
        } catch (SQLException e) {
            throw new IOException("SQLite error streaming profiles: " + e.getMessage(), e);
        }
    }

    /**
     * Stream volume profiles in chunks (defaults to "perp" market type).
     */
    public int streamProfiles(String symbol, String timeframe, long startTime, long endTime,
                              int chunkSize, java.util.function.Consumer<List<VolumeProfileDao.ProfileRow>> consumer)
            throws IOException {
        return streamProfiles(symbol, "perp", timeframe, startTime, endTime, chunkSize, consumer);
    }

    /**
     * Count volume profiles for a market type, timeframe and time range.
     */
    public long countProfiles(String symbol, String marketType, String timeframe, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).profilesDao(marketType).count(timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error counting profiles: " + e.getMessage(), e);
        }
    }

    /**
     * Count volume profiles (defaults to "perp" market type).
     */
    public long countProfiles(String symbol, String timeframe, long startTime, long endTime) throws IOException {
        return countProfiles(symbol, "perp", timeframe, startTime, endTime);
    }

    // ========== Spectrum Methods ==========

    public List<SpectrumWindow> getSpectrum(String symbol, long startTime, long endTime, String mode) throws IOException {
        return getSpectrum(symbol, "perp", startTime, endTime, mode);
    }

    public List<SpectrumWindow> getSpectrum(String symbol, String marketType, long startTime, long endTime, String mode) throws IOException {
        try {
            return forSymbol(symbol).spectrumDao(marketType).queryWindows(mode, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting spectrum: " + e.getMessage(), e);
        }
    }

    public List<SpectrumWindow> getSpectrum(String symbol, long startTime, long endTime) throws IOException {
        return getSpectrum(symbol, startTime, endTime, "raw");
    }

    public List<SpectrumDao.AggregatedBucket> getSpectrumAggregated(String symbol, long startTime, long endTime, long windowMs, String mode)
            throws IOException {
        try {
            return forSymbol(symbol).spectrumDao("perp").queryAggregated(mode, startTime, endTime, windowMs);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting aggregated spectrum: " + e.getMessage(), e);
        }
    }

    public List<SpectrumDao.AggregatedBucket> getSpectrumAggregated(String symbol, long startTime, long endTime, long windowMs)
            throws IOException {
        return getSpectrumAggregated(symbol, startTime, endTime, windowMs, "raw");
    }

    public List<SpectrumDao.FlatBucket> getSpectrumFlat(String symbol, long startTime, long endTime, String mode)
            throws IOException {
        try {
            return forSymbol(symbol).spectrumDao("perp").queryFlat(mode, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting flat spectrum: " + e.getMessage(), e);
        }
    }

    public List<SpectrumDao.FlatBucket> getSpectrumFlat(String symbol, long startTime, long endTime)
            throws IOException {
        return getSpectrumFlat(symbol, startTime, endTime, "raw");
    }

    public void saveSpectrum(String symbol, String mode, List<SpectrumDao.SpectrumRow> rows) throws IOException {
        try {
            forSymbol(symbol).spectrumDao("perp").insertBatch(mode, rows);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving spectrum: " + e.getMessage(), e);
        }
    }

    public void saveSpectrum(String symbol, List<SpectrumDao.SpectrumRow> rows) throws IOException {
        saveSpectrum(symbol, "raw", rows);
    }

    public long countSpectrum(String symbol, long startTime, long endTime, String mode) throws IOException {
        try {
            return forSymbol(symbol).spectrumDao("perp").countInRange(mode, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error counting spectrum: " + e.getMessage(), e);
        }
    }

    public long countSpectrum(String symbol, long startTime, long endTime) throws IOException {
        return countSpectrum(symbol, startTime, endTime, "raw");
    }

    // ========== Coverage Methods ==========

    /**
     * Record coverage for a data type.
     * Routes to the correct DB file based on dataType and subKey.
     */
    public void addCoverage(String symbol, String dataType, String subKey,
                            long rangeStart, long rangeEnd, boolean isComplete) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            String qualifier = deriveQualifier(dbType, subKey);
            forSymbol(symbol).coverageFor(dbType, qualifier).addCoverage(dataType, subKey, rangeStart, rangeEnd, isComplete);
        } catch (SQLException e) {
            throw new IOException("SQLite error adding coverage: " + e.getMessage(), e);
        }
    }

    /**
     * Remove coverage for a specific time range.
     * Deletes ranges entirely within the range, and shrinks ranges that partially overlap.
     */
    public void removeCoverage(String symbol, String dataType, String subKey,
                                long rangeStart, long rangeEnd) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            String qualifier = deriveQualifier(dbType, subKey);
            forSymbol(symbol).coverageFor(dbType, qualifier).removeCoverageRange(dataType, subKey, rangeStart, rangeEnd);
        } catch (SQLException e) {
            throw new IOException("SQLite error removing coverage: " + e.getMessage(), e);
        }
    }

    /**
     * Find gaps in coverage.
     */
    public List<long[]> findGaps(String symbol, String dataType, String subKey,
                                  long start, long end) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            String qualifier = deriveQualifier(dbType, subKey);
            return forSymbol(symbol).coverageFor(dbType, qualifier).findGaps(dataType, subKey, start, end);
        } catch (SQLException e) {
            throw new IOException("SQLite error finding gaps: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a range is fully covered.
     */
    public boolean isFullyCovered(String symbol, String dataType, String subKey,
                                   long start, long end) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            String qualifier = deriveQualifier(dbType, subKey);
            return forSymbol(symbol).coverageFor(dbType, qualifier).isFullyCovered(dataType, subKey, start, end);
        } catch (SQLException e) {
            throw new IOException("SQLite error checking coverage: " + e.getMessage(), e);
        }
    }

    /**
     * Get all coverage ranges for a data type and sub_key.
     * Routes to the correct DB file based on dataType and subKey.
     */
    public List<CoverageDao.CoverageRange> getCoverageRanges(String symbol, String dataType, String subKey) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            String qualifier = deriveQualifier(dbType, subKey);
            return forSymbol(symbol).coverageFor(dbType, qualifier).getCoverageRanges(dataType, subKey);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting coverage ranges: " + e.getMessage(), e);
        }
    }

    /**
     * Derive the file qualifier from a coverage sub_key.
     * For unsplit types, returns null (uses base filename).
     * For candles/profiles/spectrum: qualifier is "perp" (default).
     * For aggTrades: maps sub_key to the file qualifier.
     *   "default" -> "binance_perp", "spot" -> "binance_spot", "bybit_perp" -> "bybit_perp"
     */
    private String deriveQualifier(DataStoreType type, String subKey) {
        if (!type.isSplit()) return null;
        if (type.isSplitByMarket()) {
            // Coverage sub_keys for candles/profiles/spectrum have format "timeframe:marketType"
            // or just "marketType". Extract the market type to route to the correct DB file.
            if (subKey != null && subKey.contains(":")) {
                String mt = subKey.substring(subKey.lastIndexOf(':') + 1);
                if ("spot".equals(mt) || "perp".equals(mt)) {
                    return mt;
                }
            }
            if ("spot".equals(subKey)) return "spot";
            return "perp";
        }
        // AGG_TRADES — map legacy sub_keys to file qualifiers
        if (subKey == null || subKey.isEmpty() || "default".equals(subKey)) {
            return "binance_perp";
        }
        if ("spot".equals(subKey)) {
            return "binance_spot";
        }
        return subKey;
    }

    /**
     * Consolidate fragmented coverage ranges for a symbol.
     */
    public void consolidateCoverage(String symbol) throws IOException {
        try {
            Map<DataStoreType, List<String>> typeKeys = Map.of(
                DataStoreType.CANDLES, List.of("candles", "klines", "candles:perp", "candles:spot"),
                DataStoreType.AGG_TRADES, List.of("agg_trades"),
                DataStoreType.FUNDING_RATES, List.of("funding_rates"),
                DataStoreType.OPEN_INTEREST, List.of("open_interest"),
                DataStoreType.PREMIUM_INDEX, List.of("premium_index"),
                DataStoreType.VOLUME_PROFILES, List.of("volume_profiles"),
                DataStoreType.SPECTRUM, List.of("spectrum")
            );

            for (var entry : typeKeys.entrySet()) {
                CoverageDao dao = forSymbol(symbol).coverageFor(entry.getKey(), null);
                for (String dataType : entry.getValue()) {
                    var ranges = dao.getCoverageRanges(dataType, "");
                    if (ranges.size() > 1) {
                        dao.consolidateRanges(dataType, "");
                    }
                    var defaultRanges = dao.getCoverageRanges(dataType, "default");
                    if (defaultRanges.size() > 1) {
                        dao.consolidateRanges(dataType, "default");
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("SQLite error consolidating coverage: " + e.getMessage(), e);
        }
    }

    // ========== Discovery Methods ==========

    /**
     * Get all symbols that have a subdirectory in the data directory.
     */
    public List<String> getAvailableSymbolNames() {
        File dataDir = DataConfig.getInstance().getDataDir();
        if (dataDir == null || !dataDir.exists()) return List.of();

        File[] dirs = dataDir.listFiles(File::isDirectory);
        if (dirs == null) return List.of();

        List<String> symbols = new ArrayList<>();
        for (File d : dirs) {
            String name = d.getName();
            if (name.startsWith("__")) continue;
            File[] dbs = d.listFiles((dir, n) -> n.endsWith(".db"));
            if (dbs != null && dbs.length > 0) {
                symbols.add(name);
            }
        }
        symbols.sort(String::compareTo);
        return symbols;
    }

    /**
     * Get distinct data types with coverage for a symbol.
     * Queries all DB files and merges results.
     */
    public Map<String, List<String>> getCoverageDataTypes(String symbol) throws IOException {
        try {
            Map<String, List<String>> result = new LinkedHashMap<>();
            SymbolData data = forSymbol(symbol);

            String sql = "SELECT DISTINCT data_type, sub_key FROM data_coverage ORDER BY data_type, sub_key";

            // Query coverage from all connections that exist
            for (SqliteConnection conn : data.getAllConnections()) {
                java.sql.Connection c = conn.getConnection();
                try (java.sql.PreparedStatement stmt = c.prepareStatement(sql);
                     java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String dt = rs.getString("data_type");
                        String sk = rs.getString("sub_key");
                        result.computeIfAbsent(dt, k -> new ArrayList<>()).add(sk);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IOException("SQLite error getting coverage types: " + e.getMessage(), e);
        }
    }

    /**
     * List available qualifiers for a split data type by scanning DB files on disk.
     * e.g., for AGG_TRADES in BTCUSDT: ["binance_perp", "binance_spot", "bybit_perp"]
     */
    public List<String> getAvailableQualifiers(String symbol, DataStoreType type) {
        File dataDir = DataConfig.getInstance().getDataDir();
        File symbolDir = new File(dataDir, symbol);
        if (!symbolDir.isDirectory()) return List.of();

        String base = type.getFilename().replace(".db", "");
        String prefix = base + "_";

        File[] matches = symbolDir.listFiles((dir, name) ->
            name.startsWith(prefix) && name.endsWith(".db") && !name.endsWith("-wal") && !name.endsWith("-shm"));

        if (matches == null || matches.length == 0) return List.of();

        List<String> qualifiers = new ArrayList<>();
        for (File f : matches) {
            // Extract qualifier: "agg_trades_binance_perp.db" → "binance_perp"
            String fname = f.getName();
            String qualifier = fname.substring(prefix.length(), fname.length() - 3); // strip ".db"
            qualifiers.add(qualifier);
        }
        qualifiers.sort(String::compareTo);
        return qualifiers;
    }

    // ========== Utility Methods ==========

    public void close() {
        SqliteConnection.closeAll();
        symbolDataMap.clear();
    }

    public boolean symbolExists(String symbol) {
        File dataDir = DataConfig.getInstance().getDataDir();
        File symbolDir = new File(dataDir, symbol);
        if (!symbolDir.isDirectory()) return false;
        File[] dbs = symbolDir.listFiles((dir, name) -> name.endsWith(".db"));
        return dbs != null && dbs.length > 0;
    }

    public DatabaseStats getStats(String symbol) throws IOException {
        try {
            SymbolData data = forSymbol(symbol);
            CandleDao.CandleStats candleStats = null;

            CandleDao candleDao = data.candlesDao("perp");
            List<String> timeframes = candleDao.getAvailableTimeframes();
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = candleDao.getStats(tf);
            }

            long aggTradeCount = data.aggTradesDao("binance", "perp").count();
            int fundingCount = data.fundingRates().count();
            int oiCount = data.openInterest().count();

            long totalSize = 0;
            File dataDir = DataConfig.getInstance().getDataDir();
            File symbolDir = new File(dataDir, symbol);
            if (symbolDir.isDirectory()) {
                File[] dbFiles = symbolDir.listFiles((dir, name) -> name.endsWith(".db") || name.endsWith("-wal") || name.endsWith("-shm"));
                if (dbFiles != null) {
                    for (File f : dbFiles) {
                        totalSize += f.length();
                    }
                }
            }

            return new DatabaseStats(
                symbol,
                candleStats != null ? candleStats.count() : 0,
                aggTradeCount,
                fundingCount,
                oiCount,
                totalSize
            );
        } catch (SQLException e) {
            throw new IOException("SQLite error getting stats: " + e.getMessage(), e);
        }
    }

    public record DatabaseStats(
        String symbol,
        int candleCount,
        long aggTradeCount,
        int fundingCount,
        int oiCount,
        long fileSizeBytes
    ) {
        public String fileSizeFormatted() {
            if (fileSizeBytes > 1_000_000_000) {
                return String.format("%.2f GB", fileSizeBytes / 1_000_000_000.0);
            } else if (fileSizeBytes > 1_000_000) {
                return String.format("%.2f MB", fileSizeBytes / 1_000_000.0);
            } else if (fileSizeBytes > 1_000) {
                return String.format("%.2f KB", fileSizeBytes / 1_000.0);
            }
            return fileSizeBytes + " bytes";
        }
    }

    /**
     * Container for all DAOs for a single symbol.
     * Uses a lazy connection map — each (type, qualifier) pair gets its own SqliteConnection and DAO.
     */
    public static class SymbolData {
        private final String symbol;
        private final Map<String, SqliteConnection> connections = new ConcurrentHashMap<>();
        private final Map<String, CandleDao> candleDaos = new ConcurrentHashMap<>();
        private final Map<String, AggTradesDao> aggTradesDaos = new ConcurrentHashMap<>();
        private final Map<String, VolumeProfileDao> profileDaos = new ConcurrentHashMap<>();
        private final Map<String, SpectrumDao> spectrumDaos = new ConcurrentHashMap<>();
        private final Map<String, CoverageDao> coverageDaos = new ConcurrentHashMap<>();

        // Unsplit DAOs — one per type
        private volatile FundingRateDao fundingRateDao;
        private volatile OpenInterestDao openInterestDao;
        private volatile PremiumIndexDao premiumIndexDao;

        SymbolData(String symbol) {
            this.symbol = symbol;
        }

        // ========== Unsplit types (funding, OI, premium) ==========

        private SqliteConnection getOrCreateConn(DataStoreType type, String qualifier) {
            String key = type.name() + (qualifier != null && !qualifier.isEmpty() ? ":" + qualifier : "");
            return connections.computeIfAbsent(key, k -> {
                SqliteConnection conn = SqliteConnection.forSymbolAndType(symbol, type, qualifier);
                try {
                    SqliteSchema.initialize(conn, type);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to initialize schema for " + symbol + " " + type + " " + qualifier, e);
                }
                return conn;
            });
        }

        public FundingRateDao fundingRates() {
            if (fundingRateDao == null) {
                synchronized (this) {
                    if (fundingRateDao == null) {
                        fundingRateDao = new FundingRateDao(getOrCreateConn(DataStoreType.FUNDING_RATES, null));
                    }
                }
            }
            return fundingRateDao;
        }

        public OpenInterestDao openInterest() {
            if (openInterestDao == null) {
                synchronized (this) {
                    if (openInterestDao == null) {
                        openInterestDao = new OpenInterestDao(getOrCreateConn(DataStoreType.OPEN_INTEREST, null));
                    }
                }
            }
            return openInterestDao;
        }

        public PremiumIndexDao premiumIndex() {
            if (premiumIndexDao == null) {
                synchronized (this) {
                    if (premiumIndexDao == null) {
                        premiumIndexDao = new PremiumIndexDao(getOrCreateConn(DataStoreType.PREMIUM_INDEX, null));
                    }
                }
            }
            return premiumIndexDao;
        }

        // ========== Split types ==========

        /**
         * Get CandleDao for a market type (each marketType gets its own DB file).
         */
        public CandleDao candlesDao(String marketType) {
            String qualifier = marketType != null ? marketType : "perp";
            return candleDaos.computeIfAbsent(qualifier, q ->
                new CandleDao(getOrCreateConn(DataStoreType.CANDLES, q)));
        }

        /**
         * Backward-compatible: candles() returns perp DAO.
         */
        public CandleDao candles() {
            return candlesDao("perp");
        }

        /**
         * Get AggTradesDao for an exchange+marketType pair.
         */
        public AggTradesDao aggTradesDao(String exchange, String marketType) {
            String ex = exchange != null ? exchange : "binance";
            String mt = marketType != null ? marketType : "perp";
            String qualifier = ex + "_" + mt;
            return aggTradesDaos.computeIfAbsent(qualifier, q -> {
                Exchange exEnum = Exchange.fromConfigKey(ex);
                DataMarketType mtEnum = DataMarketType.fromConfigKey(mt);
                return new AggTradesDao(
                    getOrCreateConn(DataStoreType.AGG_TRADES, q),
                    exEnum != null ? exEnum : Exchange.BINANCE,
                    mtEnum != null ? mtEnum : DataMarketType.FUTURES_PERP
                );
            });
        }

        /**
         * Backward-compatible: aggTrades() returns binance/perp DAO.
         */
        public AggTradesDao aggTrades() {
            return aggTradesDao("binance", "perp");
        }

        /**
         * Get VolumeProfileDao for a market type.
         */
        public VolumeProfileDao profilesDao(String marketType) {
            String qualifier = marketType != null ? marketType : "perp";
            return profileDaos.computeIfAbsent(qualifier, q ->
                new VolumeProfileDao(getOrCreateConn(DataStoreType.VOLUME_PROFILES, q)));
        }

        /**
         * Backward-compatible: volumeProfiles() returns perp DAO.
         */
        public VolumeProfileDao volumeProfiles() {
            return profilesDao("perp");
        }

        /**
         * Get SpectrumDao for a market type.
         */
        public SpectrumDao spectrumDao(String marketType) {
            String qualifier = marketType != null ? marketType : "perp";
            return spectrumDaos.computeIfAbsent(qualifier, q ->
                new SpectrumDao(getOrCreateConn(DataStoreType.SPECTRUM, q)));
        }

        /**
         * Backward-compatible: spectrum() returns perp DAO.
         */
        public SpectrumDao spectrum() {
            return spectrumDao("perp");
        }

        // ========== Coverage ==========

        /**
         * Get CoverageDao for a specific data store type and qualifier.
         * For unsplit types, qualifier is ignored.
         */
        public CoverageDao coverageFor(DataStoreType type, String qualifier) {
            String q;
            if (!type.isSplit()) {
                q = null;
            } else if (qualifier != null) {
                q = qualifier;
            } else if (type.isSplitByExchangeAndMarket()) {
                q = "binance_perp";  // Default for AGG_TRADES
            } else {
                q = "perp";  // Default for CANDLES, VOLUME_PROFILES, SPECTRUM
            }
            String key = type.name() + (q != null ? ":" + q : "");
            return coverageDaos.computeIfAbsent(key, k ->
                new CoverageDao(getOrCreateConn(type, q)));
        }

        /**
         * Backward-compatible: coverageFor(type) routes to unsplit or default perp.
         */
        public CoverageDao coverageFor(DataStoreType type) {
            return coverageFor(type, null);
        }

        /**
         * Get the SqliteConnection for a specific data store type (unsplit or default).
         */
        public SqliteConnection connectionFor(DataStoreType type) {
            return getOrCreateConn(type, null);
        }

        /**
         * Get all active connections for this symbol (for queries across all DBs).
         */
        public List<SqliteConnection> getAllConnections() {
            // Ensure at least the unsplit types are initialized
            getOrCreateConn(DataStoreType.FUNDING_RATES, null);
            getOrCreateConn(DataStoreType.OPEN_INTEREST, null);
            getOrCreateConn(DataStoreType.PREMIUM_INDEX, null);

            // Also initialize default split connections (perp) if they exist on disk
            File dataDir = DataConfig.getInstance().getDataDir();
            File symbolDir = new File(dataDir, symbol);
            if (symbolDir.isDirectory()) {
                File[] dbFiles = symbolDir.listFiles((dir, name) -> name.endsWith(".db"));
                if (dbFiles != null) {
                    for (File dbFile : dbFiles) {
                        // Try to map each DB file to a type+qualifier and ensure it's in our map
                        String fname = dbFile.getName();
                        for (DataStoreType type : DataStoreType.values()) {
                            String base = type.getFilename().replace(".db", "");
                            if (fname.equals(type.getFilename())) {
                                getOrCreateConn(type, null);
                            } else if (fname.startsWith(base + "_") && fname.endsWith(".db")) {
                                String qualifier = fname.substring(base.length() + 1, fname.length() - 3);
                                getOrCreateConn(type, qualifier);
                            }
                        }
                    }
                }
            }

            return new ArrayList<>(connections.values());
        }

        /**
         * Get all coverage entries across all DBs for this symbol.
         */
        public List<CoverageDao.CoverageEntry> getAllCoverage() throws SQLException {
            List<CoverageDao.CoverageEntry> all = new ArrayList<>();
            for (SqliteConnection conn : getAllConnections()) {
                CoverageDao dao = new CoverageDao(conn);
                all.addAll(dao.getAllCoverage());
            }
            return all;
        }
    }
}
