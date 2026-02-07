package com.tradery.forge.data.page;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataType;
import com.tradery.forge.data.log.DownloadLogStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Parameterized page manager for data types that follow the standard
 * subscribe-page → receive-data pattern via the Data Service.
 *
 * Uses WebSocket push for all data delivery (binary msgpack frames).
 *
 * AggTradesPageManager remains separate (chunked delivery for large datasets).
 *
 * @param <T> The type of data records managed
 */
public class DataServicePageManager<T> extends DataPageManager<T> {

    /**
     * Deserializes msgpack bytes into a typed list.
     * Used when receiving binary data via WS push.
     */
    @FunctionalInterface
    public interface DataDeserializer<T> {
        List<T> deserialize(ObjectMapper msgpackMapper, byte[] data) throws Exception;
    }

    private final DataDeserializer<T> dataDeserializer;
    private final String logEndpoint;
    private final int recordSizeBytes;

    /**
     * @param dataType         The data type enum
     * @param threadPoolSize   Number of concurrent load threads
     * @param logEndpoint      Endpoint name for download log entries
     * @param recordSizeBytes  Estimated bytes per record for memory tracking
     * @param dataDeserializer Function to deserialize msgpack bytes
     */
    public DataServicePageManager(DataType dataType, int threadPoolSize,
                                   String logEndpoint, int recordSizeBytes,
                                   DataDeserializer<T> dataDeserializer) {
        super(dataType, threadPoolSize);
        this.dataDeserializer = dataDeserializer;
        this.logEndpoint = logEndpoint;
        this.recordSizeBytes = recordSizeBytes;
    }

    @Override
    protected void loadData(DataPage<T> page) throws Exception {
        assertNotEDT("DataServicePageManager.loadData");

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        String forgePageKey = page.getKey();

        log.info("{}.loadData: {} {} requesting from data service", dataType.getDisplayName(), symbol,
            timeframe != null ? timeframe : "");

        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null || !ctx.isDataServiceAvailable()) {
            log.error("Data service not available");
            DownloadLogStore.getInstance().logError(forgePageKey, dataType,
                "Data service not available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DataServiceClient client = ctx.getDataServiceClient();
        if (!client.hasActiveConnection()) {
            log.error("No WebSocket connection available for {}", dataType.getDisplayName());
            DownloadLogStore.getInstance().logError(forgePageKey, dataType,
                "No WebSocket connection available");
            updatePageData(page, Collections.emptyList());
            return;
        }

        DownloadLogStore logStore = DownloadLogStore.getInstance();
        loadDataViaWs(page, client, logStore);
    }

    /**
     * Load data via WebSocket push (binary msgpack frames).
     * Subscribes to the page and waits for binary data to be pushed.
     */
    private void loadDataViaWs(DataPage<T> page, DataServiceClient client,
                                DownloadLogStore logStore) throws Exception {
        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        String detail = timeframe != null
            ? String.format("%s/%s %d-%d", symbol, timeframe, startTime, endTime)
            : String.format("%s %d-%d", symbol, startTime, endTime);

        logStore.logApiRequestStarted(forgePageKey, dataType,
            "data-service/ws-subscribe", detail);

        long requestStart = System.currentTimeMillis();

        try {
            CompletableFuture<byte[]> future = client.subscribePage(
                dataType, symbol, timeframe, startTime, endTime,
                new DataServiceClient.DataPageCallback() {
                    @Override
                    public void onStateChanged(String state, int progress) {
                        if ("LOADING".equals(state)) {
                            updatePageProgress(page, Math.min(progress, 95));
                        }
                    }

                    @Override
                    public void onData(byte[] msgpackData, long recordCount) {
                        // Data handled via the future
                    }

                    @Override
                    public void onError(String message) {
                        log.error("WS page error for {}: {}", forgePageKey, message);
                    }
                });

            // Wait for binary data (timeout 5 minutes for large datasets)
            byte[] msgpackData = future.get(5, TimeUnit.MINUTES);

            if (msgpackData != null) {
                List<T> data = dataDeserializer.deserialize(client.getMsgpackMapper(), msgpackData);
                long totalDuration = System.currentTimeMillis() - requestStart;

                log.info("{}.loadData (WS): {} {} got {} records in {}ms",
                    dataType.getDisplayName(), symbol,
                    timeframe != null ? timeframe : "", data.size(), totalDuration);

                logStore.logApiRequestCompleted(forgePageKey, dataType,
                    logEndpoint, data.size(), totalDuration);

                updatePageData(page, data);
            } else {
                log.warn("{}.loadData (WS): null data for {}", dataType.getDisplayName(), forgePageKey);
                updatePageData(page, Collections.emptyList());
            }
        } catch (Exception e) {
            log.error("{}.loadData (WS) failed: {}", dataType.getDisplayName(), e.getMessage());
            client.unsubscribePage(dataType, symbol, timeframe, startTime, endTime);
            logStore.logError(forgePageKey, dataType,
                "WS data load failed: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    @Override
    protected int getRecordSizeBytes() {
        return recordSizeBytes;
    }
}
