package com.tradery.dataservice.data;

import com.tradery.dataservice.data.sqlite.DataStoreType;
import com.tradery.dataservice.data.sqlite.SqliteConnection;
import com.tradery.dataservice.data.sqlite.SqliteDataStore;
import com.tradery.dataservice.data.sqlite.SqliteSchema;
import com.tradery.dataservice.data.sqlite.dao.CoverageDao;
import com.tradery.dataservice.profile.VolumeProfileComputer;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProfileStore gap detection + intersection logic.
 * Uses real SQLite CoverageDao for coverage tracking, records calls to VolumeProfileComputer.
 */
class ProfileStoreTest {

    private static final String SYMBOL = "TESTUSDT";
    private static final String MARKET_TYPE = "perp";

    private static File originalDataDir;
    private File tempDir;
    private SqliteDataStore dataStore;
    private RecordingComputer recordingComputer;
    private StubAggTradesStore stubAggTradesStore;
    private ProfileStore profileStore;

    @BeforeAll
    static void saveOriginalDataDir() {
        originalDataDir = DataConfig.getInstance().getDataDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("profilestore-test").toFile();
        DataConfig.getInstance().setDataDir(tempDir);

        dataStore = new SqliteDataStore();

        // Initialize both profile and aggTrades DBs so coverage DAOs can read/write
        SqliteConnection profileConn = SqliteConnection.forSymbolAndType(SYMBOL, DataStoreType.VOLUME_PROFILES, MARKET_TYPE);
        SqliteSchema.initialize(profileConn, DataStoreType.VOLUME_PROFILES);

        SqliteConnection aggTradesConn = SqliteConnection.forSymbolAndType(SYMBOL, DataStoreType.AGG_TRADES, "binance_perp");
        SqliteSchema.initialize(aggTradesConn, DataStoreType.AGG_TRADES);

        recordingComputer = new RecordingComputer();
        stubAggTradesStore = new StubAggTradesStore();
        profileStore = new ProfileStore(dataStore, stubAggTradesStore, recordingComputer);
    }

    @AfterEach
    void tearDown() {
        SqliteConnection.closeAll();
        DataConfig.getInstance().setDataDir(originalDataDir);
        deleteRecursive(tempDir);
    }

    // ========== No gaps ==========

    @Test
    @DisplayName("no profile gaps: no computation triggered")
    void noProfileGapsNoComputation() throws Exception {
        // Pre-fill profile coverage for the full range
        CoverageDao profileCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.VOLUME_PROFILES, MARKET_TYPE);
        profileCov.addCoverage("volume_profiles", MARKET_TYPE, 1000, 5000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        assertTrue(recordingComputer.calls.isEmpty(), "No computation should be triggered when fully covered");
    }

    // ========== Gap with full aggTrades coverage ==========

    @Test
    @DisplayName("profile gap with full aggTrades coverage: computes for full gap")
    void profileGapWithFullAggTrades() throws Exception {
        // No profile coverage, but aggTrades cover the full range
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 1000, 5000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        assertEquals(1, recordingComputer.calls.size(), "Should compute for the full gap");
        var call = recordingComputer.calls.get(0);
        assertEquals(SYMBOL, call.symbol);
        assertEquals(MARKET_TYPE, call.marketType);
        assertEquals(1000, call.start);
        assertEquals(5000, call.end);
    }

    // ========== Gap with partial aggTrades overlap ==========

    @Test
    @DisplayName("profile gap with partial aggTrades overlap: computes only intersection")
    void profileGapWithPartialAggTrades() throws Exception {
        // Profile gap: 1000 - 5000 (no coverage)
        // AggTrades coverage: 2000 - 4000 only
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 2000, 4000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        assertEquals(1, recordingComputer.calls.size(), "Should compute for the intersection only");
        var call = recordingComputer.calls.get(0);
        assertEquals(2000, call.start, "Intersection start should be aggTrades start");
        assertEquals(4000, call.end, "Intersection end should be aggTrades end");
    }

    @Test
    @DisplayName("multiple aggTrades islands within a profile gap")
    void multipleAggTradesIslands() throws Exception {
        // Profile gap: 1000 - 10000 (no coverage)
        // AggTrades coverage: two islands
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 2000, 4000, true);
        aggCov.addCoverage("agg_trades", "default", 7000, 9000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 10000);

