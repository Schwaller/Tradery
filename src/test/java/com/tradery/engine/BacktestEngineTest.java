package com.tradery.engine;

import com.tradery.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BacktestEngine covering various trading scenarios.
 */
class BacktestEngineTest {

    private BacktestEngine engine;
    private Strategy baseStrategy;
    private BacktestConfig baseConfig;

    @BeforeEach
    void setUp() {
        engine = new BacktestEngine();

        baseStrategy = new Strategy();
        baseStrategy.setId("test-strategy");
        baseStrategy.setName("Test Strategy");
        baseStrategy.setEntry("true");  // Always enter
        baseStrategy.setExit("false");  // Never exit (manually)
        baseStrategy.setStopLossType("none");
        baseStrategy.setTakeProfitType("none");
        baseStrategy.setMaxOpenTrades(1);
        baseStrategy.setMinCandlesBetweenTrades(0);
        baseStrategy.setMinBarsBeforeExit(0);
        baseStrategy.setDcaEnabled(false);

        baseConfig = new BacktestConfig(
            "TESTUSDT",
            "1h",
            0L,
            System.currentTimeMillis(),
            10000.0,
            "fixed_dollar",
            1000.0,
            0.001  // 0.1% commission
        );
    }

    // Helper to create flat candles at a fixed price (need 100+ for warmup)
    private List<Candle> createFlatCandles(double price, int count) {
        List<Candle> candles = new ArrayList<>();
        long time = System.currentTimeMillis() - (count) * 3600000L;

        for (int i = 0; i < count; i++) {
            candles.add(new Candle(time + i * 3600000L, price, price + 1, price - 1, price, 1000.0));
        }
        return candles;
    }

    // Helper to create trending candles
    private List<Candle> createTrendingCandles(double startPrice, double endPrice, int count) {
        List<Candle> candles = new ArrayList<>();
        long time = System.currentTimeMillis() - count * 3600000L;
        double step = (endPrice - startPrice) / count;

        for (int i = 0; i < count; i++) {
            double close = startPrice + step * (i + 1);
            double open = startPrice + step * i;
            double high = Math.max(open, close) + 5;
            double low = Math.min(open, close) - 5;
            candles.add(new Candle(time + i * 3600000L, open, high, low, close, 1000.0));
        }
        return candles;
    }

    @Nested
    @DisplayName("Basic Entry/Exit Tests")
    class BasicEntryExitTests {

        @Test
        @DisplayName("Opens trade when entry condition is met (true)")
        void opensTradeOnEntrySignal() {
            baseStrategy.setEntry("true");  // Always true
            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            assertTrue(result.isSuccessful());
            assertFalse(result.trades().isEmpty(), "Should have at least one trade");
        }

        @Test
        @DisplayName("Respects maxOpenTrades limit")
        void respectsMaxOpenTradesLimit() {
            baseStrategy.setEntry("true");
            baseStrategy.setExit("false");
            baseStrategy.setMaxOpenTrades(3);
            baseStrategy.setMinCandlesBetweenTrades(5);

            List<Candle> candles = createFlatCandles(100.0, 200);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            // At any point, should not exceed maxOpenTrades
            long openTrades = result.trades().stream().filter(Trade::isOpen).count();
            assertTrue(openTrades <= 3, "Should not exceed maxOpenTrades");
        }

        @Test
        @DisplayName("Applies minimum candles between trades")
        void appliesMinCandlesBetween() {
            baseStrategy.setEntry("true");
            baseStrategy.setMinCandlesBetweenTrades(10);
            baseStrategy.setMaxOpenTrades(10);

            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> trades = result.trades();
            for (int i = 1; i < trades.size(); i++) {
                int barDiff = trades.get(i).entryBar() - trades.get(i - 1).entryBar();
                assertTrue(barDiff >= 10, "Should have at least 10 bars between trades");
            }
        }
    }

    @Nested
    @DisplayName("Stop Loss Tests")
    class StopLossTests {

        @Test
        @DisplayName("Fixed stop loss triggers at correct level")
        void fixedStopLossTriggers() {
            baseStrategy.setEntry("true");
            baseStrategy.setStopLossType("fixed_percent");
            baseStrategy.setStopLossValue(5.0);  // 5% stop loss

            // Entry at 100, then price drops below stop level
            List<Candle> candles = new ArrayList<>();
            candles.addAll(createFlatCandles(100.0, 50));  // Entry price
            candles.addAll(createTrendingCandles(100.0, 90.0, 50));  // Drop 10%

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> stoppedTrades = result.trades().stream()
                .filter(t -> "stop_loss".equals(t.exitReason()))
                .toList();
            assertFalse(stoppedTrades.isEmpty(), "Should have stop loss exits");
        }

