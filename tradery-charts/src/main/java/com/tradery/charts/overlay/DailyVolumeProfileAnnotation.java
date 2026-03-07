package com.tradery.charts.overlay;

import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Annotation that draws daily volume profile histograms on the price chart.
 * Shows mini histograms on the LEFT side of each day's bounds, displaying
 * volume distribution at different price levels.
 *
 * Features:
 * - Horizontal bars at price levels, width proportional to volume
 * - POC (Point of Control) bar highlighted in a different color
 * - Colors based on delta direction (green for buy, red for sell) or volume intensity
 * - HVN (High Volume Node) highlighting with glow effect
 * - LVN (Low Volume Node) with dashed/faded style
 */
public class DailyVolumeProfileAnnotation extends AbstractXYAnnotation {

    /**
     * Color mode for volume profile bars.
     */
    public enum ColorMode {
        /** Color based on volume intensity (blue to orange) */
        VOLUME_INTENSITY,
        /** Color based on delta direction (green for buy, red for sell) */
        DELTA,
        /** Blend volume intensity with delta direction */
        DELTA_INTENSITY
    }

    private final List<DayProfile> dayProfiles;
    private final int histogramWidth;
    private ColorMode colorMode = ColorMode.DELTA_INTENSITY;
    private boolean showHvnLvn = true;
    private double hvnThreshold = 0.7;  // Top 30% of volume = HVN
    private double lvnThreshold = 0.2;  // Bottom 20% of volume = LVN

    /**
     * Profile data for a single day.
     */
    public record DayProfile(
        long dayStartTime,      // UTC midnight timestamp
        long dayEndTime,        // End of day timestamp
        double[] priceLevels,   // Bin centers
        double[] volumes,       // Volume at each bin
        double[] deltas,        // Delta (buy - sell) at each bin (may be null)
        double poc,             // Point of Control price
        double vah,             // Value Area High
        double val,             // Value Area Low
        double maxVolume,       // For normalization
        double minPrice,        // Lowest price level
        double maxPrice         // Highest price level
    ) {
        /**
         * Create a DayProfile without delta information (backward compatible).
         */
        public DayProfile(long dayStartTime, long dayEndTime, double[] priceLevels,
                          double[] volumes, double poc, double vah, double val,
                          double maxVolume, double minPrice, double maxPrice) {
            this(dayStartTime, dayEndTime, priceLevels, volumes, null,
                 poc, vah, val, maxVolume, minPrice, maxPrice);
        }
    }

    /**
     * Create a daily volume profile annotation.
     *
     * @param dayProfiles    Pre-calculated day profiles
     * @param histogramWidth Max width in pixels for histogram bars
     */
    public DailyVolumeProfileAnnotation(List<DayProfile> dayProfiles, int histogramWidth) {
        this.dayProfiles = dayProfiles;
        this.histogramWidth = histogramWidth;
    }

    /**
     * Set the color mode for volume profile bars.
     */
    public void setColorMode(ColorMode colorMode) {
        this.colorMode = colorMode;
    }

    /**
     * Get the current color mode.
     */
    public ColorMode getColorMode() {
        return colorMode;
    }

    /**
     * Enable or disable HVN/LVN highlighting.
     */
    public void setShowHvnLvn(boolean showHvnLvn) {
        this.showHvnLvn = showHvnLvn;
    }

    /**
     * Set the HVN threshold (0-1, default 0.7 = top 30% of volume).
     */
    public void setHvnThreshold(double threshold) {
        this.hvnThreshold = Math.max(0.5, Math.min(1.0, threshold));
    }

    /**
     * Set the LVN threshold (0-1, default 0.2 = bottom 20% of volume).
     */
    public void setLvnThreshold(double threshold) {
        this.lvnThreshold = Math.max(0.0, Math.min(0.5, threshold));
    }

