package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.ChartComponent;
import com.tradery.forge.ui.charts.ChartStyles;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.util.List;

/**
 * Abstract base class for all indicator charts.
 * Each subclass implements rendering logic for a specific indicator type.
 */
public abstract class IndicatorChart {

    protected final IndicatorType type;
    protected final ChartComponent component;

    protected IndicatorChart(IndicatorType type) {
        this.type = type;
        this.component = new ChartComponent(type.getTitle(), type.getYAxisRange());
    }

    public IndicatorType getType() { return type; }
    public ChartComponent getComponent() { return component; }
    public JFreeChart getChart() { return component.getChart(); }
    public org.jfree.chart.ChartPanel getChartPanel() { return component.getChartPanel(); }
    public JPanel getChartWrapper() { return component.getWrapper(); }
    public JButton getZoomButton() { return component.getZoomButton(); }

    /**
     * Subscribe to required data sources. Called when data context changes.
     */
    public abstract void subscribe(ChartDataContext ctx);

    /**
     * Render indicator data onto the chart plot.
     * @return true if data was available and rendering succeeded
     */
    protected abstract boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx);

    /**
     * Add reference lines (RSI 30/70, zero lines, etc.).
     * Override in subclasses that need reference lines.
     */
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        // Default: no reference lines
    }

    /**
     * Minimum number of candles needed before rendering.
     */
    public int minimumCandles() {
        return 1;
    }

    /**
     * Whether this chart requires orderflow (aggTrades) data.
     */
    public boolean requiresOrderflow() {
        return false;
    }

    /**
     * Get chart title. Override for dynamic titles (e.g., Whale with threshold).
     */
    public String getTitle() {
        return type.getTitle();
    }

    /**
     * Whether this chart manages its own annotations (skips framework title/annotation step).
     */
    public boolean managesOwnAnnotations() {
        return false;
    }

    /**
     * Release external resources (data pages, etc.). Override in subclasses that hold resources.
     */
    public void dispose() {
        // Default: nothing to release
    }

    /**
     * Template method: guard checks → render → annotations.
     * Called by the framework when data is ready.
     */
    public final void redraw(List<Candle> candles, ChartDataContext ctx) {
        if (candles == null || candles.size() < minimumCandles()) {
            return;
        }

        XYPlot plot = component.getChart().getXYPlot();
        boolean rendered = render(plot, candles, ctx);

        if (rendered && !managesOwnAnnotations()) {
            plot.clearAnnotations();
            ChartStyles.addChartTitleAnnotation(plot, getTitle());

            if (!candles.isEmpty()) {
                long startTime = candles.get(0).timestamp();
                long endTime = candles.get(candles.size() - 1).timestamp();
                addReferenceLines(plot, startTime, endTime);
            }
        }
    }

    /**
     * Re-stylize chart when theme changes.
     */
    public void refreshTheme() {
        ChartStyles.stylizeChart(component.getChart(), getTitle());
    }
}
