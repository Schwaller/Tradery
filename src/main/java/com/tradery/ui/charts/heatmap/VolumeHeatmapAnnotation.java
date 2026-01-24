package com.tradery.ui.charts.heatmap;

import com.tradery.model.AggTrade;
import com.tradery.model.Candle;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Annotation that draws volume heatmap behind candles on the price chart.
 * Shows volume distribution at different price levels for each candle.
 *
 * Features:
 * - Multiple display modes (buy, sell, total, delta, split)
 * - Configurable color ramps and opacity
 * - Tick size-based bucketing for consistent visualization
 */
public class VolumeHeatmapAnnotation extends AbstractXYAnnotation {

    private final List<CandleHeatmap> heatmaps;
    private final VolumeHeatmapConfig config;

    /**
     * Heatmap data for a single candle.
     */
    public record CandleHeatmap(
        long timestamp,         // Candle timestamp
        double[] priceLevels,   // Price level centers
        double[] buyVolumes,    // Buy volume at each level
        double[] sellVolumes,   // Sell volume at each level
        double tickSize,        // Size of each price bucket
        double maxVolume        // Max volume for normalization
    ) {
        public double[] getTotalVolumes() {
            double[] totals = new double[buyVolumes.length];
            for (int i = 0; i < totals.length; i++) {
                totals[i] = buyVolumes[i] + sellVolumes[i];
            }
            return totals;
        }

        public double[] getDeltas() {
            double[] deltas = new double[buyVolumes.length];
            for (int i = 0; i < deltas.length; i++) {
                deltas[i] = buyVolumes[i] - sellVolumes[i];
            }
            return deltas;
        }
    }

    public VolumeHeatmapAnnotation(List<CandleHeatmap> heatmaps, VolumeHeatmapConfig config) {
        this.heatmaps = heatmaps;
        this.config = config;
    }

    /**
     * Calculate heatmaps from candles and aggTrades.
     *
     * @param candles   List of candles
     * @param aggTrades List of aggregated trades (may be null)
     * @param config    Heatmap configuration
     * @return List of heatmap data for each candle
     */
    public static List<CandleHeatmap> calculate(List<Candle> candles, List<AggTrade> aggTrades,
                                                 VolumeHeatmapConfig config) {
        List<CandleHeatmap> heatmaps = new ArrayList<>();

        if (candles == null || candles.isEmpty()) {
            return heatmaps;
        }

        // Calculate tick size
        double tickSize = calculateTickSize(candles, config);

        // If no aggTrades, create simple volume distribution from candle data
        if (aggTrades == null || aggTrades.isEmpty()) {
            return calculateFromCandles(candles, tickSize, config);
        }

        // Calculate from aggTrades for accurate buy/sell distribution
        return calculateFromAggTrades(candles, aggTrades, tickSize, config);
    }

    private static double calculateTickSize(List<Candle> candles, VolumeHeatmapConfig config) {
        if (config.getTickSizeMode() == VolumeHeatmapConfig.TickSizeMode.FIXED) {
            return config.getFixedTickSize();
        }

        // Auto mode: calculate based on average candle range
        double sumRange = 0;
        int count = 0;
        for (Candle c : candles) {
            double range = c.high() - c.low();
            if (range > 0) {
                sumRange += range;
                count++;
            }
        }

        if (count == 0) return 50.0; // Default

        double avgRange = sumRange / count;
        int bucketCount = config.getBucketCount();

        // Calculate ideal tick size
        double idealTick = avgRange / bucketCount;

        // Round to nice tick size
        return roundToNiceTick(idealTick);
    }

    private static double roundToNiceTick(double idealTick) {
        double[] niceTicks = {0.01, 0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000};

        for (double tick : niceTicks) {
            if (tick >= idealTick) {
                return tick;
            }
        }
        return niceTicks[niceTicks.length - 1];
    }

