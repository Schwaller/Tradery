package com.tradery.forge.data.page;

import com.tradery.core.model.Candle;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for OHLCV candle data.
 * Delegates all data loading to the Data Service via WebSocket.
 */
public class CandlePageManager extends DataServicePageManager<Candle> {

    public CandlePageManager() {
        super(DataType.CANDLES, 4,
            "data-service/candles", 88,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, Candle.class)));
    }
}
