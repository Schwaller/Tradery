package com.tradery.ui.charts.footprint;

import com.tradery.model.Exchange;
import com.tradery.model.Footprint;
import com.tradery.model.FootprintBucket;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * JFreeChart annotation that renders footprint heatmap for each candle.
 * Shows colored buckets based on delta direction and intensity,
 * with optional imbalance markers and delta numbers.
 */
public class FootprintHeatmapAnnotation extends AbstractXYAnnotation {

    private final List<Footprint> footprints;
    private final FootprintHeatmapConfig config;
    private final double maxVolume;  // For normalization

    public FootprintHeatmapAnnotation(List<Footprint> footprints, FootprintHeatmapConfig config) {
        this.footprints = footprints;
        this.config = config;

        // Calculate max volume for normalization
        this.maxVolume = footprints.stream()
            .flatMap(f -> f.buckets().stream())
            .mapToDouble(FootprintBucket::totalVolume)
            .max()
            .orElse(1.0);
    }

    @Override
    public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                     ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                     PlotRenderingInfo info) {

        if (!config.isEnabled() || footprints.isEmpty()) {
            return;
        }

        // Enable anti-aliasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Calculate candle width
        long candleInterval = estimateCandleInterval();
        double halfIntervalMs = candleInterval * 0.4;

        // Check if we should show zoom hint (candles too narrow)
        boolean tooNarrow = false;
        if (footprints.size() >= 2) {
            long interval = footprints.get(1).timestamp() - footprints.get(0).timestamp();
            double testWidth = domainAxis.valueToJava2D(interval, dataArea, RectangleEdge.BOTTOM)
                             - domainAxis.valueToJava2D(0, dataArea, RectangleEdge.BOTTOM);
            tooNarrow = testWidth < 3; // Less than 3 pixels per candle
        }

        if (tooNarrow) {
            // Draw zoom hint at bottom center
            drawZoomHint(g2, dataArea);
        } else {
            // Draw footprints
            for (Footprint footprint : footprints) {
                drawFootprint(g2, plot, dataArea, domainAxis, rangeAxis, footprint, halfIntervalMs);
            }
        }

    }

    private void drawZoomHint(Graphics2D g2, Rectangle2D dataArea) {
        String hint = "Zoom in to see volume heatmap";
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(hint);
        int textHeight = fm.getHeight();

        int x = (int) (dataArea.getCenterX() - textWidth / 2);
        int y = (int) (dataArea.getMaxY() - 10);

        // Draw background
        g2.setColor(new Color(40, 40, 40, 180));
        g2.fillRoundRect(x - 8, y - textHeight + 2, textWidth + 16, textHeight + 4, 6, 6);

        // Draw text
        g2.setColor(new Color(180, 180, 180));
        g2.drawString(hint, x, y);
    }

    private long estimateCandleInterval() {
        if (footprints.size() < 2) return 3600000;

        long minInterval = Long.MAX_VALUE;
        for (int i = 1; i < Math.min(10, footprints.size()); i++) {
            long diff = footprints.get(i).timestamp() - footprints.get(i - 1).timestamp();
            if (diff > 0) {
                minInterval = Math.min(minInterval, diff);
            }
        }
        return minInterval == Long.MAX_VALUE ? 3600000 : minInterval;
    }

    private void drawFootprint(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                                ValueAxis domainAxis, ValueAxis rangeAxis,
                                Footprint footprint, double halfIntervalMs) {

        double leftX = domainAxis.valueToJava2D(footprint.timestamp() - halfIntervalMs, dataArea, RectangleEdge.BOTTOM);
        double rightX = domainAxis.valueToJava2D(footprint.timestamp() + halfIntervalMs, dataArea, RectangleEdge.BOTTOM);

        // Skip if outside visible area
        if (rightX < dataArea.getMinX() || leftX > dataArea.getMaxX()) {
            return;
        }

        double candleWidth = rightX - leftX;
        if (candleWidth < 0.5) {
            return; // Only skip if truly invisible
        }

        // Draw value area background first
        if (config.isShowValueArea()) {
            drawValueArea(g2, dataArea, rangeAxis, leftX, rightX, footprint);
        }

        // Draw buckets
        for (FootprintBucket bucket : footprint.buckets()) {
            drawBucket(g2, dataArea, rangeAxis, leftX, candleWidth, footprint, bucket);
        }

        // Draw POC buy/sell bar
        if (config.isShowPocLine()) {
            drawPocBuySellBar(g2, dataArea, rangeAxis, leftX, rightX, footprint);
        }
    }

    private void drawValueArea(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                double leftX, double rightX, Footprint footprint) {
        double vahY = rangeAxis.valueToJava2D(footprint.vah(), dataArea, RectangleEdge.LEFT);
        double valY = rangeAxis.valueToJava2D(footprint.val(), dataArea, RectangleEdge.LEFT);

        int x = (int) leftX;
        int y = (int) Math.min(vahY, valY);
        int width = (int) (rightX - leftX);
        int height = (int) Math.abs(valY - vahY);

        g2.setColor(FootprintHeatmapConfig.VALUE_AREA_COLOR);
        g2.fillRect(x, y, width, height);
    }

    private void drawBucket(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                             double leftX, double candleWidth, Footprint footprint, FootprintBucket bucket) {

        double tickSize = footprint.tickSize();
        double priceLevel = bucket.priceLevel();

        double topY = rangeAxis.valueToJava2D(priceLevel + tickSize / 2, dataArea, RectangleEdge.LEFT);
        double bottomY = rangeAxis.valueToJava2D(priceLevel - tickSize / 2, dataArea, RectangleEdge.LEFT);

        int x = (int) leftX;
        int y = (int) Math.min(topY, bottomY);
        int width = (int) candleWidth;
        int height = Math.max(1, Math.abs((int) (bottomY - topY)));

        // Handle SPLIT mode separately
        if (config.getDisplayMode() == FootprintDisplayMode.SPLIT) {
            drawSplitBucket(g2, x, y, width, height, bucket);
        } else {
            // Get delta color based on mode
            Color bucketColor = getBucketColor(bucket, footprint);
            g2.setColor(bucketColor);
            g2.fillRect(x, y, width, height);
        }

        // Draw imbalance markers
        if (config.isShowImbalanceMarkers()) {
            drawImbalanceMarker(g2, x, y, width, height, bucket);
        }

        // Draw delta numbers if enabled and there's enough space
        if (config.isShowDeltaNumbers() && width >= 20 && height >= 10) {
            drawDeltaNumber(g2, x, y, width, height, bucket);
        }
    }

    /**
     * Draw split bucket - buy volume on left, sell volume on right.
     * Uses thermal color ramp based on volume intensity.
     */
    private void drawSplitBucket(Graphics2D g2, int x, int y, int width, int height, FootprintBucket bucket) {
        double buyVol = bucket.totalBuyVolume();
        double sellVol = bucket.totalSellVolume();
        double totalVol = buyVol + sellVol;

        if (totalVol <= 0) return;

        int halfWidth = width / 2;

        // Buy side (left) - thermal color ramp based on volume intensity
        if (buyVol > 0) {
            double buyIntensity = buyVol / maxVolume;
            Color buyColor = config.getVolumeRampColor(buyIntensity);
            g2.setColor(buyColor);
            g2.fillRect(x, y, halfWidth, height);
        }

        // Sell side (right) - thermal color ramp based on volume intensity
        if (sellVol > 0) {
            double sellIntensity = sellVol / maxVolume;
            Color sellColor = config.getVolumeRampColor(sellIntensity);
            g2.setColor(sellColor);
            g2.fillRect(x + halfWidth, y, width - halfWidth, height);
        }
    }

    private Color getBucketColor(FootprintBucket bucket, Footprint footprint) {
        switch (config.getDisplayMode()) {
            case DIVERGENCE -> {
                if (bucket.hasExchangeDivergence()) {
                    double volumeIntensity = bucket.totalVolume() / maxVolume;
                    int alpha = (int) (100 + volumeIntensity * 100 * config.getOpacity());
                    return new Color(
                        FootprintHeatmapConfig.DIVERGENCE_COLOR.getRed(),
                        FootprintHeatmapConfig.DIVERGENCE_COLOR.getGreen(),
                        FootprintHeatmapConfig.DIVERGENCE_COLOR.getBlue(),
                        Math.min(200, alpha)
                    );
                }
                // Fall through to delta coloring
            }

            case SINGLE_EXCHANGE -> {
                Exchange ex = config.getSelectedExchange();
                double exBuy = bucket.buyVolumeByExchange().getOrDefault(ex, 0.0);
                double exSell = bucket.sellVolumeByExchange().getOrDefault(ex, 0.0);
                double exTotal = exBuy + exSell;
                if (exTotal <= 0) {
                    return new Color(0, 0, 0, 0); // Transparent
                }
                double deltaPct = (exBuy - exSell) / exTotal;
                double volumeIntensity = exTotal / maxVolume;
                return config.getDeltaColor(deltaPct, volumeIntensity);
            }

            case STACKED -> {
                // For stacked mode, we still color by combined delta
                // The stacking is handled in a separate rendering pass
            }

            default -> {
                // COMBINED mode - use total delta
            }
        }

        // Default: combined delta coloring
        double totalVol = bucket.totalVolume();
        if (totalVol <= 0) {
            return new Color(0, 0, 0, 0);
        }

        double deltaPct = bucket.totalDelta() / totalVol;
        double volumeIntensity = totalVol / maxVolume;
        return config.getDeltaColor(deltaPct, volumeIntensity);
    }

    private void drawImbalanceMarker(Graphics2D g2, int x, int y, int width, int height,
                                      FootprintBucket bucket) {
        if (!bucket.hasBuyImbalance() && !bucket.hasSellImbalance()) {
            return;
        }

        // Draw small arrow indicator
        int arrowSize = Math.min(6, Math.min(width / 4, height / 2));
        if (arrowSize < 3) return;

        g2.setColor(Color.WHITE);

        int centerY = y + height / 2;

        if (bucket.hasBuyImbalance()) {
            // Up arrow on right side
            int arrowX = x + width - arrowSize - 2;
            int[] xPoints = {arrowX, arrowX + arrowSize, arrowX + arrowSize / 2};
            int[] yPoints = {centerY, centerY, centerY - arrowSize};
            g2.fillPolygon(xPoints, yPoints, 3);
        } else {
            // Down arrow on right side
            int arrowX = x + width - arrowSize - 2;
            int[] xPoints = {arrowX, arrowX + arrowSize, arrowX + arrowSize / 2};
            int[] yPoints = {centerY, centerY, centerY + arrowSize};
            g2.fillPolygon(xPoints, yPoints, 3);
        }
    }

    private void drawDeltaNumber(Graphics2D g2, int x, int y, int width, int height,
                                  FootprintBucket bucket) {
        double delta = bucket.totalDelta();
        if (Math.abs(delta) < 0.01) return;

        // Format delta value
        String text;
        if (Math.abs(delta) >= 1000000) {
            text = String.format("%.1fM", delta / 1000000);
        } else if (Math.abs(delta) >= 1000) {
            text = String.format("%.1fK", delta / 1000);
        } else {
            text = String.format("%.0f", delta);
        }

        // Draw centered in bucket
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, Math.min(9, height - 2));
        g2.setFont(font);
        g2.setColor(Color.WHITE);

        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + height / 2 + fm.getAscent() / 2 - 1;

        g2.drawString(text, textX, textY);
    }

    /**
     * Draw a buy/sell bar at the POC level showing the volume ratio at the highest volume price.
     */
    private void drawPocBuySellBar(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                    double leftX, double rightX, Footprint footprint) {
        double poc = footprint.poc();
        double tickSize = footprint.tickSize();

        // Find the POC bucket
        FootprintBucket pocBucket = null;
        for (FootprintBucket bucket : footprint.buckets()) {
            if (Math.abs(bucket.priceLevel() - poc) < tickSize / 2) {
                pocBucket = bucket;
                break;
            }
        }

        if (pocBucket == null) return;

        double buyVol = pocBucket.totalBuyVolume();
        double sellVol = pocBucket.totalSellVolume();
        double totalVol = buyVol + sellVol;

        if (totalVol <= 0) return;

        // Position at POC level
        double pocY = rangeAxis.valueToJava2D(poc, dataArea, RectangleEdge.LEFT);
        int barHeight = 1;
        int y = (int) pocY - barHeight / 2;

        int x = (int) leftX;
        int width = (int) (rightX - leftX);

        // Calculate proportions - left side buy, right side sell
        double buyRatio = buyVol / totalVol;
        int buyWidth = (int) (width * buyRatio);
        int sellWidth = width - buyWidth;

        // Draw buy portion (green) on left
        if (buyWidth > 0) {
            g2.setColor(new Color(
                FootprintHeatmapConfig.BUY_COLOR.getRed(),
                FootprintHeatmapConfig.BUY_COLOR.getGreen(),
                FootprintHeatmapConfig.BUY_COLOR.getBlue(),
                230));
            g2.fillRect(x, y, buyWidth, barHeight);
        }

        // Draw sell portion (red) on right
        if (sellWidth > 0) {
            g2.setColor(new Color(
                FootprintHeatmapConfig.SELL_COLOR.getRed(),
                FootprintHeatmapConfig.SELL_COLOR.getGreen(),
                FootprintHeatmapConfig.SELL_COLOR.getBlue(),
                230));
            g2.fillRect(x + buyWidth, y, sellWidth, barHeight);
        }
    }
}
