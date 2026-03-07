package com.tradery.desk.ui.charts;

import com.tradery.charts.indicator.IndicatorDataProvider;
import com.tradery.core.indicators.Indicators;
import com.tradery.core.indicators.MACD;
import com.tradery.core.indicators.Stochastic;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Desk implementation of {@link IndicatorDataProvider}.
 * Delegates to {@link DeskDataProvider}'s engine for all indicator computations.
 *
 * <p>Unlike forge's page-based async system, desk computes indicators eagerly
 * via IndicatorEngine. All subscribe methods are no-ops since data is available
 * immediately after the engine is updated.</p>
 */
public class DeskIndicatorDataProvider implements IndicatorDataProvider {

    private final DeskDataProvider deskData;
    private final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    public DeskIndicatorDataProvider(DeskDataProvider deskData) {
        this.deskData = deskData;
    }

    // ===== Subscribe methods (all no-ops for desk) =====

    @Override public void subscribeRSI(int period) {}
    @Override public void subscribeMACD(int fast, int slow, int signal) {}
    @Override public void subscribeATR(int period) {}
    @Override public void subscribeADX(int period) {}
    @Override public void subscribePlusDI(int period) {}
    @Override public void subscribeMinusDI(int period) {}
    @Override public void subscribeStochastic(int kPeriod, int dPeriod) {}
    @Override public void subscribeDelta() {}
    @Override public void subscribeCumDelta() {}
    @Override public void subscribeBuyVolume() {}
    @Override public void subscribeSellVolume() {}
    @Override public void subscribeWhaleDelta(double threshold) {}
    @Override public void subscribeRetailDelta(double threshold) {}
    @Override public void subscribeTradeCount() {}
    @Override public void subscribeBuyRatio() {}
    @Override public void subscribeOhlcvDelta() {}
    @Override public void subscribeOhlcvCvd() {}
    @Override public void subscribeRangePosition(int period, int skip) {}

    // ===== Get methods (delegate to DeskDataProvider's engine) =====

    @Override
    public double[] getRSI(int period) {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getRSI(period) : null;
    }

    @Override
    public MACD.Result getMACD(int fast, int slow, int signal) {
        IndicatorEngine e = deskData.getEngine();
        if (e == null) return null;
        Indicators.MACDResult r = e.getMACD(fast, slow, signal);
        return r != null ? new MACD.Result(r.line(), r.signal(), r.histogram()) : null;
    }

    @Override
    public double[] getATR(int period) {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getATR(period) : null;
    }

    @Override
    public double[] getADX(int period) {
        IndicatorEngine e = deskData.getEngine();
        if (e == null) return null;
        Indicators.ADXResult r = e.getADX(period);
        return r != null ? r.adx() : null;
    }

    @Override
    public double[] getPlusDI(int period) {
        IndicatorEngine e = deskData.getEngine();
        if (e == null) return null;
        Indicators.ADXResult r = e.getADX(period);
        return r != null ? r.plusDI() : null;
    }

    @Override
    public double[] getMinusDI(int period) {
        IndicatorEngine e = deskData.getEngine();
        if (e == null) return null;
        Indicators.ADXResult r = e.getADX(period);
        return r != null ? r.minusDI() : null;
    }

    @Override
    public Stochastic.Result getStochastic(int kPeriod, int dPeriod) {
        IndicatorEngine e = deskData.getEngine();
        if (e == null) return null;
        Indicators.StochasticResult r = e.getStochastic(kPeriod, dPeriod);
        return r != null ? new Stochastic.Result(r.k(), r.d()) : null;
    }

    // Desk has no aggTrades — orderflow indicators return null
    @Override public double[] getDelta() { return null; }
    @Override public double[] getCumDelta() { return null; }
    @Override public double[] getBuyVolume() { return null; }
    @Override public double[] getSellVolume() { return null; }
    @Override public double[] getWhaleDelta(double threshold) { return null; }
    @Override public double[] getRetailDelta(double threshold) { return null; }

    // OHLCV-based indicators (available from candle data)

    @Override
    public double[] getTradeCount() {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getTradeCount() : null;
    }

    @Override
    public double[] getBuyRatio() {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getBuyRatio() : null;
    }

    @Override
    public double[] getOhlcvDelta() {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getOhlcvDelta() : null;
    }

    @Override
    public double[] getOhlcvCvd() {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getOhlcvCvd() : null;
    }

    @Override
    public double[] getRangePosition(int period, int skip) {
        IndicatorEngine e = deskData.getEngine();
        return e != null ? e.getRangePosition(period, skip) : null;
    }

    @Override
    public List<Candle> getCandles() {
        return deskData.getCandles();
    }

    // ===== Data change notification =====

    @Override
    public void addDataListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeDataListener(Runnable listener) {
        listeners.remove(listener);
    }

    // ===== Lifecycle =====

    @Override
    public void setDataContext(List<Candle> candles, String symbol, String timeframe, long start, long end) {
        // DeskDataProvider already manages candles and engine.
        // Just notify listeners so charts redraw with fresh engine data.
        notifyListeners();
    }

    @Override
    public void releaseAll() {
        // DeskDataProvider owns the engine — nothing to release here.
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
