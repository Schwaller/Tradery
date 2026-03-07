package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addHorizontalLine;

public class FearGreedChart extends IndicatorChartBase {

    public FearGreedChart() { super(IndicatorType.FEAR_GREED); }

    @Override
    public void subscribe(ChartDataContext ctx) {
        if (ctx.indicatorEngine() == null) return;

        var engine = ctx.indicatorEngine();
        Thread.startVirtualThread(() -> {
            double[] fearGreed = engine.getFearGreed();
            if (fearGreed == null || fearGreed.length == 0) return;
            SwingUtilities.invokeLater(() -> applyData(ctx, fearGreed));
        });
    }

    private void applyData(ChartDataContext ctx, double[] fearGreed) {
        List<Candle> candles = ctx.indicatorDataProvider().getCandles();
        if (candles == null || candles.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();

        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Fear & Greed");

        for (int i = 0; i < candles.size() && i < fearGreed.length; i++) {
            if (!Double.isNaN(fearGreed[i])) {
                series.add(candles.get(i).timestamp(), fearGreed[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);

        final XYSeriesCollection finalDataset = dataset;
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false) {
            @Override
            public Paint getItemPaint(int s, int item) {
                double value = finalDataset.getYValue(s, item);
                if (value < 25) return new Color(231, 76, 60);
                if (value < 50) return new Color(230, 126, 34);
                if (value <= 74) return new Color(142, 171, 59);
                return new Color(38, 166, 91);
            }
        };
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(0, renderer);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Fear & Greed Index");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addHorizontalLine(plot, 25, ChartStyles.DASHED_STROKE, new Color(231, 76, 60, 80), startTime, endTime);
            addHorizontalLine(plot, 50, ChartStyles.DASHED_STROKE, new Color(200, 200, 200, 80), startTime, endTime);
            addHorizontalLine(plot, 75, ChartStyles.DASHED_STROKE, new Color(38, 166, 91, 80), startTime, endTime);
        }
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        return false;
    }

    @Override
    public boolean managesOwnAnnotations() { return true; }
}
