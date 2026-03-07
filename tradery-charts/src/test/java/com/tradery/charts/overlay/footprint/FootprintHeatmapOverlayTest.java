package com.tradery.charts.overlay.footprint;

import com.tradery.charts.overlay.FootprintProfileProvider;
import com.tradery.core.model.*;
import com.tradery.ui.controls.indicators.FootprintDisplayMode;
import com.tradery.ui.controls.indicators.FootprintHeatmapConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FootprintHeatmapOverlayTest {

    // ===== buildFootprintFromProfile =====

    @Nested
    @DisplayName("buildFootprintFromProfile")
    class BuildFootprintFromProfileTest {

        private Footprint invokeBuilder(FootprintProfileProvider.RawProfile profile, double tickSize, Candle candle) {
            Footprint.Builder fpBuilder = new Footprint.Builder()
                .timestamp(candle.timestamp())
                .barIndex(0)
                .high(candle.high())
                .low(candle.low())
                .tickSize(tickSize);

            FootprintHeatmapOverlay.buildFootprintFromProfile(fpBuilder, profile, tickSize, candle);
            return fpBuilder.build();
        }

        @Test
        @DisplayName("null profile produces empty buckets covering candle range")
        void nullProfileProducesEmptyBuckets() {
            Candle candle = new Candle(1000L, 100.0, 105.0, 95.0, 102.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double tickSize = 5.0;

            Footprint fp = invokeBuilder(null, tickSize, candle);

            assertFalse(fp.buckets().isEmpty(), "Should have buckets");
            // Range from 95 to 105 with tickSize 5 -> buckets at 95, 100, 105
            assertEquals(3, fp.buckets().size());
            // All should be empty (zero volume)
            for (FootprintBucket b : fp.buckets()) {
                assertEquals(0.0, b.totalVolume(), 0.001);
            }
        }

        @Test
        @DisplayName("profile ticks that snap to different footprint levels stay separate")
        void separateProfileTicksStaySeparate() {
            // Profile with 2 ticks at different prices that map to different footprint buckets
            Map<String, double[]> levels = Map.of(
                "100", new double[]{50.0, 30.0},   // price 100*1.0 = 100 -> snaps to 100
                "110", new double[]{40.0, 20.0}    // price 110*1.0 = 110 -> snaps to 110
            );
            FootprintProfileProvider.RawProfile profile =
                new FootprintProfileProvider.RawProfile(1000L, 1.0, 90.0, 50.0, levels);

            Candle candle = new Candle(1000L, 100.0, 115.0, 95.0, 110.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double tickSize = 10.0;

            Footprint fp = invokeBuilder(profile, tickSize, candle);

            assertEquals(2, fp.buckets().size());
            // Bucket at 100: buy=50, sell=30
            FootprintBucket b100 = fp.buckets().get(0);
            assertEquals(100.0, b100.priceLevel(), 0.1);
            assertEquals(50.0, b100.totalBuyVolume(), 0.001);
            assertEquals(30.0, b100.totalSellVolume(), 0.001);
            // Bucket at 110: buy=40, sell=20
            FootprintBucket b110 = fp.buckets().get(1);
            assertEquals(110.0, b110.priceLevel(), 0.1);
            assertEquals(40.0, b110.totalBuyVolume(), 0.001);
            assertEquals(20.0, b110.totalSellVolume(), 0.001);
        }

        @Test
        @DisplayName("multiple profile ticks that snap to same level are accumulated")
        void profileTicksAccumulateAtSameLevel() {
            // Profile ticks at 99, 100, 101 with tickSize=1, footprint tickSize=10
            // All three snap to 100
            Map<String, double[]> levels = Map.of(
                "99", new double[]{10.0, 5.0},
                "100", new double[]{20.0, 15.0},
                "101", new double[]{30.0, 25.0}
            );
            FootprintProfileProvider.RawProfile profile =
                new FootprintProfileProvider.RawProfile(1000L, 1.0, 60.0, 45.0, levels);

            Candle candle = new Candle(1000L, 95.0, 105.0, 95.0, 100.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double tickSize = 10.0;

            Footprint fp = invokeBuilder(profile, tickSize, candle);

            // All 3 ticks should accumulate into a single bucket at 100
            assertEquals(1, fp.buckets().size());
            FootprintBucket bucket = fp.buckets().get(0);
            assertEquals(100.0, bucket.priceLevel(), 0.1);
            assertEquals(60.0, bucket.totalBuyVolume(), 0.001);  // 10 + 20 + 30
            assertEquals(45.0, bucket.totalSellVolume(), 0.001); // 5 + 15 + 25
        }

        @Test
        @DisplayName("accumulation across many fine-grained ticks preserves total volume")
        void accumulationPreservesTotalVolume() {
            // Simulate 50 fine-grained profile ticks (at $0.10 resolution)
            // across a $10 candle with $5 footprint tick size -> 2 buckets
            Map<String, double[]> levels = new java.util.HashMap<>();
            double expectedTotalBuy = 0;
            double expectedTotalSell = 0;
            for (int i = 950; i < 1000; i++) {
                double buy = i * 0.1;
                double sell = i * 0.05;
                levels.put(String.valueOf(i), new double[]{buy, sell});
                expectedTotalBuy += buy;
                expectedTotalSell += sell;
            }
            FootprintProfileProvider.RawProfile profile =
                new FootprintProfileProvider.RawProfile(1000L, 0.1, expectedTotalBuy, expectedTotalSell, levels);

            Candle candle = new Candle(1000L, 95.0, 100.0, 94.0, 98.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double tickSize = 5.0;

            Footprint fp = invokeBuilder(profile, tickSize, candle);

            // Verify total volume is preserved across all buckets
            double actualBuy = fp.buckets().stream().mapToDouble(FootprintBucket::totalBuyVolume).sum();
            double actualSell = fp.buckets().stream().mapToDouble(FootprintBucket::totalSellVolume).sum();
            assertEquals(expectedTotalBuy, actualBuy, 0.01, "Total buy volume must be preserved");
            assertEquals(expectedTotalSell, actualSell, 0.01, "Total sell volume must be preserved");
        }

        @Test
        @DisplayName("empty levels map produces empty buckets like null profile")
        void emptyLevelsProducesEmptyBuckets() {
            FootprintProfileProvider.RawProfile profile =
                new FootprintProfileProvider.RawProfile(1000L, 1.0, 0, 0, Map.of());

            Candle candle = new Candle(1000L, 100.0, 105.0, 95.0, 102.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double tickSize = 5.0;

            Footprint fp = invokeBuilder(profile, tickSize, candle);

            assertFalse(fp.buckets().isEmpty());
            for (FootprintBucket b : fp.buckets()) {
                assertEquals(0.0, b.totalVolume(), 0.001);
            }
        }
    }

    // ===== calculateATR =====

    @Nested
    @DisplayName("calculateATR")
    class CalculateATRTest {

        @Test
        void singleCandleReturnsHighMinusLow() {
            Candle c = new Candle(1000L, 100.0, 110.0, 90.0, 105.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double atr = FootprintHeatmapOverlay.calculateATR(List.of(c), 14);
            assertEquals(20.0, atr, 0.001);
        }

        @Test
        void twoCandlesComputesTrueRange() {
            Candle c1 = new Candle(1000L, 100.0, 110.0, 90.0, 105.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            Candle c2 = new Candle(2000L, 106.0, 115.0, 95.0, 112.0, 1000.0,
                100, 500.0, 300.0, 150.0);
            double atr = FootprintHeatmapOverlay.calculateATR(List.of(c1, c2), 14);
            // TR = max(115-95, |115-105|, |95-105|) = max(20, 10, 10) = 20
            assertEquals(20.0, atr, 0.001);
        }
    }
}
