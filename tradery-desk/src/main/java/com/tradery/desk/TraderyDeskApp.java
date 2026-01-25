package com.tradery.desk;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceLocator;
import com.tradery.dataclient.LiveCandleClient;
import com.tradery.desk.alert.AlertDispatcher;
import com.tradery.desk.feed.CandleAggregator;
import com.tradery.desk.signal.SignalDeduplicator;
import com.tradery.desk.signal.SignalEvaluator;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.DeskStrategyStore;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.desk.strategy.StrategyLibrary;
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
 * Monitors activated strategies and generates alerts when entry/exit
 * conditions are met on live market data.
 *
 * Architecture:
 * - Library: iCloud Drive (~/Library/Mobile Documents/com~apple~CloudDocs/Tradery/)
 *   - Versioned strategies published from Forge
 * - Active: ~/.tradery/desk/active/
 *   - Imported copies of specific versions from library
 * - Historical candles: fetched from data-service (indicator warmup)
 * - Live updates: direct Binance WebSocket connection
 */
public class TraderyDeskApp {

    private static final Logger log = LoggerFactory.getLogger(TraderyDeskApp.class);

    private final DeskConfig config;
    private final DeskStrategyStore strategyStore;
    private final StrategyLibrary library;
    private final StrategyWatcher strategyWatcher;
    private final AlertDispatcher alertDispatcher;
    private final SignalDeduplicator deduplicator;

    // Data service client for historical data
    private DataServiceClient dataClient;

    // Live candle client for real-time updates
    private LiveCandleClient liveClient;

    // Per-strategy components
    private final Map<String, CandleAggregator> aggregators = new HashMap<>();
    private final Map<String, SignalEvaluator> evaluators = new HashMap<>();

    // Chart data for first strategy (to display after UI init)
    private List<Candle> initialChartCandles;
    private String initialChartSymbol;
    private String initialChartTimeframe;

    // UI
    private DeskFrame frame;

    public TraderyDeskApp() {
        this.config = DeskConfig.load();
        this.strategyStore = new DeskStrategyStore();
        this.library = new StrategyLibrary(config);
        this.alertDispatcher = new AlertDispatcher(config);
        this.deduplicator = new SignalDeduplicator();

        // Set up strategy watcher on active folder
        this.strategyWatcher = new StrategyWatcher(this::onStrategyChanged);
    }

