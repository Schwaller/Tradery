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

import static com.tradery.charts.util.ChartAnnotationHelper.addStochasticLines;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class StochasticChart extends IndicatorChart {

    private int kPeriod = 14;
    private int dPeriod = 3;

    public StochasticChart() { super(IndicatorType.STOCHASTIC); }

    public int getKPeriod() { return kPeriod; }
    public void setKPeriod(int kPeriod) { this.kPeriod = kPeriod; }
    public int getDPeriod() { return dPeriod; }
    public void setDPeriod(int dPeriod) { this.dPeriod = dPeriod; }

    @Override public int minimumCandles() { return kPeriod + dPeriod; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeStochastic(kPeriod, dPeriod);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        var result = ctx.indicatorDataService().getStochastic(kPeriod, dPeriod);
        if (result == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries kSeries = new TimeSeries("%K(" + kPeriod + ")");
        TimeSeries dSeries = new TimeSeries("%D(" + dPeriod + ")");

        double[] kValues = result.k();
        double[] dValues = result.d();

        for (int i = kPeriod - 1; i < candles.size() && i < kValues.length; i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            if (!Double.isNaN(kValues[i])) kSeries.addOrUpdate(ms, kValues[i]);
            if (!Double.isNaN(dValues[i])) dSeries.addOrUpdate(ms, dValues[i]);
        }

        dataset.addSeries(kSeries);
        dataset.addSeries(dSeries);
        plot.setDataset(dataset);
        plot.setRenderer(lineRenderer(
            ChartStyles.STOCHASTIC_K_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.STOCHASTIC_D_COLOR, ChartStyles.MEDIUM_STROKE));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addStochasticLines(plot, startTime, endTime);
    }
}
