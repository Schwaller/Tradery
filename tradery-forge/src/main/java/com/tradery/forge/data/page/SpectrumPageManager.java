package com.tradery.forge.data.page;

import com.tradery.core.model.SpectrumWindow;
import com.tradery.data.page.DataType;

import java.util.List;

/**
 * Page manager for trade size spectrum data.
 * Spectrum doesn't have a timeframe - base resolution is fixed 10s windows.
 * Delegates all data loading to the Data Service via WebSocket.
 */
public class SpectrumPageManager extends DataServicePageManager<SpectrumWindow> {

    public SpectrumPageManager() {
        super(DataType.SPECTRUM, 2,
            "data-service/spectrum", 128,
            (mapper, data) -> mapper.readValue(data,
                mapper.getTypeFactory().constructCollectionType(List.class, SpectrumWindow.class)));
    }
}
