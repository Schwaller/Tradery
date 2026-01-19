package com.tradery.ui.charts;

import com.tradery.indicators.VolumeProfile;
import com.tradery.model.Candle;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.time.*;
import java.util.*;
import java.util.List;

/**
 * Annotation that draws daily volume profile histograms on the price chart.
 * Shows mini histograms on the LEFT side of each day's bounds, displaying
 * volume distribution at different price levels.
 *
 * Features:
 * - Horizontal bars at price levels, width proportional to volume
 * - POC (Point of Control) bar highlighted in a different color
 * - Colors based on volume intensity (cool→warm palette)
 */
public class DailyVolumeProfileAnnotation extends AbstractXYAnnotation {

    private final List<DayProfile> dayProfiles;
    private final int histogramWidth;

    /**
     * Profile data for a single day.
     */
    public record DayProfile(
        long dayStartTime,      // UTC midnight timestamp
        long dayEndTime,        // End of day timestamp
        double[] priceLevels,   // Bin centers
        double[] volumes,       // Volume at each bin
        double poc,             // Point of Control price
        double vah,             // Value Area High
        double val,             // Value Area Low
        double maxVolume,       // For normalization
        double minPrice,        // Lowest price level
        double maxPrice         // Highest price level
    ) {}

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
     * Calculate day profiles from candle data.
     *
     * @param candles       List of candles to analyze
     * @param numBins       Number of price bins per day (default 24)
     * @param valueAreaPct  Value area percentage (typically 70%)
     * @return List of DayProfile for each day
     */
    public static List<DayProfile> calculateDayProfiles(List<Candle> candles, int numBins, double valueAreaPct) {
        return calculateDayProfiles(candles, numBins, valueAreaPct, 14);  // Default to 14 days
    }

    /**
     * Calculate day profiles from candle data with a maximum day limit.
     *
     * @param candles       List of candles to analyze
     * @param numBins       Number of price bins per day (default 24)
     * @param valueAreaPct  Value area percentage (typically 70%)
     * @param maxDays       Maximum number of recent days to include (0 = unlimited)
     * @return List of DayProfile for each day
     */
    public static List<DayProfile> calculateDayProfiles(List<Candle> candles, int numBins, double valueAreaPct, int maxDays) {
        List<DayProfile> profiles = new ArrayList<>();

        if (candles == null || candles.isEmpty()) {
            return profiles;
        }

        // Group candles by UTC day
        Map<LocalDate, List<Candle>> byDay = new LinkedHashMap<>();
        for (Candle c : candles) {
            LocalDate day = Instant.ofEpochMilli(c.timestamp())
                .atZone(ZoneOffset.UTC).toLocalDate();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(c);
        }

        // Limit to most recent maxDays if specified
        List<LocalDate> days = new ArrayList<>(byDay.keySet());
        if (maxDays > 0 && days.size() > maxDays) {
            // Keep only the most recent maxDays
            int startIndex = days.size() - maxDays;
            days = days.subList(startIndex, days.size());
        }

        // Calculate volume profile for each day
        for (LocalDate day : days) {
            List<Candle> dayCandles = byDay.get(day);
            if (dayCandles == null || dayCandles.isEmpty()) continue;

            // Calculate volume profile using existing VolumeProfile class
            VolumeProfile.Result vp = VolumeProfile.calculate(dayCandles, dayCandles.size(), numBins, valueAreaPct);

            if (vp.priceLevels().length == 0) continue;

            long dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            double maxVol = Arrays.stream(vp.volumes()).max().orElse(1.0);

            // Find price range
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;
            for (Candle c : dayCandles) {
                minPrice = Math.min(minPrice, c.low());
                maxPrice = Math.max(maxPrice, c.high());
            }

            profiles.add(new DayProfile(
                dayStart, dayEnd,
                vp.priceLevels(), vp.volumes(),
                vp.poc(), vp.vah(), vp.val(),
                maxVol, minPrice, maxPrice
            ));
        }

        return profiles;
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

        for (int i = 0; i < priceLevels.length; i++) {
            double priceLevel = priceLevels[i];
            double volume = volumes[i];

            if (volume <= 0) continue;

            // Convert price to screen Y
            double screenY = rangeAxis.valueToJava2D(priceLevel + binHeight / 2, dataArea, RectangleEdge.LEFT);
            double screenY2 = rangeAxis.valueToJava2D(priceLevel - binHeight / 2, dataArea, RectangleEdge.LEFT);

            // Calculate bar width based on relative volume (scaled to 2/3 of day width)
            double normalizedVolume = day.maxVolume > 0 ? volume / day.maxVolume : 0;
            int barWidth = (int) (normalizedVolume * maxBarWidth);

            // Calculate bar height in pixels
            int barHeight = Math.max(1, Math.abs((int)(screenY2 - screenY)));

            // Determine if this is the POC level
            boolean isPOC = Math.abs(priceLevel - day.poc) < binHeight / 2;

            // Choose color based on volume intensity and POC
            Color barColor = getVolumeColor(normalizedVolume, isPOC);

            // Draw bar starting at day start, extending RIGHT into the day
            int x = (int) dayStartX;
            int y = (int) Math.min(screenY, screenY2);

            // Ensure bar is visible
            if (barWidth > 0 && barHeight > 0) {
                g2.setColor(barColor);
                g2.fillRect(x, y, barWidth, barHeight);

                // Draw thin border for POC
                if (isPOC) {
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.drawRect(x, y, barWidth, barHeight);
                }
            }
        }

        // Draw VAH/VAL markers at the right edge of histogram area
        drawValueAreaMarker(g2, dataArea, domainAxis, rangeAxis, day.dayStartTime, maxBarWidth, day.vah);
        drawValueAreaMarker(g2, dataArea, domainAxis, rangeAxis, day.dayStartTime, maxBarWidth, day.val);
    }

    private void drawValueAreaMarker(Graphics2D g2, Rectangle2D dataArea,
                                      ValueAxis domainAxis, ValueAxis rangeAxis,
                                      long dayStartTime, double maxBarWidth, double price) {
        double dayStartX = domainAxis.valueToJava2D(dayStartTime, dataArea, RectangleEdge.BOTTOM);
        double screenY = rangeAxis.valueToJava2D(price, dataArea, RectangleEdge.LEFT);

        // Draw small tick mark at the right edge of the histogram area
        g2.setColor(new Color(150, 150, 150, 150));
        g2.setStroke(new BasicStroke(1.0f));
        int tickX = (int) (dayStartX + maxBarWidth);
        g2.drawLine(tickX, (int) screenY, tickX + 3, (int) screenY);
    }

    /**
     * Get color for a volume bar based on intensity.
     *
     * @param normalizedVolume Volume normalized to 0-1 range
     * @param isPOC            True if this is the Point of Control
     * @return Color for the bar
     */
    private Color getVolumeColor(double normalizedVolume, boolean isPOC) {
        if (isPOC) {
            // POC gets highlighted with the floating POC color (magenta)
            return ChartStyles.FLOATING_POC_COLOR;
        }

        // Use volume color palette for gradual intensity
        Color[] palette = ChartStyles.VOLUME_COLORS;
        int colorIndex = (int) (normalizedVolume * (palette.length - 1));
        colorIndex = Math.min(colorIndex, palette.length - 1);

        // Add some transparency for non-POC bars
        Color baseColor = palette[colorIndex];
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 150);
    }
}