        @Test
        @DisplayName("Trailing stop follows price and locks in profit")
        void trailingStopLocksProfit() {
            baseStrategy.setEntry("true");
            baseStrategy.setStopLossType("trailing_percent");
            baseStrategy.setStopLossValue(5.0);  // 5% trailing stop
            baseStrategy.setMaxOpenTrades(1);

            // Price goes up 20%, then drops 8% from high (triggers 5% trailing stop)
            List<Candle> candles = new ArrayList<>();
            candles.addAll(createFlatCandles(100.0, 50));  // Entry
            candles.addAll(createTrendingCandles(100.0, 120.0, 30));  // Up 20%
            candles.addAll(createTrendingCandles(120.0, 108.0, 30));  // Down 10% from high

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> trailingStops = result.trades().stream()
                .filter(t -> "trailing_stop".equals(t.exitReason()))
                .toList();
            assertFalse(trailingStops.isEmpty(), "Should have trailing stop exits");

            // Verify trade was profitable
            Trade trade = trailingStops.get(0);
            assertTrue(trade.pnlPercent() > 0, "Trailing stop should lock in profit");
        }
    }

    @Nested
    @DisplayName("Take Profit Tests")
    class TakeProfitTests {

        @Test
        @DisplayName("Take profit triggers at target level")
        void takeProfitTriggers() {
            baseStrategy.setEntry("true");
            baseStrategy.setTakeProfitType("fixed_percent");
            baseStrategy.setTakeProfitValue(10.0);  // 10% take profit
            baseStrategy.setMaxOpenTrades(1);

            // Price rises 15% after entry
            List<Candle> candles = new ArrayList<>();
            candles.addAll(createFlatCandles(100.0, 50));
            candles.addAll(createTrendingCandles(100.0, 120.0, 50));  // Up 20%

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> tpTrades = result.trades().stream()
                .filter(t -> "take_profit".equals(t.exitReason()))
                .toList();
            assertFalse(tpTrades.isEmpty(), "Should have take profit exits");

            Trade trade = tpTrades.get(0);
            assertTrue(trade.pnlPercent() >= 8.0, "Should have ~10% profit (minus commission)");
        }
    }

    @Nested
    @DisplayName("Exit Zone Tests")
    class ExitZoneTests {

        @Test
        @DisplayName("Emergency exit zone triggers immediately on deep loss")
        void emergencyZoneTriggersImmediately() {
            baseStrategy.setEntry("true");
            baseStrategy.setStopLossType("none");
            baseStrategy.setMaxOpenTrades(1);

            // Set up failure zone at -5% with exitImmediately
            List<ExitZone> zones = List.of(
                new ExitZone("Failure", null, -5.0, "", "none", null, "none", null, true, 0),
                new ExitZone("Default", -5.0, null, "", "none", null, "none", null, false, 0)
            );
            baseStrategy.setExitZones(zones);

            // Price drops 10% immediately
            List<Candle> candles = new ArrayList<>();
            candles.addAll(createFlatCandles(100.0, 50));
            candles.addAll(createTrendingCandles(100.0, 85.0, 50));  // Drop 15%

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> zoneExits = result.trades().stream()
                .filter(t -> "zone_exit".equals(t.exitReason()))
                .toList();
            assertFalse(zoneExits.isEmpty(), "Should have zone exits");
            assertEquals("Failure", zoneExits.get(0).exitZone());
        }

        @Test
        @DisplayName("Profit protection zone applies trailing stop")
        void profitZoneAppliesTrailingStop() {
            baseStrategy.setEntry("true");
            baseStrategy.setStopLossType("none");
            baseStrategy.setMaxOpenTrades(1);

            // Set up zones - profit zone with trailing stop
            List<ExitZone> zones = List.of(
                new ExitZone("Default", null, 5.0, "", "none", null, "none", null, false, 0),
                new ExitZone("Protect", 5.0, null, "", "trailing_percent", 2.0, "none", null, false, 0)
            );
            baseStrategy.setExitZones(zones);

            // Price goes up then pulls back
            List<Candle> candles = new ArrayList<>();
            candles.addAll(createFlatCandles(100.0, 50));
            candles.addAll(createTrendingCandles(100.0, 115.0, 30));  // Up 15%
            candles.addAll(createTrendingCandles(115.0, 105.0, 30));  // Drop 8.7% from high

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> trailingStops = result.trades().stream()
                .filter(t -> "trailing_stop".equals(t.exitReason()))
                .toList();
            assertFalse(trailingStops.isEmpty(), "Should have trailing stop exits from profit zone");

            Trade trade = trailingStops.get(0);
            assertTrue(trade.pnlPercent() > 3.0, "Should lock in at least some profit");
        }

