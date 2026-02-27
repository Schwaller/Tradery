package com.tradery.dataservice.news;

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
 * DAO for RSS feed configuration stored in SQLite.
 * Uses the same news.db database as NewsArticleDao.
 */
public class FeedConfigDao {

    private static final Logger log = LoggerFactory.getLogger(FeedConfigDao.class);

    private final SqliteConnection conn;

    public FeedConfigDao(SqliteConnection conn) {
        this.conn = conn;
    }

    /**
     * Create the feeds table and seed built-in defaults.
     */
    public void createTable() throws SQLException {
        Connection c = conn.getConnection();
        try (var stmt = c.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS feeds (
                    source_id TEXT PRIMARY KEY,
                    source_name TEXT NOT NULL,
                    feed_url TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    built_in INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                ) WITHOUT ROWID
                """);
        }

        // Seed built-in feeds (idempotent)
        long now = System.currentTimeMillis();
        for (RssFeedPoller feed : RssFeedPoller.defaultFeeds()) {
            insert(new FeedConfigRecord(
                feed.getSourceId(), feed.getSourceName(), feed.getFeedUrl(),
                true, true, now
            ));
        }
    }

    /**
     * Get all feed configs.
     */
    public List<FeedConfigRecord> getAll() throws SQLException {
        Connection c = conn.getConnection();
        List<FeedConfigRecord> results = new ArrayList<>();
        String sql = "SELECT source_id, source_name, feed_url, enabled, built_in, created_at FROM feeds ORDER BY created_at";
        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    /**
     * Get only enabled feed configs.
     */
    public List<FeedConfigRecord> getEnabled() throws SQLException {
        Connection c = conn.getConnection();
        List<FeedConfigRecord> results = new ArrayList<>();
        String sql = "SELECT source_id, source_name, feed_url, enabled, built_in, created_at FROM feeds WHERE enabled = 1 ORDER BY created_at";
        try (PreparedStatement stmt = c.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    /**
     * Insert a feed config. Ignores duplicates by source_id.
     */
    public void insert(FeedConfigRecord record) throws SQLException {
        Connection c = conn.getConnection();
        String sql = """
            INSERT OR IGNORE INTO feeds (source_id, source_name, feed_url, enabled, built_in, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, record.sourceId());
            stmt.setString(2, record.sourceName());
            stmt.setString(3, record.feedUrl());
            stmt.setInt(4, record.enabled() ? 1 : 0);
            stmt.setInt(5, record.builtIn() ? 1 : 0);
            stmt.setLong(6, record.createdAt());
            stmt.executeUpdate();
        }
    }

    /**
     * Delete a feed config. Only allows deleting non-built-in feeds.
     * @return true if deleted, false if not found or built-in
     */
    public boolean delete(String sourceId) throws SQLException {
        Connection c = conn.getConnection();
        String sql = "DELETE FROM feeds WHERE source_id = ? AND built_in = 0";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, sourceId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Enable or disable a feed.
     * @return true if updated, false if not found
     */
    public boolean setEnabled(String sourceId, boolean enabled) throws SQLException {
        Connection c = conn.getConnection();
        String sql = "UPDATE feeds SET enabled = ? WHERE source_id = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setInt(1, enabled ? 1 : 0);
            stmt.setString(2, sourceId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Check if a feed is built-in.
     */
    public boolean isBuiltIn(String sourceId) throws SQLException {
        Connection c = conn.getConnection();
        String sql = "SELECT built_in FROM feeds WHERE source_id = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, sourceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private FeedConfigRecord mapRow(ResultSet rs) throws SQLException {
        return new FeedConfigRecord(
            rs.getString("source_id"),
            rs.getString("source_name"),
            rs.getString("feed_url"),
            rs.getInt("enabled") == 1,
            rs.getInt("built_in") == 1,
            rs.getLong("created_at")
        );
    }
}
