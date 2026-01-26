package com.tradery.charts.core;

import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.panel.CrosshairOverlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.XYPlot;

import java.awt.geom.Rectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates synchronized behavior across multiple charts.
 * Handles crosshair sync and domain axis sync in a unified way.
 *
 * <p>This replaces the dual sync mechanisms (CrosshairManager and
 * ChartInteractionManager) with a single coordinator.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChartCoordinator coordinator = new ChartCoordinator();
 *
 * // Register charts - auto-registers for sync
 * coordinator.register(priceChartPanel);
 * coordinator.register(volumeChartPanel);
 * coordinator.register(rsiChartPanel);
 *
 * // Optionally set status callback
 * coordinator.setOnStatusUpdate(status -> statusLabel.setText(status));
 *
 * // Set candle data for OHLCV status display
 * coordinator.setCandles(candles);
 * }</pre>
 */
public class ChartCoordinator {

    private final List<ChartPanel> registeredPanels = new ArrayList<>();
    private final List<Crosshair> crosshairs = new ArrayList<>();

    private ChartPanel masterPanel;
    private Consumer<String> onStatusUpdate;
    private List<Candle> currentCandles;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm");

    private final ChartMouseListener mouseListener = new ChartMouseListener() {
        @Override
        public void chartMouseMoved(ChartMouseEvent event) {
            updateCrosshairs(event);
        }

        @Override
        public void chartMouseClicked(ChartMouseEvent event) {
            // Could add click handling here
        }
    };

    /**
     * Register a chart panel for synchronized crosshairs.
     * The first registered panel becomes the master for coordinate conversion.
     */
    public void register(ChartPanel panel) {
        if (panel == null) return;

        // First panel is the master
        if (masterPanel == null) {
            masterPanel = panel;
        }

        registeredPanels.add(panel);

        // Setup crosshair
        Crosshair crosshair = new Crosshair(Double.NaN);
        crosshair.setPaint(ChartStyles.crosshairColor());
        crosshairs.add(crosshair);

        CrosshairOverlay overlay = new CrosshairOverlay();
        overlay.addDomainCrosshair(crosshair);
        panel.addOverlay(overlay);
        panel.addChartMouseListener(mouseListener);
    }

    /**
     * Unregister a chart panel.
     */
    public void unregister(ChartPanel panel) {
        int index = registeredPanels.indexOf(panel);
        if (index >= 0) {
            registeredPanels.remove(index);
            crosshairs.remove(index);
            panel.removeChartMouseListener(mouseListener);

            if (panel == masterPanel && !registeredPanels.isEmpty()) {
                masterPanel = registeredPanels.get(0);
            }
        }
    }

    /**
     * Set the status update callback.
     * Called with OHLCV information when crosshair moves.
     */
    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }

    /**
     * Set the candle data for status display.
     */
    public void setCandles(List<Candle> candles) {
        this.currentCandles = candles;
    }

    /**
     * Set the crosshair position manually.
     * Useful for programmatic synchronization.
     */
    public void setCrosshairPosition(double timestamp) {
        for (Crosshair crosshair : crosshairs) {
            crosshair.setValue(timestamp);
        }
    }

    /**
     * Synchronize domain axes across all charts.
     * When the master chart's domain changes, others follow.
     */
    public void syncDomainAxes() {
        if (masterPanel == null || registeredPanels.size() < 2) return;

        JFreeChart masterChart = masterPanel.getChart();
        if (masterChart == null) return;

        DateAxis masterAxis = (DateAxis) masterChart.getXYPlot().getDomainAxis();

        masterAxis.addChangeListener(event -> {
            if (masterAxis.getRange() == null) return;

            double lower = masterAxis.getLowerBound();
            double upper = masterAxis.getUpperBound();

            for (ChartPanel panel : registeredPanels) {
                if (panel == masterPanel || panel.getChart() == null) continue;

                XYPlot plot = panel.getChart().getXYPlot();
                if (plot.getDomainAxis() instanceof DateAxis axis) {
                    if (axis.getLowerBound() != lower || axis.getUpperBound() != upper) {
                        axis.setRange(lower, upper);
                    }
                }
            }
        });
    }

    /**
     * Set the domain range for all charts.
     */
    public void setDomainRange(double min, double max) {
        for (ChartPanel panel : registeredPanels) {
            if (panel.getChart() == null) continue;

            XYPlot plot = panel.getChart().getXYPlot();
            if (plot.getDomainAxis() instanceof DateAxis axis) {
                axis.setRange(min, max);
            }
        }
    }

    private void updateCrosshairs(ChartMouseEvent event) {
        if (masterPanel == null) return;

        Rectangle2D dataArea = masterPanel.getScreenDataArea();
        if (dataArea == null) return;

        JFreeChart chart = event.getChart();
        XYPlot plot = chart.getXYPlot();

        double x = event.getTrigger().getX();

        // Convert to data coordinates using the master panel's data area
        ValueAxis domainAxis = plot.getDomainAxis();
        double domainValue = domainAxis.java2DToValue(x, dataArea, plot.getDomainAxisEdge());

        // Update all crosshairs
        for (Crosshair crosshair : crosshairs) {
            crosshair.setValue(domainValue);
        }

        // Update status bar
        updateStatus(domainValue);
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
     * Get all registered chart panels.
     */
    public List<ChartPanel> getRegisteredPanels() {
        return new ArrayList<>(registeredPanels);
    }

    /**
     * Get the master panel used for coordinate conversion.
     */
    public ChartPanel getMasterPanel() {
        return masterPanel;
    }
}
