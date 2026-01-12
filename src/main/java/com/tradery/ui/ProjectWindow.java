package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.FileWatcher;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.io.WindowStateStore;
import com.tradery.model.*;
import com.tradery.ui.charts.ChartConfig;
import com.tradery.ui.controls.IndicatorControlsPanel;
import com.tradery.ui.coordination.AutoSaveScheduler;
import com.tradery.ui.coordination.BacktestCoordinator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Window for editing and backtesting a single strategy/project.
 * Each strategy opens in its own ProjectWindow.
 */
public class ProjectWindow extends JFrame {

    private Strategy strategy;
    private final Consumer<String> onClose;

    // Panels
    private StrategyEditorPanel editorPanel;
    private BacktestSettingsPanel settingsPanel;
    private ChartsPanel chartPanel;
    private MetricsPanel metricsPanel;
    private TradeTablePanel tradeTablePanel;
    private JLabel statusBar;
    private JProgressBar statusProgressBar;
    private JProgressBar dataLoadingProgressBar;
    private JLabel dataLoadingLabel;
    private JLabel titleLabel;

    // Toolbar controls
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JComboBox<String> durationCombo;
    private JToggleButton fitWidthBtn;
    private JToggleButton fixedWidthBtn;
    private JToggleButton fitYBtn;
    private JToggleButton fullYBtn;
    private JButton clearCacheBtn;
    private JButton claudeBtn;
    private JButton codexBtn;
    private JButton historyBtn;
    private JButton phaseAnalysisBtn;
    private JPanel dataStatusPanel;
    private JLabel ohlcStatusLabel;
    private JLabel aggTradesStatusLabel;
    private JLabel fundingStatusLabel;
    private JLabel oiStatusLabel;

    // Phase analysis window
    private PhaseAnalysisWindow phaseAnalysisWindow;
    private BacktestResult currentResult;

    // Data loading status window
    private DataLoadingStatusWindow dataLoadingStatusWindow;

    // Indicator controls panel (extracted)
    private IndicatorControlsPanel indicatorControls;

    // Embedded AI terminal (for Claude/Codex)
    private JSplitPane editorTerminalSplit;  // Vertical split: editor | terminal (left side)
    private AiTerminalController aiTerminalController;

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "10s", "15s", "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    // Data stores
    private final StrategyStore strategyStore;
    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private FileWatcher fileWatcher;
    private FileWatcher phaseWatcher;

    // Extracted coordinators
    private final BacktestCoordinator backtestCoordinator;
    private final AutoSaveScheduler autoSaveScheduler;

    // Listener references for cleanup
    private Runnable themeChangeListener;
    private Runnable chartConfigChangeListener;

