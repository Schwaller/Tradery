package com.tradery.forge.data.page;

import com.tradery.core.model.OpenInterest;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for open interest data.
 * Fixed 5-minute resolution from Binance Futures API.
 * Delegates all data loading to the Data Service.
 */
public class OIPageManager extends DataServicePageManager<OpenInterest> {

    public OIPageManager() {
        super(DataType.OPEN_INTEREST, "OPEN_INTEREST", 2,
            (client, pageKey) -> client.getOpenInterest(pageKey),
            "data-service/openinterest", 64,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, OpenInterest.class)));
    }
}
