package com.tradery.dataclient.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.SpectrumWindow;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.DataType;
import com.tradery.data.page.PageKey;
import com.tradery.data.page.PageState;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote page manager for spectrum (trade size distribution) data.
 *
 * Spectrum pages are anchored (fixed start/end), not live.
 * The "timeframe" field in the page key carries the bucket mode (e.g., "raw").
 */
public class RemoteSpectrumPageManager {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteSpectrumPageManager.class);

    private final DataServiceConnection connection;
    private final ObjectMapper msgpackMapper;

    private final Map<String, DataPage<SpectrumWindow>> pages = new ConcurrentHashMap<>();
    private final Map<String, Set<DataPageListener<SpectrumWindow>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();

    public RemoteSpectrumPageManager(DataServiceConnection connection) {
        this.connection = connection;
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
    }

    /**
     * Request spectrum data for a symbol and time range.
     *
     * @param symbol     Trading symbol
     * @param bucketMode Bucket aggregation mode (passed as timeframe in page key)
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @param listener   Listener for state/data changes
     * @param consumer   Consumer name for debugging
     * @return Read-only view of the page
     */
    public DataPageView<SpectrumWindow> request(String symbol, String bucketMode, long startTime, long endTime,
                                                 DataPageListener<SpectrumWindow> listener, String consumer) {
        String key = makeKey(symbol, bucketMode, startTime, endTime);

        DataPage<SpectrumWindow> page = pages.computeIfAbsent(key, k -> {
            DataPage<SpectrumWindow> newPage = new DataPage<>(DataType.SPECTRUM, symbol, bucketMode, startTime, endTime);
            connection.subscribePage(DataType.SPECTRUM, symbol, bucketMode, startTime, endTime,
                createPageCallback(key));
            connection.setPageDataCallback(key, createDataCallback(key));
            return newPage;
        });

        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
            if (page.getState() != PageState.EMPTY) {
                notifyStateOnEDT(page, PageState.EMPTY, page.getState(), listener);
            }
        }

        refCounts.merge(key, 1, Integer::sum);
        LOG.debug("Spectrum page requested: {} (refCount={})", key, refCounts.get(key));
        return page;
    }

    /**
     * Release a spectrum page.
     */
    public void release(DataPageView<SpectrumWindow> pageView, DataPageListener<SpectrumWindow> listener) {
        if (pageView == null) return;
        String key = pageView.getKey();

        if (listener != null) {
            Set<DataPageListener<SpectrumWindow>> ls = listeners.get(key);
            if (ls != null) ls.remove(listener);
        }

        int newCount = refCounts.compute(key, (k, v) -> {
            if (v == null || v <= 1) return null;
            return v - 1;
        }) == null ? 0 : refCounts.getOrDefault(key, 0);

        if (newCount == 0) {
            cleanupPage(key);
        }
    }

    public void shutdown() {
        for (String key : List.copyOf(pages.keySet())) {
            cleanupPage(key);
        }
    }

    // ===== Internal =====

    private DataServiceConnection.PageUpdateCallback createPageCallback(String pageKey) {
        return new DataServiceConnection.PageUpdateCallback() {
            @Override
            public void onStateChanged(String state, int progress) {
                DataPage<SpectrumWindow> page = pages.get(pageKey);
                if (page == null) return;

                PageState newState = parseState(state);
                // Don't propagate READY until data arrives via binary callback
                if (newState == PageState.READY) return;

                PageState oldState = page.getState();
                page.setState(newState);
                if (oldState != newState) {
                    notifyStateOnEDT(page, oldState, newState);
                }
            }

            @Override
            public void onDataReady(long recordCount) {}

            @Override
            public void onError(String message) {
                DataPage<SpectrumWindow> page = pages.get(pageKey);
                if (page == null) return;
                PageState oldState = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage(message);
                notifyStateOnEDT(page, oldState, PageState.ERROR);
            }

            @Override
            public void onEvicted() {
                DataPage<SpectrumWindow> page = pages.get(pageKey);
                if (page == null) return;
                if (refCounts.getOrDefault(pageKey, 0) > 0) {
                    connection.subscribePage(DataType.SPECTRUM, page.getSymbol(), page.getTimeframe(),
                        page.getStartTime(), page.getEndTime(), this);
                }
            }

            @Override public void onLiveUpdate(com.tradery.core.model.Candle candle) {}
            @Override public void onLiveAppend(com.tradery.core.model.Candle candle, List<Long> removed) {}
        };
    }

    private DataServiceConnection.PageDataCallback createDataCallback(String pageKey) {
        return (key, dt, recordCount, msgpackData) -> {
            DataPage<SpectrumWindow> page = pages.get(pageKey);
            if (page == null) return;

            try {
                List<SpectrumWindow> windows = msgpackMapper.readValue(msgpackData,
                    msgpackMapper.getTypeFactory().constructCollectionType(List.class, SpectrumWindow.class));
                page.setData(windows);
                page.setLastSyncTime(System.currentTimeMillis());

                PageState oldState = page.getState();
                if (oldState != PageState.READY) {
                    page.setState(PageState.READY);
                    notifyStateOnEDT(page, oldState, PageState.READY);
                }
            } catch (Exception e) {
                LOG.error("Failed to deserialize spectrum data for {}: {}", pageKey, e.getMessage());
                PageState oldState = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage("Deserialization failed: " + e.getMessage());
                notifyStateOnEDT(page, oldState, PageState.ERROR);
            }

            connection.removePageDataCallback(key);
        };
    }

    private void cleanupPage(String key) {
        DataPage<SpectrumWindow> page = pages.remove(key);
        listeners.remove(key);
        refCounts.remove(key);
        connection.removePageDataCallback(key);
        if (page != null) {
            connection.unsubscribePage(DataType.SPECTRUM, page.getSymbol(), page.getTimeframe(),
                page.getStartTime(), page.getEndTime(), null);
        }
    }

    private void notifyStateOnEDT(DataPage<SpectrumWindow> page, PageState oldState, PageState newState) {
        Set<DataPageListener<SpectrumWindow>> ls = listeners.get(page.getKey());
        if (ls == null || ls.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (DataPageListener<SpectrumWindow> l : ls) {
                try { l.onStateChanged(page, oldState, newState); }
                catch (Exception e) { LOG.warn("Spectrum listener error: {}", e.getMessage()); }
            }
        });
    }

    private void notifyStateOnEDT(DataPage<SpectrumWindow> page, PageState oldState, PageState newState,
                                   DataPageListener<SpectrumWindow> listener) {
        SwingUtilities.invokeLater(() -> {
            try { listener.onStateChanged(page, oldState, newState); }
            catch (Exception e) { LOG.warn("Spectrum listener error: {}", e.getMessage()); }
        });
    }

    private PageState parseState(String state) {
        return switch (state.toUpperCase()) {
            case "PENDING" -> PageState.EMPTY;
            case "LOADING" -> PageState.LOADING;
            case "READY" -> PageState.READY;
            case "ERROR" -> PageState.ERROR;
            case "UPDATING" -> PageState.UPDATING;
            default -> PageState.EMPTY;
        };
    }

    private String makeKey(String symbol, String bucketMode, long startTime, long endTime) {
        return new PageKey(
            DataType.SPECTRUM.toWireFormat(), "binance", symbol.toUpperCase(), bucketMode,
            "perp", endTime, endTime - startTime
        ).toKeyString();
    }
}
