package com.tradery.dataservice.data.sqlite.dao;

import com.tradery.dataservice.data.DataConfig;
import com.tradery.dataservice.data.sqlite.DataStoreType;
import com.tradery.dataservice.data.sqlite.SqliteConnection;
import com.tradery.dataservice.data.sqlite.SqliteSchema;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VolumeProfileDao with market type support.
 */
class VolumeProfileDaoTest {

    private static File originalDataDir;
    private File tempDir;
    private VolumeProfileDao dao;

    @BeforeAll
    static void saveOriginalDataDir() {
        originalDataDir = DataConfig.getInstance().getDataDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("vpdao-test").toFile();
        DataConfig.getInstance().setDataDir(tempDir);

        SqliteConnection conn = SqliteConnection.forSymbolAndType("TESTUSDT", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);
        dao = new VolumeProfileDao(conn);
    }

    @AfterEach
    void tearDown() {
        SqliteConnection.closeAll();
        DataConfig.getInstance().setDataDir(originalDataDir);
        deleteRecursive(tempDir);
    }

    // ========== ProfileRow market type ==========

    @Test
    @DisplayName("ProfileRow includes marketType field")
    void profileRowIncludesMarketType() {
        var row = new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1});
        assertEquals("perp", row.marketType());
        assertEquals("1h", row.timeframe());
        assertEquals(1000L, row.windowStart());
    }

    // ========== Insert and query ==========

    @Test
    @DisplayName("upsert and query by market type")
    void upsertAndQueryByMarketType() throws Exception {
        var perpRow = new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1});
        var spotRow = new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2});

        dao.upsert(perpRow);
        dao.upsert(spotRow);

        // Query perp only
        List<VolumeProfileDao.ProfileRow> perpResults = dao.query("perp", "1h", 0, 2000);
        assertEquals(1, perpResults.size());
        assertEquals("perp", perpResults.get(0).marketType());
        assertEquals(100.0, perpResults.get(0).totalBuyVolume(), 0.001);

        // Query spot only
        List<VolumeProfileDao.ProfileRow> spotResults = dao.query("spot", "1h", 0, 2000);
        assertEquals(1, spotResults.size());
        assertEquals("spot", spotResults.get(0).marketType());
        assertEquals(200.0, spotResults.get(0).totalBuyVolume(), 0.001);
    }

    @Test
    @DisplayName("different market types don't mix in queries")
    void differentMarketTypesDontMix() throws Exception {
        // Insert 3 perp rows and 2 spot rows
        for (int i = 0; i < 3; i++) {
            dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L + i * 3600000, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        }
        for (int i = 0; i < 2; i++) {
            dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 1000L + i * 3600000, 0.1, 200.0, 80.0, 15, new byte[]{2}));
        }

        assertEquals(3, dao.query("perp", "1h", 0, Long.MAX_VALUE).size());
        assertEquals(2, dao.query("spot", "1h", 0, Long.MAX_VALUE).size());
    }

    // ========== Batch upsert ==========

    @Test
    @DisplayName("batch upsert preserves market type")
    void batchUpsertPreservesMarketType() throws Exception {
        List<VolumeProfileDao.ProfileRow> rows = List.of(
            new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}),
            new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2}),
            new VolumeProfileDao.ProfileRow("1h", "perp", 2000L, 0.1, 150.0, 60.0, 12, new byte[]{3})
        );

        int count = dao.upsertBatch(rows);
        assertEquals(3, count);

        assertEquals(2, dao.query("perp", "1h", 0, Long.MAX_VALUE).size());
        assertEquals(1, dao.query("spot", "1h", 0, Long.MAX_VALUE).size());
    }

    // ========== Count ==========

    @Test
    @DisplayName("count filters by market type")
    void countFiltersByMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 2000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        assertEquals(2, dao.count("perp", "1h", 0, Long.MAX_VALUE));
        assertEquals(1, dao.count("spot", "1h", 0, Long.MAX_VALUE));
    }

    @Test
    @DisplayName("countAll with market type filters correctly")
    void countAllWithMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        assertEquals(1, dao.countAll("perp", "1h"));
        assertEquals(1, dao.countAll("spot", "1h"));
        // countAll(timeframe) — across all market types
        assertEquals(2, dao.countAll("1h"));
    }

    // ========== Time range ==========

    @Test
    @DisplayName("getTimeRange filters by market type")
    void getTimeRangeFiltersByMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 5000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 2000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 8000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        long[] perpRange = dao.getTimeRange("perp", "1h");
        assertNotNull(perpRange);
        assertEquals(1000L, perpRange[0]);
        assertEquals(5000L, perpRange[1]);

        long[] spotRange = dao.getTimeRange("spot", "1h");
        assertNotNull(spotRange);
        assertEquals(2000L, spotRange[0]);
        assertEquals(8000L, spotRange[1]);
    }

    @Test
    @DisplayName("getTimeRange across all market types")
    void getTimeRangeAcrossAllMarketTypes() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 9000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        long[] range = dao.getTimeRange("1h");
        assertNotNull(range);
        assertEquals(1000L, range[0]);
        assertEquals(9000L, range[1]);
    }

    // ========== Delete ==========

    @Test
    @DisplayName("deleteRange filters by market type")
    void deleteRangeFiltersByMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        // Delete only perp
        int deleted = dao.deleteRange("perp", "1h", 0, Long.MAX_VALUE);
        assertEquals(1, deleted);

        // Spot should still exist
        assertEquals(0, dao.query("perp", "1h", 0, Long.MAX_VALUE).size());
        assertEquals(1, dao.query("spot", "1h", 0, Long.MAX_VALUE).size());
    }

    // getAvailableMarketTypeTimeframes was removed from VolumeProfileDao — test disabled

    // ========== Latest window start ==========

    @Test
    @DisplayName("getLatestWindowStart filters by market type")
    void getLatestWindowStartFiltersByMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 5000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 3000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        assertEquals(5000L, dao.getLatestWindowStart("perp", "1h"));
        assertEquals(3000L, dao.getLatestWindowStart("spot", "1h"));
    }

    // ========== Stream query ==========

    @Test
    @DisplayName("streamQuery filters by market type")
    void streamQueryFiltersByMarketType() throws Exception {
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 1000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "perp", 2000L, 0.1, 100.0, 50.0, 10, new byte[]{1}));
        dao.upsert(new VolumeProfileDao.ProfileRow("1h", "spot", 1000L, 0.1, 200.0, 80.0, 15, new byte[]{2}));

        List<VolumeProfileDao.ProfileRow> streamed = new java.util.ArrayList<>();
        int count = dao.streamQuery("perp", "1h", 0, Long.MAX_VALUE, 100, streamed::addAll);

        assertEquals(2, count);
        assertEquals(2, streamed.size());
        assertTrue(streamed.stream().allMatch(r -> r.marketType().equals("perp")));
    }

    // ========== Helpers ==========

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) deleteRecursive(f);
        }
        file.delete();
    }
}
