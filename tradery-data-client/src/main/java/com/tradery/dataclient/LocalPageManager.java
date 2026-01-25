package com.tradery.dataclient;

import com.tradery.model.Candle;
import com.tradery.model.AggTrade;
import com.tradery.model.FundingRate;
import com.tradery.model.OpenInterest;
import com.tradery.model.PremiumIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Local page manager that wraps the remote data service.
 * Provides a simpler interface for apps to request and use data.
 */
public class LocalPageManager {
    private static final Logger LOG = LoggerFactory.getLogger(LocalPageManager.class);

    private final String consumerId;
    private final String consumerName;
    private final DataServiceClient client;
    private final PageSubscription subscription;
    private final Map<String, PageState> pageStates = new ConcurrentHashMap<>();

    private volatile boolean connected = false;

    public LocalPageManager(String consumerName) {
        this.consumerId = UUID.randomUUID().toString();
        this.consumerName = consumerName;
        this.client = null;
        this.subscription = null;
    }

    public LocalPageManager(DataServiceClient client, PageSubscription subscription, String consumerName) {
        this.consumerId = UUID.randomUUID().toString();
        this.consumerName = consumerName;
        this.client = client;
        this.subscription = subscription;

        if (subscription != null) {
            subscription.addConnectionListener(state -> {
                connected = state == PageSubscription.ConnectionState.CONNECTED;
            });
        }
    }

    /**
     * Create a LocalPageManager connected to a running data service.
     * @return Optional containing the manager if service is available
     */
    public static Optional<LocalPageManager> create(String consumerName, long timeoutMs) {
        Optional<Integer> port = DataServiceLocator.waitForService(timeoutMs);
        if (port.isEmpty()) {
            LOG.warn("Data service not available");
            return Optional.empty();
        }

        DataServiceClient client = new DataServiceClient("localhost", port.get());
        PageSubscription subscription = new PageSubscription("localhost", port.get(),
            UUID.randomUUID().toString());
        subscription.connect();

        return Optional.of(new LocalPageManager(client, subscription, consumerName));
    }

    /**
     * Check if connected to the data service.
     */
    public boolean isConnected() {
        return connected && client != null && client.isHealthy();
    }

    /**
     * Request candles and wait for them to be ready.
     * @param symbol Trading symbol
     * @param timeframe Candle timeframe
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param timeoutMs Maximum time to wait
     * @return List of candles, or empty if timeout or error
     */
    public List<Candle> getCandles(String symbol, String timeframe, long startTime, long endTime, long timeoutMs) {
        if (client == null) return List.of();

        try {
            DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                "CANDLES", symbol, timeframe, startTime, endTime
            );

            DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
            String pageKey = response.pageKey();

            if (waitForReady(pageKey, timeoutMs)) {
                return client.getCandles(pageKey);
            }
        } catch (IOException e) {
            LOG.error("Failed to get candles", e);
        }