    /**
     * Start the application.
     */
    public void start() {
        log.info("Starting Tradery Desk...");

        // Log library status
        if (library.isAvailable()) {
            log.info("Strategy library: {}", config.getLibraryDir());
        } else {
            log.warn("Strategy library not found at {}", config.getLibraryDir());
        }

        // Connect to data service for historical candles
        initDataService();

        // Sync activated strategies from config
        syncActivatedStrategies();

        // Load active strategies
        strategyStore.loadAll();
        List<PublishedStrategy> strategies = strategyStore.getAll();
        log.info("Loaded {} active strategies", strategies.size());

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
     * Sync activated strategies from config - import any missing from library.
     */
    private void syncActivatedStrategies() {
        for (DeskConfig.ActivatedStrategy activation : config.getActivatedStrategies()) {
            // Check if already in active folder
            if (!strategyStore.exists(activation.getId())) {
                // Import from library
                log.info("Importing {} v{} from library", activation.getId(), activation.getVersion());
                library.importToActive(activation.getId(), activation.getVersion());
            }
        }
    }

    /**
     * Initialize connection to data service.
     */
    private void initDataService() {
        try {
            var clientOpt = DataServiceLocator.createClient();
            if (clientOpt.isPresent()) {
                dataClient = clientOpt.get();
                if (dataClient.isHealthy()) {
                    log.info("Connected to data-service");

                    // Create live candle client
                    var serviceInfo = DataServiceLocator.locate();
                    if (serviceInfo.isPresent()) {
                        liveClient = new LiveCandleClient(
                            serviceInfo.get().host(),
                            serviceInfo.get().port(),
                            "tradery-desk-" + UUID.randomUUID().toString().substring(0, 8)
                        );
                        liveClient.connect();
                        log.info("Connected to data-service live stream");
                    }
                } else {
                    log.warn("Data service not healthy, historical data unavailable");
                    dataClient = null;
                }
            } else {
                log.warn("Data service not found, historical data unavailable");
            }
        } catch (Exception e) {
            log.warn("Failed to connect to data-service: {}", e.getMessage());
            dataClient = null;
        }
    }

    /**
     * Fetch historical candles from data service for indicator warmup.
     */
    private List<Candle> fetchHistoricalCandles(String symbol, String timeframe, int bars) {
        if (dataClient == null) {
            log.debug("No data service, skipping historical fetch for {}", symbol);
            return List.of();
        }

        try {
            long now = System.currentTimeMillis();
            long barDurationMs = parseTimeframeMs(timeframe);
            long startTime = now - (bars * barDurationMs);

            log.info("Fetching {} historical candles for {} {}", bars, symbol, timeframe);
            List<Candle> candles = dataClient.getCandles(symbol, timeframe, startTime, now);
            log.info("Fetched {} historical candles from data-service", candles.size());
            return candles;
        } catch (Exception e) {
            log.warn("Failed to fetch historical candles: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse timeframe string to milliseconds.
     */
    private long parseTimeframeMs(String timeframe) {
        if (timeframe == null) return 3600000;

        return switch (timeframe) {
            case "1m" -> 60 * 1000L;
            case "3m" -> 3 * 60 * 1000L;
            case "5m" -> 5 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "30m" -> 30 * 60 * 1000L;
            case "1h" -> 60 * 60 * 1000L;
            case "2h" -> 2 * 60 * 60 * 1000L;
            case "4h" -> 4 * 60 * 60 * 1000L;
            case "6h" -> 6 * 60 * 60 * 1000L;
            case "8h" -> 8 * 60 * 60 * 1000L;
            case "12h" -> 12 * 60 * 60 * 1000L;
            case "1d" -> 24 * 60 * 60 * 1000L;
            case "3d" -> 3 * 24 * 60 * 60 * 1000L;
            case "1w" -> 7 * 24 * 60 * 60 * 1000L;
            case "1M" -> 30 * 24 * 60 * 60 * 1000L;
            default -> 60 * 60 * 1000L;
        };
    }

    /**
     * Initialize components for a strategy.
     */
    private void initializeStrategy(PublishedStrategy strategy) {
        String id = strategy.getId();
        String symbol = strategy.getSymbol();
        String timeframe = strategy.getTimeframe();

        log.info("Initializing strategy: {} v{} ({} {})",
            strategy.getName(), strategy.getVersion(), symbol, timeframe);

        // Create evaluator
        SignalEvaluator evaluator = new SignalEvaluator(strategy);
        evaluators.put(id, evaluator);

        // Create candle aggregator
        CandleAggregator aggregator = new CandleAggregator(symbol, timeframe, config.getHistoryBars());
        aggregator.setOnCandleClose(candle -> onCandleClose(strategy, candle, aggregator));
        aggregator.setOnCandleUpdate(candle -> onCandleUpdate(candle));
        aggregators.put(id, aggregator);

        // Fetch historical candles from data-service for indicator warmup
        List<Candle> history = fetchHistoricalCandles(symbol, timeframe, config.getHistoryBars());
        if (!history.isEmpty()) {
            aggregator.setHistory(history);
            log.info("Warmed up {} with {} historical candles", strategy.getName(), history.size());

            // Store for chart (first strategy only, or update existing)
            if (initialChartCandles == null) {
                initialChartCandles = history;
                initialChartSymbol = symbol;
                initialChartTimeframe = timeframe;
            }

            // Update chart with historical data if UI is ready
            if (frame != null) {
                frame.setChartCandles(history, symbol, timeframe);
            }
        }

        // Subscribe to live candles via data-service
        if (liveClient != null) {
            liveClient.subscribe(symbol, timeframe,
                // On update (incomplete candle)
                candle -> {
                    if (frame != null) {
                        frame.updatePrice(candle.close());
                        frame.updateChartCandle(candle);
                    }
                },
                // On close (complete candle)
                candle -> {
                    aggregator.addClosedCandle(candle);
                    onCandleClose(strategy, candle, aggregator);
                    if (frame != null) {
                        frame.addChartCandle(candle);
                    }
                }
            );
            log.info("Subscribed to live candles for {} {}", symbol, timeframe);

            // Update UI with connection state
            if (frame != null) {
                frame.updateSymbol(symbol, timeframe);
            }
        } else {
            log.warn("No live client, real-time updates unavailable");
        }
    }

    /**
     * Handle live candle update (incomplete candle).
     */
    private void onCandleUpdate(Candle candle) {
        if (frame != null) {
            frame.updatePrice(candle.close());
            frame.updateChartCandle(candle);
        }
    }

    /**
     * Handle candle close event - evaluate signals.
     */
    private void onCandleClose(PublishedStrategy strategy, Candle closedCandle, CandleAggregator aggregator) {
        log.debug("Candle closed for {}: {} @ {}",
            strategy.getId(), closedCandle.timestamp(), closedCandle.close());

        // Update price display and chart
        if (frame != null) {
            frame.updatePrice(closedCandle.close());
            frame.addChartCandle(closedCandle);
        }

        SignalEvaluator evaluator = evaluators.get(strategy.getId());
        if (evaluator == null) {
            return;
        }

        List<Candle> candles = aggregator.getHistory();
        if (candles.isEmpty()) {
            return;
        }

        // Evaluate entry condition
        Optional<SignalEvent> signalOpt = evaluator.evaluateEntry(closedCandle, candles);

        signalOpt.ifPresent(signal -> {
            if (deduplicator.isDuplicateCandle(signal)) {
                log.debug("Duplicate signal ignored for {}", strategy.getId());
                return;
            }

            alertDispatcher.dispatch(signal);

            if (frame != null) {
                frame.addSignal(signal);
            }
        });
    }

    /**
     * Handle strategy file change in active folder.
     */
    private void onStrategyChanged(String strategyId) {
        log.info("Strategy changed: {}", strategyId);

        strategyStore.reloadStrategy(strategyId);
        PublishedStrategy strategy = strategyStore.get(strategyId);

        if (strategy != null) {
            stopStrategy(strategyId);
            initializeStrategy(strategy);
        } else {
            stopStrategy(strategyId);
        }

        if (frame != null) {
            frame.setStrategies(strategyStore.getAll());
        }
    }

    /**
     * Stop components for a strategy.
     */
    private void stopStrategy(String strategyId) {
        CandleAggregator aggregator = aggregators.remove(strategyId);
        if (aggregator != null && liveClient != null) {
            liveClient.unsubscribe(aggregator.getSymbol(), aggregator.getTimeframe(), null, null);
        }
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

            // Set initial chart data if available
            if (initialChartCandles != null && !initialChartCandles.isEmpty()) {
                frame.setChartCandles(initialChartCandles, initialChartSymbol, initialChartTimeframe);
            }

            frame.setVisible(true);
        });
    }

    /**
     * Shutdown the application.
     */
    public void shutdown() {
        log.info("Shutting down Tradery Desk...");

        strategyWatcher.stop();

        // Close live candle client
        if (liveClient != null) {
            liveClient.close();
        }

        aggregators.clear();
        evaluators.clear();

        if (dataClient != null) {
            dataClient.close();
        }

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
            java.nio.file.Files.createDirectories(DeskConfig.ACTIVE_DIR);
        } catch (java.io.IOException e) {
            log.error("Failed to create desk directories: {}", e.getMessage());
        }

        TraderyDeskApp app = new TraderyDeskApp();
        app.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered");
        }));
    }
}