        @Test
        @DisplayName("Fallback zone does not trigger exitImmediately")
        void fallbackZoneNoImmediateExit() {
            baseStrategy.setEntry("true");
            baseStrategy.setStopLossType("none");
            baseStrategy.setTakeProfitType("none");
            baseStrategy.setMaxOpenTrades(1);

            // Set up zones with a gap - only Failure (<-10%) and Profit (>10%) zones
            List<ExitZone> zones = List.of(
                new ExitZone("Failure", null, -10.0, "", "none", null, "none", null, true, 0),
                new ExitZone("Profit", 10.0, null, "", "none", null, "none", null, false, 0)
            );
            baseStrategy.setExitZones(zones);

            // Price stays flat (P&L near 0, doesn't match any zone directly)
            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            // Trade should still be open or closed at end, not by zone_exit
            List<Trade> zoneExits = result.trades().stream()
                .filter(t -> "zone_exit".equals(t.exitReason()))
                .toList();
            assertTrue(zoneExits.isEmpty(), "Should not have premature zone exits when using fallback");
        }
    }

    @Nested
    @DisplayName("DCA Tests")
    class DCATests {

        @BeforeEach
        void setUpDCA() {
            baseStrategy.setDcaEnabled(true);
            baseStrategy.setDcaMaxEntries(3);
            baseStrategy.setDcaBarsBetween(10);
            baseStrategy.setDcaMode("continue");
        }

        @Test
        @DisplayName("DCA creates multiple entries with same group ID")
        void dcaCreatesMultipleEntries() {
            baseStrategy.setEntry("true");

            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> dcaTrades = result.trades().stream()
                .filter(t -> t.groupId() != null && t.groupId().startsWith("dca-"))
                .toList();

            assertTrue(dcaTrades.size() >= 2, "Should have multiple DCA entries");

            // All DCA trades should have same group ID
            String groupId = dcaTrades.get(0).groupId();
            assertTrue(dcaTrades.stream().allMatch(t -> groupId.equals(t.groupId())),
                "All DCA entries should share same group ID");
        }

        @Test
        @DisplayName("DCA respects bars between entries")
        void dcaRespectsBarsBetween() {
            baseStrategy.setEntry("true");
            baseStrategy.setDcaBarsBetween(15);

            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            List<Trade> trades = result.trades().stream()
                .filter(t -> t.groupId() != null)
                .sorted((a, b) -> Integer.compare(a.entryBar(), b.entryBar()))
                .toList();

            for (int i = 1; i < trades.size(); i++) {
                int barDiff = trades.get(i).entryBar() - trades.get(i - 1).entryBar();
                assertTrue(barDiff >= 15, "Should have at least 15 bars between DCA entries");
            }
        }

        @Test
        @DisplayName("DCA counts as single position against maxOpenTrades")
        void dcaCountsAsSinglePosition() {
            baseStrategy.setEntry("true");
            baseStrategy.setMaxOpenTrades(1);
            baseStrategy.setDcaMaxEntries(3);
            baseStrategy.setDcaBarsBetween(5);

            List<Candle> candles = createFlatCandles(100.0, 100);

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            // All trades should have the same group ID (one position with multiple DCA entries)
            List<String> uniqueGroups = result.trades().stream()
                .filter(t -> t.groupId() != null)
                .map(Trade::groupId)
                .distinct()
                .toList();

            // With maxOpenTrades=1 and DCA, we should have exactly 1 position group
            assertEquals(1, uniqueGroups.size(), "DCA group should count as single position");
        }
    }

    @Nested
    @DisplayName("Metrics Calculation Tests")
    class MetricsTests {

        @Test
        @DisplayName("Profitable trades increase final equity")
        void profitableTradesIncreaseEquity() {
            baseStrategy.setEntry("true");
            baseStrategy.setTakeProfitType("fixed_percent");
            baseStrategy.setTakeProfitValue(5.0);
            baseStrategy.setMaxOpenTrades(1);
            baseStrategy.setMinCandlesBetweenTrades(20);

            // Multiple profitable moves
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                candles.addAll(createFlatCandles(100.0, 25));
                candles.addAll(createTrendingCandles(100.0, 110.0, 25));  // Win
            }

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            PerformanceMetrics metrics = result.metrics();
            assertTrue(metrics.finalEquity() > 10000.0, "Profitable trades should increase equity");
        }

        @Test
        @DisplayName("Win rate calculated correctly")
        void winRateCalculation() {
            baseStrategy.setEntry("true");
            baseStrategy.setTakeProfitType("fixed_percent");
            baseStrategy.setTakeProfitValue(5.0);
            baseStrategy.setMaxOpenTrades(1);
            baseStrategy.setMinCandlesBetweenTrades(25);

            // All winning trades
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                candles.addAll(createFlatCandles(100.0, 25));
                candles.addAll(createTrendingCandles(100.0, 110.0, 25));
            }

            BacktestResult result = engine.run(baseStrategy, baseConfig, candles, null);

            PerformanceMetrics metrics = result.metrics();
            assertEquals(100.0, metrics.winRate(), 0.1, "All trades should be winners");
        }
    }
}
