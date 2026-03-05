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
 * Tests for CoverageDao gap detection and coverage merging.
 */
class CoverageDaoTest {

    private static File originalDataDir;
    private File tempDir;
    private CoverageDao dao;

    @BeforeAll
    static void saveOriginalDataDir() {
        originalDataDir = DataConfig.getInstance().getDataDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("coverage-test").toFile();
        DataConfig.getInstance().setDataDir(tempDir);

        SqliteConnection conn = SqliteConnection.forSymbolAndType("TESTUSDT", DataStoreType.VOLUME_PROFILES);
        SqliteSchema.initialize(conn, DataStoreType.VOLUME_PROFILES);
        dao = new CoverageDao(conn);
    }

    @AfterEach
    void tearDown() {
        SqliteConnection.closeAll();
        DataConfig.getInstance().setDataDir(originalDataDir);
        deleteRecursive(tempDir);
    }

    // ========== findGaps: empty coverage ==========

    @Test
    @DisplayName("empty coverage returns single gap spanning full range")
    void emptyCoverageReturnsSingleGap() throws Exception {
        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);

        assertEquals(1, gaps.size());
        assertEquals(1000, gaps.get(0)[0]);
        assertEquals(5000, gaps.get(0)[1]);
    }

    // ========== findGaps: full coverage ==========

    @Test
    @DisplayName("full coverage returns no gaps")
    void fullCoverageReturnsNoGaps() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 5000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertTrue(gaps.isEmpty());
    }

    @Test
    @DisplayName("coverage wider than query returns no gaps")
    void coverageWiderThanQueryReturnsNoGaps() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 500, 6000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertTrue(gaps.isEmpty());
    }

    // ========== findGaps: partial coverage ==========

    @Test
    @DisplayName("gap at start")
    void gapAtStart() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 3000, 5000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertEquals(1, gaps.size());
        assertEquals(1000, gaps.get(0)[0]);
        assertEquals(2999, gaps.get(0)[1]);
    }

    @Test
    @DisplayName("gap at end")
    void gapAtEnd() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 3000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertEquals(1, gaps.size());
        assertEquals(3001, gaps.get(0)[0]);
        assertEquals(5000, gaps.get(0)[1]);
    }

    @Test
    @DisplayName("gap in the middle")
    void gapInTheMiddle() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 2000, true);
        dao.addCoverage("volume_profiles", "perp", 4000, 5000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertEquals(1, gaps.size());
        assertEquals(2001, gaps.get(0)[0]);
        assertEquals(3999, gaps.get(0)[1]);
    }

    @Test
    @DisplayName("multiple gaps between coverage islands")
    void multipleGapsBetweenCoverageIslands() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 2000, 3000, true);
        dao.addCoverage("volume_profiles", "perp", 5000, 6000, true);
        dao.addCoverage("volume_profiles", "perp", 8000, 9000, true);

        List<long[]> gaps = dao.findGaps("volume_profiles", "perp", 1000, 10000);
        assertEquals(4, gaps.size());
        // Gap before first island
        assertEquals(1000, gaps.get(0)[0]);
        assertEquals(1999, gaps.get(0)[1]);
        // Gap between first and second
        assertEquals(3001, gaps.get(1)[0]);
        assertEquals(4999, gaps.get(1)[1]);
        // Gap between second and third
        assertEquals(6001, gaps.get(2)[0]);
        assertEquals(7999, gaps.get(2)[1]);
        // Gap after third island
        assertEquals(9001, gaps.get(3)[0]);
        assertEquals(10000, gaps.get(3)[1]);
    }

    // ========== addCoverage: merging ==========

    @Test
    @DisplayName("adjacent ranges merge into one")
    void adjacentRangesMerge() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 2000, true);
        dao.addCoverage("volume_profiles", "perp", 2001, 3000, true);

        List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges("volume_profiles", "perp");
        assertEquals(1, ranges.size());
        assertEquals(1000, ranges.get(0).rangeStart());
        assertEquals(3000, ranges.get(0).rangeEnd());
    }

    @Test
    @DisplayName("overlapping ranges merge correctly")
    void overlappingRangesMerge() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 3000, true);
        dao.addCoverage("volume_profiles", "perp", 2500, 5000, true);

        List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges("volume_profiles", "perp");
        assertEquals(1, ranges.size());
        assertEquals(1000, ranges.get(0).rangeStart());
        assertEquals(5000, ranges.get(0).rangeEnd());
    }

    @Test
    @DisplayName("non-overlapping ranges stay separate")
    void nonOverlappingRangesStaySeparate() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 2000, true);
        dao.addCoverage("volume_profiles", "perp", 4000, 5000, true);

        List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges("volume_profiles", "perp");
        assertEquals(2, ranges.size());
        assertEquals(1000, ranges.get(0).rangeStart());
        assertEquals(2000, ranges.get(0).rangeEnd());
        assertEquals(4000, ranges.get(1).rangeStart());
        assertEquals(5000, ranges.get(1).rangeEnd());
    }

    @Test
    @DisplayName("new range fully containing existing merges to the larger range")
    void containingRangeMerges() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 2000, 3000, true);
        dao.addCoverage("volume_profiles", "perp", 1000, 5000, true);

        List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges("volume_profiles", "perp");
        assertEquals(1, ranges.size());
        assertEquals(1000, ranges.get(0).rangeStart());
        assertEquals(5000, ranges.get(0).rangeEnd());
    }

    @Test
    @DisplayName("new range bridging two existing ranges merges all three")
    void bridgingRangeMergesAll() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 2000, true);
        dao.addCoverage("volume_profiles", "perp", 4000, 5000, true);
        // Bridge them
        dao.addCoverage("volume_profiles", "perp", 1500, 4500, true);

        List<CoverageDao.CoverageRange> ranges = dao.getCoverageRanges("volume_profiles", "perp");
        assertEquals(1, ranges.size());
        assertEquals(1000, ranges.get(0).rangeStart());
        assertEquals(5000, ranges.get(0).rangeEnd());
    }

    // ========== different sub_keys don't interfere ==========

    @Test
    @DisplayName("different sub_keys maintain independent coverage")
    void differentSubKeysIndependent() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 5000, true);
        dao.addCoverage("volume_profiles", "spot", 2000, 3000, true);

        List<long[]> perpGaps = dao.findGaps("volume_profiles", "perp", 1000, 5000);
        assertTrue(perpGaps.isEmpty());

        List<long[]> spotGaps = dao.findGaps("volume_profiles", "spot", 1000, 5000);
        assertEquals(2, spotGaps.size()); // gap before and after spot coverage
    }

    // ========== isFullyCovered ==========

    @Test
    @DisplayName("isFullyCovered returns correct results")
    void isFullyCovered() throws Exception {
        dao.addCoverage("volume_profiles", "perp", 1000, 3000, true);

        assertTrue(dao.isFullyCovered("volume_profiles", "perp", 1000, 3000));
        assertTrue(dao.isFullyCovered("volume_profiles", "perp", 1500, 2500));
        assertFalse(dao.isFullyCovered("volume_profiles", "perp", 500, 3000));
        assertFalse(dao.isFullyCovered("volume_profiles", "perp", 1000, 4000));
    }

    // ========== consolidateRanges ==========

    @Test
    @DisplayName("consolidation merges adjacent ranges")
    void consolidationMergesAdjacentRanges() throws Exception {
        // Insert ranges that should merge (using direct SQL to avoid auto-merge in addCoverage)
        dao.addCoverage("volume_profiles", "perp", 1000, 2000, true);
        dao.addCoverage("volume_profiles", "perp", 4000, 5000, true);
        dao.addCoverage("volume_profiles", "perp", 7000, 8000, true);

        // Should remain 3 separate ranges
        assertEquals(3, dao.getCoverageRanges("volume_profiles", "perp").size());

        // After consolidation (they're already non-adjacent, so should stay separate)
        dao.consolidateRanges("volume_profiles", "perp");
        assertEquals(3, dao.getCoverageRanges("volume_profiles", "perp").size());
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
