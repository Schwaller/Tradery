package com.tradery.forge.ui.charts;

import com.tradery.core.indicators.VolumeProfile;
import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
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
            double[] deltas = new double[numBins];

            for (int i = 0; i < numBins; i++) {
                priceLevels[i] = minPrice + (i + 0.5) * binSize;  // bin center
            }

            // Aggregate volume and delta into bins
            for (AggTrade t : dayTrades) {
                int binIndex = (int) ((t.price() - minPrice) / binSize);
                binIndex = Math.max(0, Math.min(numBins - 1, binIndex));
                volumes[binIndex] += t.quantity();
                // Delta: positive for aggressive buys, negative for aggressive sells
                deltas[binIndex] += t.delta();
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
                priceLevels, volumes, deltas,
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
     *
     * @param normalizedVolume Volume normalized to 0-1 range
     * @param delta            Buy - sell volume (positive = more buys)
     * @param totalVolume      Total volume at this level
     * @return Color for the bar
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
     *
     * @param delta       Buy - sell volume
     * @param totalVolume Total volume at this level
     * @param alpha       Alpha value for the color
     * @return Color based on delta direction
     */
    private Color getDeltaColor(double delta, double totalVolume, int alpha) {
        if (totalVolume <= 0) {
            return new Color(100, 100, 100, alpha);  // Gray for no volume
        }

        // Calculate delta percentage (-1 to +1)
        double deltaPct = delta / totalVolume;

        // Color ranges:
        // Strong buy (>40%):  Bright green #26A65B
        // Moderate buy (10-40%): Light green #7DCEA0
        // Neutral (-10% to 10%): Gray #646464
        // Moderate sell (-40% to -10%): Light red #F1948A
        // Strong sell (<-40%): Bright red #E74C3C

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
     * Blue for lower volume, Orange for higher volume.
     * POC is drawn as a separate white line.
     * Lower opacity (100) so price/candles are visible on top.
     *
     * @param normalizedVolume Volume normalized to 0-1 range
     * @return Color for the bar
     */
    private Color getVolumeColor(double normalizedVolume) {
        // Smooth gradient: blue (low volume) → orange (high volume)
        // Alpha scales with volume so low-volume bars are more transparent
        double t = Math.max(0, Math.min(1, normalizedVolume));
        int r = (int) (70 + t * (255 - 70));   // 70 → 255
        int g = (int) (130 + t * (140 - 130));  // 130 → 140
        int b = (int) (220 + t * (50 - 220));   // 220 → 50
        int alpha = (int) (60 + t * 60);         // 60 → 120
        return new Color(r, g, b, alpha);
    }
}
