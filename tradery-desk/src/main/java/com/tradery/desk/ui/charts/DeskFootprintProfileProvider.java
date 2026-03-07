package com.tradery.desk.ui.charts;

import com.tradery.charts.overlay.FootprintProfileProvider;
import com.tradery.dataclient.DataServiceClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Desk adapter for FootprintProfileProvider that wraps DataServiceClient.
 */
public class DeskFootprintProfileProvider implements FootprintProfileProvider {

    private final DataServiceClient client;

    public DeskFootprintProfileProvider(DataServiceClient client) {
        this.client = client;
    }

    @Override
    public List<RawProfile> getProfiles(String symbol, String timeframe,
                                         long start, long end, String marketType) throws Exception {
        var rawProfiles = client.getProfiles(symbol, timeframe, start, end, marketType);
        if (rawProfiles == null) return List.of();
        return rawProfiles.stream()
            .map(p -> new RawProfile(p.windowStart(), p.tickSize(),
                p.totalBuyVolume(), p.totalSellVolume(), p.levels()))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }
}
