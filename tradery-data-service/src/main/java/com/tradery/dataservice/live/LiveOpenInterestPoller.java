package com.tradery.dataservice.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.core.model.OpenInterestUpdate;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Polls Binance REST API for open interest data every 15 seconds.
 * Computes OI change between polls.
 */
public class LiveOpenInterestPoller {
    private static final Logger LOG = LoggerFactory.getLogger(LiveOpenInterestPoller.class);
    private static final String BINANCE_OI_URL = "https://fapi.binance.com/fapi/v1/openInterest";
    private static final int POLL_INTERVAL_MS = 15_000;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<BiConsumer<String, OpenInterestUpdate>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> previousOi = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pollTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LiveOpenInterestPoller-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public void subscribe(String symbol, BiConsumer<String, OpenInterestUpdate> listener) {
        String key = symbol.toUpperCase();

        listeners.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!pollTasks.containsKey(key)) {
            startPolling(key);
        }
    }

    public void unsubscribe(String symbol, BiConsumer<String, OpenInterestUpdate> listener) {
        String key = symbol.toUpperCase();

        Set<BiConsumer<String, OpenInterestUpdate>> set = listeners.get(key);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                stopPolling(key);
            }
        }
    }

    private void startPolling(String symbol) {
        LOG.info("Starting OI polling for {}", symbol);

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> pollOpenInterest(symbol), 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
        pollTasks.put(symbol, task);
    }

    private void stopPolling(String symbol) {
        ScheduledFuture<?> task = pollTasks.remove(symbol);
        if (task != null) {
            LOG.info("Stopping OI polling for {}", symbol);
            task.cancel(false);
        }
        previousOi.remove(symbol);
        listeners.remove(symbol);
    }

    private void pollOpenInterest(String symbol) {
        try {
            String url = BINANCE_OI_URL + "?symbol=" + symbol;
            Request request = new Request.Builder().url(url).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    LOG.warn("OI poll failed for {}: {}", symbol, response.code());
                    return;
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                double oi = root.get("openInterest").asDouble();
                long timestamp = root.get("time").asLong();

                Double prevOi = previousOi.put(symbol, oi);
                double oiChange = prevOi != null ? oi - prevOi : 0.0;

                OpenInterestUpdate update = new OpenInterestUpdate(timestamp, oi, oiChange);

                Set<BiConsumer<String, OpenInterestUpdate>> set = listeners.get(symbol);
                if (set != null) {
                    for (BiConsumer<String, OpenInterestUpdate> listener : set) {
                        try {
                            listener.accept(symbol, update);
                        } catch (Exception e) {
                            LOG.warn("Error in OI listener: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to poll OI for {}: {}", symbol, e.getMessage());
        }
    }

    public void shutdown() {
        LOG.info("Shutting down LiveOpenInterestPoller");
        scheduler.shutdown();
        for (String key : pollTasks.keySet()) {
            stopPolling(key);
        }
    }
}
