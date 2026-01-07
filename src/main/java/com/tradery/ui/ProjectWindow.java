package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.FileWatcher;
import com.tradery.io.PhaseStore;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.io.WindowStateStore;
import com.tradery.model.*;
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
    private ProjectSettingsPanel settingsPanel;
    private ChartsPanel chartPanel;
    private MetricsPanel metricsPanel;
    private TradeTablePanel tradeTablePanel;
    private JLabel statusBar;
    private JLabel titleLabel;

    // Toolbar controls
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JComboBox<String> durationCombo;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JToggleButton fitWidthBtn;
    private JToggleButton fixedWidthBtn;
    private JToggleButton fitYBtn;
    private JToggleButton fullYBtn;
    private JButton clearCacheBtn;
    private JButton claudeBtn;
    private JButton codexBtn;
    private JButton historyBtn;

    // Indicator controls panel (extracted)
    private IndicatorControlsPanel indicatorControls;

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    // Data stores
    private final StrategyStore strategyStore;
    private final CandleStore candleStore;
    private FileWatcher fileWatcher;
    private FileWatcher phaseWatcher;

    // Extracted coordinators
    private final BacktestCoordinator backtestCoordinator;
    private final AutoSaveScheduler autoSaveScheduler;

    public ProjectWindow(Strategy strategy, Consumer<String> onClose) {
        super(strategy.getName() + " - " + TraderyApp.APP_NAME);
        this.strategy = strategy;
        this.onClose = onClose;

        // Use shared stores from ApplicationContext
        this.strategyStore = ApplicationContext.getInstance().getStrategyStore();
        this.candleStore = ApplicationContext.getInstance().getCandleStore();

        // Initialize coordinators
        ResultStore resultStore = new ResultStore(strategy.getId());
        BacktestEngine backtestEngine = new BacktestEngine(candleStore);
        this.backtestCoordinator = new BacktestCoordinator(backtestEngine, candleStore, resultStore);
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
        settingsPanel = new ProjectSettingsPanel();
        chartPanel = new ChartsPanel();
        metricsPanel = new MetricsPanel();
        tradeTablePanel = new TradeTablePanel();

        // Wire up chart status callback
        chartPanel.setOnStatusUpdate(this::setStatus);

        // Toolbar controls
        symbolCombo = new JComboBox<>(SYMBOLS);
        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        timeframeCombo.addActionListener(e -> updateDurationOptions());

        durationCombo = new JComboBox<>();
        updateDurationOptions();

        // Progress bar (hidden by default)
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(120, 16));
        progressBar.setVisible(false);
        progressLabel = new JLabel("");
        progressLabel.setFont(progressLabel.getFont().deriveFont(11f));
        progressLabel.setForeground(Color.GRAY);
        progressLabel.setVisible(false);

        // Width toggle group (Fidt / Fixed)
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

        // Clear cache button
        clearCacheBtn = new JButton("Reload Data");
        clearCacheBtn.setToolTipText("Clear cached OHLC data and redownload from Binance");
        clearCacheBtn.addActionListener(e -> clearCacheAndReload());

        // Claude button - opens terminal with Claude CLI
        claudeBtn = new JButton("Claude");
        claudeBtn.setToolTipText("Open Claude CLI to help optimize this strategy");
        claudeBtn.addActionListener(e -> openClaudeTerminal());

        // Codex button - opens terminal with Codex CLI
        codexBtn = new JButton("Codex");
        codexBtn.setToolTipText("Open Codex CLI to help optimize this strategy");
        codexBtn.addActionListener(e -> openCodexTerminal());

        // History button - browse and restore previous versions
        historyBtn = new JButton("History");
        historyBtn.setToolTipText("Browse strategy history and restore previous versions");
        historyBtn.addActionListener(e -> showHistory());

        // Indicator controls panel (extracted to separate class)
        indicatorControls = new IndicatorControlsPanel();
        indicatorControls.setChartPanel(chartPanel);
        indicatorControls.setOnBacktestNeeded(this::runBacktest);

        // Wire up auto-save scheduler
        autoSaveScheduler.setOnSave(this::saveStrategyQuietly);
        autoSaveScheduler.setOnBacktest(this::runBacktest);

        // Wire up backtest coordinator callbacks
        backtestCoordinator.setOnProgress(this::setProgress);
        backtestCoordinator.setOnComplete(this::displayResult);
        backtestCoordinator.setOnStatus(this::setStatus);

        // Wire up change listeners for auto-save and auto-backtest
        symbolCombo.addActionListener(e -> autoSaveScheduler.scheduleUpdate());
        timeframeCombo.addActionListener(e -> autoSaveScheduler.scheduleUpdate());
        durationCombo.addActionListener(e -> autoSaveScheduler.scheduleUpdate());

        // Wire up panel change listeners
        editorPanel.setOnChange(autoSaveScheduler::scheduleUpdate);
        settingsPanel.setOnChange(autoSaveScheduler::scheduleUpdate);
    }

    private void updateDurationOptions() {
        String timeframe = (String) timeframeCombo.getSelectedItem();
        durationCombo.removeAllItems();

        // Provide sensible duration options based on timeframe
        switch (timeframe) {
            case "1m" -> {
                durationCombo.addItem("1 day");
                durationCombo.addItem("3 days");
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
            }
            case "5m" -> {
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("3 months");
            }
            case "15m" -> {
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
            }
            case "30m" -> {
                durationCombo.addItem("1 month");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
            }
            case "1h" -> {
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
            }
            case "4h" -> {
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
            }
            case "1d" -> {
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
            }
            case "1w" -> {
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

        // Progress bar panel (right side of toolbar)
        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        toolbarRight.add(historyBtn);
        toolbarRight.add(Box.createHorizontalStrut(8));
        toolbarRight.add(claudeBtn);
        toolbarRight.add(codexBtn);
        toolbarRight.add(Box.createHorizontalStrut(8));
        toolbarRight.add(clearCacheBtn);
        toolbarRight.add(Box.createHorizontalStrut(8));
        toolbarRight.add(progressLabel);
        toolbarRight.add(progressBar);

        JPanel toolbarPanel = new JPanel(new BorderLayout(0, 0));
        toolbarPanel.add(toolbarLeft, BorderLayout.CENTER);
        toolbarPanel.add(toolbarRight, BorderLayout.EAST);
        toolbarPanel.add(new JSeparator(), BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.add(titleBar, BorderLayout.NORTH);
        topPanel.add(toolbarPanel, BorderLayout.CENTER);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // Left side: Editor only
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.add(editorPanel, BorderLayout.CENTER);

        // Add vertical separator on the right
        JSeparator verticalSeparator = new JSeparator(SwingConstants.VERTICAL);
        leftPanel.add(verticalSeparator, BorderLayout.EAST);

        // Center: Charts
        JPanel centerPanel = new JPanel(new BorderLayout());
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
        mainSplit.setDividerLocation(450);
        mainSplit.setContinuousLayout(true);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightSplit);

        contentPane.add(mainSplit, BorderLayout.CENTER);

        // Status bar
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);

        statusBar = new JLabel(" Ready");
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        bottomPanel.add(statusBar, BorderLayout.CENTER);

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

        // Check if this phase is required by current strategy
        List<String> requiredPhaseIds = strategy.getRequiredPhaseIds();
        if (requiredPhaseIds.contains(phaseId)) {
            SwingUtilities.invokeLater(() -> {
                setStatus("Phase '" + phaseId + "' changed - re-running backtest...");
                runBacktest();
            });
        }
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

    private boolean isCommandAvailable(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", command});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            setStatus("Could not open browser: " + e.getMessage());
        }
    }

    private void openClaudeTerminal() {
        // Check if claude CLI is installed
        if (!isCommandAvailable("claude")) {
            int result = JOptionPane.showConfirmDialog(this,
                "Claude Code CLI is not installed.\n\n" +
                "Would you like to open the installation instructions?",
                "Claude Code Not Found",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                openUrl("https://docs.anthropic.com/en/docs/claude-code");
            }
            return;
        }

        // Open in ~/.tradery where CLAUDE.md provides context
        String traderyDir = System.getProperty("user.home") + "/.tradery";
        String strategyId = strategy.getId();
        String strategyName = strategy.getName();

        // Get current backtest settings for context
        String symbol = (String) symbolCombo.getSelectedItem();
        String timeframe = (String) timeframeCombo.getSelectedItem();
        String duration = (String) durationCombo.getSelectedItem();

        // Build the initial prompt for Claude with full context
        String initialPrompt = String.format(
            "[Launched from Tradery app] " +
            "Currently open: Strategy '%s' (id: %s), " +
            "backtesting %s on %s timeframe for %s. " +
            "Read strategies/%s/strategy.json and strategies/%s/latest.json, " +
            "then help me optimize entry/exit conditions for better performance. " +
            "The app auto-reloads when you save changes to strategy.json.",
            strategyName, strategyId, symbol, timeframe, duration,
            strategyId, strategyId
        );

        String command = String.format(
            "cd '%s' && claude '%s'",
            traderyDir.replace("'", "'\\''"),
            initialPrompt.replace("'", "'\\''")
        );

        try {
            // Use osascript to open Terminal.app with the command (macOS)
            String[] osascript = {
                "osascript", "-e",
                String.format(
                    "tell application \"Terminal\"\n" +
                    "    activate\n" +
                    "    do script \"%s\"\n" +
                    "end tell",
                    command.replace("\\", "\\\\").replace("\"", "\\\"")
                )
            };

            Runtime.getRuntime().exec(osascript);
            setStatus("Opened Claude CLI for " + strategyName);
        } catch (IOException e) {
            setStatus("Error opening terminal: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Could not open Terminal: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openCodexTerminal() {
        // Check if codex CLI is installed
        if (!isCommandAvailable("codex")) {
            int result = JOptionPane.showConfirmDialog(this,
                "OpenAI Codex CLI is not installed.\n\n" +
                "Would you like to open the installation instructions?",
                "Codex CLI Not Found",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                openUrl("https://github.com/openai/codex");
            }
            return;
        }

        // Open in ~/.tradery where CLAUDE.md provides context
        String traderyDir = System.getProperty("user.home") + "/.tradery";
        String strategyId = strategy.getId();
        String strategyName = strategy.getName();

        // Get current backtest settings for context
        String symbol = (String) symbolCombo.getSelectedItem();
        String timeframe = (String) timeframeCombo.getSelectedItem();
        String duration = (String) durationCombo.getSelectedItem();

        // Build the initial prompt for Codex with full context
        String initialPrompt = String.format(
            "[Launched from Tradery app] " +
            "Currently open: Strategy '%s' (id: %s), " +
            "backtesting %s on %s timeframe for %s. " +
            "Read strategies/%s/strategy.json and strategies/%s/latest.json, " +
            "then help me optimize entry/exit conditions for better performance. " +
            "The app auto-reloads when you save changes to strategy.json.",
            strategyName, strategyId, symbol, timeframe, duration,
            strategyId, strategyId
        );

        String command = String.format(
            "cd '%s' && codex '%s'",
            traderyDir.replace("'", "'\\''"),
            initialPrompt.replace("'", "'\\''")
        );

        try {
            // Use osascript to open Terminal.app with the command (macOS)
            String[] osascript = {
                "osascript", "-e",
                String.format(
                    "tell application \"Terminal\"\n" +
                    "    activate\n" +
                    "    do script \"%s\"\n" +
                    "end tell",
                    command.replace("\\", "\\\\").replace("\"", "\\\"")
                )
            };

            Runtime.getRuntime().exec(osascript);
            setStatus("Opened Codex CLI for " + strategyName);
        } catch (IOException e) {
            setStatus("Error opening terminal: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Could not open Terminal: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
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
        // Update metrics panel
        metricsPanel.updateMetrics(result.metrics());

        // Update trade table
        tradeTablePanel.updateTrades(result.trades());

        // Update charts with candles from coordinator
        List<Candle> candles = backtestCoordinator.getCurrentCandles();
        if (candles != null && !candles.isEmpty()) {
            chartPanel.updateCharts(candles, result.trades(), result.config().initialCapital());

            // Update indicator controls with current candle data
            indicatorControls.setCurrentCandles(candles);
            indicatorControls.refreshOverlays();
        }
    }

    private void setStatus(String message) {
        statusBar.setText(" " + message);
    }

    private void setProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            if (percentage >= 100 || message.equals("Error") || message.equals("Complete")) {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
            } else {
                progressBar.setVisible(true);
                progressBar.setValue(percentage);
                progressLabel.setVisible(true);
                progressLabel.setText(message);
            }
        });
    }

    private void closeWindow() {
        // Mark as closed in window state
        WindowStateStore.getInstance().setProjectOpen(strategy.getId(), false);

        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        if (phaseWatcher != null) {
            phaseWatcher.stop();
        }
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
