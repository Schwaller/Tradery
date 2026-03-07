package com.tradery.forge.ui.charts;

import com.tradery.charts.overlay.DailyProfileProvider;
import com.tradery.charts.overlay.FootprintProfileProvider;
import com.tradery.ui.controls.ChartConfig;
import com.tradery.charts.indicator.PremiumChart;
import com.tradery.charts.indicator.SpectrumChart;
import com.tradery.core.model.PremiumIndex;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.page.PremiumPageManager;
import com.tradery.forge.data.page.SpectrumPageManager;
import com.tradery.ui.controls.indicators.SpectrumBucketMode;
import com.tradery.ui.controls.indicators.SpectrumColorMode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating tradery-charts adapter implementations
 * that bridge forge-specific singletons to constructor-injected interfaces.
 */
public final class ForgeChartProviders {

    private ForgeChartProviders() {}

    public static PremiumChart.PremiumDataSource premiumDataSource() {
        PremiumPageManager mgr = ApplicationContext.getInstance().getPremiumPageManager();
        if (mgr == null) return null;
        return new PremiumChart.PremiumDataSource() {
            @Override
            public DataPageView<PremiumIndex> request(String symbol, String timeframe, long start, long end,
                                                       DataPageListener<PremiumIndex> listener, String consumer) {
                return mgr.request(symbol, timeframe, start, end, listener, consumer);
            }

            @Override
            public void release(DataPageView<PremiumIndex> page, DataPageListener<PremiumIndex> listener) {
                mgr.release(page, listener);
            }
        };
    }

    public static SpectrumChart.SpectrumDataSource spectrumDataSource() {
        SpectrumPageManager mgr = ApplicationContext.getInstance().getSpectrumPageManager();
        if (mgr == null) return null;
        return new SpectrumChart.SpectrumDataSource() {
            @Override
            public DataPageView<SpectrumWindow> request(String symbol, String bucketMode, long start, long end,
                                                         DataPageListener<SpectrumWindow> listener, String consumer) {
                return mgr.request(symbol, bucketMode, start, end, listener, consumer);
            }

            @Override
            public void release(DataPageView<SpectrumWindow> page, DataPageListener<SpectrumWindow> listener) {
                mgr.release(page, listener);
            }
        };
    }

    /**
     * Create a FootprintProfileProvider that wraps the forge DataServiceClient.
     */
    public static FootprintProfileProvider footprintProfileProvider() {
        return new FootprintProfileProvider() {
            @Override
            public List<RawProfile> getProfiles(String symbol, String timeframe,
                                                  long start, long end, String marketType) throws Exception {
                ApplicationContext ctx = ApplicationContext.getInstance();
                if (ctx == null || !ctx.isDataServiceAvailable()) return List.of();
                DataServiceClient client = ctx.getDataServiceClient();
                var rawProfiles = client.getProfiles(symbol, timeframe, start, end, marketType);
                if (rawProfiles == null) return List.of();
                return rawProfiles.stream()
                    .map(p -> new RawProfile(p.windowStart(), p.tickSize(),
                        p.totalBuyVolume(), p.totalSellVolume(), p.levels()))
                    .collect(Collectors.toList());
            }

            @Override
            public boolean isAvailable() {
                ApplicationContext ctx = ApplicationContext.getInstance();
                return ctx != null && ctx.isDataServiceAvailable();
            }
        };
    }

    /**
     * Create a DailyProfileProvider that wraps the forge DataServiceClient.
     */
    public static DailyProfileProvider dailyProfileProvider() {
        return new DailyProfileProvider() {
            @Override
            public List<DailyBinnedResult> getDailyBinned(String symbol, long start, long end,
                                                            int binCount, double valueAreaPct) throws Exception {
                ApplicationContext ctx = ApplicationContext.getInstance();
                if (ctx == null || !ctx.isDataServiceAvailable()) return List.of();
                DataServiceClient client = ctx.getDataServiceClient();
                var dailyBinned = client.getProfileDailyBinned(symbol, start, end, binCount, valueAreaPct);
                if (dailyBinned == null) return List.of();
                return dailyBinned.stream()
                    .map(d -> new DailyBinnedResult(d.dayStart(), d.poc(), d.vah(), d.val(),
                        d.bins() != null ? d.bins().priceLevels() : new double[0],
                        d.bins() != null ? d.bins().buyVolumes() : new double[0],
                        d.bins() != null ? d.bins().sellVolumes() : new double[0]))
                    .collect(Collectors.toList());
            }

            @Override
            public boolean isAvailable() {
                ApplicationContext ctx = ApplicationContext.getInstance();
                return ctx != null && ctx.isDataServiceAvailable();
            }
        };
    }

    public static SpectrumChart.SpectrumConfig spectrumConfig() {
        return new SpectrumChart.SpectrumConfig() {
            @Override
            public SpectrumColorMode getColorMode() {
                return ChartConfig.getInstance().getSpectrumColorMode();
            }

            @Override
            public SpectrumBucketMode getBucketMode() {
                return ChartConfig.getInstance().getSpectrumBucketMode();
            }
        };
    }
}
