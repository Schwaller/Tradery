package com.tradery.dataservice.data.sqlite;

import com.tradery.core.model.*;
import com.tradery.dataservice.data.DataConfig;
import com.tradery.dataservice.data.sqlite.dao.*;
import com.tradery.dataservice.data.sqlite.dao.FearGreedDao;
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
 * Each symbol gets a subdirectory with one DB per data type.
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
        return symbolDataMap.computeIfAbsent(symbol, sym -> {
            try {
                return new SymbolData(sym);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize SQLite for " + sym, e);
            }
        });
    }

    // ========== Candle Methods ==========

    /**
     * Get candles for a symbol, market type, and timeframe.
     */
    public List<Candle> getCandles(String symbol, String marketType, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).candles().query(timeframe, marketType, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting candles: " + e.getMessage(), e);
        }
    }

    /**
     * Save candles (insert or update).
     */
    public void saveCandles(String symbol, String marketType, String timeframe, List<Candle> candles) throws IOException {
        try {
            forSymbol(symbol).candles().insertBatch(timeframe, marketType, candles);

            // Record coverage so inventory/coverage APIs reflect stored data
            if (!candles.isEmpty()) {
                long start = candles.stream().mapToLong(Candle::timestamp).min().getAsLong();
                long end = candles.stream().mapToLong(Candle::timestamp).max().getAsLong();
                String subKey = timeframe + ":" + marketType;
                forSymbol(symbol).coverageFor(DataStoreType.CANDLES).addCoverage("klines", subKey, start, end, true);
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
            return forSymbol(symbol).candles().getLatest(timeframe, marketType);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest candle: " + e.getMessage(), e);
        }
    }

    /**
     * Check if candle data exists for a range.
     * Coverage key is "candles:{marketType}" to separate spot vs perp coverage.
     */
    public boolean hasCandleData(String symbol, String marketType, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            String coverageKey = "candles:" + marketType;
            return forSymbol(symbol).coverageFor(DataStoreType.CANDLES).isFullyCovered(coverageKey, timeframe, startTime, endTime);
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

    /**
     * Get funding rates for a symbol.
     */
    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).fundingRates().queryWithLookback(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting funding rates: " + e.getMessage(), e);
        }
    }

    /**
     * Save funding rates.
     */
    public void saveFundingRates(String symbol, List<FundingRate> rates) throws IOException {
        try {
            forSymbol(symbol).fundingRates().insertBatch(rates);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving funding rates: " + e.getMessage(), e);
        }
    }

    // ========== Open Interest Methods ==========

    /**
     * Get open interest data for a symbol.
     */
    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).openInterest().query(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting open interest: " + e.getMessage(), e);
        }
    }

    /**
     * Save open interest data.
     */
    public void saveOpenInterest(String symbol, List<OpenInterest> data) throws IOException {
        try {
            forSymbol(symbol).openInterest().insertBatch(data);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving open interest: " + e.getMessage(), e);
        }
    }

    // ========== Premium Index Methods ==========

    /**
     * Get premium index data for a symbol and interval.
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String interval, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).premiumIndex().query(interval, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting premium index: " + e.getMessage(), e);
        }
    }

    /**
     * Save premium index data.
     */
    public void savePremiumIndex(String symbol, String interval, List<PremiumIndex> data)
            throws IOException {
        try {
            forSymbol(symbol).premiumIndex().insertBatch(interval, data);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving premium index: " + e.getMessage(), e);
        }
    }

    // ========== Aggregated Trades Methods ==========

    /**
     * Get aggregated trades for a symbol.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).aggTrades().query(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Save aggregated trades.
     */
    public void saveAggTrades(String symbol, List<AggTrade> trades) throws IOException {
        try {
            forSymbol(symbol).aggTrades().insertBatch(trades);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest aggregated trade.
     */
    public AggTrade getLatestAggTrade(String symbol) throws IOException {
        try {
            return forSymbol(symbol).aggTrades().getLatest();
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest agg trade: " + e.getMessage(), e);
        }
    }

    /**
     * Count aggregated trades in a time range without loading them.
     */
    public long countAggTrades(String symbol, long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).aggTrades().countInRange(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error counting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Stream aggregated trades in chunks to avoid loading all into memory.
     */
    public int streamAggTrades(String symbol, long startTime, long endTime, int chunkSize,
                               java.util.function.Consumer<List<AggTrade>> chunkConsumer) throws IOException {
        try {
            return forSymbol(symbol).aggTrades().streamQuery(startTime, endTime, chunkSize, chunkConsumer);
        } catch (SQLException e) {
            throw new IOException("SQLite error streaming agg trades: " + e.getMessage(), e);
        }
    }

    // ========== Coverage Methods ==========

    /**
     * Record coverage for a data type.
     * Routes to the correct DB based on dataType.
     */
    public void addCoverage(String symbol, String dataType, String subKey,
                            long rangeStart, long rangeEnd, boolean isComplete) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            forSymbol(symbol).coverageFor(dbType).addCoverage(dataType, subKey, rangeStart, rangeEnd, isComplete);
        } catch (SQLException e) {
            throw new IOException("SQLite error adding coverage: " + e.getMessage(), e);
        }
    }

    /**
     * Find gaps in coverage.
     */
    public List<long[]> findGaps(String symbol, String dataType, String subKey,
                                  long start, long end) throws IOException {
        try {
            DataStoreType dbType = DataStoreType.fromCoverageKey(dataType);
            return forSymbol(symbol).coverageFor(dbType).findGaps(dataType, subKey, start, end);
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
            return forSymbol(symbol).coverageFor(dbType).isFullyCovered(dataType, subKey, start, end);
        } catch (SQLException e) {
            throw new IOException("SQLite error checking coverage: " + e.getMessage(), e);
        }
    }

    /**
     * Consolidate fragmented coverage ranges for a symbol.
     */
    public void consolidateCoverage(String symbol) throws IOException {
        try {
            // Consolidate coverage in each DB
            Map<DataStoreType, List<String>> typeKeys = Map.of(
                DataStoreType.CANDLES, List.of("candles", "klines", "candles:perp", "candles:spot"),
                DataStoreType.AGG_TRADES, List.of("agg_trades"),
                DataStoreType.FUNDING_RATES, List.of("funding_rates"),
                DataStoreType.OPEN_INTEREST, List.of("open_interest"),
                DataStoreType.PREMIUM_INDEX, List.of("premium_index")
            );

            for (var entry : typeKeys.entrySet()) {
                CoverageDao dao = forSymbol(symbol).coverageFor(entry.getKey());
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
            // Skip internal directories
            if (name.startsWith("__")) continue;
            // Must contain at least one .db file
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
     * Queries all 5 DBs and merges results.
     */
    public Map<String, List<String>> getCoverageDataTypes(String symbol) throws IOException {
        try {
            Map<String, List<String>> result = new LinkedHashMap<>();
            SymbolData data = forSymbol(symbol);

            String sql = "SELECT DISTINCT data_type, sub_key FROM data_coverage ORDER BY data_type, sub_key";

            for (DataStoreType type : DataStoreType.values()) {
                java.sql.Connection c = data.connectionFor(type).getConnection();
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

    // ========== Utility Methods ==========

    /**
     * Close all connections (call on shutdown).
     */
    public void close() {
        SqliteConnection.closeAll();
        symbolDataMap.clear();
    }

    /**
     * Check if a symbol database exists (has a subdirectory with any .db files).
     */
    public boolean symbolExists(String symbol) {
        File dataDir = DataConfig.getInstance().getDataDir();
        File symbolDir = new File(dataDir, symbol);
        if (!symbolDir.isDirectory()) return false;
        File[] dbs = symbolDir.listFiles((dir, name) -> name.endsWith(".db"));
        return dbs != null && dbs.length > 0;
    }

    /**
     * Get statistics for a symbol's databases.
     */
    public DatabaseStats getStats(String symbol) throws IOException {
        try {
            SymbolData data = forSymbol(symbol);
            CandleDao.CandleStats candleStats = null;

            List<String> timeframes = data.candles().getAvailableTimeframes("perp");
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = data.candles().getStats(tf, "perp");
            }

            long aggTradeCount = data.aggTrades().count();
            int fundingCount = data.fundingRates().count();
            int oiCount = data.openInterest().count();

            // Sum file sizes across all DBs in the symbol subdirectory
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

    /**
     * Database statistics.
     */
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
     * Each data type gets its own SqliteConnection and CoverageDao.
     */
    public static class SymbolData {
        private final String symbol;
        private final SqliteConnection candlesConn;
        private final SqliteConnection aggTradesConn;
        private final SqliteConnection fundingRatesConn;
        private final SqliteConnection openInterestConn;
        private final SqliteConnection premiumIndexConn;

        private final CandleDao candleDao;
        private final AggTradesDao aggTradesDao;
        private final FundingRateDao fundingRateDao;
        private final OpenInterestDao openInterestDao;
        private final PremiumIndexDao premiumIndexDao;

        private final CoverageDao candlesCoverage;
        private final CoverageDao aggTradesCoverage;
        private final CoverageDao fundingRatesCoverage;
        private final CoverageDao openInterestCoverage;
        private final CoverageDao premiumIndexCoverage;

        SymbolData(String symbol) throws SQLException {
            this.symbol = symbol;

            candlesConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.CANDLES);
            SqliteSchema.initialize(candlesConn, DataStoreType.CANDLES);

            aggTradesConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.AGG_TRADES);
            SqliteSchema.initialize(aggTradesConn, DataStoreType.AGG_TRADES);

            fundingRatesConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.FUNDING_RATES);
            SqliteSchema.initialize(fundingRatesConn, DataStoreType.FUNDING_RATES);

            openInterestConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.OPEN_INTEREST);
            SqliteSchema.initialize(openInterestConn, DataStoreType.OPEN_INTEREST);

            premiumIndexConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.PREMIUM_INDEX);
            SqliteSchema.initialize(premiumIndexConn, DataStoreType.PREMIUM_INDEX);

            this.candleDao = new CandleDao(candlesConn);
            this.aggTradesDao = new AggTradesDao(aggTradesConn);
            this.fundingRateDao = new FundingRateDao(fundingRatesConn);
            this.openInterestDao = new OpenInterestDao(openInterestConn);
            this.premiumIndexDao = new PremiumIndexDao(premiumIndexConn);

            this.candlesCoverage = new CoverageDao(candlesConn);
            this.aggTradesCoverage = new CoverageDao(aggTradesConn);
            this.fundingRatesCoverage = new CoverageDao(fundingRatesConn);
            this.openInterestCoverage = new CoverageDao(openInterestConn);
            this.premiumIndexCoverage = new CoverageDao(premiumIndexConn);
        }

        public CandleDao candles() {
            return candleDao;
        }

        public AggTradesDao aggTrades() {
            return aggTradesDao;
        }

        public FundingRateDao fundingRates() {
            return fundingRateDao;
        }

        public OpenInterestDao openInterest() {
            return openInterestDao;
        }

        public PremiumIndexDao premiumIndex() {
            return premiumIndexDao;
        }

        /**
         * Get the CoverageDao for a specific data store type.
         */
        public CoverageDao coverageFor(DataStoreType type) {
            return switch (type) {
                case CANDLES -> candlesCoverage;
                case AGG_TRADES -> aggTradesCoverage;
                case FUNDING_RATES -> fundingRatesCoverage;
                case OPEN_INTEREST -> openInterestCoverage;
                case PREMIUM_INDEX -> premiumIndexCoverage;
            };
        }

        /**
         * Get the SqliteConnection for a specific data store type.
         */
        public SqliteConnection connectionFor(DataStoreType type) {
            return switch (type) {
                case CANDLES -> candlesConn;
                case AGG_TRADES -> aggTradesConn;
                case FUNDING_RATES -> fundingRatesConn;
                case OPEN_INTEREST -> openInterestConn;
                case PREMIUM_INDEX -> premiumIndexConn;
            };
        }

        /**
         * Get all coverage entries across all DBs for this symbol.
         */
        public List<CoverageDao.CoverageEntry> getAllCoverage() throws SQLException {
            List<CoverageDao.CoverageEntry> all = new ArrayList<>();
            for (DataStoreType type : DataStoreType.values()) {
                all.addAll(coverageFor(type).getAllCoverage());
            }
            return all;
        }
    }
}
