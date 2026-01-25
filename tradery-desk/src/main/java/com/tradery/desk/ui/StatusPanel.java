package com.tradery.desk.ui;

import com.tradery.desk.feed.BinanceWebSocketClient.ConnectionState;

import javax.swing.*;
import java.awt.*;

/**
 * Status panel showing WebSocket connection state and current price.
 */
public class StatusPanel extends JPanel {

    private final JLabel statusLabel;
    private final JLabel priceLabel;
    private final JLabel symbolLabel;

    private static final Color CONNECTED_COLOR = new Color(0, 180, 0);
    private static final Color DISCONNECTED_COLOR = new Color(180, 0, 0);
    private static final Color CONNECTING_COLOR = new Color(180, 180, 0);

    public StatusPanel() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Left side: connection status
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        statusLabel = new JLabel("●");
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f));
        leftPanel.add(statusLabel);

        symbolLabel = new JLabel("");
        symbolLabel.setFont(symbolLabel.getFont().deriveFont(Font.BOLD, 14f));
        leftPanel.add(symbolLabel);

        add(leftPanel, BorderLayout.WEST);

        // Right side: current price
        priceLabel = new JLabel("");
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(priceLabel, BorderLayout.EAST);

        updateState(ConnectionState.DISCONNECTED);
    }

    /**
     * Update connection state display.
     */
    public void updateState(ConnectionState state) {
        SwingUtilities.invokeLater(() -> {
            switch (state) {
                case CONNECTED -> {
                    statusLabel.setForeground(CONNECTED_COLOR);
                    statusLabel.setToolTipText("Connected");
                }
                case CONNECTING, RECONNECTING -> {
                    statusLabel.setForeground(CONNECTING_COLOR);
                    statusLabel.setToolTipText(state == ConnectionState.CONNECTING
                        ? "Connecting..." : "Reconnecting...");
                }
                case DISCONNECTED -> {
                    statusLabel.setForeground(DISCONNECTED_COLOR);
                    statusLabel.setToolTipText("Disconnected");
                }
            }
        });
    }

    /**
     * Update the symbol display.
     */
    public void updateSymbol(String symbol, String timeframe) {
        SwingUtilities.invokeLater(() ->
            symbolLabel.setText(symbol.toUpperCase() + " " + timeframe)
        );
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
}
