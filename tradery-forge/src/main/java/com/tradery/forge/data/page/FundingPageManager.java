package com.tradery.forge.data.page;

import com.tradery.core.model.FundingRate;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.DataType;
import com.tradery.forge.data.log.DownloadLogStore;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for funding rate data.
 *
 * Funding rates don't have a timeframe - they occur every 8 hours.
 * Delegates all data loading to the Data Service which handles
 * caching and fetching from Binance.
 */
public class FundingPageManager extends DataPageManager<FundingRate> {

    public FundingPageManager() {
        super(DataType.FUNDING, 2);
    }

    @Override
    protected void loadData(DataPage<FundingRate> page) throws Exception {
        assertNotEDT("FundingPageManager.loadData");

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        log.info("FundingPageManager.loadData: {} requesting from data service", symbol);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            DownloadLogStore.getInstance().logError(forgePageKey, DataType.FUNDING,
                "Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        try {
            // Log the request to data service
            logStore.logApiRequestStarted(forgePageKey, DataType.FUNDING,
                "data-service/pages/request",
                String.format("%s %d-%d", symbol, startTime, endTime));

            long requestStart = System.currentTimeMillis();

            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest("FUNDING", symbol, null, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "FundingPageManager"
            );

            // Poll for completion
            String dataServicePageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(dataServicePageKey);
                if (status == null) {
                    log.error("Page status not found: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, DataType.FUNDING,
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
                    logStore.logError(forgePageKey, DataType.FUNDING,
                        "Data service page error: " + dataServicePageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            List<FundingRate> rates = client.getFundingRates(symbol, startTime, endTime);
            long totalDuration = System.currentTimeMillis() - requestStart;

            log.info("FundingPageManager.loadData: {} got {} rates", symbol, rates.size());

            // Log the response from data service
            logStore.logApiRequestCompleted(forgePageKey, DataType.FUNDING,
                "data-service/funding", rates.size(), totalDuration);

            updatePageData(page, rates);

        } catch (Exception e) {
            log.error("Failed to load funding rates from data service: {}", e.getMessage());
            logStore.logError(forgePageKey, DataType.FUNDING,
                "Failed to load from data service: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    /**
     * Load from cache via data service.
     */
    public List<FundingRate> loadFromCacheOnly(String symbol, long startTime, long endTime) {
        try {
            ApplicationContext ctx = ApplicationContext.getInstance();
            if (ctx != null && ctx.isDataServiceAvailable()) {
                DataServiceClient client = ctx.getDataServiceClient();
                List<FundingRate> rates = client.getFundingRates(symbol, startTime, endTime);
                log.debug("Loaded {} funding rates from data service for {}", rates.size(), symbol);
                return rates;
            }
            log.warn("Data service not available");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to load funding rates from data service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
