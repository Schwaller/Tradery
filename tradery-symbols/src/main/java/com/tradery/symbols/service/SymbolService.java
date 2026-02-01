package com.tradery.symbols.service;

import com.tradery.symbols.model.SymbolEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Read-only service for querying the symbols database (~/.tradery/symbols.db).
 * Thread-safe — uses a single read-only WAL-mode connection.
 */
public class SymbolService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SymbolService.class);

    private final Path dbPath;
    private Connection connection;

    public SymbolService() {
        this(resolveDefaultDbPath());
    }

    /**
     * Resolve the symbols.db path, respecting the configurable data directory.
     * Reads ~/.tradery/data-location.txt if present, otherwise uses ~/.tradery/data/.
     */
    private static Path resolveDefaultDbPath() {
        Path userDir = Path.of(System.getProperty("user.home"), ".tradery");
        Path configFile = userDir.resolve("data-location.txt");
        try {
            if (Files.exists(configFile)) {
                String customPath = Files.readString(configFile).trim();
                if (!customPath.isEmpty()) {
                    Path customDir = Path.of(customPath);
                    if (Files.isDirectory(customDir)) {
                        return customDir.resolve("symbols.db");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore, use default
        }
        return userDir.resolve("data").resolve("symbols.db");
    }

    public SymbolService(Path dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Check if the database file exists and is readable.
     */
    public boolean isDatabaseAvailable() {
        return Files.isReadable(dbPath);
    }

    /**
     * Search trading pairs with optional filters.
     *
     * @param query      Text to match against base_symbol, symbol, or coingecko_base_id (nullable)
     * @param exchange   Exchange filter, e.g. "binance" (nullable for all)
     * @param marketType Market type filter, e.g. "spot" or "perp" (nullable for all)
     * @param limit      Max results to return
     * @return Matching symbol entries
     */
    public List<SymbolEntry> search(String query, String exchange, String marketType, int limit) {
        List<SymbolEntry> results = new ArrayList<>();
        if (!isDatabaseAvailable()) return results;

        var sb = new StringBuilder("""
            SELECT symbol, exchange, market_type, base_symbol, quote_symbol, coingecko_base_id
            FROM trading_pairs
            WHERE is_active = 1
            """);

        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            sb.append(" AND (LOWER(base_symbol) LIKE ? OR LOWER(symbol) LIKE ? OR LOWER(coingecko_base_id) LIKE ?)");
            String pattern = "%" + query.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (exchange != null && !exchange.isBlank()) {
            sb.append(" AND exchange = ?");
            params.add(exchange);
        }
        if (marketType != null && !marketType.isBlank()) {
            sb.append(" AND market_type = ?");
            params.add(marketType);
        }

        sb.append(" ORDER BY base_symbol, exchange LIMIT ?");
        params.add(limit);

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sb.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object p = params.get(i);
                    if (p instanceof String s) stmt.setString(i + 1, s);
                    else if (p instanceof Integer n) stmt.setInt(i + 1, n);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(readEntry(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search symbols", e);
        }
        return results;
    }

    /**
     * Resolve a canonical identifier (coingecko ID or base symbol) to a specific exchange pair.
     */
    public Optional<SymbolEntry> resolve(String canonical, String exchange, String marketType, String quote) {
        if (!isDatabaseAvailable()) return Optional.empty();

        // Try coingecko_base_id first
        String sql = """
            SELECT symbol, exchange, market_type, base_symbol, quote_symbol, coingecko_base_id
            FROM trading_pairs
            WHERE coingecko_base_id = ? AND exchange = ? AND market_type = ? AND quote_symbol = ? AND is_active = 1
            LIMIT 1
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, canonical.toLowerCase());
                stmt.setString(2, exchange);
                stmt.setString(3, marketType);
                stmt.setString(4, quote);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return Optional.of(readEntry(rs));
                }
            }

            // Fallback: base_symbol match
            sql = """
                SELECT symbol, exchange, market_type, base_symbol, quote_symbol, coingecko_base_id
                FROM trading_pairs
                WHERE UPPER(base_symbol) = UPPER(?) AND exchange = ? AND market_type = ? AND quote_symbol = ? AND is_active = 1
                LIMIT 1
                """;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                stmt.setString(2, exchange);
                stmt.setString(3, marketType);
                stmt.setString(4, quote);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return Optional.of(readEntry(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to resolve symbol: {}", canonical, e);
        }
        return Optional.empty();
    }

    /**
     * Reverse resolve an exchange-specific symbol back to a SymbolEntry.
     */
    public Optional<SymbolEntry> reverseResolve(String symbol, String exchange) {
        if (!isDatabaseAvailable()) return Optional.empty();

        String sql = """
            SELECT symbol, exchange, market_type, base_symbol, quote_symbol, coingecko_base_id
            FROM trading_pairs
            WHERE symbol = ? AND exchange = ?
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, symbol);
                stmt.setString(2, exchange);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return Optional.of(readEntry(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to reverse resolve: {} on {}", symbol, exchange, e);
        }
        return Optional.empty();
    }

    /**
     * Get sync status: total pair count and last sync time.
     */
    public SyncStatus getSyncStatus() {
        if (!isDatabaseAvailable()) return new SyncStatus(0, null);

        try {
            Connection c = getConnection();

            int pairCount = 0;
            try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM trading_pairs WHERE is_active = 1");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) pairCount = rs.getInt(1);
            }

            Instant lastSync = null;
            try (PreparedStatement stmt = c.prepareStatement("SELECT MAX(last_sync) FROM sync_metadata");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(1);
                    if (val != null) lastSync = Instant.parse(val);
                }
            }

            return new SyncStatus(pairCount, lastSync);
        } catch (SQLException e) {
            log.error("Failed to get sync status", e);
            return new SyncStatus(0, null);
        }
    }

    /**
     * Get distinct exchanges that have active pairs.
     */
    public List<String> getExchanges() {
        List<String> exchanges = new ArrayList<>();
        if (!isDatabaseAvailable()) return exchanges;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT DISTINCT exchange FROM trading_pairs WHERE is_active = 1 ORDER BY exchange");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    exchanges.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get exchanges", e);
        }
        return exchanges;
    }

    /**
     * Get distinct market types for an exchange (e.g. "spot", "perp").
     */
    public List<String> getMarketTypes(String exchange) {
        List<String> types = new ArrayList<>();
        if (!isDatabaseAvailable()) return types;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT DISTINCT market_type FROM trading_pairs WHERE is_active = 1 AND exchange = ? ORDER BY market_type")) {
                stmt.setString(1, exchange);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        types.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get market types for {}", exchange, e);
        }
        return types;
    }

    /**
     * Get symbols for a given exchange and market type.
     */
    public List<String> getSymbols(String exchange, String market, int limit) {
        List<String> symbols = new ArrayList<>();
        if (!isDatabaseAvailable()) return symbols;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT symbol FROM trading_pairs WHERE is_active = 1 AND exchange = ? AND market_type = ? ORDER BY symbol LIMIT ?")) {
                stmt.setString(1, exchange);
                stmt.setString(2, market);
                stmt.setInt(3, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        symbols.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get symbols for {} {}", exchange, market, e);
        }
        return symbols;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Failed to close symbols database connection", e);
            }
            connection = null;
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            Properties props = new Properties();
            props.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
            connection = DriverManager.getConnection(url, props);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA query_only=ON");
            }
        }
        return connection;
    }

    private SymbolEntry readEntry(ResultSet rs) throws SQLException {
        return new SymbolEntry(
            rs.getString("symbol"),
            rs.getString("exchange"),
            rs.getString("market_type"),
            rs.getString("base_symbol"),
            rs.getString("quote_symbol"),
            rs.getString("coingecko_base_id")
        );
    }

    public record SyncStatus(int pairCount, Instant lastSync) {}
}
