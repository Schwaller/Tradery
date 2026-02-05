package com.tradery.forge.data.page;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.DataType;
import com.tradery.forge.data.log.DownloadLogStore;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.core.model.Candle;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for OHLCV candle data.
 *
 * Delegates all data loading to the Data Service which handles
 * caching and fetching from Binance.
 */
public class CandlePageManager extends DataPageManager<Candle> {

    public CandlePageManager() {
        super(DataType.CANDLES, 4);
    }

    @Override
    protected void loadData(DataPage<Candle> page) throws Exception {
        assertNotEDT("CandlePageManager.loadData");

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        log.info("CandlePageManager.loadData: {} {} requesting from data service", symbol, timeframe);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            DownloadLogStore.getInstance().logError(forgePageKey, DataType.CANDLES,
                "Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        try {
            // Log the request to data service
            logStore.logApiRequestStarted(forgePageKey, DataType.CANDLES,
                "data-service/pages/request",
                String.format("%s/%s %d-%d", symbol, timeframe, startTime, endTime));

            long requestStart = System.currentTimeMillis();

            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest("CANDLES", symbol, timeframe, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "CandlePageManager"
            );

            // Poll for completion
            String dataServicePageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(dataServicePageKey);
                if (status == null) {
                    log.error("Page status not found: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, DataType.CANDLES,
                        "Page status not found: " + dataServicePageKey);
                    break;
                }

                // Update progress
                if (status.progress() > lastProgress) {
                    lastProgress = status.progress();
                    updatePageProgress(page, Math.min(lastProgress, 95));
                }

                // Check if ready
                if ("READY".equals(status.state())) {
                    break;
                } else if ("ERROR".equals(status.state())) {
                    log.error("Page load error: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, DataType.CANDLES,
                        "Data service page error: " + dataServicePageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            long fetchStart = System.currentTimeMillis();
            List<Candle> candles = client.getCandles(symbol, timeframe, startTime, endTime);
            long fetchDuration = System.currentTimeMillis() - fetchStart;
            long totalDuration = System.currentTimeMillis() - requestStart;

            log.info("CandlePageManager.loadData: {} {} got {} candles", symbol, timeframe, candles.size());

            // Log the response from data service
            logStore.logApiRequestCompleted(forgePageKey, DataType.CANDLES,
                "data-service/candles", candles.size(), totalDuration);

            updatePageData(page, candles);

        } catch (Exception e) {
            log.error("Failed to load candles from data service: {}", e.getMessage());
            logStore.logError(forgePageKey, DataType.CANDLES,
                "Failed to load from data service: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    @Override
    protected int getRecordSizeBytes() {
        return 88;
    }
}
