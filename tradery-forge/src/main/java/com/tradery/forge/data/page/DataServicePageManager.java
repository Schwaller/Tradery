package com.tradery.forge.data.page;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataType;
import com.tradery.forge.data.log.DownloadLogStore;

import java.util.Collections;
import java.util.List;

/**
 * Parameterized page manager for data types that follow the standard
 * request-page → poll-status → fetch-data pattern via the Data Service.
 *
 * Replaces CandlePageManager, FundingPageManager, OIPageManager, and
 * PremiumPageManager which were near-identical copies of each other.
 *
 * AggTradesPageManager remains separate (genuinely different retry/stall logic).
 *
 * @param <T> The type of data records managed
 */
public class DataServicePageManager<T> extends DataPageManager<T> {

    /**
     * Fetches data from the data service using the page key.
     */
    @FunctionalInterface
    public interface DataFetcher<T> {
        List<T> fetch(DataServiceClient client, String pageKey) throws Exception;
    }

    private final String wireFormat;
    private final DataFetcher<T> dataFetcher;
    private final String logEndpoint;
    private final int recordSizeBytes;

    /**
     * @param dataType       The data type enum
     * @param wireFormat     Wire format string for page requests (e.g. "CANDLES", "FUNDING")
     * @param threadPoolSize Number of concurrent load threads
     * @param dataFetcher    Function to fetch data from the data service
     * @param logEndpoint    Endpoint name for download log entries (e.g. "data-service/candles")
     * @param recordSizeBytes Estimated bytes per record for memory tracking
     */
    public DataServicePageManager(DataType dataType, String wireFormat, int threadPoolSize,
                                   DataFetcher<T> dataFetcher, String logEndpoint,
                                   int recordSizeBytes) {
        super(dataType, threadPoolSize);
        this.wireFormat = wireFormat;
        this.dataFetcher = dataFetcher;
        this.logEndpoint = logEndpoint;
        this.recordSizeBytes = recordSizeBytes;
    }

    public DataServicePageManager(DataType dataType, String wireFormat, int threadPoolSize,
                                   DataFetcher<T> dataFetcher, String logEndpoint) {
        this(dataType, wireFormat, threadPoolSize, dataFetcher, logEndpoint, 64);
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

            log.info("{}.loadData: {} {} got {} records", dataType.getDisplayName(), symbol,
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
