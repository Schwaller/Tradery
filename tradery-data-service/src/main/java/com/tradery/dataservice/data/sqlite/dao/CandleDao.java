package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.dataservice.data.sqlite.SqliteConnection;
import com.tradery.core.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for OHLCV candle data.
 * Stores candles for multiple timeframes in a single table.
 */
public class CandleDao {

    private static final Logger log = LoggerFactory.getLogger(CandleDao.class);

    private final SqliteConnection conn;
    private final String symbol;

    public CandleDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * Insert a single candle (upsert).
     */
    public void insert(String timeframe, String marketType, Candle candle) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO candles
            (timeframe, market_type, timestamp, open, high, low, close, volume,
             trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setLong(3, candle.timestamp());
            stmt.setDouble(4, candle.open());
            stmt.setDouble(5, candle.high());
            stmt.setDouble(6, candle.low());
            stmt.setDouble(7, candle.close());
            stmt.setDouble(8, candle.volume());
            stmt.setInt(9, candle.tradeCount());
            stmt.setDouble(10, candle.quoteVolume());
            stmt.setDouble(11, candle.takerBuyVolume());
            stmt.setDouble(12, candle.takerBuyQuoteVolume());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple candles in a batch (much faster).
     */
    public int insertBatch(String timeframe, String marketType, List<Candle> candles) throws SQLException {
        if (candles.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO candles
                (timeframe, market_type, timestamp, open, high, low, close, volume,
                 trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (Candle candle : candles) {
                    stmt.setString(1, timeframe);
                    stmt.setString(2, marketType);
                    stmt.setLong(3, candle.timestamp());
                    stmt.setDouble(4, candle.open());
                    stmt.setDouble(5, candle.high());
                    stmt.setDouble(6, candle.low());
                    stmt.setDouble(7, candle.close());
                    stmt.setDouble(8, candle.volume());
                    stmt.setInt(9, candle.tradeCount());
                    stmt.setDouble(10, candle.quoteVolume());
                    stmt.setDouble(11, candle.takerBuyVolume());
                    stmt.setDouble(12, candle.takerBuyQuoteVolume());
                    stmt.addBatch();

                    // Execute in batches of 1000
                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} candles ({}/{}) for {}", candles.size(), timeframe, marketType, symbol);
            return candles.size();
        });
    }

    /**
     * Query candles in a time range for a specific timeframe and market type.
     */
    public List<Candle> query(String timeframe, String marketType, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND market_type = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setLong(3, startTime);
            stmt.setLong(4, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candles.add(readCandle(rs));
                }
            }
        }

