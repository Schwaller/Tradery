package com.tradery.dataservice.data.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Manages SQLite schema creation and versioning.
 * Creates only the tables relevant to each DataStoreType.
 * No migration logic — incompatible schema versions drop and recreate tables.
 * Data re-backfills on demand.
 */
public class SqliteSchema {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchema.class);

    public static final int CURRENT_VERSION = 4;

    /**
     * Initialize the schema for a specific data store type.
     * Creates only the tables relevant to the given type.
     */
    public static void initialize(SqliteConnection conn, DataStoreType type) throws SQLException {
        Connection c = conn.getConnection();

        int currentVersion = getSchemaVersion(c);

        if (currentVersion == 0) {
            createTablesForType(c, type);
            setSchemaVersion(c, CURRENT_VERSION);
            log.info("Created SQLite schema v{} for {} ({})", CURRENT_VERSION, conn.getSymbol(), type);
        } else if (currentVersion < CURRENT_VERSION) {
            // Incompatible schema — drop all data tables and recreate.
            // Data re-backfills on demand. No migration logic needed.
            log.info("Schema v{} outdated (current: v{}) for {} ({}) — dropping and recreating",
                    currentVersion, CURRENT_VERSION, conn.getSymbol(), type);
            dropDataTables(c, type);
            createTablesForType(c, type);
            setSchemaVersion(c, CURRENT_VERSION);
        } else {
            log.debug("SQLite schema v{} up to date for {} ({})", currentVersion, conn.getSymbol(), type);
        }
    }

    /**
     * Drop all data tables for a type (preserves schema_version and data_coverage structure).
     * Coverage entries are cleared so data re-backfills on demand.
     */
    private static void dropDataTables(Connection c, DataStoreType type) throws SQLException {
        try (Statement stmt = c.createStatement()) {
            switch (type) {
                case CANDLES -> {
                    stmt.execute("DROP TABLE IF EXISTS candles");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type LIKE '%candle%' OR data_type = 'klines'");
                }
                case AGG_TRADES -> {
                    stmt.execute("DROP TABLE IF EXISTS agg_trades");
                    stmt.execute("DROP TABLE IF EXISTS stablecoin_rates");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'agg_trades'");
                }
                case FUNDING_RATES -> {
                    stmt.execute("DROP TABLE IF EXISTS funding_rates");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'funding_rates'");
                }
                case OPEN_INTEREST -> {
                    stmt.execute("DROP TABLE IF EXISTS open_interest");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'open_interest'");
                }
                case PREMIUM_INDEX -> {
                    stmt.execute("DROP TABLE IF EXISTS premium_index");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'premium_index'");
                }
                case VOLUME_PROFILES -> {
                    stmt.execute("DROP TABLE IF EXISTS volume_profiles");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'volume_profiles'");
                }
                case SPECTRUM -> {
                    stmt.execute("DROP TABLE IF EXISTS trade_size_spectrum");
                    stmt.execute("DELETE FROM data_coverage WHERE data_type = 'spectrum'");
                }
            }
        }
    }

    private static int getSchemaVersion(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "schema_version", null)) {
            if (!rs.next()) {
                return 0;
            }
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            stmt.setInt(1, version);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    /**
     * Create tables specific to each DataStoreType.
     * Each DB gets schema_version + data_coverage + its own data tables.
     */
    private static void createTablesForType(Connection conn, DataStoreType type) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Common: schema_version
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at INTEGER NOT NULL
                )
                """);

            // Common: data_coverage
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS data_coverage (
                    data_type TEXT NOT NULL,
                    sub_key TEXT NOT NULL DEFAULT '',
                    range_start INTEGER NOT NULL,
                    range_end INTEGER NOT NULL,
                    is_complete INTEGER NOT NULL DEFAULT 0,
                    last_updated INTEGER NOT NULL,
                    PRIMARY KEY (data_type, sub_key, range_start)
                ) WITHOUT ROWID
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_coverage_type_key
                ON data_coverage(data_type, sub_key)
                """);

            // Type-specific tables
            switch (type) {
                case CANDLES -> createCandlesTables(stmt);
                case AGG_TRADES -> createAggTradesTables(stmt);
                case FUNDING_RATES -> createFundingRatesTables(stmt);
                case OPEN_INTEREST -> createOpenInterestTables(stmt);
                case PREMIUM_INDEX -> createPremiumIndexTables(stmt);
                case VOLUME_PROFILES -> createVolumeProfilesTables(stmt);
                case SPECTRUM -> createSpectrumTables(stmt);
            }
        }
    }

    private static void createCandlesTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS candles (
                timeframe TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                open REAL NOT NULL,
                high REAL NOT NULL,
                low REAL NOT NULL,
                close REAL NOT NULL,
                volume REAL NOT NULL,
                trade_count INTEGER DEFAULT -1,
                quote_volume REAL DEFAULT -1,
                taker_buy_volume REAL DEFAULT -1,
                taker_buy_quote_volume REAL DEFAULT -1,
                PRIMARY KEY (timeframe, timestamp)
            ) WITHOUT ROWID
            """);
    }

    private static void createAggTradesTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS agg_trades (
                agg_trade_id INTEGER NOT NULL PRIMARY KEY,
                price REAL NOT NULL,
                quantity REAL NOT NULL,
                first_trade_id INTEGER NOT NULL,
                last_trade_id INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                is_buyer_maker INTEGER NOT NULL,
                raw_symbol TEXT,
                normalized_price REAL
            )
            """);

        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_agg_trades_ts
            ON agg_trades(timestamp)
            """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS stablecoin_rates (
                symbol TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                rate REAL NOT NULL,
                PRIMARY KEY (symbol, timestamp)
            ) WITHOUT ROWID
            """);
    }

    private static void createFundingRatesTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS funding_rates (
                funding_time INTEGER PRIMARY KEY,
                funding_rate REAL NOT NULL,
                mark_price REAL NOT NULL DEFAULT 0.0
            ) WITHOUT ROWID
            """);
    }

    private static void createOpenInterestTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS open_interest (
                timestamp INTEGER PRIMARY KEY,
                open_interest REAL NOT NULL,
                open_interest_value REAL NOT NULL
            ) WITHOUT ROWID
            """);
    }

    private static void createPremiumIndexTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS premium_index (
                interval TEXT NOT NULL,
                open_time INTEGER NOT NULL,
                open REAL NOT NULL,
                high REAL NOT NULL,
                low REAL NOT NULL,
                close REAL NOT NULL,
                close_time INTEGER NOT NULL,
                PRIMARY KEY (interval, open_time)
            ) WITHOUT ROWID
            """);
    }

    private static void createVolumeProfilesTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS volume_profiles (
                timeframe TEXT NOT NULL,
                window_start INTEGER NOT NULL,
                tick_size REAL NOT NULL,
                total_buy_volume REAL NOT NULL DEFAULT 0,
                total_sell_volume REAL NOT NULL DEFAULT 0,
                level_count INTEGER NOT NULL DEFAULT 0,
                profile_data BLOB NOT NULL,
                PRIMARY KEY (timeframe, window_start)
            ) WITHOUT ROWID
            """);
    }

    private static void createSpectrumTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS trade_size_spectrum (
                mode          TEXT NOT NULL DEFAULT 'raw',
                window_start  INTEGER NOT NULL,
                bucket_index  INTEGER NOT NULL,
                trade_count   INTEGER NOT NULL,
                total_volume  REAL NOT NULL,
                buy_volume    REAL NOT NULL,
                sell_volume   REAL NOT NULL,
                PRIMARY KEY (mode, window_start, bucket_index)
            ) WITHOUT ROWID
            """);

        stmt.execute("""
            CREATE INDEX IF NOT EXISTS idx_spectrum_mode_window
            ON trade_size_spectrum(mode, window_start)
            """);
    }

    /**
     * Get table statistics for debugging.
     */
    public static TableStats getTableStats(Connection conn, String tableName) throws SQLException {
        long rowCount = 0;
        long pageCount = 0;

        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                if (rs.next()) {
                    rowCount = rs.getLong(1);
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pragma_page_count() WHERE name = '" + tableName + "'")) {
                if (rs.next()) {
                    pageCount = rs.getLong(1);
                }
            }
        }

        return new TableStats(tableName, rowCount, pageCount);
    }

    public record TableStats(String tableName, long rowCount, long pageCount) {
        public long estimatedSizeBytes() {
            return pageCount * 4096;
        }
    }
}
