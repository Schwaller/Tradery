package com.tradery.forge.data.sqlite;

import com.tradery.core.model.*;
import com.tradery.forge.data.DataConfig;
import com.tradery.forge.data.sqlite.dao.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main facade for SQLite data storage.
 * Provides unified access to all DAOs with lazy initialization per symbol.
 * Each symbol gets a subdirectory with one DB per data type.
 * Split types (aggTrades, candles) get separate DB files per qualifier.
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
     * Get candles for a symbol and timeframe. Defaults to perp.
     */
    public List<Candle> getCandles(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        return getCandles(symbol, "perp", timeframe, startTime, endTime);
    }

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
     * Save candles (insert or update). Defaults to perp.
     */
    public void saveCandles(String symbol, String timeframe, List<Candle> candles) throws IOException {
        saveCandles(symbol, "perp", timeframe, candles);
    }

    /**
     * Save candles for a specific market type.
     */
    public void saveCandles(String symbol, String marketType, String timeframe, List<Candle> candles) throws IOException {
        try {
            forSymbol(symbol).candlesDao(marketType).insertBatch(timeframe, candles);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving candles: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest candle. Defaults to perp.
     */
    public Candle getLatestCandle(String symbol, String timeframe) throws IOException {
        try {
            return forSymbol(symbol).candlesDao("perp").getLatest(timeframe);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest candle: " + e.getMessage(), e);
        }
    }

    /**
     * Check if candle data exists for a range. Defaults to perp.
     */
    public boolean hasCandleData(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).coverageFor(DataStoreType.CANDLES, "perp")
                    .isFullyCovered("candles", timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error checking candle coverage: " + e.getMessage(), e);
        }
    }

    // ========== Funding Rate Methods ==========

    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime)
            throws IOException {
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

    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime)
            throws IOException {
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

    public List<PremiumIndex> getPremiumIndex(String symbol, String interval, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).premiumIndex().query(interval, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting premium index: " + e.getMessage(), e);
        }
    }

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
     * Get aggregated trades for a symbol (all exchanges, default perp).
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime) throws IOException {
        return getAggTrades(symbol, Exchange.BINANCE, DataMarketType.FUTURES_PERP, startTime, endTime);
    }

    /**
     * Get aggregated trades filtered by market types.
     * Routes to the appropriate DB file(s) based on market type.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime,
                                        Set<DataMarketType> marketTypes) throws IOException {
        if (marketTypes == null || marketTypes.isEmpty()) {
            return getAggTrades(symbol, startTime, endTime);
        }

        try {
            // If single market type, query directly
            if (marketTypes.size() == 1) {
                DataMarketType mt = marketTypes.iterator().next();
                return forSymbol(symbol).aggTradesDao(Exchange.BINANCE, mt).query(startTime, endTime);
            }

            // Multiple market types: query each file and merge
            List<AggTrade> merged = new ArrayList<>();
            for (DataMarketType mt : marketTypes) {
                merged.addAll(forSymbol(symbol).aggTradesDao(Exchange.BINANCE, mt).query(startTime, endTime));
            }
            merged.sort(Comparator.comparingLong(AggTrade::timestamp).thenComparingLong(AggTrade::aggTradeId));
            return merged;
        } catch (SQLException e) {
            throw new IOException("SQLite error getting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Get aggregated trades for a specific exchange and market type.
     */
    public List<AggTrade> getAggTrades(String symbol, Exchange exchange, DataMarketType marketType,
                                        long startTime, long endTime) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(exchange, marketType).query(startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Save aggregated trades. Routes to the correct DB file based on each trade's exchange + market type.
     * Trades are grouped by qualifier before saving.
     */
    public void saveAggTrades(String symbol, List<AggTrade> trades) throws IOException {
        if (trades.isEmpty()) return;

        // Group by exchange + market type qualifier
        Map<String, List<AggTrade>> grouped = new LinkedHashMap<>();
        for (AggTrade trade : trades) {
            Exchange ex = trade.exchange() != null ? trade.exchange() : Exchange.BINANCE;
            DataMarketType mt = trade.marketType() != null ? trade.marketType() : DataMarketType.FUTURES_PERP;
            String key = ex.getConfigKey() + "_" + mt.getConfigKey();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(trade);
        }

        try {
            for (var entry : grouped.entrySet()) {
                List<AggTrade> group = entry.getValue();
                AggTrade first = group.get(0);
                Exchange ex = first.exchange() != null ? first.exchange() : Exchange.BINANCE;
                DataMarketType mt = first.marketType() != null ? first.marketType() : DataMarketType.FUTURES_PERP;
                forSymbol(symbol).aggTradesDao(ex, mt).insertBatch(group);
            }
        } catch (SQLException e) {
            throw new IOException("SQLite error saving agg trades: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest aggregated trade. Defaults to binance perp.
     */
    public AggTrade getLatestAggTrade(String symbol) throws IOException {
        try {
            return forSymbol(symbol).aggTradesDao(Exchange.BINANCE, DataMarketType.FUTURES_PERP).getLatest();
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest agg trade: " + e.getMessage(), e);
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
            // For split types, derive qualifier from subKey
            String qualifier = deriveQualifier(dbType, subKey);
            forSymbol(symbol).coverageFor(dbType, qualifier).addCoverage(dataType, subKey, rangeStart, rangeEnd, isComplete);
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
     * Derive the file qualifier from a coverage sub_key.
     * For unsplit types, returns null (uses base filename).
     * For candles/profiles/spectrum: qualifier is "perp" (default).
     * For aggTrades: maps sub_key to the file qualifier.
     *   "default" → "binance_perp", "spot" → "binance_spot", "bybit_perp" → "bybit_perp"
     */
    private String deriveQualifier(DataStoreType type, String subKey) {
        if (!type.isSplit()) return null;
        if (type.isSplitByMarket()) {
            // Candles, volume_profiles, spectrum — default to perp
            return "perp";
        }
        // AGG_TRADES — map legacy sub_keys to file qualifiers
        if (subKey == null || subKey.isEmpty() || "default".equals(subKey)) {
            return "binance_perp";
        }
        if ("spot".equals(subKey)) {
            return "binance_spot";
        }
        // Already in "exchange_marketType" format
        return subKey;
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

            CandleDao candles = data.candlesDao("perp");
            List<String> timeframes = candles.getAvailableTimeframes();
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = candles.getStats(tf);
            }

            long aggTradeCount = data.aggTradesDao(Exchange.BINANCE, DataMarketType.FUTURES_PERP).count();
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
     * Split types (aggTrades, candles) use lazy initialization per qualifier.
     */
    public static class SymbolData {
        private final String symbol;

        // Unsplit type connections (initialized eagerly)
        private final SqliteConnection fundingRatesConn;
        private final SqliteConnection openInterestConn;
        private final SqliteConnection premiumIndexConn;

        // Unsplit DAOs
        private final FundingRateDao fundingRateDao;
        private final OpenInterestDao openInterestDao;
        private final PremiumIndexDao premiumIndexDao;

        // Unsplit coverage DAOs
        private final CoverageDao fundingRatesCoverage;
        private final CoverageDao openInterestCoverage;
        private final CoverageDao premiumIndexCoverage;

        // Split type connections + DAOs (lazy per qualifier)
        private final Map<String, SqliteConnection> connections = new ConcurrentHashMap<>();
        private final Map<String, CandleDao> candleDaos = new ConcurrentHashMap<>();
        private final Map<String, AggTradesDao> aggTradesDaos = new ConcurrentHashMap<>();
        private final Map<String, CoverageDao> coverageDaos = new ConcurrentHashMap<>();

        SymbolData(String symbol) throws SQLException {
            this.symbol = symbol;

            // Initialize unsplit type connections
            fundingRatesConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.FUNDING_RATES);
            SqliteSchema.initialize(fundingRatesConn, DataStoreType.FUNDING_RATES);

            openInterestConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.OPEN_INTEREST);
            SqliteSchema.initialize(openInterestConn, DataStoreType.OPEN_INTEREST);

            premiumIndexConn = SqliteConnection.forSymbolAndType(symbol, DataStoreType.PREMIUM_INDEX);
            SqliteSchema.initialize(premiumIndexConn, DataStoreType.PREMIUM_INDEX);

            this.fundingRateDao = new FundingRateDao(fundingRatesConn);
            this.openInterestDao = new OpenInterestDao(openInterestConn);
            this.premiumIndexDao = new PremiumIndexDao(premiumIndexConn);

            this.fundingRatesCoverage = new CoverageDao(fundingRatesConn);
            this.openInterestCoverage = new CoverageDao(openInterestConn);
            this.premiumIndexCoverage = new CoverageDao(premiumIndexConn);
        }

        // ---- Split type accessors (lazy) ----

        /**
         * Get candle DAO for a market type (e.g., "perp", "spot").
         */
        public CandleDao candlesDao(String marketType) throws SQLException {
            String qualifier = marketType != null ? marketType : "perp";
            return candleDaos.computeIfAbsent(qualifier, q -> {
                try {
                    SqliteConnection conn = getOrCreateConnection(DataStoreType.CANDLES, q);
                    return new CandleDao(conn);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to init candles DAO for " + symbol + "/" + q, e);
                }
            });
        }

        /**
         * Get aggTrades DAO for a specific exchange and market type.
         */
        public AggTradesDao aggTradesDao(Exchange exchange, DataMarketType marketType) throws SQLException {
            String exKey = exchange != null ? exchange.getConfigKey() : "binance";
            String mtKey = marketType != null ? marketType.getConfigKey() : "perp";
            String qualifier = exKey + "_" + mtKey;
            return aggTradesDaos.computeIfAbsent(qualifier, q -> {
                try {
                    SqliteConnection conn = getOrCreateConnection(DataStoreType.AGG_TRADES, q);
                    return new AggTradesDao(conn, exchange != null ? exchange : Exchange.BINANCE,
                            marketType != null ? marketType : DataMarketType.FUTURES_PERP);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to init aggTrades DAO for " + symbol + "/" + q, e);
                }
            });
        }

        // ---- Unsplit type accessors ----

        public FundingRateDao fundingRates() {
            return fundingRateDao;
        }

        public OpenInterestDao openInterest() {
            return openInterestDao;
        }

        public PremiumIndexDao premiumIndex() {
            return premiumIndexDao;
        }

        // ---- Coverage ----

        /**
         * Get coverage DAO for a data store type with optional qualifier.
         */
        public CoverageDao coverageFor(DataStoreType type, String qualifier) throws SQLException {
            if (!type.isSplit() || qualifier == null || qualifier.isEmpty()) {
                return coverageForUnsplit(type);
            }
            String key = type.name() + ":" + qualifier;
            return coverageDaos.computeIfAbsent(key, k -> {
                try {
                    SqliteConnection conn = getOrCreateConnection(type, qualifier);
                    return new CoverageDao(conn);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to init coverage for " + symbol + "/" + type + "/" + qualifier, e);
                }
            });
        }

        /**
         * Get coverage DAO for an unsplit type.
         */
        public CoverageDao coverageFor(DataStoreType type) throws SQLException {
            return coverageForUnsplit(type);
        }

        private CoverageDao coverageForUnsplit(DataStoreType type) {
            return switch (type) {
                case FUNDING_RATES -> fundingRatesCoverage;
                case OPEN_INTEREST -> openInterestCoverage;
                case PREMIUM_INDEX -> premiumIndexCoverage;
                default -> throw new IllegalArgumentException(
                        "Use coverageFor(type, qualifier) for split types: " + type);
            };
        }

        /**
         * Get the SqliteConnection for a specific data store type (unsplit).
         */
        public SqliteConnection connectionFor(DataStoreType type) {
            return switch (type) {
                case FUNDING_RATES -> fundingRatesConn;
                case OPEN_INTEREST -> openInterestConn;
                case PREMIUM_INDEX -> premiumIndexConn;
                default -> throw new IllegalArgumentException(
                        "Use getOrCreateConnection(type, qualifier) for split types: " + type);
            };
        }

        private SqliteConnection getOrCreateConnection(DataStoreType type, String qualifier) throws SQLException {
            String key = type.name() + ":" + qualifier;
            SqliteConnection conn = connections.get(key);
            if (conn != null) return conn;

            conn = SqliteConnection.forSymbolAndType(symbol, type, qualifier);
            SqliteSchema.initialize(conn, type);
            connections.put(key, conn);
            return conn;
        }
    }
}
