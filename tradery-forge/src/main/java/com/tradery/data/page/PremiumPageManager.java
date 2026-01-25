package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.PremiumIndex;

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

        log.info("PremiumPageManager.loadData: {} {} requesting from data service", symbol, timeframe);

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
                new DataServiceClient.PageRequest("PREMIUM_INDEX", symbol, timeframe, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "PremiumPageManager"
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
            List<PremiumIndex> premium = client.getPremiumIndex(symbol, timeframe, startTime, endTime);
            log.info("PremiumPageManager.loadData: {} {} got {} records", symbol, timeframe, premium.size());
            updatePageData(page, premium);

        } catch (Exception e) {
            log.error("Failed to load premium index from data service: {}", e.getMessage());
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
