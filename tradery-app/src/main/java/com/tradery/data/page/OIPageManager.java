package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.OpenInterest;

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

        log.info("OIPageManager.loadData: {} requesting from data service", symbol);

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
                new DataServiceClient.PageRequest("OPEN_INTEREST", symbol, null, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                "OIPageManager"
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
            List<OpenInterest> oi = client.getOpenInterest(symbol, startTime, endTime);
            log.info("OIPageManager.loadData: {} got {} records", symbol, oi.size());
            updatePageData(page, oi);

        } catch (Exception e) {
            log.error("Failed to load OI from data service: {}", e.getMessage());
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
