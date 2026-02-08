package com.tradery.forge.data.page;

import com.tradery.core.model.FearGreedIndex;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for Fear & Greed Index data.
 * Daily data - no timeframe needed.
 * Delegates all data loading to the Data Service via WebSocket.
 */
public class FearGreedPageManager extends DataServicePageManager<FearGreedIndex> {

    public FearGreedPageManager() {
        super(DataType.FEAR_GREED, 2,
            "data-service/fear-greed", 64,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, FearGreedIndex.class)));
    }
}
