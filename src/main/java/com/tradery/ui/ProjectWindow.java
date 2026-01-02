package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.FileWatcher;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.model.*;

import javax.swing.*;
import java.awt.*;
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
    private JSpinner capitalSpinner;

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

    // Auto-save debounce timer
    private Timer autoSaveTimer;
    private static final int AUTO_SAVE_DELAY_MS = 1000;

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

        // Load latest result for this project
        BacktestResult latest = resultStore.loadLatest();
        if (latest != null) {
            displayResult(latest);
        }
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        // macOS-specific settings
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });
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

        capitalSpinner = new JSpinner(new SpinnerNumberModel(10000, 100, 10000000, 1000));
        ((JSpinner.NumberEditor) capitalSpinner.getEditor()).getFormat().setGroupingUsed(true);
        capitalSpinner.setPreferredSize(new Dimension(100, capitalSpinner.getPreferredSize().height));

        // Toolbar buttons
        runButton = new JButton("Run Backtest");
        runButton.addActionListener(e -> runBacktest());

        // Auto-save timer
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> saveStrategyQuietly());
        autoSaveTimer.setRepeats(false);

        // Wire up change listeners for auto-save
        symbolCombo.addActionListener(e -> scheduleAutoSave());
        timeframeCombo.addActionListener(e -> scheduleAutoSave());
        capitalSpinner.addChangeListener(e -> scheduleAutoSave());

        // Wire up panel change listeners
        editorPanel.setOnChange(this::scheduleAutoSave);
        settingsPanel.setOnChange(this::scheduleAutoSave);
    }

    private void scheduleAutoSave() {
        if (ignoringFileChanges) return;
        autoSaveTimer.restart();
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
        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbarPanel.add(new JLabel("Symbol:"));
        toolbarPanel.add(symbolCombo);
        toolbarPanel.add(Box.createHorizontalStrut(8));
        toolbarPanel.add(new JLabel("Timeframe:"));
        toolbarPanel.add(timeframeCombo);
        toolbarPanel.add(Box.createHorizontalStrut(8));
        toolbarPanel.add(new JLabel("Capital:"));
        toolbarPanel.add(capitalSpinner);
        toolbarPanel.add(Box.createHorizontalStrut(16));
        toolbarPanel.add(runButton);

        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.add(toolbarPanel, BorderLayout.CENTER);
        JPanel separator = new JPanel();
        separator.setPreferredSize(new Dimension(0, 1));
        separator.setBackground(new Color(200, 200, 200));
        topPanel.add(separator, BorderLayout.SOUTH);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // Left side: Editor only
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.add(editorPanel, BorderLayout.CENTER);

        // Add vertical separator on the right
        JPanel verticalSeparator = new JPanel();
        verticalSeparator.setPreferredSize(new Dimension(1, 0));
        verticalSeparator.setBackground(new Color(200, 200, 200));
        leftPanel.add(verticalSeparator, BorderLayout.EAST);

        // Center: Charts
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(chartPanel, BorderLayout.CENTER);

        // Right side: Settings above Metrics above Trade table
        // Metrics panel with separator line above
        JPanel metricsWrapper = new JPanel(new BorderLayout(0, 0));
        JPanel metricsSeparator = new JPanel();
        metricsSeparator.setPreferredSize(new Dimension(0, 1));
        metricsSeparator.setBackground(new Color(200, 200, 200));
        metricsWrapper.add(metricsSeparator, BorderLayout.NORTH);
        metricsWrapper.add(metricsPanel, BorderLayout.CENTER);

        JPanel rightTopPanel = new JPanel(new BorderLayout(0, 0));
        rightTopPanel.add(settingsPanel, BorderLayout.NORTH);
        rightTopPanel.add(metricsWrapper, BorderLayout.CENTER);

        JPanel rightContent = new JPanel(new BorderLayout(0, 0));
        rightContent.add(rightTopPanel, BorderLayout.NORTH);
        rightContent.add(tradeTablePanel, BorderLayout.CENTER);

        // Wrap with vertical separator on left
        JPanel rightVerticalSeparator = new JPanel();
        rightVerticalSeparator.setPreferredSize(new Dimension(1, 0));
        rightVerticalSeparator.setBackground(new Color(200, 200, 200));

        JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
        rightPanel.setPreferredSize(new Dimension(280, 0));
        rightPanel.add(rightVerticalSeparator, BorderLayout.WEST);
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
        JPanel bottomSeparator = new JPanel();
        bottomSeparator.setPreferredSize(new Dimension(0, 1));
        bottomSeparator.setBackground(new Color(200, 200, 200));
        bottomPanel.add(bottomSeparator, BorderLayout.NORTH);

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
            System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000), // 1 year ago
            System.currentTimeMillis(),
            capital,
            sizingType,
            sizingValue,
            commission
        );

        // Run in background
        runButton.setEnabled(false);
        metricsPanel.setProgress(0, "Starting...");

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
                metricsPanel.setProgress(latest.percentage(), latest.message());
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

                    metricsPanel.setProgress(100, "Complete");
                    setStatus(result.getSummary());

                } catch (Exception e) {
                    metricsPanel.setProgress(0, "Error");
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

    private void closeWindow() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        if (onClose != null) {
            onClose.accept(strategy.getId());
        }
        dispose();
    }

    public String getStrategyId() {
        return strategy.getId();
    }

    public void bringToFront() {
        toFront();
        requestFocus();
    }
}
