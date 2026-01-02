package com.tradery.ui;

import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing project-specific backtest settings.
 * Includes position sizing, fees, and slippage.
 * (Symbol, timeframe, and capital are in the toolbar)
 */
public class ProjectSettingsPanel extends JPanel {

    private JComboBox<String> positionSizingCombo;
    private JSpinner feeSpinner;
    private JSpinner slippageSpinner;

    private static final String[] POSITION_SIZING_TYPES = {
        "Fixed 1%", "Fixed 5%", "Fixed 10%",
        "$100 per trade", "$500 per trade", "$1000 per trade",
        "Risk 1% per trade", "Risk 2% per trade",
        "Kelly Criterion", "Volatility-based"
    };

    private Strategy strategy;
    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public ProjectSettingsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Color.WHITE);
        setOpaque(true);

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        positionSizingCombo = new JComboBox<>(POSITION_SIZING_TYPES);

        // Fee: 0.1% default (Binance spot), range 0-1%
        feeSpinner = new JSpinner(new SpinnerNumberModel(0.10, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor feeEditor = new JSpinner.NumberEditor(feeSpinner, "0.00'%'");
        feeSpinner.setEditor(feeEditor);

        // Slippage: 0.05% default, range 0-1%
        slippageSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor slipEditor = new JSpinner.NumberEditor(slippageSpinner, "0.00'%'");
        slippageSpinner.setEditor(slipEditor);

        // Wire up change listeners
        positionSizingCombo.addActionListener(e -> fireChange());
        feeSpinner.addChangeListener(e -> fireChange());
        slippageSpinner.addChangeListener(e -> fireChange());
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void layoutComponents() {
        // Title
        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        // Position sizing row
        JPanel sizePanel = new JPanel(new BorderLayout(8, 0));
        sizePanel.setOpaque(false);
        sizePanel.add(new JLabel("Position Size:"), BorderLayout.WEST);
        sizePanel.add(positionSizingCombo, BorderLayout.CENTER);

        // Fee row
        JPanel feePanel = new JPanel(new BorderLayout(8, 0));
        feePanel.setOpaque(false);
        feePanel.add(new JLabel("Fee:"), BorderLayout.WEST);
        feePanel.add(feeSpinner, BorderLayout.CENTER);

        // Slippage row
        JPanel slippagePanel = new JPanel(new BorderLayout(8, 0));
        slippagePanel.setOpaque(false);
        slippagePanel.add(new JLabel("Slippage:"), BorderLayout.WEST);
        slippagePanel.add(slippageSpinner, BorderLayout.CENTER);

        // All settings in a grid
        JPanel settingsGrid = new JPanel(new GridLayout(3, 1, 0, 4));
        settingsGrid.setOpaque(false);
        settingsGrid.add(sizePanel);
        settingsGrid.add(feePanel);
        settingsGrid.add(slippagePanel);

        add(title, BorderLayout.NORTH);
        add(settingsGrid, BorderLayout.CENTER);
    }

    /**
     * Set the strategy to edit settings for
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        suppressChangeEvents = true;

        try {
            if (strategy != null) {
                positionSizingCombo.setSelectedItem(positionSizingTypeToDisplay(strategy.getPositionSizingType(), strategy.getPositionSizingValue()));
                feeSpinner.setValue(strategy.getFeePercent());
                slippageSpinner.setValue(strategy.getSlippagePercent());
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;

        String sizingDisplay = (String) positionSizingCombo.getSelectedItem();
        strategy.setPositionSizingType(displayToPositionSizingType(sizingDisplay));
        strategy.setPositionSizingValue(displayToPositionSizingValue(sizingDisplay));

        strategy.setFeePercent(((Number) feeSpinner.getValue()).doubleValue());
        strategy.setSlippagePercent(((Number) slippageSpinner.getValue()).doubleValue());
    }

    // Getters for direct access

    public String getPositionSizing() {
        return (String) positionSizingCombo.getSelectedItem();
    }

    public double getFeePercent() {
        return ((Number) feeSpinner.getValue()).doubleValue();
    }

    public double getSlippagePercent() {
        return ((Number) slippageSpinner.getValue()).doubleValue();
    }

    public double getTotalCommission() {
        return (getFeePercent() + getSlippagePercent()) / 100.0;
    }

    // Conversion helpers

    private String positionSizingTypeToDisplay(String type, double value) {
        if (type == null) type = "fixed_percent";

        return switch (type) {
            case "fixed_percent" -> {
                if (value == 1.0) yield "Fixed 1%";
                else if (value == 5.0) yield "Fixed 5%";
                else yield "Fixed 10%";
            }
            case "fixed_dollar" -> {
                if (value == 100.0) yield "$100 per trade";
                else if (value == 500.0) yield "$500 per trade";
                else yield "$1000 per trade";
            }
            case "risk_percent" -> value == 1.0 ? "Risk 1% per trade" : "Risk 2% per trade";
            case "kelly" -> "Kelly Criterion";
            case "volatility" -> "Volatility-based";
            default -> "Fixed 10%";
        };
    }

    private String displayToPositionSizingType(String display) {
        if (display == null) return "fixed_percent";

        if (display.startsWith("Fixed")) return "fixed_percent";
        if (display.startsWith("$")) return "fixed_dollar";
        if (display.startsWith("Risk")) return "risk_percent";
        if (display.equals("Kelly Criterion")) return "kelly";
        if (display.equals("Volatility-based")) return "volatility";

        return "fixed_percent";
    }

    private double displayToPositionSizingValue(String display) {
        if (display == null) return 10.0;

        return switch (display) {
            case "Fixed 1%" -> 1.0;
            case "Fixed 5%" -> 5.0;
            case "Fixed 10%" -> 10.0;
            case "$100 per trade" -> 100.0;
            case "$500 per trade" -> 500.0;
            case "$1000 per trade" -> 1000.0;
            case "Risk 1% per trade" -> 1.0;
            case "Risk 2% per trade" -> 2.0;
            case "Kelly Criterion" -> 0.0;
            case "Volatility-based" -> 0.0;
            default -> 10.0;
        };
    }
}
