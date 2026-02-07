package com.tradery.desk.ui;

import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.ui.SymbolComboBox;

import javax.swing.*;
import java.awt.*;

/**
 * Full-width top bar showing symbol picker, price, and "X ago" timestamp.
 */
public class MarketInfoBar extends JPanel {

    private final SymbolComboBox symbolCombo;
    private final JLabel priceLabel;
    private final JLabel updatedLabel;
    private volatile long lastUpdateReceived;
    private final Timer agoTimer;

    public MarketInfoBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 12, 4));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));

        symbolCombo = new SymbolComboBox(new SymbolService(), true);
        add(symbolCombo);

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

    /**
     * Get the symbol combo box for external listeners.
     */
    public SymbolComboBox getSymbolCombo() {
        return symbolCombo;
    }

    public void updateSymbol(String symbol, String timeframe) {
        SwingUtilities.invokeLater(() -> symbolCombo.setSelectedSymbol(symbol));
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
