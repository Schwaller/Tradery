package com.tradery.forge.data.page;

import com.tradery.core.model.PremiumIndex;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for premium index data (futures vs spot spread).
 * Requires a timeframe to match strategy resolution.
 * Delegates all data loading to the Data Service via WebSocket.
 */
public class PremiumPageManager extends DataServicePageManager<PremiumIndex> {

    public PremiumPageManager() {
        super(DataType.PREMIUM_INDEX, 2,
            "data-service/premium", 64,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, PremiumIndex.class)));
    }
}
