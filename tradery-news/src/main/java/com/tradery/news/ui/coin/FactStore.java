package com.tradery.news.ui.coin;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Append-only fact store with materialized current-state table.
 * Every mutation becomes an immutable fact with provenance. The current state
 * is derived by resolving the latest fact per (entity_id, attribute) using LWW.
 */
public class FactStore {

    private static final String DB_PATH = System.getProperty("user.home") + "/.tradery/entity-network.db";

    private Connection conn;
    private String peerId;
    private long lclock;

    public FactStore() {
        try {
            new File(System.getProperty("user.home") + "/.tradery").mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=100");
            }

            // Check for old tables — if present, nuke the DB and start fresh
            if (hasOldTables()) {
                conn.close();
                new File(DB_PATH).delete();
                conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                try (Statement s = conn.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("PRAGMA busy_timeout=100");
                }
            }

            createTables();
            loadLocalConfig();
        } catch (SQLException e) {
            System.err.println("Failed to initialize fact store: " + e.getMessage());
        }
    }

    private boolean hasOldTables() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='entities'")) {
            return rs.next();
        }
    }

    private void createTables() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS facts (
                    id TEXT PRIMARY KEY,
                    entity_id TEXT NOT NULL,
                    attribute TEXT NOT NULL,
                    value TEXT,
                    source TEXT NOT NULL,
                    peer_id TEXT NOT NULL,
                    lclock INTEGER NOT NULL,
                    wall_clock INTEGER NOT NULL
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_entity ON facts(entity_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_lclock ON facts(lclock)");

            s.execute("""
                CREATE TABLE IF NOT EXISTS current (
                    entity_id TEXT NOT NULL,
                    attribute TEXT NOT NULL,
                    value TEXT,
                    fact_id TEXT NOT NULL,
                    PRIMARY KEY(entity_id, attribute)
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_current_attr_val ON current(attribute, value)");

            s.execute("""
                CREATE TABLE IF NOT EXISTS local_config (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);
        }
    }

    private void loadLocalConfig() throws SQLException {
        // Load or generate peer ID
        peerId = getConfigValue("peer_id");
        if (peerId == null) {
            peerId = Ulid.generate();
            setConfigValue("peer_id", peerId);
        }

        // Load Lamport clock
        String clockStr = getConfigValue("lclock");
        lclock = clockStr != null ? Long.parseLong(clockStr) : 0;
    }

    private String getConfigValue(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM local_config WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("value") : null;
        }
    }

    private void setConfigValue(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO local_config (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.execute();
        }
    }

    // ==================== APPEND FACTS ====================

    /** Append a single fact and update the current table. Returns the fact ID. */
    public String appendFact(String entityId, String attribute, String value, String source) {
        if (conn == null) return null;
        try {
            lclock++;
            String id = Ulid.generate();
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, entityId);
                ps.setString(3, attribute);
                ps.setString(4, value);
                ps.setString(5, source);
                ps.setString(6, peerId);
                ps.setLong(7, lclock);
                ps.setLong(8, wallClock);
                ps.execute();
            }

            updateCurrent(entityId, attribute, value, id, lclock, wallClock, peerId);
            persistClock();
            return id;
        } catch (SQLException e) {
            System.err.println("Failed to append fact: " + e.getMessage());
            return null;
        }
    }

    /** Batch append facts in a single transaction. */
    public void appendFacts(List<PendingFact> facts) {
        if (conn == null || facts.isEmpty()) return;
        try {
            conn.setAutoCommit(false);
            lclock++;
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (PendingFact f : facts) {
                    String id = Ulid.generate();
                    ps.setString(1, id);
                    ps.setString(2, f.entityId());
                    ps.setString(3, f.attribute());
                    ps.setString(4, f.value());
                    ps.setString(5, f.source());
                    ps.setString(6, peerId);
                    ps.setLong(7, lclock);
                    ps.setLong(8, wallClock);
                    ps.addBatch();

                    updateCurrent(f.entityId(), f.attribute(), f.value(), id, lclock, wallClock, peerId);
                }
                ps.executeBatch();
            }

            persistClock();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("Failed to append facts batch: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Update the current table for a (entity_id, attribute) pair.
     * Uses LWW: highest lclock wins, then wall_clock, then peer_id.
     */
    private void updateCurrent(String entityId, String attribute, String value,
                                String factId, long factLclock, long factWallClock, String factPeerId) throws SQLException {
        // Check existing current row
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT fact_id FROM current WHERE entity_id = ? AND attribute = ?")) {
            ps.setString(1, entityId);
            ps.setString(2, attribute);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                // No current row — insert
                insertCurrent(entityId, attribute, value, factId);
            } else {
                // Compare with existing fact
                String existingFactId = rs.getString("fact_id");
                if (factWins(factId, factLclock, factWallClock, factPeerId, existingFactId)) {
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE current SET value = ?, fact_id = ? WHERE entity_id = ? AND attribute = ?")) {
                        up.setString(1, value);
                        up.setString(2, factId);
                        up.setString(3, entityId);
                        up.setString(4, attribute);
                        up.execute();
                    }
                }
            }
        }
    }

    private void insertCurrent(String entityId, String attribute, String value, String factId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO current (entity_id, attribute, value, fact_id) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, entityId);
            ps.setString(2, attribute);
            ps.setString(3, value);
            ps.setString(4, factId);
            ps.execute();
        }
    }

    /**
     * Check if a new fact wins over the existing fact referenced by existingFactId.
     * For local single-peer appends, the new fact always wins (higher lclock).
     * For P2P, we compare lclock → wall_clock → peer_id.
     */
    private boolean factWins(String newFactId, long newLclock, long newWallClock, String newPeerId,
                              String existingFactId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT lclock, wall_clock, peer_id FROM facts WHERE id = ?")) {
            ps.setString(1, existingFactId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return true; // Existing fact not found, new wins

            long existLclock = rs.getLong("lclock");
            long existWallClock = rs.getLong("wall_clock");
            String existPeerId = rs.getString("peer_id");

            if (newLclock != existLclock) return newLclock > existLclock;
            if (newWallClock != existWallClock) return newWallClock > existWallClock;
            return newPeerId.compareTo(existPeerId) > 0;
        }
    }

    private void persistClock() throws SQLException {
        setConfigValue("lclock", String.valueOf(lclock));
    }

    // ==================== READ CURRENT STATE ====================

    /** Read current value for a single (entity_id, attribute). */
    public String getCurrent(String entityId, String attribute) {
        if (conn == null) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM current WHERE entity_id = ? AND attribute = ?")) {
            ps.setString(1, entityId);
            ps.setString(2, attribute);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("value") : null;
        } catch (SQLException e) {
            System.err.println("Failed to get current value: " + e.getMessage());
            return null;
        }
    }

    /** Read all current attributes for an entity. */
    public Map<String, String> getCurrentMap(String entityId) {
        Map<String, String> map = new LinkedHashMap<>();
        if (conn == null) return map;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT attribute, value FROM current WHERE entity_id = ?")) {
            ps.setString(1, entityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("attribute"), rs.getString("value"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get current map: " + e.getMessage());
        }
        return map;
    }

    /** Read current attributes matching a prefix (e.g., "cat:" for categories). */
    public Map<String, String> getCurrentByPrefix(String entityId, String prefix) {
        Map<String, String> map = new LinkedHashMap<>();
        if (conn == null) return map;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT attribute, value FROM current WHERE entity_id = ? AND attribute LIKE ?")) {
            ps.setString(1, entityId);
            ps.setString(2, prefix + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString("attribute"), rs.getString("value"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get current by prefix: " + e.getMessage());
        }
        return map;
    }

    /** Find entity IDs where a specific attribute has a specific value. */
    public List<String> findByAttribute(String attribute, String value) {
        List<String> ids = new ArrayList<>();
        if (conn == null) return ids;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT entity_id FROM current WHERE attribute = ? AND value = ?")) {
            ps.setString(1, attribute);
            ps.setString(2, value);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("entity_id"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to find by attribute: " + e.getMessage());
        }
        return ids;
    }

    /** Find entity IDs matching a LIKE pattern (e.g., '_type:%' or '_rel:%'). */
    public List<String> findByEntityIdPattern(String pattern) {
        List<String> ids = new ArrayList<>();
        if (conn == null) return ids;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT entity_id FROM current WHERE entity_id LIKE ?")) {
            ps.setString(1, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("entity_id"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to find by entity ID pattern: " + e.getMessage());
        }
        return ids;
    }

    /** Check if an entity has _deleted = '1'. */
    public boolean isDeleted(String entityId) {
        String val = getCurrent(entityId, "_deleted");
        return "1".equals(val);
    }

    /** Count entities where a specific attribute has a specific value, excluding deleted ones. */
    public int countByAttribute(String attribute, String value) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM current c1
            WHERE c1.attribute = ? AND c1.value = ?
            AND NOT EXISTS (
                SELECT 1 FROM current c2
                WHERE c2.entity_id = c1.entity_id AND c2.attribute = '_deleted' AND c2.value = '1'
            )
        """)) {
            ps.setString(1, attribute);
            ps.setString(2, value);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Failed to count by attribute: " + e.getMessage());
            return 0;
        }
    }

    /** Count entities matching attribute=value AND a second attribute=value, excluding deleted. */
    public int countByTwoAttributes(String attr1, String val1, String attr2, String val2) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM current c1
            JOIN current c2 ON c1.entity_id = c2.entity_id
            WHERE c1.attribute = ? AND c1.value = ?
            AND c2.attribute = ? AND c2.value = ?
            AND NOT EXISTS (
                SELECT 1 FROM current c3
                WHERE c3.entity_id = c1.entity_id AND c3.attribute = '_deleted' AND c3.value = '1'
            )
        """)) {
            ps.setString(1, attr1);
            ps.setString(2, val1);
            ps.setString(3, attr2);
            ps.setString(4, val2);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Failed to count by two attributes: " + e.getMessage());
            return 0;
        }
    }

    // ==================== P2P SUPPORT ====================

    /** Rebuild the entire current table from facts (disaster recovery / full sync). */
    public void rebuildCurrent() {
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM current");
            }

            // Process all facts ordered by resolution priority
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT id, entity_id, attribute, value, lclock, wall_clock, peer_id FROM facts ORDER BY lclock, wall_clock, peer_id")) {
                while (rs.next()) {
                    String factId = rs.getString("id");
                    String entityId = rs.getString("entity_id");
                    String attribute = rs.getString("attribute");
                    String value = rs.getString("value");

                    // For rebuild, just REPLACE — ordered processing ensures last writer wins
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO current (entity_id, attribute, value, fact_id) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, entityId);
                        ps.setString(2, attribute);
                        ps.setString(3, value);
                        ps.setString(4, factId);
                        ps.execute();
                    }
                }
            }

            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("Failed to rebuild current table: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Get facts since a logical clock value (for P2P sync). */
    public List<Fact> getFactsSince(long sinceLogicalClock) {
        List<Fact> facts = new ArrayList<>();
        if (conn == null) return facts;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM facts WHERE lclock > ? ORDER BY lclock")) {
            ps.setLong(1, sinceLogicalClock);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                facts.add(mapFact(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get facts since clock: " + e.getMessage());
        }
        return facts;
    }

    /** Receive remote facts (P2P sync) — inserts and re-resolves current. */
    public void receiveFacts(List<Fact> facts) {
        if (conn == null || facts.isEmpty()) return;
        try {
            conn.setAutoCommit(false);

            for (Fact f : facts) {
                // Update local Lamport clock
                if (f.lclock() >= lclock) {
                    lclock = f.lclock() + 1;
                }

                // Insert fact (ignore if already received)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, f.id());
                    ps.setString(2, f.entityId());
                    ps.setString(3, f.attribute());
                    ps.setString(4, f.value());
                    ps.setString(5, f.source());
                    ps.setString(6, f.peerId());
                    ps.setLong(7, f.lclock());
                    ps.setLong(8, f.wallClock());
                    ps.execute();
                }

                updateCurrent(f.entityId(), f.attribute(), f.value(), f.id(), f.lclock(), f.wallClock(), f.peerId());
            }

            persistClock();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("Failed to receive remote facts: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private Fact mapFact(ResultSet rs) throws SQLException {
        return new Fact(
            rs.getString("id"),
            rs.getString("entity_id"),
            rs.getString("attribute"),
            rs.getString("value"),
            rs.getString("source"),
            rs.getString("peer_id"),
            rs.getLong("lclock"),
            rs.getLong("wall_clock")
        );
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ==================== RECORDS ====================

    public record Fact(String id, String entityId, String attribute, String value,
                       String source, String peerId, long lclock, long wallClock) {}

    public record PendingFact(String entityId, String attribute, String value, String source) {}
}
