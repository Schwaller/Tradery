package com.tradery.symbols.service;

import com.tradery.symbols.model.SymbolEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

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

            // Post-query: bulk lookup categories for all results
            if (!results.isEmpty()) {
                Set<String> coingeckoIds = new HashSet<>();
                for (SymbolEntry e : results) {
                    if (e.coingeckoId() != null && !e.coingeckoId().isEmpty()) {
                        coingeckoIds.add(e.coingeckoId());
                    }
                }
                if (!coingeckoIds.isEmpty()) {
                    Map<String, List<String>> categoryMap = getCategoriesForCoins(c, coingeckoIds);
                    if (!categoryMap.isEmpty()) {
                        List<SymbolEntry> enriched = new ArrayList<>(results.size());
                        for (SymbolEntry e : results) {
                            List<String> cats = e.coingeckoId() != null ? categoryMap.get(e.coingeckoId()) : null;
                            enriched.add(new SymbolEntry(e.symbol(), e.exchange(), e.marketType(),
                                e.base(), e.quote(), e.coingeckoId(),
                                cats != null ? cats : List.of()));
                        }
                        return enriched;
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search symbols", e);
        }
        return results;
    }

    /**
     * Bulk lookup categories for a set of coingecko IDs from coin_categories table.
     */
    private Map<String, List<String>> getCategoriesForCoins(Connection c, Collection<String> coingeckoIds) {
        Map<String, List<String>> result = new HashMap<>();
        if (coingeckoIds.isEmpty()) return result;

        // Check if coin_categories table exists
        try {
            ResultSet tables = c.getMetaData().getTables(null, null, "coin_categories", null);
            if (!tables.next()) return result;
        } catch (SQLException e) {
            return result;
        }

        List<String> idList = new ArrayList<>(coingeckoIds);
        try {
            for (int i = 0; i < idList.size(); i += 500) {
                List<String> chunk = idList.subList(i, Math.min(i + 500, idList.size()));
                String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
                String sql = "SELECT coingecko_id, category_name FROM coin_categories WHERE coingecko_id IN (" + placeholders + ")";

                try (PreparedStatement stmt = c.prepareStatement(sql)) {
                    for (int j = 0; j < chunk.size(); j++) {
                        stmt.setString(j + 1, chunk.get(j));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String id = rs.getString("coingecko_id");
                            String cat = rs.getString("category_name");
                            result.computeIfAbsent(id, k -> new ArrayList<>()).add(cat);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to lookup categories: {}", e.getMessage());
        }
        return result;
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
            rs.getString("coingecko_base_id"),
            List.of()
        );
    }

    /**
     * Info about a coin (base asset) for the coin picker combo.
     */
    public record CoinInfo(String base, String coinName, String coingeckoId) {}

    /**
     * A flat row for building the exchange × coin matrix.
     * Each row represents one trading pair on one exchange/market.
     */
    public record MatrixEntry(String base, String coinName, String coingeckoId,
                              String exchange, String marketType, String quote, String symbol,
                              double marketCapUsd, int marketCapRank) {}

    /**
     * Get trading pairs across all exchanges, optionally filtered by quote and search query.
     * Pass null for quote to get all quote currencies ("All" mode).
     */
    public List<MatrixEntry> getMatrix(String quote, String searchQuery, int limit) {
        List<MatrixEntry> results = new ArrayList<>();
        if (!isDatabaseAvailable()) return results;

        var sb = new StringBuilder("""
            SELECT tp.base_symbol, cc.name, tp.coingecko_base_id,
                   tp.exchange, tp.market_type, tp.quote_symbol, tp.symbol,
                   COALESCE(cc.market_cap_usd, 0) AS market_cap_usd,
                   COALESCE(cc.market_cap_rank, 0) AS market_cap_rank
            FROM trading_pairs tp
            LEFT JOIN coins_cache cc ON tp.coingecko_base_id = cc.coingecko_id
            WHERE tp.is_active = 1
            """);

        List<Object> params = new ArrayList<>();

        if (quote != null && !quote.isBlank()) {
            sb.append(" AND tp.quote_symbol = ?");
            params.add(quote);
        }

        if (searchQuery != null && !searchQuery.isBlank()) {
            sb.append(" AND (LOWER(tp.base_symbol) LIKE ? OR LOWER(tp.symbol) LIKE ? OR LOWER(cc.name) LIKE ?)");
            String pattern = "%" + searchQuery.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sb.append("""
             ORDER BY CASE WHEN cc.market_cap_rank IS NOT NULL AND cc.market_cap_rank > 0
                      THEN 0 ELSE 1 END,
                      cc.market_cap_rank,
                      tp.base_symbol, tp.exchange, tp.market_type, tp.quote_symbol
             LIMIT ?""");
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
                        results.add(new MatrixEntry(
                            rs.getString("base_symbol"),
                            rs.getString("name"),
                            rs.getString("coingecko_base_id"),
                            rs.getString("exchange"),
                            rs.getString("market_type"),
                            rs.getString("quote_symbol"),
                            rs.getString("symbol"),
                            rs.getDouble("market_cap_usd"),
                            rs.getInt("market_cap_rank")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get matrix data", e);
        }
        return results;
    }

    /**
     * Get distinct quote currencies across all exchanges.
     */
    public List<String> getAllQuoteCurrencies() {
        List<String> quotes = new ArrayList<>();
        if (!isDatabaseAvailable()) return quotes;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT DISTINCT quote_symbol FROM trading_pairs WHERE is_active = 1 ORDER BY quote_symbol");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) quotes.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Failed to get quote currencies", e);
        }
        return quotes;
    }

    /**
     * Get distinct coins (base assets) available on an exchange/market, with display names from coins_cache.
     */
    public List<CoinInfo> getCoins(String exchange, String market, int limit) {
        List<CoinInfo> results = new ArrayList<>();
        if (!isDatabaseAvailable()) return results;

        String sql = """
            SELECT DISTINCT tp.base_symbol, cc.name, tp.coingecko_base_id
            FROM trading_pairs tp
            LEFT JOIN coins_cache cc ON tp.coingecko_base_id = cc.coingecko_id
            WHERE tp.is_active = 1 AND tp.exchange = ? AND tp.market_type = ?
            ORDER BY tp.base_symbol LIMIT ?
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, exchange);
                stmt.setString(2, market);
                stmt.setInt(3, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new CoinInfo(
                            rs.getString("base_symbol"),
                            rs.getString("name"),
                            rs.getString("coingecko_base_id")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get coins for {} {}", exchange, market, e);
        }
        return results;
    }

    /**
     * Get available quote currencies for a specific base on an exchange/market.
     */
    public List<String> getQuoteCurrencies(String exchange, String market, String base) {
        List<String> quotes = new ArrayList<>();
        if (!isDatabaseAvailable()) return quotes;

        String sql = """
            SELECT DISTINCT quote_symbol FROM trading_pairs
            WHERE is_active = 1 AND exchange = ? AND market_type = ? AND base_symbol = ?
            ORDER BY quote_symbol
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, exchange);
                stmt.setString(2, market);
                stmt.setString(3, base);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        quotes.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get quote currencies for {} {} {}", exchange, market, base, e);
        }
        return quotes;
    }

    /**
     * Resolve base+quote to exchange-specific symbol.
     */
    public Optional<String> resolveToSymbol(String exchange, String market, String base, String quote) {
        if (!isDatabaseAvailable()) return Optional.empty();

        String sql = """
            SELECT symbol FROM trading_pairs
            WHERE is_active = 1 AND exchange = ? AND market_type = ?
              AND base_symbol = ? AND quote_symbol = ?
            LIMIT 1
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, exchange);
                stmt.setString(2, market);
                stmt.setString(3, base);
                stmt.setString(4, quote);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return Optional.of(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to resolve symbol for {} {} {}/{}", exchange, market, base, quote, e);
        }
        return Optional.empty();
    }

    public record SyncStatus(int pairCount, Instant lastSync) {}

    public record ExchangeCoverage(String exchange, String marketType, int pairCount, int matchedCount) {}

    public record ExchangeSyncInfo(String exchange, String marketType, Instant lastSync, String status) {}

    public record CategoryStats(int categoryCount, int categorizedCoins) {}

    /**
     * Get per-exchange/market pair counts with CoinGecko match rates.
     */
    public List<ExchangeCoverage> getExchangeCoverage() {
        List<ExchangeCoverage> results = new ArrayList<>();
        if (!isDatabaseAvailable()) return results;

        String sql = """
            SELECT exchange, market_type,
                   COUNT(*) as pair_count,
                   COUNT(CASE WHEN coingecko_base_id IS NOT NULL AND coingecko_base_id != '' THEN 1 END) as matched_count
            FROM trading_pairs WHERE is_active = 1
            GROUP BY exchange, market_type
            ORDER BY exchange, market_type
            """;

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new ExchangeCoverage(
                        rs.getString("exchange"),
                        rs.getString("market_type"),
                        rs.getInt("pair_count"),
                        rs.getInt("matched_count")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get exchange coverage", e);
        }
        return results;
    }

    /**
     * Get sync metadata per exchange/market from sync_metadata table.
     */
    public List<ExchangeSyncInfo> getSyncInfo() {
        List<ExchangeSyncInfo> results = new ArrayList<>();
        if (!isDatabaseAvailable()) return results;

        String sql = "SELECT exchange, market_type, last_sync, status FROM sync_metadata ORDER BY exchange, market_type";

        try {
            Connection c = getConnection();
            try (PreparedStatement stmt = c.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Instant lastSync = null;
                    String val = rs.getString("last_sync");
                    if (val != null) lastSync = Instant.parse(val);
                    results.add(new ExchangeSyncInfo(
                        rs.getString("exchange"),
                        rs.getString("market_type"),
                        lastSync,
                        rs.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get sync info", e);
        }
        return results;
    }

    /**
     * Get category sync statistics.
     */
    public CategoryStats getCategoryStats() {
        if (!isDatabaseAvailable()) return new CategoryStats(0, 0);

        try {
            Connection c = getConnection();

            // Check if table exists first
            try (ResultSet tables = c.getMetaData().getTables(null, null, "category_sync_metadata", null)) {
                if (!tables.next()) return new CategoryStats(0, 0);
            }

            int categoryCount = 0;
            try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM category_sync_metadata");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) categoryCount = rs.getInt(1);
            }

            int categorizedCoins = 0;
            try (ResultSet tables = c.getMetaData().getTables(null, null, "coin_categories", null)) {
                if (tables.next()) {
                    try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(DISTINCT coingecko_id) FROM coin_categories");
                         ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) categorizedCoins = rs.getInt(1);
                    }
                }
            }

            return new CategoryStats(categoryCount, categorizedCoins);
        } catch (SQLException e) {
            log.error("Failed to get category stats", e);
            return new CategoryStats(0, 0);
        }
    }
}
