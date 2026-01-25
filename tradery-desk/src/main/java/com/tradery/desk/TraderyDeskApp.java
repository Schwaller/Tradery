package com.tradery.desk;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.desk.alert.AlertDispatcher;
import com.tradery.desk.feed.BinanceWebSocketClient;
import com.tradery.desk.feed.CandleAggregator;
import com.tradery.desk.signal.SignalDeduplicator;
import com.tradery.desk.signal.SignalEvaluator;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.DeskStrategyStore;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.desk.strategy.StrategyWatcher;
import com.tradery.desk.ui.DeskFrame;
import com.tradery.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;

/**
 * Tradery Desk - Real-time signal evaluation and alerting.
 *
 * Monitors published strategies and generates alerts when entry/exit
 * conditions are met on live market data.
 */
public class TraderyDeskApp {

    private static final Logger log = LoggerFactory.getLogger(TraderyDeskApp.class);

    private final DeskConfig config;
    private final DeskStrategyStore strategyStore;
    private final StrategyWatcher strategyWatcher;
    private final AlertDispatcher alertDispatcher;
    private final SignalDeduplicator deduplicator;

    // Per-strategy components
    private final Map<String, BinanceWebSocketClient> wsClients = new HashMap<>();
    private final Map<String, CandleAggregator> aggregators = new HashMap<>();
    private final Map<String, SignalEvaluator> evaluators = new HashMap<>();

    // UI
    private DeskFrame frame;

    public TraderyDeskApp() {
        this.config = DeskConfig.load();
        this.strategyStore = new DeskStrategyStore();
        this.alertDispatcher = new AlertDispatcher(config);
        this.deduplicator = new SignalDeduplicator();

        // Set up strategy watcher with reload callback
        this.strategyWatcher = new StrategyWatcher(
            DeskConfig.STRATEGIES_DIR,
            this::onStrategyChanged
        );
    }

    /**
     * Start the application.
     */
    public void start() {
        log.info("Starting Tradery Desk...");

        // Load strategies
        strategyStore.loadAll();
        List<PublishedStrategy> strategies = strategyStore.getAll();
        log.info("Loaded {} strategies", strategies.size());

        // Initialize evaluators and start WebSocket connections for each strategy
        for (PublishedStrategy strategy : strategies) {
            initializeStrategy(strategy);
        }

        // Start strategy file watcher
        if (config.isAutoReload()) {
            strategyWatcher.start();
        }

        // Initialize UI
        initializeUI();

        // Update UI with strategies
        if (frame != null) {
            frame.setStrategies(strategies);
        }

        log.info("Tradery Desk started");
    }

    /**
     * Initialize components for a strategy.
     */
    private void initializeStrategy(PublishedStrategy strategy) {
        String id = strategy.getId();
        String symbol = strategy.getSymbol();
        String timeframe = strategy.getTimeframe();

        log.info("Initializing strategy: {} ({} {})", strategy.getName(), symbol, timeframe);

        // Create evaluator
        SignalEvaluator evaluator = new SignalEvaluator(strategy);
        evaluators.put(id, evaluator);

        // Create candle aggregator
        CandleAggregator aggregator = new CandleAggregator(symbol, timeframe, config.getHistoryBars());
        aggregator.setOnCandleClose(candle -> onCandleClose(strategy, candle, aggregator));
        aggregators.put(id, aggregator);

        // Create WebSocket client
        BinanceWebSocketClient wsClient = new BinanceWebSocketClient(symbol, timeframe);
        wsClient.setOnMessage(aggregator::processKline);
        wsClient.setOnStateChange(state -> {
            log.debug("{} connection state: {}", symbol, state);
            if (frame != null) {
                frame.updateConnectionState(state);
                frame.updateSymbol(symbol, timeframe);
            }
        });
        wsClient.setOnError((msg, ex) -> log.error("WebSocket error for {}: {}", symbol, msg));
        wsClients.put(id, wsClient);

        // Connect
        wsClient.connect();
    }

    /**
     * Handle candle close event - evaluate signals.
     */
    private void onCandleClose(PublishedStrategy strategy, Candle closedCandle, CandleAggregator aggregator) {
        log.debug("Candle closed for {}: {} @ {}",
            strategy.getId(), closedCandle.timestamp(), closedCandle.close());

        // Update price display
        if (frame != null) {
            frame.updatePrice(closedCandle.close());
        }

        // Get evaluator
        SignalEvaluator evaluator = evaluators.get(strategy.getId());
        if (evaluator == null) {
            return;
        }

        // Get all candles for evaluation
        List<Candle> candles = aggregator.getHistory();
        if (candles.isEmpty()) {
            return;
        }

        // Evaluate entry condition only (signal mode - no position tracking)
        Optional<SignalEvent> signalOpt = evaluator.evaluateEntry(closedCandle, candles);

        signalOpt.ifPresent(signal -> {
            // Check for duplicate (same candle)
            if (deduplicator.isDuplicateCandle(signal)) {
                log.debug("Duplicate signal ignored for {}", strategy.getId());
                return;
            }

            // Dispatch alert
            alertDispatcher.dispatch(signal);

            // Update UI
            if (frame != null) {
                frame.addSignal(signal);
            }
        });
    }

    /**
     * Handle strategy file change.
     */
    private void onStrategyChanged(String strategyId) {
        log.info("Strategy changed: {}", strategyId);

        // Reload the strategy
        strategyStore.reloadStrategy(strategyId);
        PublishedStrategy strategy = strategyStore.get(strategyId);

        if (strategy != null) {
            // Stop existing components
            stopStrategy(strategyId);

            // Re-initialize
            initializeStrategy(strategy);
        } else {
            // Strategy was deleted
            stopStrategy(strategyId);
        }

        // Update UI
        if (frame != null) {
            frame.setStrategies(strategyStore.getAll());
        }
    }

    /**
     * Stop components for a strategy.
     */
    private void stopStrategy(String strategyId) {
        BinanceWebSocketClient ws = wsClients.remove(strategyId);
        if (ws != null) {
            ws.shutdown();
        }
        aggregators.remove(strategyId);
        evaluators.remove(strategyId);
        deduplicator.clearStrategy(strategyId);
    }

    /**
     * Initialize the Swing UI.
     */
    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            log.warn("Failed to set look and feel: {}", e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            frame = new DeskFrame();
            frame.setOnClose(this::shutdown);
            frame.setVisible(true);
        });
    }

    /**
     * Shutdown the application.
     */
    public void shutdown() {
        log.info("Shutting down Tradery Desk...");

        // Stop strategy watcher
        strategyWatcher.stop();

        // Stop all WebSocket connections
        for (BinanceWebSocketClient ws : wsClients.values()) {
            ws.shutdown();
        }
        wsClients.clear();
        aggregators.clear();
        evaluators.clear();

        // Shutdown alert dispatcher
        alertDispatcher.shutdown();

        log.info("Tradery Desk shutdown complete");
        System.exit(0);
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        log.info("Tradery Desk v1.0");

        // Ensure directories exist
        try {
            java.nio.file.Files.createDirectories(DeskConfig.DESK_DIR);
            java.nio.file.Files.createDirectories(DeskConfig.STRATEGIES_DIR);
        } catch (java.io.IOException e) {
            log.error("Failed to create desk directories: {}", e.getMessage());
        }

        TraderyDeskApp app = new TraderyDeskApp();
        app.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered");
        }));
    }
}
