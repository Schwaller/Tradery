package com.tradery.forge.data.page;

import com.tradery.core.model.AggTrade;
import com.tradery.core.model.Candle;
import com.tradery.data.page.DataPage;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.PageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Represents a computed indicator for the indicator page system.
 *
 * Star architecture: IndicatorPage is the center, owning:
 * - Source data page requests (candles, aggTrades)
 * - Listener callbacks for source data changes
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
    private final String timeframe;      // null for non-timeframe indicators
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

    // Source data pages (owned by this indicator page)
    private volatile DataPageView<Candle> sourceCandlePage;
    private volatile DataPageView<AggTrade> sourceAggTradesPage;

    // Source data (cached for coordination)
    private volatile List<Candle> sourceCandles;
    private volatile List<AggTrade> sourceAggTrades;
    private volatile boolean candlesReady = false;
    private volatile boolean aggTradesReady = false;

    // Callback for computation
    private volatile ComputeCallback<T> computeCallback;

    /**
     * Callback interface for triggering computation.
     * Implemented by IndicatorPageManager.
     */
    public interface ComputeCallback<T> {
        void compute(IndicatorPage<T> page, List<Candle> candles, List<AggTrade> aggTrades);
        void onError(IndicatorPage<T> page, String errorMessage);
    }

    // AggTrades listener (separate because it's a different type)
    // Uses virtual threads to avoid blocking the notification thread
    private final DataPageListener<AggTrade> aggTradesListener = new DataPageListener<>() {
        @Override
        public void onStateChanged(DataPageView<AggTrade> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                sourceAggTrades = page.getData();
                aggTradesReady = true;
                setLoadProgress(90);  // Source data ready, computing...
                // Virtual thread - lightweight, doesn't block notification loop
                Thread.startVirtualThread(() -> checkAndCompute());
            } else if (newState == PageState.LOADING || newState == PageState.UPDATING) {
                // Propagate source page progress
                if (page instanceof DataPage<?> dataPage) {
                    int sourceProgress = dataPage.getLoadProgress();
                    setLoadProgress((sourceProgress * 80) / 100);  // 0-80% for data loading
                }
            } else if (newState == PageState.ERROR) {
                // AggTrades error is not fatal - can fall back to candles
                log.debug("AggTrades not available for {}, will use candle fallback", getKey());
                aggTradesReady = true;
                Thread.startVirtualThread(() -> checkAndCompute());
            }
        }

        @Override
        public void onDataChanged(DataPageView<AggTrade> page) {
            if (page.isReady()) {
                sourceAggTrades = page.getData();
                // Virtual thread - lightweight, doesn't block notification loop
                Thread.startVirtualThread(() -> checkAndCompute());
            }
        }
    };


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
     * Request source data pages based on indicator dependency.
     * This is the "allocation" side of the star.
     *
     * @param candlePageMgr    Candle page manager
     * @param aggTradesPageMgr AggTrades page manager (can be null if not needed)
     * @param callback         Callback for triggering computation
     */
    public void requestSourceData(CandlePageManager candlePageMgr,
                                   AggTradesPageManager aggTradesPageMgr,
                                   ComputeCallback<T> callback) {
        this.computeCallback = callback;
        this.state = PageState.LOADING;

        switch (type.getDependency()) {
            case CANDLES -> {
                // Only need candles
                aggTradesReady = true;  // Mark as "ready" since we don't need it
                sourceCandlePage = candlePageMgr.request(
                    symbol, timeframe, startTime, endTime,
                    this,  // IndicatorPage implements DataPageListener<Candle>
                    "IndicatorPage:" + type.getName());
            }
            case AGG_TRADES -> {
                // Need both candles and aggTrades
                sourceCandlePage = candlePageMgr.request(
                    symbol, timeframe, startTime, endTime,
                    this,
                    "IndicatorPage:" + type.getName());

                sourceAggTradesPage = aggTradesPageMgr.request(
                    symbol, null, startTime, endTime,
                    aggTradesListener,
                    "IndicatorPage:" + type.getName());
            }
            default -> {
                log.warn("Indicator {} dependency {} not supported in page system",
                    type, type.getDependency());
                if (callback != null) {
                    callback.onError(this, "Dependency not supported: " + type.getDependency());
                }
            }
        }
    }

    /**
     * Release source data pages.
     * This is the "disposal" side of the star.
     */
    public void releaseSourcePages(CandlePageManager candlePageMgr, AggTradesPageManager aggTradesPageMgr) {
        if (sourceCandlePage != null && candlePageMgr != null) {
            candlePageMgr.release(sourceCandlePage, this);
            sourceCandlePage = null;
            sourceCandles = null;
            candlesReady = false;
        }
        if (sourceAggTradesPage != null && aggTradesPageMgr != null) {
            aggTradesPageMgr.release(sourceAggTradesPage, aggTradesListener);
            sourceAggTradesPage = null;
            sourceAggTrades = null;
            aggTradesReady = false;
        }
        computeCallback = null;
    }

    // ========== DataPageListener<Candle> Implementation ==========
    // Uses virtual threads to avoid blocking the notification thread

    @Override
    public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
        if (newState == PageState.READY) {
            sourceCandles = page.getData();
            candlesReady = true;
            setLoadProgress(90);  // Source data ready, computing...
            // Virtual thread - lightweight, doesn't block notification loop
            Thread.startVirtualThread(() -> checkAndCompute());
        } else if (newState == PageState.LOADING || newState == PageState.UPDATING) {
            // Propagate source page progress (scale 0-80 for data loading phase)
            if (page instanceof DataPage<?> dataPage) {
                int sourceProgress = dataPage.getLoadProgress();
                setLoadProgress((sourceProgress * 80) / 100);  // 0-80% for data loading
            }
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
            // Virtual thread - lightweight, doesn't block notification loop
            Thread.startVirtualThread(() -> checkAndCompute());
        }
    }

    // ========== Coordination ==========

    /**
     * Check if all required data is ready and trigger computation.
     */
    private synchronized void checkAndCompute() {
        if (candlesReady && aggTradesReady && sourceCandles != null && computeCallback != null) {
            computeCallback.compute(this, sourceCandles, sourceAggTrades);
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
