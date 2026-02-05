package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.core.model.PremiumIndex;
import com.tradery.dataservice.data.sqlite.SqliteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for premium index kline data.
 * Premium index is stored per interval (1h, 5m, etc.) similar to candles.
 */
public class PremiumIndexDao {

    private static final Logger log = LoggerFactory.getLogger(PremiumIndexDao.class);

    private final SqliteConnection conn;
    private final String symbol;

    public PremiumIndexDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * Insert a single premium index record (upsert).
     */
    public void insert(String interval, PremiumIndex pi) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO premium_index
            (interval, open_time, open, high, low, close, close_time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);
            stmt.setLong(2, pi.openTime());
            stmt.setDouble(3, pi.open());
            stmt.setDouble(4, pi.high());
            stmt.setDouble(5, pi.low());
            stmt.setDouble(6, pi.close());
            stmt.setLong(7, pi.closeTime());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple premium index records in a batch (much faster).
     */
    public int insertBatch(String interval, List<PremiumIndex> records) throws SQLException {
        if (records.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO premium_index
                (interval, open_time, open, high, low, close, close_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (PremiumIndex pi : records) {
                    stmt.setString(1, interval);
                    stmt.setLong(2, pi.openTime());
                    stmt.setDouble(3, pi.open());
                    stmt.setDouble(4, pi.high());
                    stmt.setDouble(5, pi.low());
                    stmt.setDouble(6, pi.close());
                    stmt.setLong(7, pi.closeTime());
                    stmt.addBatch();

                    // Execute in batches of 1000
                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} premium index records ({}) for {}", records.size(), interval, symbol);
            return records.size();
        });
    }

    /**
     * Query premium index in a time range for a specific interval.
     */
    public List<PremiumIndex> query(String interval, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<PremiumIndex> records = new ArrayList<>();

        String sql = """
            SELECT open_time, open, high, low, close, close_time
            FROM premium_index
            WHERE interval = ? AND open_time >= ? AND open_time <= ?
            ORDER BY open_time
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new PremiumIndex(
                        rs.getLong("open_time"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("close_time")
                    ));
                }
            }
        }

        return records;
    }

    /**
     * Get the most recent premium index record for an interval.
     */
    public PremiumIndex getLatest(String interval) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT open_time, open, high, low, close, close_time
            FROM premium_index
            WHERE interval = ?
            ORDER BY open_time DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PremiumIndex(
                        rs.getLong("open_time"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("close_time")
                    );
                }
            }
        }

        return null;
    }

    /**
     * Get the oldest premium index record for an interval.
     */
    public PremiumIndex getOldest(String interval) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT open_time, open, high, low, close, close_time
            FROM premium_index
            WHERE interval = ?
            ORDER BY open_time ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PremiumIndex(
                        rs.getLong("open_time"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("close_time")
                    );
                }
            }
        }

        return null;
    }

    /**
     * Count records in a time range for an interval.
     */
    public int countInRange(String interval, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT COUNT(*) FROM premium_index
            WHERE interval = ? AND open_time >= ? AND open_time <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);
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
     * Count total records for an interval.
     */
    public int count(String interval) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM premium_index WHERE interval = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Delete all premium index records for an interval.
     */
    public void deleteAll(String interval) throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM premium_index WHERE interval = ?")) {
            stmt.setString(1, interval);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete all premium index records (all intervals).
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM premium_index")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored data for an interval.
     */
    public long[] getTimeRange(String interval) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(open_time), MAX(open_time) FROM premium_index WHERE interval = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, interval);

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
     * Get list of intervals that have data.
     */
    public List<String> getAvailableIntervals() throws SQLException {
        Connection c = conn.getConnection();
        List<String> intervals = new ArrayList<>();

        String sql = "SELECT DISTINCT interval FROM premium_index ORDER BY interval";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                intervals.add(rs.getString(1));
            }
        }

        return intervals;
    }
}
