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

public class TradeCountChart extends IndicatorChart {

    public TradeCountChart() { super(IndicatorType.TRADE_COUNT); }

    @Override public boolean requiresOrderflow() { return true; }

    @Override
    public void subscribe(ChartDataContext ctx) {
        ctx.indicatorDataService().subscribeTradeCount();
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        double[] tradeCount = ctx.indicatorDataService().getTradeCount();
        if (tradeCount == null) return false;

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = new TimeSeries("Trade Count");

        for (int i = 0; i < candles.size() && i < tradeCount.length; i++) {
            if (!Double.isNaN(tradeCount[i])) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), tradeCount[i]);
            }
        }

        dataset.addSeries(series);
        plot.setDataset(0, dataset);
        plot.setRenderer(0, lineRenderer(ChartStyles.TRADE_COUNT_LINE_COLOR));
        return true;
    }
}
