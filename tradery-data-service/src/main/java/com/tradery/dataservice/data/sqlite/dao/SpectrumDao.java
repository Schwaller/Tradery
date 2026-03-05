package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.core.model.SizeBucket;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.dataservice.data.sqlite.SqliteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * DAO for trade size spectrum data.
 * Stores pre-aggregated 10s histograms of trade notional distribution by log10 bucket.
 * Supports time-pyramid merging via GROUP BY for arbitrary timeframes.
 *
 * The `mode` column partitions raw vs taker-order-reconstructed spectrum data,
 * allowing both to coexist without re-backfilling on toggle.
 */
public class SpectrumDao {

    private static final Logger log = LoggerFactory.getLogger(SpectrumDao.class);
    private static final int BATCH_SIZE = 5000;

    private final SqliteConnection conn;
    private final String symbol;

    public SpectrumDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * Batch upsert spectrum rows for a given mode.
     */
    public int insertBatch(String mode, List<SpectrumRow> rows) throws SQLException {
        if (rows.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO trade_size_spectrum
                    (mode, window_start, bucket_index, trade_count, total_volume, buy_volume, sell_volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (SpectrumRow row : rows) {
                    stmt.setString(1, mode);
                    stmt.setLong(2, row.windowStart());
                    stmt.setInt(3, row.bucketIndex());
                    stmt.setInt(4, row.tradeCount());
                    stmt.setDouble(5, row.totalVolume());
                    stmt.setDouble(6, row.buyVolume());
                    stmt.setDouble(7, row.sellVolume());
                    stmt.addBatch();

                    if (++count % BATCH_SIZE == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} spectrum rows (mode={}) for {}", rows.size(), mode, symbol);
            return rows.size();
        });
    }

    /**
     * Batch upsert spectrum rows with default 'raw' mode (backward compat).
     */
    public int insertBatch(List<SpectrumRow> rows) throws SQLException {
        return insertBatch("raw", rows);
    }

    /**
     * Query flat distribution for a given mode.
     */
    public List<FlatBucket> queryFlat(String mode, long start, long end) throws SQLException {
        Connection c = conn.getConnection();
        List<FlatBucket> result = new ArrayList<>();

        String sql = """
            SELECT bucket_index,
                   SUM(trade_count) AS tc,
                   SUM(total_volume) AS tv,
                   SUM(buy_volume) AS bv,
                   SUM(sell_volume) AS sv
            FROM trade_size_spectrum
            WHERE mode = ? AND window_start >= ? AND window_start < ?
            GROUP BY bucket_index
            ORDER BY bucket_index
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, mode);
            stmt.setLong(2, start);
            stmt.setLong(3, end);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new FlatBucket(
                        rs.getInt("bucket_index"),
                        rs.getInt("tc"),
                        rs.getDouble("tv"),
                        rs.getDouble("bv"),
                        rs.getDouble("sv")
                    ));
                }
            }
        }

        return result;
    }

    /** Backward-compatible flat query (defaults to 'raw' mode). */
    public List<FlatBucket> queryFlat(long start, long end) throws SQLException {
        return queryFlat("raw", start, end);
    }

    /**
     * Query time-series for a given mode.
     */
    public List<AggregatedBucket> queryAggregated(String mode, long start, long end, long windowMs) throws SQLException {
        Connection c = conn.getConnection();
        List<AggregatedBucket> result = new ArrayList<>();

        String sql = """
            SELECT (window_start / ?) * ? AS period_start,
                   bucket_index,
                   SUM(trade_count) AS tc,
                   SUM(total_volume) AS tv,
                   SUM(buy_volume) AS bv,
                   SUM(sell_volume) AS sv
            FROM trade_size_spectrum
            WHERE mode = ? AND window_start >= ? AND window_start < ?
            GROUP BY period_start, bucket_index
            ORDER BY period_start, bucket_index
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, windowMs);
            stmt.setLong(2, windowMs);
            stmt.setString(3, mode);
            stmt.setLong(4, start);
            stmt.setLong(5, end);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new AggregatedBucket(
                        rs.getLong("period_start"),
                        rs.getInt("bucket_index"),
                        rs.getInt("tc"),
                        rs.getDouble("tv"),
                        rs.getDouble("bv"),
                        rs.getDouble("sv")
                    ));
                }
            }
        }

        return result;
    }

    /** Backward-compatible aggregated query (defaults to 'raw' mode). */
    public List<AggregatedBucket> queryAggregated(long start, long end, long windowMs) throws SQLException {
        return queryAggregated("raw", start, end, windowMs);
    }

    /**
     * Query raw 10s windows for page serving, filtered by mode.
     */
    public List<SpectrumWindow> queryWindows(String mode, long start, long end) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT window_start, bucket_index, trade_count, total_volume, buy_volume, sell_volume
            FROM trade_size_spectrum
            WHERE mode = ? AND window_start >= ? AND window_start < ?
            ORDER BY window_start, bucket_index
            """;

        Map<Long, SizeBucket[]> windowMap = new LinkedHashMap<>();

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, mode);
            stmt.setLong(2, start);
            stmt.setLong(3, end);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long ws = rs.getLong("window_start");
                    int bi = rs.getInt("bucket_index");
                    int tc = rs.getInt("trade_count");
                    double tv = rs.getDouble("total_volume");
                    double bv = rs.getDouble("buy_volume");
                    double sv = rs.getDouble("sell_volume");

                    SizeBucket[] buckets = windowMap.computeIfAbsent(ws, k -> {
                        SizeBucket[] arr = new SizeBucket[SpectrumWindow.BUCKET_COUNT];
                        Arrays.fill(arr, SizeBucket.EMPTY);
                        return arr;
                    });

                    if (bi >= 0 && bi < SpectrumWindow.BUCKET_COUNT) {
                        buckets[bi] = new SizeBucket(tc, tv, bv, sv);
                    }
                }
            }
        }

        List<SpectrumWindow> result = new ArrayList<>(windowMap.size());
        for (var entry : windowMap.entrySet()) {
            result.add(new SpectrumWindow(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** Backward-compatible window query (defaults to 'raw' mode). */
    public List<SpectrumWindow> queryWindows(long start, long end) throws SQLException {
        return queryWindows("raw", start, end);
    }

    /**
     * Count spectrum rows in a time range for a given mode.
     */
    public long countInRange(String mode, long start, long end) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT COUNT(*) FROM trade_size_spectrum WHERE mode = ? AND window_start >= ? AND window_start < ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, mode);
            stmt.setLong(2, start);
            stmt.setLong(3, end);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /** Backward-compatible count (defaults to 'raw' mode). */
    public long countInRange(long start, long end) throws SQLException {
        return countInRange("raw", start, end);
    }

    /**
     * Count total rows.
     */
    public long count() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM trade_size_spectrum");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Get the time range of stored data for a given mode.
     */
    public long[] getTimeRange(String mode) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(window_start), MAX(window_start) FROM trade_size_spectrum WHERE mode = ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, mode);
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

    /** Backward-compatible time range (defaults to 'raw' mode). */
    public long[] getTimeRange() throws SQLException {
        return getTimeRange("raw");
    }

    /**
     * Delete all spectrum data.
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();
        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM trade_size_spectrum")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Delete spectrum data in a time range for a given mode.
     */
    public void deleteInRange(String mode, long start, long end) throws SQLException {
        Connection c = conn.getConnection();

        String sql = "DELETE FROM trade_size_spectrum WHERE mode = ? AND window_start >= ? AND window_start < ?";

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, mode);
            stmt.setLong(2, start);
            stmt.setLong(3, end);
            stmt.executeUpdate();
        }
    }

    /** Backward-compatible delete (defaults to 'raw' mode). */
    public void deleteInRange(long start, long end) throws SQLException {
        deleteInRange("raw", start, end);
    }

    // ========== DTOs ==========

    /**
     * A single row in the spectrum table — one bucket within one window.
     */
    public record SpectrumRow(
        long windowStart,
        int bucketIndex,
        int tradeCount,
        double totalVolume,
        double buyVolume,
        double sellVolume
    ) {}

    /**
     * Flat (time-collapsed) bucket aggregation.
     */
    public record FlatBucket(
        int bucketIndex,
        int tradeCount,
        double totalVolume,
        double buyVolume,
        double sellVolume
    ) {
        public double delta() {
            return buyVolume - sellVolume;
        }

        public String label() {
            return SpectrumWindow.bucketLabel(bucketIndex);
        }
    }

    /**
     * Time-series aggregated bucket (grouped by period + bucket).
     */
    public record AggregatedBucket(
        long periodStart,
        int bucketIndex,
        int tradeCount,
        double totalVolume,
        double buyVolume,
        double sellVolume
    ) {
        public double delta() {
            return buyVolume - sellVolume;
        }
    }
}
