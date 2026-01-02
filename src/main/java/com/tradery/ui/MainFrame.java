package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.engine.BacktestEngine;
import com.tradery.io.FileWatcher;
import com.tradery.io.ResultStore;
import com.tradery.io.StrategyStore;
import com.tradery.model.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Main application window with four-panel layout:
 * - Left: Strategy list and editor
 * - Center-Left: Control panel (settings)
 * - Center-Right: Charts (price + equity)
 * - Right: Metrics and trade table
 */
public class MainFrame extends JFrame {

    private StrategyPanel strategyPanel;
    private ControlPanel toolbar;
    private ChartsPanel chartPanel;
    private MetricsPanel metricsPanel;
    private TradeTablePanel tradeTablePanel;
    private JLabel statusBar;

    // Data stores
    private final StrategyStore strategyStore;
    private final CandleStore candleStore;
    private final ResultStore resultStore;
    private final BacktestEngine backtestEngine;
    private FileWatcher fileWatcher;

    // Current backtest data for charts
    private List<Candle> currentCandles;

    public MainFrame() {
        super(TraderyApp.APP_NAME);

        // Initialize stores
        strategyStore = new StrategyStore(new File(TraderyApp.USER_DIR, "strategies"));
        candleStore = new CandleStore();
        resultStore = new ResultStore();
        backtestEngine = new BacktestEngine();

        initializeFrame();
        initializeComponents();
        layoutComponents();
        startFileWatcher();

        // Load latest result if available
        BacktestResult latest = resultStore.loadLatest();
        if (latest != null) {
            displayResult(latest);
        }
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);

