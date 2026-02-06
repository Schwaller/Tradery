package com.tradery.news.ui.coin;

import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for persistent storage of entities (coins, ETFs, VCs, exchanges)
 * and their relationships. Separates auto-fetched entities (CoinGecko) from manual ones.
 */
public class EntityStore {

    private static final String DB_PATH = System.getProperty("user.home") + "/.tradery/entity-network.db";
    private static final long COINGECKO_CACHE_TTL_MS = 6 * 60 * 60 * 1000L;  // 6 hours

    private Connection conn;

    public EntityStore() {
        try {
            new File(System.getProperty("user.home") + "/.tradery").mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            // WAL mode allows concurrent reads while writing
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=100");
            }
            createTables();
        } catch (SQLException e) {
            System.err.println("Failed to initialize entity store: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Main entities table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entities (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    symbol TEXT,
                    type TEXT NOT NULL,
                    parent_id TEXT,
                    market_cap REAL,
                    source TEXT NOT NULL DEFAULT 'manual',
                    created_at INTEGER,
                    updated_at INTEGER
                )
            """);

            // Categories (many-to-many)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entity_categories (
                    entity_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    PRIMARY KEY (entity_id, category),
                    FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE
                )
            """);

            // Relationships between entities
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS relationships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_id TEXT NOT NULL,
                    to_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    note TEXT,
                    source TEXT NOT NULL DEFAULT 'manual',
                    created_at INTEGER,
                    UNIQUE(from_id, to_id, type)
                )
            """);

            // Cache metadata
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cache_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """);

            // Schema types (dynamic entity and relationship type definitions)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_types (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    color TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    from_type_id TEXT,
                    to_type_id TEXT,
                    label TEXT,
                    display_order INTEGER DEFAULT 0
                )
            """);

            // Attributes per schema type
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_attributes (
                    type_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    data_type TEXT NOT NULL,
                    required INTEGER DEFAULT 0,
                    display_order INTEGER DEFAULT 0,
                    PRIMARY KEY (type_id, name),
                    FOREIGN KEY (type_id) REFERENCES schema_types(id) ON DELETE CASCADE
                )
            """);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_entities_source ON entities(source)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_from ON relationships(from_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_to ON relationships(to_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_relationships_source ON relationships(source)");
        }
    }

    // ==================== CACHE VALIDITY ====================

    public boolean isCoinGeckoCacheValid() {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM cache_meta WHERE key = 'coingecko_last_fetch'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long lastFetch = Long.parseLong(rs.getString("value"));
                return System.currentTimeMillis() - lastFetch < COINGECKO_CACHE_TTL_MS;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    public void updateCoinGeckoFetchTime() {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO cache_meta (key, value) VALUES ('coingecko_last_fetch', ?)")) {
            ps.setString(1, String.valueOf(System.currentTimeMillis()));
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to update cache time: " + e.getMessage());
        }
    }

    // ==================== ENTITY CRUD ====================

    public List<CoinEntity> loadAllEntities() {
        List<CoinEntity> entities = new ArrayList<>();
        if (conn == null) return entities;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM entities")) {
            while (rs.next()) {
                CoinEntity entity = mapEntityFromResultSet(rs);
                loadCategoriesFor(entity);
                entities.add(entity);
            }
        } catch (Exception e) {
            System.err.println("Failed to load entities: " + e.getMessage());
        }
        return entities;
    }

    public List<CoinEntity> loadEntitiesBySource(String source) {
        List<CoinEntity> entities = new ArrayList<>();
        if (conn == null) return entities;

        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM entities WHERE source = ?")) {
            ps.setString(1, source);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CoinEntity entity = mapEntityFromResultSet(rs);
                loadCategoriesFor(entity);
                entities.add(entity);
            }
        } catch (Exception e) {
            System.err.println("Failed to load entities by source: " + e.getMessage());
        }
        return entities;
    }

    public CoinEntity getEntity(String id) {
        if (conn == null) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM entities WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                CoinEntity entity = mapEntityFromResultSet(rs);
                loadCategoriesFor(entity);
                return entity;
            }
        } catch (Exception e) {
            System.err.println("Failed to get entity: " + e.getMessage());
        }
        return null;
    }

    private CoinEntity mapEntityFromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String symbol = rs.getString("symbol");
        String typeStr = rs.getString("type");
        String parentId = rs.getString("parent_id");
        double marketCap = rs.getDouble("market_cap");

        CoinEntity.Type type = CoinEntity.Type.valueOf(typeStr);
        CoinEntity entity = new CoinEntity(id, name, symbol, type, parentId);
        entity.setMarketCap(marketCap);
        return entity;
    }

    private void loadCategoriesFor(CoinEntity entity) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category FROM entity_categories WHERE entity_id = ?")) {
            ps.setString(1, entity.id());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entity.addCategory(rs.getString("category"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load categories for " + entity.id() + ": " + e.getMessage());
        }
    }

    public void saveEntity(CoinEntity entity, String source) {
        if (conn == null) return;

        try {
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO entities (id, name, symbol, type, parent_id, market_cap, source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    symbol = excluded.symbol,
                    type = excluded.type,
                    parent_id = excluded.parent_id,
                    market_cap = excluded.market_cap,
                    updated_at = excluded.updated_at
            """)) {
                ps.setString(1, entity.id());
                ps.setString(2, entity.name());
                ps.setString(3, entity.symbol());
                ps.setString(4, entity.type().name());
                ps.setString(5, entity.parentId());
                ps.setDouble(6, entity.marketCap());
                ps.setString(7, source);
                ps.setLong(8, now);
                ps.setLong(9, now);
                ps.execute();
            }

            // Update categories
            saveCategoriesFor(entity);

        } catch (SQLException e) {
            System.err.println("Failed to save entity: " + e.getMessage());
        }
    }

    private void saveCategoriesFor(CoinEntity entity) {
        try {
            // Delete existing categories
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM entity_categories WHERE entity_id = ?")) {
                ps.setString(1, entity.id());
                ps.execute();
            }

            // Insert new categories
            if (!entity.categories().isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO entity_categories (entity_id, category) VALUES (?, ?)")) {
                    for (String cat : entity.categories()) {
                        ps.setString(1, entity.id());
                        ps.setString(2, cat);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to save categories: " + e.getMessage());
        }
    }

    public void saveCoinGeckoEntities(List<CoinEntity> entities) {
        if (conn == null) return;

        try {
            conn.setAutoCommit(false);

            // Delete all coingecko entities (manual ones persist)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM entities WHERE source = 'coingecko'");
            }

            // Insert new coingecko entities
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO entities (id, name, symbol, type, parent_id, market_cap, source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'coingecko', ?, ?)
            """)) {
                for (CoinEntity entity : entities) {
                    ps.setString(1, entity.id());
                    ps.setString(2, entity.name());
                    ps.setString(3, entity.symbol());
                    ps.setString(4, entity.type().name());
                    ps.setString(5, entity.parentId());
                    ps.setDouble(6, entity.marketCap());
                    ps.setLong(7, now);
                    ps.setLong(8, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Save categories
            for (CoinEntity entity : entities) {
                if (!entity.categories().isEmpty()) {
                    saveCategoriesFor(entity);
                }
            }

            updateCoinGeckoFetchTime();
            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("Saved " + entities.size() + " CoinGecko entities to store");
        } catch (Exception e) {
            System.err.println("Failed to save CoinGecko entities: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }

    public void deleteEntity(String id) {
        if (conn == null) return;

        try {
            // Delete categories
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM entity_categories WHERE entity_id = ?")) {
                ps.setString(1, id);
                ps.execute();
            }

            // Delete relationships
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM relationships WHERE from_id = ? OR to_id = ?")) {
                ps.setString(1, id);
                ps.setString(2, id);
                ps.execute();
            }

            // Delete entity
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM entities WHERE id = ?")) {
                ps.setString(1, id);
                ps.execute();
            }
        } catch (SQLException e) {
            System.err.println("Failed to delete entity: " + e.getMessage());
        }
    }

    public boolean entityExists(String id) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM entities WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== RELATIONSHIP CRUD ====================

    public List<CoinRelationship> loadAllRelationships() {
        List<CoinRelationship> relationships = new ArrayList<>();
        if (conn == null) return relationships;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM relationships")) {
            while (rs.next()) {
                relationships.add(mapRelationshipFromResultSet(rs));
            }
        } catch (Exception e) {
            System.err.println("Failed to load relationships: " + e.getMessage());
        }
        return relationships;
    }

    public List<CoinRelationship> loadRelationshipsBySource(String source) {
        List<CoinRelationship> relationships = new ArrayList<>();
        if (conn == null) return relationships;

        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM relationships WHERE source = ?")) {
            ps.setString(1, source);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                relationships.add(mapRelationshipFromResultSet(rs));
            }
        } catch (Exception e) {
            System.err.println("Failed to load relationships by source: " + e.getMessage());
        }
        return relationships;
    }

    private CoinRelationship mapRelationshipFromResultSet(ResultSet rs) throws SQLException {
        String fromId = rs.getString("from_id");
        String toId = rs.getString("to_id");
        String typeStr = rs.getString("type");
        String note = rs.getString("note");

        CoinRelationship.Type type = CoinRelationship.Type.valueOf(typeStr);
        return new CoinRelationship(fromId, toId, type, note);
    }

    public void saveRelationship(CoinRelationship rel, String source) {
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO relationships (from_id, to_id, type, note, source, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(from_id, to_id, type) DO UPDATE SET
                note = excluded.note
        """)) {
            ps.setString(1, rel.fromId());
            ps.setString(2, rel.toId());
            ps.setString(3, rel.type().name());
            ps.setString(4, rel.note());
            ps.setString(5, source);
            ps.setLong(6, System.currentTimeMillis());
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to save relationship: " + e.getMessage());
        }
    }

    public void saveAutoRelationships(List<CoinRelationship> relationships) {
        if (conn == null) return;

        try {
            conn.setAutoCommit(false);

            // Delete auto-generated relationships (manual ones persist)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM relationships WHERE source = 'auto'");
            }

            // Insert new
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR IGNORE INTO relationships (from_id, to_id, type, note, source, created_at)
                VALUES (?, ?, ?, ?, 'auto', ?)
            """)) {
                for (CoinRelationship rel : relationships) {
                    ps.setString(1, rel.fromId());
                    ps.setString(2, rel.toId());
                    ps.setString(3, rel.type().name());
                    ps.setString(4, rel.note());
                    ps.setLong(5, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("Saved " + relationships.size() + " auto relationships");
        } catch (Exception e) {
            System.err.println("Failed to save auto relationships: " + e.getMessage());
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }

    public void deleteRelationship(String fromId, String toId, CoinRelationship.Type type) {
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM relationships WHERE from_id = ? AND to_id = ? AND type = ?")) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            ps.setString(3, type.name());
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to delete relationship: " + e.getMessage());
        }
    }

    public boolean relationshipExists(String fromId, String toId, CoinRelationship.Type type) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM relationships WHERE from_id = ? AND to_id = ? AND type = ?")) {
            ps.setString(1, fromId);
            ps.setString(2, toId);
            ps.setString(3, type.name());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ==================== STATS ====================

    public int getEntityCount() {
        if (conn == null) return 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entities")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public int getManualEntityCount() {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM entities WHERE source = 'manual'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public int getRelationshipCount() {
        if (conn == null) return 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM relationships")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public int getManualRelationshipCount() {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM relationships WHERE source = 'manual'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    // ==================== SCHEMA TYPE CRUD ====================

    public List<SchemaType> loadSchemaTypes() {
        List<SchemaType> types = new ArrayList<>();
        if (conn == null) return types;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM schema_types ORDER BY display_order")) {
            while (rs.next()) {
                SchemaType type = new SchemaType();
                type.setId(rs.getString("id"));
                type.setName(rs.getString("name"));
                type.setColor(SchemaType.parseColor(rs.getString("color")));
                type.setKind(rs.getString("kind"));
                type.setFromTypeId(rs.getString("from_type_id"));
                type.setToTypeId(rs.getString("to_type_id"));
                type.setLabel(rs.getString("label"));
                type.setDisplayOrder(rs.getInt("display_order"));

                // Load attributes
                loadSchemaAttributesFor(type);
                types.add(type);
            }
        } catch (Exception e) {
            System.err.println("Failed to load schema types: " + e.getMessage());
        }
        return types;
    }

    private void loadSchemaAttributesFor(SchemaType type) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM schema_attributes WHERE type_id = ? ORDER BY display_order")) {
            ps.setString(1, type.id());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                type.addAttribute(new SchemaAttribute(
                    rs.getString("name"),
                    rs.getString("data_type"),
                    rs.getInt("required") == 1,
                    rs.getInt("display_order")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load schema attributes for " + type.id() + ": " + e.getMessage());
        }
    }

    public void saveSchemaType(SchemaType type) {
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO schema_types (id, name, color, kind, from_type_id, to_type_id, label, display_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                color = excluded.color,
                kind = excluded.kind,
                from_type_id = excluded.from_type_id,
                to_type_id = excluded.to_type_id,
                label = excluded.label,
                display_order = excluded.display_order
        """)) {
            ps.setString(1, type.id());
            ps.setString(2, type.name());
            ps.setString(3, type.colorHex());
            ps.setString(4, type.kind());
            ps.setString(5, type.fromTypeId());
            ps.setString(6, type.toTypeId());
            ps.setString(7, type.label());
            ps.setInt(8, type.displayOrder());
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to save schema type: " + e.getMessage());
        }
    }

    public void deleteSchemaType(String id) {
        if (conn == null) return;

        try {
            // Delete attributes first
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM schema_attributes WHERE type_id = ?")) {
                ps.setString(1, id);
                ps.execute();
            }
            // Delete type
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM schema_types WHERE id = ?")) {
                ps.setString(1, id);
                ps.execute();
            }
        } catch (SQLException e) {
            System.err.println("Failed to delete schema type: " + e.getMessage());
        }
    }

    public void saveSchemaAttribute(String typeId, SchemaAttribute attr) {
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO schema_attributes (type_id, name, data_type, required, display_order)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(type_id, name) DO UPDATE SET
                data_type = excluded.data_type,
                required = excluded.required,
                display_order = excluded.display_order
        """)) {
            ps.setString(1, typeId);
            ps.setString(2, attr.name());
            ps.setString(3, attr.dataType());
            ps.setInt(4, attr.required() ? 1 : 0);
            ps.setInt(5, attr.displayOrder());
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to save schema attribute: " + e.getMessage());
        }
    }

    public void removeSchemaAttribute(String typeId, String attrName) {
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM schema_attributes WHERE type_id = ? AND name = ?")) {
            ps.setString(1, typeId);
            ps.setString(2, attrName);
            ps.execute();
        } catch (SQLException e) {
            System.err.println("Failed to remove schema attribute: " + e.getMessage());
        }
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
