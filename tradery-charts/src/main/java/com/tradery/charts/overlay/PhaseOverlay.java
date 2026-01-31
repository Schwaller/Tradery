package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.core.model.Candle;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a phase as semi-transparent colored background bands on the price chart.
 * Each band spans the full Y-axis during periods where the phase is active.
 * Annotation-based (no datasets).
 */
public class PhaseOverlay implements ChartOverlay {

    private final String displayName;
    private final boolean[] activeState;
    private final List<Candle> candles;
    private final Color color;

    private final List<XYAnnotation> annotations = new ArrayList<>();
    private XYPlot currentPlot;

    public PhaseOverlay(String displayName, boolean[] activeState, List<Candle> candles, Color color) {
        this.displayName = displayName;
        this.activeState = activeState;
        this.candles = candles;
        this.color = color;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        this.currentPlot = plot;
        clear();
        render();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public int getDatasetCount() {
        return 0; // Annotation-based
    }

    @Override
    public void close() {
        clear();
    }

    private void render() {
        if (currentPlot == null || candles == null || candles.isEmpty() || activeState == null) return;

        int len = Math.min(activeState.length, candles.size());
        if (len == 0) return;

        // Estimate bar width from candle intervals
        long barWidth = len > 1
            ? candles.get(1).timestamp() - candles.get(0).timestamp()
            : 3_600_000L;

        // Find contiguous active ranges and add box annotations
        int rangeStart = -1;
        for (int i = 0; i < len; i++) {
            if (activeState[i]) {
                if (rangeStart < 0) rangeStart = i;
            } else {
                if (rangeStart >= 0) {
                    addBox(rangeStart, i - 1, barWidth);
                    rangeStart = -1;
                }
            }
        }
        // Close final range
        if (rangeStart >= 0) {
            addBox(rangeStart, len - 1, barWidth);
        }

        if (currentPlot.getChart() != null) {
            currentPlot.getChart().fireChartChanged();
        }
    }

    private void addBox(int startIdx, int endIdx, long barWidth) {
        double x0 = candles.get(startIdx).timestamp() - barWidth / 2.0;
        double x1 = candles.get(endIdx).timestamp() + barWidth / 2.0;

        AbstractXYAnnotation box = new AbstractXYAnnotation() {
            @Override
            public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                           ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                           PlotRenderingInfo info) {
                double px0 = domainAxis.valueToJava2D(x0, dataArea, plot.getDomainAxisEdge());
                double px1 = domainAxis.valueToJava2D(x1, dataArea, plot.getDomainAxisEdge());
                double left = Math.max(Math.min(px0, px1), dataArea.getMinX());
                double right = Math.min(Math.max(px0, px1), dataArea.getMaxX());
                if (right <= left) return;

                g2.setColor(color);
                g2.fillRect((int) left, (int) dataArea.getMinY(),
                    (int) (right - left), (int) dataArea.getHeight());
            }
        };
        currentPlot.addAnnotation(box);
        annotations.add(box);
    }

    private void clear() {
        if (currentPlot != null) {
            for (XYAnnotation ann : annotations) {
                currentPlot.removeAnnotation(ann);
            }
        }
        annotations.clear();
    }
}
