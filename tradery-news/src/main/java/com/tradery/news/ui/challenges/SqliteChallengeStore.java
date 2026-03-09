package com.tradery.news.ui.challenges;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.schedule.ChallengeSubscription;
import com.tradery.ai.challenges.store.ChallengeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed ChallengeStore. Uses its own independent database file
 * (challenges.db) alongside the document directory.
 */
public class SqliteChallengeStore implements ChallengeStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteChallengeStore.class);
    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

    private final Connection conn;

    public SqliteChallengeStore(Path dbPath) {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open challenges database: " + dbPath, e);
        }
        createTables();
    }

    public void close() {
        try { conn.close(); } catch (SQLException e) { log.warn("Failed to close challenges db", e); }
    }

    private void createTables() {
        executeSQL("CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
        executeSQL("""
            CREATE TABLE IF NOT EXISTS challenge_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                challenge_id TEXT NOT NULL,
                subject_id TEXT NOT NULL,
                escalation_index INTEGER NOT NULL,
                output_type TEXT NOT NULL,
                text_result TEXT,
                list_result TEXT,
                fields_json TEXT,
                signal_value REAL,
                timestamp INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                resolved_tier TEXT,
                verified INTEGER DEFAULT 0,
                error TEXT
            )""");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_cr_subject ON challenge_results(subject_id, challenge_id)");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_cr_timestamp ON challenge_results(challenge_id, subject_id, timestamp DESC)");
        executeSQL("""
            CREATE TABLE IF NOT EXISTS challenge_subscriptions (
                challenge_id TEXT NOT NULL,
                subject_id TEXT NOT NULL,
                last_run_timestamp INTEGER NOT NULL,
                next_run_timestamp INTEGER NOT NULL,
                PRIMARY KEY (challenge_id, subject_id)
            )""");
        // Upgrade: add columns if missing (pre-existing DBs)
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE challenge_results ADD COLUMN fields_json TEXT");
        } catch (SQLException ignored) { /* column already exists */ }
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE challenge_results ADD COLUMN items_json TEXT");
        } catch (SQLException ignored) { /* column already exists */ }
    }

    private void executeSQL(String sql) {
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            log.error("Failed to execute SQL: {}", sql.substring(0, Math.min(60, sql.length())), e);
        }
    }

    // ==================== Challenges ====================

    @Override
    public List<Challenge> listChallenges() {
        List<Challenge> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT json FROM challenges ORDER BY id")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Challenge c = JSON.readValue(rs.getString("json"), Challenge.class);
                result.add(c);
            }
        } catch (Exception e) {
            log.error("Failed to list challenges", e);
        }
        result.sort((a, b) -> Integer.compare(a.displayOrder(), b.displayOrder()));
        return result;
    }

    @Override
    public Challenge getChallenge(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT json FROM challenges WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return JSON.readValue(rs.getString("json"), Challenge.class);
            }
        } catch (Exception e) {
            log.error("Failed to get challenge: {}", id, e);
        }
        return null;
    }

    @Override
    public void saveChallenge(Challenge challenge) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO challenges (id, json) VALUES (?, ?)")) {
            ps.setString(1, challenge.id());
            ps.setString(2, JSON.writeValueAsString(challenge));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to save challenge: {}", challenge.id(), e);
        }
    }

    @Override
    public void deleteChallenge(String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM challenges WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete challenge: {}", id, e);
        }
    }

    // ==================== Results ====================

    @Override
    public List<ChallengeResult> getResults(String challengeId, String subjectId) {
        return queryResults(
            "SELECT * FROM challenge_results WHERE challenge_id = ? AND subject_id = ? ORDER BY timestamp DESC",
            challengeId, subjectId);
    }

    @Override
    public List<ChallengeResult> getLatestResults(String subjectId) {
        // Subquery to get max timestamp per challenge_id for this subject
        String sql = """
            SELECT cr.* FROM challenge_results cr
            INNER JOIN (
                SELECT challenge_id, MAX(timestamp) as max_ts
                FROM challenge_results WHERE subject_id = ?
                GROUP BY challenge_id
            ) latest ON cr.challenge_id = latest.challenge_id AND cr.timestamp = latest.max_ts
            WHERE cr.subject_id = ?
            ORDER BY cr.timestamp DESC
        """;
        List<ChallengeResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectId);
            ps.setString(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(rowToResult(rs));
            }
        } catch (Exception e) {
            log.error("Failed to get latest results for subject: {}", subjectId, e);
        }
        return results;
    }

    @Override
    public ChallengeResult getLatestResult(String challengeId, String subjectId) {
        String sql = "SELECT * FROM challenge_results WHERE challenge_id = ? AND subject_id = ? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setString(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rowToResult(rs);
            }
        } catch (Exception e) {
            log.error("Failed to get latest result for {}/{}", challengeId, subjectId, e);
        }
        return null;
    }

    @Override
    public List<ChallengeResult> getSignalHistory(String challengeId, String subjectId, int limit) {
        String sql = """
            SELECT * FROM challenge_results
            WHERE challenge_id = ? AND subject_id = ? AND signal_value IS NOT NULL
            ORDER BY timestamp DESC LIMIT ?
        """;
        List<ChallengeResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setString(2, subjectId);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(rowToResult(rs));
            }
        } catch (Exception e) {
            log.error("Failed to get signal history for {}/{}", challengeId, subjectId, e);
        }
        return results;
    }

    @Override
    public List<ChallengeResult> getResultsForChallenge(String challengeId, int limit) {
        String sql = "SELECT * FROM challenge_results WHERE challenge_id = ? ORDER BY timestamp ASC LIMIT ?";
        List<ChallengeResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(rowToResult(rs));
            }
        } catch (Exception e) {
            log.error("Failed to get results for challenge: {}", challengeId, e);
        }
        return results;
    }

    @Override
    public void saveResult(ChallengeResult result) {
        String sql = """
            INSERT INTO challenge_results
            (challenge_id, subject_id, escalation_index, output_type, text_result, list_result,
             fields_json, items_json, signal_value, timestamp, duration_ms, resolved_tier, verified, error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, result.challengeId());
            ps.setString(2, result.subjectId());
            ps.setInt(3, result.escalationIndex());
            ps.setString(4, result.outputType().name());
            ps.setString(5, result.textResult());
            ps.setString(6, result.listResult() != null ? JSON.writeValueAsString(result.listResult()) : null);
            ps.setString(7, result.fields() != null ? JSON.writeValueAsString(result.fields()) : null);
            ps.setString(8, result.itemResults() != null ? JSON.writeValueAsString(result.itemResults()) : null);
            if (result.signalValue() != null) {
                ps.setDouble(9, result.signalValue());
            } else {
                ps.setNull(9, Types.REAL);
            }
            ps.setLong(10, result.timestamp());
            ps.setLong(11, result.durationMs());
            ps.setString(12, result.resolvedTier());
            ps.setInt(13, result.verified() ? 1 : 0);
            ps.setString(14, result.error());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                result.setId(keys.getLong(1));
            }
        } catch (Exception e) {
            log.error("Failed to save challenge result", e);
        }
    }

    // ==================== Subscriptions ====================

    @Override
    public List<ChallengeSubscription> getActiveSubscriptions() {
        List<ChallengeSubscription> subs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM challenge_subscriptions")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                subs.add(rowToSubscription(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to list subscriptions", e);
        }
        return subs;
    }

    @Override
    public ChallengeSubscription getSubscription(String challengeId, String subjectId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM challenge_subscriptions WHERE challenge_id = ? AND subject_id = ?")) {
            ps.setString(1, challengeId);
            ps.setString(2, subjectId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rowToSubscription(rs);
        } catch (SQLException e) {
            log.error("Failed to get subscription {}/{}", challengeId, subjectId, e);
        }
        return null;
    }

    @Override
    public void saveSubscription(ChallengeSubscription sub) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO challenge_subscriptions
                (challenge_id, subject_id, last_run_timestamp, next_run_timestamp) VALUES (?, ?, ?, ?)
            """)) {
            ps.setString(1, sub.challengeId());
            ps.setString(2, sub.subjectId());
            ps.setLong(3, sub.lastRunTimestamp());
            ps.setLong(4, sub.nextRunTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save subscription {}/{}", sub.challengeId(), sub.subjectId(), e);
        }
    }

    @Override
    public void deleteSubscription(String challengeId, String subjectId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM challenge_subscriptions WHERE challenge_id = ? AND subject_id = ?")) {
            ps.setString(1, challengeId);
            ps.setString(2, subjectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete subscription {}/{}", challengeId, subjectId, e);
        }
    }

    // ==================== Helpers ====================

    private List<ChallengeResult> queryResults(String sql, String challengeId, String subjectId) {
        List<ChallengeResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setString(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(rowToResult(rs));
            }
        } catch (Exception e) {
            log.error("Failed to query results", e);
        }
        return results;
    }

    private ChallengeResult rowToResult(ResultSet rs) throws Exception {
        ChallengeResult r = new ChallengeResult();
        r.setId(rs.getLong("id"));
        r.setChallengeId(rs.getString("challenge_id"));
        r.setSubjectId(rs.getString("subject_id"));
        r.setEscalationIndex(rs.getInt("escalation_index"));
        r.setOutputType(ChallengeOutput.Type.valueOf(rs.getString("output_type")));
        r.setTextResult(rs.getString("text_result"));
        String listJson = rs.getString("list_result");
        if (listJson != null) {
            r.setListResult(JSON.readValue(listJson, new TypeReference<List<String>>() {}));
        }
        String fieldsJson = rs.getString("fields_json");
        if (fieldsJson != null) {
            r.setFields(JSON.readValue(fieldsJson, new TypeReference<Map<String, String>>() {}));
        }
        try {
            String itemsJson = rs.getString("items_json");
            if (itemsJson != null) {
                r.setItemResults(JSON.readValue(itemsJson, new TypeReference<List<Map<String, String>>>() {}));
            }
        } catch (Exception ignored) { /* column may not exist in old DBs */ }
        double sig = rs.getDouble("signal_value");
        r.setSignalValue(rs.wasNull() ? null : sig);
        r.setTimestamp(rs.getLong("timestamp"));
        r.setDurationMs(rs.getLong("duration_ms"));
        r.setResolvedTier(rs.getString("resolved_tier"));
        r.setVerified(rs.getInt("verified") == 1);
        r.setError(rs.getString("error"));
        return r;
    }

    private ChallengeSubscription rowToSubscription(ResultSet rs) throws SQLException {
        return new ChallengeSubscription(
            rs.getString("challenge_id"),
            rs.getString("subject_id"),
            rs.getLong("last_run_timestamp"),
            rs.getLong("next_run_timestamp")
        );
    }
}
