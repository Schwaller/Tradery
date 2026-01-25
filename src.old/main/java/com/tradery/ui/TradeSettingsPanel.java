package com.tradery.ui;

import com.tradery.model.Strategy;
import com.tradery.model.TradeDirection;
import com.tradery.ui.base.ConfigurationPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring trade management settings (direction, max trades, spacing).
 */
public class TradeSettingsPanel extends ConfigurationPanel {

    private JComboBox<TradeDirection> directionCombo;
    private JSpinner maxOpenTradesSpinner;
    private JSpinner minCandlesBetweenSpinner;

    public TradeSettingsPanel() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        directionCombo = new JComboBox<>(TradeDirection.values());
        directionCombo.setSelectedItem(TradeDirection.LONG);
        maxOpenTradesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        minCandlesBetweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

        directionCombo.addActionListener(e -> fireChange());
        maxOpenTradesSpinner.addChangeListener(e -> fireChange());
        minCandlesBetweenSpinner.addChangeListener(e -> fireChange());
    }

    private void layoutComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Direction
        gbc.gridx = 0;
        JLabel directionLabel = new JLabel("Direction:");
        directionLabel.setForeground(Color.GRAY);
        add(directionLabel, gbc);

        gbc.gridx = 1;
        add(directionCombo, gbc);

        // Max Open Trades
        gbc.gridx = 2;
        gbc.insets = new Insets(2, 16, 2, 8);
        JLabel maxTradesLabel = new JLabel("Max Open Trades:");
        maxTradesLabel.setForeground(Color.GRAY);
        add(maxTradesLabel, gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(2, 0, 2, 8);
        add(maxOpenTradesSpinner, gbc);

        // Min Candles Between
        gbc.gridx = 4;
        gbc.insets = new Insets(2, 16, 2, 8);
        JLabel minCandlesLabel = new JLabel("Min Candles Between:");
        minCandlesLabel.setForeground(Color.GRAY);
        add(minCandlesLabel, gbc);

        gbc.gridx = 5;
        gbc.insets = new Insets(2, 0, 2, 0);
        add(minCandlesBetweenSpinner, gbc);

        // Spacer to push everything left
        gbc.gridx = 6;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createHorizontalGlue(), gbc);
    }

    public void loadFrom(Strategy strategy) {
        setSuppressChangeEvents(true);
        try {
            if (strategy != null) {
                directionCombo.setSelectedItem(strategy.getDirection());
                maxOpenTradesSpinner.setValue(strategy.getMaxOpenTrades());
                minCandlesBetweenSpinner.setValue(strategy.getMinCandlesBetweenTrades());
            } else {
                directionCombo.setSelectedItem(TradeDirection.LONG);
                maxOpenTradesSpinner.setValue(1);
                minCandlesBetweenSpinner.setValue(0);
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setDirection((TradeDirection) directionCombo.getSelectedItem());
        strategy.setMaxOpenTrades(((Number) maxOpenTradesSpinner.getValue()).intValue());
        strategy.setMinCandlesBetweenTrades(((Number) minCandlesBetweenSpinner.getValue()).intValue());
    }
}
