package com.tradery.ui.charts;

import com.tradery.model.Candle;
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
 * Manages synchronized crosshairs across all charts.
 */
public class CrosshairManager {

    // All crosshairs in a single list for easy iteration
    private final List<Crosshair> crosshairs = new ArrayList<>();

    // Status update callback
    private Consumer<String> onStatusUpdate;

    // Data reference for status updates
    private List<Candle> currentCandles;

    // Date formatter
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm");

    // Reference to price chart panel for coordinate conversion
    private ChartPanel priceChartPanel;

    // Shared mouse listener
    private final ChartMouseListener mouseListener = new ChartMouseListener() {
        @Override
        public void chartMouseMoved(ChartMouseEvent event) {
            updateCrosshairs(event);
        }

        @Override
        public void chartMouseClicked(ChartMouseEvent event) {
        }
    };

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
            ChartPanel pricePanel,
            ChartPanel equityPanel,
            ChartPanel comparisonPanel,
            ChartPanel capitalUsagePanel,
            ChartPanel tradePLPanel,
            ChartPanel volumePanel) {

        this.priceChartPanel = pricePanel;

        setupCrosshair(pricePanel);
        setupCrosshair(equityPanel);
        setupCrosshair(comparisonPanel);
        setupCrosshair(capitalUsagePanel);
        setupCrosshair(tradePLPanel);
        setupCrosshair(volumePanel);
    }

    /**
     * Setup crosshairs for indicator charts.
     */
    public void setupIndicatorChartCrosshairs(
            ChartPanel rsiPanel,
            ChartPanel macdPanel,
            ChartPanel atrPanel,
            ChartPanel deltaPanel,
            ChartPanel cvdPanel,
            ChartPanel volumeRatioPanel,
            ChartPanel whalePanel,
            ChartPanel retailPanel,
            ChartPanel fundingPanel,
            ChartPanel oiPanel,
            ChartPanel stochasticPanel,
            ChartPanel rangePositionPanel,
            ChartPanel adxPanel,
            ChartPanel tradeCountPanel,
            ChartPanel premiumPanel) {

        setupCrosshair(rsiPanel);
        setupCrosshair(macdPanel);
        setupCrosshair(atrPanel);
        setupCrosshair(deltaPanel);
        setupCrosshair(cvdPanel);
        setupCrosshair(volumeRatioPanel);
        setupCrosshair(whalePanel);
        setupCrosshair(retailPanel);
        setupCrosshair(fundingPanel);
        setupCrosshair(oiPanel);
        setupCrosshair(stochasticPanel);
        setupCrosshair(rangePositionPanel);
        setupCrosshair(adxPanel);
        setupCrosshair(tradeCountPanel);
        setupCrosshair(premiumPanel);
    }

    /**
     * Setup a crosshair for a single chart panel.
     */
    private void setupCrosshair(ChartPanel panel) {
        if (panel == null) return;

        Crosshair crosshair = new Crosshair(Double.NaN);
        crosshair.setPaint(ChartStyles.CROSSHAIR_COLOR);
        crosshairs.add(crosshair);

        CrosshairOverlay overlay = new CrosshairOverlay();
        overlay.addDomainCrosshair(crosshair);
        panel.addOverlay(overlay);
        panel.addChartMouseListener(mouseListener);
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