        return candles;
    }

    /**
     * Read a Candle from a ResultSet (helper method).
     */
    private Candle readCandle(ResultSet rs) throws SQLException {
        return new Candle(
            rs.getLong("timestamp"),
            rs.getDouble("open"),
            rs.getDouble("high"),
            rs.getDouble("low"),
            rs.getDouble("close"),
            rs.getDouble("volume"),
            rs.getInt("trade_count"),
            rs.getDouble("quote_volume"),
            rs.getDouble("taker_buy_volume"),
            rs.getDouble("taker_buy_quote_volume")
        );
    }

    /**
     * Query candles with a limit on results.
     */
    public List<Candle> queryWithLimit(String timeframe, String marketType, long startTime, long endTime, int limit)
            throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND market_type = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setLong(3, startTime);
            stmt.setLong(4, endTime);
            stmt.setInt(5, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candles.add(readCandle(rs));
                }
            }
        }

        return candles;
    }

    /**
     * Get the most recent N candles for a timeframe and market type.
     */
    public List<Candle> getLatest(String timeframe, String marketType, int count) throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND market_type = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setInt(3, count);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candles.add(readCandle(rs));
                }
            }
        }

        // Reverse to chronological order
        java.util.Collections.reverse(candles);
        return candles;
    }

    /**
     * Get the most recent candle for a timeframe and market type.
     */
    public Candle getLatest(String timeframe, String marketType) throws SQLException {
        List<Candle> candles = getLatest(timeframe, marketType, 1);
        return candles.isEmpty() ? null : candles.get(0);
    }

    /**
     * Get the oldest candle for a timeframe and market type.
     */
    public Candle getOldest(String timeframe, String marketType) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND market_type = ?
            ORDER BY timestamp ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readCandle(rs);
                }
            }
        }

        return null;
    }

    /**
     * Count candles in a time range for a timeframe and market type.
     */
    public int countInRange(String timeframe, String marketType, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT COUNT(*) FROM candles
            WHERE timeframe = ? AND market_type = ? AND timestamp >= ? AND timestamp <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setLong(3, startTime);
            stmt.setLong(4, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Count total candles for a timeframe and market type.
     */
    public int count(String timeframe, String marketType) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM candles WHERE timeframe = ? AND market_type = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Delete all candles for a timeframe and market type.
     */
    public void deleteAll(String timeframe, String marketType) throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM candles WHERE timeframe = ? AND market_type = ?")) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete all candles (all timeframes and market types).
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM candles")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored candles for a timeframe and market type.
     */
    public long[] getTimeRange(String timeframe, String marketType) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(timestamp), MAX(timestamp) FROM candles WHERE timeframe = ? AND market_type = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long min = rs.getLong(1);
                    long max = rs.getLong(2);
                    if (min > 0 && max > 0) {
                        return new long[]{min, max};
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get list of timeframes that have data for a market type.
     */
    public List<String> getAvailableTimeframes(String marketType) throws SQLException {
        Connection c = conn.getConnection();
        List<String> timeframes = new ArrayList<>();

        String sql = "SELECT DISTINCT timeframe FROM candles WHERE market_type = ? ORDER BY timeframe";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, marketType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    timeframes.add(rs.getString(1));
                }
            }
        }

        return timeframes;
    }

    /**
     * Find gaps in candle data for a timeframe and market type.
     *
     * @param timeframe  Candle timeframe (e.g., "1h")
     * @param marketType Market type (spot, perp, dated)
     * @param startTime  Start of range to check
     * @param endTime    End of range to check
     * @param intervalMs Expected interval between candles
     * @return List of [gapStart, gapEnd] pairs
     */
    public List<long[]> findGaps(String timeframe, String marketType, long startTime, long endTime, long intervalMs)
            throws SQLException {
        Connection c = conn.getConnection();
        List<long[]> gaps = new ArrayList<>();

        // Allow some tolerance for interval variance
        long maxGap = intervalMs + (intervalMs / 10);

        String sql = """
            SELECT timestamp FROM candles
            WHERE timeframe = ? AND market_type = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);
            stmt.setLong(3, startTime);
            stmt.setLong(4, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                long lastTs = startTime;
                boolean hasFirst = false;

                while (rs.next()) {
                    long ts = rs.getLong(1);

                    if (!hasFirst) {
                        // Check gap from start
                        if (ts - startTime > maxGap) {
                            gaps.add(new long[]{startTime, ts - intervalMs});
                        }
                        hasFirst = true;
                    } else if (ts - lastTs > maxGap) {
                        gaps.add(new long[]{lastTs + intervalMs, ts - intervalMs});
                    }

                    lastTs = ts;
                }

                // Check gap at the end
                if (!hasFirst) {
                    // No data at all
                    gaps.add(new long[]{startTime, endTime});
                } else if (endTime - lastTs > maxGap) {
                    gaps.add(new long[]{lastTs + intervalMs, endTime});
                }
            }
        }

        return gaps;
    }

    /**
     * Get statistics about candle data for a timeframe and market type.
     */
    public CandleStats getStats(String timeframe, String marketType) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT
                COUNT(*) as count,
                MIN(timestamp) as min_ts,
                MAX(timestamp) as max_ts,
                AVG(volume) as avg_volume
            FROM candles
            WHERE timeframe = ? AND market_type = ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setString(2, marketType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CandleStats(
                        timeframe,
                        marketType,
                        rs.getInt("count"),
                        rs.getLong("min_ts"),
                        rs.getLong("max_ts"),
                        rs.getDouble("avg_volume")
                    );
                }
            }
        }

        return new CandleStats(timeframe, marketType, 0, 0, 0, 0);
    }

    /**
     * Statistics about candle data.
     */
    public record CandleStats(
        String timeframe,
        String marketType,
        int count,
        long minTimestamp,
        long maxTimestamp,
        double avgVolume
    ) {
        public long durationMs() {
            return maxTimestamp - minTimestamp;
        }
    }
}
