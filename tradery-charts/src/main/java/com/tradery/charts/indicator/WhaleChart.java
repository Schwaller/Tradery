package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addSimpleZeroLine;
import static com.tradery.charts.util.RendererBuilder.colorCodedBarRendererNoMargin;

public class WhaleChart extends IndicatorChartBase {

    private double threshold = 50000;

    public WhaleChart() { super(IndicatorType.WHALE); }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataProvider().subscribeWhaleDelta(threshold);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] whaleDelta = ctx.indicatorDataProvider().getWhaleDelta(threshold);
        if (whaleDelta == null) return false;

        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Whale Delta");

        double maxDelta = 0;
        for (int i = 0; i < candles.size() && i < whaleDelta.length; i++) {
            if (!Double.isNaN(whaleDelta[i])) {
                series.add(candles.get(i).timestamp(), whaleDelta[i]);
                maxDelta = Math.max(maxDelta, Math.abs(whaleDelta[i]));
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, colorCodedBarRendererNoMargin(dataset, ChartStyles.WHALE_DELTA_POS, ChartStyles.WHALE_DELTA_NEG));

        double padding = maxDelta * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addSimpleZeroLine(plot, startTime, endTime);
    }

    @Override
    public String getTitle() {
        return String.format("Whale Delta ($%.0fK+)", threshold / 1000);
    }
}
