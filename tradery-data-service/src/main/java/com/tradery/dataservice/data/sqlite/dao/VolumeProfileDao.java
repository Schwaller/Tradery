package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.dataservice.data.sqlite.SqliteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DAO for volume profile data.
 * Stores precomputed volume profiles at multiple timeframe levels (10s → 1d pyramid).
 * Each row contains a msgpack-encoded tick map: Map<int_tick, [buy_vol, sell_vol]>.
 *
 * Each DB file is scoped to a single market_type, so that column is NOT in the table.
 */
public class VolumeProfileDao {

    private static final Logger log = LoggerFactory.getLogger(VolumeProfileDao.class);

    private static final int BATCH_SIZE = 5000;

    private final SqliteConnection conn;
    private final String symbol;

    public VolumeProfileDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * A single volume profile row.
     * marketType is NOT stored in the table — it's implicit in the DB file.
     * The field is kept in the record for API compatibility with callers that need it.
     */
    public record ProfileRow(
        String timeframe,        // "10s","1m","5m","30m","1h","4h","1d"
        String marketType,       // "perp" or "spot" — NOT stored in DB, carried for callers
        long windowStart,        // epoch ms, aligned to boundary
        double tickSize,
        double totalBuyVolume,
        double totalSellVolume,
        int levelCount,
        byte[] profileData       // msgpack: Map<int_tick, [buy_vol, sell_vol]>
    ) {}

