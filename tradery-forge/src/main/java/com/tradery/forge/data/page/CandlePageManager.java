package com.tradery.forge.data.page;

import com.tradery.core.model.Candle;
import com.tradery.data.page.DataType;

/**
 * Page manager for OHLCV candle data.
 * Delegates all data loading to the Data Service.
 */
public class CandlePageManager extends DataServicePageManager<Candle> {

    public CandlePageManager() {
        super(DataType.CANDLES, "CANDLES", 4,
            (client, pageKey) -> client.getCandles(pageKey),
            "data-service/candles", 88);
    }
}
