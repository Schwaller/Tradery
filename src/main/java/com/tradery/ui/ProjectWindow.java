package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.FileWatcher;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.io.WindowStateStore;
import com.tradery.model.*;

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
    private JButton runButton;

    // Toolbar controls
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JComboBox<String> durationCombo;
    private JSpinner capitalSpinner;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JToggleButton fitWidthBtn;
    private JToggleButton fixedWidthBtn;
    private JToggleButton fitYBtn;
    private JToggleButton fullYBtn;

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
    private final ResultStore resultStore;
    private final BacktestEngine backtestEngine;
    private FileWatcher fileWatcher;

    // Current backtest data for charts
    private List<Candle> currentCandles;

    // Flag to ignore self-triggered file changes
    private volatile boolean ignoringFileChanges = false;

    // Auto-save and auto-backtest debounce timers
    private Timer autoSaveTimer;
    private Timer autoBacktestTimer;
    private static final int AUTO_SAVE_DELAY_MS = 500;
    private static final int AUTO_BACKTEST_DELAY_MS = 800;

    public ProjectWindow(Strategy strategy, Consumer<String> onClose) {
        super(strategy.getName() + " - " + TraderyApp.APP_NAME);
        this.strategy = strategy;
        this.onClose = onClose;

        // Use shared stores from ApplicationContext
        this.strategyStore = ApplicationContext.getInstance().getStrategyStore();
        this.candleStore = ApplicationContext.getInstance().getCandleStore();
        // Per-project result storage
        this.resultStore = new ResultStore(strategy.getId());
        this.backtestEngine = new BacktestEngine();

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

        capitalSpinner = new JSpinner(new SpinnerNumberModel(10000, 100, 10000000, 1000));
        ((JSpinner.NumberEditor) capitalSpinner.getEditor()).getFormat().setGroupingUsed(true);

        // Toolbar buttons
        runButton = new JButton("Run Backtest");
        runButton.addActionListener(e -> runBacktest());

        // Progress bar (hidden by default)
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(120, 16));
        progressBar.setVisible(false);
        progressLabel = new JLabel("");
        progressLabel.setFont(progressLabel.getFont().deriveFont(11f));
        progressLabel.setForeground(Color.GRAY);
        progressLabel.setVisible(false);

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

        // Auto-save timer
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> saveStrategyQuietly());
        autoSaveTimer.setRepeats(false);

        // Auto-backtest timer (runs after save)
        autoBacktestTimer = new Timer(AUTO_BACKTEST_DELAY_MS, e -> runBacktest());
        autoBacktestTimer.setRepeats(false);

        // Wire up change listeners for auto-save and auto-backtest
        symbolCombo.addActionListener(e -> scheduleAutoUpdate());
        timeframeCombo.addActionListener(e -> scheduleAutoUpdate());
        durationCombo.addActionListener(e -> scheduleAutoUpdate());
        capitalSpinner.addChangeListener(e -> scheduleAutoUpdate());

        // Wire up panel change listeners
        editorPanel.setOnChange(this::scheduleAutoUpdate);
        settingsPanel.setOnChange(this::scheduleAutoUpdate);
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

    private long getDurationMillis() {
        String duration = (String) durationCombo.getSelectedItem();
        if (duration == null) return 365L * 24 * 60 * 60 * 1000; // Default 1 year

        long day = 24L * 60 * 60 * 1000;
        return switch (duration) {
            case "1 day" -> day;
            case "3 days" -> 3 * day;
            case "1 week" -> 7 * day;
            case "2 weeks" -> 14 * day;
            case "1 month" -> 30 * day;
            case "2 months" -> 60 * day;
            case "3 months" -> 90 * day;
            case "6 months" -> 180 * day;
            case "1 year" -> 365 * day;
            case "2 years" -> 2 * 365 * day;
            case "3 years" -> 3 * 365 * day;
            case "5 years" -> 5 * 365 * day;
            case "10 years" -> 10 * 365 * day;
            default -> 365 * day;
        };
    }

    private void scheduleAutoUpdate() {
        if (ignoringFileChanges) return;
        autoSaveTimer.restart();
        autoBacktestTimer.restart();
    }

    private void saveStrategyQuietly() {
        // Apply UI values to strategy
        editorPanel.applyToStrategy(strategy);
        settingsPanel.applyToStrategy(strategy);
        applyToolbarToStrategy();

        // Temporarily ignore file changes
        ignoringFileChanges = true;

        strategyStore.save(strategy);
        setTitle(strategy.getName() + " - " + TraderyApp.APP_NAME);
        setStatus("Auto-saved");

        // Re-enable file watching after a short delay
        Timer timer = new Timer(600, e -> ignoringFileChanges = false);
        timer.setRepeats(false);
        timer.start();
    }

    private void layoutComponents() {
        // Main content pane with padding for macOS title bar
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));
        setContentPane(contentPane);

        // Top: Toolbar with symbol, timeframe, capital, Save and Run buttons
        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbarLeft.add(new JLabel("Symbol:"));
        toolbarLeft.add(symbolCombo);
        toolbarLeft.add(Box.createHorizontalStrut(8));
        toolbarLeft.add(new JLabel("Timeframe:"));
        toolbarLeft.add(timeframeCombo);
        toolbarLeft.add(durationCombo);
        toolbarLeft.add(Box.createHorizontalStrut(8));
        toolbarLeft.add(new JLabel("Capital:"));
        toolbarLeft.add(capitalSpinner);
        toolbarLeft.add(Box.createHorizontalStrut(16));
        toolbarLeft.add(runButton);
        toolbarLeft.add(Box.createHorizontalStrut(12));

        // Toggle buttons
        toolbarLeft.add(fitWidthBtn);
        toolbarLeft.add(fixedWidthBtn);
        toolbarLeft.add(Box.createHorizontalStrut(8));
        toolbarLeft.add(fitYBtn);
        toolbarLeft.add(fullYBtn);

        // Progress bar panel (right side of toolbar)
        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        toolbarRight.add(progressLabel);
        toolbarRight.add(progressBar);

        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.add(toolbarLeft, BorderLayout.CENTER);
        topPanel.add(toolbarRight, BorderLayout.EAST);
        topPanel.add(new JSeparator(), BorderLayout.SOUTH);
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
        mainSplit.setDividerLocation(200);
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
        ignoringFileChanges = true;

        try {
            editorPanel.setStrategy(strategy);
            settingsPanel.setStrategy(strategy);

            // Toolbar controls
            symbolCombo.setSelectedItem(strategy.getSymbol());
            timeframeCombo.setSelectedItem(strategy.getTimeframe());
            capitalSpinner.setValue(strategy.getInitialCapital());
        } finally {
            ignoringFileChanges = false;
        }
    }

    private void applyToolbarToStrategy() {
        strategy.setSymbol((String) symbolCombo.getSelectedItem());
        strategy.setTimeframe((String) timeframeCombo.getSelectedItem());
        strategy.setInitialCapital(((Number) capitalSpinner.getValue()).doubleValue());
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
    }

    private void onStrategyFileChanged(Path path) {
        if (ignoringFileChanges) return;

        SwingUtilities.invokeLater(() -> {
            setStatus("File changed externally - reloading...");

            // Reload strategy from disk
            Strategy reloaded = strategyStore.load(strategy.getId());
            if (reloaded != null) {
                this.strategy = reloaded;
                loadStrategyData();
                setTitle(strategy.getName() + " - " + TraderyApp.APP_NAME);
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

    private void runBacktest() {
        // Apply current UI values
        editorPanel.applyToStrategy(strategy);
        settingsPanel.applyToStrategy(strategy);
        applyToolbarToStrategy();

        // Build config from toolbar and settings panel
        String symbol = (String) symbolCombo.getSelectedItem();
        String resolution = (String) timeframeCombo.getSelectedItem();
        double capital = ((Number) capitalSpinner.getValue()).doubleValue();
        String sizingType = strategy.getPositionSizingType();
        double sizingValue = strategy.getPositionSizingValue();
        double commission = strategy.getTotalCommission();

        BacktestConfig config = new BacktestConfig(
            symbol,
            resolution,
            System.currentTimeMillis() - getDurationMillis(),
            System.currentTimeMillis(),
            capital,
            sizingType,
            sizingValue,
            commission
        );

        // Run in background
        runButton.setEnabled(false);
        setProgress(0, "Starting...");

        SwingWorker<BacktestResult, BacktestEngine.Progress> worker = new SwingWorker<>() {
            @Override
            protected BacktestResult doInBackground() throws Exception {
                // Fetch candles
                publish(new BacktestEngine.Progress(0, 0, 0, "Fetching data from Binance..."));

                currentCandles = candleStore.getCandles(
                    config.symbol(),
                    config.resolution(),
                    config.startDate(),
                    config.endDate()
                );

                if (currentCandles.isEmpty()) {
                    throw new Exception("No candle data available for " + config.symbol());
                }

                // Run backtest
                return backtestEngine.run(strategy, config, currentCandles, this::publish);
            }

            @Override
            protected void process(List<BacktestEngine.Progress> chunks) {
                BacktestEngine.Progress latest = chunks.get(chunks.size() - 1);
                ProjectWindow.this.setProgress(latest.percentage(), latest.message());
                setStatus(latest.message());
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    BacktestResult result = get();

                    // Save result to per-project storage
                    resultStore.save(result);

                    // Display result
                    displayResult(result);

                    ProjectWindow.this.setProgress(100, "Complete");
                    setStatus(result.getSummary());

                } catch (Exception e) {
                    ProjectWindow.this.setProgress(0, "Error");
                    setStatus("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    private void displayResult(BacktestResult result) {
        // Update metrics panel
        metricsPanel.updateMetrics(result.metrics());

        // Update trade table
        tradeTablePanel.updateTrades(result.trades());

        // Update charts
        if (currentCandles != null && !currentCandles.isEmpty()) {
            chartPanel.updateCharts(currentCandles, result.trades(), result.config().initialCapital());
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

    public void bringToFront() {
        toFront();
        requestFocus();
    }
}
