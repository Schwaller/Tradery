package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;
import static com.tradery.charts.util.RendererBuilder.colorCodedBarRenderer;

public class DeltaChart extends IndicatorChartBase {

    public DeltaChart() { super(IndicatorType.DELTA); }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataProvider().subscribeDelta();
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] delta = ctx.indicatorDataProvider().getDelta();
        if (delta == null) return false;

        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Delta");

        for (int i = 0; i < candles.size() && i < delta.length; i++) {
            if (!Double.isNaN(delta[i])) {
                series.add(candles.get(i).timestamp(), delta[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, colorCodedBarRenderer(dataset, ChartStyles.DELTA_POSITIVE, ChartStyles.DELTA_NEGATIVE));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addZeroLine(plot, startTime, endTime);
    }
}
