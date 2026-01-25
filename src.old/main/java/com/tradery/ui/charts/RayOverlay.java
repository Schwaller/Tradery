package com.tradery.ui.charts;

import com.tradery.ApplicationContext;
import com.tradery.data.PageState;
import com.tradery.data.page.IndicatorPage;
import com.tradery.data.page.IndicatorPageListener;
import com.tradery.data.page.IndicatorPageManager;
import com.tradery.data.page.IndicatorPageManager.HistoricRays;
import com.tradery.data.page.IndicatorType;
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
 * Uses IndicatorPageManager for background computation - never blocks EDT.
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
    private int historicRayInterval = 1;

    // Current data context
    private List<Candle> currentCandles;
    private String currentSymbol;
    private String currentTimeframe;
    private long currentStartTime;
    private long currentEndTime;

    // Indicator pages (background computed)
    private IndicatorPage<RaySet> resistancePage;
    private IndicatorPage<RaySet> supportPage;
    private IndicatorPage<HistoricRays> historicPage;

    // Listeners (stored to release later)
    private final RayPageListener resistanceListener = new RayPageListener();
    private final RayPageListener supportListener = new RayPageListener();
    private final HistoricRayPageListener historicListener = new HistoricRayPageListener();

    // Callback for repaint
    private Runnable onDataReady;

    // Colors - resistance rays (blue gradient, brighter = broken)
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

    // Colors - support rays (violet gradient)
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

    public RayOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
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
        this.historicRayInterval = Math.max(1, interval);
    }

    public int getHistoricRayInterval() {
        return historicRayInterval;
    }

    // ===== Data Request =====

    /**
     * Request ray computation for given candles.
     * This is non-blocking - rays will be computed in background.
     * Call redraw() to draw whatever data is currently available.
     */
    public void requestData(List<Candle> candles, String symbol, String timeframe,
                            long startTime, long endTime) {
        if (candles == null || candles.isEmpty()) {
            releasePages();
            return;
        }

        if (!enabled) {
            this.currentCandles = candles;
            releasePages();
            return;
        }

        // Check if we already have pages with the same params - reuse them
        if (resistancePage != null &&
            symbol.equals(currentSymbol) &&
            timeframe.equals(currentTimeframe) &&
            startTime == currentStartTime &&
            endTime == currentEndTime) {
            this.currentCandles = candles;
            return;
        }

        // Release previous pages since params changed
        releasePages();

        this.currentCandles = candles;
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr == null) return;

        // Request resistance rays
        if (showResistance) {
            String params = lookback + ":" + skip;
            resistancePage = pageMgr.request(
                IndicatorType.RESISTANCE_RAYS, params,
                symbol, timeframe, startTime, endTime,
                resistanceListener,
                "RayOverlay-Resistance");
        }

        // Request support rays
        if (showSupport) {
            String params = lookback + ":" + skip;
            supportPage = pageMgr.request(
                IndicatorType.SUPPORT_RAYS, params,
                symbol, timeframe, startTime, endTime,
                supportListener,
                "RayOverlay-Support");
        }

        // Request historic rays
        if (showHistoricRays) {
            String params = skip + ":" + historicRayInterval;
            historicPage = pageMgr.request(
                IndicatorType.HISTORIC_RAYS, params,
                symbol, timeframe, startTime, endTime,
                historicListener,
                "RayOverlay-Historic");
        }
    }

    /**
     * Release indicator pages when no longer needed.
     */
    public void releasePages() {
        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr == null) return;

        if (resistancePage != null) {
            pageMgr.release(resistancePage, resistanceListener);
            resistancePage = null;
        }
        if (supportPage != null) {
            pageMgr.release(supportPage, supportListener);
            supportPage = null;
        }
        if (historicPage != null) {
            pageMgr.release(historicPage, historicListener);
            historicPage = null;
        }
    }

    // ===== Drawing =====

    /**
     * Redraw rays using currently available data.
     * This is fast - no computation, just draws pre-computed rays.
     */
    public void redraw() {
        clear();

        if (!enabled || currentCandles == null || currentCandles.isEmpty()) {
            return;
        }

        XYPlot plot = priceChart.getXYPlot();
        int lastBarIndex = currentCandles.size() - 1;

        // Draw historic rays first (so current rays are on top)
        if (showHistoricRays && historicPage != null && historicPage.hasData()) {
            drawHistoricRays(plot, historicPage.getData());
        }

        // Draw current resistance rays
        if (showResistance && resistancePage != null && resistancePage.hasData()) {
            drawRaySet(plot, currentCandles, resistancePage.getData(), lastBarIndex, true);
        }

        // Draw current support rays
        if (showSupport && supportPage != null && supportPage.hasData()) {
            drawRaySet(plot, currentCandles, supportPage.getData(), lastBarIndex, false);
        }
    }

    /**
     * Draw historic rays from pre-computed data.
     */
    private void drawHistoricRays(XYPlot plot, HistoricRays historicRays) {
        for (HistoricRays.HistoricRayEntry entry : historicRays.entries()) {
            if (showResistance && entry.resistance() != null && entry.resistance().count() > 0) {
                drawHistoricRaySet(plot, currentCandles, entry.resistance(), entry.barIndex(), true);
            }
            if (showSupport && entry.support() != null && entry.support().count() > 0) {
                drawHistoricRaySet(plot, currentCandles, entry.support(), entry.barIndex(), false);
            }
        }
    }

    /**
     * Draw a historic ray set (from a past bar) in greyish color.
     * Only draws Ray 1 (most significant) to avoid visual clutter.
     */
    private void drawHistoricRaySet(XYPlot plot, List<Candle> candles, RaySet raySet, int atBar, boolean isResistance) {
        if (raySet.count() < 1) return;

        Ray ray = raySet.getRay(1);
        if (ray == null) return;

        Color color = isResistance ? HISTORIC_RESISTANCE_COLOR : HISTORIC_SUPPORT_COLOR;
        addHistoricRayLine(plot, candles, ray, atBar, color);
    }

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

        addAnchorMarker(plot, candles, raySet);

        for (int rayNum = 1; rayNum <= raySet.count(); rayNum++) {
            Ray ray = raySet.getRay(rayNum);
            if (ray == null) continue;

            boolean isBroken = isRayBrokenAtBar(raySet, rayNum, candles, currentBar);

            int colorIdx = Math.min(rayNum - 1, normalColors.length - 1);
            Color color = isBroken ? brokenColors[colorIdx] : normalColors[colorIdx];
            BasicStroke stroke = isBroken ? DASHED_STROKE : SOLID_STROKE;

            addRayLine(plot, candles, ray, color, stroke);
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
                g2.draw(new Ellipse2D.Double(x - size/2, y - size/2, size, size));
            }
        };
        plot.addAnnotation(marker);
        annotations.add(marker);
    }

    private void addRayLine(XYPlot plot, List<Candle> candles, Ray ray, Color color, BasicStroke stroke) {
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
                g2.fill(new Ellipse2D.Double(x - size/2, y - size/2, size, size));
            }
        };
        plot.addAnnotation(marker);
        annotations.add(marker);
    }

    public void clear() {
        if (priceChart == null) return;

        XYPlot plot = priceChart.getXYPlot();
        for (XYAnnotation annotation : annotations) {
            plot.removeAnnotation(annotation);
        }
        annotations.clear();
    }

    // ===== Listeners =====

    private class RayPageListener implements IndicatorPageListener<RaySet> {
        @Override
        public void onStateChanged(IndicatorPage<RaySet> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                redraw();
                if (onDataReady != null) {
                    onDataReady.run();
                }
            }
        }

        @Override
        public void onDataChanged(IndicatorPage<RaySet> page) {
            redraw();
            if (onDataReady != null) {
                onDataReady.run();
            }
        }
    }

    private class HistoricRayPageListener implements IndicatorPageListener<HistoricRays> {
        @Override
        public void onStateChanged(IndicatorPage<HistoricRays> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                redraw();
                if (onDataReady != null) {
                    onDataReady.run();
                }
            }
        }

        @Override
        public void onDataChanged(IndicatorPage<HistoricRays> page) {
            redraw();
            if (onDataReady != null) {
                onDataReady.run();
            }
        }
    }

    // ===== Legacy API (for compatibility during transition) =====

    /**
     * @deprecated Use requestData() + redraw() instead
     */
    @Deprecated
    public void update(List<Candle> candles, com.tradery.indicators.IndicatorEngine engine) {
        update(candles, engine, "BTCUSDT", "1h");
    }

    /**
     * Update ray overlay with symbol and timeframe context.
     */
    public void update(List<Candle> candles, com.tradery.indicators.IndicatorEngine engine,
                       String symbol, String timeframe) {
        if (candles == null || candles.isEmpty()) {
            clear();
            return;
        }
        // Request data with correct symbol/timeframe
        requestData(candles, symbol, timeframe,
            candles.get(0).timestamp(),
            candles.get(candles.size() - 1).timestamp());
        redraw();
    }
}
