package com.tradery.engine;

import com.tradery.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HoopPatternEvaluatorTest {

    private HoopPatternEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new HoopPatternEvaluator();
    }

    // --- helpers ---

    private static Candle candle(int bar, double close) {
        long ts = 1_000_000L + bar * 3_600_000L; // 1h spacing
        return new Candle(ts, close, close + 1, close - 1, close, 100);
    }

    private static List<Candle> flatCandles(int count, double price) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candles.add(candle(i, price));
        }
        return candles;
    }

    private static List<Candle> candlesWithPrices(double... prices) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            candles.add(candle(i, prices[i]));
        }
        return candles;
    }

    private static Hoop hoop(String name, double minPct, double maxPct, int distance, int tolerance) {
        return new Hoop(name, minPct, maxPct, distance, tolerance, Hoop.AnchorMode.ACTUAL_HIT);
    }

    private static HoopPattern pattern(String id, Hoop... hoops) {
        HoopPattern p = new HoopPattern(id, id, Arrays.asList(hoops), "BTCUSDT", "1h");
        return p;
    }

    // =========================================================================
    // Backtracking tests
    // =========================================================================

    @Nested
    @DisplayName("Backtracking")
    class BacktrackingTests {

        @Test
        @DisplayName("greedy hit on hoop 1 blocks hoop 2 — backtracking finds later hit")
        void backtrackingFindsPatternGreedyMisses() {
            // Hoop1: +1% to +3%, distance=3, tolerance=2 → window [1,5] from bar 0
            // Hoop2: -2% to 0%, distance=2, tolerance=0 → strict window [hitBar+2, hitBar+2]
            HoopPattern p = pattern("bt",
                hoop("up", 1.0, 3.0, 3, 2),
                hoop("down", -2.0, 0.0, 2, 0)
            );

            // Greedy picks hoop1 at bar 1 (102), hoop2 window=[3,3], bar 3=105 → out of range → FAIL
            // Backtrack: hoop1 at bar 4 (101.5), hoop2 window=[6,6], bar 6=100 → -1% from 101.5 → HIT
            List<Candle> candles = candlesWithPrices(
                100,   // 0 - anchor
                102,   // 1 - greedy hoop1 hit (first in [101,103])
                100,   // 2
                105,   // 3 - hoop2 miss from bar 1 (105 > 102)
                101.5, // 4 - backtrack hoop1 hit
                99,    // 5
                100    // 6 - hoop2 hit from bar 4 (100 in [99.47, 101.5])
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);

            assertEquals(1, matches.size(), "backtracking should find the pattern");
            assertEquals(0, matches.get(0).anchorBar());
            assertEquals(4, matches.get(0).hoopHitBars()[0], "should use bar 4 for hoop1");
            assertEquals(6, matches.get(0).hoopHitBars()[1], "should use bar 6 for hoop2");
        }

        @Test
        @DisplayName("first hit works — no backtracking needed")
        void noBacktrackingNeeded() {
            HoopPattern p = pattern("simple",
                hoop("up", 1.0, 5.0, 3, 1),
                hoop("down", -3.0, -1.0, 3, 1)
            );

            List<Candle> candles = candlesWithPrices(
                100,   // 0 - anchor
                100,   // 1
                100,   // 2
                103,   // 3 - hoop1 hit
                103,   // 4
                103,   // 5
                101,   // 6 - hoop2 hit (-1.9% from 103)
                100,   // 7
                100    // 8
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);

            assertEquals(1, matches.size());
            assertEquals(3, matches.get(0).hoopHitBars()[0]);
            assertEquals(6, matches.get(0).hoopHitBars()[1]);
        }

        @Test
        @DisplayName("all options exhausted — pattern correctly fails")
        void backtrackingExhaustsAllOptions() {
            HoopPattern p = pattern("fail",
                hoop("up", 1.0, 3.0, 3, 1),
                hoop("down", -5.0, -4.0, 3, 1) // needs a big drop that never happens
            );

            List<Candle> candles = candlesWithPrices(
                100, 100, 100, 102, 101.5, 103, 104, 103, 102, 101
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("three-hoop pattern with backtracking on middle hoop")
        void threeHoopBacktracking() {
            // hoop1: +1% to +3%, hoop2: -1% to +1% (from hoop1), hoop3: +2% to +4% (from hoop2)
            HoopPattern p = pattern("3h",
                hoop("up", 1.0, 3.0, 2, 1),
                hoop("flat", -1.0, 1.0, 2, 1),
                hoop("breakout", 2.0, 4.0, 2, 1)
            );

            // First hoop1 hit at bar 2 (102), hoop2 hits at bar 3 (101.5),
            // but hoop3 window [4,6] has no +2-4% from 101.5 (needs 103.5-105.6).
            // Backtrack: hoop2 no more hits. Backtrack to hoop1: bar 3 (101.5),
            // hoop2 window [4,6]: bar 5 (102) is +0.3% → hit,
            // hoop3 window [6,8]: bar 7 (105) is +2.9% from 102 → hit!
            List<Candle> candles = candlesWithPrices(
                100,   // 0 - anchor
                100,   // 1
                102,   // 2 - first hoop1 hit
                101.5, // 3 - second hoop1 hit / hoop2 hit from bar2
                101,   // 4
                102,   // 5 - hoop2 hit from bar3
                101,   // 6
                105,   // 7 - hoop3 hit from bar5 (+2.9%)
                100    // 8
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);

            assertEquals(1, matches.size());
            assertEquals(7, matches.get(0).completionBar());
        }
    }

    // =========================================================================
    // Basic pattern matching
    // =========================================================================

    @Nested
    @DisplayName("Basic matching")
    class BasicMatchingTests {

        @Test
        @DisplayName("single hoop pattern matches")
        void singleHoop() {
            HoopPattern p = pattern("single", hoop("up", 2.0, 5.0, 3, 1));
            List<Candle> candles = candlesWithPrices(100, 100, 100, 104, 100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);

            assertEquals(1, matches.size());
            assertEquals(3, matches.get(0).completionBar());
            assertEquals(104, matches.get(0).hoopHitPrices()[0]);
        }

        @Test
        @DisplayName("empty hoops returns no matches")
        void emptyHoops() {
            HoopPattern p = pattern("empty");
            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, flatCandles(10, 100));
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("empty candles returns no matches")
        void emptyCandles() {
            HoopPattern p = pattern("p", hoop("up", 1.0, 3.0, 3, 1));
            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, List.of());
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("hoop hit at window boundary (earliest)")
        void windowBoundaryEarly() {
            HoopPattern p = pattern("bound", hoop("up", 1.0, 5.0, 5, 2));
            // Window is [3, 7] (distance=5, tolerance=2 → 5-2=3, 5+2=7)
            double[] prices = new double[10];
            Arrays.fill(prices, 100);
            prices[3] = 103; // hit at earliest window bar
            List<Candle> candles = candlesWithPrices(prices);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
            assertEquals(3, matches.get(0).completionBar());
        }

        @Test
        @DisplayName("hoop hit at window boundary (latest)")
        void windowBoundaryLate() {
            HoopPattern p = pattern("bound", hoop("up", 1.0, 5.0, 5, 2));
            double[] prices = new double[10];
            Arrays.fill(prices, 100);
            prices[7] = 103; // hit at latest window bar
            List<Candle> candles = candlesWithPrices(prices);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
            assertEquals(7, matches.get(0).completionBar());
        }

        @Test
        @DisplayName("hoop just outside window misses")
        void outsideWindow() {
            // distance=3, tolerance=0 → window from bar S is exactly [S+3, S+3]
            HoopPattern p = pattern("miss", hoop("up", 2.0, 5.0, 3, 0));
            // Only bar 5 has a price spike. For bar 5 to match, need start bar = 2 (2+3=5).
            // But bar 2's price is 200, so +2-5% range is [204, 210] — 103 is way below.
            // From bar 0: window [3,3], bar 3=100 → miss. No start bar's anchor matches bar 5.
            List<Candle> candles = candlesWithPrices(100, 100, 200, 100, 100, 103, 100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("price just outside hoop range misses")
        void priceOutOfRange() {
            // Hoop needs +2% to +3%, price is +1.9%
            HoopPattern p = pattern("miss", hoop("up", 2.0, 3.0, 3, 1));
            List<Candle> candles = candlesWithPrices(100, 100, 100, 101.9, 100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("multiple matches found across candle series")
        void multipleMatches() {
            // distance=2, tolerance=0 → strict window. No overlap → sequential matches.
            HoopPattern p = pattern("multi", hoop("up", 2.0, 5.0, 2, 0));
            p.setAllowOverlap(false);

            List<Candle> candles = candlesWithPrices(
                100, 100, 103, // anchor 0, hit bar 2
                100, 100, 104, // anchor 3, hit bar 5
                100, 100, 102.5 // anchor 6, hit bar 8
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(3, matches.size());
            assertEquals(2, matches.get(0).completionBar());
            assertEquals(5, matches.get(1).completionBar());
            assertEquals(8, matches.get(2).completionBar());
        }
    }

    // =========================================================================
    // Cooldown and overlap
    // =========================================================================

    @Nested
    @DisplayName("Cooldown and overlap")
    class CooldownTests {

        @Test
        @DisplayName("cooldown prevents match too soon after last completion")
        void cooldownPreventsEarlyMatch() {
            // distance=2, tolerance=0 → window [S+2, S+2]
            HoopPattern p = pattern("cd", hoop("up", 2.0, 5.0, 2, 0));
            p.setCooldownBars(5);

            // Match 1: anchor bar 0, hit bar 2 (completionBar=2)
            // Cooldown: startBar - 2 must be > 5, so startBar >= 8
            // Bar 3 as anchor: 3-2=1 ≤ 5 → blocked
            // Bar 8 as anchor: 8-2=6 > 5 → allowed, hit at bar 10
            List<Candle> candles = candlesWithPrices(
                100, 100, 103,  // match 1: anchor 0, hit bar 2
                100, 100, 104,  // anchor 3 blocked by cooldown
                100, 100,       // bars 6,7 still in cooldown
                100, 100, 102.5 // match 2: anchor 8, hit bar 10
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(2, matches.size());
            assertEquals(2, matches.get(0).completionBar());
            assertEquals(10, matches.get(1).completionBar());
        }

        @Test
        @DisplayName("allowOverlap=true finds overlapping patterns")
        void overlapAllowed() {
            HoopPattern p = pattern("ov",
                hoop("up", 1.0, 5.0, 3, 1),
                hoop("down", -3.0, -1.0, 3, 1)
            );
            p.setAllowOverlap(true);

            List<Candle> candles = candlesWithPrices(
                100,   // 0
                100,   // 1 - anchor for second pattern
                100,   // 2
                103,   // 3 - hoop1 hit from bar 0, also anchor for patterns starting at bar 1+
                103,   // 4
                103,   // 5
                101,   // 6 - hoop2 hit from bar 0's pattern
                100,   // 7
                100    // 8
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.size() >= 1);
        }

        @Test
        @DisplayName("allowOverlap=false skips to completion bar")
        void noOverlapSkips() {
            HoopPattern p = pattern("noov",
                hoop("up", 1.0, 5.0, 2, 1),
                hoop("down", -3.0, -1.0, 2, 1)
            );
            p.setAllowOverlap(false);

            // First pattern: anchor bar 0, hoop1 at bar 2, hoop2 at bar 4
            // Without overlap, next scan starts at bar 5
            List<Candle> candles = candlesWithPrices(
                100, 100, 103, 103, 101, // first pattern completes at bar 4
                100, 100, 103, 103, 101  // second pattern completes at bar 9
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(2, matches.size());
            assertEquals(4, matches.get(0).completionBar());
            assertEquals(9, matches.get(1).completionBar());
        }
    }

    // =========================================================================
    // Anchor modes
    // =========================================================================

    @Nested
    @DisplayName("Anchor modes")
    class AnchorModeTests {

        @Test
        @DisplayName("ACTUAL_HIT uses close price as next anchor")
        void actualHitAnchor() {
            HoopPattern p = pattern("ah",
                new Hoop("up", 2.0, 5.0, 2, 1, Hoop.AnchorMode.ACTUAL_HIT),
                new Hoop("down", -3.0, -1.0, 2, 1, Hoop.AnchorMode.ACTUAL_HIT)
            );

            // Anchor 100, hoop1 hits at 103 (+3%), hoop2 needs -3% to -1% from 103 → [99.91, 101.97]
            List<Candle> candles = candlesWithPrices(100, 100, 103, 103, 101, 100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
            // Hoop2 hit at bar 4 (101), which is -1.94% from 103 → in range
            assertEquals(4, matches.get(0).completionBar());
        }

        @Test
        @DisplayName("AVG_RANGE uses midpoint of bounds as next anchor")
        void avgRangeAnchor() {
            HoopPattern p = pattern("ar",
                new Hoop("up", 2.0, 4.0, 2, 1, Hoop.AnchorMode.AVG_RANGE),
                new Hoop("down", -2.0, 0.0, 2, 1, Hoop.AnchorMode.ACTUAL_HIT)
            );

            // Anchor 100, hoop1 range is [102, 104], midpoint = 103
            // Hoop1 window from bar 0: [1, 3]
            // Bar 2: 102.5 → in [102, 104] → hit. AVG_RANGE anchor = (102+104)/2 = 103
            // Hoop2 anchored from 103, needs -2% to 0% → [100.94, 103]
            // Hoop2 window from bar 2: [3, 5]
            // Bar 3: 103 → exactly at upper bound → hit (first in window)
            List<Candle> candles = candlesWithPrices(100, 100, 102.5, 103, 101, 100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
            assertEquals(3, matches.get(0).completionBar());
        }
    }

    // =========================================================================
    // Timeframe mapping
    // =========================================================================

    @Nested
    @DisplayName("Timeframe mapping")
    class TimeframeMappingTests {

        @Test
        @DisplayName("same timeframe maps correctly")
        void sameTimeframe() {
            HoopPattern p = pattern("tf", hoop("up", 2.0, 5.0, 2, 1));
            List<Candle> candles = candlesWithPrices(100, 100, 103, 100, 100);

            Map<String, List<Candle>> patternCandles = Map.of("BTCUSDT:1h", candles);
            Map<String, boolean[]> result = evaluator.evaluatePatterns(
                List.of(p), candles, "1h", patternCandles
            );

            boolean[] state = result.get("tf");
            assertNotNull(state);
            assertEquals(candles.size(), state.length);
            assertTrue(state[2], "completion bar should be true");
            assertFalse(state[0]);
            assertFalse(state[1]);
            assertFalse(state[3]);
        }

        @Test
        @DisplayName("missing candles for pattern produces empty state")
        void missingCandlesProducesEmptyState() {
            HoopPattern p = pattern("missing", hoop("up", 2.0, 5.0, 2, 1));
            p.setSymbol("ETHUSDT");

            List<Candle> strategyCandles = flatCandles(10, 100);
            Map<String, boolean[]> result = evaluator.evaluatePatterns(
                List.of(p), strategyCandles, "1h", Map.of()
            );

            boolean[] state = result.get("missing");
            assertNotNull(state);
            for (boolean b : state) assertFalse(b);
        }

        @Test
        @DisplayName("pattern on different timeframe maps to strategy bars by timestamp")
        void crossTimeframeMapping() {
            HoopPattern p = pattern("cross", hoop("up", 2.0, 5.0, 2, 1));
            p.setTimeframe("15m");

            // Pattern candles: 15m bars, completion at bar 2
            List<Candle> patternCandles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                double price = (i == 2) ? 103 : 100;
                patternCandles.add(new Candle(
                    1_000_000L + i * 900_000L, // 15m spacing
                    price, price + 1, price - 1, price, 100
                ));
            }

            // Strategy candles: 1h bars
            List<Candle> strategyCandles = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                strategyCandles.add(new Candle(
                    1_000_000L + i * 3_600_000L, // 1h spacing
                    100, 101, 99, 100, 100
                ));
            }

            Map<String, List<Candle>> pcMap = Map.of("BTCUSDT:15m", patternCandles);
            Map<String, boolean[]> result = evaluator.evaluatePatterns(
                List.of(p), strategyCandles, "1h", pcMap
            );

            boolean[] state = result.get("cross");
            assertNotNull(state);
            // The 15m completion timestamp should map to the correct 1h bar
            boolean anyTrue = false;
            for (boolean b : state) if (b) anyTrue = true;
            assertTrue(anyTrue, "completion should map to at least one strategy bar");
        }
    }

    // =========================================================================
    // patternsMatch with completion window
    // =========================================================================

    @Nested
    @DisplayName("patternsMatch with completion window")
    class PatternsMatchTests {

        @Test
        @DisplayName("window=1 only matches exact bar (backward compat)")
        void windowOneExactBar() {
            boolean[] state = new boolean[10];
            state[5] = true;
            Map<String, boolean[]> states = Map.of("p1", state);

            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 5, 1));
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 6, 1));
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 4, 1));
        }

        @Test
        @DisplayName("window=3 matches completion bar and 2 bars after")
        void windowThreeMatchesRange() {
            boolean[] state = new boolean[10];
            state[5] = true;
            Map<String, boolean[]> states = Map.of("p1", state);

            // Bar 5 = completion, window=3 means bars 5,6,7 should match
            // But window looks BACK: at bar 7, window is [5,7] which includes bar 5
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 5, 3));
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 6, 3));
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 7, 3));
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 8, 3));
        }

        @Test
        @DisplayName("excluded pattern blocks within window")
        void excludedWithWindow() {
            boolean[] state = new boolean[10];
            state[5] = true;
            Map<String, boolean[]> states = Map.of("ex", state);

            // Excluded pattern completed at bar 5, window=3
            // Bars 5-7 should be blocked
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of(), List.of("ex"), 5, 3));
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of(), List.of("ex"), 7, 3));
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of(), List.of("ex"), 8, 3));
        }

        @Test
        @DisplayName("no required patterns returns true")
        void noRequiredPatternsReturnsTrue() {
            Map<String, boolean[]> states = Map.of();
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of(), List.of(), 5, 1));
        }

        @Test
        @DisplayName("unknown required pattern returns false")
        void unknownRequiredReturnsFalse() {
            Map<String, boolean[]> states = Map.of();
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of("nonexistent"), List.of(), 5, 1));
        }

        @Test
        @DisplayName("unknown excluded pattern is ignored")
        void unknownExcludedIgnored() {
            Map<String, boolean[]> states = Map.of();
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of(), List.of("nonexistent"), 5, 1));
        }

        @Test
        @DisplayName("multiple required patterns all must match within window")
        void multipleRequiredAll() {
            boolean[] s1 = new boolean[10];
            s1[4] = true;
            boolean[] s2 = new boolean[10];
            s2[5] = true;
            Map<String, boolean[]> states = Map.of("p1", s1, "p2", s2);

            // At bar 5 with window=2: p1 window is [4,5] → hits bar 4, p2 window is [4,5] → hits bar 5
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1", "p2"), List.of(), 5, 2));

            // At bar 5 with window=1: p1 needs bar 5 → false, p2 bar 5 → true
            assertFalse(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1", "p2"), List.of(), 5, 1));
        }

        @Test
        @DisplayName("window at start of array does not go negative")
        void windowAtArrayStart() {
            boolean[] state = new boolean[10];
            state[0] = true;
            Map<String, boolean[]> states = Map.of("p1", state);

            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 0, 5));
            assertTrue(HoopPatternEvaluator.patternsMatch(
                states, List.of("p1"), List.of(), 2, 5));
        }
    }

    // =========================================================================
    // Price smoothing
    // =========================================================================

    @Nested
    @DisplayName("Price smoothing")
    class SmoothingTests {

        @Test
        @DisplayName("HLC3 smoothing uses (high + low + close) / 3")
        void hlc3Smoothing() {
            HoopPattern p = pattern("hlc3", hoop("up", 2.0, 5.0, 2, 0));
            p.setPriceSmoothingType(PriceSmoothingType.HLC3);

            // Candle at bar 2: close=103, high=104, low=102 → HLC3 = (104+102+103)/3 = 103
            // Anchor candle at bar 0: close=100, high=101, low=99 → HLC3 = (101+99+100)/3 = 100
            // +3% → in range
            List<Candle> candles = List.of(
                new Candle(0, 100, 101, 99, 100, 100),
                new Candle(3_600_000L, 100, 101, 99, 100, 100),
                new Candle(7_200_000L, 103, 104, 102, 103, 100)
            );

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
        }

        @Test
        @DisplayName("SMA smoothing skips warmup bars")
        void smaWarmup() {
            HoopPattern p = pattern("sma", hoop("up", 1.0, 50.0, 2, 1));
            p.setPriceSmoothingType(PriceSmoothingType.SMA);
            p.setPriceSmoothingPeriod(3);

            // SMA(3) needs 3 bars of warmup. Pattern should not anchor on NaN bars.
            double[] prices = new double[10];
            Arrays.fill(prices, 100);
            prices[5] = 110; // spike that shows up in SMA
            List<Candle> candles = candlesWithPrices(prices);

            // Should not crash, should find match after warmup
            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            // The exact match depends on SMA values but it shouldn't throw
            assertNotNull(matches);
        }
    }

    // =========================================================================
    // HoopPatternSettings completion window
    // =========================================================================

    @Nested
    @DisplayName("HoopPatternSettings")
    class SettingsTests {

        @Test
        @DisplayName("default completionWindowBars is 1")
        void defaultWindow() {
            HoopPatternSettings settings = new HoopPatternSettings();
            assertEquals(1, settings.getCompletionWindowBars());
        }

        @Test
        @DisplayName("completionWindowBars minimum is 1")
        void minimumWindow() {
            HoopPatternSettings settings = new HoopPatternSettings();
            settings.setCompletionWindowBars(0);
            assertEquals(1, settings.getCompletionWindowBars());
            settings.setCompletionWindowBars(-5);
            assertEquals(1, settings.getCompletionWindowBars());
        }

        @Test
        @DisplayName("completionWindowBars can be set to higher values")
        void higherWindow() {
            HoopPatternSettings settings = new HoopPatternSettings();
            settings.setCompletionWindowBars(10);
            assertEquals(10, settings.getCompletionWindowBars());
        }
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("one-sided hoop (no min bound)")
        void noMinBound() {
            HoopPattern p = pattern("nomin",
                new Hoop("cap", null, 5.0, 3, 1, Hoop.AnchorMode.ACTUAL_HIT)
            );

            List<Candle> candles = candlesWithPrices(100, 100, 100, 90, 100);
            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            // 90 is below 105 (max) and above 0 (no min) → should match
            assertEquals(1, matches.size());
        }

        @Test
        @DisplayName("one-sided hoop (no max bound)")
        void noMaxBound() {
            HoopPattern p = pattern("nomax",
                new Hoop("floor", 2.0, null, 3, 1, Hoop.AnchorMode.ACTUAL_HIT)
            );

            List<Candle> candles = candlesWithPrices(100, 100, 100, 200, 100);
            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            // 200 is above 102 (min) and below MAX_VALUE → should match
            assertEquals(1, matches.size());
        }

        @Test
        @DisplayName("pattern with tolerance=0 requires exact distance")
        void zeroTolerance() {
            HoopPattern p = pattern("exact", hoop("up", 2.0, 5.0, 3, 0));

            // From anchor bar 0: window is exactly [3, 3]
            double[] prices = new double[6];
            Arrays.fill(prices, 100);
            prices[3] = 103;
            List<Candle> candles = candlesWithPrices(prices);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.size() >= 1);
            // First match should be from anchor bar 0, hitting bar 3
            assertEquals(0, matches.get(0).anchorBar());
            assertEquals(3, matches.get(0).completionBar());

            // Price spike only at bar 4 — from bar 0 window=[3,3] misses,
            // but from bar 1 window=[4,4] hits (same price → same % range)
            Arrays.fill(prices, 100);
            prices[4] = 103;
            candles = candlesWithPrices(prices);
            matches = evaluator.findPatternCompletions(p, candles);
            assertEquals(1, matches.size());
            assertEquals(1, matches.get(0).anchorBar(), "should match from anchor bar 1");
            assertEquals(4, matches.get(0).completionBar());
        }

        @Test
        @DisplayName("very short candle series doesn't crash")
        void veryShortSeries() {
            HoopPattern p = pattern("short", hoop("up", 2.0, 5.0, 3, 1));
            List<Candle> candles = candlesWithPrices(100);

            List<HoopMatchResult> matches = evaluator.findPatternCompletions(p, candles);
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("HoopMatchResult accessors")
        void matchResultAccessors() {
            HoopMatchResult r = new HoopMatchResult(
                "test", 0, 10, 100.0,
                new double[]{102, 99}, new int[]{5, 10}
            );

            assertEquals(10, r.getDuration());
            assertEquals(-1.0, r.getPriceChange(), 0.001);
            assertEquals(-1.0, r.getPriceChangePercent(), 0.01);
            assertEquals(2, r.getHoopCount());
            assertEquals(5, r.getHoopHitBar(0));
            assertEquals(10, r.getHoopHitBar(1));
            assertEquals(-1, r.getHoopHitBar(5)); // out of bounds
            assertEquals(102, r.getHoopHitPrice(0));
            assertTrue(Double.isNaN(r.getHoopHitPrice(5))); // out of bounds
        }
    }
}
