package com.tradery.forge.data.page;

import com.tradery.core.model.PremiumIndex;
import com.tradery.data.page.DataType;

/**
 * Page manager for premium index data (futures vs spot spread).
 * Requires a timeframe to match strategy resolution.
 * Delegates all data loading to the Data Service.
 */
public class PremiumPageManager extends DataServicePageManager<PremiumIndex> {

    public PremiumPageManager() {
        super(DataType.PREMIUM_INDEX, "PREMIUM_INDEX", 2,
            (client, sym, tf, start, end) -> client.getPremiumIndex(sym, tf, start, end),
            "data-service/premium");
    }
}
