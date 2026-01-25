package com.tradery.dataclient.page;

import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Represents a computed indicator for the remote indicator page system.
 *
 * Star architecture: IndicatorPage is the center, owning:
 * - Source data page request (candles from RemoteCandlePageManager)
 * - Listener callback for source data changes
 * - Coordination of when to trigger computation
 *
 * When source data is ready or changes, notifies the manager via callback.
 *
 * @param <T> The type of computed data (double[], MACDResult, etc.)
 */
public class IndicatorPage<T> implements DataPageListener<Candle> {

    private static final Logger log = LoggerFactory.getLogger(IndicatorPage.class);

    // Identity
    private final IndicatorType type;
    private final String params;         // e.g., "14" for RSI(14), "12:26:9" for MACD
    private final String symbol;
    private final String timeframe;
    private final long startTime;
    private final long endTime;

    // State
    private volatile PageState state = PageState.EMPTY;
    private volatile String errorMessage;
    private volatile int loadProgress = 0;  // 0-100 percentage

    // Computed data
    private volatile T data;

    // Source dependency tracking
    private volatile String sourceCandleHash;   // Hash of candles used for computation
    private volatile long computeTime;          // When computation finished

    // Source data page (owned by this indicator page)
    private volatile DataPageView<Candle> sourceCandlePage;

    // Source data (cached for coordination)
    private volatile List<Candle> sourceCandles;
    private volatile boolean candlesReady = false;

    // Callback for computation
    private volatile ComputeCallback<T> computeCallback;

    /**
     * Callback interface for triggering computation.
     * Implemented by RemoteIndicatorPageManager.
     */
    public interface ComputeCallback<T> {
        void compute(IndicatorPage<T> page, List<Candle> candles);
        void onError(IndicatorPage<T> page, String errorMessage);
    }

    public IndicatorPage(IndicatorType type, String params, String symbol,
                          String timeframe, long startTime, long endTime) {
        this.type = type;
        this.params = params;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ========== Source Data Acquisition (Star Center) ==========

    /**
     * Request source data from the candle page manager.
     * This is the "allocation" side of the star.
     *
     * @param candlePageMgr Remote candle page manager
     * @param callback      Callback for triggering computation
     */
    public void requestSourceData(RemoteCandlePageManager candlePageMgr,
                                   ComputeCallback<T> callback) {
        this.computeCallback = callback;
        this.state = PageState.LOADING;

        // Request candles - IndicatorPage implements DataPageListener<Candle>
        sourceCandlePage = candlePageMgr.request(
            symbol, timeframe, startTime, endTime,
            this,  // IndicatorPage implements DataPageListener<Candle>
            "IndicatorPage:" + type.getName());
    }

    /**
     * Release source data page.
     * This is the "disposal" side of the star.
     */
    public void releaseSourcePage(RemoteCandlePageManager candlePageMgr) {
        if (sourceCandlePage != null && candlePageMgr != null) {
            candlePageMgr.release(sourceCandlePage, this);
            sourceCandlePage = null;
            sourceCandles = null;
            candlesReady = false;
        }
        computeCallback = null;
    }

    // ========== DataPageListener<Candle> Implementation ==========

    @Override
    public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
        if (newState == PageState.READY) {
            sourceCandles = page.getData();
            candlesReady = true;
            setLoadProgress(90);  // Source data ready, computing...
            // Trigger computation in virtual thread
            Thread.startVirtualThread(this::triggerCompute);
        } else if (newState == PageState.LOADING || newState == PageState.UPDATING) {
            // Propagate source page progress (scale 0-80 for data loading phase)
            int sourceProgress = page.getLoadProgress();
            setLoadProgress((sourceProgress * 80) / 100);  // 0-80% for data loading
        } else if (newState == PageState.ERROR) {
            if (computeCallback != null) {
                computeCallback.onError(this, page.getErrorMessage());
            }
        }
    }

    @Override
    public void onDataChanged(DataPageView<Candle> page) {
        if (page.isReady()) {
            sourceCandles = page.getData();
            // Trigger re-computation in virtual thread
            Thread.startVirtualThread(this::triggerCompute);
        }
    }

    @Override
    public void onProgress(DataPageView<Candle> page, int progress) {
        // Scale source progress to 0-80 range
        setLoadProgress((progress * 80) / 100);
    }

    // ========== Coordination ==========

    /**
     * Trigger computation when data is ready.
     */
    private synchronized void triggerCompute() {
        if (candlesReady && sourceCandles != null && computeCallback != null) {
            computeCallback.compute(this, sourceCandles);
        }
    }

    // ========== Identity ==========

    public IndicatorType getType() {
        return type;
    }

    public String getParams() {
        return params;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * Generate a unique key for deduplication.
     */
    public String getKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(":");
        sb.append(params).append(":");
        sb.append(symbol).append(":");
        if (timeframe != null) {
            sb.append(timeframe).append(":");
        }
        sb.append(startTime).append(":").append(endTime);
        return sb.toString();
    }

    // ========== State ==========

    public PageState getState() {
        return state;
    }

    public void setState(PageState state) {
        this.state = state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getLoadProgress() {
        return loadProgress;
    }

    public void setLoadProgress(int loadProgress) {
        this.loadProgress = Math.max(0, Math.min(100, loadProgress));
    }

    // ========== Data ==========

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean hasData() {
        return data != null;
    }

    // ========== Source Tracking ==========

    public String getSourceCandleHash() {
        return sourceCandleHash;
    }

    public void setSourceCandleHash(String hash) {
        this.sourceCandleHash = hash;
    }

    public long getComputeTime() {
        return computeTime;
    }

    public void setComputeTime(long computeTime) {
        this.computeTime = computeTime;
    }

    /**
     * Check if the computed data is still valid (source hasn't changed).
     */
    public boolean isValid(String currentSourceHash) {
        return sourceCandleHash != null && sourceCandleHash.equals(currentSourceHash);
    }

    // ========== Convenience State Checks ==========

    public boolean isReady() {
        return state == PageState.READY;
    }

    public boolean isLoading() {
        return state == PageState.LOADING;
    }

    public boolean hasError() {
        return state == PageState.ERROR;
    }

    @Override
    public String toString() {
        String tfStr = timeframe != null ? "/" + timeframe : "";
        return "IndicatorPage[" + type.getName() + "(" + params + ") " +
               symbol + tfStr + " " + state + "]";
    }
}
