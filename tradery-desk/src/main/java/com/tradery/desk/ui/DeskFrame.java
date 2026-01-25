package com.tradery.desk.ui;

import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.desk.signal.SignalEvent;
import com.tradery.desk.strategy.PublishedStrategy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Main window for Tradery Desk.
 * Displays connection status, active strategies, and signal log.
 */
public class DeskFrame extends JFrame {

    private final StatusPanel statusPanel;
    private final StrategyListPanel strategyListPanel;
    private final SignalLogPanel signalLogPanel;

    private Runnable onClose;

    public DeskFrame() {
        super("Tradery Desk");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status panel at top
        statusPanel = new StatusPanel();
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Split pane for strategies and signals
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);

        strategyListPanel = new StrategyListPanel();
        splitPane.setTopComponent(strategyListPanel);

        signalLogPanel = new SignalLogPanel();
        splitPane.setBottomComponent(signalLogPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);

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
}
