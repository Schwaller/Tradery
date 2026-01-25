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

        // Center: Price chart
        priceChartPanel = new PriceChartPanel();

        // Main horizontal split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(1);
        mainSplit.setDividerLocation(280);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(priceChartPanel);

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
