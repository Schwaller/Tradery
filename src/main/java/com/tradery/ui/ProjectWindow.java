package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.AggTradesStore;
import com.tradery.data.sqlite.SqliteDataStore;
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
    private DataRangePanel dataRangePanel;
    private BacktestSettingsPanel settingsPanel;
    private ChartsPanel chartPanel;
    private MetricsPanel metricsPanel;
    private TradeTablePanel tradeTablePanel;
    private JLabel statusBar;
    private JProgressBar statusProgressBar;
    private JProgressBar dataLoadingProgressBar;
    private JLabel dataLoadingLabel;
    private TimelineBar timelineBar;

    // Toolbar controls
    private JToggleButton fitWidthBtn;
    private JToggleButton fixedWidthBtn;
    private JToggleButton candlestickToggle;
    private JSlider priceOpacitySlider;
    private JToggleButton fitYBtn;
    private JToggleButton fullYBtn;
    private JButton claudeBtn;
    private JButton codexBtn;
    private JButton historyBtn;
    private JButton phaseAnalysisBtn;
    private PageManagerBadgesPanel pageManagerBadges;
    private MemoryStatusPanel memoryStatusPanel;

    // Phase analysis window
    private PhaseAnalysisWindow phaseAnalysisWindow;
    private BacktestResult currentResult;

    // Indicator controls panel (extracted)
    private IndicatorControlsPanel indicatorControls;

    // Embedded AI terminal (for Claude/Codex)
    private JSplitPane editorTerminalSplit;  // Vertical split: editor | terminal (left side)
    private AiTerminalController aiTerminalController;

    // Data stores
    private final StrategyStore strategyStore;
    private final AggTradesStore aggTradesStore;
    private FileWatcher fileWatcher;
    private FileWatcher phaseWatcher;

    // Extracted coordinators
    private final BacktestCoordinator backtestCoordinator;
    private final AutoSaveScheduler autoSaveScheduler;
    private final StatusManager statusManager;

    // Listener references for cleanup
    private Runnable themeChangeListener;

    public ProjectWindow(Strategy strategy, Consumer<String> onClose) {
        super(strategy.getName() + " - " + TraderyApp.APP_NAME);
        this.strategy = strategy;
        this.onClose = onClose;

        // Use shared stores from ApplicationContext
        this.strategyStore = ApplicationContext.getInstance().getStrategyStore();
        this.aggTradesStore = ApplicationContext.getInstance().getAggTradesStore();

        // Initialize coordinators
        ResultStore resultStore = new ResultStore(strategy.getId());
        SqliteDataStore sqliteDataStore = ApplicationContext.getInstance().getSqliteDataStore();
        BacktestEngine backtestEngine = new BacktestEngine(sqliteDataStore);
        com.tradery.data.FundingRateStore fundingRateStore = new com.tradery.data.FundingRateStore();
        com.tradery.data.OpenInterestStore openInterestStore = new com.tradery.data.OpenInterestStore();
        com.tradery.data.PremiumIndexStore premiumIndexStore = new com.tradery.data.PremiumIndexStore();
        this.backtestCoordinator = new BacktestCoordinator(backtestEngine, sqliteDataStore, aggTradesStore, fundingRateStore, openInterestStore, premiumIndexStore, resultStore);
        this.autoSaveScheduler = new AutoSaveScheduler();
        this.statusManager = new StatusManager();

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
        dataRangePanel = new DataRangePanel();
        settingsPanel = new BacktestSettingsPanel();
        chartPanel = new ChartsPanel();
        metricsPanel = new MetricsPanel();
        tradeTablePanel = new TradeTablePanel();
        timelineBar = new TimelineBar();
        timelineBar.setOnAnchorDateChanged(this::onTimelineAnchorChanged);

        // Wire up data range manage button
        dataRangePanel.setOnManageClicked(() -> {
            SqliteDataStore dataStore = ApplicationContext.getInstance().getSqliteDataStore();
            DataManagementDialog.show(this, dataStore);
        });

        // Wire up chart status callback (hover info uses low priority)
        chartPanel.setOnStatusUpdate(statusManager::setHoverStatus);

        // Wire up theme change listener
        themeChangeListener = chartPanel::refreshTheme;
        com.tradery.ui.theme.ThemeManager.getInstance().addThemeChangeListener(themeChangeListener);

        // Wire up trade table hover/select to chart highlight
        tradeTablePanel.setOnTradeHover(chartPanel::highlightTrades);
        tradeTablePanel.setOnTradeSelect(chartPanel::highlightTrades);

        // Width toggle group (Fit / Fixed)
        fitWidthBtn = new JToggleButton("Fit");
        fixedWidthBtn = new JToggleButton("Fixed");
        ButtonGroup widthGroup = new ButtonGroup();
        widthGroup.add(fitWidthBtn);
        widthGroup.add(fixedWidthBtn);
        fitWidthBtn.setSelected(true);
        fitWidthBtn.addActionListener(e -> chartPanel.setFixedWidthMode(false));
        fixedWidthBtn.addActionListener(e -> chartPanel.setFixedWidthMode(true));

        // Candlestick toggle
        candlestickToggle = new JToggleButton("Candles");
        candlestickToggle.setToolTipText("Toggle between line and candlestick chart");
        candlestickToggle.setSelected(ChartConfig.getInstance().isCandlestickMode());
        candlestickToggle.addActionListener(e -> {
            ChartConfig.getInstance().setCandlestickMode(candlestickToggle.isSelected());
            chartPanel.refreshPriceChart();
        });

        // Price opacity slider
        priceOpacitySlider = new JSlider(0, 100, ChartConfig.getInstance().getPriceOpacity());
        priceOpacitySlider.setPreferredSize(new Dimension(60, 20));
        priceOpacitySlider.setToolTipText("Price opacity: " + priceOpacitySlider.getValue() + "%");
        priceOpacitySlider.addChangeListener(e -> {
            int value = priceOpacitySlider.getValue();
            priceOpacitySlider.setToolTipText("Price opacity: " + value + "%");
            if (!priceOpacitySlider.getValueIsAdjusting()) {
                ChartConfig.getInstance().setPriceOpacity(value);
                chartPanel.refreshPriceChart();
            }
        });

        // Y-axis toggle group (Fit / Full)
        fitYBtn = new JToggleButton("Fit Y");
        fullYBtn = new JToggleButton("Full Y");
        ButtonGroup yGroup = new ButtonGroup();
        yGroup.add(fitYBtn);
        yGroup.add(fullYBtn);
        fitYBtn.setSelected(true);
        fitYBtn.addActionListener(e -> chartPanel.setFitYAxisToVisibleData(true));
        fullYBtn.addActionListener(e -> chartPanel.setFitYAxisToVisibleData(false));

        // Initialize AI terminal controller
        aiTerminalController = new AiTerminalController(this, this::runBacktest, this::setStatus);

        // Claude button - opens terminal with Claude CLI
        claudeBtn = new JButton("Claude");
        claudeBtn.setToolTipText("Open Claude CLI to help optimize this strategy");
        claudeBtn.addActionListener(e -> aiTerminalController.openClaudeTerminal(
            strategy.getId(), strategy.getName(),
            dataRangePanel.getSymbol(),
            dataRangePanel.getTimeframe(),
            dataRangePanel.getDuration()
        ));

        // Codex button - opens terminal with Codex CLI
        codexBtn = new JButton("Codex");
        codexBtn.setToolTipText("Open Codex CLI to help optimize this strategy");
        codexBtn.addActionListener(e -> aiTerminalController.openCodexTerminal(
            strategy.getId(), strategy.getName(),
            dataRangePanel.getSymbol(),
            dataRangePanel.getTimeframe(),
            dataRangePanel.getDuration()
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

        // Wire up StatusManager callbacks
        statusManager.setCallbacks(
            (message, progress) -> {
                statusBar.setText(" " + message);
                if (progress >= 0 && progress < 100) {
                    statusProgressBar.setValue(progress);
                    statusProgressBar.setVisible(true);
                } else {
                    statusProgressBar.setVisible(false);
                }
            },
            () -> statusProgressBar.setVisible(false)
        );

        // Wire up backtest coordinator callbacks
        backtestCoordinator.setOnProgress(this::setProgress);
        backtestCoordinator.setOnComplete(this::displayResult);
        backtestCoordinator.setOnStatus(this::handleBacktestStatus);
        backtestCoordinator.setOnError(this::showBacktestError);
        backtestCoordinator.setOnViewDataReady(this::handleViewDataReady);
        backtestCoordinator.setOnDataLoadingProgress(this::handleDataLoadingProgress);
        backtestCoordinator.setOnBacktestStart(this::clearStaleData);

        // Wire up panel change listeners
        editorPanel.setOnChange(autoSaveScheduler::scheduleUpdate);
        settingsPanel.setOnChange(autoSaveScheduler::scheduleUpdate);
        dataRangePanel.setOnChange(() -> {
            autoSaveScheduler.scheduleUpdate();
            updateTimeline();
        });
    }

    private void updateTimeline() {
        String symbol = dataRangePanel.getSymbol();
        String duration = dataRangePanel.getDuration();
        Long anchorDate = dataRangePanel.getAnchorDate();

        long durationMs = BacktestCoordinator.parseDurationMillis(duration);
        long endTime = anchorDate != null ? anchorDate : System.currentTimeMillis();
        long startTime = endTime - durationMs;

        timelineBar.update(symbol, startTime, endTime);
    }

    private void onTimelineAnchorChanged(Long newAnchorDate) {
        dataRangePanel.setAnchorDate(newAnchorDate);
        // Update timeline window without refetching
        String duration = dataRangePanel.getDuration();
        long durationMs = BacktestCoordinator.parseDurationMillis(duration);
        timelineBar.updateWindow(newAnchorDate - durationMs, newAnchorDate);
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
        timelineBar.setTitle(strategy.getName());
        statusManager.setInfoStatus(StatusManager.SOURCE_AUTOSAVE, "Auto-saved");
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Set title on timeline bar
        timelineBar.setTitle(strategy.getName());

        // Left side: Claude, Codex, Help
        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbarLeft.add(claudeBtn);
        toolbarLeft.add(codexBtn);
        JButton helpBtn = new JButton("Help");
        helpBtn.setToolTipText("Strategy Guide & DSL Reference");
        helpBtn.addActionListener(e -> StrategyHelpDialog.show(this));
        toolbarLeft.add(helpBtn);

        // Center: Chart controls and Indicators
        JPanel toolbarCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        toolbarCenter.add(candlestickToggle);
        toolbarCenter.add(priceOpacitySlider);
        toolbarCenter.add(Box.createHorizontalStrut(8));
        toolbarCenter.add(fitWidthBtn);
        toolbarCenter.add(fixedWidthBtn);
        toolbarCenter.add(Box.createHorizontalStrut(8));
        toolbarCenter.add(fitYBtn);
        toolbarCenter.add(fullYBtn);
        toolbarCenter.add(Box.createHorizontalStrut(16));
        toolbarCenter.add(indicatorControls);

        // Right side: Actions
        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        toolbarRight.add(phaseAnalysisBtn);
        toolbarRight.add(historyBtn);

        JPanel toolbarPanel = new JPanel(new BorderLayout(0, 0));
        toolbarPanel.add(toolbarLeft, BorderLayout.WEST);
        toolbarPanel.add(toolbarCenter, BorderLayout.CENTER);
        toolbarPanel.add(toolbarRight, BorderLayout.EAST);
        toolbarPanel.add(new JSeparator(), BorderLayout.SOUTH);

        // Stack: timeline, toolbar
        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.add(timelineBar);
        topStack.add(new JSeparator());
        topStack.add(toolbarPanel);

        contentPane.add(topStack, BorderLayout.NORTH);

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

        // Settings panels stacked: Data Range above Backtest Settings
        JPanel settingsStack = new JPanel();
        settingsStack.setLayout(new BoxLayout(settingsStack, BoxLayout.Y_AXIS));
        settingsStack.add(dataRangePanel);
        settingsStack.add(new JSeparator());
        settingsStack.add(settingsPanel);

        JPanel rightTopPanel = new JPanel(new BorderLayout(0, 0));
        rightTopPanel.add(settingsStack, BorderLayout.NORTH);
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
        mainSplit.setDividerLocation(640);
        mainSplit.setContinuousLayout(true);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightSplit);

        contentPane.add(mainSplit, BorderLayout.CENTER);

        // Status bar with progress bar
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Left side: progress bars first, then status text
        JPanel leftStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftStatusPanel.setOpaque(false);

        // Main progress bar (backtest, data fetch, etc.) - first
        statusProgressBar = new JProgressBar(0, 100);
        statusProgressBar.setPreferredSize(new Dimension(120, 12));
        statusProgressBar.setVisible(false);
        leftStatusPanel.add(statusProgressBar);

        // Data loading label + progress bar (OI, Funding, etc.) - second
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

        // Status text - after progress bars
        statusBar = new JLabel("Ready");
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        leftStatusPanel.add(statusBar);

        statusPanel.add(leftStatusPanel, BorderLayout.WEST);

        // Right side: memory status + page manager badges
        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightStatusPanel.setOpaque(false);

        // Memory status (heap + data memory)
        memoryStatusPanel = new MemoryStatusPanel();
        rightStatusPanel.add(memoryStatusPanel);

        // Page manager badges (shows checked out pages per manager)
        pageManagerBadges = new PageManagerBadgesPanel();
        rightStatusPanel.add(pageManagerBadges);

        statusPanel.add(rightStatusPanel, BorderLayout.EAST);

        // Right-click context menu for status bar
        statusPanel.setToolTipText("Right-click to open Download Dashboard");
        JPopupMenu statusContextMenu = new JPopupMenu();
        JMenuItem openDashboardItem = new JMenuItem("Open Download Dashboard...");
        openDashboardItem.addActionListener(e -> openDownloadDashboard());
        statusContextMenu.add(openDashboardItem);
        statusPanel.setComponentPopupMenu(statusContextMenu);

        bottomPanel.add(statusPanel, BorderLayout.CENTER);

        contentPane.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadStrategyData() {
        // Suppress auto-save while loading
        autoSaveScheduler.withIgnoredFileChanges(() -> {
            editorPanel.setStrategy(strategy);
            dataRangePanel.setStrategy(strategy);
            settingsPanel.setStrategy(strategy);

            // Update timeline with current data range
            updateTimeline();
        });
    }

    private void applyToolbarToStrategy() {
        dataRangePanel.applyToStrategy(strategy);
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
                statusManager.startBacktest("Phase '" + phaseId + "' changed - re-running backtest...");
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
            statusManager.startBacktest("File changed externally - reloading...");

            // Reload strategy from disk
            Strategy reloaded = strategyStore.load(strategy.getId());
            if (reloaded != null) {
                this.strategy = reloaded;
                loadStrategyData();
                setTitle(strategy.getName() + " - " + TraderyApp.APP_NAME);
                timelineBar.setTitle(strategy.getName());
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
            statusManager.setInfoStatus(StatusManager.SOURCE_FILE_CHANGE, "Restored strategy from history");
            runBacktest();
        });
    }

    private void openPhaseAnalysis() {
        if (currentResult == null) {
            statusManager.setInfoStatus(StatusManager.SOURCE_FILE_CHANGE, "Run a backtest first");
            return;
        }

        List<Candle> candles = backtestCoordinator.getCurrentCandles();
        if (candles == null || candles.isEmpty()) {
            statusManager.setInfoStatus(StatusManager.SOURCE_FILE_CHANGE, "No candle data available");
            return;
        }

        // Create window if needed
        if (phaseAnalysisWindow == null) {
            PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
            SqliteDataStore sqliteStore = ApplicationContext.getInstance().getSqliteDataStore();
            phaseAnalysisWindow = new PhaseAnalysisWindow(this, sqliteStore, phaseStore);

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

        String timeframe = dataRangePanel.getTimeframe();
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
        String symbol = dataRangePanel.getSymbol();
        String resolution = dataRangePanel.getTimeframe();
        String duration = dataRangePanel.getDuration();
        double capital = settingsPanel.getCapital();
        Long anchorDate = dataRangePanel.getAnchorDate();

        // Clear chart immediately so stale data isn't shown while loading
        chartPanel.clear();

        // Set VIEW tier requirements based on enabled charts
        backtestCoordinator.setViewRequirements(
            chartPanel.isAnyOrderflowChartEnabled(),
            chartPanel.isFundingChartEnabled(),
            chartPanel.isOiChartEnabled(),
            chartPanel.isPremiumChartEnabled()
        );

        // Run backtest via coordinator
        backtestCoordinator.runBacktest(
            strategy,
            symbol,
            resolution,
            BacktestCoordinator.parseDurationMillis(duration),
            capital,
            anchorDate
        );
    }

    /**
     * Clear stale data when a new backtest starts.
     * This prevents showing old trades/metrics while new data loads.
     */
    private void clearStaleData() {
        // Clear trade table to prevent showing old trades
        tradeTablePanel.updateTrades(java.util.Collections.emptyList(), null, strategy.getName());

        // Clear current result
        this.currentResult = null;

        // Disable phase analysis (no valid result)
        phaseAnalysisBtn.setEnabled(false);
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

            // Set data context for background indicator computation
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            chartPanel.setIndicatorDataContext(candles, dataRangePanel.getSymbol(),
                dataRangePanel.getTimeframe(), startTime, endTime);

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
        statusManager.setInfoStatus(StatusManager.SOURCE_FILE_CHANGE, message);
    }

    /**
     * Handle status updates from BacktestCoordinator.
     * Uses OPERATION priority so it takes precedence over loading/hover.
     */
    private void handleBacktestStatus(String message) {
        if (message.startsWith("Error:")) {
            statusManager.setErrorStatus(message.substring(7).trim());
        } else if (message.contains("trades") || message.contains("Win rate") || message.contains("Return")) {
            // This is a summary message - set as idle message
            statusManager.completeBacktest(message);
        } else {
            // Active operation message
            statusManager.startBacktest(message);
        }
    }

    private void showBacktestError(String error) {
        // Error is already shown in status bar via handleBacktestStatus
        // No popup dialog needed - less intrusive UX
    }

    /**
     * Open the Download Dashboard window.
     */
    private void openDownloadDashboard() {
        DownloadDashboardWindow dashboard = new DownloadDashboardWindow();
        dashboard.setLocationRelativeTo(this);
        dashboard.setVisible(true);
    }

    /**
     * Handle VIEW tier data becoming ready asynchronously.
     * Refreshes the corresponding chart without re-running the backtest.
     */
    private void handleViewDataReady(String dataType) {
        // First, refresh the data in the IndicatorEngine from the page
        backtestCoordinator.refreshViewData(dataType);

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
            case "Premium" -> {
                System.out.println("VIEW data ready: Premium - refreshing chart");
                chartPanel.setIndicatorEngine(backtestCoordinator.getIndicatorEngine());
                chartPanel.refreshPremiumChart();
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

            // Pulse timeline while data is loading
            timelineBar.setLoading(true);
        }
    }

    private void setProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            if (percentage >= 100 || message.equals("Error") || message.equals("Complete")) {
                statusManager.clearStatus(StatusManager.SOURCE_BACKTEST);
                timelineBar.setLoading(false);
            } else {
                statusManager.updateBacktest(message, percentage);
                timelineBar.setLoading(true);
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

        // Dispose timeline bar (releases data page)
        if (timelineBar != null) {
            timelineBar.dispose();
        }

        // Dispose status bar panels
        if (pageManagerBadges != null) {
            pageManagerBadges.dispose();
        }
        if (memoryStatusPanel != null) {
            memoryStatusPanel.dispose();
        }

        // Stop auto-save scheduler
        if (autoSaveScheduler != null) {
            autoSaveScheduler.stop();
        }

        // Unregister singleton listeners
        if (themeChangeListener != null) {
            com.tradery.ui.theme.ThemeManager.getInstance().removeThemeChangeListener(themeChangeListener);
        }
        // Close auxiliary windows
        if (phaseAnalysisWindow != null) {
            phaseAnalysisWindow.dispose();
            phaseAnalysisWindow = null;
        }

        // Clear backtest coordinator callbacks to break reference cycles
        if (backtestCoordinator != null) {
            backtestCoordinator.setOnProgress(null);
            backtestCoordinator.setOnComplete(null);
            backtestCoordinator.setOnStatus(null);
            backtestCoordinator.setOnError(null);
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

    /**
     * Info about this window for debugging API.
     */
    public record WindowInfo(
        String strategyId,
        String strategyName,
        String symbol,
        String timeframe,
        String duration,
        boolean isVisible,
        List<String> enabledOverlays,
        List<String> enabledIndicators
    ) {}

    /**
     * Get info about this window for the debugging API.
     */
    public WindowInfo getWindowInfo() {
        String symbol = dataRangePanel != null ? dataRangePanel.getSymbol() : "";
        String timeframe = dataRangePanel != null ? dataRangePanel.getTimeframe() : "";
        String duration = dataRangePanel != null ? dataRangePanel.getDuration() : "";

        // Collect enabled overlays and indicators from ChartConfig
        List<String> overlays = new java.util.ArrayList<>();
        List<String> indicators = new java.util.ArrayList<>();

        ChartConfig config = ChartConfig.getInstance();

        // Overlays
        if (config.isSmaEnabled() || !config.getSmaPeriods().isEmpty()) {
            overlays.add("SMA");
        }
        if (config.isEmaEnabled() || !config.getEmaPeriods().isEmpty()) {
            overlays.add("EMA");
        }
        if (config.isBollingerEnabled()) {
            overlays.add("BBANDS");
        }
        if (config.isHighLowEnabled()) {
            overlays.add("HighLow");
        }
        if (config.isMayerEnabled()) {
            overlays.add("Mayer");
        }
        if (config.isVwapEnabled()) {
            overlays.add("VWAP");
        }
        if (config.isDailyPocEnabled()) {
            overlays.add("DailyPOC");
        }
        if (config.isFloatingPocEnabled()) {
            overlays.add("FloatingPOC");
        }
        if (config.isRayOverlayEnabled()) {
            overlays.add("Rays");
        }
        if (config.isIchimokuEnabled()) {
            overlays.add("Ichimoku");
        }

        // Indicator charts
        if (config.isRsiEnabled()) {
            indicators.add("RSI(" + config.getRsiPeriod() + ")");
        }
        if (config.isMacdEnabled()) {
            indicators.add("MACD");
        }
        if (config.isAtrEnabled()) {
            indicators.add("ATR(" + config.getAtrPeriod() + ")");
        }
        if (config.isStochasticEnabled()) {
            indicators.add("STOCHASTIC");
        }
        if (config.isRangePositionEnabled()) {
            indicators.add("RANGE_POSITION");
        }
        if (config.isAdxEnabled()) {
            indicators.add("ADX(" + config.getAdxPeriod() + ")");
        }
        if (config.isDeltaEnabled()) {
            indicators.add("DELTA");
        }
        if (config.isCvdEnabled()) {
            indicators.add("CVD");
        }
        if (config.isFundingEnabled()) {
            indicators.add("FUNDING");
        }
        if (config.isOiEnabled()) {
            indicators.add("OI");
        }
        if (config.isPremiumEnabled()) {
            indicators.add("PREMIUM");
        }

        return new WindowInfo(
            strategy.getId(),
            strategy.getName(),
            symbol,
            timeframe,
            duration,
            isVisible(),
            overlays,
            indicators
        );
    }
}