    /**
     * Upsert a single profile row.
     */
    public void upsert(ProfileRow row) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO volume_profiles
            (timeframe, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            setProfileParams(stmt, row);
            stmt.executeUpdate();
        }
    }

    /**
     * Batch upsert profile rows.
     */
    public int upsertBatch(List<ProfileRow> rows) throws SQLException {
        if (rows.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO volume_profiles
                (timeframe, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (ProfileRow row : rows) {
                    setProfileParams(stmt, row);
                    stmt.addBatch();

                    if (++count % BATCH_SIZE == 0) {
                        stmt.executeBatch();
                        log.trace("Inserted {} volume profile rows for {}", count, symbol);
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Upserted {} volume profile rows for {}", rows.size(), symbol);
            return rows.size();
        });
    }

    /**
     * Query profiles for a timeframe and time range.
     */
    public List<ProfileRow> query(String timeframe, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<ProfileRow> rows = new ArrayList<>();

        String sql = """
            SELECT timeframe, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data
            FROM volume_profiles
            WHERE timeframe = ? AND window_start >= ? AND window_start <= ?
            ORDER BY window_start
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(readRow(rs));
                }
            }
        }

        return rows;
    }

    /**
     * Backward-compatible query with marketType parameter (ignored in SQL, carried in ProfileRow).
     */
    public List<ProfileRow> query(String marketType, String timeframe, long startTime, long endTime) throws SQLException {
        // marketType is now implicit in the DB file — just delegate
        return query(timeframe, startTime, endTime);
    }

    /**
     * Stream profiles in chunks to avoid loading all into memory.
     */
    public int streamQuery(String timeframe, long startTime, long endTime, int chunkSize,
                           Consumer<List<ProfileRow>> chunkConsumer) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT timeframe, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data
            FROM volume_profiles
            WHERE timeframe = ? AND window_start >= ? AND window_start <= ?
            ORDER BY window_start
            """;

        int totalCount = 0;
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);
            stmt.setFetchSize(chunkSize);

            try (ResultSet rs = stmt.executeQuery()) {
                List<ProfileRow> chunk = new ArrayList<>(chunkSize);
                while (rs.next()) {
                    chunk.add(readRow(rs));
                    totalCount++;

                    if (chunk.size() >= chunkSize) {
                        chunkConsumer.accept(chunk);
                        chunk = new ArrayList<>(chunkSize);
                    }
                }
                if (!chunk.isEmpty()) {
                    chunkConsumer.accept(chunk);
                }
            }
        }

        return totalCount;
    }

    /**
     * Backward-compatible stream with marketType parameter.
     */
    public int streamQuery(String marketType, String timeframe, long startTime, long endTime, int chunkSize,
                           Consumer<List<ProfileRow>> chunkConsumer) throws SQLException {
        return streamQuery(timeframe, startTime, endTime, chunkSize, chunkConsumer);
    }

    /**
     * Delete profiles in a time range for a timeframe.
     */
    public int deleteRange(String timeframe, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            DELETE FROM volume_profiles
            WHERE timeframe = ? AND window_start >= ? AND window_start <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);
            return stmt.executeUpdate();
        }
    }

    /**
     * Backward-compatible delete with marketType parameter.
     */
    public int deleteRange(String marketType, String timeframe, long startTime, long endTime) throws SQLException {
        return deleteRange(timeframe, startTime, endTime);
    }

    /**
     * Count profiles for a timeframe and time range.
     */
    public long count(String timeframe, long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT COUNT(*) FROM volume_profiles
            WHERE timeframe = ? AND window_start >= ? AND window_start <= ?
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            stmt.setLong(2, startTime);
            stmt.setLong(3, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    /**
     * Backward-compatible count with marketType parameter.
     */
    public long count(String marketType, String timeframe, long startTime, long endTime) throws SQLException {
        return count(timeframe, startTime, endTime);
    }

    /**
     * Get available timeframes that have profile data.
     */
    public List<String> getAvailableTimeframes() throws SQLException {
        Connection c = conn.getConnection();
        List<String> tfs = new ArrayList<>();
        String sql = "SELECT DISTINCT timeframe FROM volume_profiles ORDER BY timeframe";
        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tfs.add(rs.getString(1));
            }
        }
        return tfs;
    }

    /**
     * Count all profiles for a timeframe (for inventory).
     */
    public long countAll(String timeframe) throws SQLException {
        Connection c = conn.getConnection();
        String sql = "SELECT COUNT(*) FROM volume_profiles WHERE timeframe = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    /**
     * Backward-compatible countAll with marketType parameter.
     */
    public long countAll(String marketType, String timeframe) throws SQLException {
        return countAll(timeframe);
    }

    /**
     * Get the time range across all data for a timeframe (for inventory).
     */
    public long[] getTimeRange(String timeframe) throws SQLException {
        Connection c = conn.getConnection();
        String sql = "SELECT MIN(window_start), MAX(window_start) FROM volume_profiles WHERE timeframe = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long min = rs.getLong(1);
                    if (rs.wasNull()) return null;
                    long max = rs.getLong(2);
                    return new long[]{min, max};
                }
            }
        }
        return null;
    }

    /**
     * Backward-compatible getTimeRange with marketType parameter.
     */
    public long[] getTimeRange(String marketType, String timeframe) throws SQLException {
        return getTimeRange(timeframe);
    }

    /**
     * Get the latest window_start for a timeframe.
     */
    public long getLatestWindowStart(String timeframe) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MAX(window_start) FROM volume_profiles WHERE timeframe = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, timeframe);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    /**
     * Backward-compatible getLatestWindowStart with marketType parameter.
     */
    public long getLatestWindowStart(String marketType, String timeframe) throws SQLException {
        return getLatestWindowStart(timeframe);
    }

    private void setProfileParams(PreparedStatement stmt, ProfileRow row) throws SQLException {
        stmt.setString(1, row.timeframe());
        stmt.setLong(2, row.windowStart());
        stmt.setDouble(3, row.tickSize());
        stmt.setDouble(4, row.totalBuyVolume());
        stmt.setDouble(5, row.totalSellVolume());
        stmt.setInt(6, row.levelCount());
        stmt.setBytes(7, row.profileData());
    }

    private ProfileRow readRow(ResultSet rs) throws SQLException {
        return new ProfileRow(
            rs.getString("timeframe"),
            null,  // marketType — not in table, callers set it from context
            rs.getLong("window_start"),
            rs.getDouble("tick_size"),
            rs.getDouble("total_buy_volume"),
            rs.getDouble("total_sell_volume"),
            rs.getInt("level_count"),
            rs.getBytes("profile_data")
        );
    }
}
