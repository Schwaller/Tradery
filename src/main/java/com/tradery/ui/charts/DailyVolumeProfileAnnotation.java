package com.tradery.ui.charts;

import com.tradery.indicators.VolumeProfile;
import com.tradery.model.AggTrade;
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
        return calculateDayProfiles(candles, numBins, valueAreaPct, 30);  // Default to 30 days
    }

    /**
     * Calculate day profiles from aggregated trades for accurate volume distribution.
     *
     * @param aggTrades     List of aggregated trades
     * @param numBins       Number of price bins per day
     * @param valueAreaPct  Value area percentage (typically 70%)
     * @param maxDays       Maximum number of recent days to include (0 = unlimited)
     * @return List of DayProfile for each day
     */
    public static List<DayProfile> calculateDayProfilesFromAggTrades(List<AggTrade> aggTrades, int numBins, double valueAreaPct, int maxDays) {
        List<DayProfile> profiles = new ArrayList<>();

        if (aggTrades == null || aggTrades.isEmpty()) {
            return profiles;
        }

        // Group trades by UTC day
        Map<LocalDate, List<AggTrade>> byDay = new LinkedHashMap<>();
        for (AggTrade t : aggTrades) {
            LocalDate day = Instant.ofEpochMilli(t.timestamp())
                .atZone(ZoneOffset.UTC).toLocalDate();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(t);
        }

        // Limit to most recent maxDays if specified
        List<LocalDate> days = new ArrayList<>(byDay.keySet());
        if (maxDays > 0 && days.size() > maxDays) {
            int startIndex = days.size() - maxDays;
            days = days.subList(startIndex, days.size());
        }

        // Calculate volume profile for each day
        for (LocalDate day : days) {
            List<AggTrade> dayTrades = byDay.get(day);
            if (dayTrades == null || dayTrades.isEmpty()) continue;

            // Find price range for the day
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;
            for (AggTrade t : dayTrades) {
                minPrice = Math.min(minPrice, t.price());
                maxPrice = Math.max(maxPrice, t.price());
            }

            if (minPrice >= maxPrice) continue;

            // Create bins
            double binSize = (maxPrice - minPrice) / numBins;
            double[] priceLevels = new double[numBins];
            double[] volumes = new double[numBins];

            for (int i = 0; i < numBins; i++) {
                priceLevels[i] = minPrice + (i + 0.5) * binSize;  // bin center
            }

            // Aggregate volume into bins
            for (AggTrade t : dayTrades) {
                int binIndex = (int) ((t.price() - minPrice) / binSize);
                binIndex = Math.max(0, Math.min(numBins - 1, binIndex));
                volumes[binIndex] += t.quantity();
            }

            // Find POC (max volume bin)
            double maxVol = 0;
            int pocIndex = 0;
            for (int i = 0; i < volumes.length; i++) {
                if (volumes[i] > maxVol) {
                    maxVol = volumes[i];
                    pocIndex = i;
                }
            }
            double poc = priceLevels[pocIndex];

            // Calculate value area (70% of volume around POC)
            double totalVolume = Arrays.stream(volumes).sum();
            double targetVolume = totalVolume * valueAreaPct;

            // Expand from POC until we capture targetVolume
            int lowIdx = pocIndex;
            int highIdx = pocIndex;
            double areaVolume = volumes[pocIndex];

            while (areaVolume < targetVolume && (lowIdx > 0 || highIdx < numBins - 1)) {
                double lowAdd = lowIdx > 0 ? volumes[lowIdx - 1] : 0;
                double highAdd = highIdx < numBins - 1 ? volumes[highIdx + 1] : 0;

                if (lowAdd >= highAdd && lowIdx > 0) {
                    lowIdx--;
                    areaVolume += volumes[lowIdx];
                } else if (highIdx < numBins - 1) {
                    highIdx++;
                    areaVolume += volumes[highIdx];
                } else if (lowIdx > 0) {
                    lowIdx--;
                    areaVolume += volumes[lowIdx];
                } else {
                    break;
                }
            }

            double val = priceLevels[lowIdx];
            double vah = priceLevels[highIdx];

            long dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            profiles.add(new DayProfile(
                dayStart, dayEnd,
                priceLevels, volumes,
                poc, vah, val,
                maxVol, minPrice, maxPrice
            ));
        }

        return profiles;
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

            // Choose color based on volume intensity
            Color barColor = getVolumeColor(normalizedVolume);

            // Draw bar starting at day start, extending RIGHT into the day
            int x = (int) dayStartX;
            int y = (int) Math.min(screenY, screenY2);

            // Ensure bar is visible
            if (barWidth > 0 && barHeight > 0) {
                g2.setColor(barColor);
                g2.fillRect(x, y, barWidth, barHeight);
            }
        }

        // Draw 20% white background rect for the day range
        drawDayBackground(g2, dataArea, rangeAxis, dayStartX, dayEndX, day.minPrice, day.maxPrice);

        // Draw POC as horizontal white line spanning the day
        drawPocLine(g2, dataArea, rangeAxis, dayStartX, dayEndX, day.poc);
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

        g2.setColor(new Color(255, 255, 255, 200));
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawLine((int) dayStartX, (int) pocY, (int) dayEndX, (int) pocY);
    }

    /**
     * Get color for a volume bar based on intensity.
     * Blue for lower volume, Orange for higher volume.
     * POC is drawn as a separate white line.
     * Lower opacity (100) so price/candles are visible on top.
     *
     * @param normalizedVolume Volume normalized to 0-1 range
     * @return Color for the bar
     */
    private Color getVolumeColor(double normalizedVolume) {
        // Blue for lower volume, Orange for higher volume
        // Alpha 100 (~40%) so candles/price line show through
        if (normalizedVolume > 0.6) {
            return new Color(255, 140, 50, 100);  // Orange
        } else {
            return new Color(70, 130, 220, 100);  // Blue
        }
    }
}
