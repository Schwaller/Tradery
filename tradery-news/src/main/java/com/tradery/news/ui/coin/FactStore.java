package com.tradery.news.ui.coin;

import java.io.File;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Append-only fact store with materialized current-state table.
 * Every mutation becomes an immutable fact with provenance. The current state
 * is derived by resolving the latest fact per (entity_id, attribute) using LWW.
 */
public class FactStore {

    private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".tradery", "entity-network.db");

    private final Path dbPath;
    private Connection conn;
    private String peerId;
    private long lclock;

    public FactStore() {
        this(DEFAULT_PATH);
    }

    public FactStore(Path dbPath) {
        this.dbPath = dbPath;
        try {
            dbPath.getParent().toFile().mkdirs();
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=100");
            }

            // If schema is outdated, nuke the DB and start fresh
            if (needsReset()) {
                conn.close();
                dbPath.toFile().delete();
                conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                try (Statement s = conn.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("PRAGMA busy_timeout=100");
                }
            }

            createTables();
            loadLocalConfig();
        } catch (SQLException e) {
            System.err.println("Failed to initialize fact store: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Path defaultPath() {
        return DEFAULT_PATH;
    }

    /** The local peer ID (ULID, unique per FactStore instance). */
    public String peerId() { return peerId; }

    /** The current Lamport logical clock value. */
    public long lclock() { return lclock; }

    /** Read a value from local_config. Returns null if not found or on error. */
    public String getLocalConfig(String key) {
        try { return getConfigValue(key); }
        catch (SQLException e) { return null; }
    }

    /** Write a value to local_config. Silently ignores errors. */
    public void setLocalConfig(String key, String value) {
        try { setConfigValue(key, value); }
        catch (SQLException ignored) {}
    }

    /** Delete a key from local_config. Silently ignores errors. */
    public void deleteLocalConfig(String key) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM local_config WHERE key = ?")) {
            ps.setString(1, key);
            ps.execute();
        } catch (SQLException ignored) {}
    }

    // ==================== ENTITY ACCEPTANCE (USER_CURATED) ====================

    private static final String ACCEPTED_PREFIX = "accepted:";

    /** Mark an entity as accepted in the local view (USER_CURATED mode). */
    public void acceptEntity(String entityId) {
        setLocalConfig(ACCEPTED_PREFIX + entityId, "1");
    }

    /** Remove an entity from the local accepted set (USER_CURATED mode). */
    public void unacceptEntity(String entityId) {
        deleteLocalConfig(ACCEPTED_PREFIX + entityId);
    }

    /** Check if an entity is accepted in the local view. */
    public boolean isEntityAccepted(String entityId) {
        return "1".equals(getLocalConfig(ACCEPTED_PREFIX + entityId));
    }

    /** Get all accepted entity IDs. */
    public Set<String> getAcceptedEntityIds() {
        Set<String> ids = new HashSet<>();
        if (conn == null) return ids;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT key FROM local_config WHERE key LIKE ?")) {
            ps.setString(1, ACCEPTED_PREFIX + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("key").substring(ACCEPTED_PREFIX.length()));
            }
        } catch (SQLException ignored) {}
        return ids;
    }

    /** Batch-accept multiple entities. */
    public void acceptEntities(Collection<String> entityIds) {
        if (conn == null || entityIds.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO local_config (key, value) VALUES (?, '1')")) {
            for (String entityId : entityIds) {
                ps.setString(1, ACCEPTED_PREFIX + entityId);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ignored) {}
    }

    /** Count accepted entities. */
    public int getAcceptedCount() {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM local_config WHERE key LIKE ?")) {
            ps.setString(1, ACCEPTED_PREFIX + "%");
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Check if DB has outdated schema and needs a full reset. */
    private boolean needsReset() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) tables.add(rs.getString("name"));
        }
        // Pre-fact-store schema
        if (tables.contains("entities")) return true;
        // Missing required tables
        if (!tables.contains("pending")) return true;
        // Missing commit_id column on facts
        if (tables.contains("facts")) {
            boolean hasCommitId = false;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA table_info(facts)")) {
                while (rs.next()) {
                    if ("commit_id".equals(rs.getString("name"))) {
                        hasCommitId = true;
                        break;
                    }
                }
            }
            if (!hasCommitId) return true;
        }
        return false;
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
                    wall_clock INTEGER NOT NULL,
                    commit_id TEXT
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_entity ON facts(entity_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_lclock ON facts(lclock)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_commit ON facts(commit_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_facts_wall_clock ON facts(wall_clock)");

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
                CREATE TABLE IF NOT EXISTS pending (
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
            s.execute("CREATE INDEX IF NOT EXISTS idx_pending_entity ON pending(entity_id)");

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
            String commitId = Ulid.generate();
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock, commit_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, entityId);
                ps.setString(3, attribute);
                ps.setString(4, value);
                ps.setString(5, source);
                ps.setString(6, peerId);
                ps.setLong(7, lclock);
                ps.setLong(8, wallClock);
                ps.setString(9, commitId);
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
            String commitId = Ulid.generate();
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock, commit_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
                    ps.setString(9, commitId);
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
     * The factId may reference either the facts or pending table.
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
     * Looks up the existing fact in both facts and pending tables.
     */
    private boolean factWins(String newFactId, long newLclock, long newWallClock, String newPeerId,
                              String existingFactId) throws SQLException {
        // Try facts table first, then pending
        for (String table : new String[]{"facts", "pending"}) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT lclock, wall_clock, peer_id FROM " + table + " WHERE id = ?")) {
                ps.setString(1, existingFactId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    long existLclock = rs.getLong("lclock");
                    long existWallClock = rs.getLong("wall_clock");
                    String existPeerId = rs.getString("peer_id");

                    if (newLclock != existLclock) return newLclock > existLclock;
                    if (newWallClock != existWallClock) return newWallClock > existWallClock;
                    return newPeerId.compareTo(existPeerId) > 0;
                }
            }
        }
        return true; // Existing fact not found in either table, new wins
    }

    private void persistClock() throws SQLException {
        setConfigValue("lclock", String.valueOf(lclock));
    }

    // ==================== STAGE / COMMIT / ROLLBACK ====================

    /** Stage a single fact to pending, update current optimistically. Returns the pending row ID. */
    public String stageFact(String entityId, String attribute, String value, String source) {
        if (conn == null) return null;
        try {
            lclock++;
            String id = Ulid.generate();
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pending (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            System.err.println("Failed to stage fact: " + e.getMessage());
            return null;
        }
    }

    /** Batch stage facts to pending in a single transaction. */
    public void stageFacts(List<PendingFact> facts) {
        if (conn == null || facts.isEmpty()) return;
        try {
            conn.setAutoCommit(false);
            lclock++;
            long wallClock = System.currentTimeMillis();

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pending (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            System.err.println("Failed to stage facts batch: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Commit pending facts: squash by (entity_id, attribute), move to facts table.
     * Returns the commit_id.
     */
    public String commit() {
        if (conn == null) return null;
        try {
            conn.setAutoCommit(false);
            String commitId = Ulid.generate();

            // For each unique (entity_id, attribute) in pending, take the latest row (highest lclock, then wall_clock)
            try (Statement s = conn.createStatement();
                 ResultSet groups = s.executeQuery(
                     "SELECT entity_id, attribute FROM pending GROUP BY entity_id, attribute")) {

                try (PreparedStatement selectLatest = conn.prepareStatement(
                         "SELECT id, entity_id, attribute, value, source, peer_id, lclock, wall_clock FROM pending " +
                         "WHERE entity_id = ? AND attribute = ? ORDER BY lclock DESC, wall_clock DESC LIMIT 1");
                     PreparedStatement insertFact = conn.prepareStatement(
                         "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock, commit_id) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                    while (groups.next()) {
                        String entityId = groups.getString("entity_id");
                        String attribute = groups.getString("attribute");

                        selectLatest.setString(1, entityId);
                        selectLatest.setString(2, attribute);
                        ResultSet latest = selectLatest.executeQuery();

                        if (latest.next()) {
                            String newId = Ulid.generate();
                            insertFact.setString(1, newId);
                            insertFact.setString(2, latest.getString("entity_id"));
                            insertFact.setString(3, latest.getString("attribute"));
                            insertFact.setString(4, latest.getString("value"));
                            insertFact.setString(5, latest.getString("source"));
                            insertFact.setString(6, latest.getString("peer_id"));
                            insertFact.setLong(7, latest.getLong("lclock"));
                            insertFact.setLong(8, latest.getLong("wall_clock"));
                            insertFact.setString(9, commitId);
                            insertFact.addBatch();

                            // Update current to point to the new fact ID (in facts table)
                            updateCurrent(entityId, attribute, latest.getString("value"),
                                    newId, latest.getLong("lclock"), latest.getLong("wall_clock"),
                                    latest.getString("peer_id"));
                        }
                    }
                    insertFact.executeBatch();
                }
            }

            // Clear pending
            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM pending");
            }

            conn.commit();
            conn.setAutoCommit(true);
            return commitId;
        } catch (SQLException e) {
            System.err.println("Failed to commit pending facts: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
            return null;
        }
    }

    /** Rollback: discard all pending facts and rebuild current from committed facts only. */
    public void rollback() {
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM pending");
            }
            // Rebuild current from facts only (discards optimistic pending state)
            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM current");
            }
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT id, entity_id, attribute, value, lclock, wall_clock, peer_id FROM facts ORDER BY lclock, wall_clock, peer_id")) {
                while (rs.next()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR REPLACE INTO current (entity_id, attribute, value, fact_id) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, rs.getString("entity_id"));
                        ps.setString(2, rs.getString("attribute"));
                        ps.setString(3, rs.getString("value"));
                        ps.setString(4, rs.getString("id"));
                        ps.execute();
                    }
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("Failed to rollback pending facts: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Count distinct (entity_id, attribute) pairs in pending. */
    public int getPendingCount() {
        if (conn == null) return 0;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM (SELECT DISTINCT entity_id, attribute FROM pending)")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Failed to get pending count: " + e.getMessage());
            return 0;
        }
    }

    /** Summary of pending changes: for each unique (entity_id, attribute), old value (from facts) vs new value (from pending). */
    public List<PendingChange> getPendingSummary() {
        List<PendingChange> changes = new ArrayList<>();
        if (conn == null) return changes;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT entity_id, attribute FROM pending GROUP BY entity_id, attribute ORDER BY entity_id, attribute")) {
            while (rs.next()) {
                String entityId = rs.getString("entity_id");
                String attribute = rs.getString("attribute");

                // Old value: latest from facts
                String oldValue = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT value FROM facts WHERE entity_id = ? AND attribute = ? ORDER BY lclock DESC, wall_clock DESC LIMIT 1")) {
                    ps.setString(1, entityId);
                    ps.setString(2, attribute);
                    ResultSet ors = ps.executeQuery();
                    if (ors.next()) oldValue = ors.getString("value");
                }

                // New value: latest from pending
                String newValue = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT value FROM pending WHERE entity_id = ? AND attribute = ? ORDER BY lclock DESC, wall_clock DESC LIMIT 1")) {
                    ps.setString(1, entityId);
                    ps.setString(2, attribute);
                    ResultSet nrs = ps.executeQuery();
                    if (nrs.next()) newValue = nrs.getString("value");
                }

                changes.add(new PendingChange(entityId, attribute, oldValue, newValue));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get pending summary: " + e.getMessage());
        }
        return changes;
    }

    public record PendingChange(String entityId, String attribute, String oldValue, String newValue) {}

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

    /** Find entity IDs where a specific attribute has any non-null value. */
    public List<String> findByAttributeNotNull(String attribute) {
        List<String> ids = new ArrayList<>();
        if (conn == null) return ids;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT entity_id FROM current WHERE attribute = ? AND value IS NOT NULL")) {
            ps.setString(1, attribute);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("entity_id"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to find by attribute not null: " + e.getMessage());
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

    /** Count entities where a specific attribute has any non-null value, excluding deleted. */
    public int countByAttributeNotNull(String attribute) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM current c1
            WHERE c1.attribute = ? AND c1.value IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM current c2
                WHERE c2.entity_id = c1.entity_id AND c2.attribute = '_deleted' AND c2.value = '1'
            )
        """)) {
            ps.setString(1, attribute);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Failed to count by attribute not null: " + e.getMessage());
            return 0;
        }
    }

    /** Count entities where attribute is not null AND _source matches, excluding deleted. */
    public int countByAttributeNotNullAndSource(String attribute, String sourceValue) {
        if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT COUNT(*) FROM current c1
            JOIN current c2 ON c1.entity_id = c2.entity_id
            WHERE c1.attribute = ? AND c1.value IS NOT NULL
            AND c2.attribute = '_source' AND c2.value = ?
            AND NOT EXISTS (
                SELECT 1 FROM current c3
                WHERE c3.entity_id = c1.entity_id AND c3.attribute = '_deleted' AND c3.value = '1'
            )
        """)) {
            ps.setString(1, attribute);
            ps.setString(2, sourceValue);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Failed to count by attribute not null and source: " + e.getMessage());
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
                        "INSERT OR IGNORE INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock, commit_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, f.id());
                    ps.setString(2, f.entityId());
                    ps.setString(3, f.attribute());
                    ps.setString(4, f.value());
                    ps.setString(5, f.source());
                    ps.setString(6, f.peerId());
                    ps.setLong(7, f.lclock());
                    ps.setLong(8, f.wallClock());
                    ps.setString(9, f.commitId());
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
            rs.getLong("wall_clock"),
            rs.getString("commit_id")
        );
    }

    // ==================== GOVERNANCE SUPPORT ====================

    /**
     * Stage remote facts into pending (for governance review).
     * Unlike receiveFacts(), writes to pending instead of facts — preserving
     * the original peer_id, lclock, etc. from the remote peer.
     */
    public void stageRemoteFacts(List<Fact> facts) {
        if (conn == null || facts.isEmpty()) return;
        try {
            conn.setAutoCommit(false);

            for (Fact f : facts) {
                // Advance Lamport clock
                if (f.lclock() >= lclock) {
                    lclock = f.lclock() + 1;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO pending (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            System.err.println("Failed to stage remote facts: " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /** Get all pending facts as Fact records (for governance review UI). */
    public List<Fact> getPendingFacts() {
        List<Fact> facts = new ArrayList<>();
        if (conn == null) return facts;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT id, entity_id, attribute, value, source, peer_id, lclock, wall_clock FROM pending ORDER BY lclock, wall_clock")) {
            while (rs.next()) {
                facts.add(new Fact(
                    rs.getString("id"),
                    rs.getString("entity_id"),
                    rs.getString("attribute"),
                    rs.getString("value"),
                    rs.getString("source"),
                    rs.getString("peer_id"),
                    rs.getLong("lclock"),
                    rs.getLong("wall_clock"),
                    null // pending facts have no commit_id yet
                ));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get pending facts: " + e.getMessage());
        }
        return facts;
    }

    /** Get distinct peer IDs that have pending facts (for governance: see who submitted). */
    public List<String> getPendingPeerIds() {
        List<String> peerIds = new ArrayList<>();
        if (conn == null) return peerIds;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT DISTINCT peer_id FROM pending ORDER BY peer_id")) {
            while (rs.next()) {
                peerIds.add(rs.getString("peer_id"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get pending peer IDs: " + e.getMessage());
        }
        return peerIds;
    }

    /**
     * Commit only pending facts from a specific peer (governance: approve one peer's submission).
     * Squashes by (entity_id, attribute) within that peer's pending, moves to facts.
     * Returns the commit_id, or null on failure.
     */
    public String commitPendingByPeerId(String targetPeerId) {
        if (conn == null) return null;
        try {
            conn.setAutoCommit(false);
            String commitId = Ulid.generate();

            // For each unique (entity_id, attribute) in pending from this peer
            try (PreparedStatement groups = conn.prepareStatement(
                     "SELECT entity_id, attribute FROM pending WHERE peer_id = ? GROUP BY entity_id, attribute")) {
                groups.setString(1, targetPeerId);
                ResultSet grs = groups.executeQuery();

                try (PreparedStatement selectLatest = conn.prepareStatement(
                         "SELECT id, entity_id, attribute, value, source, peer_id, lclock, wall_clock FROM pending " +
                         "WHERE entity_id = ? AND attribute = ? AND peer_id = ? ORDER BY lclock DESC, wall_clock DESC LIMIT 1");
                     PreparedStatement insertFact = conn.prepareStatement(
                         "INSERT INTO facts (id, entity_id, attribute, value, source, peer_id, lclock, wall_clock, commit_id) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                    while (grs.next()) {
                        String entityId = grs.getString("entity_id");
                        String attribute = grs.getString("attribute");

                        selectLatest.setString(1, entityId);
                        selectLatest.setString(2, attribute);
                        selectLatest.setString(3, targetPeerId);
                        ResultSet latest = selectLatest.executeQuery();

                        if (latest.next()) {
                            String newId = Ulid.generate();
                            insertFact.setString(1, newId);
                            insertFact.setString(2, latest.getString("entity_id"));
                            insertFact.setString(3, latest.getString("attribute"));
                            insertFact.setString(4, latest.getString("value"));
                            insertFact.setString(5, latest.getString("source"));
                            insertFact.setString(6, latest.getString("peer_id"));
                            insertFact.setLong(7, latest.getLong("lclock"));
                            insertFact.setLong(8, latest.getLong("wall_clock"));
                            insertFact.setString(9, commitId);
                            insertFact.addBatch();

                            updateCurrent(entityId, attribute, latest.getString("value"),
                                    newId, latest.getLong("lclock"), latest.getLong("wall_clock"),
                                    latest.getString("peer_id"));
                        }
                    }
                    insertFact.executeBatch();
                }
            }

            // Delete only this peer's pending
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pending WHERE peer_id = ?")) {
                ps.setString(1, targetPeerId);
                ps.execute();
            }

            conn.commit();
            conn.setAutoCommit(true);
            return commitId;
        } catch (SQLException e) {
            System.err.println("Failed to commit pending for peer " + targetPeerId + ": " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
            return null;
        }
    }

    /**
     * Rollback only pending facts from a specific peer (governance: reject one peer's submission).
     * Rebuilds current state to remove the optimistic pending state from that peer.
     */
    public void rollbackPendingByPeerId(String targetPeerId) {
        if (conn == null) return;
        try {
            conn.setAutoCommit(false);

            // Delete this peer's pending
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pending WHERE peer_id = ?")) {
                ps.setString(1, targetPeerId);
                ps.execute();
            }

            // Rebuild current from facts + remaining pending
            rebuildCurrentInternal();

            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println("Failed to rollback pending for peer " + targetPeerId + ": " + e.getMessage());
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    /**
     * Rebuild current table from facts + pending (internal helper, assumes caller manages transaction).
     */
    private void rebuildCurrentInternal() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("DELETE FROM current");
        }
        // Replay facts
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT id, entity_id, attribute, value, lclock, wall_clock, peer_id FROM facts ORDER BY lclock, wall_clock, peer_id")) {
            while (rs.next()) {
                updateCurrent(rs.getString("entity_id"), rs.getString("attribute"), rs.getString("value"),
                        rs.getString("id"), rs.getLong("lclock"), rs.getLong("wall_clock"), rs.getString("peer_id"));
            }
        }
        // Replay remaining pending
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT id, entity_id, attribute, value, lclock, wall_clock, peer_id FROM pending ORDER BY lclock, wall_clock, peer_id")) {
            while (rs.next()) {
                updateCurrent(rs.getString("entity_id"), rs.getString("attribute"), rs.getString("value"),
                        rs.getString("id"), rs.getLong("lclock"), rs.getLong("wall_clock"), rs.getString("peer_id"));
            }
        }
    }

    public void close() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    // ==================== FACT HISTORY QUERIES ====================

    public record FactQuery(int limit, int offset, String search) {}

    /** Paginated query of committed facts, ordered by wall_clock DESC. */
    public List<Fact> queryFacts(FactQuery query) {
        List<Fact> facts = new ArrayList<>();
        if (conn == null) return facts;
        try {
            String sql;
            if (query.search() != null && !query.search().isBlank()) {
                sql = "SELECT * FROM facts WHERE entity_id LIKE ? OR attribute LIKE ? OR value LIKE ? OR source LIKE ? ORDER BY wall_clock DESC, lclock DESC LIMIT ? OFFSET ?";
            } else {
                sql = "SELECT * FROM facts ORDER BY wall_clock DESC, lclock DESC LIMIT ? OFFSET ?";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                if (query.search() != null && !query.search().isBlank()) {
                    String pattern = "%" + query.search() + "%";
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                }
                ps.setInt(idx++, query.limit());
                ps.setInt(idx, query.offset());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    facts.add(mapFact(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to query facts: " + e.getMessage());
        }
        return facts;
    }

    /** Count facts matching a search query (or all facts if search is null). */
    public int countFacts(FactQuery query) {
        if (conn == null) return 0;
        try {
            String sql;
            if (query.search() != null && !query.search().isBlank()) {
                sql = "SELECT COUNT(*) FROM facts WHERE entity_id LIKE ? OR attribute LIKE ? OR value LIKE ? OR source LIKE ?";
            } else {
                sql = "SELECT COUNT(*) FROM facts";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (query.search() != null && !query.search().isBlank()) {
                    String pattern = "%" + query.search() + "%";
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                    ps.setString(3, pattern);
                    ps.setString(4, pattern);
                }
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Failed to count facts: " + e.getMessage());
            return 0;
        }
    }

    // ==================== COMMIT HISTORY QUERIES ====================

    public record CommitQuery(int limit, int offset, String search) {}

    public record CommitSummary(String commitId, long wallClock, String source,
                                 String peerId, int factCount, int entityCount) {}

    /** Paginated query of commits, ordered by wall_clock DESC. */
    public List<CommitSummary> queryCommits(CommitQuery query) {
        List<CommitSummary> commits = new ArrayList<>();
        if (conn == null) return commits;
        try {
            String sql;
            if (query.search() != null && !query.search().isBlank()) {
                sql = """
                    SELECT commit_id, MAX(wall_clock) as wall_clock,
                           GROUP_CONCAT(DISTINCT source) as sources,
                           MAX(peer_id) as peer_id,
                           COUNT(*) as fact_count,
                           COUNT(DISTINCT entity_id) as entity_count
                    FROM facts
                    WHERE commit_id IS NOT NULL
                      AND (entity_id LIKE ? OR attribute LIKE ? OR value LIKE ? OR source LIKE ?)
                    GROUP BY commit_id
                    ORDER BY MAX(wall_clock) DESC
                    LIMIT ? OFFSET ?
                    """;
            } else {
                sql = """
                    SELECT commit_id, MAX(wall_clock) as wall_clock,
                           GROUP_CONCAT(DISTINCT source) as sources,
                           MAX(peer_id) as peer_id,
                           COUNT(*) as fact_count,
                           COUNT(DISTINCT entity_id) as entity_count
                    FROM facts
                    WHERE commit_id IS NOT NULL
                    GROUP BY commit_id
                    ORDER BY MAX(wall_clock) DESC
                    LIMIT ? OFFSET ?
                    """;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                if (query.search() != null && !query.search().isBlank()) {
                    String pattern = "%" + query.search() + "%";
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                    ps.setString(idx++, pattern);
                }
                ps.setInt(idx++, query.limit());
                ps.setInt(idx, query.offset());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    commits.add(new CommitSummary(
                        rs.getString("commit_id"),
                        rs.getLong("wall_clock"),
                        rs.getString("sources"),
                        rs.getString("peer_id"),
                        rs.getInt("fact_count"),
                        rs.getInt("entity_count")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to query commits: " + e.getMessage());
        }
        return commits;
    }

    /** Count distinct commits matching a search query (or all commits if search is null). */
    public int countCommits(CommitQuery query) {
        if (conn == null) return 0;
        try {
            String sql;
            if (query.search() != null && !query.search().isBlank()) {
                sql = """
                    SELECT COUNT(DISTINCT commit_id) FROM facts
                    WHERE commit_id IS NOT NULL
                      AND (entity_id LIKE ? OR attribute LIKE ? OR value LIKE ? OR source LIKE ?)
                    """;
            } else {
                sql = "SELECT COUNT(DISTINCT commit_id) FROM facts WHERE commit_id IS NOT NULL";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (query.search() != null && !query.search().isBlank()) {
                    String pattern = "%" + query.search() + "%";
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                    ps.setString(3, pattern);
                    ps.setString(4, pattern);
                }
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Failed to count commits: " + e.getMessage());
            return 0;
        }
    }

    /** Get all facts for a specific commit, ordered by entity_id, attribute. */
    public List<Fact> getFactsByCommitId(String commitId) {
        List<Fact> facts = new ArrayList<>();
        if (conn == null || commitId == null) return facts;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM facts WHERE commit_id = ? ORDER BY entity_id, attribute")) {
            ps.setString(1, commitId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                facts.add(mapFact(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get facts by commit ID: " + e.getMessage());
        }
        return facts;
    }

    /** Get distinct entity IDs for a commit (for summary display). */
    public List<String> getEntityIdsForCommit(String commitId) {
        List<String> ids = new ArrayList<>();
        if (conn == null || commitId == null) return ids;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT entity_id FROM facts WHERE commit_id = ? ORDER BY entity_id")) {
            ps.setString(1, commitId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("entity_id"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to get entity IDs for commit: " + e.getMessage());
        }
        return ids;
    }

    // ==================== RECORDS ====================

    public record Fact(String id, String entityId, String attribute, String value,
                       String source, String peerId, long lclock, long wallClock,
                       String commitId) {}

    public record PendingFact(String entityId, String attribute, String value, String source) {}
}
