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
    public void insert(String timeframe, Candle candle) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO candles
            (timeframe, timestamp, open, high, low, close, volume,
             trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, candle.timestamp());
            stmt.setDouble(3, candle.open());
            stmt.setDouble(4, candle.high());
            stmt.setDouble(5, candle.low());
            stmt.setDouble(6, candle.close());
            stmt.setDouble(7, candle.volume());
            stmt.setInt(8, candle.tradeCount());
            stmt.setDouble(9, candle.quoteVolume());
            stmt.setDouble(10, candle.takerBuyVolume());
            stmt.setDouble(11, candle.takerBuyQuoteVolume());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple candles in a batch (much faster).
     */
    public int insertBatch(String timeframe, List<Candle> candles) throws SQLException {
        if (candles.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO candles
                (timeframe, timestamp, open, high, low, close, volume,
                 trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (Candle candle : candles) {
                    stmt.setString(1, timeframe);
                    stmt.setLong(2, candle.timestamp());
                    stmt.setDouble(3, candle.open());
                    stmt.setDouble(4, candle.high());
                    stmt.setDouble(5, candle.low());
                    stmt.setDouble(6, candle.close());
                    stmt.setDouble(7, candle.volume());
                    stmt.setInt(8, candle.tradeCount());
                    stmt.setDouble(9, candle.quoteVolume());
                    stmt.setDouble(10, candle.takerBuyVolume());
                    stmt.setDouble(11, candle.takerBuyQuoteVolume());
                    stmt.addBatch();

                    // Execute in batches of 1000
                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} candles ({}) for {}", candles.size(), timeframe, symbol);
            return candles.size();
        });
    }

    /**
     * Query candles in a time range for a specific timeframe.
     */
    public List<Candle> query(String timeframe, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

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
    public List<Candle> queryWithLimit(String timeframe, long startTime, long endTime, int limit)
            throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);
            stmt.setInt(4, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candles.add(readCandle(rs));
                }
            }
        }

        return candles;
    }

    /**
     * Get the most recent N candles for a timeframe.
     */
    public List<Candle> getLatest(String timeframe, int count) throws SQLException {
        Connection c = conn.getConnection();
        List<Candle> candles = new ArrayList<>();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setInt(2, count);

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
     * Get the most recent candle for a timeframe.
     */
    public Candle getLatest(String timeframe) throws SQLException {
        List<Candle> candles = getLatest(timeframe, 1);
        return candles.isEmpty() ? null : candles.get(0);
    }

    /**
     * Get the oldest candle for a timeframe.
     */
    public Candle getOldest(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timestamp, open, high, low, close, volume,
                   trade_count, quote_volume, taker_buy_volume, taker_buy_quote_volume
            FROM candles
            WHERE timeframe = ?
            ORDER BY timestamp ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readCandle(rs);
                }
            }
        }

        return null;
    }

    /**
     * Count candles in a time range for a timeframe.
     */
    public int countInRange(String timeframe, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT COUNT(*) FROM candles
            WHERE timeframe = ? AND timestamp >= ? AND timestamp <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Count total candles for a timeframe.
     */
    public int count(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM candles WHERE timeframe = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Delete all candles for a timeframe.
     */
    public void deleteAll(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM candles WHERE timeframe = ?")) {
            stmt.setString(1, timeframe);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete all candles (all timeframes).
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM candles")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored candles for a timeframe.
     */
    public long[] getTimeRange(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(timestamp), MAX(timestamp) FROM candles WHERE timeframe = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);

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
     * Get list of timeframes that have data.
     */
    public List<String> getAvailableTimeframes() throws SQLException {
        Connection c = conn.getConnection();
        List<String> timeframes = new ArrayList<>();

        String sql = "SELECT DISTINCT timeframe FROM candles ORDER BY timeframe";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                timeframes.add(rs.getString(1));
            }
        }

        return timeframes;
    }

    /**
     * Find gaps in candle data for a timeframe.
     *
     * @param timeframe  Candle timeframe (e.g., "1h")
     * @param startTime  Start of range to check
     * @param endTime    End of range to check
     * @param intervalMs Expected interval between candles
     * @return List of [gapStart, gapEnd] pairs
     */
    public List<long[]> findGaps(String timeframe, long startTime, long endTime, long intervalMs)
            throws SQLException {
        Connection c = conn.getConnection();
        List<long[]> gaps = new ArrayList<>();

        // Allow some tolerance for interval variance
        long maxGap = intervalMs + (intervalMs / 10);

        String sql = """
            SELECT timestamp FROM candles
            WHERE timeframe = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

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
     * Get statistics about candle data for a timeframe.
     */
    public CandleStats getStats(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT
                COUNT(*) as count,
                MIN(timestamp) as min_ts,
                MAX(timestamp) as max_ts,
                AVG(volume) as avg_volume
            FROM candles
            WHERE timeframe = ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CandleStats(
                        timeframe,
                        rs.getInt("count"),
                        rs.getLong("min_ts"),
                        rs.getLong("max_ts"),
                        rs.getDouble("avg_volume")
                    );
                }
            }
        }

        return new CandleStats(timeframe, 0, 0, 0, 0);
    }

    /**
     * Statistics about candle data.
     */
    public record CandleStats(
        String timeframe,
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
