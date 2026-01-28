package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.HistoricRaysCompute;
import com.tradery.charts.indicator.impl.RaysCompute;
import com.tradery.core.indicators.RotatingRays.Ray;
import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.model.Candle;
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
 * Rotating Ray Trendlines overlay.
 *
 * Resistance rays: Start from ATH, rotate clockwise to find successive peaks (descending)
 * Support rays: Start from ATL, rotate counter-clockwise to find successive troughs (ascending)
 *
 * Supports both ChartOverlay (immutable, subscribe-once) and mutable usage (setters + redraw).
 * Subscribes to RaysCompute and HistoricRaysCompute for async background computation.
 */
public class RayOverlay implements ChartOverlay {

    // Resistance ray colors (blue gradient)
    private static final Color[] RESISTANCE_COLORS = {
        new Color(30, 90, 180),
        new Color(50, 110, 200),
        new Color(70, 130, 220),
        new Color(90, 150, 235),
        new Color(110, 170, 250)
    };

    private static final Color[] RESISTANCE_BROKEN_COLORS = {
        new Color(100, 160, 255),
        new Color(120, 175, 255),
        new Color(140, 190, 255),
        new Color(160, 205, 255),
        new Color(180, 220, 255)
    };

    // Support ray colors (violet gradient)
    private static final Color[] SUPPORT_COLORS = {
        new Color(120, 50, 160),
        new Color(140, 70, 180),
        new Color(160, 90, 200),
        new Color(180, 110, 220),
        new Color(200, 130, 240)
    };

    private static final Color[] SUPPORT_BROKEN_COLORS = {
        new Color(180, 120, 255),
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

    private static final int FUTURE_BARS = 20;
    private static final int DEFAULT_MAX_RAYS = 5;

    // Mutable settings
    private int lookback = 200;
    private int skip = 5;
    private int maxRays = DEFAULT_MAX_RAYS;
    private boolean enabled = true;
    private boolean showResistance = true;
    private boolean showSupport = true;
    private boolean showHistoricRays = false;
    private int historicRayInterval = 1;

    // State
    private final List<XYAnnotation> annotations = new ArrayList<>();
    private IndicatorSubscription<RaysCompute.Result> raysSubscription;
    private IndicatorSubscription<HistoricRaysCompute.Result> historicSubscription;
    private XYPlot currentPlot;
    private ChartDataProvider currentProvider;
    private Runnable onDataReady;

    public RayOverlay() {
    }

    public RayOverlay(int lookback, int skip) {
        this.lookback = lookback;
        this.skip = skip;
    }

    public RayOverlay(int lookback, int skip, boolean showResistance, boolean showSupport) {
        this.lookback = lookback;
        this.skip = skip;
        this.showResistance = showResistance;
        this.showSupport = showSupport;
    }

    public RayOverlay(int lookback, int skip, int maxRays, boolean showResistance, boolean showSupport) {
        this.lookback = lookback;
        this.skip = skip;
        this.maxRays = maxRays;
        this.showResistance = showResistance;
        this.showSupport = showSupport;
    }

    // ===== Settings =====

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setLookback(int lookback) { this.lookback = lookback; }
    public int getLookback() { return lookback; }
    public void setSkip(int skip) { this.skip = skip; }
    public int getSkip() { return skip; }
    public void setShowResistance(boolean show) { this.showResistance = show; }
    public boolean isShowResistance() { return showResistance; }
    public void setShowSupport(boolean show) { this.showSupport = show; }
    public boolean isShowSupport() { return showSupport; }
    public void setShowHistoricRays(boolean show) { this.showHistoricRays = show; }
    public boolean isShowHistoricRays() { return showHistoricRays; }
    public void setHistoricRayInterval(int interval) { this.historicRayInterval = Math.max(1, interval); }
    public int getHistoricRayInterval() { return historicRayInterval; }
    public void setOnDataReady(Runnable callback) { this.onDataReady = callback; }

    // ===== ChartOverlay interface =====

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        this.currentPlot = plot;
        this.currentProvider = provider;
        subscribe();
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("Rays");
        if (showResistance && !showSupport) sb.append(" (R)");
        else if (showSupport && !showResistance) sb.append(" (S)");
        return sb.toString();
    }

