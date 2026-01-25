package com.tradery.forge.data.sqlite;

import com.tradery.forge.data.sqlite.dao.*;
import com.tradery.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main facade for SQLite data storage.
 * Provides unified access to all DAOs with lazy initialization per symbol.
 *
 * This class is designed to match the APIs of the existing CSV-based stores
 * to enable seamless migration.
 */
public class SqliteDataStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataStore.class);

    // Lazy-loaded DAOs per symbol
    private final Map<String, SymbolData> symbolDataMap = new ConcurrentHashMap<>();

    /**
     * Get or create DAO container for a symbol.
     * Initializes schema on first access.
     */
    public SymbolData forSymbol(String symbol) {
        return symbolDataMap.computeIfAbsent(symbol, sym -> {
            try {
                SqliteConnection conn = SqliteConnection.forSymbol(sym);
                SqliteSchema.initialize(conn);
                return new SymbolData(conn);
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
            return forSymbol(symbol).coverage().isFullyCovered("candles", timeframe, startTime, endTime);
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
     */
    public void addCoverage(String symbol, String dataType, String subKey,
                            long rangeStart, long rangeEnd, boolean isComplete) throws IOException {
        try {
            forSymbol(symbol).coverage().addCoverage(dataType, subKey, rangeStart, rangeEnd, isComplete);
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
            return forSymbol(symbol).coverage().findGaps(dataType, subKey, start, end);
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
            return forSymbol(symbol).coverage().isFullyCovered(dataType, subKey, start, end);
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
     * Get the SQLite connection for a symbol (for advanced operations).
     */
    public SqliteConnection getConnection(String symbol) {
        return forSymbol(symbol).connection;
    }

    /**
     * Check if a symbol database exists.
     */
    public boolean symbolExists(String symbol) {
        return SqliteConnection.forSymbol(symbol).exists();
    }

    /**
     * Get statistics for a symbol's database.
     */
    public DatabaseStats getStats(String symbol) throws IOException {
        try {
            SymbolData data = forSymbol(symbol);
            CandleDao.CandleStats candleStats = null;

            // Get candle stats for the most common timeframe (1h)
            List<String> timeframes = data.candles().getAvailableTimeframes();
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = data.candles().getStats(tf);
            }

            long aggTradeCount = data.aggTrades().count();
            int fundingCount = data.fundingRates().count();
            int oiCount = data.openInterest().count();

            return new DatabaseStats(
                symbol,
                candleStats != null ? candleStats.count() : 0,
                aggTradeCount,
                fundingCount,
                oiCount,
                data.connection.getDbFile().length()
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
     */
    public static class SymbolData {
        private final SqliteConnection connection;
        private final CandleDao candleDao;
        private final AggTradesDao aggTradesDao;
        private final FundingRateDao fundingRateDao;
        private final OpenInterestDao openInterestDao;
        private final PremiumIndexDao premiumIndexDao;
        private final CoverageDao coverageDao;

        SymbolData(SqliteConnection connection) {
            this.connection = connection;
            this.candleDao = new CandleDao(connection);
            this.aggTradesDao = new AggTradesDao(connection);
            this.fundingRateDao = new FundingRateDao(connection);
            this.openInterestDao = new OpenInterestDao(connection);
            this.premiumIndexDao = new PremiumIndexDao(connection);
            this.coverageDao = new CoverageDao(connection);
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

        public CoverageDao coverage() {
            return coverageDao;
        }

        public SqliteConnection connection() {
            return connection;
        }
    }
}
