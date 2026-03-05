package com.tradery.forge.data.sqlite.dao;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.forge.data.sqlite.SqliteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for aggregated trade data.
 * Each DAO instance wraps a single DB file for a specific exchange + market type.
 * Exchange and market type are implicit from the filename, not stored in columns.
 */
public class AggTradesDao {

    private static final Logger log = LoggerFactory.getLogger(AggTradesDao.class);

    // Batch size for bulk inserts
    private static final int BATCH_SIZE = 5000;

    private final SqliteConnection conn;
    private final String symbol;
    private final Exchange exchange;
    private final DataMarketType marketType;

    public AggTradesDao(SqliteConnection conn, Exchange exchange, DataMarketType marketType) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
        this.exchange = exchange;
        this.marketType = marketType;
    }

    /**
     * Insert a single aggregated trade.
     */
    public void insert(AggTrade trade) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO agg_trades
            (agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
             raw_symbol, normalized_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, trade.aggTradeId());
            stmt.setDouble(2, trade.price());
            stmt.setDouble(3, trade.quantity());
            stmt.setLong(4, trade.firstTradeId());
            stmt.setLong(5, trade.lastTradeId());
            stmt.setLong(6, trade.timestamp());
            stmt.setInt(7, trade.isBuyerMaker() ? 1 : 0);
            stmt.setString(8, trade.rawSymbol());
            stmt.setDouble(9, trade.normalizedPrice() > 0 ? trade.normalizedPrice() : trade.price());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple aggregated trades in a batch (much faster).
     * This is the critical method for performance with millions of records.
     */
    public int insertBatch(List<AggTrade> trades) throws SQLException {
        if (trades.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO agg_trades
                (agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
                 raw_symbol, normalized_price)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (AggTrade trade : trades) {
                    stmt.setLong(1, trade.aggTradeId());
                    stmt.setDouble(2, trade.price());
                    stmt.setDouble(3, trade.quantity());
                    stmt.setLong(4, trade.firstTradeId());
                    stmt.setLong(5, trade.lastTradeId());
                    stmt.setLong(6, trade.timestamp());
                    stmt.setInt(7, trade.isBuyerMaker() ? 1 : 0);
                    stmt.setString(8, trade.rawSymbol());
                    stmt.setDouble(9, trade.normalizedPrice() > 0 ? trade.normalizedPrice() : trade.price());
                    stmt.addBatch();

                    // Execute in batches
                    if (++count % BATCH_SIZE == 0) {
                        stmt.executeBatch();
                        log.trace("Inserted {} aggTrades batch for {}", count, symbol);
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} aggTrades for {}", trades.size(), symbol);
            return trades.size();
        });
    }

    /**
     * Query aggregated trades in a time range.
     */
    public List<AggTrade> query(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<AggTrade> trades = new ArrayList<>();

        String sql = """
            SELECT agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
                   raw_symbol, normalized_price
            FROM agg_trades
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp, agg_trade_id
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(parseAggTrade(rs));
                }
            }
        }

        return trades;
    }

    /**
     * Parse an AggTrade from a ResultSet.
     * Exchange and market type come from the DAO constructor (implicit in filename).
     */
    private AggTrade parseAggTrade(ResultSet rs) throws SQLException {
        long aggTradeId = rs.getLong("agg_trade_id");
        double price = rs.getDouble("price");
        double quantity = rs.getDouble("quantity");
        long firstTradeId = rs.getLong("first_trade_id");
        long lastTradeId = rs.getLong("last_trade_id");
        long timestamp = rs.getLong("timestamp");
        boolean isBuyerMaker = rs.getInt("is_buyer_maker") == 1;
        String rawSymbol = rs.getString("raw_symbol");
        double normalizedPrice = rs.getDouble("normalized_price");
        if (normalizedPrice <= 0) normalizedPrice = price;

        return new AggTrade(aggTradeId, price, quantity, firstTradeId, lastTradeId, timestamp, isBuyerMaker,
            exchange, marketType, rawSymbol, normalizedPrice);
    }

    /**
     * Query aggregated trades with a limit on results.
     */
    public List<AggTrade> queryWithLimit(long startTime, long endTime, int limit) throws SQLException {
        Connection c = conn.getConnection();
        List<AggTrade> trades = new ArrayList<>();

        String sql = """
            SELECT agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
                   raw_symbol, normalized_price
            FROM agg_trades
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp, agg_trade_id
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(parseAggTrade(rs));
                }
            }
        }

        return trades;
    }

    /**
     * Get the most recent N aggregated trades.
     */
    public List<AggTrade> getLatest(int count) throws SQLException {
        Connection c = conn.getConnection();
        List<AggTrade> trades = new ArrayList<>();

        String sql = """
            SELECT agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
                   raw_symbol, normalized_price
            FROM agg_trades
            ORDER BY timestamp DESC, agg_trade_id DESC
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setInt(1, count);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trades.add(parseAggTrade(rs));
                }
            }
        }

        // Reverse to chronological order
        java.util.Collections.reverse(trades);
        return trades;
    }

    /**
     * Get the most recent aggregated trade.
     */
    public AggTrade getLatest() throws SQLException {
        List<AggTrade> trades = getLatest(1);
        return trades.isEmpty() ? null : trades.get(0);
    }

    /**
     * Get the oldest aggregated trade.
     */
    public AggTrade getOldest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT agg_trade_id, price, quantity, first_trade_id, last_trade_id, timestamp, is_buyer_maker,
                   raw_symbol, normalized_price
            FROM agg_trades
            ORDER BY timestamp ASC, agg_trade_id ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return parseAggTrade(rs);
            }
        }

        return null;
    }

    /**
     * Count trades in a time range.
     */
    public int countInRange(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM agg_trades WHERE timestamp >= ? AND timestamp <= ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Count total trades.
     */
    public long count() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM agg_trades";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Delete all aggregated trades.
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM agg_trades")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Delete trades in a time range.
     */
    public int deleteInRange(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "DELETE FROM agg_trades WHERE timestamp >= ? AND timestamp <= ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);
            return stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored trades.
     */
    public long[] getTimeRange() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(timestamp), MAX(timestamp) FROM agg_trades";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                long min = rs.getLong(1);
                long max = rs.getLong(2);
                if (min > 0 && max > 0) {
                    return new long[]{min, max};
                }
            }
        }

        return null;
    }

    /**
     * Get the max agg_trade_id in a time range.
     * Useful for continuing fetches from where we left off.
     */
    public long getMaxAggTradeId(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MAX(agg_trade_id) FROM agg_trades WHERE timestamp >= ? AND timestamp <= ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Get overall max agg_trade_id.
     */
    public long getMaxAggTradeId() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MAX(agg_trade_id) FROM agg_trades";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Calculate buy/sell volume aggregates for a time range (for delta calculation).
     */
    public VolumeStats getVolumeStats(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT
                SUM(CASE WHEN is_buyer_maker = 0 THEN quantity ELSE 0 END) as buy_volume,
                SUM(CASE WHEN is_buyer_maker = 1 THEN quantity ELSE 0 END) as sell_volume,
                COUNT(*) as trade_count
            FROM agg_trades
            WHERE timestamp >= ? AND timestamp <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VolumeStats(
                        rs.getDouble("buy_volume"),
                        rs.getDouble("sell_volume"),
                        rs.getLong("trade_count")
                    );
                }
            }
        }

        return new VolumeStats(0, 0, 0);
    }

    /**
     * Volume statistics.
     */
    public record VolumeStats(
        double buyVolume,
        double sellVolume,
        long tradeCount
    ) {
        public double delta() {
            return buyVolume - sellVolume;
        }

        public double totalVolume() {
            return buyVolume + sellVolume;
        }
    }

    /**
     * Check if we have data for an hour (for hour-based completeness check).
     */
    public boolean hasDataForHour(long hourStartTime) throws SQLException {
        Connection c = conn.getConnection();

        long hourEndTime = hourStartTime + (60 * 60 * 1000) - 1;

        String sql = "SELECT 1 FROM agg_trades WHERE timestamp >= ? AND timestamp <= ? LIMIT 1";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, hourStartTime);
            stmt.setLong(2, hourEndTime);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
