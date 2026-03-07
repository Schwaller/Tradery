package com.tradery.charts.indicator;

import com.tradery.core.indicators.MACD;
import com.tradery.core.indicators.Stochastic;
import com.tradery.core.model.Candle;

import java.util.List;

/**
 * Interface for providing indicator data to shared chart components.
 * Forge implements this with page-based async subscriptions.
 * Desk implements this with direct IndicatorEngine computation.
 */
public interface IndicatorDataProvider {

    // ===== Subscribe methods =====
    // Forge: triggers async page requests
    // Desk: no-op (computed eagerly on setDataContext)

    void subscribeRSI(int period);
    void subscribeMACD(int fast, int slow, int signal);
    void subscribeATR(int period);
    void subscribeADX(int period);
    void subscribePlusDI(int period);
    void subscribeMinusDI(int period);
    void subscribeStochastic(int kPeriod, int dPeriod);
    void subscribeDelta();
    void subscribeCumDelta();
    void subscribeBuyVolume();
    void subscribeSellVolume();
    void subscribeWhaleDelta(double threshold);
    void subscribeRetailDelta(double threshold);
    void subscribeTradeCount();
    void subscribeBuyRatio();
    void subscribeOhlcvDelta();
    void subscribeOhlcvCvd();
    void subscribeRangePosition(int period, int skip);

    // ===== Get methods (return null if not ready) =====

    double[] getRSI(int period);
    MACD.Result getMACD(int fast, int slow, int signal);
    double[] getATR(int period);
    double[] getADX(int period);
    double[] getPlusDI(int period);
    double[] getMinusDI(int period);
    Stochastic.Result getStochastic(int kPeriod, int dPeriod);
    double[] getDelta();
    double[] getCumDelta();
    double[] getBuyVolume();
    double[] getSellVolume();
    double[] getWhaleDelta(double threshold);
    double[] getRetailDelta(double threshold);
    double[] getTradeCount();
    double[] getBuyRatio();
    double[] getOhlcvDelta();
    double[] getOhlcvCvd();
    double[] getRangePosition(int period, int skip);

    List<Candle> getCandles();

    // ===== Data change notification =====

    void addDataListener(Runnable listener);
    void removeDataListener(Runnable listener);

    // ===== Lifecycle =====

    void setDataContext(List<Candle> candles, String symbol, String timeframe, long start, long end);
    void releaseAll();
}
