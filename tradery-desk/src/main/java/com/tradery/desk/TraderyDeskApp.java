package com.tradery.desk;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.core.model.Candle;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceLauncher;
import com.tradery.dataclient.DataServiceLocator;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.PageState;
import com.tradery.dataclient.page.DataServiceConnection;
import com.tradery.dataclient.page.RemoteCandlePageManager;
import com.tradery.desk.alert.AlertDispatcher;
import com.tradery.desk.api.DeskApiServer;
import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.desk.feed.CandleAggregator;
import com.tradery.desk.service.SpotReferenceService;
import com.tradery.desk.signal.SignalDeduplicator;
import com.tradery.desk.signal.SignalEvaluator;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.DeskStrategyStore;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.desk.strategy.StrategyLibrary;
import com.tradery.desk.strategy.StrategyWatcher;
import com.tradery.desk.ui.DeskFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.time.Instant;
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

    // Data service launcher and client
    private DataServiceLauncher dataServiceLauncher;
    private DataServiceClient dataClient;

    // Page-based data system with live sliding window
    private DataServiceConnection pageConnection;
    private RemoteCandlePageManager candlePageMgr;

    // Per-strategy components
    private final Map<String, CandleAggregator> aggregators = new HashMap<>();
    private final Map<String, SignalEvaluator> evaluators = new HashMap<>();

    // Page system: per-strategy candle pages and listeners
    private final Map<String, DataPageView<Candle>> strategyPages = new HashMap<>();
    private final Map<String, DataPageListener<Candle>> strategyPageListeners = new HashMap<>();

    // Chart data for first strategy (to display after UI init)
    private List<Candle> initialChartCandles;
    private String initialChartSymbol;
    private String initialChartTimeframe;

    // Chart-specific page subscription (independent of strategies)
    private String chartSymbol;
    private String chartMarketType = "perp";
    private String chartTimeframe = "1h";
    private DataPageView<Candle> chartPage;
    private DataPageListener<Candle> chartPageListener;

    // API server
    private DeskApiServer apiServer;

    // UI
    private DeskFrame frame;

    // Spot reference price service (for debug comparison)
    private SpotReferenceService spotReferenceService;

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

        // Initialize app context for status window
        DeskAppContext appCtx = DeskAppContext.getInstance();
        appCtx.setStartTime(Instant.now());
        appCtx.setConfig(config);
        appCtx.setAlertDispatcher(alertDispatcher);

        // Log library status
        if (library.isAvailable()) {
            log.info("Strategy library: {}", config.getLibraryDir());
        } else {
            log.warn("Strategy library not found at {}", config.getLibraryDir());
        }

        // Ensure data service is running (start if needed)
        startDataService();

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

        // Update app context with strategy maps
        updateAppContext();

        // Start strategy file watcher
        if (config.isAutoReload()) {
            strategyWatcher.start();
        }

        // Initialize UI
        initializeUI();

        // Start API server
        startApiServer();

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
     * Start data service if not already running.
     */
    private void startDataService() {
        try {
            dataServiceLauncher = new DataServiceLauncher("TraderyDesk");
            int port = dataServiceLauncher.ensureRunning();
            log.info("Data service available on port {}", port);
        } catch (Exception e) {
            log.warn("Could not start data service: {}", e.getMessage());
        }
    }

    /**
     * Initialize connection to data service.
     */
    private void initDataService() {
        initPageSystem();
    }

    /**
     * Initialize the new page-based data system with unified WebSocket connection.
     * Features:
     * - Single WebSocket for page subscriptions and live updates
     * - Reference counting for page cleanup
     * - Progress tracking for data loading
     * - EDT-safe callbacks
     */
    private void initPageSystem() {
        try {
            var serviceInfo = DataServiceLocator.locate();
            if (serviceInfo.isEmpty()) {
                log.warn("Data service not found, page system unavailable");
                return;
            }

            String consumerId = "tradery-desk-" + UUID.randomUUID().toString().substring(0, 8);

            // Create unified WebSocket connection
            pageConnection = new DataServiceConnection(
                serviceInfo.get().host(),
                serviceInfo.get().port(),
                consumerId,
                "TraderyDesk"
            );
            pageConnection.connect();

            // Create HTTP client for fetching page data
            dataClient = new DataServiceClient(serviceInfo.get().host(), serviceInfo.get().port());

            // Create page manager
            candlePageMgr = new RemoteCandlePageManager(pageConnection, dataClient, "TraderyDesk");

            // Monitor connection state and update UI
            pageConnection.addConnectionListener(state -> {
                log.info("Data service connection: {}", state);
                if (frame != null) {
                    var uiState = switch (state) {
                        case CONNECTED -> ConnectionState.CONNECTED;
                        case CONNECTING -> ConnectionState.CONNECTING;
                        case RECONNECTING -> ConnectionState.RECONNECTING;
                        case DISCONNECTED -> ConnectionState.DISCONNECTED;
                    };
                    frame.updateConnectionState(uiState);
                }
            });

            // Update app context
            DeskAppContext.getInstance().setPageConnection(pageConnection);
            DeskAppContext.getInstance().setDataClient(dataClient);
            DeskAppContext.getInstance().setCandlePageManager(candlePageMgr);

            log.info("Initialized page-based data system");
        } catch (Exception e) {
            log.warn("Failed to initialize page system: {}", e.getMessage());
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

        if (candlePageMgr != null) {
            initializeStrategyWithPageSystem(strategy, aggregator);
        } else {
            log.warn("No candle page manager, strategy {} will not receive data", id);
        }
    }

    /**
     * Initialize strategy using the unified page system.
     * Uses live (sliding window) pages for real-time updates.
     */
    private void initializeStrategyWithPageSystem(PublishedStrategy strategy, CandleAggregator aggregator) {
        String id = strategy.getId();
        String symbol = strategy.getSymbol();
        String timeframe = strategy.getTimeframe();

        // Calculate duration for live page (window size in milliseconds)
        long barDurationMs = parseTimeframeMs(timeframe);
        long duration = config.getHistoryBars() * barDurationMs;

        // Create page listener
        DataPageListener<Candle> listener = new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
                log.info("Strategy {} page state: {} -> {}", id, oldState, newState);

                if (newState == PageState.READY) {
                    List<Candle> candles = page.getData();
                    log.info("Strategy {} READY: {} candles, frame={}", id,
                        candles != null ? candles.size() : "null", frame != null);
                    if (candles != null && !candles.isEmpty()) {
                        aggregator.setHistory(candles);
                        log.info("Warmed up {} with {} candles via live page", strategy.getName(), candles.size());

                        // Store for chart (first strategy only)
                        if (initialChartCandles == null) {
                            initialChartCandles = candles;
                            initialChartSymbol = symbol;
                            initialChartTimeframe = timeframe;
                        }

                        // Update chart
                        if (frame != null) {
                            frame.setChartCandles(candles, symbol, timeframe);
                            frame.updateSymbol(symbol, timeframe);
                        }
                    }
                } else if (newState == PageState.ERROR) {
                    log.warn("Strategy {} page error: {}", id, page.getErrorMessage());
                }
            }

            @Override
            public void onLiveUpdate(DataPageView<Candle> page, Candle candle) {
                // Incomplete candle update - just update price display
                if (frame != null) {
                    frame.updatePrice(candle.close(), candle.timestamp());
                    frame.updateChartCandle(candle);
                }
            }

            @Override
            public void onLiveAppend(DataPageView<Candle> page, Candle candle) {
                // New completed candle - update aggregator and evaluate signals
                aggregator.addClosedCandle(candle);
                onCandleClose(strategy, candle, aggregator);

                if (frame != null) {
                    frame.addChartCandle(candle);
                    frame.updatePrice(candle.close(), candle.timestamp());
                }
            }

            @Override
            public void onProgress(DataPageView<Candle> page, int progress) {
                log.trace("Strategy {} loading: {}%", id, progress);
            }
        };

        // Store listener for cleanup
        strategyPageListeners.put(id, listener);

        // Request live page (sliding window, async)
        String marketType = strategy.getBacktestSettings().getSymbolMarket();
        DataPageView<Candle> page = candlePageMgr.requestLive(
            symbol, timeframe, marketType, duration, listener, "Strategy:" + id);
        strategyPages.put(id, page);

        log.info("Strategy {} using live page (duration={}ms)", id, duration);
    }

    /**
     * Handle live candle update (incomplete candle).
     */
    private void onCandleUpdate(Candle candle) {
        if (frame != null) {
            frame.updatePrice(candle.close(), candle.timestamp());
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
            frame.updatePrice(closedCandle.close(), closedCandle.timestamp());
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
            DeskAppContext.getInstance().incrementSignalCount();

            if (frame != null) {
                frame.addSignal(signal);
            }
        });
    }

    /**
     * Update DeskAppContext with current strategy/evaluator/aggregator maps.
     */
    private void updateAppContext() {
        DeskAppContext ctx = DeskAppContext.getInstance();
        ctx.setEvaluators(Collections.unmodifiableMap(evaluators));
        ctx.setAggregators(Collections.unmodifiableMap(aggregators));
        Map<String, PublishedStrategy> strategyMap = new LinkedHashMap<>();
        for (PublishedStrategy s : strategyStore.getAll()) {
            strategyMap.put(s.getId(), s);
        }
        ctx.setStrategies(Collections.unmodifiableMap(strategyMap));
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

        updateAppContext();

        if (frame != null) {
            frame.setStrategies(strategyStore.getAll());
        }
    }

    /**
     * Stop components for a strategy.
     */
    private void stopStrategy(String strategyId) {
        aggregators.remove(strategyId);
        evaluators.remove(strategyId);
        deduplicator.clearStrategy(strategyId);

        // Release page system resources
        DataPageView<Candle> page = strategyPages.remove(strategyId);
        DataPageListener<Candle> listener = strategyPageListeners.remove(strategyId);
        if (page != null && candlePageMgr != null) {
            candlePageMgr.release(page, listener);
        }
    }

    /**
     * Switch the chart to display a different symbol.
     * Creates a new page subscription for the chart independent of strategies.
     */
    private void switchChartSymbol(String newSymbol) {
        // Get market type from UI (spot/perp)
        String marketType = frame != null ? frame.getSelectedMarket() : "perp";
        switchChartSymbol(newSymbol, marketType);
    }

    /**
     * Switch the chart to display a different symbol with specific market type.
     * Creates a new page subscription for the chart independent of strategies.
     */
    private void switchChartSymbol(String newSymbol, String marketType) {
        String mt = marketType != null ? marketType : "perp";

        // Check if anything changed (symbol OR market type)
        boolean symbolChanged = newSymbol != null && !newSymbol.equals(chartSymbol);
        boolean marketChanged = !mt.equals(chartMarketType);

        if (!symbolChanged && !marketChanged) {
            return;
        }

        log.info("Switching chart to symbol: {} ({}) [symbolChanged={}, marketChanged={}]",
            newSymbol, mt, symbolChanged, marketChanged);

        // Release old chart page if exists
        if (chartPage != null && candlePageMgr != null) {
            candlePageMgr.release(chartPage, chartPageListener);
            chartPage = null;
            chartPageListener = null;
        }

        chartSymbol = newSymbol;
        chartMarketType = mt;

        if (candlePageMgr == null) {
            log.warn("Cannot switch symbol: page manager not available");
            return;
        }

        // Calculate duration for chart page
        long barDurationMs = parseTimeframeMs(chartTimeframe);
        long duration = config.getHistoryBars() * barDurationMs;

        // Create new chart page listener
        chartPageListener = new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
                log.info("Chart page state: {} -> {} for {}", oldState, newState, newSymbol);

                if (newState == PageState.READY) {
                    List<Candle> candles = page.getData();
                    if (candles != null && !candles.isEmpty() && frame != null) {
                        SwingUtilities.invokeLater(() -> {
                            frame.setChartCandles(candles, newSymbol, chartTimeframe);
                            frame.updateSymbol(newSymbol, chartTimeframe);
                            // Update price from last candle
                            Candle last = candles.get(candles.size() - 1);
                            frame.updatePrice(last.close(), last.timestamp());
                        });
                    }
                } else if (newState == PageState.ERROR) {
                    log.warn("Chart page error for {}: {}", newSymbol, page.getErrorMessage());
                }
            }

            @Override
            public void onLiveUpdate(DataPageView<Candle> page, Candle candle) {
                if (frame != null) {
                    SwingUtilities.invokeLater(() -> {
                        frame.updatePrice(candle.close(), candle.timestamp());
                        frame.updateChartCandle(candle);
                    });
                }
            }

            @Override
            public void onLiveAppend(DataPageView<Candle> page, Candle candle) {
                if (frame != null) {
                    SwingUtilities.invokeLater(() -> {
                        frame.addChartCandle(candle);
                        frame.updatePrice(candle.close(), candle.timestamp());
                    });
                }
            }

            @Override
            public void onProgress(DataPageView<Candle> page, int progress) {
                log.trace("Chart loading {}: {}%", newSymbol, progress);
            }
        };

        // Request live page for chart with market type
        chartPage = candlePageMgr.requestLive(
            newSymbol, chartTimeframe, mt, duration, chartPageListener, "Chart");

        log.info("Chart subscribed to {} {} ({})", newSymbol, chartTimeframe, mt);
    }

    /**
     * Start the debugging API server.
     */
    private void startApiServer() {
        try {
            apiServer = new DeskApiServer(strategyStore, () -> frame);
            apiServer.start();
        } catch (Exception e) {
            log.warn("Failed to start API server: {}", e.getMessage());
        }
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
                frame.updateSymbol(initialChartSymbol, initialChartTimeframe);
                chartSymbol = initialChartSymbol;
                chartTimeframe = initialChartTimeframe;
            }

            // Wire up symbol change listener
            frame.addSymbolChangeListener(e -> {
                String newSymbol = frame.getSelectedSymbol();
                log.info("Symbol picker changed to: {}", newSymbol);
                switchChartSymbol(newSymbol);
            });

            // Start symbol sync status polling
            if (dataClient != null) {
                frame.startSyncPolling(dataClient);
                log.info("Started symbol sync status polling");
            }

            // Push current connection state to UI (listener may have fired before frame was created)
            if (pageConnection != null) {
                var state = pageConnection.getConnectionState();
                var uiState = switch (state) {
                    case CONNECTED -> ConnectionState.CONNECTED;
                    case CONNECTING -> ConnectionState.CONNECTING;
                    case RECONNECTING -> ConnectionState.RECONNECTING;
                    case DISCONNECTED -> ConnectionState.DISCONNECTED;
                };
                frame.updateConnectionState(uiState);
            }

            frame.setVisible(true);

            // Force chart to respect UI's selected market type
            // (initial data may have come from strategy with different market type)
            String uiSymbol = frame.getSelectedSymbol();
            String uiMarket = frame.getSelectedMarket();
            if (uiSymbol != null && !uiSymbol.isEmpty()) {
                chartMarketType = ""; // Reset to force refresh
                switchChartSymbol(uiSymbol, uiMarket);
            }

            // Start spot reference price service (debug: blue BTCUSDT spot line)
            startSpotReferenceService();
        });
    }

    /**
     * Start the spot reference price service for debug comparison.
     */
    private void startSpotReferenceService() {
        spotReferenceService = new SpotReferenceService();

        // Enable the reference price overlay on the chart
        frame.getPriceChartPanel().setReferencePriceEnabled(true);

        // Wire price updates to the chart
        spotReferenceService.setPriceListener(price -> {
            SwingUtilities.invokeLater(() -> {
                frame.getPriceChartPanel().updateReferencePrice(price);
                // Force chart repaint
                frame.getPriceChartPanel().repaint();
                // Debug: compare with chart price
                var candles = frame.getPriceChartPanel().getDataProvider().getCandles();
                if (!candles.isEmpty()) {
                    double chartPrice = candles.get(candles.size() - 1).close();
                    double diff = price - chartPrice;
                    String pageKey = chartPage != null ? chartPage.getKey() : "no-page";
                    if (Math.abs(diff) > 5) { // Only log if diff > $5
                        log.info("[PRICE-CMP] Spot ref: {}, Chart: {}, Diff: {}, Page: {}",
                            String.format("%.2f", price),
                            String.format("%.2f", chartPrice),
                            String.format("%.2f", diff),
                            pageKey);
                    }
                }
            });
        });

        spotReferenceService.start();
        log.info("Started spot reference price service (BTCUSDT spot)");
    }

    /**
     * Shutdown the application.
     */
    public void shutdown() {
        log.info("Shutting down Tradery Desk...");

        strategyWatcher.stop();

        // Stop spot reference service
        if (spotReferenceService != null) {
            spotReferenceService.stop();
        }

        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }

        // Release chart page
        if (chartPage != null && candlePageMgr != null) {
            candlePageMgr.release(chartPage, chartPageListener);
            chartPage = null;
            chartPageListener = null;
        }

        // Shutdown page system if used
        if (candlePageMgr != null) {
            candlePageMgr.shutdown();
        }
        if (pageConnection != null) {
            pageConnection.disconnect();
        }

        aggregators.clear();
        evaluators.clear();
        strategyPages.clear();
        strategyPageListeners.clear();

        if (dataClient != null) {
            dataClient.close();
        }

        if (dataServiceLauncher != null) {
            dataServiceLauncher.shutdown();
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