    @Override
    public int getDatasetCount() {
        return 0;  // Uses annotations, not datasets
    }

    // ===== Mutable API =====

    /**
     * Resubscribe and redraw with current settings.
     * Call after changing settings (lookback, skip, show flags, etc.).
     */
    public void redraw() {
        if (currentPlot == null || currentProvider == null) return;
        if (!enabled) {
            clear();
            return;
        }
        subscribe();
    }

    /**
     * Clear all ray annotations from the plot.
     */
    public void clear() {
        if (currentPlot == null) return;
        for (XYAnnotation ann : annotations) {
            currentPlot.removeAnnotation(ann);
        }
        annotations.clear();
    }

    private void subscribe() {
        List<Candle> candles = currentProvider.getCandles();
        if (candles == null || candles.size() < Math.max(lookback, 20)) return;

        IndicatorPool pool = currentProvider.getIndicatorPool();
        if (pool == null) return;

        // Close previous subscriptions
        if (raysSubscription != null) raysSubscription.close();
        if (historicSubscription != null) historicSubscription.close();

        clear();

        // Subscribe to current rays
        raysSubscription = pool.subscribe(new RaysCompute(lookback, skip, showResistance, showSupport));
        raysSubscription.onReady(result -> {
            if (result == null) return;
            renderCurrentRays(result);
            fireDataReady();
        });

        // Subscribe to historic rays if enabled
        if (showHistoricRays) {
            historicSubscription = pool.subscribe(new HistoricRaysCompute(skip, historicRayInterval));
            historicSubscription.onReady(result -> {
                if (result == null) return;
                renderHistoricRays(result);
                fireDataReady();
            });
        }
    }

    private void fireDataReady() {
        if (currentPlot.getChart() != null) {
            currentPlot.getChart().fireChartChanged();
        }
        if (onDataReady != null) {
            onDataReady.run();
        }
    }

    // ===== Rendering =====

    private void renderCurrentRays(RaysCompute.Result result) {
        List<Candle> candles = currentProvider.getCandles();
        if (candles == null || candles.isEmpty()) return;

        int lastBarIndex = candles.size() - 1;

        if (showResistance && result.resistance() != null) {
            drawRaySet(candles, result.resistance(), lastBarIndex, true);
        }
        if (showSupport && result.support() != null) {
            drawRaySet(candles, result.support(), lastBarIndex, false);
        }
    }

    private void renderHistoricRays(HistoricRaysCompute.Result result) {
        List<Candle> candles = currentProvider.getCandles();
        if (candles == null || candles.isEmpty()) return;

        for (HistoricRaysCompute.Entry entry : result.entries()) {
            if (showResistance && entry.resistance() != null && entry.resistance().count() > 0) {
                drawHistoricRaySet(candles, entry.resistance(), entry.barIndex(), true);
            }
            if (showSupport && entry.support() != null && entry.support().count() > 0) {
                drawHistoricRaySet(candles, entry.support(), entry.barIndex(), false);
            }
        }
    }

    private void drawRaySet(List<Candle> candles, RaySet raySet, int currentBar, boolean isResistance) {
        Color[] normalColors = isResistance ? RESISTANCE_COLORS : SUPPORT_COLORS;
        Color[] brokenColors = isResistance ? RESISTANCE_BROKEN_COLORS : SUPPORT_BROKEN_COLORS;

        addAnchorMarker(candles, raySet);

        int rayCount = Math.min(raySet.count(), maxRays);
        for (int rayNum = 1; rayNum <= rayCount; rayNum++) {
            Ray ray = raySet.getRay(rayNum);
            if (ray == null) continue;

            boolean isBroken = isRayBroken(raySet, ray, candles, currentBar);

            int colorIdx = Math.min(rayNum - 1, normalColors.length - 1);
            Color color = isBroken ? brokenColors[colorIdx] : normalColors[colorIdx];
            BasicStroke stroke = isBroken ? DASHED_STROKE : SOLID_STROKE;

            addRayLine(candles, ray, color, stroke);
            addEndpointMarker(candles, ray, color);
        }
    }