    @Override
    public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                     ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                     PlotRenderingInfo info) {

        // Enable anti-aliasing for smoother rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (DayProfile day : dayProfiles) {
            drawDayProfile(g2, plot, dataArea, domainAxis, rangeAxis, day);
        }
    }

    private void drawDayProfile(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                                 ValueAxis domainAxis, ValueAxis rangeAxis, DayProfile day) {

        // Convert day start and end times to screen X coordinates
        double dayStartX = domainAxis.valueToJava2D(day.dayStartTime, dataArea, RectangleEdge.BOTTOM);
        double dayEndX = domainAxis.valueToJava2D(day.dayEndTime, dataArea, RectangleEdge.BOTTOM);

        // Calculate day width in pixels, use 2/3 for histogram
        double dayWidthPx = dayEndX - dayStartX;
        double maxBarWidth = dayWidthPx * 0.67;  // 2/3 of day width

        // Skip if outside visible area
        if (dayEndX < dataArea.getMinX() || dayStartX > dataArea.getMaxX()) {
            return;
        }

        // Skip if day is too narrow to draw meaningfully
        if (maxBarWidth < 5) return;

        double[] priceLevels = day.priceLevels;
        double[] volumes = day.volumes;

        if (priceLevels.length == 0) return;

        // Calculate bin height (price difference between levels)
        double binHeight = priceLevels.length > 1
            ? Math.abs(priceLevels[1] - priceLevels[0])
            : (day.maxPrice - day.minPrice) / 24;

        double[] deltas = day.deltas;

        // Pre-compute integer Y positions for each bin to ensure consistent 1px gaps.
        // Using the bin center for positioning avoids floating-point rounding inconsistencies.
        int[] binTopY = new int[priceLevels.length];
        int[] binBotY = new int[priceLevels.length];
        for (int i = 0; i < priceLevels.length; i++) {
            double top = rangeAxis.valueToJava2D(priceLevels[i] + binHeight / 2, dataArea, RectangleEdge.LEFT);
            double bot = rangeAxis.valueToJava2D(priceLevels[i] - binHeight / 2, dataArea, RectangleEdge.LEFT);
            binTopY[i] = (int) Math.round(Math.min(top, bot));
            binBotY[i] = (int) Math.round(Math.max(top, bot));
        }

        // Draw POC line first (underneath bars)
        drawPocLine(g2, dataArea, rangeAxis, dayStartX, dayEndX, day.poc);

        // Draw day background
        drawDayBackground(g2, dataArea, rangeAxis, dayStartX, dayEndX, day.minPrice, day.maxPrice);

        for (int i = 0; i < priceLevels.length; i++) {
            double volume = volumes[i];
            double delta = deltas != null && i < deltas.length ? deltas[i] : 0;

            if (volume <= 0) continue;

            // Calculate bar width based on relative volume (scaled to 2/3 of day width)
            double normalizedVolume = day.maxVolume > 0 ? volume / day.maxVolume : 0;
            int barWidth = (int) (normalizedVolume * maxBarWidth);

            // Use pre-computed positions with 1px gap between bars
            int y = binTopY[i];
            int barHeight = Math.max(1, binBotY[i] - binTopY[i] - 1);

            // Choose color based on mode and delta
            Color barColor = getBarColor(normalizedVolume, delta, volume);

            // Draw bar starting at day start, extending RIGHT into the day
            int x = (int) dayStartX;

            // Ensure bar is visible
            if (barWidth > 0 && barHeight > 0) {
                // Draw HVN marker (high volume node) — vertical line 2px left of histogram baseline
                if (showHvnLvn && normalizedVolume >= hvnThreshold) {
                    g2.setColor(new Color(255, 255, 255, 80));
                    g2.fillRect(x - 3, y, 1, barHeight);
                }

                g2.setColor(barColor);

                // LVN style: reduced opacity fill (no outline to avoid visual noise)
                if (showHvnLvn && normalizedVolume <= lvnThreshold) {
                    g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 35));
                    g2.fillRect(x, y, barWidth, barHeight);
                } else {
                    g2.fillRect(x, y, barWidth, barHeight);
                }
            }
        }
    }

    private void drawDayBackground(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                                    double dayStartX, double dayEndX, double minPrice, double maxPrice) {
        double highY = rangeAxis.valueToJava2D(maxPrice, dataArea, RectangleEdge.LEFT);
        double lowY = rangeAxis.valueToJava2D(minPrice, dataArea, RectangleEdge.LEFT);

        int x = (int) dayStartX;
        int y = (int) Math.min(highY, lowY);
        int width = (int) (dayEndX - dayStartX);
        int height = (int) Math.abs(lowY - highY);

        // ~10% white background
        g2.setColor(new Color(255, 255, 255, 25));
        g2.fillRect(x, y, width, height);
    }

    private void drawPocLine(Graphics2D g2, Rectangle2D dataArea, ValueAxis rangeAxis,
                              double dayStartX, double dayEndX, double poc) {
        double pocY = rangeAxis.valueToJava2D(poc, dataArea, RectangleEdge.LEFT);

        g2.setColor(new Color(255, 220, 0, 180));  // Yellow POC line
        g2.setStroke(new BasicStroke(0.5f));
        g2.drawLine((int) dayStartX, (int) pocY, (int) dayEndX, (int) pocY);
        g2.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Get color for a volume bar based on mode, delta, and volume.
     */
    private Color getBarColor(double normalizedVolume, double delta, double totalVolume) {
        switch (colorMode) {
            case DELTA:
                return getDeltaColor(delta, totalVolume, 100);

            case DELTA_INTENSITY:
                // Blend delta direction with volume intensity for alpha
                int alpha = (int) (60 + normalizedVolume * 80);  // 60-140 based on volume
                return getDeltaColor(delta, totalVolume, alpha);

            case VOLUME_INTENSITY:
            default:
                return getVolumeColor(normalizedVolume);
        }
    }

    /**
     * Get color based on delta direction.
     */
    private Color getDeltaColor(double delta, double totalVolume, int alpha) {
        if (totalVolume <= 0) {
            return new Color(100, 100, 100, alpha);  // Gray for no volume
        }

        double deltaPct = delta / totalVolume;

        if (deltaPct >= 0.4) {
            return new Color(0x26, 0xA6, 0x5B, alpha);  // Strong buy - bright green
        } else if (deltaPct >= 0.1) {
            return new Color(0x7D, 0xCE, 0xA0, alpha);  // Moderate buy - light green
        } else if (deltaPct <= -0.4) {
            return new Color(0xE7, 0x4C, 0x3C, alpha);  // Strong sell - bright red
        } else if (deltaPct <= -0.1) {
            return new Color(0xF1, 0x94, 0x8A, alpha);  // Moderate sell - light red
        } else {
            return new Color(0x64, 0x64, 0x64, alpha);  // Neutral - gray
        }
    }

    /**
     * Get color for a volume bar based on intensity.
     */
    private Color getVolumeColor(double normalizedVolume) {
        double t = Math.max(0, Math.min(1, normalizedVolume));
        int r = (int) (70 + t * (255 - 70));   // 70 → 255
        int g = (int) (130 + t * (140 - 130));  // 130 → 140
        int b = (int) (220 + t * (50 - 220));   // 220 → 50
        int alpha = (int) (60 + t * 60);         // 60 → 120
        return new Color(r, g, b, alpha);
    }
}
