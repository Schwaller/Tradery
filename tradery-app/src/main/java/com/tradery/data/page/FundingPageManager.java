package com.tradery.data.page;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.data.FundingRateStore;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.model.FundingRate;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for funding rate data.
 *
 * Funding rates don't have a timeframe - they occur every 8 hours.
 * Uses FundingRateStore for both cache and API fetching.
 */
public class FundingPageManager extends DataPageManager<FundingRate> {

    private final FundingRateStore fundingRateStore;

    public FundingPageManager(FundingRateStore fundingRateStore) {
        super(DataType.FUNDING, 2);
        this.fundingRateStore = fundingRateStore;
    }

    @Override
    protected void loadData(DataPage<FundingRate> page) throws Exception {
        assertNotEDT("FundingPageManager.loadData");

        if (fundingRateStore == null) {
            updatePageData(page, Collections.emptyList());
            return;
        }

        String symbol = page.getSymbol();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // Check cache first for initial progress
        List<FundingRate> cached = loadFromCacheOnly(symbol, startTime, endTime);
        if (!cached.isEmpty()) {
            // Estimate expected records (8-hour intervals)
            long expectedRecords = (endTime - startTime) / (8 * 60 * 60 * 1000);
            int initialProgress = expectedRecords > 0 ? (int) ((cached.size() * 100) / expectedRecords) : 50;
            updatePageProgress(page, Math.min(initialProgress, 95));
        } else {
            updatePageProgress(page, 10);  // Show we're working
        }

        // FundingRateStore handles caching + API fetch internally
        List<FundingRate> rates = fundingRateStore.getFundingRates(symbol, startTime, endTime);

        log.debug("Loaded {} funding rates for {}", rates.size(), symbol);
        updatePageData(page, rates);
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
