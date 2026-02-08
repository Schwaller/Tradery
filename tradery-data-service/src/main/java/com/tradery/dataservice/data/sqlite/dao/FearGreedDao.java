package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.core.model.FearGreedIndex;
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
 * DAO for Fear & Greed Index data.
 * Uses a dedicated __feargreed symbol database.
 * Daily data (~1 record per day, ~2900 total since Feb 2018).
 */
public class FearGreedDao {

    private static final Logger log = LoggerFactory.getLogger(FearGreedDao.class);

    private final SqliteConnection conn;

    public FearGreedDao(SqliteConnection conn) {
        this.conn = conn;
    }

    /**
     * Create the fear_greed table if it doesn't exist.
     */
    public void createTable() throws SQLException {
        Connection c = conn.getConnection();
        try (var stmt = c.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fear_greed (
                    timestamp INTEGER PRIMARY KEY,
                    value INTEGER NOT NULL,
                    classification TEXT NOT NULL
                ) WITHOUT ROWID
                """);
        }
    }

    /**
     * Insert multiple records in a batch (upsert).
     */
    public int insertBatch(List<FearGreedIndex> records) throws SQLException {
        if (records.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO fear_greed (timestamp, value, classification)
                VALUES (?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (FearGreedIndex record : records) {
                    stmt.setLong(1, record.timestamp());
                    stmt.setInt(2, record.value());
                    stmt.setString(3, record.classification());
                    stmt.addBatch();

                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} Fear & Greed records", records.size());
            return records.size();
        });
    }

    /**
     * Query records in a time range.
     */
    public List<FearGreedIndex> query(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<FearGreedIndex> results = new ArrayList<>();

        String sql = """
            SELECT timestamp, value, classification
            FROM fear_greed
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new FearGreedIndex(
                        rs.getInt("value"),
                        rs.getString("classification"),
                        rs.getLong("timestamp")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Query with lookback: includes records before startTime for averaging.
     */
    public List<FearGreedIndex> queryWithLookback(long startTime, long endTime, int lookbackDays) throws SQLException {
        // Get lookbackDays records before startTime
        long lookbackMs = lookbackDays * 86400000L;
        return query(startTime - lookbackMs, endTime);
    }

    /**
     * Get the most recent record.
     */
    public FearGreedIndex getLatest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timestamp, value, classification
            FROM fear_greed
            ORDER BY timestamp DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new FearGreedIndex(
                    rs.getInt("value"),
                    rs.getString("classification"),
                    rs.getLong("timestamp")
                );
            }
        }

        return null;
    }

    /**
     * Count total records.
     */
    public int count() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM fear_greed";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }
}
