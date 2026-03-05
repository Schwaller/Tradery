package com.tradery.forge.ui.charts.footprint;

import com.tradery.core.model.Exchange;
import com.tradery.core.model.Footprint;
import com.tradery.core.model.FootprintBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FootprintHeatmapAnnotationTest {

    /**
     * Helper to build a FootprintBucket with buy/sell volumes.
     */
    private static FootprintBucket bucket(double price, double buy, double sell) {
        var builder = new FootprintBucket.Builder(price);
        builder.addBuyVolume(Exchange.BINANCE, buy);
        builder.addSellVolume(Exchange.BINANCE, sell);
        return builder.build();
    }

    /**
     * Helper to build a Footprint with given buckets.
     */
    private static Footprint footprint(long ts, double high, double low, double tickSize,
                                        FootprintBucket... buckets) {
        var builder = new Footprint.Builder()
            .timestamp(ts).barIndex(0).high(high).low(low).tickSize(tickSize);
        for (var b : buckets) builder.addBucket(b);
        return builder.build();
    }

    @Nested
    @DisplayName("Split mode per-side normalization")
    class SplitModeNormalizationTest {

        @Test
        @DisplayName("per-side max volumes are computed independently from total")
        void perSideMaxVolumesAreIndependent() throws Exception {
            // Bucket A: buy=100, sell=10 (total=110) → buy-heavy
            // Bucket B: buy=10, sell=80 (total=90)  → sell-heavy
            Footprint fp = footprint(1000L, 110, 90, 10.0,
                bucket(100.0, 100.0, 10.0),
                bucket(110.0, 10.0, 80.0)
            );

            FootprintHeatmapConfig config = new FootprintHeatmapConfig();
            config.setDisplayMode(FootprintDisplayMode.SPLIT);
            config.setGlobalVolumeNorm(false); // per-candle normalization

            FootprintHeatmapAnnotation annotation = new FootprintHeatmapAnnotation(List.of(fp), config);

            // Access per-side max buy/sell via reflection
            double maxBuy = getPrivateDouble(annotation, "getMaxBuyVolume", 0);
            double maxSell = getPrivateDouble(annotation, "getMaxSellVolume", 0);

            // Max buy should be 100 (from bucket A), not 110 (total)
            assertEquals(100.0, maxBuy, 0.001, "Max buy volume should be per-side, not total");
            // Max sell should be 80 (from bucket B), not 110 (total)
            assertEquals(80.0, maxSell, 0.001, "Max sell volume should be per-side, not total");
        }

        @Test
        @DisplayName("global per-side normalization spans all footprints")
        void globalPerSideNormalizationSpansAllFootprints() throws Exception {
            Footprint fp1 = footprint(1000L, 110, 90, 10.0,
                bucket(100.0, 50.0, 30.0));
            Footprint fp2 = footprint(2000L, 110, 90, 10.0,
                bucket(100.0, 200.0, 10.0));

            FootprintHeatmapConfig config = new FootprintHeatmapConfig();
            config.setDisplayMode(FootprintDisplayMode.SPLIT);
            config.setGlobalVolumeNorm(true);

            FootprintHeatmapAnnotation annotation = new FootprintHeatmapAnnotation(List.of(fp1, fp2), config);

            double maxBuy = getPrivateDouble(annotation, "getMaxBuyVolume", 0);
            double maxSell = getPrivateDouble(annotation, "getMaxSellVolume", 0);

            // Global: max buy = 200 (from fp2), max sell = 30 (from fp1)
            assertEquals(200.0, maxBuy, 0.001);
            assertEquals(30.0, maxSell, 0.001);
        }

        @Test
        @DisplayName("total maxVolume uses combined buy+sell (for non-split modes)")
        void totalMaxVolumeUsesCombinedBuySell() throws Exception {
            // Bucket: buy=100, sell=80 → total=180
            Footprint fp = footprint(1000L, 110, 90, 10.0,
                bucket(100.0, 100.0, 80.0));

            FootprintHeatmapConfig config = new FootprintHeatmapConfig();
            config.setDisplayMode(FootprintDisplayMode.COMBINED);
            config.setGlobalVolumeNorm(false);

            FootprintHeatmapAnnotation annotation = new FootprintHeatmapAnnotation(List.of(fp), config);

            Method getMaxVol = FootprintHeatmapAnnotation.class.getDeclaredMethod("getMaxVolume", int.class);
            getMaxVol.setAccessible(true);
            double maxVol = (double) getMaxVol.invoke(annotation, 0);

            assertEquals(180.0, maxVol, 0.001, "Total max should be buy + sell");
        }
    }

    private double getPrivateDouble(FootprintHeatmapAnnotation annotation, String methodName, int index) throws Exception {
        Method method = FootprintHeatmapAnnotation.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (double) method.invoke(annotation, index);
    }
}
