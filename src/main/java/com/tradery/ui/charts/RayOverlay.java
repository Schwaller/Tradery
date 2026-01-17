package com.tradery.ui.charts;

import com.tradery.indicators.IndicatorEngine;
import com.tradery.indicators.RotatingRays.Ray;
import com.tradery.indicators.RotatingRays.RaySet;
import com.tradery.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Overlay for drawing Rotating Ray Trendlines on the price chart.
 *
 * Resistance rays: drawn from ATH through successive peaks, descending
 * Support rays: drawn from ATL through successive troughs, ascending
 *
 * Visual styling:
 * - Unbroken rays: solid lines (darker)
 * - Broken rays: dashed lines (brighter, more visible)
 * - Ray colors gradient by number (ray 1 = most significant)
 * - Peak/trough markers at ray connection points
 */
public class RayOverlay {

    private final JFreeChart priceChart;
    private final List<XYAnnotation> annotations = new ArrayList<>();

    // Settings
    private int lookback = 200;
    private int skip = 5;
    private boolean enabled = false;
    private boolean showResistance = true;
    private boolean showSupport = true;
    private boolean showHistoricRays = false;
    private int historicRayInterval = 1; // Draw historic rays every N bars (1 = every bar)

    // Colors - resistance rays (blue gradient, brighter = broken)
    private static final Color[] RESISTANCE_COLORS = {
        new Color(30, 90, 180),      // Ray 1 - dark blue (most significant)
        new Color(50, 110, 200),     // Ray 2
        new Color(70, 130, 220),     // Ray 3
        new Color(90, 150, 235),     // Ray 4
        new Color(110, 170, 250)     // Ray 5+ - lightest blue
    };

    // Broken resistance rays are brighter
    private static final Color[] RESISTANCE_BROKEN_COLORS = {
        new Color(100, 160, 255),    // Ray 1 broken - bright blue
        new Color(120, 175, 255),
        new Color(140, 190, 255),
        new Color(160, 205, 255),
        new Color(180, 220, 255)
    };

    // Colors - support rays (violet gradient, brighter = broken)
    private static final Color[] SUPPORT_COLORS = {
        new Color(120, 50, 160),     // Ray 1 - dark violet (most significant)
        new Color(140, 70, 180),     // Ray 2
        new Color(160, 90, 200),     // Ray 3
        new Color(180, 110, 220),    // Ray 4
        new Color(200, 130, 240)     // Ray 5+ - lightest violet
    };

    // Broken support rays are brighter
    private static final Color[] SUPPORT_BROKEN_COLORS = {
        new Color(180, 120, 255),    // Ray 1 broken - bright violet
        new Color(190, 140, 255),
        new Color(200, 160, 255),
        new Color(210, 180, 255),
        new Color(220, 200, 255)
    };

    // Historic ray colors (greyish, semi-transparent)
    private static final Color HISTORIC_RESISTANCE_COLOR = new Color(100, 120, 140, 80);
    private static final Color HISTORIC_SUPPORT_COLOR = new Color(140, 100, 160, 80);

    // Strokes
    private static final BasicStroke SOLID_STROKE = new BasicStroke(1.5f);
    private static final BasicStroke DASHED_STROKE = new BasicStroke(
        2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
        10.0f, new float[]{8.0f, 4.0f}, 0.0f
    );
    private static final BasicStroke HISTORIC_STROKE = new BasicStroke(1.0f);

