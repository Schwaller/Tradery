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
            return forSymbol(symbol).coverage().isFullyCovered(coverageKey, timeframe, startTime, endTime);
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
     *
     * @param symbol Trading symbol
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param chunkSize Number of trades per chunk
     * @param chunkConsumer Consumer called with each chunk of trades
     * @return Total number of trades streamed
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

    /**
     * Consolidate fragmented coverage ranges for a symbol.
     * Call once on startup to compact ranges accumulated before inline compaction was added.
     */
    public void consolidateCoverage(String symbol) throws IOException {
        try {
            CoverageDao dao = forSymbol(symbol).coverage();
            for (String dataType : List.of("agg_trades", "candles", "funding_rates", "open_interest", "premium_index")) {
                // Get all sub_keys for this data type
                var ranges = dao.getCoverageRanges(dataType, "");
                if (ranges.size() > 1) {
                    dao.consolidateRanges(dataType, "");
                }
                var defaultRanges = dao.getCoverageRanges(dataType, "default");
                if (defaultRanges.size() > 1) {
                    dao.consolidateRanges(dataType, "default");
                }
            }
        } catch (SQLException e) {
            throw new IOException("SQLite error consolidating coverage: " + e.getMessage(), e);
        }
    }

    // ========== Discovery Methods ==========

    /**
     * Get all symbols that have a database file in the data directory.
     */
    public List<String> getAvailableSymbolNames() {
        File dataDir = DataConfig.getInstance().getDataDir();
        if (dataDir == null || !dataDir.exists()) return List.of();

        File[] dbFiles = dataDir.listFiles((dir, name) ->
            name.endsWith(".db") && !name.equals("symbols.db"));
        if (dbFiles == null) return List.of();

        List<String> symbols = new ArrayList<>();
        for (File f : dbFiles) {
            String name = f.getName();
            symbols.add(name.substring(0, name.length() - 3)); // strip .db
        }
        symbols.sort(String::compareTo);
        return symbols;
    }

    /**
     * Get distinct data types with coverage for a symbol.
     * Returns map of dataType -> list of subKeys (e.g. "klines" -> ["1h","4h"]).
     */
    public Map<String, List<String>> getCoverageDataTypes(String symbol) throws IOException {
        try {
            java.sql.Connection c = forSymbol(symbol).connection.getConnection();
            Map<String, List<String>> result = new LinkedHashMap<>();

            String sql = "SELECT DISTINCT data_type, sub_key FROM data_coverage ORDER BY data_type, sub_key";
            try (java.sql.PreparedStatement stmt = c.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String dt = rs.getString("data_type");
                    String sk = rs.getString("sub_key");
                    result.computeIfAbsent(dt, k -> new ArrayList<>()).add(sk);
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

            // Get candle stats for the most common timeframe (1h) from perp data
            // (most existing data is perp futures)
            List<String> timeframes = data.candles().getAvailableTimeframes("perp");
            if (!timeframes.isEmpty()) {
                String tf = timeframes.contains("1h") ? "1h" : timeframes.get(0);
                candleStats = data.candles().getStats(tf, "perp");
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
