package com.tradery.desk.ui;

import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;
import com.tradery.ui.controls.StatusBadge;
import com.tradery.ui.status.MemoryStatusPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Status bar showing WebSocket connection state, symbol/timeframe, memory, and current price.
 */
public class StatusPanel extends JPanel {

    private final StatusBadge connectionBadge;
    private final StatusBadge symbolBadge;
    private final MemoryStatusPanel memoryStatusPanel;
    private final JLabel priceLabel;

    public StatusPanel() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Left side: connection + symbol + memory badges
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        connectionBadge = new StatusBadge("Disconnected");
        connectionBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
        leftPanel.add(connectionBadge);

        symbolBadge = new StatusBadge("");
        symbolBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
        symbolBadge.setVisible(false);
        leftPanel.add(symbolBadge);

        memoryStatusPanel = new MemoryStatusPanel();
        leftPanel.add(memoryStatusPanel);

        add(leftPanel, BorderLayout.WEST);

        // Right side: current price
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);

        priceLabel = new JLabel("");
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        rightPanel.add(priceLabel);

        add(rightPanel, BorderLayout.EAST);

        updateState(ConnectionState.DISCONNECTED);
    }

    /**
     * Update connection state display.
     */
    public void updateState(ConnectionState state) {
        SwingUtilities.invokeLater(() -> {
            switch (state) {
                case CONNECTED -> {
                    connectionBadge.setText("Connected");
                    connectionBadge.setStatusColor(StatusBadge.BG_OK, StatusBadge.FG_OK);
                    connectionBadge.setToolTipText("Connected");
                }
                case CONNECTING, RECONNECTING -> {
                    connectionBadge.setText(state == ConnectionState.CONNECTING ? "Connecting" : "Reconnecting");
                    connectionBadge.setStatusColor(StatusBadge.BG_WARNING, StatusBadge.FG_WARNING);
                    connectionBadge.setToolTipText(state == ConnectionState.CONNECTING
                        ? "Connecting..." : "Reconnecting...");
                }
                case DISCONNECTED -> {
                    connectionBadge.setText("Disconnected");
                    connectionBadge.setStatusColor(StatusBadge.BG_ERROR, StatusBadge.FG_ERROR);
                    connectionBadge.setToolTipText("Disconnected");
                }
            }
        });
    }

    /**
     * Update the symbol display.
     */
    public void updateSymbol(String symbol, String timeframe) {
        SwingUtilities.invokeLater(() -> {
            symbolBadge.setText(symbol.toUpperCase() + " " + timeframe);
            symbolBadge.setStatusColor(StatusBadge.BG_IDLE, StatusBadge.FG_IDLE);
            symbolBadge.setVisible(true);
        });
    }

    /**
     * Update the current price display.
     */
    public void updatePrice(double price) {
        SwingUtilities.invokeLater(() -> {
            if (Double.isNaN(price)) {
                priceLabel.setText("");
            } else {
                priceLabel.setText(String.format("%,.2f", price));
            }
        });
    }

    public void dispose() {
        memoryStatusPanel.dispose();
    }
}
