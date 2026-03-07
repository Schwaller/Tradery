package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Trade;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

public class HoldingCostCumulativeChart extends IndicatorChartBase {

    public HoldingCostCumulativeChart() { super(IndicatorType.HOLDING_COST_CUMULATIVE); }

    @Override
    public void subscribe(ChartDataContext ctx) {
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        List<Trade> trades = ctx.trades();
        if (trades == null || trades.isEmpty()) return false;

        TreeMap<Long, Double> holdingCostByTime = new TreeMap<>();
        for (Trade t : trades) {
            if (t.exitTime() != null && t.holdingCosts() != null) {
                holdingCostByTime.merge(t.exitTime(), t.holdingCosts(), Double::sum);
            }
        }

        TimeSeries cumulativeSeries = new TimeSeries("Cumulative Holding Costs");
        double cumulative = 0;

        for (Candle c : candles) {
            if (holdingCostByTime.containsKey(c.timestamp())) {
                cumulative += holdingCostByTime.get(c.timestamp());
            }
            cumulativeSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), cumulative);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(cumulativeSeries);
        plot.setDataset(dataset);
        plot.setRenderer(lineRenderer(cumulative >= 0 ? ChartStyles.LOSS_COLOR : ChartStyles.WIN_COLOR));
        return true;
    }

    @Override
    protected void addReferenceLines(XYPlot plot, long startTime, long endTime) {
        addZeroLine(plot, startTime, endTime);
    }

    @Override
    public String getTitle() { return "Cumulative Holding Costs ($)"; }
}