    public ProjectWindow(Strategy strategy, Consumer<String> onClose) {
        super(strategy.getName() + " - " + TraderyApp.APP_NAME);
        this.strategy = strategy;
        this.onClose = onClose;

        // Use shared stores from ApplicationContext
        this.strategyStore = ApplicationContext.getInstance().getStrategyStore();
        this.candleStore = ApplicationContext.getInstance().getCandleStore();
        this.aggTradesStore = ApplicationContext.getInstance().getAggTradesStore();

        // Initialize coordinators
        ResultStore resultStore = new ResultStore(strategy.getId());
        BacktestEngine backtestEngine = new BacktestEngine(candleStore);
        com.tradery.data.FundingRateStore fundingRateStore = new com.tradery.data.FundingRateStore();
        com.tradery.data.OpenInterestStore openInterestStore = new com.tradery.data.OpenInterestStore();
        this.backtestCoordinator = new BacktestCoordinator(backtestEngine, candleStore, aggTradesStore, fundingRateStore, openInterestStore, resultStore);
        this.autoSaveScheduler = new AutoSaveScheduler();

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadStrategyData();
        startFileWatcher();

        // Run backtest on startup (after window is shown)
        SwingUtilities.invokeLater(this::runBacktest);
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));

        // Restore saved position or center on screen
        Rectangle savedBounds = WindowStateStore.getInstance().getProjectBounds(strategy.getId());
        if (savedBounds != null) {
            setBounds(savedBounds);
        } else {
            setLocationRelativeTo(null);
        }

        // Integrated title bar look
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });

        // Save position on move/resize
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveWindowState();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                saveWindowState();
            }
        });
    }

    private void saveWindowState() {
        if (strategy != null && isVisible()) {
            WindowStateStore.getInstance().saveProjectBounds(strategy.getId(), getBounds());
        }
    }

    private void initializeComponents() {
        editorPanel = new StrategyEditorPanel();
        settingsPanel = new BacktestSettingsPanel();
        chartPanel = new ChartsPanel();
        metricsPanel = new MetricsPanel();
        tradeTablePanel = new TradeTablePanel();

        // Wire up chart status callback
        chartPanel.setOnStatusUpdate(this::setStatus);

        // Wire up theme change listener
        themeChangeListener = chartPanel::refreshTheme;
        com.tradery.ui.theme.ThemeManager.getInstance().addThemeChangeListener(themeChangeListener);

        // Wire up trade table hover/select to chart highlight
        tradeTablePanel.setOnTradeHover(chartPanel::highlightTrades);
        tradeTablePanel.setOnTradeSelect(chartPanel::highlightTrades);

        // Toolbar controls
        symbolCombo = new JComboBox<>(SYMBOLS);
        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        timeframeCombo.addActionListener(e -> updateDurationOptions());

        durationCombo = new JComboBox<>();
        updateDurationOptions();

        // Width toggle group (Fit / Fixed)
        fitWidthBtn = new JToggleButton("Fit");
        fixedWidthBtn = new JToggleButton("Fixed");
        ButtonGroup widthGroup = new ButtonGroup();
        widthGroup.add(fitWidthBtn);
        widthGroup.add(fixedWidthBtn);
        fitWidthBtn.setSelected(true);
        fitWidthBtn.addActionListener(e -> chartPanel.setFixedWidthMode(false));
        fixedWidthBtn.addActionListener(e -> chartPanel.setFixedWidthMode(true));

        // Y-axis toggle group (Fit / Full)
        fitYBtn = new JToggleButton("Fit Y");
        fullYBtn = new JToggleButton("Full Y");
        ButtonGroup yGroup = new ButtonGroup();
        yGroup.add(fitYBtn);
        yGroup.add(fullYBtn);
        fitYBtn.setSelected(true);
        fitYBtn.addActionListener(e -> chartPanel.setFitYAxisToVisibleData(true));
        fullYBtn.addActionListener(e -> chartPanel.setFitYAxisToVisibleData(false));

        // Sync data button
        clearCacheBtn = new JButton("Sync Data");
        clearCacheBtn.setToolTipText("Fetch latest OHLC data from Binance");
        clearCacheBtn.addActionListener(e -> clearCacheAndReload());

        // Initialize AI terminal controller
        aiTerminalController = new AiTerminalController(this, this::runBacktest, this::setStatus);

        // Claude button - opens terminal with Claude CLI
        claudeBtn = new JButton("Claude");
        claudeBtn.setToolTipText("Open Claude CLI to help optimize this strategy");
        claudeBtn.addActionListener(e -> aiTerminalController.openClaudeTerminal(
            strategy.getId(), strategy.getName(),
            (String) symbolCombo.getSelectedItem(),
            (String) timeframeCombo.getSelectedItem(),
            (String) durationCombo.getSelectedItem()
        ));

        // Codex button - opens terminal with Codex CLI
        codexBtn = new JButton("Codex");
        codexBtn.setToolTipText("Open Codex CLI to help optimize this strategy");
        codexBtn.addActionListener(e -> aiTerminalController.openCodexTerminal(
            strategy.getId(), strategy.getName(),
            (String) symbolCombo.getSelectedItem(),
            (String) timeframeCombo.getSelectedItem(),
            (String) durationCombo.getSelectedItem()
        ));

        // History button - browse and restore previous versions
        historyBtn = new JButton("History");
        historyBtn.setToolTipText("Browse strategy history and restore previous versions");
        historyBtn.addActionListener(e -> showHistory());

        // Phase Analysis button - analyze phase correlation with trade performance
        phaseAnalysisBtn = new JButton("Phase Analysis");
        phaseAnalysisBtn.setToolTipText("Analyze which phases correlate with trade performance");
        phaseAnalysisBtn.setEnabled(false);  // Enabled after backtest completes
        phaseAnalysisBtn.addActionListener(e -> openPhaseAnalysis());

        // Data status panel created later in status bar

        // Indicator controls panel (extracted to separate class)
        indicatorControls = new IndicatorControlsPanel();
        indicatorControls.setChartPanel(chartPanel);
        indicatorControls.setOnBacktestNeeded(this::runBacktest);

        // Apply saved chart configuration
        chartPanel.applySavedConfig();

        // Wire up auto-save scheduler
        autoSaveScheduler.setOnSave(this::saveStrategyQuietly);
        autoSaveScheduler.setOnBacktest(this::runBacktest);

        // Wire up backtest coordinator callbacks
        backtestCoordinator.setOnProgress(this::setProgress);
        backtestCoordinator.setOnComplete(this::displayResult);
        backtestCoordinator.setOnStatus(this::setStatus);
        backtestCoordinator.setOnError(this::showBacktestError);
        backtestCoordinator.setOnDataStatus(this::handleDataStatus);
        backtestCoordinator.setOnViewDataReady(this::handleViewDataReady);
        backtestCoordinator.setOnDataLoadingProgress(this::handleDataLoadingProgress);

        // Wire up chart config changes to update badges
        chartConfigChangeListener = this::updateDataRequirementsBadges;
        ChartConfig.getInstance().addChangeListener(chartConfigChangeListener);

        // Wire up data fetch progress (shows in status bar)
        candleStore.setProgressCallback(progress -> {
            SwingUtilities.invokeLater(() -> {
                if (progress.message().equals("Complete") || progress.message().equals("Cancelled")) {
                    statusProgressBar.setVisible(false);
                } else {
                    setStatus(progress.message());
                    statusProgressBar.setVisible(true);
                    statusProgressBar.setValue(progress.percentComplete());
                }
            });
        });

        // Wire up change listeners for auto-save and auto-backtest
        // Cancel any ongoing data fetch when switching symbol/resolution
        symbolCombo.addActionListener(e -> {
            candleStore.cancelCurrentFetch();
            autoSaveScheduler.scheduleUpdate();
        });
        timeframeCombo.addActionListener(e -> {
            candleStore.cancelCurrentFetch();
            autoSaveScheduler.scheduleUpdate();
            updateDataRequirementsBadges();
        });
        durationCombo.addActionListener(e -> autoSaveScheduler.scheduleUpdate());

        // Wire up panel change listeners
        editorPanel.setOnChange(() -> {
            autoSaveScheduler.scheduleUpdate();
            updateDataRequirementsBadges();
        });
        settingsPanel.setOnChange(autoSaveScheduler::scheduleUpdate);
    }

    private void updateDurationOptions() {
        String timeframe = (String) timeframeCombo.getSelectedItem();
        durationCombo.removeAllItems();

        // Provide sensible duration options based on timeframe
        switch (timeframe) {
            case "10s", "15s" -> {
                // Sub-minute: requires aggTrades
                durationCombo.addItem("1 hour");
                durationCombo.addItem("3 hours");
                durationCombo.addItem("6 hours");
                durationCombo.addItem("12 hours");
                durationCombo.addItem("1 day");
                durationCombo.addItem("3 days");
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("4 weeks");
            }
            case "1m" -> {
                durationCombo.addItem("1 day");
                durationCombo.addItem("3 days");
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("4 weeks");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
            }
            case "5m" -> {
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
            }
            case "15m" -> {
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
            }
            case "30m" -> {
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
            }
            case "1h" -> {
                durationCombo.addItem("1 month");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
            }
            case "4h" -> {
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
            }
            case "1d" -> {
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
                durationCombo.addItem("10 years");
            }
            case "1w" -> {
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
                durationCombo.addItem("10 years");
            }
            default -> {
                durationCombo.addItem("1 year");
            }
        }
    }

    private void saveStrategyQuietly() {
        // Apply UI values to strategy
        editorPanel.applyToStrategy(strategy);
        settingsPanel.applyToStrategy(strategy);
        applyToolbarToStrategy();

        // Save and mark that save occurred (temporarily ignores file changes)
        autoSaveScheduler.markSaveOccurred();
        strategyStore.save(strategy);
        setTitle(strategy.getName() + " - " + TraderyApp.APP_NAME);
        titleLabel.setText(strategy.getName());
        setStatus("Auto-saved");
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Title bar (centered, like LauncherFrame)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        titleLabel = new JLabel(strategy.getName(), SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Toolbar with symbol, timeframe, Run button
        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        toolbarLeft.add(new JLabel("Symbol:"));
        toolbarLeft.add(symbolCombo);
        toolbarLeft.add(Box.createHorizontalStrut(8));
        toolbarLeft.add(new JLabel("Timeframe:"));
        toolbarLeft.add(timeframeCombo);
        toolbarLeft.add(durationCombo);
        toolbarLeft.add(Box.createHorizontalStrut(16));

        // Toggle buttons
        toolbarLeft.add(fitWidthBtn);
        toolbarLeft.add(fixedWidthBtn);
        toolbarLeft.add(Box.createHorizontalStrut(8));
        toolbarLeft.add(fitYBtn);
        toolbarLeft.add(fullYBtn);
        toolbarLeft.add(Box.createHorizontalStrut(16));

        // Indicator controls (extracted to IndicatorControlsPanel)
        toolbarLeft.add(indicatorControls);

        // Right side of toolbar
        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        toolbarRight.add(phaseAnalysisBtn);
        toolbarRight.add(historyBtn);
        toolbarRight.add(Box.createHorizontalStrut(8));
        toolbarRight.add(claudeBtn);
        toolbarRight.add(codexBtn);
        toolbarRight.add(Box.createHorizontalStrut(8));
        toolbarRight.add(clearCacheBtn);

        JPanel toolbarPanel = new JPanel(new BorderLayout(0, 0));
        toolbarPanel.add(toolbarLeft, BorderLayout.CENTER);
        toolbarPanel.add(toolbarRight, BorderLayout.EAST);
        toolbarPanel.add(new JSeparator(), BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.add(titleBar, BorderLayout.NORTH);
        topPanel.add(toolbarPanel, BorderLayout.CENTER);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // Left side: Editor on top, terminal on bottom (in vertical split)
        editorTerminalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        editorTerminalSplit.setBorder(null);
        editorTerminalSplit.setDividerSize(4);
        editorTerminalSplit.setResizeWeight(0.6);
        editorTerminalSplit.setContinuousLayout(true);
        editorTerminalSplit.setTopComponent(editorPanel);

        // Initialize docked terminal via controller
        aiTerminalController.initializeDockedTerminal(editorTerminalSplit);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(0, 0));
        leftPanel.add(editorTerminalSplit, BorderLayout.CENTER);

        // Add vertical separator on the right
        JSeparator verticalSeparator = new JSeparator(SwingConstants.VERTICAL);
        leftPanel.add(verticalSeparator, BorderLayout.EAST);

        // Center: Charts
        JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
        centerPanel.setBorder(null);
        centerPanel.add(chartPanel, BorderLayout.CENTER);

        // Right side: Settings above Metrics above Trade table
        // Metrics panel with separator line above
        JPanel metricsWrapper = new JPanel(new BorderLayout(0, 0));
        metricsWrapper.add(new JSeparator(), BorderLayout.NORTH);
        metricsWrapper.add(metricsPanel, BorderLayout.CENTER);

        JPanel rightTopPanel = new JPanel(new BorderLayout(0, 0));
        rightTopPanel.add(settingsPanel, BorderLayout.NORTH);
        rightTopPanel.add(metricsWrapper, BorderLayout.CENTER);

        JPanel rightContent = new JPanel(new BorderLayout(0, 0));
        rightContent.add(rightTopPanel, BorderLayout.NORTH);
        rightContent.add(tradeTablePanel, BorderLayout.CENTER);

        // Wrap with vertical separator on left
        JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
        rightPanel.setPreferredSize(new Dimension(280, 0));
        rightPanel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST);
        rightPanel.add(rightContent, BorderLayout.CENTER);

        // Split: Center (charts) | Right (metrics+trades)
        // resizeWeight=1.0 means all extra space goes to charts, right panel stays fixed width
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        rightSplit.setBorder(null);
        rightSplit.setDividerSize(1);
        rightSplit.setResizeWeight(1.0);
        rightSplit.setContinuousLayout(true);
        rightSplit.setLeftComponent(centerPanel);
        rightSplit.setRightComponent(rightPanel);
        rightPanel.setMinimumSize(new Dimension(250, 0));

        // Main split: Left | Center+Right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(1);
        mainSplit.setDividerLocation(580);
        mainSplit.setContinuousLayout(true);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightSplit);

        contentPane.add(mainSplit, BorderLayout.CENTER);

        // Status bar with progress bar
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Left side: status text + data loading progress
        JPanel leftStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftStatusPanel.setOpaque(false);

        statusBar = new JLabel("Ready");
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        leftStatusPanel.add(statusBar);

        // Data loading progress bar (shows when loading OI, Funding, etc.)
        dataLoadingLabel = new JLabel("");
        dataLoadingLabel.setFont(dataLoadingLabel.getFont().deriveFont(10f));
        dataLoadingLabel.setForeground(UIColors.STATUS_LOADING_ACCENT);
        dataLoadingLabel.setVisible(false);
        leftStatusPanel.add(dataLoadingLabel);

        dataLoadingProgressBar = new JProgressBar(0, 100);
        dataLoadingProgressBar.setPreferredSize(new Dimension(100, 12));
        dataLoadingProgressBar.setStringPainted(true);
        dataLoadingProgressBar.setFont(dataLoadingProgressBar.getFont().deriveFont(9f));
        dataLoadingProgressBar.setVisible(false);
        leftStatusPanel.add(dataLoadingProgressBar);

        statusPanel.add(leftStatusPanel, BorderLayout.WEST);

        // Data status panel (center) - shows status for each data type
        dataStatusPanel = createDataStatusPanel();
        statusPanel.add(dataStatusPanel, BorderLayout.CENTER);

        // Progress bar (right) - for backtest progress
        statusProgressBar = new JProgressBar(0, 100);
        statusProgressBar.setPreferredSize(new Dimension(120, 14));
        statusProgressBar.setVisible(false);
        statusPanel.add(statusProgressBar, BorderLayout.EAST);

        // Click anywhere on status bar to open data loading status window
        statusPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        statusPanel.setToolTipText("Click for detailed data loading status");
        statusPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openDataLoadingStatusWindow(statusPanel);
            }
        });

        bottomPanel.add(statusPanel, BorderLayout.CENTER);

        contentPane.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadStrategyData() {
        // Suppress auto-save while loading
        autoSaveScheduler.withIgnoredFileChanges(() -> {
            editorPanel.setStrategy(strategy);
            settingsPanel.setStrategy(strategy);

            // Toolbar controls
            symbolCombo.setSelectedItem(strategy.getSymbol());
            timeframeCombo.setSelectedItem(strategy.getTimeframe());
            // Duration options depend on timeframe, so update them first
            updateDurationOptions();
            durationCombo.setSelectedItem(strategy.getDuration());

            // Update data requirements badges (auto-detected from DSL)
            updateDataRequirementsBadges();
        });
    }

    private void applyToolbarToStrategy() {
        strategy.setSymbol((String) symbolCombo.getSelectedItem());
        strategy.setTimeframe((String) timeframeCombo.getSelectedItem());
        strategy.setDuration((String) durationCombo.getSelectedItem());
    }

    private void startFileWatcher() {
        Path strategyFile = strategyStore.getFile(strategy.getId()).toPath();

        fileWatcher = FileWatcher.forFile(
            strategyFile,
            this::onStrategyFileChanged,
            this::onStrategyFileDeleted
        );

        try {
            fileWatcher.start();
        } catch (IOException e) {
            System.err.println("Failed to start file watcher: " + e.getMessage());
        }

        // Watch phases directory for changes to required phases
        startPhaseWatcher();
    }

    private void startPhaseWatcher() {
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        Path phasesDir = phaseStore.getDirectory().toPath();

        phaseWatcher = FileWatcher.forDirectory(
            phasesDir,
            this::onPhaseFileChanged,  // onModified
            this::onPhaseFileChanged,  // onDeleted
            this::onPhaseFileChanged   // onCreated
        );

        try {
            phaseWatcher.start();
        } catch (IOException e) {
            System.err.println("Failed to start phase watcher: " + e.getMessage());
        }
    }

    private void onPhaseFileChanged(Path path) {
        if (autoSaveScheduler.isIgnoringFileChanges()) return;

        // Check if this phase is one of our required phases
        String filename = path.getFileName().toString();
        String phaseId = path.getParent().getFileName().toString();

        // Only react to phase.json files
        if (!filename.equals("phase.json")) return;

        // Check if this phase is used by current strategy (entry or exit zones)
        if (isPhaseUsedByStrategy(phaseId)) {
            SwingUtilities.invokeLater(() -> {
                setStatus("Phase '" + phaseId + "' changed - re-running backtest...");
                runBacktest();
            });
        }
    }

    private boolean isPhaseUsedByStrategy(String phaseId) {
        // Check strategy-level phases
        if (strategy.getRequiredPhaseIds().contains(phaseId)) return true;
        if (strategy.getExcludedPhaseIds().contains(phaseId)) return true;

        // Check exit zone phases
        for (ExitZone zone : strategy.getExitZones()) {
            if (zone.requiredPhaseIds().contains(phaseId)) return true;
            if (zone.excludedPhaseIds().contains(phaseId)) return true;
        }
        return false;
    }

    private void onStrategyFileChanged(Path path) {
        if (autoSaveScheduler.isIgnoringFileChanges()) return;

        SwingUtilities.invokeLater(() -> {
            setStatus("File changed externally - reloading...");

            // Reload strategy from disk
            Strategy reloaded = strategyStore.load(strategy.getId());
            if (reloaded != null) {
                this.strategy = reloaded;
                loadStrategyData();
                setTitle(strategy.getName() + " - " + TraderyApp.APP_NAME);
                titleLabel.setText(strategy.getName());
                runBacktest();
            }
        });
    }

    private void onStrategyFileDeleted(Path path) {
        SwingUtilities.invokeLater(() -> {
            int result = JOptionPane.showConfirmDialog(this,
                "This strategy was deleted externally. Close window?",
                "Strategy Deleted",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                closeWindow();
            }
        });
    }

    private void clearCacheAndReload() {
        String symbol = (String) symbolCombo.getSelectedItem();

        clearCacheBtn.setEnabled(false);
        setStatus("Clearing cache for " + symbol + "...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                candleStore.clearCache(symbol);
                return null;
            }

            @Override
            protected void done() {
                clearCacheBtn.setEnabled(true);
                try {
                    get();
                    setStatus("Cache cleared - reloading data...");
                    runBacktest();
                } catch (Exception e) {
                    setStatus("Error clearing cache: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void showHistory() {
        File strategyDir = strategyStore.getFolder(strategy.getId());
        HistoryDialog.show(this, strategyDir, restoredStrategy -> {
            // Update the current strategy with restored settings
            strategy.setEntrySettings(restoredStrategy.getEntrySettings());
            strategy.setExitSettings(restoredStrategy.getExitSettings());

            // Reload UI panels
            editorPanel.setStrategy(strategy);

            // Save and re-run backtest
            strategyStore.save(strategy);
            setStatus("Restored strategy from history");
            runBacktest();
        });
    }

    private void openPhaseAnalysis() {
        if (currentResult == null) {
            setStatus("Run a backtest first");
            return;
        }

        List<Candle> candles = backtestCoordinator.getCurrentCandles();
        if (candles == null || candles.isEmpty()) {
            setStatus("No candle data available");
            return;
        }

        // Create window if needed
        if (phaseAnalysisWindow == null) {
            PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
            phaseAnalysisWindow = new PhaseAnalysisWindow(this, candleStore, phaseStore);

            // Wire up callbacks to apply phases to strategy
            phaseAnalysisWindow.setOnRequirePhases(phaseIds -> {
                // Add to existing required phases (don't replace)
                List<String> existing = new java.util.ArrayList<>(strategy.getRequiredPhaseIds());
                for (String id : phaseIds) {
                    if (!existing.contains(id)) {
                        existing.add(id);
                    }
                }
                strategy.setRequiredPhaseIds(existing);
                editorPanel.setStrategy(strategy);
                autoSaveScheduler.scheduleUpdate();
            });

            phaseAnalysisWindow.setOnExcludePhases(phaseIds -> {
                // Add to existing excluded phases (don't replace)
                List<String> existing = new java.util.ArrayList<>(strategy.getExcludedPhaseIds());
                for (String id : phaseIds) {
                    if (!existing.contains(id)) {
                        existing.add(id);
                    }
                }
                strategy.setExcludedPhaseIds(existing);
                editorPanel.setStrategy(strategy);
                autoSaveScheduler.scheduleUpdate();
            });
        }

        String timeframe = (String) timeframeCombo.getSelectedItem();
        phaseAnalysisWindow.analyze(currentResult.trades(), candles, timeframe, strategy);
        phaseAnalysisWindow.setVisible(true);
        phaseAnalysisWindow.toFront();
    }

    private void runBacktest() {
        // Apply current UI values
        editorPanel.applyToStrategy(strategy);
        settingsPanel.applyToStrategy(strategy);
        applyToolbarToStrategy();

        // Get config from UI
        String symbol = (String) symbolCombo.getSelectedItem();
        String resolution = (String) timeframeCombo.getSelectedItem();
        String duration = (String) durationCombo.getSelectedItem();
        double capital = settingsPanel.getCapital();

        // Run backtest via coordinator
        backtestCoordinator.runBacktest(
            strategy,
            symbol,
            resolution,
            BacktestCoordinator.parseDurationMillis(duration),
            capital
        );
    }

    private void displayResult(BacktestResult result) {
        // Store for phase analysis
        this.currentResult = result;

        // Update metrics panel
        metricsPanel.updateMetrics(result.metrics());

        // Update charts with candles from coordinator
        List<Candle> candles = backtestCoordinator.getCurrentCandles();

        // Update trade table (with candles for mini charts)
        tradeTablePanel.updateTrades(result.trades(), candles, strategy.getName());
        if (candles != null && !candles.isEmpty()) {
            // Pass indicator engine to charts for orderflow/funding data
            chartPanel.setIndicatorEngine(backtestCoordinator.getIndicatorEngine());

            chartPanel.updateCharts(candles, result.trades(), result.config().initialCapital());

            // Apply saved overlays
            chartPanel.applySavedOverlays(candles);

            // Update indicator controls with current candle data
            indicatorControls.setCurrentCandles(candles);
            indicatorControls.refreshOverlays();
        }

        // Enable phase analysis button if we have trades
        phaseAnalysisBtn.setEnabled(result.trades() != null && !result.trades().isEmpty());
    }

    private void setStatus(String message) {
        statusBar.setText(" " + message);
    }

    private void showBacktestError(String error) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                error,
                "Missing Required Data",
                JOptionPane.WARNING_MESSAGE);
        });
    }

    /**
     * Create the data status panel for the status bar.
     * Shows status for each data type: OHLC, AggTrades, Funding, OI
     */
    private JPanel createDataStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        panel.setOpaque(false);

        // Create status labels for each data type
        ohlcStatusLabel = createStatusLabel("OHLC");
        aggTradesStatusLabel = createStatusLabel("AggTrades");
        fundingStatusLabel = createStatusLabel("Funding");
        oiStatusLabel = createStatusLabel("OI");

        panel.add(ohlcStatusLabel);
        panel.add(aggTradesStatusLabel);
        panel.add(fundingStatusLabel);
        panel.add(oiStatusLabel);

        return panel;
    }

    /**
     * Open the data loading status window.
     */
    private void openDataLoadingStatusWindow(Component anchor) {
        if (dataLoadingStatusWindow == null) {
            // Include both backtest tracker and global preview tracker
            dataLoadingStatusWindow = new DataLoadingStatusWindow(
                this,
                backtestCoordinator.getTracker(),
                ApplicationContext.getInstance().getPreviewTracker()
            );
        }
        dataLoadingStatusWindow.showNear(anchor);
    }

    private JLabel createStatusLabel(String dataType) {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        label.setName(dataType); // Store data type for updates
        return label;
    }

    /**
     * Update data status labels based on requirements and availability.
     * Shows: "--" (not required) / "required" / "ready"
     */
    private void updateDataRequirementsBadges() {
        if (ohlcStatusLabel == null) return;

        // Get chart config for active indicators
        ChartConfig chartConfig = ChartConfig.getInstance();

        // Check what data is required from DSL
        boolean needsAggTradesFromDSL = strategy != null && strategy.requiresAggTrades();
        boolean needsOIFromDSL = strategy != null && strategy.usesOpenInterest();

        // Check what data is required from active charts
        boolean needsAggTradesFromCharts = chartConfig.isDeltaEnabled() ||
                                           chartConfig.isCvdEnabled() ||
                                           chartConfig.isWhaleEnabled() ||
                                           chartConfig.isRetailEnabled();
        boolean needsFundingFromCharts = chartConfig.isFundingEnabled();
        boolean needsOIFromCharts = chartConfig.isOiEnabled();

        // Check if sub-minute timeframe (always needs aggTrades, no separate OHLC)
        String timeframe = (String) timeframeCombo.getSelectedItem();
        boolean isSubMinute = timeframe != null && timeframe.endsWith("s");

        // Combine requirements
        boolean needsOHLC = !isSubMinute; // Sub-minute generates candles from aggTrades
        boolean needsAggTrades = isSubMinute || needsAggTradesFromDSL || needsAggTradesFromCharts;
        boolean needsFunding = needsFundingFromCharts;
        boolean needsOI = needsOIFromDSL || needsOIFromCharts;

        // Update status labels
        updateStatusLabel(ohlcStatusLabel, "OHLC", needsOHLC);
        updateStatusLabel(aggTradesStatusLabel, "AggTrades", needsAggTrades);
        updateStatusLabel(fundingStatusLabel, "Funding", needsFunding);
        updateStatusLabel(oiStatusLabel, "OI", needsOI);
    }

    private void updateStatusLabel(JLabel label, String dataType, boolean required) {
        if (!required) {
            label.setText(dataType + ": not used");
            label.setForeground(UIColors.STATUS_UNKNOWN);
            label.setToolTipText(dataType + " not required for current strategy");
        } else {
            // Check if data is loaded (simplified - actual check would need backtest coordinator state)
            boolean isLoaded = isDataLoaded(dataType);
            if (isLoaded) {
                label.setText(dataType + ": ready");
                label.setForeground(UIColors.STATUS_READY);
                label.setToolTipText(dataType + " data loaded");
            } else {
                label.setText(dataType + ": required");
                label.setForeground(UIColors.STATUS_LOADING);
                label.setToolTipText(dataType + " will be fetched on next backtest");
            }
        }
    }

    /**
     * Check if data type is currently loaded.
     * Returns true if last backtest loaded this data type.
     */
    private boolean isDataLoaded(String dataType) {
        // Check if we have data from last backtest
        return switch (dataType) {
            case "OHLC" -> backtestCoordinator.getCurrentCandles() != null &&
                          !backtestCoordinator.getCurrentCandles().isEmpty();
            case "AggTrades" -> backtestCoordinator.getCurrentAggTrades() != null &&
                               !backtestCoordinator.getCurrentAggTrades().isEmpty();
            case "Funding" -> backtestCoordinator.getCurrentFundingRates() != null &&
                             !backtestCoordinator.getCurrentFundingRates().isEmpty();
            case "OI" -> backtestCoordinator.getCurrentOpenInterest() != null &&
                        !backtestCoordinator.getCurrentOpenInterest().isEmpty();
            default -> false;
        };
    }

    /**
     * Handle data status updates from backtest coordinator.
     * @param dataType One of: "OHLC", "AggTrades", "Funding", "OI"
     * @param status One of: "loading", "ready", "error"
     */
    private void handleDataStatus(String dataType, String status) {
        JLabel label = switch (dataType) {
            case "OHLC" -> ohlcStatusLabel;
            case "AggTrades" -> aggTradesStatusLabel;
            case "Funding" -> fundingStatusLabel;
            case "OI" -> oiStatusLabel;
            default -> null;
        };
        if (label == null) return;

        switch (status) {
            case "loading" -> {
                label.setText(dataType + ": loading...");
                label.setForeground(UIColors.STATUS_LOADING_ACCENT);
                label.setToolTipText("Loading " + dataType + " data...");
            }
            case "ready" -> {
                label.setText(dataType + ": ready");
                label.setForeground(UIColors.STATUS_READY);
                label.setToolTipText(dataType + " data loaded");
            }
            case "error" -> {
                label.setText(dataType + ": unavailable");
                label.setForeground(UIColors.STATUS_ERROR);
                label.setToolTipText(dataType + " data could not be loaded");
            }
        }
    }

    /**
     * Handle VIEW tier data becoming ready asynchronously.
     * Refreshes the corresponding chart without re-running the backtest.
     */
    private void handleViewDataReady(String dataType) {
        switch (dataType) {
            case "Funding" -> {
                System.out.println("VIEW data ready: Funding - refreshing chart");
                chartPanel.setIndicatorEngine(backtestCoordinator.getIndicatorEngine());
                chartPanel.refreshFundingChart();
            }
            case "OI" -> {
                System.out.println("VIEW data ready: OI - refreshing chart");
                chartPanel.setIndicatorEngine(backtestCoordinator.getIndicatorEngine());
                chartPanel.refreshOiChart();
            }
            case "AggTrades" -> {
                System.out.println("VIEW data ready: AggTrades - refreshing orderflow charts");
                chartPanel.setIndicatorEngine(backtestCoordinator.getIndicatorEngine());
                chartPanel.refreshOrderflowCharts();
            }
        }
    }

    /**
     * Handle data loading progress updates for the progress bar.
     * @param dataType Data type being loaded (e.g., "OI", "Funding")
     * @param loaded Number of records loaded so far
     * @param expected Expected total records (-1 to hide progress bar)
     */
    private void handleDataLoadingProgress(String dataType, int loaded, int expected) {
        if (loaded < 0 || expected < 0) {
            // Hide progress bar
            dataLoadingLabel.setVisible(false);
            dataLoadingProgressBar.setVisible(false);
        } else {
            // Show and update progress bar
            dataLoadingLabel.setText(dataType + ":");
            dataLoadingLabel.setVisible(true);

            int percent = expected > 0 ? Math.min(100, (loaded * 100) / expected) : 0;
            dataLoadingProgressBar.setValue(percent);
            dataLoadingProgressBar.setString(loaded + "/" + expected);
            dataLoadingProgressBar.setVisible(true);
        }
    }

    private void setProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            if (percentage >= 100 || message.equals("Error") || message.equals("Complete")) {
                statusProgressBar.setVisible(false);
            } else {
                setStatus(message);
                statusProgressBar.setVisible(true);
                statusProgressBar.setValue(percentage);
            }
        });
    }

    private void closeWindow() {
        // Mark as closed in window state
        WindowStateStore.getInstance().setProjectOpen(strategy.getId(), false);

        // Stop file watchers
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        if (phaseWatcher != null) {
            phaseWatcher.stop();
        }

        // Dispose terminal (docked or undocked)
        if (aiTerminalController != null) {
            aiTerminalController.dispose();
        }

        // Stop auto-save scheduler
        if (autoSaveScheduler != null) {
            autoSaveScheduler.stop();
        }

        // Cancel any ongoing data fetches
        if (candleStore != null) {
            candleStore.cancelCurrentFetch();
            candleStore.setProgressCallback(null);
        }

        // Unregister singleton listeners
        if (themeChangeListener != null) {
            com.tradery.ui.theme.ThemeManager.getInstance().removeThemeChangeListener(themeChangeListener);
        }
        if (chartConfigChangeListener != null) {
            ChartConfig.getInstance().removeChangeListener(chartConfigChangeListener);
        }

        // Close auxiliary windows
        if (phaseAnalysisWindow != null) {
            phaseAnalysisWindow.dispose();
            phaseAnalysisWindow = null;
        }
        if (dataLoadingStatusWindow != null) {
            dataLoadingStatusWindow.dispose();
            dataLoadingStatusWindow = null;
        }

        // Clear backtest coordinator callbacks to break reference cycles
        if (backtestCoordinator != null) {
            backtestCoordinator.setOnProgress(null);
            backtestCoordinator.setOnComplete(null);
            backtestCoordinator.setOnStatus(null);
            backtestCoordinator.setOnError(null);
            backtestCoordinator.setOnDataStatus(null);
            backtestCoordinator.setOnViewDataReady(null);
            backtestCoordinator.setOnDataLoadingProgress(null);
        }

        // Notify close callback
        if (onClose != null) {
            onClose.accept(strategy.getId());
        }

        dispose();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && strategy != null) {
            // Mark as open in window state
            WindowStateStore.getInstance().setProjectOpen(strategy.getId(), true);
        }
    }

    public String getStrategyId() {
        return strategy.getId();
    }

    /**
     * Reload strategy from disk and refresh all UI components.
     * Called when the preset is restored externally.
     */
    public void reloadStrategy() {
        Strategy reloaded = strategyStore.load(strategy.getId());
        if (reloaded != null) {
            this.strategy = reloaded;
            loadStrategyData();
            runBacktest();
        }
    }

    public void bringToFront() {
        toFront();
        requestFocus();
    }
}
