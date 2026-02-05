package com.tradery.forge.ui.charts;

import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.model.Candle;
import com.tradery.forge.ApplicationContext;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.page.IndicatorPage;
import com.tradery.forge.data.page.IndicatorPageListener;
import com.tradery.forge.data.page.IndicatorPageManager;
import com.tradery.forge.data.page.IndicatorPageManager.HistoricRays;
import com.tradery.forge.data.page.IndicatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Central service for managing indicator data subscriptions.
 *
 * Components register which indicators they need, and this service:
 * 1. Requests indicator pages from IndicatorPageManager
 * 2. Tracks all active subscriptions
 * 3. Notifies listeners when any indicator data changes
 * 4. Provides getters for current indicator values
 *
 * This centralizes indicator data management and ensures all computation
 * happens in background threads, never blocking the EDT.
 */
public class IndicatorDataService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorDataService.class);

    // Current data context
    private String symbol;
    private String timeframe;
    private long startTime;
    private long endTime;
    private List<Candle> candles;

    // Active indicator pages
    private final Map<String, IndicatorPage<?>> pages = new ConcurrentHashMap<>();
    private final Map<String, IndicatorPageListener<?>> listeners = new ConcurrentHashMap<>();

    // Data change listeners
    private final Set<Runnable> dataListeners = new CopyOnWriteArraySet<>();

    // ===== Lifecycle =====

    /**
     * Update the data context. Call this when candles change.
     */
    public void setDataContext(List<Candle> candles, String symbol, String timeframe,
                                long startTime, long endTime) {
        this.candles = candles;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;

        // Re-request all active subscriptions with new context
        refreshSubscriptions();
    }

    /**
     * Add a listener for data changes.
     */
    public void addDataListener(Runnable listener) {
        dataListeners.add(listener);
    }

    /**
     * Remove a data listener.
     */
    public void removeDataListener(Runnable listener) {
        dataListeners.remove(listener);
    }

    /**
     * Release all indicator pages.
     */
    public void releaseAll() {
        IndicatorPageManager pageMgr = getPageManager();
        if (pageMgr == null) return;

        for (Map.Entry<String, IndicatorPage<?>> entry : pages.entrySet()) {
            IndicatorPageListener<?> listener = listeners.get(entry.getKey());
            if (listener != null) {
                releasePageUnchecked(pageMgr, entry.getValue(), listener);
            }
        }
        pages.clear();
        listeners.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> void releasePageUnchecked(IndicatorPageManager mgr, IndicatorPage<?> page,
                                           IndicatorPageListener<?> listener) {
        mgr.release((IndicatorPage<T>) page, (IndicatorPageListener<T>) listener);
    }

    // ===== Subscriptions =====

    /**
     * Subscribe to RSI indicator.
     */
    public void subscribeRSI(int period) {
        subscribe(IndicatorType.RSI, String.valueOf(period));
    }

    /**
     * Subscribe to SMA indicator.
     */
    public void subscribeSMA(int period) {
        subscribe(IndicatorType.SMA, String.valueOf(period));
    }

    /**
     * Subscribe to EMA indicator.
     */
    public void subscribeEMA(int period) {
        subscribe(IndicatorType.EMA, String.valueOf(period));
    }

    /**
     * Subscribe to ATR indicator.
     */
    public void subscribeATR(int period) {
        subscribe(IndicatorType.ATR, String.valueOf(period));
    }

    /**
     * Subscribe to ADX indicator.
     */
    public void subscribeADX(int period) {
        subscribe(IndicatorType.ADX, String.valueOf(period));
    }

    /**
     * Subscribe to +DI indicator.
     */
    public void subscribePlusDI(int period) {
        subscribe(IndicatorType.PLUS_DI, String.valueOf(period));
    }

    /**
     * Subscribe to -DI indicator.
     */
    public void subscribeMinusDI(int period) {
        subscribe(IndicatorType.MINUS_DI, String.valueOf(period));
    }

    /**
     * Subscribe to MACD indicator.
     */
    public void subscribeMACD(int fast, int slow, int signal) {
        subscribe(IndicatorType.MACD, fast + ":" + slow + ":" + signal);
    }

    /**
     * Subscribe to Bollinger Bands.
     */
    public void subscribeBBands(int period, double stdDev) {
        subscribe(IndicatorType.BBANDS, period + ":" + stdDev);
    }

    /**
     * Subscribe to Stochastic.
     */
    public void subscribeStochastic(int kPeriod, int dPeriod) {
        subscribe(IndicatorType.STOCHASTIC, kPeriod + ":" + dPeriod);
    }

    /**
     * Subscribe to resistance rays.
     */
    public void subscribeResistanceRays(int lookback, int skip) {
        subscribe(IndicatorType.RESISTANCE_RAYS, lookback + ":" + skip);
    }

    /**
     * Subscribe to support rays.
     */
    public void subscribeSupportRays(int lookback, int skip) {
        subscribe(IndicatorType.SUPPORT_RAYS, lookback + ":" + skip);
    }

    /**
     * Subscribe to historic rays.
     */
    public void subscribeHistoricRays(int skip, int interval) {
        subscribe(IndicatorType.HISTORIC_RAYS, skip + ":" + interval);
    }

    // ===== Orderflow Subscriptions (aggTrades-based) =====

    /**
     * Subscribe to Delta indicator (buy - sell volume).
     */
    public void subscribeDelta() {
        subscribe(IndicatorType.DELTA, "");
    }

    /**
     * Subscribe to Cumulative Delta indicator.
     */
    public void subscribeCumDelta() {
        subscribe(IndicatorType.CUM_DELTA, "");
    }

    /**
     * Subscribe to Buy Volume indicator (aggTrades).
     */
    public void subscribeBuyVolume() {
        subscribe(IndicatorType.BUY_VOLUME, "");
    }

    /**
     * Subscribe to Sell Volume indicator (aggTrades).
     */
    public void subscribeSellVolume() {
        subscribe(IndicatorType.SELL_VOLUME, "");
    }

    /**
     * Subscribe to Whale Delta indicator (large trades only).
     */
    public void subscribeWhaleDelta(double threshold) {
        subscribe(IndicatorType.WHALE_DELTA, String.valueOf(threshold));
    }

    /**
     * Subscribe to Retail Delta indicator (small trades only).
     */
    public void subscribeRetailDelta(double threshold) {
        subscribe(IndicatorType.RETAIL_DELTA, String.valueOf(threshold));
    }

    // ===== OHLCV-based Subscriptions =====

    /**
     * Subscribe to Trade Count indicator.
     */
    public void subscribeTradeCount() {
        subscribe(IndicatorType.TRADE_COUNT, "");
    }

    /**
     * Subscribe to Buy Ratio indicator.
     */
    public void subscribeBuyRatio() {
        subscribe(IndicatorType.BUY_RATIO, "");
    }

    /**
     * Subscribe to OHLCV Delta indicator.
     */
    public void subscribeOhlcvDelta() {
        subscribe(IndicatorType.OHLCV_DELTA, "");
    }

    /**
     * Subscribe to OHLCV CVD indicator.
     */
    public void subscribeOhlcvCvd() {
        subscribe(IndicatorType.OHLCV_CVD, "");
    }

    /**
     * Subscribe to Range Position indicator.
     */
    public void subscribeRangePosition(int period, int skip) {
        subscribe(IndicatorType.RANGE_POSITION, period + ":" + skip);
    }

    /**
     * Unsubscribe from an indicator.
     */
    public void unsubscribe(IndicatorType type, String params) {
        String key = makeKey(type, params);
        IndicatorPage<?> page = pages.remove(key);
        IndicatorPageListener<?> listener = listeners.remove(key);

        if (page != null && listener != null) {
            IndicatorPageManager pageMgr = getPageManager();
            if (pageMgr != null) {
                releasePageUnchecked(pageMgr, page, listener);
            }
        }
    }

    // ===== Data Access =====

    /**
     * Get RSI values (or null if not ready).
     */
    public double[] getRSI(int period) {
        return getData(IndicatorType.RSI, String.valueOf(period));
    }

    /**
     * Get SMA values (or null if not ready).
     */
    public double[] getSMA(int period) {
        return getData(IndicatorType.SMA, String.valueOf(period));
    }

    /**
     * Get EMA values (or null if not ready).
     */
    public double[] getEMA(int period) {
        return getData(IndicatorType.EMA, String.valueOf(period));
    }

    /**
     * Get ATR values (or null if not ready).
     */
    public double[] getATR(int period) {
        return getData(IndicatorType.ATR, String.valueOf(period));
    }

    /**
     * Get ADX values (or null if not ready).
     */
    public double[] getADX(int period) {
        return getData(IndicatorType.ADX, String.valueOf(period));
    }

    /**
     * Get +DI values (or null if not ready).
     */
    public double[] getPlusDI(int period) {
        return getData(IndicatorType.PLUS_DI, String.valueOf(period));
    }

    /**
     * Get -DI values (or null if not ready).
     */
    public double[] getMinusDI(int period) {
        return getData(IndicatorType.MINUS_DI, String.valueOf(period));
    }

    /**
     * Get MACD result (or null if not ready).
     */
    public com.tradery.core.indicators.MACD.Result getMACD(int fast, int slow, int signal) {
        return getData(IndicatorType.MACD, fast + ":" + slow + ":" + signal);
    }

    /**
     * Get Bollinger Bands result (or null if not ready).
     */
    public com.tradery.core.indicators.BollingerBands.Result getBBands(int period, double stdDev) {
        return getData(IndicatorType.BBANDS, period + ":" + stdDev);
    }

    /**
     * Get Stochastic result (or null if not ready).
     */
    public com.tradery.core.indicators.Stochastic.Result getStochastic(int kPeriod, int dPeriod) {
        return getData(IndicatorType.STOCHASTIC, kPeriod + ":" + dPeriod);
    }

    /**
     * Get resistance rays (or null if not ready).
     */
    public RaySet getResistanceRays(int lookback, int skip) {
        return getData(IndicatorType.RESISTANCE_RAYS, lookback + ":" + skip);
    }

    /**
     * Get support rays (or null if not ready).
     */
    public RaySet getSupportRays(int lookback, int skip) {
        return getData(IndicatorType.SUPPORT_RAYS, lookback + ":" + skip);
    }

    /**
     * Get historic rays (or null if not ready).
     */
    public HistoricRays getHistoricRays(int skip, int interval) {
        return getData(IndicatorType.HISTORIC_RAYS, skip + ":" + interval);
    }

    // ===== Orderflow Data Access (aggTrades-based) =====

    /**
     * Get Delta values (or null if not ready).
     */
    public double[] getDelta() {
        return getData(IndicatorType.DELTA, "");
    }

    /**
     * Get Cumulative Delta values (or null if not ready).
     */
    public double[] getCumDelta() {
        return getData(IndicatorType.CUM_DELTA, "");
    }

    /**
     * Get Buy Volume values (or null if not ready).
     */
    public double[] getBuyVolume() {
        return getData(IndicatorType.BUY_VOLUME, "");
    }

    /**
     * Get Sell Volume values (or null if not ready).
     */
    public double[] getSellVolume() {
        return getData(IndicatorType.SELL_VOLUME, "");
    }

    /**
     * Get Whale Delta values (or null if not ready).
     */
    public double[] getWhaleDelta(double threshold) {
        return getData(IndicatorType.WHALE_DELTA, String.valueOf(threshold));
    }

    /**
     * Get Retail Delta values (or null if not ready).
     */
    public double[] getRetailDelta(double threshold) {
        return getData(IndicatorType.RETAIL_DELTA, String.valueOf(threshold));
    }

    // ===== OHLCV-based Data Access =====

    /**
     * Get Trade Count values (or null if not ready).
     */
    public double[] getTradeCount() {
        return getData(IndicatorType.TRADE_COUNT, "");
    }

    /**
     * Get Buy Ratio values (or null if not ready).
     */
    public double[] getBuyRatio() {
        return getData(IndicatorType.BUY_RATIO, "");
    }

    /**
     * Get OHLCV Delta values (or null if not ready).
     */
    public double[] getOhlcvDelta() {
        return getData(IndicatorType.OHLCV_DELTA, "");
    }

    /**
     * Get OHLCV CVD values (or null if not ready).
     */
    public double[] getOhlcvCvd() {
        return getData(IndicatorType.OHLCV_CVD, "");
    }

    /**
     * Get Range Position values (or null if not ready).
     */
    public double[] getRangePosition(int period, int skip) {
        return getData(IndicatorType.RANGE_POSITION, period + ":" + skip);
    }

    /**
     * Check if an indicator is ready.
     */
    public boolean isReady(IndicatorType type, String params) {
        String key = makeKey(type, params);
        IndicatorPage<?> page = pages.get(key);
        return page != null && page.hasData();
    }

    /**
     * Check if an indicator is loading.
     */
    public boolean isLoading(IndicatorType type, String params) {
        String key = makeKey(type, params);
        IndicatorPage<?> page = pages.get(key);
        return page != null && page.isLoading();
    }

    /**
     * Get current candles.
     */
    public List<Candle> getCandles() {
        return candles;
    }

    // ===== Internal =====

    private void subscribe(IndicatorType type, String params) {
        if (candles == null || candles.isEmpty()) return;

        String key = makeKey(type, params);
        if (pages.containsKey(key)) {
            return; // Already subscribed
        }

        IndicatorPageManager pageMgr = getPageManager();
        if (pageMgr == null) return;

        DataChangeListener listener = new DataChangeListener();
        listeners.put(key, listener);

        IndicatorPage<?> page = pageMgr.request(
            type, params, symbol, timeframe, startTime, endTime, listener, "IndicatorChart");
        pages.put(key, page);

        log.debug("Subscribed to {} with params {}", type, params);
    }

    private void refreshSubscriptions() {
        if (candles == null || candles.isEmpty()) {
            releaseAll();
            return;
        }

        IndicatorPageManager pageMgr = getPageManager();
        if (pageMgr == null) return;

        // Re-request each subscription with new context
        for (Map.Entry<String, IndicatorPage<?>> entry : pages.entrySet()) {
            IndicatorPage<?> oldPage = entry.getValue();
            IndicatorPageListener<?> listener = listeners.get(entry.getKey());

            if (listener != null) {
                // Release old page
                releasePageUnchecked(pageMgr, oldPage, listener);

                // Request new page with updated context
                IndicatorPage<?> newPage = pageMgr.request(
                    oldPage.getType(), oldPage.getParams(),
                    symbol, timeframe, startTime, endTime,
                    (IndicatorPageListener) listener, "IndicatorChart");
                pages.put(entry.getKey(), newPage);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getData(IndicatorType type, String params) {
        String key = makeKey(type, params);
        IndicatorPage<?> page = pages.get(key);
        if (page == null || !page.hasData()) {
            return null;
        }
        return (T) page.getData();
    }

    private String makeKey(IndicatorType type, String params) {
        return type.name() + ":" + params;
    }

    private IndicatorPageManager getPageManager() {
        return ApplicationContext.getInstance().getIndicatorPageManager();
    }

    private void notifyDataListeners() {
        for (Runnable listener : dataListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.warn("Data listener error: {}", e.getMessage());
            }
        }
    }

    /**
     * Listener that notifies when any indicator data changes.
     */
    private class DataChangeListener implements IndicatorPageListener<Object> {
        @Override
        public void onStateChanged(IndicatorPage<Object> page, PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                notifyDataListeners();
            }
        }

        @Override
        public void onDataChanged(IndicatorPage<Object> page) {
            notifyDataListeners();
        }
    }
}
