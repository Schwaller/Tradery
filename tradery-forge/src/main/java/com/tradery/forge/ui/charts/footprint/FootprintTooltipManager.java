package com.tradery.forge.ui.charts.footprint;

import com.tradery.core.model.Exchange;
import com.tradery.core.model.Footprint;
import com.tradery.core.model.FootprintBucket;
import com.tradery.core.model.FootprintResult;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.Map;

/**
 * Manages hover tooltips for footprint heatmap visualization.
 * Shows detailed bucket information including per-exchange breakdown.
 */
public class FootprintTooltipManager implements MouseMotionListener {

    private final ChartPanel chartPanel;
    private final JFreeChart chart;
    private FootprintResult footprintResult;
    private FootprintHeatmapConfig config;

    // Tooltip popup
    private JToolTip tooltip;
    private Popup currentPopup;

    // Number formatters
    private static final NumberFormat VOLUME_FORMAT = NumberFormat.getNumberInstance();
    private static final NumberFormat DELTA_FORMAT = NumberFormat.getNumberInstance();
    private static final NumberFormat RATIO_FORMAT = NumberFormat.getNumberInstance();
    private static final NumberFormat PRICE_FORMAT = NumberFormat.getNumberInstance();

    static {
        VOLUME_FORMAT.setMaximumFractionDigits(2);
        VOLUME_FORMAT.setGroupingUsed(true);
        DELTA_FORMAT.setMaximumFractionDigits(2);
        DELTA_FORMAT.setGroupingUsed(true);
        RATIO_FORMAT.setMaximumFractionDigits(2);
        PRICE_FORMAT.setMaximumFractionDigits(2);
        PRICE_FORMAT.setGroupingUsed(true);
    }

    public FootprintTooltipManager(ChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        this.chart = chartPanel.getChart();
        this.tooltip = new JToolTip();
        this.tooltip.setBackground(new Color(40, 40, 40));
        this.tooltip.setForeground(Color.WHITE);
        this.tooltip.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
    }

    /**
     * Set the footprint data for tooltip lookup.
     */
    public void setFootprintResult(FootprintResult result) {
        this.footprintResult = result;
    }

    /**
     * Set the config for display mode.
     */
    public void setConfig(FootprintHeatmapConfig config) {
        this.config = config;
    }

    /**
     * Enable tooltip tracking.
     */
    public void enable() {
        chartPanel.addMouseMotionListener(this);
    }

    /**
     * Disable tooltip tracking.
     */
    public void disable() {
        chartPanel.removeMouseMotionListener(this);
        hideTooltip();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (footprintResult == null || footprintResult.footprints().isEmpty()) {
            hideTooltip();
            return;
        }

        // Get mouse position in data coordinates
        XYPlot plot = chart.getXYPlot();
        Rectangle2D dataArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();

        if (!dataArea.contains(e.getPoint())) {
            hideTooltip();
            return;
        }

        ValueAxis domainAxis = plot.getDomainAxis();
        ValueAxis rangeAxis = plot.getRangeAxis();

        double mouseX = domainAxis.java2DToValue(e.getX(), dataArea, RectangleEdge.BOTTOM);
        double mouseY = rangeAxis.java2DToValue(e.getY(), dataArea, RectangleEdge.LEFT);

        // Find the footprint and bucket at this position
        FootprintBucket bucket = findBucketAt(mouseX, mouseY);

        if (bucket == null) {
            hideTooltip();
            return;
        }

        // Build tooltip text
        String tooltipText = buildTooltipText(bucket, mouseY);
        showTooltip(e.getXOnScreen(), e.getYOnScreen(), tooltipText);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        hideTooltip();
    }

    /**
     * Find the bucket at the given data coordinates.
     */
    private FootprintBucket findBucketAt(double timestamp, double price) {
        // Find the footprint for this timestamp
        Footprint footprint = null;
        long ts = (long) timestamp;

        // Estimate candle interval
        long interval = estimateCandleInterval();

        for (Footprint fp : footprintResult.footprints()) {
            if (Math.abs(fp.timestamp() - ts) < interval) {
                footprint = fp;
                break;
            }
        }

        if (footprint == null) {
            return null;
        }

        // Find the bucket at this price level
        return footprint.getBucketAt(price);
    }

    /**
     * Estimate candle interval from footprint data.
     */
    private long estimateCandleInterval() {
        if (footprintResult.footprints().size() < 2) {
            return 3600000L; // Default 1 hour
        }

        long first = footprintResult.footprints().get(0).timestamp();
        long second = footprintResult.footprints().get(1).timestamp();
        return second - first;
    }

    /**
     * Build tooltip HTML text for a bucket.
     */
    private String buildTooltipText(FootprintBucket bucket, double price) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='font-family: monospace; padding: 4px;'>");

