package com.tradery.dataclient.page;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.ProfileEntry;
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
 * Remote page manager for volume profile data.
 * Subscribes to live profile pages from the data service.
 * When new aggTrades arrive, the server retriggers profile computation
 * and pushes updated data via WebSocket.
 *
 * Follows the same pattern as RemoteCandlePageManager.
 */
public class RemoteProfilePageManager {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteProfilePageManager.class);

    private final DataServiceConnection connection;
    private final String consumerName;
    private final ObjectMapper msgpackMapper;

    private final Map<String, DataPage<ProfileEntry>> pages = new ConcurrentHashMap<>();
    private final Map<String, Set<DataPageListener<ProfileEntry>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Integer> refCounts = new ConcurrentHashMap<>();

    public RemoteProfilePageManager(DataServiceConnection connection, String consumerName) {
        this.connection = connection;
        this.consumerName = consumerName;
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
    }

    /**
     * Subscribe to a live profile page. Returns immediately.
     * Listener is notified on EDT when profile data arrives or is updated.
     */
    public DataPageView<ProfileEntry> requestLive(String symbol, String timeframe,
                                                    String marketType, long duration,
                                                    DataPageListener<ProfileEntry> listener) {
        String key = makeLiveKey(symbol, timeframe, marketType, duration);

        DataPage<ProfileEntry> page = pages.computeIfAbsent(key, k -> {
            long now = System.currentTimeMillis();
            DataPage<ProfileEntry> newPage = DataPage.live(
                DataType.VOLUME_PROFILE, symbol, timeframe, marketType,
                now - duration, now, duration);

            connection.subscribeLivePage(DataType.VOLUME_PROFILE, symbol, timeframe,
                marketType, duration, createPageCallback(key));
            connection.setPageDataCallback(key, createDataCallback(key));
            return newPage;
        });

        if (listener != null) {
            listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);
            if (page.getState() != PageState.EMPTY) {
                notifyStateChangedOnEDT(page, PageState.EMPTY, page.getState(), listener);
            }
        }

        refCounts.merge(key, 1, Integer::sum);
        LOG.debug("Live profile page requested: {} (refCount={})", key, refCounts.get(key));
        return page;
    }

    /**
     * Release a profile page subscription.
     */
    public void release(DataPageView<ProfileEntry> pageView, DataPageListener<ProfileEntry> listener) {
        String key = pageView.getKey();

        if (listener != null) {
            Set<DataPageListener<ProfileEntry>> pageListeners = listeners.get(key);
            if (pageListeners != null) pageListeners.remove(listener);
        }

        int newCount = refCounts.compute(key, (k, v) ->
            (v == null || v <= 1) ? null : v - 1) == null ? 0 : refCounts.getOrDefault(key, 0);

        if (newCount == 0) {
            cleanupPage(key);
        }
    }

    public void shutdown() {
        for (String key : List.copyOf(pages.keySet())) {
            cleanupPage(key);
        }
    }

    // ========== Internal ==========

    private DataServiceConnection.PageUpdateCallback createPageCallback(String pageKey) {
        return new DataServiceConnection.PageUpdateCallback() {
            @Override
            public void onStateChanged(String state, int progress) {
                DataPage<ProfileEntry> page = pages.get(pageKey);
                if (page == null) return;

                PageState newState = parseState(state);
                // Don't propagate READY until binary data arrives
                if (newState == PageState.READY) return;

                PageState oldState = page.getState();
                page.setState(newState);
                if (oldState != newState) {
                    notifyStateChangedOnEDT(page, oldState, newState);
                }
            }

            @Override public void onDataReady(long recordCount) {}
            @Override public void onError(String message) {
                DataPage<ProfileEntry> page = pages.get(pageKey);
                if (page == null) return;
                PageState old = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage(message);
                notifyStateChangedOnEDT(page, old, PageState.ERROR);
            }

            @Override public void onEvicted() {
                DataPage<ProfileEntry> page = pages.get(pageKey);
                if (page == null) return;
                if (refCounts.getOrDefault(pageKey, 0) > 0) {
                    connection.subscribeLivePage(DataType.VOLUME_PROFILE,
                        page.getSymbol(), page.getTimeframe(), page.getMarketType(),
                        page.getEndTime() - page.getStartTime(), this);
                }
            }

            @Override public void onLiveUpdate(com.tradery.core.model.Candle candle) {}
            @Override public void onLiveAppend(com.tradery.core.model.Candle candle, List<Long> removed) {}
        };
    }

    private DataServiceConnection.PageDataCallback createDataCallback(String pageKey) {
        return (key, dt, recordCount, msgpackData) -> {
            DataPage<ProfileEntry> page = pages.get(pageKey);
            if (page == null) return;

            try {
                List<ProfileEntry> entries = msgpackMapper.readValue(msgpackData,
                    msgpackMapper.getTypeFactory().constructCollectionType(List.class, ProfileEntry.class));
                page.setData(entries);
                page.setLastSyncTime(System.currentTimeMillis());

                PageState oldState = page.getState();
                if (oldState != PageState.READY) {
                    page.setState(PageState.READY);
                    notifyStateChangedOnEDT(page, oldState, PageState.READY);
                }
                notifyDataChangedOnEDT(page);

                LOG.debug("Profile data received for {}: {} entries", pageKey, entries.size());
            } catch (Exception e) {
                LOG.error("Failed to deserialize profile data for {}: {}", pageKey, e.getMessage());
                PageState old = page.getState();
                page.setState(PageState.ERROR);
                page.setErrorMessage("Deserialize failed: " + e.getMessage());
                notifyStateChangedOnEDT(page, old, PageState.ERROR);
            }

            // Re-register callback for next data push (server retriggers send new binary)
            connection.setPageDataCallback(key, createDataCallback(pageKey));
        };
    }

    private void cleanupPage(String key) {
        DataPage<ProfileEntry> page = pages.remove(key);
        listeners.remove(key);
        refCounts.remove(key);
        connection.removePageDataCallback(key);

        if (page != null) {
            connection.unsubscribePage(DataType.VOLUME_PROFILE, page.getSymbol(),
                page.getTimeframe(), page.getStartTime(), page.getEndTime(), null);
        }
        LOG.debug("Profile page cleaned up: {}", key);
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

    private void notifyStateChangedOnEDT(DataPage<ProfileEntry> page, PageState oldState, PageState newState) {
        Set<DataPageListener<ProfileEntry>> ls = listeners.get(page.getKey());
        if (ls == null || ls.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (var l : ls) {
                try { l.onStateChanged(page, oldState, newState); }
                catch (Exception e) { LOG.warn("Profile listener error", e); }
            }
        });
    }

    private void notifyStateChangedOnEDT(DataPage<ProfileEntry> page, PageState oldState, PageState newState,
                                          DataPageListener<ProfileEntry> listener) {
        SwingUtilities.invokeLater(() -> {
            try { listener.onStateChanged(page, oldState, newState); }
            catch (Exception e) { LOG.warn("Profile listener error", e); }
        });
    }

    private void notifyDataChangedOnEDT(DataPage<ProfileEntry> page) {
        Set<DataPageListener<ProfileEntry>> ls = listeners.get(page.getKey());
        if (ls == null || ls.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (var l : ls) {
                try { l.onDataChanged(page); }
                catch (Exception e) { LOG.warn("Profile listener error", e); }
            }
        });
    }

    private String makeLiveKey(String symbol, String timeframe, String marketType, long duration) {
        return PageKey.liveProfile(symbol, timeframe, marketType, duration).toKeyString();
    }
}
