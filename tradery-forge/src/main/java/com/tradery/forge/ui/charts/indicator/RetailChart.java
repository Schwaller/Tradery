package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addSimpleZeroLine;
import static com.tradery.charts.util.RendererBuilder.colorCodedBarRendererNoMargin;

public class RetailChart extends IndicatorChart {

    private double threshold = 50000;

    public RetailChart() { super(IndicatorType.RETAIL); }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeRetailDelta(threshold);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] retailDelta = ctx.indicatorDataService().getRetailDelta(threshold);
        if (retailDelta == null) return false;

        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Retail Delta");

        double maxDelta = 0;
        for (int i = 0; i < candles.size() && i < retailDelta.length; i++) {
            if (!Double.isNaN(retailDelta[i])) {
                series.add(candles.get(i).timestamp(), retailDelta[i]);
                maxDelta = Math.max(maxDelta, Math.abs(retailDelta[i]));
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, colorCodedBarRendererNoMargin(dataset, new Color(52, 152, 219), new Color(231, 76, 60)));

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
        return String.format("Retail Delta (<$%.0fK)", threshold / 1000);
    }
}
