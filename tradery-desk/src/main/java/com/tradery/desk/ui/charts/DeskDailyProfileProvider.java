package com.tradery.desk.ui.charts;

import com.tradery.charts.overlay.DailyProfileProvider;
import com.tradery.dataclient.DataServiceClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Desk adapter for DailyProfileProvider that wraps DataServiceClient.
 */
public class DeskDailyProfileProvider implements DailyProfileProvider {

    private final DataServiceClient client;

    public DeskDailyProfileProvider(DataServiceClient client) {
        this.client = client;
    }

    @Override
    public List<DailyBinnedResult> getDailyBinned(String symbol, long start, long end,
                                                    int binCount, double valueAreaPct) throws Exception {
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
        return client != null;
    }
}
