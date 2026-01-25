package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.FundingRate;

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

        log.info("FundingPageManager.loadData: {} requesting from data service", symbol);

        // Request page from data service (handles fetching if needed)
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();

        try {
            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest("FUNDING", symbol, null, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "FundingPageManager"
            );

            // Poll for completion
            String pageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(pageKey);
                if (status == null) {
                    log.error("Page status not found: {}", pageKey);
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
                    log.error("Page load error: {}", pageKey);
                    break;
                }

                Thread.sleep(100); // Poll interval
            }

            // Fetch final data
            List<FundingRate> rates = client.getFundingRates(symbol, startTime, endTime);
            log.info("FundingPageManager.loadData: {} got {} rates", symbol, rates.size());
            updatePageData(page, rates);

        } catch (Exception e) {
            log.error("Failed to load funding rates from data service: {}", e.getMessage());
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
