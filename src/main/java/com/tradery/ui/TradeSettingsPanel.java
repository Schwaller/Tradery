package com.tradery.ui;

import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring trade management settings (max trades, spacing).
 */
public class TradeSettingsPanel extends JPanel {

    private JSpinner maxOpenTradesSpinner;
    private JSpinner minCandlesBetweenSpinner;

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public TradeSettingsPanel() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        maxOpenTradesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        minCandlesBetweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

        maxOpenTradesSpinner.addChangeListener(e -> fireChange());
        minCandlesBetweenSpinner.addChangeListener(e -> fireChange());
    }

    private void layoutComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Max Open Trades
        gbc.gridx = 0;
        JLabel maxTradesLabel = new JLabel("Max Open Trades:");
        maxTradesLabel.setForeground(Color.GRAY);
        add(maxTradesLabel, gbc);

        gbc.gridx = 1;
        add(maxOpenTradesSpinner, gbc);

        // Min Candles Between
        gbc.gridx = 2;
        gbc.insets = new Insets(2, 16, 2, 8);
        JLabel minCandlesLabel = new JLabel("Min Candles Between:");
        minCandlesLabel.setForeground(Color.GRAY);
        add(minCandlesLabel, gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(2, 0, 2, 0);
        add(minCandlesBetweenSpinner, gbc);

        // Spacer to push everything left
        gbc.gridx = 4;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createHorizontalGlue(), gbc);

        // Help button (right-aligned)
        gbc.gridx = 5;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(2, 8, 2, 0);
        add(createHelpButton(), gbc);
    }

    private JButton createHelpButton() {
        JButton btn = new JButton("?");
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Strategy Guide");
        btn.addActionListener(e -> StrategyHelpDialog.show(this));
        return btn;
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void loadFrom(Strategy strategy) {
        suppressChangeEvents = true;
        try {
            if (strategy != null) {
                maxOpenTradesSpinner.setValue(strategy.getMaxOpenTrades());
                minCandlesBetweenSpinner.setValue(strategy.getMinCandlesBetweenTrades());
            } else {
                maxOpenTradesSpinner.setValue(1);
                minCandlesBetweenSpinner.setValue(0);
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setMaxOpenTrades(((Number) maxOpenTradesSpinner.getValue()).intValue());
        strategy.setMinCandlesBetweenTrades(((Number) minCandlesBetweenSpinner.getValue()).intValue());
    }
}
