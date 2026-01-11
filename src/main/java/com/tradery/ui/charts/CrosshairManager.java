package com.tradery.ui.charts;

import com.tradery.model.Candle;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;

import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages synchronized crosshairs across all charts.
 */
public class CrosshairManager {

    // Core chart crosshairs
    private Crosshair priceCrosshair;
    private Crosshair equityCrosshair;
    private Crosshair comparisonCrosshair;
    private Crosshair capitalUsageCrosshair;
    private Crosshair tradePLCrosshair;
    private Crosshair volumeCrosshair;

    // Indicator chart crosshairs
    private Crosshair rsiCrosshair;
    private Crosshair macdCrosshair;
    private Crosshair atrCrosshair;
    private Crosshair deltaCrosshair;
    private Crosshair fundingCrosshair;

    // Status update callback
    private Consumer<String> onStatusUpdate;

    // Data reference for status updates
    private List<Candle> currentCandles;

    // Date formatter
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm");

    // Reference to price chart panel for coordinate conversion
    private org.jfree.chart.ChartPanel priceChartPanel;

    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }

    public void setCurrentCandles(List<Candle> candles) {
        this.currentCandles = candles;
    }

    /**
     * Setup crosshairs for all core charts.
     */
    public void setupCoreChartCrosshairs(
            org.jfree.chart.ChartPanel pricePanel,
            org.jfree.chart.ChartPanel equityPanel,
            org.jfree.chart.ChartPanel comparisonPanel,
            org.jfree.chart.ChartPanel capitalUsagePanel,
            org.jfree.chart.ChartPanel tradePLPanel,
            org.jfree.chart.ChartPanel volumePanel) {

        this.priceChartPanel = pricePanel;

        priceCrosshair = createCrosshair();
        equityCrosshair = createCrosshair();
        comparisonCrosshair = createCrosshair();
        capitalUsageCrosshair = createCrosshair();
        tradePLCrosshair = createCrosshair();
        volumeCrosshair = createCrosshair();

        addCrosshairOverlay(pricePanel, priceCrosshair);
        addCrosshairOverlay(equityPanel, equityCrosshair);
        addCrosshairOverlay(comparisonPanel, comparisonCrosshair);
        addCrosshairOverlay(capitalUsagePanel, capitalUsageCrosshair);
        addCrosshairOverlay(tradePLPanel, tradePLCrosshair);
        addCrosshairOverlay(volumePanel, volumeCrosshair);

        ChartMouseListener listener = createMouseListener();
        pricePanel.addChartMouseListener(listener);
        equityPanel.addChartMouseListener(listener);
        comparisonPanel.addChartMouseListener(listener);
        capitalUsagePanel.addChartMouseListener(listener);
        tradePLPanel.addChartMouseListener(listener);
        volumePanel.addChartMouseListener(listener);
    }

    /**
     * Setup crosshairs for indicator charts.
     */
    public void setupIndicatorChartCrosshairs(
            org.jfree.chart.ChartPanel rsiPanel,
            org.jfree.chart.ChartPanel macdPanel,
            org.jfree.chart.ChartPanel atrPanel,
            org.jfree.chart.ChartPanel deltaPanel,
            org.jfree.chart.ChartPanel fundingPanel) {

        rsiCrosshair = createCrosshair();
        macdCrosshair = createCrosshair();
        atrCrosshair = createCrosshair();
        deltaCrosshair = createCrosshair();
        fundingCrosshair = createCrosshair();

        addCrosshairOverlay(rsiPanel, rsiCrosshair);
        addCrosshairOverlay(macdPanel, macdCrosshair);
        addCrosshairOverlay(atrPanel, atrCrosshair);
        if (deltaPanel != null) addCrosshairOverlay(deltaPanel, deltaCrosshair);
        if (fundingPanel != null) addCrosshairOverlay(fundingPanel, fundingCrosshair);

        ChartMouseListener listener = createMouseListener();
        rsiPanel.addChartMouseListener(listener);
        macdPanel.addChartMouseListener(listener);
        atrPanel.addChartMouseListener(listener);
        if (deltaPanel != null) deltaPanel.addChartMouseListener(listener);
        if (fundingPanel != null) fundingPanel.addChartMouseListener(listener);
    }

    private Crosshair createCrosshair() {
        Crosshair crosshair = new Crosshair(Double.NaN);
        crosshair.setPaint(ChartStyles.CROSSHAIR_COLOR);
        return crosshair;
    }

    private void addCrosshairOverlay(org.jfree.chart.ChartPanel panel, Crosshair crosshair) {
        CrosshairOverlay overlay = new CrosshairOverlay();
        overlay.addDomainCrosshair(crosshair);
        panel.addOverlay(overlay);
    }

    private ChartMouseListener createMouseListener() {
        return new ChartMouseListener() {
            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                updateCrosshairs(event);
            }

            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
            }
        };
    }

    private void updateCrosshairs(ChartMouseEvent event) {
        JFreeChart chart = event.getChart();
        XYPlot plot = chart.getXYPlot();
        Rectangle2D dataArea = priceChartPanel.getScreenDataArea();

        if (dataArea == null) return;

        double x = event.getTrigger().getX();

        // Convert to data coordinates
        ValueAxis domainAxis = plot.getDomainAxis();
        double domainValue = domainAxis.java2DToValue(x, dataArea, plot.getDomainAxisEdge());

        // Update all crosshairs
        updateAllCrosshairs(domainValue);

        // Update status bar
        updateStatus(domainValue);
    }

    private void updateAllCrosshairs(double domainValue) {
        // Core crosshairs
        if (priceCrosshair != null) priceCrosshair.setValue(domainValue);
        if (equityCrosshair != null) equityCrosshair.setValue(domainValue);
        if (comparisonCrosshair != null) comparisonCrosshair.setValue(domainValue);
        if (capitalUsageCrosshair != null) capitalUsageCrosshair.setValue(domainValue);
        if (tradePLCrosshair != null) tradePLCrosshair.setValue(domainValue);
        if (volumeCrosshair != null) volumeCrosshair.setValue(domainValue);

        // Indicator crosshairs
        if (rsiCrosshair != null) rsiCrosshair.setValue(domainValue);
        if (macdCrosshair != null) macdCrosshair.setValue(domainValue);
        if (atrCrosshair != null) atrCrosshair.setValue(domainValue);
        if (deltaCrosshair != null) deltaCrosshair.setValue(domainValue);
        if (fundingCrosshair != null) fundingCrosshair.setValue(domainValue);
    }

    private void updateStatus(double timestamp) {
        if (onStatusUpdate == null || currentCandles == null || currentCandles.isEmpty()) return;

        // Find the candle closest to this timestamp
        long ts = (long) timestamp;
        Candle closest = null;
        long minDiff = Long.MAX_VALUE;

        for (Candle c : currentCandles) {
            long diff = Math.abs(c.timestamp() - ts);
            if (diff < minDiff) {
                minDiff = diff;
                closest = c;
            }
        }

        if (closest != null) {
            String status = String.format("%s  O: %.2f  H: %.2f  L: %.2f  C: %.2f  Vol: %.0f",
                dateFormat.format(new Date(closest.timestamp())),
                closest.open(), closest.high(), closest.low(), closest.close(), closest.volume());
            onStatusUpdate.accept(status);
        }
    }

    /**
     * Synchronize domain axes across all charts.
     * Sets up the price chart as master, with others following.
     */
    public void syncDomainAxes(JFreeChart priceChart, JFreeChart[] otherCharts) {
        DateAxis masterAxis = (DateAxis) priceChart.getXYPlot().getDomainAxis();

        masterAxis.addChangeListener(event -> {
            if (masterAxis.getRange() == null) return;

            double lower = masterAxis.getLowerBound();
            double upper = masterAxis.getUpperBound();

            for (JFreeChart chart : otherCharts) {
                if (chart == null) continue;
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                if (axis.getLowerBound() != lower || axis.getUpperBound() != upper) {
                    axis.setRange(lower, upper);
                }
            }
        });
    }
}
