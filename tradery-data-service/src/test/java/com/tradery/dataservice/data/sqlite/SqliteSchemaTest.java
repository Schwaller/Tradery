package com.tradery.dataservice.data.sqlite;

import com.tradery.dataservice.data.DataConfig;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqliteSchema initialization and version handling.
 */
class SqliteSchemaTest {

    private static File originalDataDir;
    private File tempDir;

    @BeforeAll
    static void saveOriginalDataDir() {
        originalDataDir = DataConfig.getInstance().getDataDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("schema-test").toFile();
        DataConfig.getInstance().setDataDir(tempDir);
    }

    @AfterEach
    void tearDown() {
        SqliteConnection.closeAll();
        DataConfig.getInstance().setDataDir(originalDataDir);
        deleteRecursive(tempDir);
    }

    // ========== Fresh DB creation ==========

    @Test
    @DisplayName("fresh DB creates tables and sets version")
    void freshDbCreatesTablesAndSetsVersion() throws Exception {
        SqliteConnection conn = SqliteConnection.forSymbolAndType("TEST", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);

        Connection c = conn.getConnection();

        // volume_profiles table exists
        assertTrue(tableExists(c, "volume_profiles"));
        // schema_version table exists
        assertTrue(tableExists(c, "schema_version"));
        // data_coverage table exists
        assertTrue(tableExists(c, "data_coverage"));
        // Version is CURRENT_VERSION
        assertEquals(SqliteSchema.CURRENT_VERSION, getSchemaVersion(c));
    }

    @Test
    @DisplayName("fresh DB creates correct tables for each type")
    void freshDbCreatesCorrectTablesForEachType() throws Exception {
        for (DataStoreType type : DataStoreType.values()) {
            SqliteConnection.closeAll(); // Clear cached connections

            SqliteConnection conn = SqliteConnection.forSymbolAndType("TEST_" + type.name(), type);
            SqliteSchema.initialize(conn, type);
            Connection c = conn.getConnection();

            switch (type) {
                case CANDLES -> assertTrue(tableExists(c, "candles"), "candles table should exist for CANDLES type");
                case AGG_TRADES -> {
                    assertTrue(tableExists(c, "agg_trades"), "agg_trades table should exist for AGG_TRADES type");
                    assertTrue(tableExists(c, "stablecoin_rates"), "stablecoin_rates table should exist");
                }
                case FUNDING_RATES -> assertTrue(tableExists(c, "funding_rates"), "funding_rates table should exist");
                case OPEN_INTEREST -> assertTrue(tableExists(c, "open_interest"), "open_interest table should exist");
                case PREMIUM_INDEX -> assertTrue(tableExists(c, "premium_index"), "premium_index table should exist");
                case VOLUME_PROFILES -> assertTrue(tableExists(c, "volume_profiles"), "volume_profiles table should exist");
                case SPECTRUM -> assertTrue(tableExists(c, "trade_size_spectrum"), "trade_size_spectrum table should exist");
            }
        }
    }

    // ========== Version mismatch handling ==========

    @Test
    @DisplayName("outdated version drops and recreates tables")
    void outdatedVersionDropsAndRecreates() throws Exception {
        SqliteConnection conn = SqliteConnection.forSymbolAndType("TEST_MIGRATE", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);
        Connection c = conn.getConnection();

        // Insert test data
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO volume_profiles (timeframe, market_type, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, "1h");
            stmt.setString(2, "perp");
            stmt.setLong(3, 1000L);
            stmt.setDouble(4, 0.1);
            stmt.setDouble(5, 100.0);
            stmt.setDouble(6, 50.0);
            stmt.setInt(7, 10);
            stmt.setBytes(8, new byte[]{1, 2, 3});
            stmt.executeUpdate();
        }

        // Insert coverage entry
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO data_coverage (data_type, sub_key, range_start, range_end, is_complete, last_updated) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, "volume_profiles");
            stmt.setString(2, "perp");
            stmt.setLong(3, 1000);
            stmt.setLong(4, 2000);
            stmt.setInt(5, 1);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        // Verify data exists
        assertEquals(1, countRows(c, "volume_profiles"));
        assertEquals(1, countCoverage(c, "volume_profiles"));

        // Downgrade version to simulate outdated schema
        // Must delete all rows first since PK is the version number itself
        try (Statement stmt = c.createStatement()) {
            stmt.execute("DELETE FROM schema_version");
        }
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            stmt.setInt(1, 1);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        // Close and re-get connection (the connection is cached, so we need a fresh SqliteConnection)
        SqliteConnection.closeAll();
        SqliteConnection conn2 = SqliteConnection.forSymbolAndType("TEST_MIGRATE", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn2, DataStoreType.VOLUME_PROFILES);
        Connection c2 = conn2.getConnection();

        // Table should exist but data should be gone
        assertTrue(tableExists(c2, "volume_profiles"));
        assertEquals(0, countRows(c2, "volume_profiles"), "Data should be cleared after schema reset");
        assertEquals(0, countCoverage(c2, "volume_profiles"), "Coverage should be cleared after schema reset");
        assertEquals(SqliteSchema.CURRENT_VERSION, getSchemaVersion(c2));
    }

    @Test
    @DisplayName("up-to-date version is a no-op")
    void upToDateVersionIsNoOp() throws Exception {
        SqliteConnection conn = SqliteConnection.forSymbolAndType("TEST_NOOP", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);
        Connection c = conn.getConnection();

        // Insert test data
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO volume_profiles (timeframe, market_type, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, "1h");
            stmt.setString(2, "perp");
            stmt.setLong(3, 1000L);
            stmt.setDouble(4, 0.1);
            stmt.setDouble(5, 100.0);
            stmt.setDouble(6, 50.0);
            stmt.setInt(7, 10);
            stmt.setBytes(8, new byte[]{1, 2, 3});
            stmt.executeUpdate();
        }

        // Re-initialize — should be a no-op
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);

        // Data should still be there
        assertEquals(1, countRows(c, "volume_profiles"), "Data should be preserved when version matches");
    }

    // ========== Volume profiles schema specifics ==========

    @Test
    @DisplayName("volume_profiles table has market_type in primary key")
    void volumeProfilesHasMarketTypeInPK() throws Exception {
        SqliteConnection conn = SqliteConnection.forSymbolAndType("TEST_PK", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);
        Connection c = conn.getConnection();

        // Insert two rows with same timeframe+window_start but different market_type — should both succeed
        String sql = "INSERT INTO volume_profiles (timeframe, market_type, window_start, tick_size, total_buy_volume, total_sell_volume, level_count, profile_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, "1h");
            stmt.setString(2, "perp");
            stmt.setLong(3, 1000L);
            stmt.setDouble(4, 0.1);
            stmt.setDouble(5, 100.0);
            stmt.setDouble(6, 50.0);
            stmt.setInt(7, 10);
            stmt.setBytes(8, new byte[]{1});
            stmt.executeUpdate();

            stmt.setString(2, "spot");
            stmt.executeUpdate();
        }

        assertEquals(2, countRows(c, "volume_profiles"), "Should allow same timeframe+window with different market_type");
    }

    // ========== Helpers ==========

    private boolean tableExists(Connection c, String tableName) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private int getSchemaVersion(Connection c) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private long countRows(Connection c, String tableName) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private long countCoverage(Connection c, String dataType) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement("SELECT COUNT(*) FROM data_coverage WHERE data_type = ?")) {
            stmt.setString(1, dataType);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) deleteRecursive(f);
        }
        file.delete();
    }
}
