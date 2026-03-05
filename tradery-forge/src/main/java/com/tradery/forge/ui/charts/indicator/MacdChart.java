package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.ChartStyles;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.Date;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;
import static com.tradery.charts.util.RendererBuilder.colorCodedBarRenderer;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class MacdChart extends IndicatorChart {

    private int fast = 12;
    private int slow = 26;
    private int signal = 9;

    public MacdChart() { super(IndicatorType.MACD); }

    public int getFast() { return fast; }
    public void setFast(int fast) { this.fast = fast; }
    public int getSlow() { return slow; }
    public void setSlow(int slow) { this.slow = slow; }
    public int getSignal() { return signal; }
    public void setSignal(int signal) { this.signal = signal; }

    @Override public int minimumCandles() { return slow + signal; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeMACD(fast, slow, signal);
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        var macdResult = ctx.indicatorDataService().getMACD(fast, slow, signal);
        if (macdResult == null) return false;

        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        TimeSeries macdLine = new TimeSeries("MACD");
        TimeSeries signalLine = new TimeSeries("Signal");

        double[] macdValues = macdResult.line();
        double[] signalValues = macdResult.signal();
        double[] histogramValues = macdResult.histogram();

        XYSeriesCollection histogramDataset = new XYSeriesCollection();
        XYSeries histogramSeries = new XYSeries("Histogram");

        for (int i = slow + signal - 1; i < candles.size() && i < macdValues.length; i++) {
            if (!Double.isNaN(macdValues[i]) && !Double.isNaN(signalValues[i])) {
                Candle c = candles.get(i);
                Millisecond ms = new Millisecond(new Date(c.timestamp()));
                macdLine.addOrUpdate(ms, macdValues[i]);
                signalLine.addOrUpdate(ms, signalValues[i]);
                histogramSeries.add(c.timestamp(), histogramValues[i]);
            }
        }

        lineDataset.addSeries(macdLine);
        lineDataset.addSeries(signalLine);
        histogramDataset.addSeries(histogramSeries);

        plot.setDataset(0, histogramDataset);
        plot.setDataset(1, lineDataset);
        plot.setRenderer(0, colorCodedBarRenderer(histogramDataset, ChartStyles.MACD_HIST_POS, ChartStyles.MACD_HIST_NEG));
        plot.setRenderer(1, lineRenderer(
            ChartStyles.MACD_LINE_COLOR, ChartStyles.MEDIUM_STROKE,
            ChartStyles.MACD_SIGNAL_COLOR, ChartStyles.MEDIUM_STROKE));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addZeroLine(plot, startTime, endTime);
    }
}