        assertEquals(2, recordingComputer.calls.size(), "Should compute for each intersection");
        assertEquals(2000, recordingComputer.calls.get(0).start);
        assertEquals(4000, recordingComputer.calls.get(0).end);
        assertEquals(7000, recordingComputer.calls.get(1).start);
        assertEquals(9000, recordingComputer.calls.get(1).end);
    }

    // ========== Gap with no aggTrades ==========

    @Test
    @DisplayName("profile gap with no aggTrades: no computation, no crash")
    void profileGapWithNoAggTrades() throws Exception {
        // No aggTrades coverage at all
        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        assertTrue(recordingComputer.calls.isEmpty(), "Should not compute when no aggTrades available");
    }

    // ========== Coverage stamped after computation ==========

    @Test
    @DisplayName("profile coverage is stamped after computation")
    void coverageStampedAfterComputation() throws Exception {
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 1000, 5000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        // First call should trigger computation
        assertEquals(1, recordingComputer.calls.size());

        // Second call should find no gaps (coverage was stamped)
        recordingComputer.calls.clear();
        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);
        assertTrue(recordingComputer.calls.isEmpty(), "Second call should find no gaps");
    }

    // ========== Background download callback ==========

    @Test
    @DisplayName("background download triggers completion callback")
    void backgroundDownloadTriggersCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> callbackSymbols = new ArrayList<>();

        profileStore.setCompletionCallback(new BackfillCompletionCallback() {
            @Override
            public void onProfileBackfillComplete(String symbol, String marketType, long start, long end) {
                callbackSymbols.add(symbol);
                latch.countDown();
            }

            @Override
            public void onSpectrumBackfillComplete(String symbol, long start, long end) {}
        });

        // Provide aggTrades coverage so phase 1 works, background phase 2 also runs
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 1000, 5000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        // Wait for background thread
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback should fire within 5 seconds");
        assertEquals(1, callbackSymbols.size());
        assertEquals(SYMBOL, callbackSymbols.get(0));
    }

    // ========== Partial profile coverage with partial aggTrades ==========

    @Test
    @DisplayName("partial profile coverage: only uncovered intersection is computed")
    void partialProfileCoverage() throws Exception {
        // Profile covers 1000-3000, gap at 3001-5000
        CoverageDao profileCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.VOLUME_PROFILES, MARKET_TYPE);
        profileCov.addCoverage("volume_profiles", MARKET_TYPE, 1000, 3000, true);

        // AggTrades cover 2000-5000
        CoverageDao aggCov = dataStore.forSymbol(SYMBOL).coverageFor(DataStoreType.AGG_TRADES, "binance_perp");
        aggCov.addCoverage("agg_trades", "default", 2000, 5000, true);

        profileStore.ensureCoverage(SYMBOL, MARKET_TYPE, 1000, 5000);

        assertEquals(1, recordingComputer.calls.size());
        var call = recordingComputer.calls.get(0);
        // Gap is 3001-5000, aggTrades 2000-5000, intersection is 3001-5000
        assertEquals(3001, call.start);
        assertEquals(5000, call.end);
    }

    // ========== Test helpers ==========

    /**
     * Records calls to compute() without doing actual work.
     */
    static class RecordingComputer extends VolumeProfileComputer {
        final List<ComputeCall> calls = new ArrayList<>();

        RecordingComputer() {
            super(null, null); // null deps — we override compute()
        }

        @Override
        public void compute(String symbol, String marketType, long startTime, long endTime) throws IOException {
            calls.add(new ComputeCall(symbol, marketType, startTime, endTime));
        }

        record ComputeCall(String symbol, String marketType, long start, long end) {}
    }

    /**
     * Stub AggTradesStore that does nothing on ensureCached.
     */
    static class StubAggTradesStore extends AggTradesStore {
        StubAggTradesStore() {
            super(null, null); // null deps — we override ensureCached()
        }

        @Override
        public void ensureCached(String symbol, long startTime, long endTime, String marketType) {
            // No-op
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
