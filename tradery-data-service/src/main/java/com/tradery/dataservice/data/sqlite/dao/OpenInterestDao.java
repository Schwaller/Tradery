package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.dataservice.data.sqlite.SqliteConnection;
import com.tradery.core.model.OpenInterest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for open interest data.
 * Open interest is available at 5-minute resolution (~8,640 records per month).
 */
public class OpenInterestDao {

    private static final Logger log = LoggerFactory.getLogger(OpenInterestDao.class);

    private final SqliteConnection conn;
    private final String symbol;

    public OpenInterestDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * Insert a single open interest record (upsert).
     */
    public void insert(OpenInterest oi) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO open_interest (timestamp, open_interest, open_interest_value)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, oi.timestamp());
            stmt.setDouble(2, oi.openInterest());
            stmt.setDouble(3, oi.openInterestValue());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple open interest records in a batch (much faster).
     */
    public int insertBatch(List<OpenInterest> records) throws SQLException {
        if (records.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO open_interest (timestamp, open_interest, open_interest_value)
                VALUES (?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (OpenInterest oi : records) {
                    stmt.setLong(1, oi.timestamp());
                    stmt.setDouble(2, oi.openInterest());
                    stmt.setDouble(3, oi.openInterestValue());
                    stmt.addBatch();

                    // Execute in batches of 1000
                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} open interest records for {}", records.size(), symbol);
            return records.size();
        });
    }

    /**
     * Query open interest in a time range.
     */
    public List<OpenInterest> query(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<OpenInterest> records = new ArrayList<>();

        String sql = """
            SELECT timestamp, open_interest, open_interest_value
            FROM open_interest
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new OpenInterest(
                        symbol,
                        rs.getLong("timestamp"),
                        rs.getDouble("open_interest"),
                        rs.getDouble("open_interest_value")
                    ));
                }
            }
        }

        return records;
    }

    /**
     * Get the most recent open interest record.
     */
    public OpenInterest getLatest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timestamp, open_interest, open_interest_value
            FROM open_interest
            ORDER BY timestamp DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new OpenInterest(
                    symbol,
                    rs.getLong("timestamp"),
                    rs.getDouble("open_interest"),
                    rs.getDouble("open_interest_value")
                );
            }
        }

        return null;
    }

    /**
     * Get the oldest open interest record.
     */
    public OpenInterest getOldest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timestamp, open_interest, open_interest_value
            FROM open_interest
            ORDER BY timestamp ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new OpenInterest(
                    symbol,
                    rs.getLong("timestamp"),
                    rs.getDouble("open_interest"),
                    rs.getDouble("open_interest_value")
                );
            }
        }

        return null;
    }

    /**
     * Count records in a time range.
     */
    public int countInRange(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM open_interest WHERE timestamp >= ? AND timestamp <= ?";

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
     * Count total records.
     */
    public int count() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM open_interest";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    /**
     * Delete all open interest records.
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM open_interest")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored data.
     */
    public long[] getTimeRange() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(timestamp), MAX(timestamp) FROM open_interest";

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
     * Find gaps in data (where expected 5-minute intervals are missing).
     *
     * @param startTime Start of range to check
     * @param endTime   End of range to check
     * @param intervalMs Expected interval between records (5 minutes = 300000ms)
     * @return List of [gapStart, gapEnd] pairs
     */
    public List<long[]> findGaps(long startTime, long endTime, long intervalMs) throws SQLException {
        Connection c = conn.getConnection();
        List<long[]> gaps = new ArrayList<>();

        // Allow some tolerance for interval variance
        long maxGap = intervalMs + (intervalMs / 10);

        String sql = """
            SELECT timestamp FROM open_interest
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

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
}