    private static List<CandleHeatmap> calculateFromCandles(List<Candle> candles, double tickSize,
                                                             VolumeHeatmapConfig config) {
        List<CandleHeatmap> heatmaps = new ArrayList<>();

        for (Candle c : candles) {
            double low = Math.floor(c.low() / tickSize) * tickSize;
            double high = Math.ceil(c.high() / tickSize) * tickSize;

            int numBuckets = Math.max(1, (int) ((high - low) / tickSize) + 1);
            double[] priceLevels = new double[numBuckets];
            double[] buyVolumes = new double[numBuckets];
            double[] sellVolumes = new double[numBuckets];

            for (int i = 0; i < numBuckets; i++) {
                priceLevels[i] = low + (i + 0.5) * tickSize;
            }

            // Distribute volume evenly across the candle range
            // Use candle direction to assign buy/sell
            boolean isBullish = c.close() >= c.open();
            double volumePerBucket = c.volume() / numBuckets;

            for (int i = 0; i < numBuckets; i++) {
                if (isBullish) {
                    buyVolumes[i] = volumePerBucket * 0.6;
                    sellVolumes[i] = volumePerBucket * 0.4;
                } else {
                    buyVolumes[i] = volumePerBucket * 0.4;
                    sellVolumes[i] = volumePerBucket * 0.6;
                }
            }

            double maxVol = volumePerBucket;
            heatmaps.add(new CandleHeatmap(c.timestamp(), priceLevels, buyVolumes, sellVolumes, tickSize, maxVol));
        }

        return heatmaps;
    }

    private static List<CandleHeatmap> calculateFromAggTrades(List<Candle> candles, List<AggTrade> aggTrades,
                                                               double tickSize, VolumeHeatmapConfig config) {
        List<CandleHeatmap> heatmaps = new ArrayList<>();

        // Sort aggTrades by timestamp for efficient processing
        int tradeIndex = 0;

        for (int candleIdx = 0; candleIdx < candles.size(); candleIdx++) {
            Candle c = candles.get(candleIdx);
            long candleEnd = candleIdx < candles.size() - 1
                ? candles.get(candleIdx + 1).timestamp()
                : c.timestamp() + estimateInterval(candles);

            // Calculate bucket bounds
            double low = Math.floor(c.low() / tickSize) * tickSize;
            double high = Math.ceil(c.high() / tickSize) * tickSize;

            int numBuckets = Math.max(1, (int) ((high - low) / tickSize) + 1);
            double[] priceLevels = new double[numBuckets];
            double[] buyVolumes = new double[numBuckets];
            double[] sellVolumes = new double[numBuckets];

            for (int i = 0; i < numBuckets; i++) {
                priceLevels[i] = low + (i + 0.5) * tickSize;
            }

            // Aggregate trades into buckets
            double maxVol = 0;
            while (tradeIndex < aggTrades.size()) {
                AggTrade trade = aggTrades.get(tradeIndex);

                if (trade.timestamp() < c.timestamp()) {
                    tradeIndex++;
                    continue;
                }

                if (trade.timestamp() >= candleEnd) {
                    break;
                }

                // Find bucket for this trade
                int bucketIdx = (int) ((trade.price() - low) / tickSize);
                bucketIdx = Math.max(0, Math.min(numBuckets - 1, bucketIdx));

                // Assign to buy or sell based on isBuyerMaker
                // Note: isBuyerMaker = true means SELL (maker was buyer, so taker was seller)
                if (trade.isBuyerMaker()) {
                    sellVolumes[bucketIdx] += trade.quantity();
                } else {
                    buyVolumes[bucketIdx] += trade.quantity();
                }

                maxVol = Math.max(maxVol, buyVolumes[bucketIdx] + sellVolumes[bucketIdx]);
                tradeIndex++;
            }

            // Reset trade index for next candle if there's overlap
            // (trades might span multiple candles)
            if (tradeIndex > 0 && candleIdx < candles.size() - 1) {
                // Backtrack to find trades that belong to next candle
                while (tradeIndex > 0 && aggTrades.get(tradeIndex - 1).timestamp() >= candleEnd) {
                    tradeIndex--;
                }
            }

            if (maxVol == 0) maxVol = 1; // Avoid division by zero

            heatmaps.add(new CandleHeatmap(c.timestamp(), priceLevels, buyVolumes, sellVolumes, tickSize, maxVol));
        }

        return heatmaps;
    }

