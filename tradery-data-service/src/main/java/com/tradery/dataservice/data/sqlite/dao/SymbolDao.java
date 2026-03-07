package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.dataservice.coingecko.CoinGeckoClient.CoinMarketData;
import com.tradery.dataservice.coingecko.CoinInfo;
import com.tradery.dataservice.data.sqlite.SymbolsConnection;
import com.tradery.dataservice.symbols.ExchangeAsset;
import com.tradery.dataservice.symbols.MarketType;
import com.tradery.dataservice.symbols.TradingPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * DAO for symbol resolution operations.
 * Handles exchange assets and trading pairs.
 */
public class SymbolDao {

    private static final Logger log = LoggerFactory.getLogger(SymbolDao.class);

    private final SymbolsConnection conn;

    public SymbolDao(SymbolsConnection conn) {
        this.conn = conn;
    }

    // ==================== Exchange Assets ====================

    /**
     * Upsert an exchange asset.
     */
    public void upsertAsset(ExchangeAsset asset) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT INTO exchange_assets (exchange, symbol, coingecko_id, coin_name, is_active, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(exchange, symbol) DO UPDATE SET
                coingecko_id = excluded.coingecko_id,
                coin_name = excluded.coin_name,
                is_active = excluded.is_active,
                last_seen = excluded.last_seen
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, asset.exchange());
            stmt.setString(2, asset.symbol());
            stmt.setString(3, asset.coingeckoId());
            stmt.setString(4, asset.coinName());
            stmt.setInt(5, asset.isActive() ? 1 : 0);
            stmt.setString(6, asset.firstSeen().toString());
            stmt.setString(7, asset.lastSeen().toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Batch upsert exchange assets.
     */
    public int upsertAssetsBatch(List<ExchangeAsset> assets) throws SQLException {
        if (assets.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT INTO exchange_assets (exchange, symbol, coingecko_id, coin_name, is_active, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(exchange, symbol) DO UPDATE SET
                    coingecko_id = excluded.coingecko_id,
                    coin_name = excluded.coin_name,
                    is_active = excluded.is_active,
                    last_seen = excluded.last_seen
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (ExchangeAsset asset : assets) {
                    stmt.setString(1, asset.exchange());
                    stmt.setString(2, asset.symbol());
                    stmt.setString(3, asset.coingeckoId());
                    stmt.setString(4, asset.coinName());
                    stmt.setInt(5, asset.isActive() ? 1 : 0);
                    stmt.setString(6, asset.firstSeen().toString());
                    stmt.setString(7, asset.lastSeen().toString());
                    stmt.addBatch();

                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            return assets.size();
        });
    }

    /**
     * Find an asset by exchange and symbol.
     */
    public Optional<ExchangeAsset> findAsset(String exchange, String symbol) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT exchange, symbol, coingecko_id, coin_name, is_active, first_seen, last_seen
            FROM exchange_assets
            WHERE exchange = ? AND symbol = ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, exchange);
            stmt.setString(2, symbol);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readAsset(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find all assets by CoinGecko ID.
     */
    public List<ExchangeAsset> findByCoingeckoId(String coingeckoId) throws SQLException {
        Connection c = conn.getConnection();
        List<ExchangeAsset> assets = new ArrayList<>();

        String sql = """
            SELECT exchange, symbol, coingecko_id, coin_name, is_active, first_seen, last_seen
            FROM exchange_assets
            WHERE coingecko_id = ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, coingeckoId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assets.add(readAsset(rs));
                }
            }
        }
        return assets;
    }

    // ==================== Trading Pairs ====================

    /**
     * Upsert a trading pair.
     */
    public void upsertPair(TradingPair pair) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT INTO trading_pairs
            (exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(exchange, market_type, symbol) DO UPDATE SET
                base_symbol = excluded.base_symbol,
                quote_symbol = excluded.quote_symbol,
                coingecko_base_id = excluded.coingecko_base_id,
                coingecko_quote_id = excluded.coingecko_quote_id,
                is_active = excluded.is_active,
                last_seen = excluded.last_seen,
                tick_size = excluded.tick_size
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, pair.exchange());
            stmt.setString(2, pair.marketType().getValue());
            stmt.setString(3, pair.symbol());
            stmt.setString(4, pair.baseSymbol());
            stmt.setString(5, pair.quoteSymbol());
            stmt.setString(6, pair.coingeckoBaseId());
            stmt.setString(7, pair.coingeckoQuoteId());
            stmt.setInt(8, pair.isActive() ? 1 : 0);
            stmt.setString(9, pair.firstSeen().toString());
            stmt.setString(10, pair.lastSeen().toString());
            stmt.setDouble(11, pair.tickSize());
            stmt.executeUpdate();
        }
    }

    /**
     * Batch upsert trading pairs.
     */
    public int upsertPairsBatch(List<TradingPair> pairs) throws SQLException {
        if (pairs.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT INTO trading_pairs
                (exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(exchange, market_type, symbol) DO UPDATE SET
                    base_symbol = excluded.base_symbol,
                    quote_symbol = excluded.quote_symbol,
                    coingecko_base_id = excluded.coingecko_base_id,
                    coingecko_quote_id = excluded.coingecko_quote_id,
                    is_active = excluded.is_active,
                    last_seen = excluded.last_seen,
                    tick_size = excluded.tick_size
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (TradingPair pair : pairs) {
                    stmt.setString(1, pair.exchange());
                    stmt.setString(2, pair.marketType().getValue());
                    stmt.setString(3, pair.symbol());
                    stmt.setString(4, pair.baseSymbol());
                    stmt.setString(5, pair.quoteSymbol());
                    stmt.setString(6, pair.coingeckoBaseId());
                    stmt.setString(7, pair.coingeckoQuoteId());
                    stmt.setInt(8, pair.isActive() ? 1 : 0);
                    stmt.setString(9, pair.firstSeen().toString());
                    stmt.setString(10, pair.lastSeen().toString());
                    stmt.setDouble(11, pair.tickSize());
                    stmt.addBatch();

                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            return pairs.size();
        });
    }

    /**
     * Resolve canonical symbol to exchange-specific symbol.
     * Primary resolution: coingecko_base_id → exchange symbol
     *
     * @param canonical CoinGecko ID (e.g., "bitcoin") or ticker (e.g., "BTC")
     * @param exchange Target exchange (e.g., "okx")
     * @param marketType SPOT or PERP
     * @param quote Quote currency (e.g., "USDT")
     * @return Exchange-specific symbol (e.g., "BTC-USDT-SWAP")
     */
    public Optional<String> resolvePairSymbol(String canonical, String exchange, MarketType marketType, String quote)
            throws SQLException {
        Connection c = conn.getConnection();

        // First, try direct match on coingecko_base_id
        String sql = """
            SELECT symbol FROM trading_pairs
            WHERE coingecko_base_id = ? AND exchange = ? AND market_type = ? AND quote_symbol = ? AND is_active = 1
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, canonical.toLowerCase());
            stmt.setString(2, exchange);
            stmt.setString(3, marketType.getValue());
            stmt.setString(4, quote);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("symbol"));
                }
            }
        }

        // Fallback: try matching on base_symbol (case-insensitive)
        sql = """
            SELECT symbol FROM trading_pairs
            WHERE UPPER(base_symbol) = UPPER(?) AND exchange = ? AND market_type = ? AND quote_symbol = ? AND is_active = 1
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, canonical);
            stmt.setString(2, exchange);
            stmt.setString(3, marketType.getValue());
            stmt.setString(4, quote);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("symbol"));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Reverse resolve: exchange symbol → canonical info.
     *
     * @param exchangeSymbol Exchange-specific symbol (e.g., "BTC-USDT-SWAP")
     * @param exchange Exchange name
     * @return Trading pair with CoinGecko IDs
     */
    public Optional<TradingPair> reverseResolve(String exchangeSymbol, String exchange) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size
            FROM trading_pairs
            WHERE symbol = ? AND exchange = ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, exchangeSymbol);
            stmt.setString(2, exchange);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readPair(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Search trading pairs by query.
     *
     * @param query Search query (matches base_symbol, symbol, or coingecko_base_id)
     * @param exchange Optional exchange filter
     * @param limit Max results
     * @return Matching trading pairs
     */
    public List<TradingPair> searchPairs(String query, String exchange, int limit) throws SQLException {
        Connection c = conn.getConnection();
        List<TradingPair> pairs = new ArrayList<>();

        String queryPattern = "%" + query.toLowerCase() + "%";

        String sql;
        if (exchange != null && !exchange.isEmpty()) {
            sql = """
                SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size
                FROM trading_pairs
                WHERE exchange = ?
                  AND (LOWER(base_symbol) LIKE ? OR LOWER(symbol) LIKE ? OR LOWER(coingecko_base_id) LIKE ?)
                  AND is_active = 1
                ORDER BY base_symbol
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size
                FROM trading_pairs
                WHERE (LOWER(base_symbol) LIKE ? OR LOWER(symbol) LIKE ? OR LOWER(coingecko_base_id) LIKE ?)
                  AND is_active = 1
                ORDER BY base_symbol, exchange
                LIMIT ?
                """;
        }

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            int paramIdx = 1;
            if (exchange != null && !exchange.isEmpty()) {
                stmt.setString(paramIdx++, exchange);
            }
            stmt.setString(paramIdx++, queryPattern);
            stmt.setString(paramIdx++, queryPattern);
            stmt.setString(paramIdx++, queryPattern);
            stmt.setInt(paramIdx, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pairs.add(readPair(rs));
                }
            }
        }
        return pairs;
    }

    /**
     * Get all pairs for an exchange and market type.
     */
    public List<TradingPair> getPairsForExchange(String exchange, MarketType marketType) throws SQLException {
        Connection c = conn.getConnection();
        List<TradingPair> pairs = new ArrayList<>();

        String sql = """
            SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen, tick_size
            FROM trading_pairs
            WHERE exchange = ? AND market_type = ? AND is_active = 1
            ORDER BY base_symbol
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, exchange);
            stmt.setString(2, marketType.getValue());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pairs.add(readPair(rs));
                }
            }
        }
        return pairs;
    }

    // ==================== Coins Cache ====================

    /**
     * Upsert coins cache (from /coins/list endpoint).
     */
    public int upsertCoinsBatch(List<CoinInfo> coins) throws SQLException {
        if (coins.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT INTO coins_cache (coingecko_id, symbol, name, last_updated)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(coingecko_id) DO UPDATE SET
                    symbol = excluded.symbol,
                    name = excluded.name,
                    last_updated = excluded.last_updated
                """;

            int count = 0;
            String now = Instant.now().toString();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (CoinInfo coin : coins) {
                    stmt.setString(1, coin.id());
                    stmt.setString(2, coin.symbol());
                    stmt.setString(3, coin.name());
                    stmt.setString(4, now);
                    stmt.addBatch();

                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            return coins.size();
        });
    }

    /**
     * Update market cap data for coins in coins_cache.
     */
    public int updateMarketData(List<CoinMarketData> data) throws SQLException {
        if (data.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                UPDATE coins_cache SET market_cap_usd = ?, market_cap_rank = ?
                WHERE coingecko_id = ?
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (CoinMarketData d : data) {
                    stmt.setDouble(1, d.marketCapUsd());
                    stmt.setInt(2, d.marketCapRank());
                    stmt.setString(3, d.id());
                    stmt.addBatch();

                    if (++count % 500 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            return data.size();
        });
    }

    /**
     * Look up CoinGecko ID by ticker symbol.
     * Returns the most common/relevant ID if multiple matches.
     */
    public Optional<String> lookupCoingeckoId(String symbol) throws SQLException {
        Connection c = conn.getConnection();

        // Prefer the coin with the best market cap rank (legitimate coins are ranked)
        String sql = """
            SELECT coingecko_id FROM coins_cache
            WHERE LOWER(symbol) = LOWER(?)
            ORDER BY CASE WHEN market_cap_rank IS NOT NULL AND market_cap_rank > 0
                     THEN 0 ELSE 1 END,
                     market_cap_rank,
                     LENGTH(coingecko_id)
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, symbol);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("coingecko_id"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Load the best symbol → coingecko_id mapping from coins_cache.
     * For ambiguous symbols (multiple coins with same ticker), prefers the one with highest market cap rank.
     */
    public Map<String, String> loadSymbolToIdMap() throws SQLException {
        Connection c = conn.getConnection();
        Map<String, String> map = new HashMap<>();

        // Get the best coingecko_id per symbol, preferring ranked coins
        String sql = """
            SELECT symbol, coingecko_id FROM (
                SELECT symbol, coingecko_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY LOWER(symbol)
                           ORDER BY CASE WHEN market_cap_rank IS NOT NULL AND market_cap_rank > 0
                                    THEN 0 ELSE 1 END,
                                    market_cap_rank,
                                    LENGTH(coingecko_id)
                       ) AS rn
                FROM coins_cache
            ) WHERE rn = 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("symbol").toLowerCase(), rs.getString("coingecko_id"));
            }
        }
        return map;
    }

    /**
     * Get coin info by CoinGecko ID.
     */
    public Optional<CoinInfo> getCoin(String coingeckoId) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT coingecko_id, symbol, name FROM coins_cache WHERE coingecko_id = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, coingeckoId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CoinInfo(
                        rs.getString("coingecko_id"),
                        rs.getString("symbol"),
                        rs.getString("name")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    // ==================== Sync Metadata ====================

    /**
     * Update sync metadata for an exchange/market type.
     */
    public void updateSyncMetadata(String exchange, MarketType marketType, int pairCount, String status, String errorMessage)
            throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT INTO sync_metadata (exchange, market_type, last_sync, pair_count, status, error_message)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(exchange, market_type) DO UPDATE SET
                last_sync = excluded.last_sync,
                pair_count = excluded.pair_count,
                status = excluded.status,
                error_message = excluded.error_message
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, exchange);
            stmt.setString(2, marketType.getValue());
            stmt.setString(3, Instant.now().toString());
            stmt.setInt(4, pairCount);
            stmt.setString(5, status);
            stmt.setString(6, errorMessage);
            stmt.executeUpdate();
        }
    }

    /**
     * Get last sync time for an exchange/market type.
     */
    public Optional<Instant> getLastSyncTime(String exchange, MarketType marketType) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT last_sync FROM sync_metadata WHERE exchange = ? AND market_type = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, exchange);
            stmt.setString(2, marketType.getValue());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String lastSync = rs.getString("last_sync");
                    if (lastSync != null) {
                        return Optional.of(Instant.parse(lastSync));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Get all sync metadata.
     */
    public List<SyncMetadata> getAllSyncMetadata() throws SQLException {
        Connection c = conn.getConnection();
        List<SyncMetadata> metadata = new ArrayList<>();

        String sql = "SELECT exchange, market_type, last_sync, pair_count, status, error_message FROM sync_metadata";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                metadata.add(new SyncMetadata(
                    rs.getString("exchange"),
                    MarketType.fromString(rs.getString("market_type")),
                    rs.getString("last_sync") != null ? Instant.parse(rs.getString("last_sync")) : null,
                    rs.getInt("pair_count"),
                    rs.getString("status"),
                    rs.getString("error_message")
                ));
            }
        }
        return metadata;
    }

    // ==================== Coin Categories ====================

    /**
     * Clear all coins for a category (before re-inserting fresh data).
     */
    public void clearCategoryCoins(String categoryId) throws SQLException {
        Connection c = conn.getConnection();
        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM coin_categories WHERE category_id = ?")) {
            stmt.setString(1, categoryId);
            stmt.executeUpdate();
        }
    }

    /**
     * Batch upsert coin→category mappings.
     */
    public int upsertCoinCategoriesBatch(String categoryId, String categoryName, List<String> coingeckoIds) throws SQLException {
        if (coingeckoIds.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            // Clear existing mappings for this category
            try (PreparedStatement del = c.prepareStatement("DELETE FROM coin_categories WHERE category_id = ?")) {
                del.setString(1, categoryId);
                del.executeUpdate();
            }

            String sql = """
                INSERT OR REPLACE INTO coin_categories (coingecko_id, category_id, category_name)
                VALUES (?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (String coingeckoId : coingeckoIds) {
                    stmt.setString(1, coingeckoId);
                    stmt.setString(2, categoryId);
                    stmt.setString(3, categoryName);
                    stmt.addBatch();

                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }
            return coingeckoIds.size();
        });
    }

    /**
     * Bulk lookup categories for a set of coingecko IDs.
     * Returns map of coingecko_id → list of category display names.
     */
    public Map<String, List<String>> getCategoriesForCoins(Collection<String> coingeckoIds) throws SQLException {
        Map<String, List<String>> result = new HashMap<>();
        if (coingeckoIds.isEmpty()) return result;

        Connection c = conn.getConnection();

        // Process in chunks of 500 to avoid SQLite variable limits
        List<String> idList = new ArrayList<>(coingeckoIds);
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
        return result;
    }

    /**
     * Update category sync metadata.
     */
    public void updateCategorySyncMetadata(String categoryId, String categoryName, int coinCount) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT INTO category_sync_metadata (category_id, category_name, last_sync, coin_count)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(category_id) DO UPDATE SET
                category_name = excluded.category_name,
                last_sync = excluded.last_sync,
                coin_count = excluded.coin_count
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, categoryId);
            stmt.setString(2, categoryName);
            stmt.setString(3, Instant.now().toString());
            stmt.setInt(4, coinCount);
            stmt.executeUpdate();
        }
    }

    /**
     * Get all category sync metadata.
     */
    public List<CategorySyncMetadata> getCategorySyncMetadata() throws SQLException {
        Connection c = conn.getConnection();
        List<CategorySyncMetadata> metadata = new ArrayList<>();

        String sql = "SELECT category_id, category_name, last_sync, coin_count FROM category_sync_metadata ORDER BY category_name";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                metadata.add(new CategorySyncMetadata(
                    rs.getString("category_id"),
                    rs.getString("category_name"),
                    rs.getString("last_sync") != null ? Instant.parse(rs.getString("last_sync")) : null,
                    rs.getInt("coin_count")
                ));
            }
        }
        return metadata;
    }

    /**
     * Count total categorized coins (distinct coingecko IDs with at least one category).
     */
    public int countCategorizedCoins() throws SQLException {
        Connection c = conn.getConnection();
        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(DISTINCT coingecko_id) FROM coin_categories");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    // ==================== Statistics ====================

    /**
     * Get coverage statistics for all exchanges.
     */
    public List<ExchangeStats> getExchangeStats() throws SQLException {
        Connection c = conn.getConnection();
        List<ExchangeStats> stats = new ArrayList<>();

        String sql = """
            SELECT exchange, market_type, COUNT(*) as pair_count
            FROM trading_pairs
            WHERE is_active = 1
            GROUP BY exchange, market_type
            ORDER BY exchange, market_type
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                stats.add(new ExchangeStats(
                    rs.getString("exchange"),
                    MarketType.fromString(rs.getString("market_type")),
                    rs.getInt("pair_count")
                ));
            }
        }
        return stats;
    }

    /**
     * Count total trading pairs.
     */
    public int countPairs() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM trading_pairs WHERE is_active = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Count total unique assets.
     */
    public int countAssets() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM exchange_assets WHERE is_active = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Count coins in cache.
     */
    public int countCoins() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM coins_cache");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Get all exchange names that have sync metadata (includes dynamically discovered ones).
     */
    public Set<String> getSyncedExchanges() throws SQLException {
        Connection c = conn.getConnection();
        Set<String> exchanges = new LinkedHashSet<>();

        try (PreparedStatement stmt = c.prepareStatement("SELECT DISTINCT exchange FROM sync_metadata");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                exchanges.add(rs.getString("exchange"));
            }
        }
        return exchanges;
    }

    // ==================== Helpers ====================

    private ExchangeAsset readAsset(ResultSet rs) throws SQLException {
        return new ExchangeAsset(
            rs.getString("exchange"),
            rs.getString("symbol"),
            rs.getString("coingecko_id"),
            rs.getString("coin_name"),
            rs.getInt("is_active") == 1,
            Instant.parse(rs.getString("first_seen")),
            Instant.parse(rs.getString("last_seen"))
        );
    }

    private TradingPair readPair(ResultSet rs) throws SQLException {
        return new TradingPair(
            rs.getString("exchange"),
            MarketType.fromString(rs.getString("market_type")),
            rs.getString("symbol"),
            rs.getString("base_symbol"),
            rs.getString("quote_symbol"),
            rs.getString("coingecko_base_id"),
            rs.getString("coingecko_quote_id"),
            rs.getInt("is_active") == 1,
            Instant.parse(rs.getString("first_seen")),
            Instant.parse(rs.getString("last_seen")),
            rs.getDouble("tick_size")
        );
    }

    // ==================== Record Types ====================

    public record SyncMetadata(
        String exchange,
        MarketType marketType,
        Instant lastSync,
        int pairCount,
        String status,
        String errorMessage
    ) {}

    public record ExchangeStats(
        String exchange,
        MarketType marketType,
        int pairCount
    ) {}

    public record CategorySyncMetadata(
        String categoryId,
        String categoryName,
        Instant lastSync,
        int coinCount
    ) {}
}
