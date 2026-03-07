package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Trade;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;
import static com.tradery.charts.util.RendererBuilder.barRenderer;

public class HoldingCostEventsChart extends IndicatorChartBase {

    public HoldingCostEventsChart() { super(IndicatorType.HOLDING_COST_EVENTS); }

    @Override
    public void subscribe(ChartDataContext ctx) {
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        List<Trade> trades = ctx.trades();
        if (trades == null || trades.isEmpty()) return false;

        XYSeries costsSeries = new XYSeries("Costs");
        XYSeries earningsSeries = new XYSeries("Earnings");

        for (Trade t : trades) {
            if (t.exitTime() != null && t.holdingCosts() != null && t.holdingCosts() != 0) {
                if (t.holdingCosts() > 0) {
                    costsSeries.add(t.exitTime(), t.holdingCosts());
                } else {
                    earningsSeries.add(t.exitTime(), t.holdingCosts());
                }
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(costsSeries);
        dataset.addSeries(earningsSeries);
        plot.setDataset(dataset);
        plot.setRenderer(barRenderer(ChartStyles.LOSS_COLOR, ChartStyles.WIN_COLOR));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addZeroLine(plot, startTime, endTime);
    }

    @Override
    public String getTitle() { return "Holding Cost Events ($)"; }
}
