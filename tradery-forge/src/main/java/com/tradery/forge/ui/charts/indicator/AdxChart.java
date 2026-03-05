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

import static com.tradery.charts.util.ChartAnnotationHelper.addAdxLines;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class AdxChart extends IndicatorChart {

    private int period = 14;

    public AdxChart() { super(IndicatorType.ADX); }

    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }

    @Override public int minimumCandles() { return period * 2; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeADX(period);
        ctx.indicatorDataService().subscribePlusDI(period);
        ctx.indicatorDataService().subscribeMinusDI(period);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] adxValues = ctx.indicatorDataService().getADX(period);
        double[] plusDIValues = ctx.indicatorDataService().getPlusDI(period);
        double[] minusDIValues = ctx.indicatorDataService().getMinusDI(period);
        if (adxValues == null || plusDIValues == null || minusDIValues == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries adxSeries = new TimeSeries("ADX(" + period + ")");
        TimeSeries plusDISeries = new TimeSeries("+DI(" + period + ")");
        TimeSeries minusDISeries = new TimeSeries("-DI(" + period + ")");

        for (int i = period * 2 - 1; i < candles.size() && i < adxValues.length; i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            if (!Double.isNaN(adxValues[i])) adxSeries.addOrUpdate(ms, adxValues[i]);
            if (!Double.isNaN(plusDIValues[i])) plusDISeries.addOrUpdate(ms, plusDIValues[i]);
            if (!Double.isNaN(minusDIValues[i])) minusDISeries.addOrUpdate(ms, minusDIValues[i]);
        }

        dataset.addSeries(adxSeries);
        dataset.addSeries(plusDISeries);
        dataset.addSeries(minusDISeries);
        plot.setDataset(dataset);
        plot.setRenderer(lineRenderer(
            ChartStyles.ADX_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.DELTA_POSITIVE, ChartStyles.THIN_STROKE,
            ChartStyles.DELTA_NEGATIVE, ChartStyles.THIN_STROKE));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addAdxLines(plot, startTime, endTime);
    }
}
