package com.tradery.forge.data.page;

import com.tradery.core.model.PremiumIndex;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.DataType;
import com.tradery.forge.data.log.DownloadLogStore;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for premium index data (futures vs spot spread).
 *
 * Premium index requires a timeframe to match strategy resolution.
 * Delegates all data loading to the Data Service which handles
 * caching and fetching from Binance.
 */
public class PremiumPageManager extends DataPageManager<PremiumIndex> {

    public PremiumPageManager() {
        super(DataType.PREMIUM_INDEX, 2);
    }

    @Override
    protected void loadData(DataPage<PremiumIndex> page) throws Exception {
        assertNotEDT("PremiumPageManager.loadData");

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        log.info("PremiumPageManager.loadData: {} {} requesting from data service", symbol, timeframe);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            DownloadLogStore.getInstance().logError(forgePageKey, DataType.PREMIUM_INDEX,
                "Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        try {
            // Log the request to data service
            logStore.logApiRequestStarted(forgePageKey, DataType.PREMIUM_INDEX,
                "data-service/pages/request",
                String.format("%s/%s %d-%d", symbol, timeframe, startTime, endTime));

            long requestStart = System.currentTimeMillis();

            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest("PREMIUM_INDEX", symbol, timeframe, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "PremiumPageManager"
            );

            // Poll for completion
            String dataServicePageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(dataServicePageKey);
                if (status == null) {
                    log.error("Page status not found: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, DataType.PREMIUM_INDEX,
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
                    logStore.logError(forgePageKey, DataType.PREMIUM_INDEX,
                        "Data service page error: " + dataServicePageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            List<PremiumIndex> premium = client.getPremiumIndex(symbol, timeframe, startTime, endTime);
            long totalDuration = System.currentTimeMillis() - requestStart;

            log.info("PremiumPageManager.loadData: {} {} got {} records", symbol, timeframe, premium.size());

            // Log the response from data service
            logStore.logApiRequestCompleted(forgePageKey, DataType.PREMIUM_INDEX,
                "data-service/premium", premium.size(), totalDuration);

            updatePageData(page, premium);

        } catch (Exception e) {
            log.error("Failed to load premium index from data service: {}", e.getMessage());
            logStore.logError(forgePageKey, DataType.PREMIUM_INDEX,
                "Failed to load from data service: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    /**
     * Load from cache via data service.
     */
    public List<PremiumIndex> loadFromCacheOnly(String symbol, String timeframe,
                                                 long startTime, long endTime) {
        try {
            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx != null && ctx.isDataServiceAvailable()) {
                DataServiceClient client = ctx.getDataServiceClient();
                List<PremiumIndex> premium = client.getPremiumIndex(symbol, timeframe, startTime, endTime);
                log.debug("Loaded {} premium index records from data service for {} {}",
                    premium.size(), symbol, timeframe);
                return premium;
            }
            log.warn("Data service not available");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to load premium index from data service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
