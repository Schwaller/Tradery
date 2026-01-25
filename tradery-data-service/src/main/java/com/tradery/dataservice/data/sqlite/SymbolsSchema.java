package com.tradery.dataservice.data.sqlite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite schema for the symbols database.
 * Separate from per-symbol data - stores exchange symbol mappings.
 * Database file: ~/.tradery/symbols.db
 */
public class SymbolsSchema {

    private static final Logger log = LoggerFactory.getLogger(SymbolsSchema.class);

    // Current schema version - increment when schema changes
    public static final int CURRENT_VERSION = 1;

    /**
     * Initialize the schema for the symbols database.
     * Creates all tables if they don't exist.
     */
    public static void initialize(Connection conn) throws SQLException {
        int currentVersion = getSchemaVersion(conn);

        if (currentVersion == 0) {
            // Fresh database - create all tables
            createAllTables(conn);
            setSchemaVersion(conn, CURRENT_VERSION);
            log.info("Created symbols schema v{}", CURRENT_VERSION);
        } else if (currentVersion < CURRENT_VERSION) {
            // Needs migration
            migrateSchema(conn, currentVersion, CURRENT_VERSION);
            setSchemaVersion(conn, CURRENT_VERSION);
            log.info("Migrated symbols schema from v{} to v{}", currentVersion, CURRENT_VERSION);
        } else {
            log.debug("Symbols schema v{} up to date", currentVersion);
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

            // Exchange assets (one row per symbol per exchange)
            // Maps exchange-specific symbols to CoinGecko canonical IDs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exchange_assets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exchange TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    coingecko_id TEXT,
                    coin_name TEXT,
                    is_active INTEGER DEFAULT 1,
                    first_seen TEXT,
                    last_seen TEXT,
                    UNIQUE(exchange, symbol)
                )
                """);

            // Index for lookups by CoinGecko ID
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_assets_coingecko
                ON exchange_assets(coingecko_id)
                """);

            // Index for lookups by exchange
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_assets_exchange
                ON exchange_assets(exchange)
                """);

            // Trading pairs (first-class spot/perp support)
            // Supports resolution: (canonical, exchange, market_type, quote) → exchange symbol
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS trading_pairs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    exchange TEXT NOT NULL,
                    market_type TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    base_symbol TEXT NOT NULL,
                    quote_symbol TEXT NOT NULL,
                    coingecko_base_id TEXT,
                    coingecko_quote_id TEXT,
                    is_active INTEGER DEFAULT 1,
                    first_seen TEXT,
                    last_seen TEXT,
                    UNIQUE(exchange, market_type, symbol)
                )
                """);

            // Primary index for resolution queries
            // Lookup: coingecko_base_id + exchange + market_type + quote → symbol
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_pairs_resolution
                ON trading_pairs(coingecko_base_id, exchange, market_type, quote_symbol)
                """);

            // Index for lookups by base symbol (for fallback resolution)
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_pairs_base
                ON trading_pairs(base_symbol, exchange, market_type)
                """);

            // Index for exchange + market type queries
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_pairs_exchange_market
                ON trading_pairs(exchange, market_type)
                """);

            // Sync metadata table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_metadata (
                    exchange TEXT NOT NULL,
                    market_type TEXT NOT NULL,
                    last_sync TEXT,
                    pair_count INTEGER DEFAULT 0,
                    status TEXT,
                    error_message TEXT,
                    PRIMARY KEY(exchange, market_type)
                )
                """);

            // Coins list cache (symbol → coingecko_id)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins_cache (
                    coingecko_id TEXT PRIMARY KEY,
                    symbol TEXT NOT NULL,
                    name TEXT NOT NULL,
                    last_updated TEXT
                )
                """);

            // Index for symbol lookup
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_coins_symbol
                ON coins_cache(symbol)
                """);
        }
    }

    /**
     * Migrate schema from one version to another.
     */
    private static void migrateSchema(Connection conn, int fromVersion, int toVersion) throws SQLException {
        for (int v = fromVersion + 1; v <= toVersion; v++) {
            switch (v) {
                // Add migrations here as needed
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
            stmt.execute("DROP TABLE IF EXISTS exchange_assets");
            stmt.execute("DROP TABLE IF EXISTS trading_pairs");
            stmt.execute("DROP TABLE IF EXISTS sync_metadata");
            stmt.execute("DROP TABLE IF EXISTS coins_cache");
        }
    }

    /**
     * Get statistics about the symbols database.
     */
    public static SymbolsStats getStats(Connection conn) throws SQLException {
        int assetCount = 0;
        int pairCount = 0;
        int exchangeCount = 0;

        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM exchange_assets")) {
                if (rs.next()) assetCount = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM trading_pairs")) {
                if (rs.next()) pairCount = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT exchange) FROM trading_pairs")) {
                if (rs.next()) exchangeCount = rs.getInt(1);
            }
        }

        return new SymbolsStats(assetCount, pairCount, exchangeCount);
    }

    /**
     * Statistics about the symbols database.
     */
    public record SymbolsStats(
        int assetCount,
        int pairCount,
        int exchangeCount
    ) {}
}
