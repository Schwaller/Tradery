package com.tradery.news.ui.coin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite cache for coin data to avoid hitting CoinGecko rate limits.
 */
public class CoinCache {

    private static final String DB_PATH = System.getProperty("user.home") + "/.tradery/coins.db";
    private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L;  // 6 hours

    private Connection conn;

    public CoinCache() {
        try {
            // Ensure directory exists
            new File(System.getProperty("user.home") + "/.tradery").mkdirs();

            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            createTables();
        } catch (SQLException e) {
            System.err.println("Failed to initialize coin cache: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS coins (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    symbol TEXT,
                    type TEXT,
                    parent_id TEXT,
                    market_cap REAL,
                    categories TEXT,
                    updated_at INTEGER
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cache_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """);
        }
    }

    public boolean isCacheValid() {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM cache_meta WHERE key = 'last_fetch'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long lastFetch = Long.parseLong(rs.getString("value"));
                return System.currentTimeMillis() - lastFetch < CACHE_TTL_MS;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    public List<CoinEntity> loadCoins() {
        List<CoinEntity> coins = new ArrayList<>();
        if (conn == null) return coins;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM coins")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String symbol = rs.getString("symbol");
                String typeStr = rs.getString("type");
                String parentId = rs.getString("parent_id");
                double marketCap = rs.getDouble("market_cap");
                String categoriesStr = rs.getString("categories");

                CoinEntity.Type type = CoinEntity.Type.valueOf(typeStr);
                CoinEntity entity = new CoinEntity(id, name, symbol, type, parentId);
                entity.setMarketCap(marketCap);

                if (categoriesStr != null && !categoriesStr.isEmpty()) {
                    for (String cat : categoriesStr.split("\\|")) {
                        entity.addCategory(cat);
                    }
                }
                coins.add(entity);
            }
        } catch (Exception e) {
            System.err.println("Failed to load coins from cache: " + e.getMessage());
        }
        return coins;
    }

    public void saveCoins(List<CoinEntity> coins) {
        if (conn == null) return;

        try {
            conn.setAutoCommit(false);

            // Clear existing
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM coins");
            }

            // Insert new
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO coins (id, name, symbol, type, parent_id, market_cap, categories, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                long now = System.currentTimeMillis();
                for (CoinEntity coin : coins) {
                    ps.setString(1, coin.id());
                    ps.setString(2, coin.name());
                    ps.setString(3, coin.symbol());
                    ps.setString(4, coin.type().name());
                    ps.setString(5, coin.parentId());
                    ps.setDouble(6, coin.marketCap());
                    ps.setString(7, String.join("|", coin.categories()));
                    ps.setLong(8, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Update meta
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO cache_meta (key, value) VALUES ('last_fetch', ?)")) {
                ps.setString(1, String.valueOf(System.currentTimeMillis()));
                ps.execute();
            }

            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("Saved " + coins.size() + " coins to cache");
        } catch (Exception e) {
            System.err.println("Failed to save coins to cache: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
