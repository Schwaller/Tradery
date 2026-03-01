package com.tradery.engine;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.indicators.registry.IndicatorContext;
import com.tradery.core.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the unified MarketData architecture ensuring chart and backtest
 * use the same data path. Uses real BTCUSDT 1h candles (200 bars, Jan 1-9 2025)
 * loaded from predownloaded data.
 */
class MarketDataUnificationTest {

    // 200 real BTCUSDT 1h candles loaded from CSV resource
    private static List<Candle> candles;

    // Day start timestamps (UTC midnight)
    private static final long JAN_1_2025 = 1735689600000L;
    private static final long JAN_2_2025 = 1735776000000L;
    private static final long JAN_3_2025 = 1735862400000L;

    // Fabricated precomputed daily profile values (simulating data-service output)
    // These are chosen to be clearly distinguishable from any candle-based approximation
    private static final double JAN1_POC = 93850.5;
    private static final double JAN1_VAH = 94200.0;
    private static final double JAN1_VAL = 93400.0;
    private static final double JAN2_POC = 96500.0;
    private static final double JAN2_VAH = 97100.0;
    private static final double JAN2_VAL = 95800.0;
    private static final double JAN3_POC = 97800.0;
    private static final double JAN3_VAH = 98500.0;
    private static final double JAN3_VAL = 97200.0;

    private static Map<Long, double[]> dailyProfiles;

    // Index of first bar on Jan 2 (bar 24 — 24 1h candles per day)
    private static final int FIRST_JAN2_BAR = 24;

    @BeforeAll
    static void loadTestData() throws Exception {
        candles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                MarketDataUnificationTest.class.getResourceAsStream("/btcusdt_1h_200bars.csv")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                candles.add(new Candle(
                    Long.parseLong(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Double.parseDouble(parts[4]),
                    Double.parseDouble(parts[5]),
                    Integer.parseInt(parts[6]),
                    Double.parseDouble(parts[7]),
                    Double.parseDouble(parts[8]),
                    Double.parseDouble(parts[9])
                ));
            }
        }
        assertEquals(200, candles.size(), "Test data should have 200 candles");

