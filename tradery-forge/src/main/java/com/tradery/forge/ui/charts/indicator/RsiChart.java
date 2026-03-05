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

import static com.tradery.charts.util.ChartAnnotationHelper.addRsiLines;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class RsiChart extends IndicatorChart {

    private int period = 14;

    public RsiChart() { super(IndicatorType.RSI); }

    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }

    @Override public int minimumCandles() { return period + 1; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeRSI(period);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] rsiValues = ctx.indicatorDataService().getRSI(period);
        if (rsiValues == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = new TimeSeries("RSI(" + period + ")");

        for (int i = period; i < candles.size() && i < rsiValues.length; i++) {
            if (!Double.isNaN(rsiValues[i])) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), rsiValues[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(dataset);
        plot.setRenderer(lineRenderer(ChartStyles.RSI_COLOR));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addRsiLines(plot, startTime, endTime);
    }
}
