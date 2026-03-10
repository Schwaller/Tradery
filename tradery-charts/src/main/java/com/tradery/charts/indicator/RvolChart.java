package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.indicators.RvolComputer;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addHorizontalLine;

/**
 * Chart indicator showing Relative Volume (RVOL) as bars with a 1.0 baseline.
 * Bars are colored by intensity: muted when normal, highlighted when extreme.
 */
public class RvolChart extends IndicatorChartBase {

    private int lookbackWeeks = 52;
    private int mode = RvolComputer.MODE_ANY;
    private int smooth = 1;

    public RvolChart() { super(IndicatorType.RVOL); }

    public void setLookbackWeeks(int lookbackWeeks) { this.lookbackWeeks = lookbackWeeks; }
    public void setMode(int mode) { this.mode = mode; }
    public void setSmooth(int smooth) { this.smooth = smooth; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        if (ctx.indicatorEngine() == null) return;

        var engine = ctx.indicatorEngine();
        Thread.startVirtualThread(() -> {
            RvolComputer.RvolResult result = engine.getRvol(lookbackWeeks, mode, smooth);
            if (result == null || result.ratio().length == 0) return;
            SwingUtilities.invokeLater(() -> applyData(ctx, result.ratio()));
        });
    }

    private void applyData(ChartDataContext ctx, double[] rvol) {
        List<Candle> candles = ctx.indicatorDataProvider().getCandles();
        if (candles == null || candles.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();

        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("RVOL");

        for (int i = 0; i < candles.size() && i < rvol.length; i++) {
            if (!Double.isNaN(rvol[i])) {
                series.add(candles.get(i).timestamp(), rvol[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);

        final XYSeriesCollection finalDataset = dataset;
        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public Paint getItemPaint(int s, int item) {
                double value = finalDataset.getYValue(s, item);
                if (value >= 2.0) return new Color(231, 76, 60);       // Very high: red
                if (value >= 1.5) return new Color(230, 126, 34);      // High: orange
                if (value >= 0.75) return new Color(142, 171, 59);     // Normal: green
                if (value >= 0.5) return new Color(100, 120, 140);     // Low: muted blue
                return new Color(80, 80, 80);                           // Very low: gray
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        plot.setRenderer(0, renderer);

        // Annotations
        plot.clearAnnotations();
        String modeLabel = switch (mode) {
            case RvolComputer.MODE_DOW -> " (DOW)";
            case RvolComputer.MODE_DAYTYPE -> " (Day Type)";
            default -> "";
        };
        String smoothLabel = smooth > 1 ? ", smooth " + smooth : "";
        ChartStyles.addChartTitleAnnotation(plot,
            "RVOL " + lookbackWeeks + "w" + modeLabel + smoothLabel);

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            // Reference line at 1.0 (normal volume)
            addHorizontalLine(plot, 1.0, ChartStyles.DASHED_STROKE, new Color(200, 200, 200, 120), startTime, endTime);
            // High volume threshold at 2.0
            addHorizontalLine(plot, 2.0, ChartStyles.DASHED_STROKE, new Color(231, 76, 60, 60), startTime, endTime);
        }
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        return false; // subscribe handles rendering
    }

    @Override
    public boolean managesOwnAnnotations() { return true; }
}