    public RayOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
    }

    // ===== Settings =====

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setLookback(int lookback) {
        this.lookback = lookback;
    }

    public int getLookback() {
        return lookback;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getSkip() {
        return skip;
    }

    public void setShowResistance(boolean show) {
        this.showResistance = show;
    }

    public boolean isShowResistance() {
        return showResistance;
    }

    public void setShowSupport(boolean show) {
        this.showSupport = show;
    }

    public boolean isShowSupport() {
        return showSupport;
    }

    public void setShowHistoricRays(boolean show) {
        this.showHistoricRays = show;
    }

    public boolean isShowHistoricRays() {
        return showHistoricRays;
    }

    public void setHistoricRayInterval(int interval) {
        this.historicRayInterval = Math.max(1, interval); // Minimum 1 bar
    }

    public int getHistoricRayInterval() {
        return historicRayInterval;
    }

    // ===== Drawing =====

    /**
     * Update the ray overlay with current data.
     * Call this when candles change or when toggling the overlay.
     */
    public void update(List<Candle> candles, IndicatorEngine engine) {
        clear();

        if (!enabled || candles == null || candles.isEmpty() || engine == null) {
            return;
        }

        XYPlot plot = priceChart.getXYPlot();
        int lastBarIndex = candles.size() - 1;

        // Draw historic rays first (so current rays are on top)
        if (showHistoricRays) {
            drawHistoricRays(plot, candles, engine);
        }

        // Draw resistance rays (current, using full dataset)
        if (showResistance) {
            RaySet resistanceRays = engine.getResistanceRaySet(lookback, skip);
            if (resistanceRays != null && resistanceRays.count() > 0) {
                drawRaySet(plot, candles, resistanceRays, lastBarIndex, true);
            }
        }

        // Draw support rays (current, using full dataset)
        if (showSupport) {
            RaySet supportRays = engine.getSupportRaySet(lookback, skip);
            if (supportRays != null && supportRays.count() > 0) {
                drawRaySet(plot, candles, supportRays, lastBarIndex, false);
            }
        }
    }

    /**
     * Draw historic rays showing how rays looked at past points in time.
     * Computes rays at every bar for full fidelity replay.
     *
     * Uses lookback=0 (unlimited) so rays search ALL available data at each historic bar.
     * This ensures meaningful slopes based on full price movements, not constrained windows.
     */
    private void drawHistoricRays(XYPlot plot, List<Candle> candles, IndicatorEngine engine) {
        int startBar = Math.max(20, historicRayInterval); // Need minimum data for ATH/ATL
        int lastBar = candles.size() - 1;

        // Compute and draw rays at each bar
        for (int barIndex = startBar; barIndex < lastBar; barIndex += Math.max(1, historicRayInterval)) {
            if (showResistance) {
                // Use lookback=0 (unlimited) for historic rays to get meaningful slopes
                RaySet historicResistance = engine.getResistanceRaySetAt(0, skip, barIndex);
                if (historicResistance != null && historicResistance.count() > 0) {
                    drawHistoricRaySet(plot, candles, historicResistance, barIndex, true);
                }
            }

            if (showSupport) {
                // Use lookback=0 (unlimited) for historic rays to get meaningful slopes
                RaySet historicSupport = engine.getSupportRaySetAt(0, skip, barIndex);
                if (historicSupport != null && historicSupport.count() > 0) {
                    drawHistoricRaySet(plot, candles, historicSupport, barIndex, false);
                }
            }
        }
    }

    /**
     * Draw a historic ray set (from a past bar) in greyish color.
     * Only draws Ray 1 (most significant) to avoid visual clutter.
     * Skips nearly horizontal rays (slope too small to be meaningful).
     */
    private void drawHistoricRaySet(XYPlot plot, List<Candle> candles, RaySet raySet, int atBar, boolean isResistance) {
        if (raySet.count() < 1) return;

        // Only draw Ray 1 for historic visualization (less clutter)
        Ray ray = raySet.getRay(1);
        if (ray == null) return;

        // TODO: Re-enable horizontal filter after debugging
        // Skip nearly horizontal rays - they're not meaningful trendlines
        // double slopePercent = Math.abs(ray.slope() / ray.startPrice()) * 100;
        // if (slopePercent < 0.001) {
        //     return; // Too flat, skip
        // }

        Color color = isResistance ? HISTORIC_RESISTANCE_COLOR : HISTORIC_SUPPORT_COLOR;

        // Draw the ray line from its start to the atBar point (where it was computed)
        addHistoricRayLine(plot, candles, ray, atBar, color);
    }

    /**
     * Draw a historic ray line segment (from ray start to the bar where it was computed).
     */
    private void addHistoricRayLine(XYPlot plot, List<Candle> candles, Ray ray, int toBar, Color color) {
        int startBar = ray.startBar();
        if (startBar < 0 || startBar >= candles.size() || toBar >= candles.size()) return;

        long startTime = candles.get(startBar).timestamp();
        double startPrice = ray.startPrice();

        long endTime = candles.get(toBar).timestamp();
        double endPrice = ray.priceAt(toBar);

        AbstractXYAnnotation lineAnnotation = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x1 = domainAxis.valueToJava2D(startTime, dataArea, plot.getDomainAxisEdge());
                double y1 = rangeAxis.valueToJava2D(startPrice, dataArea, plot.getRangeAxisEdge());
                double x2 = domainAxis.valueToJava2D(endTime, dataArea, plot.getDomainAxisEdge());
                double y2 = rangeAxis.valueToJava2D(endPrice, dataArea, plot.getRangeAxisEdge());

                g2.setColor(color);
                g2.setStroke(HISTORIC_STROKE);
                g2.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
            }
        };
        plot.addAnnotation(lineAnnotation);
        annotations.add(lineAnnotation);
    }

    private void drawRaySet(XYPlot plot, List<Candle> candles, RaySet raySet, int currentBar, boolean isResistance) {
        Color[] normalColors = isResistance ? RESISTANCE_COLORS : SUPPORT_COLORS;
        Color[] brokenColors = isResistance ? RESISTANCE_BROKEN_COLORS : SUPPORT_BROKEN_COLORS;

        // Draw anchor point marker (ATH/ATL)
        addAnchorMarker(plot, candles, raySet);

        // Draw each ray
        for (int rayNum = 1; rayNum <= raySet.count(); rayNum++) {
            Ray ray = raySet.getRay(rayNum);
            if (ray == null) continue;

            // Determine if ray is broken at current bar
            boolean isBroken = isRayBrokenAtBar(raySet, rayNum, candles, currentBar);

            // Select colors and stroke based on broken status
            int colorIdx = Math.min(rayNum - 1, normalColors.length - 1);
            Color color = isBroken ? brokenColors[colorIdx] : normalColors[colorIdx];
            BasicStroke stroke = isBroken ? DASHED_STROKE : SOLID_STROKE;

            // Draw the ray line (extends from start to end of visible data)
            addRayLine(plot, candles, ray, color, stroke);

            // Add peak/trough marker at ray endpoints
            addEndpointMarker(plot, candles, ray, color);
        }
    }

    private boolean isRayBrokenAtBar(RaySet raySet, int rayNum, List<Candle> candles, int barIndex) {
        Ray ray = raySet.getRay(rayNum);
        if (ray == null || barIndex < 0 || barIndex >= candles.size()) {
            return false;
        }

        double rayPrice = ray.priceAt(barIndex);
        double price = candles.get(barIndex).close();

        if (raySet.isResistance()) {
            return price > rayPrice;
        } else {
            return price < rayPrice;
        }
    }

    private void addAnchorMarker(XYPlot plot, List<Candle> candles, RaySet raySet) {
        int anchorBar = raySet.anchorBar();
        if (anchorBar < 0 || anchorBar >= candles.size()) return;

        long timestamp = candles.get(anchorBar).timestamp();
        double price = raySet.anchorPrice();
        Color baseColor = raySet.isResistance() ? RESISTANCE_COLORS[0] : SUPPORT_COLORS[0];
        // Make slightly transparent
        Color color = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 180);

        AbstractXYAnnotation marker = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x = domainAxis.valueToJava2D(timestamp, dataArea, plot.getDomainAxisEdge());
                double y = rangeAxis.valueToJava2D(price, dataArea, plot.getRangeAxisEdge());

                // Draw unfilled circle for ATH/ATL anchor
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.0f));
                double size = 10.0;
                g2.draw(new Ellipse2D.Double(x - size/2, y - size/2, size, size));
            }
        };
        plot.addAnnotation(marker);
        annotations.add(marker);
    }

    private void addRayLine(XYPlot plot, List<Candle> candles, Ray ray, Color color, BasicStroke stroke) {
        // Ray extends from its start point to the end of the chart
        int startBar = ray.startBar();
        int endBar = candles.size() - 1;

        if (startBar < 0 || startBar >= candles.size()) return;

        long startTime = candles.get(startBar).timestamp();
        double startPrice = ray.startPrice();

        long endTime = candles.get(endBar).timestamp();
        double endPrice = ray.priceAt(endBar);

        AbstractXYAnnotation lineAnnotation = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x1 = domainAxis.valueToJava2D(startTime, dataArea, plot.getDomainAxisEdge());
                double y1 = rangeAxis.valueToJava2D(startPrice, dataArea, plot.getRangeAxisEdge());
                double x2 = domainAxis.valueToJava2D(endTime, dataArea, plot.getDomainAxisEdge());
                double y2 = rangeAxis.valueToJava2D(endPrice, dataArea, plot.getRangeAxisEdge());

                g2.setColor(color);
                g2.setStroke(stroke);
                g2.drawLine((int)x1, (int)y1, (int)x2, (int)y2);
            }
        };
        plot.addAnnotation(lineAnnotation);
        annotations.add(lineAnnotation);
    }

    private void addEndpointMarker(XYPlot plot, List<Candle> candles, Ray ray, Color color) {
        // Mark the end point (next peak/trough) of the ray
        int endBar = ray.endBar();
        if (endBar < 0 || endBar >= candles.size()) return;

        long timestamp = candles.get(endBar).timestamp();
        double price = ray.endPrice();

        AbstractXYAnnotation marker = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x = domainAxis.valueToJava2D(timestamp, dataArea, plot.getDomainAxisEdge());
                double y = rangeAxis.valueToJava2D(price, dataArea, plot.getRangeAxisEdge());

                // Draw small circle marker
                g2.setColor(color);
                double size = 5.0;
                g2.fill(new Ellipse2D.Double(x - size/2, y - size/2, size, size));
            }
        };
        plot.addAnnotation(marker);
        annotations.add(marker);
    }

    /**
     * Clear all ray annotations from the chart.
     */
    public void clear() {
        if (priceChart == null) return;

        XYPlot plot = priceChart.getXYPlot();
        for (XYAnnotation annotation : annotations) {
            plot.removeAnnotation(annotation);
        }
        annotations.clear();
    }
}
