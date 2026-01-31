package com.tradery.desk.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Full-width top bar showing exchange, pair, price, and "X ago" timestamp.
 */
public class MarketInfoBar extends JPanel {

    private final JLabel exchangeLabel;
    private final JLabel pairLabel;
    private final JLabel priceLabel;
    private final JLabel updatedLabel;
    private volatile long lastUpdateReceived;
    private final Timer agoTimer;

    public MarketInfoBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 12, 4));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));

        exchangeLabel = new JLabel("Binance Futures");
        exchangeLabel.setFont(exchangeLabel.getFont().deriveFont(Font.BOLD, 10f));
        exchangeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(exchangeLabel);

        pairLabel = new JLabel("\u2014");
        pairLabel.setFont(pairLabel.getFont().deriveFont(Font.BOLD, 13f));
        add(pairLabel);

        priceLabel = new JLabel("\u2014");
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        add(priceLabel);

        updatedLabel = new JLabel("");
        updatedLabel.setFont(updatedLabel.getFont().deriveFont(Font.PLAIN, 9f));
        updatedLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(updatedLabel);

        agoTimer = new Timer(1000, e -> refreshAgoLabel());
        agoTimer.start();
    }

    public void updateSymbol(String symbol, String timeframe) {
        SwingUtilities.invokeLater(() -> pairLabel.setText(symbol + "  " + timeframe));
    }

    public void updatePrice(double price, long exchangeTimestamp) {
        this.lastUpdateReceived = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            if (Double.isNaN(price)) {
                priceLabel.setText("\u2014");
                updatedLabel.setText("");
            } else {
                priceLabel.setText(String.format("%,.2f", price));
                refreshAgoLabel();
            }
        });
    }

    private void refreshAgoLabel() {
        if (lastUpdateReceived == 0) return;
        long agoMs = System.currentTimeMillis() - lastUpdateReceived;
        String agoText;
        if (agoMs < 1000) agoText = "just now";
        else if (agoMs < 60_000) agoText = (agoMs / 1000) + "s ago";
        else if (agoMs < 3_600_000) agoText = (agoMs / 60_000) + "m ago";
        else agoText = (agoMs / 3_600_000) + "h ago";
        updatedLabel.setText(agoText);
    }

    public void dispose() {
        agoTimer.stop();
    }
}
