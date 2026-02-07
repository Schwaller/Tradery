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

/**
 * DAO for tracking data coverage ranges in the database.
 * Replaces .partial.csv tracking with proper gap detection.
 *
 * Data types:
 * - "candles" with sub_key = timeframe (e.g., "1h", "5m")
 * - "agg_trades" with sub_key = ""
 * - "funding_rates" with sub_key = ""
 * - "open_interest" with sub_key = ""
 * - "premium_index" with sub_key = interval (e.g., "1h", "5m")
 */
public class CoverageDao {

    private static final Logger log = LoggerFactory.getLogger(CoverageDao.class);

    private final SqliteConnection conn;

    public CoverageDao(SqliteConnection conn) {
        this.conn = conn;
    }

    /**
     * Add a coverage range, automatically merging with any adjacent or overlapping ranges.
     * This keeps the coverage table compact and avoids fragmentation.
     */
    public void addCoverage(String dataType, String subKey, long rangeStart, long rangeEnd,
                            boolean isComplete) throws SQLException {
        conn.executeInTransaction(c -> {
            // Find all ranges that overlap or are adjacent (within 1ms) to the new range
            List<CoverageRange> overlapping = new ArrayList<>();
            String selectSql = """
                SELECT range_start, range_end, is_complete, last_updated
                FROM data_coverage
                WHERE data_type = ? AND sub_key = ?
                  AND range_start <= ? AND range_end >= ?
                ORDER BY range_start
                """;

            try (PreparedStatement stmt = c.prepareStatement(selectSql)) {
                stmt.setString(1, dataType);
                stmt.setString(2, subKey);
                stmt.setLong(3, rangeEnd + 1);   // adjacent on the right
                stmt.setLong(4, rangeStart - 1); // adjacent on the left
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        overlapping.add(new CoverageRange(
                            rs.getLong("range_start"),
                            rs.getLong("range_end"),
                            rs.getInt("is_complete") == 1,
                            rs.getLong("last_updated")
                        ));
                    }
                }
            }

            // Compute merged range
            long mergedStart = rangeStart;
            long mergedEnd = rangeEnd;
            boolean mergedComplete = isComplete;
            for (CoverageRange r : overlapping) {
                mergedStart = Math.min(mergedStart, r.rangeStart());
                mergedEnd = Math.max(mergedEnd, r.rangeEnd());
                mergedComplete = mergedComplete && r.isComplete();
            }

            // Delete all overlapping/adjacent ranges
            if (!overlapping.isEmpty()) {
                String deleteSql = """
                    DELETE FROM data_coverage
                    WHERE data_type = ? AND sub_key = ?
                      AND range_start <= ? AND range_end >= ?
                    """;
                try (PreparedStatement stmt = c.prepareStatement(deleteSql)) {
                    stmt.setString(1, dataType);
                    stmt.setString(2, subKey);
                    stmt.setLong(3, rangeEnd + 1);
                    stmt.setLong(4, rangeStart - 1);
                    stmt.executeUpdate();
                }
            }

            // Insert the merged range
            String insertSql = """
                INSERT INTO data_coverage
                (data_type, sub_key, range_start, range_end, is_complete, last_updated)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = c.prepareStatement(insertSql)) {
                stmt.setString(1, dataType);
                stmt.setString(2, subKey);
                stmt.setLong(3, mergedStart);
                stmt.setLong(4, mergedEnd);
                stmt.setInt(5, mergedComplete ? 1 : 0);
                stmt.setLong(6, System.currentTimeMillis());
                stmt.executeUpdate();
            }

            if (overlapping.size() > 1) {
                log.debug("Coverage compacted: merged {} ranges into 1 for {}/{} [{} - {}]",
                    overlapping.size(), dataType, subKey, mergedStart, mergedEnd);
            }
        });
    }

    /**
     * Get all coverage ranges for a data type and sub key.
     */
    public List<CoverageRange> getCoverageRanges(String dataType, String subKey) throws SQLException {
        Connection c = conn.getConnection();
        List<CoverageRange> ranges = new ArrayList<>();

        String sql = """
            SELECT range_start, range_end, is_complete, last_updated
            FROM data_coverage
            WHERE data_type = ? AND sub_key = ?
            ORDER BY range_start
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, dataType);
            stmt.setString(2, subKey);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ranges.add(new CoverageRange(
                        rs.getLong("range_start"),
                        rs.getLong("range_end"),
                        rs.getInt("is_complete") == 1,
                        rs.getLong("last_updated")
                    ));
                }
            }
        }

        return ranges;
    }

    /**
     * Find gaps in coverage for a given time range.
     * Returns a list of [start, end] pairs representing missing data.
     * Triggers consolidation if too many fragmented ranges are found.
     */
    public List<long[]> findGaps(String dataType, String subKey, long start, long end) throws SQLException {
        List<CoverageRange> ranges = getCoverageRangesOverlapping(dataType, subKey, start, end);

        // Auto-consolidate if heavily fragmented
        if (ranges.size() > 50) {
            log.info("Coverage fragmented ({} ranges for {}/{}), consolidating...", ranges.size(), dataType, subKey);
            consolidateRanges(dataType, subKey);
            ranges = getCoverageRangesOverlapping(dataType, subKey, start, end);
            log.info("After consolidation: {} ranges", ranges.size());
        }

        List<long[]> gaps = new ArrayList<>();
        long cursor = start;

        for (CoverageRange range : ranges) {
            if (range.rangeStart() > cursor) {
                // Gap before this range
                gaps.add(new long[]{cursor, range.rangeStart() - 1});
            }
            cursor = Math.max(cursor, range.rangeEnd() + 1);
        }

        if (cursor <= end) {
            // Gap at the end
            gaps.add(new long[]{cursor, end});
        }

        return gaps;
    }

    /**
     * Get coverage ranges that overlap with the given time range.
     */
    private List<CoverageRange> getCoverageRangesOverlapping(String dataType, String subKey,
                                                              long start, long end) throws SQLException {
        Connection c = conn.getConnection();
        List<CoverageRange> ranges = new ArrayList<>();

        // Ranges overlap if: range_start <= end AND range_end >= start
        String sql = """
            SELECT range_start, range_end, is_complete, last_updated
            FROM data_coverage
            WHERE data_type = ? AND sub_key = ?
              AND range_start <= ? AND range_end >= ?
            ORDER BY range_start
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, dataType);
            stmt.setString(2, subKey);
            stmt.setLong(3, end);
            stmt.setLong(4, start);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ranges.add(new CoverageRange(
                        rs.getLong("range_start"),
                        rs.getLong("range_end"),
                        rs.getInt("is_complete") == 1,
                        rs.getLong("last_updated")
                    ));
                }
            }
        }

        return ranges;
    }

    /**
     * Check if a time range is fully covered (no gaps).
     */
    public boolean isFullyCovered(String dataType, String subKey, long start, long end) throws SQLException {
        List<long[]> gaps = findGaps(dataType, subKey, start, end);
        return gaps.isEmpty();
    }

    /**
     * Merge adjacent coverage ranges to reduce fragmentation.
     * Call this periodically or after large inserts.
     */
    public void consolidateRanges(String dataType, String subKey) throws SQLException {
        List<CoverageRange> ranges = getCoverageRanges(dataType, subKey);

        if (ranges.size() < 2) {
            return;
        }

        conn.executeInTransaction(c -> {
            // Delete all existing ranges for this type/key
            try (PreparedStatement stmt = c.prepareStatement(
                    "DELETE FROM data_coverage WHERE data_type = ? AND sub_key = ?")) {
                stmt.setString(1, dataType);
                stmt.setString(2, subKey);
                stmt.executeUpdate();
            }

            // Merge adjacent/overlapping ranges
            List<CoverageRange> merged = new ArrayList<>();
            CoverageRange current = ranges.get(0);

            for (int i = 1; i < ranges.size(); i++) {
                CoverageRange next = ranges.get(i);

                // Check if ranges are adjacent or overlapping (within 1ms)
                if (next.rangeStart() <= current.rangeEnd() + 1) {
                    // Merge: extend current range
                    current = new CoverageRange(
                        current.rangeStart(),
                        Math.max(current.rangeEnd(), next.rangeEnd()),
                        current.isComplete() && next.isComplete(),
                        Math.max(current.lastUpdated(), next.lastUpdated())
                    );
                } else {
                    // Gap between ranges - save current and start new
                    merged.add(current);
                    current = next;
                }
            }
            merged.add(current);

            // Insert merged ranges
            String insertSql = """
                INSERT OR REPLACE INTO data_coverage
                (data_type, sub_key, range_start, range_end, is_complete, last_updated)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = c.prepareStatement(insertSql)) {
                for (CoverageRange range : merged) {
                    stmt.setString(1, dataType);
                    stmt.setString(2, subKey);
                    stmt.setLong(3, range.rangeStart());
                    stmt.setLong(4, range.rangeEnd());
                    stmt.setInt(5, range.isComplete() ? 1 : 0);
                    stmt.setLong(6, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
            }

            log.debug("Consolidated {} ranges into {} for {}/{}", ranges.size(), merged.size(), dataType, subKey);
        });
    }

    /**
     * Delete coverage records for a data type (used when clearing cache).
     */
    public void deleteCoverage(String dataType, String subKey) throws SQLException {
        conn.executeInTransaction(c -> {
            try (PreparedStatement stmt = c.prepareStatement(
                    "DELETE FROM data_coverage WHERE data_type = ? AND sub_key = ?")) {
                stmt.setString(1, dataType);
                stmt.setString(2, subKey);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Delete all coverage records for a data type (all sub keys).
     */
    public void deleteAllCoverage(String dataType) throws SQLException {
        conn.executeInTransaction(c -> {
            try (PreparedStatement stmt = c.prepareStatement(
                    "DELETE FROM data_coverage WHERE data_type = ?")) {
                stmt.setString(1, dataType);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get a summary of coverage for a data type.
     */
    public CoverageSummary getCoverageSummary(String dataType, String subKey) throws SQLException {
        List<CoverageRange> ranges = getCoverageRanges(dataType, subKey);

        if (ranges.isEmpty()) {
            return new CoverageSummary(0, 0, 0, 0, 0);
        }

        long minStart = ranges.get(0).rangeStart();
        long maxEnd = ranges.get(ranges.size() - 1).rangeEnd();
        long totalCovered = 0;
        int completeRanges = 0;

        for (CoverageRange range : ranges) {
            totalCovered += (range.rangeEnd() - range.rangeStart() + 1);
            if (range.isComplete()) {
                completeRanges++;
            }
        }

        return new CoverageSummary(minStart, maxEnd, totalCovered, ranges.size(), completeRanges);
    }

    /**
     * Coverage range record.
     */
    public record CoverageRange(
        long rangeStart,
        long rangeEnd,
        boolean isComplete,
        long lastUpdated
    ) {
        public long duration() {
            return rangeEnd - rangeStart + 1;
        }
    }

    /**
     * Summary of coverage for a data type.
     */
    public record CoverageSummary(
        long minStart,
        long maxEnd,
        long totalCoveredMs,
        int rangeCount,
        int completeRangeCount
    ) {
        public double coveragePercent() {
            if (maxEnd <= minStart) {
                return 0;
            }
            long totalSpan = maxEnd - minStart;
            return (totalCoveredMs * 100.0) / totalSpan;
        }
    }
}