    private static long estimateInterval(List<Candle> candles) {
        if (candles.size() < 2) return 3600000; // Default 1h

        long totalDiff = 0;
        int count = 0;
        for (int i = 1; i < Math.min(10, candles.size()); i++) {
            totalDiff += candles.get(i).timestamp() - candles.get(i - 1).timestamp();
            count++;
        }
        return count > 0 ? totalDiff / count : 3600000;
    }

    @Override
    public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                     ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                     PlotRenderingInfo info) {

        if (!config.isEnabled() || heatmaps.isEmpty()) {
            return;
        }

        // Enable anti-aliasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Calculate candle width for positioning
        long candleInterval = estimateCandleInterval();
        double halfIntervalMs = candleInterval * 0.4; // Slightly less than half to leave gaps

        for (CandleHeatmap heatmap : heatmaps) {
            drawCandleHeatmap(g2, plot, dataArea, domainAxis, rangeAxis, heatmap, halfIntervalMs);
        }
    }

    private long estimateCandleInterval() {
        if (heatmaps.size() < 2) return 3600000;

        long minInterval = Long.MAX_VALUE;
        for (int i = 1; i < Math.min(10, heatmaps.size()); i++) {
            long diff = heatmaps.get(i).timestamp() - heatmaps.get(i - 1).timestamp();
            if (diff > 0) {
                minInterval = Math.min(minInterval, diff);
            }
        }
        return minInterval == Long.MAX_VALUE ? 3600000 : minInterval;
    }

    private void drawCandleHeatmap(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                                    ValueAxis domainAxis, ValueAxis rangeAxis,
                                    CandleHeatmap heatmap, double halfIntervalMs) {

        double centerX = domainAxis.valueToJava2D(heatmap.timestamp(), dataArea, RectangleEdge.BOTTOM);
        double leftX = domainAxis.valueToJava2D(heatmap.timestamp() - halfIntervalMs, dataArea, RectangleEdge.BOTTOM);
        double rightX = domainAxis.valueToJava2D(heatmap.timestamp() + halfIntervalMs, dataArea, RectangleEdge.BOTTOM);

        // Skip if outside visible area
        if (rightX < dataArea.getMinX() || leftX > dataArea.getMaxX()) {
            return;
        }

        double candleWidth = rightX - leftX;
        if (candleWidth < 2) return; // Too narrow to draw

        double[] priceLevels = heatmap.priceLevels();
        double tickSize = heatmap.tickSize();
        int alpha = config.getAlpha();

        switch (config.getMode()) {
            case BUY_VOLUME -> drawSingleMode(g2, dataArea, rangeAxis, heatmap,
                leftX, candleWidth, heatmap.buyVolumes(), config.getBuyRamp(), alpha);

            case SELL_VOLUME -> drawSingleMode(g2, dataArea, rangeAxis, heatmap,
                leftX, candleWidth, heatmap.sellVolumes(), config.getSellRamp(), alpha);

            case TOTAL_VOLUME -> drawSingleMode(g2, dataArea, rangeAxis, heatmap,
                leftX, candleWidth, heatmap.getTotalVolumes(), config.getTotalRamp(), alpha);

            case DELTA -> drawDeltaMode(g2, dataArea, rangeAxis, heatmap,
                leftX, candleWidth, alpha);

            case SPLIT -> drawSplitMode(g2, dataArea, rangeAxis, heatmap,
                leftX, candleWidth, alpha);
        }
    }

    private void drawSingleMode(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                 CandleHeatmap heatmap, double x, double width,
                                 double[] volumes, ColorRamp ramp, int alpha) {

        double[] priceLevels = heatmap.priceLevels();
        double tickSize = heatmap.tickSize();
        double maxVol = heatmap.maxVolume();

        for (int i = 0; i < priceLevels.length; i++) {
            double volume = volumes[i];
            if (volume <= 0) continue;

            double priceLevel = priceLevels[i];
            double topY = rangeAxis.valueToJava2D(priceLevel + tickSize / 2, dataArea, RectangleEdge.LEFT);
            double bottomY = rangeAxis.valueToJava2D(priceLevel - tickSize / 2, dataArea, RectangleEdge.LEFT);

            double normalizedVol = volume / maxVol;
            Color color = ramp.getColor(normalizedVol, alpha);

            int rectHeight = Math.max(1, Math.abs((int) (bottomY - topY)));
            int rectY = (int) Math.min(topY, bottomY);

            g2.setColor(color);
            g2.fillRect((int) x, rectY, (int) width, rectHeight);
        }
    }

    private void drawDeltaMode(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                CandleHeatmap heatmap, double x, double width, int alpha) {

        double[] priceLevels = heatmap.priceLevels();
        double[] buyVols = heatmap.buyVolumes();
        double[] sellVols = heatmap.sellVolumes();
        double tickSize = heatmap.tickSize();
        double maxVol = heatmap.maxVolume();

        ColorRamp buyRamp = config.getBuyRamp();
        ColorRamp sellRamp = config.getSellRamp();

        for (int i = 0; i < priceLevels.length; i++) {
            double buyVol = buyVols[i];
            double sellVol = sellVols[i];
            double totalVol = buyVol + sellVol;

            if (totalVol <= 0) continue;

            double priceLevel = priceLevels[i];
            double topY = rangeAxis.valueToJava2D(priceLevel + tickSize / 2, dataArea, RectangleEdge.LEFT);
            double bottomY = rangeAxis.valueToJava2D(priceLevel - tickSize / 2, dataArea, RectangleEdge.LEFT);

            // Determine color based on delta direction
            double delta = buyVol - sellVol;
            double normalizedVol = totalVol / maxVol;

            Color color;
            if (delta >= 0) {
                color = buyRamp.getColor(normalizedVol, alpha);
            } else {
                color = sellRamp.getColor(normalizedVol, alpha);
            }

            int rectHeight = Math.max(1, Math.abs((int) (bottomY - topY)));
            int rectY = (int) Math.min(topY, bottomY);

            g2.setColor(color);
            g2.fillRect((int) x, rectY, (int) width, rectHeight);
        }
    }

    private void drawSplitMode(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                CandleHeatmap heatmap, double x, double width, int alpha) {

        double[] priceLevels = heatmap.priceLevels();
        double[] buyVols = heatmap.buyVolumes();
        double[] sellVols = heatmap.sellVolumes();
        double tickSize = heatmap.tickSize();
        double maxVol = heatmap.maxVolume();

        ColorRamp buyRamp = config.getBuyRamp();
        ColorRamp sellRamp = config.getSellRamp();

        double halfWidth = width / 2;

        for (int i = 0; i < priceLevels.length; i++) {
            double buyVol = buyVols[i];
            double sellVol = sellVols[i];

            if (buyVol <= 0 && sellVol <= 0) continue;

            double priceLevel = priceLevels[i];
            double topY = rangeAxis.valueToJava2D(priceLevel + tickSize / 2, dataArea, RectangleEdge.LEFT);
            double bottomY = rangeAxis.valueToJava2D(priceLevel - tickSize / 2, dataArea, RectangleEdge.LEFT);

            int rectHeight = Math.max(1, Math.abs((int) (bottomY - topY)));
            int rectY = (int) Math.min(topY, bottomY);

            // Draw buy on left half
            if (buyVol > 0) {
                double normalizedBuy = buyVol / maxVol;
                Color buyColor = buyRamp.getColor(normalizedBuy, alpha);
                g2.setColor(buyColor);
                g2.fillRect((int) x, rectY, (int) halfWidth, rectHeight);
            }

            // Draw sell on right half
            if (sellVol > 0) {
                double normalizedSell = sellVol / maxVol;
                Color sellColor = sellRamp.getColor(normalizedSell, alpha);
                g2.setColor(sellColor);
                g2.fillRect((int) (x + halfWidth), rectY, (int) halfWidth, rectHeight);
            }
        }
    }
}
