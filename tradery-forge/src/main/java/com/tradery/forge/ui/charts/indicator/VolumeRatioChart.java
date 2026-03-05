package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.charts.IndicatorType;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addSimpleZeroLine;
import static com.tradery.charts.util.RendererBuilder.barRenderer;

public class VolumeRatioChart extends IndicatorChart {

    public VolumeRatioChart() { super(IndicatorType.VOLUME_RATIO); }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeBuyVolume();
        ctx.indicatorDataService().subscribeSellVolume();
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] buyVolume = ctx.indicatorDataService().getBuyVolume();
        double[] sellVolume = ctx.indicatorDataService().getSellVolume();
        if (buyVolume == null || sellVolume == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries buySeries = new TimeSeries("Buy");
        TimeSeries sellSeries = new TimeSeries("Sell");

        double maxVolume = 0;
        for (int i = 0; i < candles.size() && i < buyVolume.length && i < sellVolume.length; i++) {
            Candle c = candles.get(i);
            double buy = buyVolume[i];
            double sell = sellVolume[i];
            if (!Double.isNaN(buy) && !Double.isNaN(sell)) {
                maxVolume = Math.max(maxVolume, Math.max(buy, sell));
                buySeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), buy);
                sellSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), -sell);
            }
        }

        dataset.addSeries(buySeries);
        dataset.addSeries(sellSeries);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, barRenderer(new Color(38, 166, 91, 200), new Color(231, 76, 60, 200)));
        plot.mapDatasetToRangeAxis(0, 0);

        double padding = maxVolume * 1.1;
        if (padding > 0) {
            plot.getRangeAxis().setRange(-padding, padding);
        }
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addSimpleZeroLine(plot, startTime, endTime);
    }
}
