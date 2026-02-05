package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.core.model.FundingRate;
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
 * DAO for funding rate data.
 * Funding rates occur every 8 hours (~3 per day, ~90 per month).
 */
public class FundingRateDao {

    private static final Logger log = LoggerFactory.getLogger(FundingRateDao.class);

    private final SqliteConnection conn;
    private final String symbol;

    public FundingRateDao(SqliteConnection conn) {
        this.conn = conn;
        this.symbol = conn.getSymbol();
    }

    /**
     * Insert a single funding rate (upsert).
     */
    public void insert(FundingRate rate) throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            INSERT OR REPLACE INTO funding_rates (funding_time, funding_rate, mark_price)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, rate.fundingTime());
            stmt.setDouble(2, rate.fundingRate());
            stmt.setDouble(3, rate.markPrice());
            stmt.executeUpdate();
        }
    }

    /**
     * Insert multiple funding rates in a batch (much faster).
     */
    public int insertBatch(List<FundingRate> rates) throws SQLException {
        if (rates.isEmpty()) {
            return 0;
        }

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR REPLACE INTO funding_rates (funding_time, funding_rate, mark_price)
                VALUES (?, ?, ?)
                """;

            int count = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (FundingRate rate : rates) {
                    stmt.setLong(1, rate.fundingTime());
                    stmt.setDouble(2, rate.fundingRate());
                    stmt.setDouble(3, rate.markPrice());
                    stmt.addBatch();

                    // Execute in batches of 1000
                    if (++count % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                stmt.executeBatch();
            }

            log.debug("Inserted {} funding rates for {}", rates.size(), symbol);
            return rates.size();
        });
    }

    /**
     * Query funding rates in a time range.
     */
    public List<FundingRate> query(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<FundingRate> rates = new ArrayList<>();

        String sql = """
            SELECT funding_time, funding_rate, mark_price
            FROM funding_rates
            WHERE funding_time >= ? AND funding_time <= ?
            ORDER BY funding_time
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rates.add(new FundingRate(
                        symbol,
                        rs.getDouble("funding_rate"),
                        rs.getLong("funding_time"),
                        rs.getDouble("mark_price")
                    ));
                }
            }
        }

        return rates;
    }

    /**
     * Query funding rates with one rate before startTime for lookback.
     */
    public List<FundingRate> queryWithLookback(long startTime, long endTime) throws SQLException {
        Connection c = conn.getConnection();
        List<FundingRate> rates = new ArrayList<>();

        // Get the most recent rate before startTime
        String lookbackSql = """
            SELECT funding_time, funding_rate, mark_price
            FROM funding_rates
            WHERE funding_time < ?
            ORDER BY funding_time DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(lookbackSql)) {
            stmt.setLong(1, startTime);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    rates.add(new FundingRate(
                        symbol,
                        rs.getDouble("funding_rate"),
                        rs.getLong("funding_time"),
                        rs.getDouble("mark_price")
                    ));
                }
            }
        }

        // Get rates in the requested range
        rates.addAll(query(startTime, endTime));
        return rates;
    }

    /**
     * Get the most recent funding rate.
     */
    public FundingRate getLatest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT funding_time, funding_rate, mark_price
            FROM funding_rates
            ORDER BY funding_time DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new FundingRate(
                    symbol,
                    rs.getDouble("funding_rate"),
                    rs.getLong("funding_time"),
                    rs.getDouble("mark_price")
                );
            }
        }

        return null;
    }

    /**
     * Get the oldest funding rate.
     */
    public FundingRate getOldest() throws SQLException {
        Connection c = conn.getConnection();

        String sql = """
            SELECT funding_time, funding_rate, mark_price
            FROM funding_rates
            ORDER BY funding_time ASC
            LIMIT 1
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new FundingRate(
                    symbol,
                    rs.getDouble("funding_rate"),
                    rs.getLong("funding_time"),
                    rs.getDouble("mark_price")
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

        String sql = "SELECT COUNT(*) FROM funding_rates WHERE funding_time >= ? AND funding_time <= ?";

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

        String sql = "SELECT COUNT(*) FROM funding_rates";

        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    /**
     * Delete all funding rates.
     */
    public void deleteAll() throws SQLException {
        Connection c = conn.getConnection();

        try (PreparedStatement stmt = c.prepareStatement("DELETE FROM funding_rates")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the time range of stored data.
     */
    public long[] getTimeRange() throws SQLException {
        Connection c = conn.getConnection();

        String sql = "SELECT MIN(funding_time), MAX(funding_time) FROM funding_rates";

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
}
