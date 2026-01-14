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

    // Colors - resistance rays (red gradient, brighter = broken)
    private static final Color[] RESISTANCE_COLORS = {
        new Color(180, 60, 60),      // Ray 1 - darkest red (most significant)
        new Color(200, 80, 80),      // Ray 2
        new Color(220, 100, 100),    // Ray 3
        new Color(240, 120, 120),    // Ray 4
        new Color(255, 140, 140)     // Ray 5+ - lightest
    };

    // Broken resistance rays are brighter
    private static final Color[] RESISTANCE_BROKEN_COLORS = {
        new Color(255, 100, 100),    // Ray 1 broken - bright red
        new Color(255, 120, 120),
        new Color(255, 140, 140),
        new Color(255, 160, 160),
        new Color(255, 180, 180)
    };

    // Colors - support rays (green gradient, brighter = broken)
    private static final Color[] SUPPORT_COLORS = {
        new Color(60, 140, 60),      // Ray 1 - darkest green (most significant)
        new Color(80, 160, 80),
        new Color(100, 180, 100),
        new Color(120, 200, 120),
        new Color(140, 220, 140)
    };

    // Broken support rays are brighter
    private static final Color[] SUPPORT_BROKEN_COLORS = {
        new Color(100, 255, 100),    // Ray 1 broken - bright green
        new Color(120, 255, 120),
        new Color(140, 255, 140),
        new Color(160, 255, 160),
        new Color(180, 255, 180)
    };

    // Strokes
    private static final BasicStroke SOLID_STROKE = new BasicStroke(1.5f);
    private static final BasicStroke DASHED_STROKE = new BasicStroke(
        2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
        10.0f, new float[]{8.0f, 4.0f}, 0.0f
    );

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

        // Draw resistance rays
        if (showResistance) {
            RaySet resistanceRays = engine.getResistanceRaySet(lookback, skip);
            if (resistanceRays != null && resistanceRays.count() > 0) {
                drawRaySet(plot, candles, resistanceRays, lastBarIndex, true);
            }
        }

        // Draw support rays
        if (showSupport) {
            RaySet supportRays = engine.getSupportRaySet(lookback, skip);
            if (supportRays != null && supportRays.count() > 0) {
                drawRaySet(plot, candles, supportRays, lastBarIndex, false);
            }
        }
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
        Color color = raySet.isResistance() ? RESISTANCE_COLORS[0] : SUPPORT_COLORS[0];

        AbstractXYAnnotation marker = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x = domainAxis.valueToJava2D(timestamp, dataArea, plot.getDomainAxisEdge());
                double y = rangeAxis.valueToJava2D(price, dataArea, plot.getRangeAxisEdge());

                // Draw star marker for ATH/ATL
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.0f));
                double size = 8.0;

                // Draw diamond shape
                int[] xPoints = {(int)x, (int)(x + size), (int)x, (int)(x - size)};
                int[] yPoints = {(int)(y - size), (int)y, (int)(y + size), (int)y};
                g2.drawPolygon(xPoints, yPoints, 4);
                g2.fillPolygon(xPoints, yPoints, 4);
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
