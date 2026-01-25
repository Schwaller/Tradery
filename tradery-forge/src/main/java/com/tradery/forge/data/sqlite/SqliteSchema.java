package com.tradery.forge.data.sqlite;

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
    // Version 2: Added multi-exchange support (exchange, market_type, raw_symbol, normalized_price columns)
    public static final int CURRENT_VERSION = 2;

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
            // Extended fields from Binance klines: tradeCount, quoteVolume, takerBuyVolume, takerBuyQuoteVolume
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

            // Index for time range queries within a timeframe
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_candles_tf_ts
                ON candles(timeframe, timestamp)
                """);

            // AGGREGATED TRADES (multi-exchange aware)
            // exchange: source exchange (binance, bybit, okx, etc.)
            // market_type: spot, perp, dated
            // raw_symbol: original symbol from exchange
            // normalized_price: USD-normalized price for cross-exchange aggregation
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agg_trades (
                    agg_trade_id INTEGER NOT NULL,
                    price REAL NOT NULL,
                    quantity REAL NOT NULL,
                    first_trade_id INTEGER NOT NULL,
                    last_trade_id INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    is_buyer_maker INTEGER NOT NULL,
                    exchange TEXT NOT NULL DEFAULT 'binance',
                    market_type TEXT NOT NULL DEFAULT 'perp',
                    raw_symbol TEXT,
                    normalized_price REAL,
                    PRIMARY KEY (exchange, agg_trade_id)
                )
                """);

            // Index for time range queries (most common)
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_agg_trades_ts
                ON agg_trades(timestamp)
                """);

            // Index for exchange-filtered queries
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_agg_trades_exchange_ts
                ON agg_trades(exchange, timestamp)
                """);

            // STABLECOIN RATES (for price normalization)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stablecoin_rates (
                    symbol TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    rate REAL NOT NULL,
                    PRIMARY KEY (symbol, timestamp)
                ) WITHOUT ROWID
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
        for (int v = fromVersion + 1; v <= toVersion; v++) {
            switch (v) {
                case 2 -> migrateToV2(conn);
                default -> log.debug("No migration needed for version {}", v);
            }
        }
    }

    /**
     * Migrate to version 2: Add multi-exchange support to agg_trades.
     */
    private static void migrateToV2(Connection conn) throws SQLException {
        log.info("Migrating to schema v2: Adding multi-exchange support...");

        try (Statement stmt = conn.createStatement()) {
            // Add new columns to agg_trades (with defaults for existing data)
            // SQLite doesn't support adding columns with DEFAULT that references other columns,
            // so we add nullable columns then update them

            // Check if columns already exist
            boolean hasExchangeColumn = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(agg_trades)")) {
                while (rs.next()) {
                    String colName = rs.getString("name");
                    if ("exchange".equals(colName)) {
                        hasExchangeColumn = true;
                        break;
                    }
                }
            }

            if (!hasExchangeColumn) {
                stmt.execute("ALTER TABLE agg_trades ADD COLUMN exchange TEXT DEFAULT 'binance'");
                stmt.execute("ALTER TABLE agg_trades ADD COLUMN market_type TEXT DEFAULT 'perp'");
                stmt.execute("ALTER TABLE agg_trades ADD COLUMN raw_symbol TEXT");
                stmt.execute("ALTER TABLE agg_trades ADD COLUMN normalized_price REAL");

                // Set normalized_price = price for existing data
                stmt.execute("UPDATE agg_trades SET normalized_price = price WHERE normalized_price IS NULL");

                // Create new index for exchange-filtered queries
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_agg_trades_exchange_ts
                    ON agg_trades(exchange, timestamp)
                    """);

                log.info("Added exchange columns to agg_trades");
            }

            // Create stablecoin_rates table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stablecoin_rates (
                    symbol TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    rate REAL NOT NULL,
                    PRIMARY KEY (symbol, timestamp)
                ) WITHOUT ROWID
                """);

            log.info("Created stablecoin_rates table");
        }

        log.info("Migration to v2 complete");
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
