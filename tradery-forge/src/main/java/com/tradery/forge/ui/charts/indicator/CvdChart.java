package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.ChartStyles;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.Date;
import java.util.List;

import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class CvdChart extends IndicatorChart {

    public CvdChart() { super(IndicatorType.CVD); }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeCumDelta();
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] cumDelta = ctx.indicatorDataService().getCumDelta();
        if (cumDelta == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = new TimeSeries("CVD");

        for (int i = 0; i < candles.size() && i < cumDelta.length; i++) {
            if (!Double.isNaN(cumDelta[i])) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), cumDelta[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, lineRenderer(ChartStyles.CVD_COLOR));
        return true;
    }

    @Override
    public String getTitle() { return "CVD (Cumulative Delta)"; }
}