        // Price level header
        sb.append("<b style='color: #FFD700;'>Price: ").append(PRICE_FORMAT.format(bucket.priceLevel())).append("</b><br>");
        sb.append("<hr style='border-color: #555;'>");

        // Volume summary
        sb.append("<table style='border-spacing: 8px 2px;'>");
        sb.append("<tr><td>Buy Vol:</td><td style='color: #26A65B;'>").append(formatVolume(bucket.totalBuyVolume())).append("</td></tr>");
        sb.append("<tr><td>Sell Vol:</td><td style='color: #E74C3C;'>").append(formatVolume(bucket.totalSellVolume())).append("</td></tr>");
        sb.append("<tr><td>Total:</td><td>").append(formatVolume(bucket.totalVolume())).append("</td></tr>");

        // Delta with color
        double delta = bucket.totalDelta();
        String deltaColor = delta >= 0 ? "#26A65B" : "#E74C3C";
        sb.append("<tr><td>Delta:</td><td style='color: ").append(deltaColor).append(";'>")
          .append(delta >= 0 ? "+" : "").append(DELTA_FORMAT.format(delta)).append("</td></tr>");

        // Imbalance ratio
        double ratio = bucket.imbalanceRatio();
        String ratioStr = ratio >= 100 ? ">100:1" : (ratio <= 0.01 ? "<1:100" : RATIO_FORMAT.format(ratio) + ":1");
        String ratioColor = ratio >= 3 ? "#26A65B" : (ratio <= 0.33 ? "#E74C3C" : "#888");
        sb.append("<tr><td>Imbalance:</td><td style='color: ").append(ratioColor).append(";'>").append(ratioStr).append("</td></tr>");

        sb.append("</table>");

        // Imbalance indicator
        if (bucket.hasBuyImbalance()) {
            sb.append("<div style='color: #26A65B; margin-top: 4px;'>BUY IMBALANCE</div>");
        } else if (bucket.hasSellImbalance()) {
            sb.append("<div style='color: #E74C3C; margin-top: 4px;'>SELL IMBALANCE</div>");
        }

        // Exchange breakdown (if in combined mode and has multiple exchanges)
        if (config == null || config.getDisplayMode() == FootprintDisplayMode.COMBINED) {
            Map<Exchange, Double> buyByEx = bucket.buyVolumeByExchange();
            Map<Exchange, Double> sellByEx = bucket.sellVolumeByExchange();

            if (buyByEx.size() > 1 || sellByEx.size() > 1) {
                sb.append("<hr style='border-color: #555;'>");
                sb.append("<div style='color: #AAA;'>Exchange Breakdown:</div>");
                sb.append("<table style='border-spacing: 8px 2px;'>");

                for (Exchange ex : Exchange.values()) {
                    double exBuy = buyByEx.getOrDefault(ex, 0.0);
                    double exSell = sellByEx.getOrDefault(ex, 0.0);
                    if (exBuy > 0 || exSell > 0) {
                        double exDelta = exBuy - exSell;
                        String exDeltaColor = exDelta >= 0 ? "#26A65B" : "#E74C3C";
                        sb.append("<tr><td>").append(ex.getShortName()).append(":</td>")
                          .append("<td style='color: ").append(exDeltaColor).append(";'>")
                          .append(exDelta >= 0 ? "+" : "").append(DELTA_FORMAT.format(exDelta))
                          .append("</td></tr>");
                    }
                }

                sb.append("</table>");
            }

            // Exchange divergence warning
            if (bucket.hasExchangeDivergence()) {
                sb.append("<div style='color: #FFA500; margin-top: 4px;'>EXCHANGE DIVERGENCE</div>");
            }
        }

        sb.append("</div></html>");
        return sb.toString();
    }

    /**
     * Format volume for display.
     */
    private String formatVolume(double volume) {
        if (volume >= 1_000_000) {
            return VOLUME_FORMAT.format(volume / 1_000_000) + "M";
        } else if (volume >= 1_000) {
            return VOLUME_FORMAT.format(volume / 1_000) + "K";
        } else {
            return VOLUME_FORMAT.format(volume);
        }
    }

    /**
     * Show tooltip at screen position.
     */
    private void showTooltip(int x, int y, String text) {
        hideTooltip();

        tooltip.setTipText(text);
        PopupFactory factory = PopupFactory.getSharedInstance();
        currentPopup = factory.getPopup(chartPanel, tooltip, x + 15, y + 15);
        currentPopup.show();
    }

    /**
     * Hide the current tooltip.
     */
    private void hideTooltip() {
        if (currentPopup != null) {
            currentPopup.hide();
            currentPopup = null;
        }
    }
}
