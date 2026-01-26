package com.tradery.desk.ui;

import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.core.model.Candle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Main window for Tradery Desk.
 * Displays connection status, active strategies, price chart, and signal log.
 */
public class DeskFrame extends JFrame {

    private final StatusPanel statusPanel;
    private final StrategyListPanel strategyListPanel;
    private final SignalLogPanel signalLogPanel;
    private final PriceChartPanel priceChartPanel;

    // Chart control checkboxes
    private JCheckBox volumeCheckBox;
    private JCheckBox sma20CheckBox;
    private JCheckBox sma50CheckBox;
    private JCheckBox ema20CheckBox;
    private JCheckBox bbCheckBox;
    private JCheckBox vwapCheckBox;
    private JCheckBox rsiCheckBox;
    private JCheckBox macdCheckBox;
    private JCheckBox atrCheckBox;
    private JCheckBox stochasticCheckBox;
    private JCheckBox adxCheckBox;

    private Runnable onClose;

    public DeskFrame() {
        super("Tradery Desk");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));

        // Status panel at top
        statusPanel = new StatusPanel();
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(statusPanel, BorderLayout.CENTER);
        topPanel.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Left panel: Strategies and Signals
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setPreferredSize(new Dimension(280, 0));

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        leftSplit.setResizeWeight(0.3);
        leftSplit.setBorder(null);

        strategyListPanel = new StrategyListPanel();
        leftSplit.setTopComponent(strategyListPanel);

        signalLogPanel = new SignalLogPanel();
        leftSplit.setBottomComponent(signalLogPanel);

        leftPanel.add(leftSplit, BorderLayout.CENTER);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        // Center: Chart toolbar + Price chart
        JPanel chartPanel = new JPanel(new BorderLayout(0, 0));

        // Chart toolbar
        JToolBar chartToolbar = createChartToolbar();
        chartPanel.add(chartToolbar, BorderLayout.NORTH);

        // Price chart
        priceChartPanel = new PriceChartPanel();
        chartPanel.add(priceChartPanel, BorderLayout.CENTER);

        // Main horizontal split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(1);
        mainSplit.setDividerLocation(280);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(chartPanel);

        mainPanel.add(mainSplit, BorderLayout.CENTER);

        setContentPane(mainPanel);

        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (onClose != null) {
                    onClose.run();
                }
                dispose();
            }
        });
    }

    private JToolBar createChartToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorderPainted(false);

        // Volume toggle
        volumeCheckBox = new JCheckBox("Volume");
        volumeCheckBox.setFocusPainted(false);
        volumeCheckBox.addActionListener(e -> {
            priceChartPanel.setVolumeEnabled(volumeCheckBox.isSelected());
        });
        toolbar.add(volumeCheckBox);

        toolbar.addSeparator();

        // Overlays label
        toolbar.add(new JLabel("Overlays: "));

        // SMA 20
        sma20CheckBox = new JCheckBox("SMA(20)");
        sma20CheckBox.setFocusPainted(false);
        sma20CheckBox.addActionListener(e -> updateOverlays());
        toolbar.add(sma20CheckBox);

        // SMA 50
        sma50CheckBox = new JCheckBox("SMA(50)");
        sma50CheckBox.setFocusPainted(false);
        sma50CheckBox.addActionListener(e -> updateOverlays());
        toolbar.add(sma50CheckBox);

        // EMA 20
        ema20CheckBox = new JCheckBox("EMA(20)");
        ema20CheckBox.setFocusPainted(false);
        ema20CheckBox.addActionListener(e -> updateOverlays());
        toolbar.add(ema20CheckBox);

        // Bollinger Bands
        bbCheckBox = new JCheckBox("BB(20,2)");
        bbCheckBox.setFocusPainted(false);
        bbCheckBox.addActionListener(e -> updateOverlays());
        toolbar.add(bbCheckBox);

        // VWAP
        vwapCheckBox = new JCheckBox("VWAP");
        vwapCheckBox.setFocusPainted(false);
        vwapCheckBox.addActionListener(e -> updateOverlays());
        toolbar.add(vwapCheckBox);

        toolbar.addSeparator();

        // Indicators label
        toolbar.add(new JLabel("Indicators: "));

        // RSI
        rsiCheckBox = new JCheckBox("RSI(14)");
        rsiCheckBox.setFocusPainted(false);
        rsiCheckBox.addActionListener(e -> {
            priceChartPanel.setRsiEnabled(rsiCheckBox.isSelected());
        });
        toolbar.add(rsiCheckBox);

        // MACD
        macdCheckBox = new JCheckBox("MACD");
        macdCheckBox.setFocusPainted(false);
        macdCheckBox.addActionListener(e -> {
            priceChartPanel.setMacdEnabled(macdCheckBox.isSelected());
        });
        toolbar.add(macdCheckBox);

        // ATR
        atrCheckBox = new JCheckBox("ATR(14)");
        atrCheckBox.setFocusPainted(false);
        atrCheckBox.addActionListener(e -> {
            priceChartPanel.setAtrEnabled(atrCheckBox.isSelected());
        });
        toolbar.add(atrCheckBox);

        // Stochastic
        stochasticCheckBox = new JCheckBox("Stoch(14,3)");
        stochasticCheckBox.setFocusPainted(false);
        stochasticCheckBox.addActionListener(e -> {
            priceChartPanel.setStochasticEnabled(stochasticCheckBox.isSelected());
        });
        toolbar.add(stochasticCheckBox);

        // ADX
        adxCheckBox = new JCheckBox("ADX(14)");
        adxCheckBox.setFocusPainted(false);
        adxCheckBox.addActionListener(e -> {
            priceChartPanel.setAdxEnabled(adxCheckBox.isSelected());
        });
        toolbar.add(adxCheckBox);

        return toolbar;
    }

    private void updateOverlays() {
        priceChartPanel.clearOverlays();

        if (sma20CheckBox.isSelected()) {
            priceChartPanel.addSmaOverlay(20);
        }
        if (sma50CheckBox.isSelected()) {
            priceChartPanel.addSmaOverlay(50);
        }
        if (ema20CheckBox.isSelected()) {
            priceChartPanel.addEmaOverlay(20);
        }
        if (bbCheckBox.isSelected()) {
            priceChartPanel.addBollingerOverlay(20, 2.0);
        }
        if (vwapCheckBox.isSelected()) {
            priceChartPanel.addVwapOverlay();
        }

        // Refresh chart to show new overlays
        priceChartPanel.getCandlestickChart().updateData(priceChartPanel.getDataProvider());
    }

    /**
     * Set callback for window close.
     */
    public void setOnClose(Runnable callback) {
        this.onClose = callback;
    }

    /**
     * Update connection state display.
     */
    public void updateConnectionState(ConnectionState state) {
        statusPanel.updateState(state);
    }

    /**
     * Update symbol/timeframe display.
     */
    public void updateSymbol(String symbol, String timeframe) {
        statusPanel.updateSymbol(symbol, timeframe);
    }

    /**
     * Update current price display.
     */
    public void updatePrice(double price) {
        statusPanel.updatePrice(price);
    }

    /**
     * Update strategy list.
     */
    public void setStrategies(List<PublishedStrategy> strategies) {
        strategyListPanel.setStrategies(strategies);
    }

    /**
     * Add a signal to the log.
     */
    public void addSignal(SignalEvent signal) {
        signalLogPanel.addSignal(signal);
    }

    /**
     * Clear signal log.
     */
    public void clearSignals() {
        signalLogPanel.clear();
    }

    /**
     * Set historical candles for the chart.
     */
    public void setChartCandles(List<Candle> candles, String symbol, String timeframe) {
        priceChartPanel.setCandles(candles, symbol, timeframe);
    }

    /**
     * Update the current (incomplete) candle on the chart.
     */
    public void updateChartCandle(Candle candle) {
        priceChartPanel.updateCurrentCandle(candle);
    }

    /**
     * Add a completed candle to the chart.
     */
    public void addChartCandle(Candle candle) {
        priceChartPanel.addCandle(candle);
    }
}
