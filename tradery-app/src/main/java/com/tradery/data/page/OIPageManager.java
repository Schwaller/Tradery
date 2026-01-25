package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.data.OpenInterestStore;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.OpenInterest;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for open interest data.
 *
 * Open interest has fixed 5-minute resolution from Binance Futures API.
 * Note: Binance limits historical OI to 30 days, but local cache persists longer.
 */
public class OIPageManager extends DataPageManager<OpenInterest> {

    private final OpenInterestStore openInterestStore;

    public OIPageManager(OpenInterestStore openInterestStore) {
        super(DataType.OPEN_INTEREST, 2);
        this.openInterestStore = openInterestStore;
    }

    @Override
    protected void loadData(DataPage<OpenInterest> page) throws Exception {
        assertNotEDT("OIPageManager.loadData");

        if (openInterestStore == null) {
            updatePageData(page, Collections.emptyList());
            return;
        }

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // Check cache first for initial progress
        List<OpenInterest> cached = loadFromCacheOnly(symbol, startTime, endTime);
        if (!cached.isEmpty()) {
            // Estimate expected records (5-minute intervals)
            long expectedRecords = (endTime - startTime) / (5 * 60 * 1000);
            int initialProgress = expectedRecords > 0 ? (int) ((cached.size() * 100) / expectedRecords) : 50;
            updatePageProgress(page, Math.min(initialProgress, 95));
        } else {
            updatePageProgress(page, 10);  // Show we're working
        }

        // OpenInterestStore handles caching + API fetch internally
        List<OpenInterest> oi = openInterestStore.getOpenInterest(symbol, startTime, endTime);

        log.debug("Loaded {} OI records for {}", oi.size(), symbol);
        updatePageData(page, oi);
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
