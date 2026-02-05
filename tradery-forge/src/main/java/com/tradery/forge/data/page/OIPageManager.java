package com.tradery.forge.data.page;

import com.tradery.core.model.OpenInterest;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.DataType;
import com.tradery.forge.data.log.DownloadLogStore;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for open interest data.
 *
 * Open interest has fixed 5-minute resolution from Binance Futures API.
 * Note: Binance limits historical OI to 30 days, but local cache persists longer.
 * Delegates all data loading to the Data Service which handles
 * caching and fetching from Binance.
 */
public class OIPageManager extends DataPageManager<OpenInterest> {

    public OIPageManager() {
        super(DataType.OPEN_INTEREST, 2);
    }

    @Override
    protected void loadData(DataPage<OpenInterest> page) throws Exception {
        assertNotEDT("OIPageManager.loadData");

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        log.info("OIPageManager.loadData: {} requesting from data service", symbol);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            DownloadLogStore.getInstance().logError(forgePageKey, DataType.OPEN_INTEREST,
                "Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        try {
            // Log the request to data service
            logStore.logApiRequestStarted(forgePageKey, DataType.OPEN_INTEREST,
                "data-service/pages/request",
                String.format("%s %d-%d", symbol, startTime, endTime));

            long requestStart = System.currentTimeMillis();

            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest("OPEN_INTEREST", symbol, null, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "OIPageManager"
            );

            // Poll for completion
            String dataServicePageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(dataServicePageKey);
                if (status == null) {
                    log.error("Page status not found: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, DataType.OPEN_INTEREST,
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
                    logStore.logError(forgePageKey, DataType.OPEN_INTEREST,
                        "Data service page error: " + dataServicePageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            List<OpenInterest> oi = client.getOpenInterest(symbol, startTime, endTime);
            long totalDuration = System.currentTimeMillis() - requestStart;

            log.info("OIPageManager.loadData: {} got {} records", symbol, oi.size());

            // Log the response from data service
            logStore.logApiRequestCompleted(forgePageKey, DataType.OPEN_INTEREST,
                "data-service/openinterest", oi.size(), totalDuration);

            updatePageData(page, oi);

        } catch (Exception e) {
            log.error("Failed to load OI from data service: {}", e.getMessage());
            logStore.logError(forgePageKey, DataType.OPEN_INTEREST,
                "Failed to load from data service: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    /**
     * Load from cache via data service.
     */
    public List<OpenInterest> loadFromCacheOnly(String symbol, long startTime, long endTime) {
        try {
            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx != null && ctx.isDataServiceAvailable()) {
                DataServiceClient client = ctx.getDataServiceClient();
                List<OpenInterest> oi = client.getOpenInterest(symbol, startTime, endTime);
                log.debug("Loaded {} OI records from data service for {}", oi.size(), symbol);
                return oi;
            }
            log.warn("Data service not available");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to load OI from data service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
