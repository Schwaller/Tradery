package com.tradery.ui.coordination;

import com.tradery.ApplicationContext;
import com.tradery.data.AggTradesStore;
import com.tradery.data.PageState;
import com.tradery.data.SubMinuteCandleGenerator;
import com.tradery.data.page.AggTradesPageManager;
import com.tradery.data.page.CandlePageManager;
import com.tradery.data.page.DataPageListener;
import com.tradery.data.page.DataPageView;
import com.tradery.data.page.DataRequirements;
import com.tradery.data.page.FundingPageManager;
import com.tradery.data.page.OIPageManager;
import com.tradery.data.page.PremiumPageManager;
import com.tradery.data.sqlite.SqliteDataStore;
import com.tradery.model.*;
import com.tradery.engine.BacktestEngine;
import com.tradery.indicators.IndicatorEngine;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;

import javax.swing.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    // Current data (cached after backtest runs)
    private List<Candle> currentCandles;
    private List<AggTrade> currentAggTrades;
    private List<FundingRate> currentFundingRates;
    private List<OpenInterest> currentOpenInterest;
    private List<PremiumIndex> currentPremiumIndex;

    // Callbacks
    private BiConsumer<Integer, String> onProgress;
    private Consumer<BacktestResult> onComplete;
    private Consumer<String> onError;
    private Consumer<String> onStatus;
    private BiConsumer<String, String> onDataStatus;
    private Consumer<PageState> onOverallStateChanged;
    private Consumer<String> onViewDataReady;

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
                               com.tradery.data.FundingRateStore fundingRateStore,
                               com.tradery.data.OpenInterestStore openInterestStore,
                               com.tradery.data.PremiumIndexStore premiumIndexStore,
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

    public void setOnDataLoadingProgress(DataLoadingProgressCallback callback) {
        // Not used in event-driven architecture - progress comes via state changes
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
            strategy.getTotalCommission()
        );

        // Load phases (synchronous, they're small)
        this.currentPhases = loadPhases(strategy);

        reportProgress(0, "Loading data...");

        // Analyze strategy to determine required data
        boolean needsAggTrades = strategy.requiresAggTrades() ||
                                 SubMinuteCandleGenerator.parseSubMinuteInterval(timeframe) > 0;
        boolean needsFunding = strategy.requiresFunding();
        boolean needsOI = strategy.requiresOpenInterest();
        boolean needsPremium = strategy.requiresPremium();

        // Request candles (always required)
        reportDataStatus("Candles", "loading");
        candleListener = createCandleListener();
        DataPageView<Candle> candlePage = candlePageMgr.request(
            symbol, timeframe, startTime, endTime, candleListener);
        requirements.setCandlePage(candlePage);

        // Request optional data based on strategy requirements
        if (needsAggTrades) {
            reportDataStatus("AggTrades", "loading");
            aggTradesListener = createAggTradesListener();
            DataPageView<AggTrade> aggTradesPage = aggTradesPageMgr.request(
                symbol, null, startTime, endTime, aggTradesListener);
            requirements.setAggTradesPage(aggTradesPage);
        }

        if (needsFunding) {
            reportDataStatus("Funding", "loading");
            fundingListener = createFundingListener();
            DataPageView<FundingRate> fundingPage = fundingPageMgr.request(
                symbol, null, startTime, endTime, fundingListener);
            requirements.setFundingPage(fundingPage);
        }

        if (needsOI) {
            // OI limited to 30 days from Binance API
            long now = System.currentTimeMillis();
            long maxOiHistory = 30L * 24 * 60 * 60 * 1000;
            long oiStartTime = Math.max(startTime, now - maxOiHistory);

            if (oiStartTime < endTime) {
                reportDataStatus("OI", "loading");
                oiListener = createOIListener();
                DataPageView<OpenInterest> oiPage = oiPageMgr.request(
                    symbol, null, oiStartTime, endTime, oiListener);
                requirements.setOiPage(oiPage);
            }
        }

        if (needsPremium) {
            reportDataStatus("Premium", "loading");
            premiumListener = createPremiumListener();
            DataPageView<PremiumIndex> premiumPage = premiumPageMgr.request(
                symbol, timeframe, startTime, endTime, premiumListener);
            requirements.setPremiumPage(premiumPage);
        }

        // Check if already ready (from cache)
        checkAndTriggerBacktest();
    }

    /**
     * Cancel the current backtest request.
     */
    public void cancel() {
        releaseCurrentPages();
        requirements = null;
        currentStrategy = null;
        currentConfig = null;
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

        // Check if we can trigger backtest
        checkAndTriggerBacktest();
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

                // Set data in engine
                backtestEngine.setAggTrades(aggTrades);
                backtestEngine.setFundingRates(funding);
                backtestEngine.setOpenInterest(oi);
                backtestEngine.setPremiumIndex(premium);

                // Run backtest
                BacktestResult result = backtestEngine.run(
                    currentStrategy, currentConfig, candles, currentPhases,
                    progress -> SwingUtilities.invokeLater(() ->
                        reportProgress(progress.percentage(), progress.message())));

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

        // Clear stored listeners
        candleListener = null;
        aggTradesListener = null;
        fundingListener = null;
        oiListener = null;
        premiumListener = null;
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
}