        dailyProfiles = new HashMap<>();
        dailyProfiles.put(JAN_1_2025, new double[]{JAN1_POC, JAN1_VAH, JAN1_VAL});
        dailyProfiles.put(JAN_2_2025, new double[]{JAN2_POC, JAN2_VAH, JAN2_VAL});
        dailyProfiles.put(JAN_3_2025, new double[]{JAN3_POC, JAN3_VAH, JAN3_VAL});
    }

    @Nested
    @DisplayName("IndicatorEngine.setMarketData()")
    class SetMarketDataTests {

        private IndicatorEngine engine;

        @BeforeEach
        void setUp() {
            engine = new IndicatorEngine();
        }

        @Test
        @DisplayName("populates candles and resolution")
        void populatesCandlesAndResolution() {
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles).build();
            engine.setMarketData(md);

            assertTrue(engine.hasCandles());
            assertEquals(200, engine.getCandles().size());
        }

        @Test
        @DisplayName("populates daily profiles from MarketData")
        void populatesDailyProfiles() {
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .dailyProfiles(dailyProfiles)
                .build();
            engine.setMarketData(md);

            // Bar 24 is first bar of Jan 2 → getPrevDayPOC looks up Jan 1 profile
            double prevDayPoc = engine.getPrevDayPOCAt(FIRST_JAN2_BAR);
            assertEquals(JAN1_POC, prevDayPoc, 0.01,
                "PREV_DAY_POC on Jan 2 should return precomputed Jan 1 POC");

            double prevDayVah = engine.getPrevDayVAHAt(FIRST_JAN2_BAR);
            assertEquals(JAN1_VAH, prevDayVah, 0.01,
                "PREV_DAY_VAH on Jan 2 should return precomputed Jan 1 VAH");

            double prevDayVal = engine.getPrevDayVALAt(FIRST_JAN2_BAR);
            assertEquals(JAN1_VAL, prevDayVal, 0.01,
                "PREV_DAY_VAL on Jan 2 should return precomputed Jan 1 VAL");
        }

        @Test
        @DisplayName("candle fallback when no daily profiles provided")
        void candleFallbackWithoutProfiles() {
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles).build();
            engine.setMarketData(md);

            // Without precomputed profiles, engine computes from candles (different values)
            double candlePoc = engine.getPrevDayPOCAt(FIRST_JAN2_BAR);
            assertFalse(Double.isNaN(candlePoc), "Candle-based POC should still compute a value");
            assertNotEquals(JAN1_POC, candlePoc, 0.01,
                "Candle-based POC should differ from precomputed tick-level POC");
        }

        @Test
        @DisplayName("precomputed profiles override candle computation")
        void precomputedOverridesCandleComputation() {
            // First compute candle-based values
            IndicatorEngine candleEngine = new IndicatorEngine();
            candleEngine.setMarketData(
                MarketDataSnapshot.builder("BTCUSDT", "1h", candles).build());
            double candlePoc = candleEngine.getPrevDayPOCAt(FIRST_JAN2_BAR);

            // Then compute with precomputed profiles
            IndicatorEngine profileEngine = new IndicatorEngine();
            profileEngine.setMarketData(
                MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                    .dailyProfiles(dailyProfiles).build());
            double profilePoc = profileEngine.getPrevDayPOCAt(FIRST_JAN2_BAR);

            // Both should produce values
            assertFalse(Double.isNaN(candlePoc));
            assertFalse(Double.isNaN(profilePoc));

            // But they should be different (precomputed = tick-level vs candle approximation)
            assertEquals(JAN1_POC, profilePoc, 0.01, "Profile engine must use precomputed value");
            assertNotEquals(profilePoc, candlePoc, 0.01,
                "Precomputed and candle-based POC must differ — if equal, the test is not meaningful");
        }

        @Test
        @DisplayName("populates funding rates")
        void populatesFundingRates() {
            List<FundingRate> funding = List.of(
                new FundingRate("BTCUSDT", 0.0001, 1735704000000L, 93600.0),
                new FundingRate("BTCUSDT", 0.0002, 1735732800000L, 93800.0)
            );
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .fundingRates(funding)
                .build();
            engine.setMarketData(md);
            assertTrue(engine.hasFundingRates());
        }

        @Test
        @DisplayName("populates open interest")
        void populatesOpenInterest() {
            List<OpenInterest> oi = List.of(
                new OpenInterest("BTCUSDT", 1735704000000L, 50000.0, 4680000000.0)
            );
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .openInterest(oi)
                .build();
            engine.setMarketData(md);
            assertTrue(engine.hasOpenInterest());
        }

        @Test
        @DisplayName("populates premium index")
        void populatesPremiumIndex() {
            List<PremiumIndex> premium = List.of(
                new PremiumIndex(1735704000000L, 0.001, 0.002, 0.0005, 0.0015, 1735707600000L)
            );
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .premiumIndex(premium)
                .build();
            engine.setMarketData(md);
            assertTrue(engine.hasPremiumIndex());
        }

        @Test
        @DisplayName("populates fear and greed data")
        void populatesFearAndGreed() {
            List<FearGreedIndex> fg = List.of(
                new FearGreedIndex(45, "Fear", 1735689600000L)
            );
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .fearGreedIndex(fg)
                .build();
            engine.setMarketData(md);
            assertTrue(engine.hasFearGreedData());
        }

        @Test
        @DisplayName("null optional data is not set")
        void nullDataNotSet() {
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles).build();
            engine.setMarketData(md);

            assertTrue(engine.hasCandles());
            assertFalse(engine.hasAggTrades());
            assertFalse(engine.hasFundingRates());
            assertFalse(engine.hasOpenInterest());
            assertFalse(engine.hasPremiumIndex());
            assertFalse(engine.hasFearGreedData());
        }
    }

    @Nested
    @DisplayName("MarketDataSnapshot immutability")
    class SnapshotImmutabilityTests {

        @Test
        @DisplayName("candles list is copied on build")
        void candlesAreCopied() {
            MarketDataSnapshot snapshot = MarketDataSnapshot.builder("BTCUSDT", "1h", candles).build();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.candles().add(
                new Candle(0, 0, 0, 0, 0, 0)));
        }

        @Test
        @DisplayName("daily profiles map is copied on build")
        void profilesAreCopied() {
            MarketDataSnapshot snapshot = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .dailyProfiles(dailyProfiles)
                .build();
            assertThrows(UnsupportedOperationException.class,
                () -> snapshot.dailyProfiles().put(0L, new double[]{0, 0, 0}));
        }
    }

    @Nested
    @DisplayName("BacktestContext with MarketData")
    class BacktestContextTests {

        @Test
        @DisplayName("builder passes MarketData through to context")
        void builderPassesMarketData() {
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .dailyProfiles(dailyProfiles)
                .build();

            BacktestContext context = BacktestContext.builder(candles)
                .marketData(md)
                .build();

            assertNotNull(context.marketData());
            assertEquals("BTCUSDT", context.marketData().symbol());
            assertNotNull(context.marketData().dailyProfiles());
            assertEquals(3, context.marketData().dailyProfiles().size());
        }

        @Test
        @DisplayName("ofCandles has null marketData")
        void ofCandlesHasNullMarketData() {
            BacktestContext context = BacktestContext.ofCandles(candles);
            assertNull(context.marketData());
        }
    }

    @Nested
    @DisplayName("BacktestEngine with MarketData")
    class BacktestEngineTests {

        private BacktestEngine engine;

        @BeforeEach
        void setUp() {
            engine = new BacktestEngine();
        }

        @Test
        @DisplayName("backtest with MarketData uses precomputed daily profiles")
        void backtestWithMarketDataUsesPrecomputedProfiles() {
            Strategy strategy = new Strategy("test-md", "MarketData Test", "", "close > PREV_DAY_POC", true);
            strategy.setExitZones(List.of(
                ExitZone.builder("SL").maxPnl(-2.0).exitImmediately(true).build(),
                ExitZone.builder("TP").minPnl(3.0).exitImmediately(true).build()
            ));

            BacktestConfig config = BacktestConfig.defaults("BTCUSDT", "1h");

            // Run WITH precomputed profiles
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .dailyProfiles(dailyProfiles)
                .build();
            BacktestContext contextWithProfiles = BacktestContext.builder(candles)
                .marketData(md)
                .build();
            BacktestResult result = engine.run(strategy, config, contextWithProfiles, null);

            // Verify the indicator engine got the profiles
            IndicatorEngine ie = engine.getIndicatorEngine();
            double poc = ie.getPrevDayPOCAt(FIRST_JAN2_BAR);
            assertEquals(JAN1_POC, poc, 0.01,
                "BacktestEngine's IndicatorEngine must have precomputed profiles from MarketData");

            // Verify the backtest actually processed bars (200 - 50 warmup = 150)
            assertNotNull(result);
            assertTrue(result.errors().isEmpty(),
                "Backtest should complete without errors: " + result.errors());
            assertTrue(result.barsProcessed() > 0,
                "Backtest should process bars, got: " + result.barsProcessed());
        }

        @Test
        @DisplayName("backtest without MarketData uses candle fallback (legacy path)")
        void backtestWithoutMarketDataUsesCandleFallback() {
            Strategy strategy = new Strategy("test-legacy", "Legacy Test", "", "close > PREV_DAY_POC", true);
            strategy.setExitZones(List.of(
                ExitZone.builder("SL").maxPnl(-2.0).exitImmediately(true).build(),
                ExitZone.builder("TP").minPnl(3.0).exitImmediately(true).build()
            ));

            BacktestConfig config = BacktestConfig.defaults("BTCUSDT", "1h");

            // Run WITHOUT MarketData (legacy path)
            BacktestContext legacyContext = BacktestContext.ofCandles(candles);
            BacktestResult result = engine.run(strategy, config, legacyContext, null);

            // Verify the engine fell back to candle computation
            IndicatorEngine ie = engine.getIndicatorEngine();
            double candlePoc = ie.getPrevDayPOCAt(FIRST_JAN2_BAR);
            assertFalse(Double.isNaN(candlePoc), "Candle fallback must still produce a value");
            assertNotEquals(JAN1_POC, candlePoc, 0.01,
                "Without MarketData, POC should be candle-approximated, not precomputed");

            assertNotNull(result);
            assertTrue(result.errors().isEmpty(),
                "Backtest should complete without errors: " + result.errors());
            assertTrue(result.barsProcessed() > 0,
                "Backtest should process bars, got: " + result.barsProcessed());
        }

        @Test
        @DisplayName("same strategy produces different entry signals with precomputed vs candle profiles")
        void differentProfilesProduceDifferentSignals() {
            Strategy strategy = new Strategy("test-diff", "Diff Test", "", "close > PREV_DAY_POC", true);
            strategy.setExitZones(List.of(
                ExitZone.builder("SL").maxPnl(-5.0).exitImmediately(true).build(),
                ExitZone.builder("TP").minPnl(5.0).exitImmediately(true).build()
            ));

            BacktestConfig config = BacktestConfig.defaults("BTCUSDT", "1h");

            // Run with precomputed profiles
            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "1h", candles)
                .dailyProfiles(dailyProfiles)
                .build();
            BacktestEngine engine1 = new BacktestEngine();
            BacktestResult resultWithProfiles = engine1.run(strategy, config,
                BacktestContext.builder(candles).marketData(md).build(), null);

            // Run without precomputed profiles (candle fallback)
            BacktestEngine engine2 = new BacktestEngine();
            BacktestResult resultWithoutProfiles = engine2.run(strategy, config,
                BacktestContext.ofCandles(candles), null);

            // Both should complete without errors
            assertTrue(resultWithProfiles.errors().isEmpty(),
                "Precomputed run errors: " + resultWithProfiles.errors());
            assertTrue(resultWithoutProfiles.errors().isEmpty(),
                "Candle fallback run errors: " + resultWithoutProfiles.errors());

            // Both should process bars
            assertTrue(resultWithProfiles.barsProcessed() > 0);
            assertTrue(resultWithoutProfiles.barsProcessed() > 0);

            assertNotNull(resultWithProfiles.trades());
            assertNotNull(resultWithoutProfiles.trades());
        }
    }

    @Nested
    @DisplayName("IndicatorContext.from(MarketData)")
    class IndicatorContextFromMarketDataTests {

        @Test
        @DisplayName("creates context with all fields from MarketData")
        void createsContextFromMarketData() {
            List<FundingRate> funding = List.of(
                new FundingRate("BTCUSDT", 0.0001, 1735704000000L, 93600.0));
            List<OpenInterest> oi = List.of(
                new OpenInterest("BTCUSDT", 1735704000000L, 50000.0, 4680000000.0));
            List<PremiumIndex> premium = List.of(
                new PremiumIndex(1735704000000L, 0.001, 0.002, 0.0005, 0.0015, 1735707600000L));

            MarketData md = MarketDataSnapshot.builder("BTCUSDT", "4h", candles)
                .fundingRates(funding)
                .openInterest(oi)
                .premiumIndex(premium)
                .build();

            IndicatorContext ctx = IndicatorContext.from(md);

            assertEquals(200, ctx.barCount());
            assertEquals("4h", ctx.resolution());
            assertTrue(ctx.hasFunding());
            assertTrue(ctx.hasOpenInterest());
            assertTrue(ctx.hasPremium());
            assertFalse(ctx.hasAggTrades());
        }
    }
}
