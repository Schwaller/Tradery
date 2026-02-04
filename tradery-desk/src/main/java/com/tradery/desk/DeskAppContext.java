package com.tradery.desk;

import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.page.DataServiceConnection;
import com.tradery.dataclient.page.RemoteCandlePageManager;
import com.tradery.desk.alert.AlertDispatcher;
import com.tradery.desk.feed.CandleAggregator;
import com.tradery.desk.signal.SignalEvaluator;
import com.tradery.desk.strategy.PublishedStrategy;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton exposing app state for the status window.
 */
public class DeskAppContext {

    private static final DeskAppContext INSTANCE = new DeskAppContext();

    public static DeskAppContext getInstance() {
        return INSTANCE;
    }

    private Instant startTime;
    private DeskConfig config;
    private DataServiceConnection pageConnection;
    private DataServiceClient dataClient;
    private RemoteCandlePageManager candlePageManager;
    private AlertDispatcher alertDispatcher;
    private Map<String, SignalEvaluator> evaluators = Collections.emptyMap();
    private Map<String, CandleAggregator> aggregators = Collections.emptyMap();
    private Map<String, PublishedStrategy> strategies = Collections.emptyMap();
    private final AtomicInteger signalCount = new AtomicInteger(0);

    private DeskAppContext() {
    }

    // ========== Setters (called by TraderyDeskApp) ==========

    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setConfig(DeskConfig config) { this.config = config; }
    public void setPageConnection(DataServiceConnection conn) { this.pageConnection = conn; }
    public void setDataClient(DataServiceClient client) { this.dataClient = client; }
    public void setCandlePageManager(RemoteCandlePageManager mgr) { this.candlePageManager = mgr; }
    public void setAlertDispatcher(AlertDispatcher dispatcher) { this.alertDispatcher = dispatcher; }
    public void setEvaluators(Map<String, SignalEvaluator> evaluators) { this.evaluators = evaluators; }
    public void setAggregators(Map<String, CandleAggregator> aggregators) { this.aggregators = aggregators; }
    public void setStrategies(Map<String, PublishedStrategy> strategies) { this.strategies = strategies; }
    public void incrementSignalCount() { signalCount.incrementAndGet(); }

    // ========== Getters ==========

    public Instant getStartTime() { return startTime; }
    public DeskConfig getConfig() { return config; }
    public DataServiceConnection getPageConnection() { return pageConnection; }
    public DataServiceClient getDataClient() { return dataClient; }
    public RemoteCandlePageManager getCandlePageManager() { return candlePageManager; }
    public AlertDispatcher getAlertDispatcher() { return alertDispatcher; }
    public Map<String, SignalEvaluator> getEvaluators() { return evaluators; }
    public Map<String, CandleAggregator> getAggregators() { return aggregators; }
    public Map<String, PublishedStrategy> getStrategies() { return strategies; }
    public int getSignalCount() { return signalCount.get(); }
}
