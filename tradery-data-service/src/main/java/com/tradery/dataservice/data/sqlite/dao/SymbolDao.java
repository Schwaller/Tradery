package com.tradery.dataservice.data.sqlite.dao;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            (exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(exchange, market_type, symbol) DO UPDATE SET
                base_symbol = excluded.base_symbol,
                quote_symbol = excluded.quote_symbol,
                coingecko_base_id = excluded.coingecko_base_id,
                coingecko_quote_id = excluded.coingecko_quote_id,
                is_active = excluded.is_active,
                last_seen = excluded.last_seen
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
                (exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(exchange, market_type, symbol) DO UPDATE SET
                    base_symbol = excluded.base_symbol,
                    quote_symbol = excluded.quote_symbol,
                    coingecko_base_id = excluded.coingecko_base_id,
                    coingecko_quote_id = excluded.coingecko_quote_id,
                    is_active = excluded.is_active,
                    last_seen = excluded.last_seen
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
            SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen
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
                SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen
                FROM trading_pairs
                WHERE exchange = ?
                  AND (LOWER(base_symbol) LIKE ? OR LOWER(symbol) LIKE ? OR LOWER(coingecko_base_id) LIKE ?)
                  AND is_active = 1
                ORDER BY base_symbol
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen
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
            SELECT exchange, market_type, symbol, base_symbol, quote_symbol, coingecko_base_id, coingecko_quote_id, is_active, first_seen, last_seen
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
     * Look up CoinGecko ID by ticker symbol.
     * Returns the most common/relevant ID if multiple matches.
     */
    public Optional<String> lookupCoingeckoId(String symbol) throws SQLException {
        Connection c = conn.getConnection();

        // Prioritize exact match
        String sql = """
            SELECT coingecko_id FROM coins_cache
            WHERE LOWER(symbol) = LOWER(?)
            ORDER BY LENGTH(coingecko_id)
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
            Instant.parse(rs.getString("last_seen"))
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
}
