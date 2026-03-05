package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.ChartStyles;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;

/**
 * Open Interest chart uses IndicatorEngine (async via virtual thread).
 * Dual-axis: OI change bars + OI line.
 */
public class OiChart extends IndicatorChart {

    public OiChart() { super(IndicatorType.OI); }

    @Override
    public void subscribe(ChartDataContext ctx) {
        if (ctx.indicatorEngine() == null) return;

        var engine = ctx.indicatorEngine();
        Thread.startVirtualThread(() -> {
            double[] oi = engine.getOI();
            double[] oiChange = engine.getOIChange();
            SwingUtilities.invokeLater(() -> applyData(ctx, oi, oiChange));
        });
    }

    private void applyData(ChartDataContext ctx, double[] oi, double[] oiChange) {
        List<Candle> candles = ctx.indicatorDataService().getCandles();
        if (candles == null || candles.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();

        TimeSeriesCollection oiLineDataset = new TimeSeriesCollection();
        TimeSeries oiSeries = new TimeSeries("Open Interest");

        XYSeriesCollection oiChangeDataset = new XYSeriesCollection();
        XYSeries oiChangeSeries = new XYSeries("OI Change");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (i < oi.length && !Double.isNaN(oi[i])) {
                oiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), oi[i]);
            }
            if (i < oiChange.length && !Double.isNaN(oiChange[i])) {
                oiChangeSeries.add(c.timestamp(), oiChange[i]);
            }
        }

        oiLineDataset.addSeries(oiSeries);
        oiChangeDataset.addSeries(oiChangeSeries);

        plot.setDataset(0, oiChangeDataset);
        plot.setDataset(1, oiLineDataset);

        // OI change bar renderer
        final XYSeriesCollection finalOiChangeDataset = oiChangeDataset;
        XYBarRenderer changeRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = finalOiChangeDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.OI_POSITIVE : ChartStyles.OI_NEGATIVE;
            }
        };
        changeRenderer.setShadowVisible(false);
        changeRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, changeRenderer);

        // OI line renderer
        XYLineAndShapeRenderer oiLineRenderer = new XYLineAndShapeRenderer(true, false);
        oiLineRenderer.setSeriesPaint(0, ChartStyles.OI_LINE_COLOR);
        oiLineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, oiLineRenderer);

        // Primary Y-axis for OI change bars (centered around zero, hidden)
        NumberAxis changeAxis = (NumberAxis) plot.getRangeAxis();
        changeAxis.setAutoRangeIncludesZero(true);
        changeAxis.setAutoRange(true);
        changeAxis.setVisible(false);

        // Secondary Y-axis for OI line
        NumberAxis oiAxis = new NumberAxis();
        oiAxis.setAutoRangeIncludesZero(false);
        oiAxis.setAutoRange(true);
        oiAxis.setLabelPaint(ChartStyles.TEXT_COLOR());
        oiAxis.setTickLabelPaint(ChartStyles.TEXT_COLOR());
        oiAxis.setFixedDimension(60);
        oiAxis.setAxisLineVisible(false);
        oiAxis.setTickMarksVisible(false);
        plot.setRangeAxis(1, oiAxis);
        plot.setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_LEFT);
        plot.mapDatasetToRangeAxis(1, 1);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Open Interest (B)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        // Rendering handled in subscribe() via async callback
        return false;
    }

    @Override
    public boolean managesOwnAnnotations() { return true; }
}
