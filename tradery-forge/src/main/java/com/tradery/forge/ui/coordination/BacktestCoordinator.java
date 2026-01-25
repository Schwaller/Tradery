package com.tradery.forge.ui.coordination;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.AggTradesStore;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.SubMinuteCandleGenerator;
import com.tradery.forge.data.page.AggTradesPageManager;
import com.tradery.forge.data.page.CandlePageManager;
import com.tradery.forge.data.page.DataPageListener;
import com.tradery.forge.data.page.DataPageView;
import com.tradery.forge.data.page.DataRequirements;
import com.tradery.forge.data.page.FundingPageManager;
import com.tradery.forge.data.page.OIPageManager;
import com.tradery.forge.data.page.PremiumPageManager;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.core.model.*;
import com.tradery.engine.BacktestContext;
import com.tradery.engine.BacktestEngine;
import com.tradery.engine.HoopPatternEvaluator;
import com.tradery.engine.PhaseEvaluator;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.forge.io.HoopPatternStore;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.io.ResultStore;

import javax.swing.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Event-driven backtest coordinator.
 *
 * Key design principles:
 * - NEVER blocks waiting for data
 * - Uses DataPageListener to react to data availability
 * - Triggers backtest automatically when all required data is READY
 * - Clean separation via DataRequirements
 */
public class BacktestCoordinator {

    // Page managers (from ApplicationContext)
    private final CandlePageManager candlePageMgr;
    private final FundingPageManager fundingPageMgr;
    private final OIPageManager oiPageMgr;
    private final AggTradesPageManager aggTradesPageMgr;
    private final PremiumPageManager premiumPageMgr;

    // Engine and stores
    private final BacktestEngine backtestEngine;
    private final SqliteDataStore dataStore;
    private final AggTradesStore aggTradesStore;
    private final ResultStore resultStore;

    // Background executor for backtest computation
    private final ExecutorService backtestExecutor;

    // Current state
    private DataRequirements requirements;
    private Strategy currentStrategy;
    private BacktestConfig currentConfig;
    private List<Phase> currentPhases;
    private volatile boolean backtestRunning = false;

    // Current listeners (stored for proper release)
    private DataPageListener<Candle> candleListener;
    private DataPageListener<AggTrade> aggTradesListener;
    private DataPageListener<FundingRate> fundingListener;
    private DataPageListener<OpenInterest> oiListener;
    private DataPageListener<PremiumIndex> premiumListener;
    private final Map<String, DataPageListener<Candle>> phaseCandleListeners = new HashMap<>();

    // Current data (cached after backtest runs)
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;
    private List<FundingRate> currentFundingRates;
    private List<OpenInterest> currentOpenInterest;
    private List<PremiumIndex> currentPremiumIndex;
    private BacktestResult currentResult;  // Tracks if backtest has completed

    // Callbacks
    private BiConsumer<Integer, String> onProgress;
    private Consumer<BacktestResult> onComplete;
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private BiConsumer<String, String> onDataStatus;
    private Consumer<PageState> onOverallStateChanged;
    private Consumer<String> onViewDataReady;
    private Runnable onBacktestStart;  // Called when new backtest initiated (to clear stale UI)

    // VIEW tier requirements (for charts that need data beyond strategy requirements)
    private boolean viewNeedsAggTrades = false;
    private boolean viewNeedsFunding = false;
    private boolean viewNeedsOI = false;
    private boolean viewNeedsPremium = false;