        // macOS-specific settings
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
    }

    private void initializeComponents() {
        strategyPanel = new StrategyPanel(this::onStrategyChanged);
        toolbar = new ControlPanel();
        chartPanel = new ChartsPanel();
        metricsPanel = new MetricsPanel();
        tradeTablePanel = new TradeTablePanel();

        // Wire up toolbar callbacks
        toolbar.setOnNew(strategyPanel::createNewStrategy);
        toolbar.setOnSave(strategyPanel::saveStrategy);
        toolbar.setOnDelete(strategyPanel::deleteStrategy);
        toolbar.setOnRun(this::onRunBacktest);

        // Wire up chart status callback
        chartPanel.setOnStatusUpdate(this::setStatus);
    }

    private void layoutComponents() {
        // Main content pane with padding for macOS title bar
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));
        setContentPane(contentPane);

        // Top: Toolbar with separator
        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.add(toolbar, BorderLayout.CENTER);
        JPanel separator = new JPanel();
        separator.setPreferredSize(new Dimension(0, 1));
        separator.setBackground(new Color(200, 200, 200));
        topPanel.add(separator, BorderLayout.SOUTH);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // Center: Charts
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(chartPanel, BorderLayout.CENTER);

        // Right side: Metrics above trade table
        JPanel rightPanel = new JPanel(new BorderLayout(0, 8));
        rightPanel.add(metricsPanel, BorderLayout.NORTH);
        rightPanel.add(tradeTablePanel, BorderLayout.CENTER);

        // Wrap strategy panel with vertical separator on right
        JPanel leftWrapper = new JPanel(new BorderLayout(0, 0));
        leftWrapper.add(strategyPanel, BorderLayout.CENTER);
        JPanel verticalSeparator = new JPanel();
        verticalSeparator.setPreferredSize(new Dimension(1, 0));
        verticalSeparator.setBackground(new Color(200, 200, 200));
        leftWrapper.add(verticalSeparator, BorderLayout.EAST);

        // First split: Left (strategies) | Center+Right
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        leftSplit.setBorder(null);
        leftSplit.setDividerSize(6);
        leftSplit.setDividerLocation(280);

        // Second split: Center (charts) | Right (metrics+trades)
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        rightSplit.setBorder(null);
        rightSplit.setDividerSize(6);
        rightSplit.setResizeWeight(0.65);

        rightSplit.setLeftComponent(centerPanel);
        rightSplit.setRightComponent(rightPanel);

        leftSplit.setLeftComponent(leftWrapper);
        leftSplit.setRightComponent(rightSplit);

        contentPane.add(leftSplit, BorderLayout.CENTER);

        // Status bar
        // Bottom: Status bar with separator line above
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
        JPanel bottomSeparator = new JPanel();
        bottomSeparator.setPreferredSize(new Dimension(0, 1));
        bottomSeparator.setBackground(new Color(200, 200, 200));
        bottomPanel.add(bottomSeparator, BorderLayout.NORTH);

        statusBar = new JLabel(" Ready • ~/.tradery/");
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        statusBar.setFont(statusBar.getFont().deriveFont(11f));
        bottomPanel.add(statusBar, BorderLayout.CENTER);

        contentPane.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void startFileWatcher() {
        Path strategiesPath = new File(TraderyApp.USER_DIR, "strategies").toPath();
        fileWatcher = new FileWatcher(strategiesPath, this::onFileChanged);

        try {
            fileWatcher.start();
        } catch (IOException e) {
            System.err.println("Failed to start file watcher: " + e.getMessage());
        }
    }

    private void onFileChanged(Path path) {
        // File was modified externally (e.g., by Claude Code)
        SwingUtilities.invokeLater(() -> {
            setStatus("File changed: " + path.getFileName() + " - reloading...");

            // Reload strategies
            strategyPanel.reloadStrategies();

            // Auto-run backtest if a strategy is selected
            Strategy selected = strategyPanel.getSelectedStrategy();
            if (selected != null) {
                setStatus("Auto-running backtest for " + selected.getName() + "...");
                runBacktest(selected);
            }
        });
    }

    private void onStrategyChanged(String strategyId) {
        System.out.println("Strategy selected: " + strategyId);
        if (statusBar != null) {
            setStatus("Selected: " + strategyId);
        }
    }

    private void onRunBacktest() {
        Strategy strategy = strategyPanel.getSelectedStrategy();
        if (strategy == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a strategy first",
                "No Strategy Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        runBacktest(strategy);
    }

    private void runBacktest(Strategy strategy) {
        // Get config from control panel
        String symbol = toolbar.getSymbol();
        String resolution = toolbar.getResolution();
        double capital = metricsPanel.getInitialCapital();
        String sizing = metricsPanel.getPositionSizing();

        // Parse position sizing
        String sizingType = "fixed_percent";
        double sizingValue = 10.0;

        if (sizing.startsWith("Fixed ") && sizing.contains("%")) {
            sizingType = "fixed_percent";
            sizingValue = Double.parseDouble(sizing.replace("Fixed ", "").replace("%", ""));
        } else if (sizing.startsWith("$")) {
            sizingType = "fixed_amount";
            sizingValue = Double.parseDouble(sizing.replace("$", "").replace(" per trade", "").replace(",", ""));
        } else if (sizing.startsWith("Risk ")) {
            sizingType = "risk_percent";
            sizingValue = Double.parseDouble(sizing.replace("Risk ", "").replace("% per trade", ""));
        } else if (sizing.equals("Kelly Criterion")) {
            sizingType = "kelly";
            sizingValue = 0; // Not used for Kelly
        } else if (sizing.equals("Volatility-based")) {
            sizingType = "volatility";
            sizingValue = 0; // Not used for volatility
        }

        double commission = metricsPanel.getTotalCommission();

        BacktestConfig config = new BacktestConfig(
            symbol,
            resolution,
            System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000), // 1 year ago
            System.currentTimeMillis(),
            capital,
            sizingType,
            sizingValue,
            commission  // Fee + slippage from settings
        );

        // Run in background
        toolbar.setRunEnabled(false);
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
                toolbar.setRunEnabled(true);
                try {
                    BacktestResult result = get();

                    // Save result
                    resultStore.save(result);

                    // Display result
                    displayResult(result);

                    metricsPanel.setProgress(100, "Complete");
                    setStatus(result.getSummary());

                } catch (Exception e) {
                    metricsPanel.setProgress(0, "Error");
                    setStatus("Error: " + e.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                        "Backtest failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
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

    @Override
    public void dispose() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        super.dispose();
    }
}
