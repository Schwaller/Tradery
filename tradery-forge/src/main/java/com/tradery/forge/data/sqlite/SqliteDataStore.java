package com.tradery.forge.data.sqlite;

import com.tradery.core.model.*;
import com.tradery.forge.data.DataConfig;
import com.tradery.forge.data.sqlite.dao.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
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
     * Get candles for a symbol and timeframe.
     */
    public List<Candle> getCandles(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).candles().query(timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting candles: " + e.getMessage(), e);
        }
    }

    /**
     * Save candles (insert or update).
     */
    public void saveCandles(String symbol, String timeframe, List<Candle> candles) throws IOException {
        try {
            forSymbol(symbol).candles().insertBatch(timeframe, candles);
        } catch (SQLException e) {
            throw new IOException("SQLite error saving candles: " + e.getMessage(), e);
        }
    }

    /**
     * Get the latest candle.
     */
    public Candle getLatestCandle(String symbol, String timeframe) throws IOException {
        try {
            return forSymbol(symbol).candles().getLatest(timeframe);
        } catch (SQLException e) {
            throw new IOException("SQLite error getting latest candle: " + e.getMessage(), e);
        }
    }

    /**
     * Check if candle data exists for a range.
     */
    public boolean hasCandleData(String symbol, String timeframe, long startTime, long endTime)
            throws IOException {
        try {
            return forSymbol(symbol).coverageFor(DataStoreType.CANDLES).isFullyCovered("candles", timeframe, startTime, endTime);
        } catch (SQLException e) {
            throw new IOException("SQLite error checking candle coverage: " + e.getMessage(), e);
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
     * Get aggregated trades for a symbol, filtered by market type.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime,
                                        java.util.Set<DataMarketType> marketTypes) throws IOException {
        try {
            return forSymbol(symbol).aggTrades().queryWithMarketType(startTime, endTime, marketTypes);
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

            List<String> timeframes = data.candles().getAvailableTimeframes();
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = data.candles().getStats(tf);
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
    }
}
