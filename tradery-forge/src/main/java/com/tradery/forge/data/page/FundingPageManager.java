package com.tradery.forge.data.page;

import com.tradery.core.model.FundingRate;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for funding rate data.
 * Funding rates don't have a timeframe - they occur every 8 hours.
 * Delegates all data loading to the Data Service.
 */
public class FundingPageManager extends DataServicePageManager<FundingRate> {

    public FundingPageManager() {
        super(DataType.FUNDING, "FUNDING", 2,
            (client, pageKey) -> client.getFundingRates(pageKey),
            "data-service/funding", 64,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, FundingRate.class)));
    }
}