    public BacktestCoordinator(BacktestEngine backtestEngine,
                               SqliteDataStore dataStore,
                               AggTradesStore aggTradesStore,
                               ResultStore resultStore) {
        this.backtestEngine = backtestEngine;
        this.dataStore = dataStore;
        this.aggTradesStore = aggTradesStore;
        this.resultStore = resultStore;

        // Get page managers from application context
        ApplicationContext ctx = ApplicationContext.getInstance();
        this.candlePageMgr = ctx.getCandlePageManager();
        this.fundingPageMgr = ctx.getFundingPageManager();
        this.oiPageMgr = ctx.getOIPageManager();
        this.aggTradesPageMgr = ctx.getAggTradesPageManager();
        this.premiumPageMgr = ctx.getPremiumPageManager();

        this.backtestExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BacktestCoordinator");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Compatibility constructor that matches old API.
     */
    public BacktestCoordinator(BacktestEngine backtestEngine,
                               SqliteDataStore dataStore,
                               AggTradesStore aggTradesStore,
                               com.tradery.forge.data.FundingRateStore fundingRateStore,
                               com.tradery.forge.data.OpenInterestStore openInterestStore,
                               com.tradery.forge.data.PremiumIndexStore premiumIndexStore,
                               ResultStore resultStore) {
        this(backtestEngine, dataStore, aggTradesStore, resultStore);
        // Note: Individual stores are now managed by page managers
    }

    // ========== Callbacks ==========

    public void setOnProgress(BiConsumer<Integer, String> callback) {
        this.onProgress = callback;
    }

    public void setOnComplete(Consumer<BacktestResult> callback) {
        this.onComplete = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void setOnStatus(Consumer<String> callback) {
        this.onStatus = callback;
    }

    public void setOnDataStatus(BiConsumer<String, String> callback) {
        this.onDataStatus = callback;
    }

    public void setOnOverallStateChanged(Consumer<PageState> callback) {
        this.onOverallStateChanged = callback;
    }

    public void setOnViewDataReady(Consumer<String> callback) {
        this.onViewDataReady = callback;
    }

    public void setOnBacktestStart(Runnable callback) {
        this.onBacktestStart = callback;
    }

    public void setOnDataLoadingProgress(DataLoadingProgressCallback callback) {
        // Not used in event-driven architecture - progress comes via state changes
    }

    /**
     * Set VIEW tier data requirements based on enabled charts.
     * These are loaded in addition to strategy requirements.
     *
     * @param needsAggTrades True if orderflow charts are enabled (Delta, CVD, etc.)
     * @param needsFunding True if Funding chart is enabled
     * @param needsOI True if OI chart is enabled
     * @param needsPremium True if Premium chart is enabled
     */
    public void setViewRequirements(boolean needsAggTrades, boolean needsFunding,
                                     boolean needsOI, boolean needsPremium) {
        this.viewNeedsAggTrades = needsAggTrades;
        this.viewNeedsFunding = needsFunding;
        this.viewNeedsOI = needsOI;
        this.viewNeedsPremium = needsPremium;
    }

    /**
     * Callback for data loading progress updates (compatibility).
     */
    @FunctionalInterface
    public interface DataLoadingProgressCallback {
        void onProgress(String dataType, int loaded, int expected);
    }

    // ========== Data Accessors ==========

    public List<Candle> getCurrentCandles() {
        return currentCandles;
    }

    public List<AggTrade> getCurrentAggTrades() {
        return currentAggTrades;
    }

    public List<FundingRate> getCurrentFundingRates() {
        return currentFundingRates;
    }

    public List<OpenInterest> getCurrentOpenInterest() {
        return currentOpenInterest;
    }

    public List<PremiumIndex> getCurrentPremiumIndex() {
        return currentPremiumIndex;
    }

    public IndicatorEngine getIndicatorEngine() {
        return backtestEngine.getIndicatorEngine();
    }

    public AggTradesStore getAggTradesStore() {
        return aggTradesStore;
    }

    /**
     * Refresh view-only data from page and push to IndicatorEngine.
     * Call this when view-only data becomes ready AFTER backtest completion.
     *
     * @param dataType The data type ("OI", "Funding", "AggTrades", "Premium")
     */
    public void refreshViewData(String dataType) {
        if (requirements == null) return;

        IndicatorEngine engine = backtestEngine.getIndicatorEngine();
        if (engine == null) return;

        switch (dataType) {
            case "OI" -> {
                if (requirements.getOiPage() != null) {
                    currentOpenInterest = new ArrayList<>(requirements.getOiPage().getData());
                    backtestEngine.setOpenInterest(currentOpenInterest);
                }
            }
            case "Funding" -> {
                if (requirements.getFundingPage() != null) {
                    currentFundingRates = new ArrayList<>(requirements.getFundingPage().getData());
                    backtestEngine.setFundingRates(currentFundingRates);
                }
            }
            case "AggTrades" -> {
                if (requirements.getAggTradesPage() != null) {
                    currentAggTrades = new ArrayList<>(requirements.getAggTradesPage().getData());
                    backtestEngine.setAggTrades(currentAggTrades);
                    // Also set on indicator engine for VIEW tier charts
                    engine.setAggTrades(currentAggTrades);
                }
            }
            case "Premium" -> {
                if (requirements.getPremiumPage() != null) {
                    currentPremiumIndex = new ArrayList<>(requirements.getPremiumPage().getData());
                    backtestEngine.setPremiumIndex(currentPremiumIndex);
                }
            }
        }
    }

    // ========== Main API ==========

    /**
     * Run a backtest (compatibility method).
     * Converts duration to start/end times and delegates to requestBacktest.
     */
    public void runBacktest(Strategy strategy, String symbol, String resolution,
                            long durationMillis, double capital) {
        runBacktest(strategy, symbol, resolution, durationMillis, capital, null);
    }

    /**
     * Run a backtest (compatibility method with anchor date).
     * Converts duration to start/end times and delegates to requestBacktest.
     */
    public void runBacktest(Strategy strategy, String symbol, String resolution,
                            long durationMillis, double capital, Long anchorDate) {
        long endTime = (anchorDate != null) ? anchorDate : System.currentTimeMillis();
        long startTime = endTime - durationMillis;
        requestBacktest(strategy, symbol, resolution, startTime, endTime, capital);
    }

    /**
     * Request a backtest. Does NOT block.
     *
     * The backtest will run automatically when all required data is ready.
     *
     * @param strategy The strategy to backtest
     * @param symbol   Trading symbol
     * @param timeframe Timeframe
     * @param startTime Start time in milliseconds
     * @param endTime   End time in milliseconds
     * @param capital   Initial capital
     */
    public void requestBacktest(Strategy strategy, String symbol, String timeframe,
                                 long startTime, long endTime, double capital) {

        // Release any previous pages
        releaseCurrentPages();

        // Clear previous result - new backtest starting
        this.currentResult = null;

        // Notify UI to clear stale data (trades, charts) before new backtest
        if (onBacktestStart != null) {
            SwingUtilities.invokeLater(onBacktestStart);
        }

        this.currentStrategy = strategy;
        this.requirements = new DataRequirements(symbol, timeframe, startTime, endTime);

        // Build config
        this.currentConfig = new BacktestConfig(
            symbol,
            timeframe,
            startTime,
            endTime,
            capital,
            strategy.getPositionSizingType(),
            strategy.getPositionSizingValue(),
            strategy.getTotalCommission(),
            strategy.getMarketType(),
            strategy.getMarginInterestHourly()
        );

        // Load phases (synchronous, they're small)
        this.currentPhases = loadPhases(strategy);

        reportProgress(0, "Loading data...");

        // Analyze strategy requirements vs view-only requirements
        // Strategy requirements block backtest; view-only requirements don't
        boolean strategyNeedsAggTrades = strategy.requiresAggTrades() ||
                                         SubMinuteCandleGenerator.parseSubMinuteInterval(timeframe) > 0;
        boolean strategyNeedsFunding = strategy.requiresFunding();
        boolean strategyNeedsOI = strategy.requiresOpenInterest();
        boolean strategyNeedsPremium = strategy.requiresPremium();

        // Combined needs (strategy + view)
        boolean needsAggTrades = strategyNeedsAggTrades || viewNeedsAggTrades;
        boolean needsFunding = strategyNeedsFunding || viewNeedsFunding;
        boolean needsOI = strategyNeedsOI || viewNeedsOI;
        boolean needsPremium = strategyNeedsPremium || viewNeedsPremium;

        // Request candles (always required)
        reportDataStatus("Candles", "loading");
        candleListener = createCandleListener();
        DataPageView<Candle> candlePage = candlePageMgr.request(
            symbol, timeframe, startTime, endTime, candleListener, "BacktestCoordinator");
        requirements.setCandlePage(candlePage);

        // Request optional data - viewOnly flag means it won't block backtest
        if (needsAggTrades) {
            boolean viewOnly = !strategyNeedsAggTrades;  // Only view needs it
            reportDataStatus("AggTrades", viewOnly ? "loading (view)" : "loading");
            aggTradesListener = createAggTradesListener();
            DataPageView<AggTrade> aggTradesPage = aggTradesPageMgr.request(
                symbol, null, startTime, endTime, aggTradesListener, "BacktestCoordinator");
            requirements.setAggTradesPage(aggTradesPage, viewOnly);
        }

        if (needsFunding) {
            boolean viewOnly = !strategyNeedsFunding;
            reportDataStatus("Funding", viewOnly ? "loading (view)" : "loading");
            fundingListener = createFundingListener();
            DataPageView<FundingRate> fundingPage = fundingPageMgr.request(
                symbol, null, startTime, endTime, fundingListener, "BacktestCoordinator");
            requirements.setFundingPage(fundingPage, viewOnly);
        }

        if (needsOI) {
            // OI limited to 30 days from Binance API
            long now = System.currentTimeMillis();
            long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
            long oiStartTime = Math.max(startTime, now - maxOiHistory);

            if (oiStartTime < endTime) {
                boolean viewOnly = !strategyNeedsOI;
                reportDataStatus("OI", viewOnly ? "loading (view)" : "loading");
                oiListener = createOIListener();
                DataPageView<OpenInterest> oiPage = oiPageMgr.request(
                    symbol, null, oiStartTime, endTime, oiListener, "BacktestCoordinator");
                requirements.setOiPage(oiPage, viewOnly);
            }
        }

        if (needsPremium) {
            boolean viewOnly = !strategyNeedsPremium;
            reportDataStatus("Premium", viewOnly ? "loading (view)" : "loading");
            premiumListener = createPremiumListener();
            DataPageView<PremiumIndex> premiumPage = premiumPageMgr.request(
                symbol, timeframe, startTime, endTime, premiumListener, "BacktestCoordinator");
            requirements.setPremiumPage(premiumPage, viewOnly);
        }

        // Request candles for phase timeframes (may differ from strategy timeframe)
        requestPhaseCandlePages(startTime, endTime);

        // Check if already ready (from cache)
        checkAndTriggerBacktest();
    }

    /**
     * Request candle pages for all phase timeframes.
     * Phases may use different timeframes than the main strategy.
     */
    private void requestPhaseCandlePages(long startTime, long endTime) {
        if (currentPhases == null || currentPhases.isEmpty()) {
            return;
        }

        // Collect unique symbol:timeframe combinations from phases
        Set<String> phaseKeys = new HashSet<>();
        for (Phase phase : currentPhases) {
            String key = phase.getSymbol() + ":" + phase.getTimeframe();
            phaseKeys.add(key);
        }

        // Request candles for each unique combination
        for (String key : phaseKeys) {
            String[] parts = key.split(":");
            String phaseSymbol = parts[0];
            String phaseTimeframe = parts[1];

            // Calculate warmup for this phase (using max indicator period of 200 as buffer)
            long warmupMs = getTimeframeMs(phaseTimeframe) * 200;
            long phaseStartTime = startTime - warmupMs;

            reportDataStatus("Phase:" + phaseTimeframe, "loading");
            DataPageListener<Candle> listener = createPhaseCandleListener(key);
            phaseCandleListeners.put(key, listener);

            DataPageView<Candle> phasePage = candlePageMgr.request(
                phaseSymbol, phaseTimeframe, phaseStartTime, endTime, listener, "Phase:" + phaseTimeframe);
            requirements.addPhaseCandlePage(key, phasePage);
        }
    }

    /**
     * Create a listener for phase candle data.
     */
    private DataPageListener<Candle> createPhaseCandleListener(String phaseKey) {
        return new DataPageListener<>() {
            @Override
            public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
                if (newState == PageState.READY) {
                    reportDataStatus("Phase:" + phaseKey.split(":")[1], "ready");
                    checkAndTriggerBacktest();
                } else if (newState == PageState.ERROR) {
                    reportDataStatus("Phase:" + phaseKey.split(":")[1], "error");
                }
            }

            @Override
            public void onDataChanged(DataPageView<Candle> page) {
                // Phase candles updated
            }
        };
    }

    /**
     * Cancel the current backtest request.
     */
    public void cancel() {
        releaseCurrentPages();
        requirements = null;
        currentStrategy = null;
        currentConfig = null;
        currentResult = null;
        backtestRunning = false;
    }

    /**
     * Check if a backtest is currently running.
     */
    public boolean isRunning() {
        return backtestRunning;
    }

    /**
     * Get the current data requirements.
     */
    public DataRequirements getRequirements() {
        return requirements;
    }

    // ========== Internal State Change Handling ==========

    /**
     * Handle state change from any data page.
     */
    private void handleStateChanged(String dataTypeName, PageState newState) {
        // Report to UI
        String status = switch (newState) {
            case EMPTY, LOADING -> "loading";
            case READY, UPDATING -> "ready";
            case ERROR -> "error";
        };
        reportDataStatus(dataTypeName, status);

        // If view-only data becomes ready AFTER backtest has run, notify for chart refresh
        if (newState == PageState.READY && !backtestRunning && currentResult != null) {
            boolean isViewOnly = isViewOnlyData(dataTypeName);
            if (isViewOnly && onViewDataReady != null) {
                SwingUtilities.invokeLater(() -> onViewDataReady.accept(dataTypeName));
            }
        }

        // Check if we can trigger backtest
        checkAndTriggerBacktest();
    }

    /**
     * Check if a data type is view-only (not required for backtest).
     */
    private boolean isViewOnlyData(String dataTypeName) {
        if (requirements == null) return false;
        return switch (dataTypeName) {
            case "AggTrades" -> requirements.getAggTradesPage() != null &&
                               !requirements.isAggTradesRequired();
            case "Funding" -> requirements.getFundingPage() != null &&
                             !requirements.isFundingRequired();
            case "OI" -> requirements.getOiPage() != null &&
                        !requirements.isOiRequired();
            case "Premium" -> requirements.getPremiumPage() != null &&
                             !requirements.isPremiumRequired();
            default -> false;
        };
    }

    /**
     * Handle data change from any data page.
     */
    private void handleDataChanged() {
        // Data updated - check if we should trigger
        checkAndTriggerBacktest();
    }

    // ========== Internal Methods ==========

    /**
     * Check if all data is ready and trigger backtest if so.
     */
    private void checkAndTriggerBacktest() {
        if (requirements == null) return;
        if (backtestRunning) return;

        PageState overall = requirements.getOverallState();

        // Notify overall state
        if (onOverallStateChanged != null) {
            SwingUtilities.invokeLater(() -> onOverallStateChanged.accept(overall));
        }

        if (requirements.hasError()) {
            String errorMsg = requirements.getErrorMessage();
            reportError(errorMsg != null ? errorMsg : "Data loading failed");
            return;
        }

        if (requirements.isReady()) {
            triggerBacktestInBackground();
        }
    }

    /**
     * Run the backtest in a background thread.
     */
    private void triggerBacktestInBackground() {
        backtestRunning = true;
        reportProgress(10, "Running backtest...");

        backtestExecutor.submit(() -> {
            try {
                // Gather data from pages (already loaded, instant)
                List<Candle> candles = new ArrayList<>(requirements.getCandlePage().getData());
                List<AggTrade> aggTrades = requirements.getAggTradesPage() != null
                    ? new ArrayList<>(requirements.getAggTradesPage().getData()) : null;
                List<FundingRate> funding = requirements.getFundingPage() != null
                    ? new ArrayList<>(requirements.getFundingPage().getData()) : null;
                List<OpenInterest> oi = requirements.getOiPage() != null
                    ? new ArrayList<>(requirements.getOiPage().getData()) : null;
                List<PremiumIndex> premium = requirements.getPremiumPage() != null
                    ? new ArrayList<>(requirements.getPremiumPage().getData()) : null;

                // Store current data for later access
                currentCandles = candles;
                currentAggTrades = aggTrades;
                currentFundingRates = funding;
                currentOpenInterest = oi;
                currentPremiumIndex = premium;

                // Pre-compute phase states using engine's stateless evaluator
                Map<String, boolean[]> phaseStates = new HashMap<>();
                if (currentPhases != null && !currentPhases.isEmpty()) {
                    PhaseEvaluator phaseEvaluator = new PhaseEvaluator();
                    phaseStates = phaseEvaluator.evaluatePhases(
                        currentPhases, candles, currentConfig.resolution(),
                        requirements.getPhaseCandles()
                    );
                }

                // Pre-compute hoop pattern states if strategy uses any
                Map<String, boolean[]> hoopPatternStates = new HashMap<>();
                List<HoopPattern> hoopPatterns = new ArrayList<>();
                HoopPatternSettings hoopSettings = currentStrategy.getHoopPatternSettings();
                if (hoopSettings.hasAnyPatterns()) {
                    // Collect all needed pattern IDs
                    Set<String> neededPatternIds = new HashSet<>();
                    neededPatternIds.addAll(hoopSettings.getRequiredEntryPatternIds());
                    neededPatternIds.addAll(hoopSettings.getExcludedEntryPatternIds());
                    neededPatternIds.addAll(hoopSettings.getRequiredExitPatternIds());
                    neededPatternIds.addAll(hoopSettings.getExcludedExitPatternIds());

                    // Load patterns from store
                    java.io.File hoopsDir = new java.io.File(System.getProperty("user.home"), ".tradery/hoops");
                    HoopPatternStore hoopStore = new HoopPatternStore(hoopsDir);
                    hoopPatterns = hoopStore.loadByIds(neededPatternIds);

                    // Pre-compute pattern states
                    HoopPatternEvaluator hoopEvaluator = new HoopPatternEvaluator();
                    hoopPatternStates = hoopEvaluator.evaluatePatterns(
                        hoopPatterns, candles, currentConfig.resolution(),
                        requirements.getPhaseCandles()  // Reuse same candle map for patterns
                    );
                }

                // Build BacktestContext with all pre-computed data
                BacktestContext context = BacktestContext.builder(candles)
                    .phaseStates(phaseStates)
                    .hoopPatternStates(hoopPatternStates)
                    .hoopPatterns(hoopPatterns)
                    .aggTrades(aggTrades)
                    .fundingRates(funding)
                    .openInterest(oi)
                    .premiumIndex(premium)
                    .build();

                // Run backtest using engine's clean context-based API
                BacktestResult result = backtestEngine.run(
                    currentStrategy, currentConfig, context,
                    progress -> SwingUtilities.invokeLater(() ->
                        reportProgress(progress.percentage(), progress.message())));

                // Store result so we can track completion for view-only data notifications
                currentResult = result;

                // Notify completion on EDT
                SwingUtilities.invokeLater(() -> {
                    backtestRunning = false;

                    if (onComplete != null) {
                        onComplete.accept(result);
                    }

                    reportProgress(100, "Complete");
                    reportStatus(result.getSummary());

                    // Report warnings
                    if (result.hasWarnings()) {
                        for (String warning : result.warnings()) {
                            reportStatus("Warning: " + warning);
                        }
                    }
                });

                // Save result in background
                CompletableFuture.runAsync(() -> {
                    try {
                        resultStore.save(result);
                    } catch (Exception e) {
                        System.err.println("Failed to save result: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                currentResult = null;  // Failed - no result
                SwingUtilities.invokeLater(() -> {
                    backtestRunning = false;
                    reportError(e.getMessage());
                });
            }
        });
    }

    /**
     * Release all currently held pages.
     */
    private void releaseCurrentPages() {
        if (requirements == null) return;

        if (requirements.getCandlePage() != null) {
            candlePageMgr.release(requirements.getCandlePage(), candleListener);
        }
        if (requirements.getAggTradesPage() != null) {
            aggTradesPageMgr.release(requirements.getAggTradesPage(), aggTradesListener);
        }
        if (requirements.getFundingPage() != null) {
            fundingPageMgr.release(requirements.getFundingPage(), fundingListener);
        }
        if (requirements.getOiPage() != null) {
            oiPageMgr.release(requirements.getOiPage(), oiListener);
        }
        if (requirements.getPremiumPage() != null) {
            premiumPageMgr.release(requirements.getPremiumPage(), premiumListener);
        }

        // Release phase candle pages
        for (Map.Entry<String, DataPageView<Candle>> entry : requirements.getPhaseCandlePages().entrySet()) {
            DataPageListener<Candle> listener = phaseCandleListeners.get(entry.getKey());
            candlePageMgr.release(entry.getValue(), listener);
        }

        // Clear stored listeners
        candleListener = null;
        aggTradesListener = null;
        fundingListener = null;
        oiListener = null;
        premiumListener = null;
        phaseCandleListeners.clear();
    }

    /**
     * Load phases for the strategy.
     */
    private List<Phase> loadPhases(Strategy strategy) {
        List<Phase> allPhases = new ArrayList<>();
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();

        Set<String> phaseIds = new HashSet<>();
        phaseIds.addAll(strategy.getRequiredPhaseIds());
        phaseIds.addAll(strategy.getExcludedPhaseIds());

        for (ExitZone zone : strategy.getExitZones()) {
            phaseIds.addAll(zone.requiredPhaseIds());
            phaseIds.addAll(zone.excludedPhaseIds());
        }

        for (String phaseId : phaseIds) {
            Phase phase = phaseStore.load(phaseId);
            if (phase != null) {
                allPhases.add(phase);
            }
        }

        return allPhases;
    }

    // ========== Typed Listener Factories ==========

    private DataPageListener<Candle> createCandleListener() {
        return new DataPageListener<Candle>() {
            @Override
            public void onStateChanged(DataPageView<Candle> page, PageState oldState, PageState newState) {
                handleStateChanged("Candles", newState);
            }
            @Override
            public void onDataChanged(DataPageView<Candle> page) {
                handleDataChanged();
            }
        };
    }

    private DataPageListener<AggTrade> createAggTradesListener() {
        return new DataPageListener<AggTrade>() {
            @Override
            public void onStateChanged(DataPageView<AggTrade> page, PageState oldState, PageState newState) {
                handleStateChanged("AggTrades", newState);
            }
            @Override
            public void onDataChanged(DataPageView<AggTrade> page) {
                handleDataChanged();
            }
        };
    }

    private DataPageListener<FundingRate> createFundingListener() {
        return new DataPageListener<FundingRate>() {
            @Override
            public void onStateChanged(DataPageView<FundingRate> page, PageState oldState, PageState newState) {
                handleStateChanged("Funding", newState);
            }
            @Override
            public void onDataChanged(DataPageView<FundingRate> page) {
                handleDataChanged();
            }
        };
    }

    private DataPageListener<OpenInterest> createOIListener() {
        return new DataPageListener<OpenInterest>() {
            @Override
            public void onStateChanged(DataPageView<OpenInterest> page, PageState oldState, PageState newState) {
                handleStateChanged("OI", newState);
            }
            @Override
            public void onDataChanged(DataPageView<OpenInterest> page) {
                handleDataChanged();
            }
        };
    }

    private DataPageListener<PremiumIndex> createPremiumListener() {
        return new DataPageListener<PremiumIndex>() {
            @Override
            public void onStateChanged(DataPageView<PremiumIndex> page, PageState oldState, PageState newState) {
                handleStateChanged("Premium", newState);
            }
            @Override
            public void onDataChanged(DataPageView<PremiumIndex> page) {
                handleDataChanged();
            }
        };
    }

    // ========== Reporting ==========

    private void reportProgress(int percentage, String message) {
        if (onProgress != null) {
            onProgress.accept(percentage, message);
        }
    }

    private void reportStatus(String message) {
        if (onStatus != null) {
            onStatus.accept(message);
        }
    }

    private void reportDataStatus(String dataType, String status) {
        if (onDataStatus != null) {
            SwingUtilities.invokeLater(() -> onDataStatus.accept(dataType, status));
        }
    }

    private void reportError(String message) {
        reportProgress(0, "Error");
        reportStatus("Error: " + message);
        if (onError != null) {
            onError.accept(message);
        }
    }

    /**
     * Shutdown the coordinator.
     */
    public void shutdown() {
        releaseCurrentPages();
        backtestExecutor.shutdown();
    }

    // ========== Static Utilities ==========

    /**
     * Parse duration string to milliseconds.
     */
    public static long parseDurationMillis(String duration) {
        if (duration == null) return 365L * 24 * 60 * 60 * 1000; // Default 1 year

        long hour = 60L * 60 * 1000;
        long day = 24 * hour;
        return switch (duration) {
            case "1 hour" -> hour;
            case "3 hours" -> 3 * hour;
            case "6 hours" -> 6 * hour;
            case "12 hours" -> 12 * hour;
            case "1 day" -> day;
            case "3 days" -> 3 * day;
            case "1 week" -> 7 * day;
            case "2 weeks" -> 14 * day;
            case "4 weeks" -> 28 * day;
            case "1 month" -> 30 * day;
            case "2 months" -> 60 * day;
            case "3 months" -> 90 * day;
            case "6 months" -> 180 * day;
            case "1 year" -> 365 * day;
            case "2 years" -> 730 * day;
            case "3 years" -> 1095 * day;
            case "5 years" -> 1825 * day;
            case "10 years" -> 3650 * day;
            default -> 365 * day;
        };
    }

    /**
     * Convert timeframe string to milliseconds.
     */
    private static long getTimeframeMs(String timeframe) {
        if (timeframe == null) return 60 * 60 * 1000L; // Default 1h

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
}
