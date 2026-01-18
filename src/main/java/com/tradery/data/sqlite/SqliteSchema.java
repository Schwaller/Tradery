package com.tradery.data.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite schema creation and versioning.
 * Creates all tables for market data storage.
 */
public class SqliteSchema {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchema.class);

    // Current schema version - increment when schema changes
    public static final int CURRENT_VERSION = 1;

    /**
     * Initialize the schema for a symbol's database.
     * Creates all tables if they don't exist.
     */
    public static void initialize(SqliteConnection conn) throws SQLException {
        Connection c = conn.getConnection();

        int currentVersion = getSchemaVersion(c);

        if (currentVersion == 0) {
            // Fresh database - create all tables
            createAllTables(c);
            setSchemaVersion(c, CURRENT_VERSION);
            log.info("Created SQLite schema v{} for {}", CURRENT_VERSION, conn.getSymbol());
        } else if (currentVersion < CURRENT_VERSION) {
            // Needs migration
            migrateSchema(c, currentVersion, CURRENT_VERSION);
            setSchemaVersion(c, CURRENT_VERSION);
            log.info("Migrated SQLite schema from v{} to v{} for {}",
                currentVersion, CURRENT_VERSION, conn.getSymbol());
        } else {
            log.debug("SQLite schema v{} up to date for {}", currentVersion, conn.getSymbol());
        }
    }

    /**
     * Get the current schema version (0 if none set).
     */
    private static int getSchemaVersion(Connection conn) throws SQLException {
        // Check if schema_version table exists
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "schema_version", null)) {
            if (!rs.next()) {
                return 0;
            }
        }

        // Get the version
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Set the schema version.
     */
    private static void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            stmt.setInt(1, version);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    /**
     * Create all tables for a fresh database.
     */
    private static void createAllTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Schema version table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at INTEGER NOT NULL
                )
                """);

            // CANDLES (multi-timeframe in one table)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS candles (
                    timeframe TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume REAL NOT NULL,
                    PRIMARY KEY (timeframe, timestamp)
                ) WITHOUT ROWID
                """);

            // Index for time range queries within a timeframe
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_candles_tf_ts
                ON candles(timeframe, timestamp)
                """);

            // AGGREGATED TRADES
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agg_trades (
                    agg_trade_id INTEGER PRIMARY KEY,
                    price REAL NOT NULL,
                    quantity REAL NOT NULL,
                    first_trade_id INTEGER NOT NULL,
                    last_trade_id INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    is_buyer_maker INTEGER NOT NULL
                )
                """);

            // Index for time range queries
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_agg_trades_ts
                ON agg_trades(timestamp)
                """);

            // FUNDING RATES
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS funding_rates (
                    funding_time INTEGER PRIMARY KEY,
                    funding_rate REAL NOT NULL,
                    mark_price REAL NOT NULL DEFAULT 0.0
                ) WITHOUT ROWID
                """);

            // OPEN INTEREST
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS open_interest (
                    timestamp INTEGER PRIMARY KEY,
                    open_interest REAL NOT NULL,
                    open_interest_value REAL NOT NULL
                ) WITHOUT ROWID
                """);

            // PREMIUM INDEX (multi-interval)
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

            // DATA COVERAGE (replaces .partial.csv tracking)
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

            // Index for coverage queries
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_coverage_type_key
                ON data_coverage(data_type, sub_key)
                """);
        }
    }

    /**
     * Migrate schema from one version to another.
     */
    private static void migrateSchema(Connection conn, int fromVersion, int toVersion) throws SQLException {
        // Add migration logic as schema evolves
        for (int v = fromVersion + 1; v <= toVersion; v++) {
            switch (v) {
                // Add cases for future migrations
                // case 2 -> migrateToV2(conn);
                default -> log.debug("No migration needed for version {}", v);
            }
        }
    }

    /**
     * Drop all data tables (for testing or reset).
     * Keeps schema_version.
     */
    public static void dropAllTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS candles");
            stmt.execute("DROP TABLE IF EXISTS agg_trades");
            stmt.execute("DROP TABLE IF EXISTS funding_rates");
            stmt.execute("DROP TABLE IF EXISTS open_interest");
            stmt.execute("DROP TABLE IF EXISTS premium_index");
            stmt.execute("DROP TABLE IF EXISTS data_coverage");
        }
    }

    /**
     * Get table statistics for debugging.
     */
    public static TableStats getTableStats(Connection conn, String tableName) throws SQLException {
        long rowCount = 0;
        long pageCount = 0;

        try (Statement stmt = conn.createStatement()) {
            // Get row count
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                if (rs.next()) {
                    rowCount = rs.getLong(1);
                }
            }

            // Get page count (approximate size)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pragma_page_count() WHERE name = '" + tableName + "'")) {
                if (rs.next()) {
                    pageCount = rs.getLong(1);
                }
            }
        }

        return new TableStats(tableName, rowCount, pageCount);
    }

    /**
     * Table statistics.
     */
    public record TableStats(String tableName, long rowCount, long pageCount) {
        public long estimatedSizeBytes() {
            // SQLite default page size is 4096
            return pageCount * 4096;
        }
    }
}
