package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.model.Candle;
import com.tradery.core.model.PremiumIndex;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.ui.charts.ChartStyles;
import com.tradery.forge.ui.charts.IndicatorType;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.page.PremiumPageManager;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.util.*;

import static com.tradery.charts.util.ChartAnnotationHelper.addZeroLine;
import static com.tradery.charts.util.RendererBuilder.colorCodedBarRenderer;
import static com.tradery.charts.util.RendererBuilder.lineRenderer;

/**
 * Premium Index chart. Uses PremiumPageManager for async data loading,
 * with IndicatorEngine fallback.
 */
public class PremiumChart extends IndicatorChart {

    private DataPageView<PremiumIndex> premiumPage;
    private DataPageListener<PremiumIndex> premiumListener;
    private List<PremiumIndex> currentPremiumData;

    public PremiumChart() { super(IndicatorType.PREMIUM); }

    @Override
    public void subscribe(ChartDataContext ctx) {
        PremiumPageManager premiumPageMgr = ApplicationContext.getInstance().getPremiumPageManager();
        if (premiumPageMgr != null) {
            subscribePremiumPage(ctx, premiumPageMgr);
        } else if (ctx.indicatorEngine() != null) {
            subscribeViaEngine(ctx);
        }
    }

    private void subscribePremiumPage(ChartDataContext ctx, PremiumPageManager premiumPageMgr) {
        if (premiumPage != null && premiumListener != null) {
            premiumPageMgr.release(premiumPage, premiumListener);
        }

        premiumListener = new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<PremiumIndex> page, PageState oldState, PageState newState) {
                if (newState == PageState.READY) {
                    currentPremiumData = page.getData();
                    SwingUtilities.invokeLater(() -> renderFromPageData(ctx));
                }
            }

            @Override
            public void onDataChanged(DataPageView<PremiumIndex> page) {
                if (page.isReady()) {
                    currentPremiumData = page.getData();
                    SwingUtilities.invokeLater(() -> renderFromPageData(ctx));
                }
            }
        };

        premiumPage = premiumPageMgr.request(ctx.symbol(), ctx.timeframe(), ctx.startTime(), ctx.endTime(), premiumListener, "PremiumChart");
    }

    private void subscribeViaEngine(ChartDataContext ctx) {
        var engine = ctx.indicatorEngine();
        Thread.startVirtualThread(() -> {
            double[] premium = engine.getPremium();
            double[] premiumAvg = engine.getPremiumAvg(24);
            if (premium == null || premium.length == 0) return;
            SwingUtilities.invokeLater(() -> renderFromEngine(ctx, premium, premiumAvg));
        });
    }

    private void renderFromPageData(ChartDataContext ctx) {
        List<Candle> candles = ctx.indicatorDataService().getCandles();
        if (candles == null || candles.isEmpty() || currentPremiumData == null) return;

        XYPlot plot = component.getChart().getXYPlot();

        Map<Long, PremiumIndex> premiumMap = new HashMap<>();
        for (PremiumIndex p : currentPremiumData) {
            premiumMap.put(p.openTime(), p);
        }

        XYSeriesCollection premiumDataset = new XYSeriesCollection();
        XYSeries premiumSeries = new XYSeries("Premium");
        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Premium 24 Avg");

        double[] premiumValues = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            PremiumIndex p = premiumMap.get(candles.get(i).timestamp());
            premiumValues[i] = p != null ? p.close() * 100.0 : Double.NaN;
        }

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(premiumValues[i])) {
                premiumSeries.add(c.timestamp(), premiumValues[i]);
            }
            if (i >= 23) {
                double sum = 0;
                int count = 0;
                for (int j = i - 23; j <= i; j++) {
                    if (!Double.isNaN(premiumValues[j])) { sum += premiumValues[j]; count++; }
                }
                if (count > 0) {
                    avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), sum / count);
                }
            }
        }

        applyDatasets(plot, premiumDataset, premiumSeries, avgDataset, avgSeries, candles);
    }

    private void renderFromEngine(ChartDataContext ctx, double[] premium, double[] premiumAvg) {
        List<Candle> candles = ctx.indicatorDataService().getCandles();
        if (candles == null || candles.isEmpty()) return;

        XYPlot plot = component.getChart().getXYPlot();

        XYSeriesCollection premiumDataset = new XYSeriesCollection();
        XYSeries premiumSeries = new XYSeries("Premium");
        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Premium 24 Avg");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (i < premium.length && !Double.isNaN(premium[i])) {
                premiumSeries.add(c.timestamp(), premium[i]);
            }
            if (i < premiumAvg.length && !Double.isNaN(premiumAvg[i])) {
                avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), premiumAvg[i]);
            }
        }

        applyDatasets(plot, premiumDataset, premiumSeries, avgDataset, avgSeries, candles);
    }

    private void applyDatasets(XYPlot plot, XYSeriesCollection premiumDataset, XYSeries premiumSeries,
                                TimeSeriesCollection avgDataset, TimeSeries avgSeries, List<Candle> candles) {
        premiumDataset.addSeries(premiumSeries);
        avgDataset.addSeries(avgSeries);

        plot.setDataset(0, premiumDataset);
        plot.setDataset(1, avgDataset);
        plot.setRenderer(0, colorCodedBarRenderer(premiumDataset, ChartStyles.PREMIUM_POSITIVE, ChartStyles.PREMIUM_NEGATIVE));
        plot.setRenderer(1, lineRenderer(ChartStyles.PREMIUM_AVG_COLOR));

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Premium Index (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            addZeroLine(plot, startTime, endTime);
        }
    }

    @Override
    protected boolean render(XYPlot plot, List<Candle> candles, ChartDataContext ctx) {
        // Rendering handled in subscribe() via async callbacks
        return false;
    }

    @Override
    public boolean managesOwnAnnotations() { return true; }

    @Override
    public void dispose() {
        PremiumPageManager premiumPageMgr = ApplicationContext.getInstance().getPremiumPageManager();
        if (premiumPageMgr != null && premiumPage != null && premiumListener != null) {
            premiumPageMgr.release(premiumPage, premiumListener);
        }
        premiumPage = null;
        premiumListener = null;
        currentPremiumData = null;
    }
}
