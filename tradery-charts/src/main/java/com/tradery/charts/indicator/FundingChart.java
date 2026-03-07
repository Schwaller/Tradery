package com.tradery.charts.indicator;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.util.Date;
import java.util.List;

import static com.tradery.charts.util.ChartAnnotationHelper.addFundingLines;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;
import static com.tradery.charts.util.RendererBuilder.signColoredLineRenderer;

public class FundingChart extends IndicatorChartBase {

    public FundingChart() { super(IndicatorType.FUNDING); }

    @Override
    public void subscribe(ChartDataContext ctx) {
        if (ctx.indicatorEngine() == null) return;

        var engine = ctx.indicatorEngine();
        Thread.startVirtualThread(() -> {
            double[] funding = engine.getFunding();
            double[] funding8H = engine.getFunding8H();
            if (funding == null || funding.length == 0) return;
            SwingUtilities.invokeLater(() -> applyData(ctx, funding, funding8H));
        });
    }

    private void applyData(ChartDataContext ctx, double[] funding, double[] funding8H) {
        List<Candle> candles = ctx.indicatorDataProvider().getCandles();
        if (candles == null || candles.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();

        XYSeriesCollection fundingDataset = new XYSeriesCollection();
        XYSeries fundingSeries = new XYSeries("Funding");

        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Funding 8H Avg");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(funding[i])) {
                fundingSeries.add(c.timestamp(), funding[i]);
            }
            if (!Double.isNaN(funding8H[i])) {
                avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), funding8H[i]);
            }
        }

        fundingDataset.addSeries(fundingSeries);
        avgDataset.addSeries(avgSeries);

        plot.setDataset(0, fundingDataset);
        plot.setDataset(1, avgDataset);
        plot.setRenderer(0, signColoredLineRenderer(fundingDataset, ChartStyles.FUNDING_POSITIVE, ChartStyles.FUNDING_NEGATIVE));
        plot.setRenderer(1, lineRenderer(ChartStyles.FUNDING_8H_COLOR));

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new java.text.DecimalFormat("0.####"));

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Funding Rate (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addFundingLines(plot, startTime, endTime);
        }
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        return false;
    }

    @Override
    public String getTitle() { return "Funding Rate (%)"; }
}
