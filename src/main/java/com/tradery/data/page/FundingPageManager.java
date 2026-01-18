package com.tradery.data.page;

import com.tradery.data.DataType;
import com.tradery.data.FundingRateStore;
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

        // FundingRateStore handles caching + API fetch internally
        List<FundingRate> rates = fundingRateStore.getFundingRates(symbol, startTime, endTime);

        log.debug("Loaded {} funding rates for {}", rates.size(), symbol);
        updatePageData(page, rates);
    }

    /**
     * Load only from cache (no API fetch).
     */
    public List<FundingRate> loadFromCacheOnly(String symbol, long startTime, long endTime) {
        if (fundingRateStore == null) {
            return Collections.emptyList();
        }
        try {
            return fundingRateStore.getFundingRatesCacheOnly(symbol, startTime, endTime);
        } catch (Exception e) {
            log.debug("Cache read failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
