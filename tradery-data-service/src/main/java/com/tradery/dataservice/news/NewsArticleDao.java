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
 * DAO for news articles stored in SQLite.
 * Uses global database: ~/.tradery/data/__global/news.db
 */
public class NewsArticleDao {

    private static final Logger log = LoggerFactory.getLogger(NewsArticleDao.class);

    private final SqliteConnection conn;

    public NewsArticleDao(SqliteConnection conn) {
        this.conn = conn;
    }

    public void createTable() throws SQLException {
        Connection c = conn.getConnection();
        try (var stmt = c.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS articles (
                    id TEXT PRIMARY KEY,
                    source_id TEXT NOT NULL,
                    source_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT,
                    author TEXT,
                    source_url TEXT NOT NULL,
                    published_at INTEGER NOT NULL,
                    fetched_at INTEGER NOT NULL
                ) WITHOUT ROWID
                """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_articles_published_at
                ON articles (published_at)
                """);
        }
    }

    /**
     * Insert articles, ignoring duplicates by ID.
     */
    public int insertBatch(List<NewsArticleRecord> records) throws SQLException {
        if (records.isEmpty()) return 0;

        return conn.executeInTransaction(c -> {
            String sql = """
                INSERT OR IGNORE INTO articles
                (id, source_id, source_name, title, content, author, source_url, published_at, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            int inserted = 0;
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                for (NewsArticleRecord r : records) {
                    stmt.setString(1, r.id());
                    stmt.setString(2, r.sourceId());
                    stmt.setString(3, r.sourceName());
                    stmt.setString(4, r.title());
                    stmt.setString(5, r.content());
                    stmt.setString(6, r.author());
                    stmt.setString(7, r.sourceUrl());
                    stmt.setLong(8, r.publishedAt());
                    stmt.setLong(9, r.fetchedAt());
                    stmt.addBatch();

                    if (++inserted % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                int[] results = stmt.executeBatch();
                int count = 0;
                for (int r : results) {
                    if (r > 0) count++;
                }
                log.debug("Inserted {} of {} articles", count, records.size());
                return count;
            }
        });
    }

    /**
     * Get articles published since the given timestamp, newest first.
     */
    public List<NewsArticleRecord> querySince(long sinceEpochMs) throws SQLException {
        Connection c = conn.getConnection();
        List<NewsArticleRecord> results = new ArrayList<>();

        String sql = """
            SELECT id, source_id, source_name, title, content, author, source_url, published_at, fetched_at
            FROM articles
            WHERE published_at >= ?
            ORDER BY published_at DESC
            """;

        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setLong(1, sinceEpochMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    /**
     * Check if an article ID already exists.
     */
    public boolean exists(String id) throws SQLException {
        Connection c = conn.getConnection();
        try (PreparedStatement stmt = c.prepareStatement("SELECT 1 FROM articles WHERE id = ?")) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Count total articles.
     */
    public int count() throws SQLException {
        Connection c = conn.getConnection();
        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM articles");
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private NewsArticleRecord mapRow(ResultSet rs) throws SQLException {
        return new NewsArticleRecord(
            rs.getString("id"),
            rs.getString("source_id"),
            rs.getString("source_name"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("author"),
            rs.getString("source_url"),
            rs.getLong("published_at"),
            rs.getLong("fetched_at")
        );
    }
}
