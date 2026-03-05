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

import static com.tradery.charts.util.ChartAnnotationHelper.addRangePositionLines;
import static com.tradery.charts.util.RendererBuilder.colorCodedLineRenderer;

public class RangePositionChart extends IndicatorChart {

    private int period = 200;
    private int skip = 0;

    public RangePositionChart() { super(IndicatorType.RANGE_POSITION); }

    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }
    public int getSkip() { return skip; }
    public void setSkip(int skip) { this.skip = skip; }

    @Override public int minimumCandles() { return period + skip + 1; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeRangePosition(period, skip);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] values = ctx.indicatorDataService().getRangePosition(period, skip);
        if (values == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        String label = skip > 0
            ? "RANGE_POSITION(" + period + "," + skip + ")"
            : "RANGE_POSITION(" + period + ")";
        TimeSeries series = new TimeSeries(label);

        for (int i = period + skip; i < candles.size() && i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), values[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(dataset);
        plot.setRenderer(colorCodedLineRenderer(dataset,
            ChartStyles.DELTA_POSITIVE, ChartStyles.DELTA_NEGATIVE, ChartStyles.ATR_COLOR, 1.0));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addRangePositionLines(plot, startTime, endTime);
    }
}
