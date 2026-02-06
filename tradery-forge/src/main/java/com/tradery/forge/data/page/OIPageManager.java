package com.tradery.forge.data.page;

import com.tradery.core.model.OpenInterest;
import com.tradery.data.page.DataType;

/**
 * Page manager for open interest data.
 * Fixed 5-minute resolution from Binance Futures API.
 * Delegates all data loading to the Data Service.
 */
public class OIPageManager extends DataServicePageManager<OpenInterest> {

    public OIPageManager() {
        super(DataType.OPEN_INTEREST, "OPEN_INTEREST", 2,
            (client, sym, tf, start, end) -> client.getOpenInterest(sym, start, end),
            "data-service/openinterest");
    }
}
