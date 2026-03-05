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

public class AtrChart extends IndicatorChart {

    private int period = 14;

    public AtrChart() { super(IndicatorType.ATR); }

    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }

    @Override public int minimumCandles() { return period + 1; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeATR(period);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] atrValues = ctx.indicatorDataService().getATR(period);
        if (atrValues == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = new TimeSeries("ATR(" + period + ")");

        for (int i = period; i < candles.size() && i < atrValues.length; i++) {
            if (!Double.isNaN(atrValues[i])) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), atrValues[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(dataset);
        plot.setRenderer(lineRenderer(ChartStyles.ATR_COLOR));
        return true;
    }
}
