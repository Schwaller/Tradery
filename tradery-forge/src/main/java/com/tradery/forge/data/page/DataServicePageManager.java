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
 * Uses WebSocket push when available (binary msgpack frames).
 * Falls back to HTTP polling when WS is not connected.
 *
 * AggTradesPageManager remains separate (genuinely different retry/stall logic).
 *
 * @param <T> The type of data records managed
 */
public class DataServicePageManager<T> extends DataPageManager<T> {

    /**
     * Fetches data from the data service using the page key.
     * Used as HTTP fallback when WS binary push is not available.
     */
    @FunctionalInterface
    public interface DataFetcher<T> {
        List<T> fetch(DataServiceClient client, String pageKey) throws Exception;
    }

    /**
     * Deserializes msgpack bytes into a typed list.
     * Used when receiving binary data via WS push.
     */
    @FunctionalInterface
    public interface DataDeserializer<T> {
        List<T> deserialize(ObjectMapper msgpackMapper, byte[] data) throws Exception;
    }

    private final String wireFormat;
    private final DataFetcher<T> dataFetcher;
    private final DataDeserializer<T> dataDeserializer;
    private final String logEndpoint;
    private final int recordSizeBytes;

    /**
     * @param dataType         The data type enum
     * @param wireFormat       Wire format string for page requests (e.g. "CANDLES", "FUNDING")
     * @param threadPoolSize   Number of concurrent load threads
     * @param dataFetcher      Function to fetch data via HTTP (fallback)
     * @param logEndpoint      Endpoint name for download log entries
     * @param recordSizeBytes  Estimated bytes per record for memory tracking
     * @param dataDeserializer Function to deserialize msgpack bytes (for WS push)
     */
    public DataServicePageManager(DataType dataType, String wireFormat, int threadPoolSize,
                                   DataFetcher<T> dataFetcher, String logEndpoint,
                                   int recordSizeBytes, DataDeserializer<T> dataDeserializer) {
        super(dataType, threadPoolSize);
        this.wireFormat = wireFormat;
        this.dataFetcher = dataFetcher;
        this.dataDeserializer = dataDeserializer;
        this.logEndpoint = logEndpoint;
        this.recordSizeBytes = recordSizeBytes;
    }

    public DataServicePageManager(DataType dataType, String wireFormat, int threadPoolSize,
                                   DataFetcher<T> dataFetcher, String logEndpoint,
                                   int recordSizeBytes) {
        this(dataType, wireFormat, threadPoolSize, dataFetcher, logEndpoint, recordSizeBytes, null);
    }

    public DataServicePageManager(DataType dataType, String wireFormat, int threadPoolSize,
                                   DataFetcher<T> dataFetcher, String logEndpoint) {
        this(dataType, wireFormat, threadPoolSize, dataFetcher, logEndpoint, 64, null);
    }

    @Override
    protected void loadData(DataPage<T> page) throws Exception {
        assertNotEDT("DataServicePageManager.loadData");

        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
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
        DownloadLogStore logStore = DownloadLogStore.getInstance();

        // Use WS push path when available and deserializer is configured
        if (client.hasActiveConnection() && dataDeserializer != null) {
            loadDataViaWs(page, client, logStore);
        } else {
            loadDataViaHttp(page, client, logStore);
        }
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
            log.warn("{}.loadData (WS) failed, falling back to HTTP: {}",
                dataType.getDisplayName(), e.getMessage());
            // Unsubscribe to clean up
            client.unsubscribePage(dataType, symbol, timeframe, startTime, endTime);
            // Fall back to HTTP
            loadDataViaHttp(page, client, logStore);
        }
    }

    /**
     * Load data via HTTP polling (original path).
     */
    private void loadDataViaHttp(DataPage<T> page, DataServiceClient client,
                                  DownloadLogStore logStore) throws Exception {
        String symbol = page.getSymbol();
        String timeframe = page.getTimeframe();
        long startTime = page.getStartTime();
        long endTime = page.getEndTime();
        String forgePageKey = page.getKey();

        try {
            String detail = timeframe != null
                ? String.format("%s/%s %d-%d", symbol, timeframe, startTime, endTime)
                : String.format("%s %d-%d", symbol, startTime, endTime);

            logStore.logApiRequestStarted(forgePageKey, dataType,
                "data-service/pages/request", detail);

            long requestStart = System.currentTimeMillis();

            // Request page - data service will fetch if cache is incomplete
            var response = client.requestPage(
                new DataServiceClient.PageRequest(wireFormat, symbol, timeframe, startTime, endTime),
                "app-" + System.currentTimeMillis(),
                dataType.getDisplayName() + "PageManager"
            );

            // Poll for completion
            String dataServicePageKey = response.pageKey();
            int lastProgress = 0;

            while (true) {
                var status = client.getPageStatus(dataServicePageKey);
                if (status == null) {
                    log.error("Page status not found: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, dataType,
                        "Page status not found: " + dataServicePageKey);
                    break;
                }

                if (status.progress() > lastProgress) {
                    lastProgress = status.progress();
                    updatePageProgress(page, Math.min(lastProgress, 95));
                }

                if ("READY".equals(status.state())) {
                    break;
                } else if ("ERROR".equals(status.state())) {
                    log.error("Page load error: {}", dataServicePageKey);
                    logStore.logError(forgePageKey, dataType,
                        "Data service page error: " + dataServicePageKey);
                    break;
                }

                Thread.sleep(100);
            }

            // Fetch final data via page key (same path as desk)
            List<T> data = dataFetcher.fetch(client, dataServicePageKey);
            long totalDuration = System.currentTimeMillis() - requestStart;

            log.info("{}.loadData (HTTP): {} {} got {} records", dataType.getDisplayName(), symbol,
                timeframe != null ? timeframe : "", data.size());

            logStore.logApiRequestCompleted(forgePageKey, dataType,
                logEndpoint, data.size(), totalDuration);

            updatePageData(page, data);

        } catch (Exception e) {
            log.error("Failed to load {} from data service: {}", dataType.getDisplayName(), e.getMessage());
            logStore.logError(forgePageKey, dataType,
                "Failed to load from data service: " + e.getMessage());
            updatePageData(page, Collections.emptyList());
        }
    }

    @Override
    protected int getRecordSizeBytes() {
        return recordSizeBytes;
    }
}
