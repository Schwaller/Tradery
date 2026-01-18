package com.tradery.data.page;

import com.tradery.data.DataType;
import com.tradery.data.PremiumIndexStore;
import com.tradery.model.PremiumIndex;

import java.util.Collections;
import java.util.List;

/**
 * Page manager for premium index data (futures vs spot spread).
 *
 * Premium index requires a timeframe to match strategy resolution.
 * Uses PremiumIndexStore for caching and API fetching.
 */
public class PremiumPageManager extends DataPageManager<PremiumIndex> {

    private final PremiumIndexStore premiumIndexStore;

    public PremiumPageManager(PremiumIndexStore premiumIndexStore) {
        super(DataType.PREMIUM_INDEX, 2);
        this.premiumIndexStore = premiumIndexStore;
    }

    @Override
    protected void loadData(DataPage<PremiumIndex> page) throws Exception {
        assertNotEDT("PremiumPageManager.loadData");

        if (premiumIndexStore == null) {
            updatePageData(page, Collections.emptyList());
            return;
        }

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();

        // PremiumIndexStore handles caching + API fetch internally
        List<PremiumIndex> premium = premiumIndexStore.getPremiumIndex(
            symbol, timeframe, startTime, endTime);

        log.debug("Loaded {} premium index records for {} {}",
            premium.size(), symbol, timeframe);
        updatePageData(page, premium);
    }

    /**
     * Load only from cache (no API fetch).
     */
    public List<PremiumIndex> loadFromCacheOnly(String symbol, String timeframe,
                                                 long startTime, long endTime) {
        if (premiumIndexStore == null) {
            return Collections.emptyList();
        }
        try {
            return premiumIndexStore.getPremiumIndexCacheOnly(
                symbol, timeframe, startTime, endTime);
        } catch (Exception e) {
            log.debug("Cache read failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