        return List.of();
    }

    /**
     * Request aggregated trades and wait for them to be ready.
     */
    public List<AggTrade> getAggTrades(String symbol, long startTime, long endTime, long timeoutMs) {
        if (client == null) return List.of();

        try {
            DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                "AGGTRADES", symbol, null, startTime, endTime
            );

            DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
            String pageKey = response.pageKey();

            if (waitForReady(pageKey, timeoutMs)) {
                return client.getAggTrades(pageKey);
            }
        } catch (IOException e) {
            LOG.error("Failed to get aggTrades", e);
        }

        return List.of();
    }

    /**
     * Request funding rates and wait for them to be ready.
     */
    public List<FundingRate> getFundingRates(String symbol, long startTime, long endTime, long timeoutMs) {
        if (client == null) return List.of();

        try {
            DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                "FUNDING", symbol, null, startTime, endTime
            );

            DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
            String pageKey = response.pageKey();

            if (waitForReady(pageKey, timeoutMs)) {
                return client.getFundingRates(pageKey);
            }
        } catch (IOException e) {
            LOG.error("Failed to get funding rates", e);
        }

        return List.of();
    }

    /**
     * Request open interest and wait for it to be ready.
     */
    public List<OpenInterest> getOpenInterest(String symbol, long startTime, long endTime, long timeoutMs) {
        if (client == null) return List.of();

        try {
            DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                "OI", symbol, null, startTime, endTime
            );

            DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
            String pageKey = response.pageKey();

            if (waitForReady(pageKey, timeoutMs)) {
                return client.getOpenInterest(pageKey);
            }
        } catch (IOException e) {
            LOG.error("Failed to get open interest", e);
        }

        return List.of();
    }

    /**
     * Request premium index and wait for it to be ready.
     */
    public List<PremiumIndex> getPremiumIndex(String symbol, String timeframe, long startTime, long endTime, long timeoutMs) {
        if (client == null) return List.of();

        try {
            DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                "PREMIUM", symbol, timeframe, startTime, endTime
            );

            DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
            String pageKey = response.pageKey();

            if (waitForReady(pageKey, timeoutMs)) {
                return client.getPremiumIndex(pageKey);
            }
        } catch (IOException e) {
            LOG.error("Failed to get premium index", e);
        }

        return List.of();
    }

    /**
     * Request a page asynchronously with a callback.
     */
    public void requestCandlesAsync(String symbol, String timeframe, long startTime, long endTime,
                                     Consumer<List<Candle>> callback, Consumer<String> errorCallback) {
        if (client == null || subscription == null) {
            errorCallback.accept("Not connected to data service");
            return;
        }

        new Thread(() -> {
            try {
                DataServiceClient.PageRequest request = new DataServiceClient.PageRequest(
                    "CANDLES", symbol, timeframe, startTime, endTime
                );

                DataServiceClient.PageResponse response = client.requestPage(request, consumerId, consumerName);
                String pageKey = response.pageKey();

                // Subscribe for updates
                Consumer<PageSubscription.PageUpdate> updateListener = update -> {
                    if (update instanceof PageSubscription.PageUpdate.DataReady) {
                        try {
                            List<Candle> candles = client.getCandles(pageKey);
                            callback.accept(candles);
                        } catch (IOException e) {
                            errorCallback.accept(e.getMessage());
                        }
                    } else if (update instanceof PageSubscription.PageUpdate.Error error) {
                        errorCallback.accept(error.message());
                    }
                };

                subscription.subscribe(pageKey, updateListener);

                // Check if already ready
                if ("READY".equals(response.state())) {
                    List<Candle> candles = client.getCandles(pageKey);
                    callback.accept(candles);
                }
            } catch (IOException e) {
                errorCallback.accept(e.getMessage());
            }
        }).start();
    }

    /**
     * Release all pages held by this consumer.
     */
    public void releaseAll() {
        for (String pageKey : pageStates.keySet()) {
            try {
                if (client != null) {
                    client.releasePage(pageKey, consumerId);
                }
            } catch (IOException e) {
                LOG.warn("Failed to release page {}", pageKey, e);
            }
        }
        pageStates.clear();
    }

    /**
     * Close the manager and release resources.
     */
    public void close() {
        releaseAll();
        if (subscription != null) {
            subscription.disconnect();
        }
        if (client != null) {
            client.close();
        }
    }

    /**
     * Wait for a page to be ready.
     */
    private boolean waitForReady(String pageKey, long timeoutMs) {
        if (client == null) return false;

        long deadline = System.currentTimeMillis() + timeoutMs;
        int pollInterval = 100;

        while (System.currentTimeMillis() < deadline) {
            try {
                DataServiceClient.PageStatus status = client.getPageStatus(pageKey);
                if (status != null && "READY".equals(status.state())) {
                    return true;
                }
                if (status != null && "ERROR".equals(status.state())) {
                    return false;
                }
                Thread.sleep(pollInterval);
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Internal page state tracking.
     */
    private record PageState(String state, int progress) {}
}