    private boolean isRayBroken(RaySet raySet, Ray ray, List<Candle> candles, int barIndex) {
        if (barIndex < 0 || barIndex >= candles.size()) return false;
        double rayPrice = ray.priceAt(barIndex);
        double price = candles.get(barIndex).close();
        return raySet.isResistance() ? price > rayPrice : price < rayPrice;
    }

    /**
     * Draw a historic ray set (from a past bar). Only draws Ray 1 to avoid clutter.
     */
    private void drawHistoricRaySet(List<Candle> candles, RaySet raySet, int atBar, boolean isResistance) {
        if (raySet.count() < 1) return;
        Ray ray = raySet.getRay(1);
        if (ray == null) return;

        Color color = isResistance ? HISTORIC_RESISTANCE_COLOR : HISTORIC_SUPPORT_COLOR;
        addHistoricRayLine(candles, ray, atBar, color);
    }

    // ===== Annotation helpers =====

    private void addAnchorMarker(List<Candle> candles, RaySet raySet) {
        int anchorBar = raySet.anchorBar();
        if (anchorBar < 0 || anchorBar >= candles.size()) return;

        long timestamp = candles.get(anchorBar).timestamp();
        double price = raySet.anchorPrice();
        Color baseColor = raySet.isResistance() ? RESISTANCE_COLORS[0] : SUPPORT_COLORS[0];
        Color color = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 180);

        AbstractXYAnnotation marker = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double x = domainAxis.valueToJava2D(timestamp, dataArea, plot.getDomainAxisEdge());
                double y = rangeAxis.valueToJava2D(price, dataArea, plot.getRangeAxisEdge());
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.0f));
                double size = 10.0;
                g2.draw(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
            }
        };
        currentPlot.addAnnotation(marker);
        annotations.add(marker);
    }

    private void addRayLine(List<Candle> candles, Ray ray, Color color, BasicStroke stroke) {
        int startBar = ray.startBar();
        int endBar = candles.size() - 1;
        if (startBar < 0 || startBar >= candles.size()) return;

        long startTime = candles.get(startBar).timestamp();
        double startPrice = ray.startPrice();

        long barInterval = endBar > 0 ? candles.get(endBar).timestamp() - candles.get(endBar - 1).timestamp() : 0;
        long endTime = candles.get(endBar).timestamp() + barInterval * FUTURE_BARS;
        double endPrice = ray.priceAt(endBar + FUTURE_BARS);

        AbstractXYAnnotation annotation = new AbstractXYAnnotation() {
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
                g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            }
        };
        currentPlot.addAnnotation(annotation);
        annotations.add(annotation);
    }

    private void addEndpointMarker(List<Candle> candles, Ray ray, Color color) {
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
                g2.setColor(color);
                double size = 5.0;
                g2.fill(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
            }
        };
        currentPlot.addAnnotation(marker);
        annotations.add(marker);
    }

    private void addHistoricRayLine(List<Candle> candles, Ray ray, int toBar, Color color) {
        int startBar = ray.startBar();
        if (startBar < 0 || startBar >= candles.size() || toBar >= candles.size()) return;

        long startTime = candles.get(startBar).timestamp();
        double startPrice = ray.startPrice();
        long endTime = candles.get(toBar).timestamp();
        double endPrice = ray.priceAt(toBar);

        AbstractXYAnnotation annotation = new AbstractXYAnnotation() {
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
                g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
            }
        };
        currentPlot.addAnnotation(annotation);
        annotations.add(annotation);
    }
}
