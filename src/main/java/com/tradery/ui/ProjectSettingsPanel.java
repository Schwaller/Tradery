package com.tradery.ui;

import com.tradery.model.PositionSizingType;
import com.tradery.model.Strategy;
import com.tradery.ui.theme.Theme;
import com.tradery.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing project-specific backtest settings.
 * Includes capital, position sizing, fees, slippage, and theme.
 * (Symbol and timeframe are in the toolbar)
 */
public class ProjectSettingsPanel extends JPanel {

    private JSpinner capitalSpinner;
    private JComboBox<String> positionSizingCombo;
    private JSpinner feeSpinner;
    private JSpinner slippageSpinner;
    private JComboBox<String> themeCombo;
    private JLabel capitalLabel;

    private static final String[] POSITION_SIZING_TYPES = {
        "Fixed 1%", "Fixed 5%", "Fixed 10%", "Fixed 20%",
        "$100 per trade", "$500 per trade", "$1000 per trade",
        "$2000 per trade", "$3000 per trade", "$5000 per trade",
        "Risk 1% per trade", "Risk 2% per trade",
        "Kelly Criterion", "Volatility-based",
        "All In (100%)"
    };

    private Strategy strategy;
    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public ProjectSettingsPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Capital spinner: default $10,000, range $100 to $10M
        capitalSpinner = new JSpinner(new SpinnerNumberModel(10000, 100, 10000000, 1000));
        ((JSpinner.NumberEditor) capitalSpinner.getEditor()).getFormat().setGroupingUsed(true);

        positionSizingCombo = new JComboBox<>(POSITION_SIZING_TYPES);

        // Fee: 0.1% default (Binance spot), range 0-1%
        feeSpinner = new JSpinner(new SpinnerNumberModel(0.10, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor feeEditor = new JSpinner.NumberEditor(feeSpinner, "0.00'%'");
        feeSpinner.setEditor(feeEditor);

        // Slippage: 0.05% default, range 0-1%
        slippageSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor slipEditor = new JSpinner.NumberEditor(slippageSpinner, "0.00'%'");
        slippageSpinner.setEditor(slipEditor);

        // Theme selector
        themeCombo = new JComboBox<>();
        for (Theme theme : ThemeManager.getInstance().getAvailableThemes()) {
            themeCombo.addItem(theme.getName());
        }
        themeCombo.setSelectedItem(ThemeManager.getInstance().getCurrentTheme().getName());
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null) {
                ThemeManager.getInstance().setTheme(selected);
            }
        });

        // Wire up change listeners
        capitalSpinner.addChangeListener(e -> fireChange());
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

        // Settings grid with GridBagLayout
        JPanel settingsGrid = new JPanel(new GridBagLayout());
        settingsGrid.setOpaque(false);

        // Capital row
        settingsGrid.add(new JLabel("Capital:"), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 8), 0, 0));
        settingsGrid.add(capitalSpinner, new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));

        // Position sizing row
        settingsGrid.add(new JLabel("Position Size:"), new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 8), 0, 0));
        settingsGrid.add(positionSizingCombo, new GridBagConstraints(1, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));

        // Fee row
        settingsGrid.add(new JLabel("Fee:"), new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 8), 0, 0));
        settingsGrid.add(feeSpinner, new GridBagConstraints(1, 2, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));

        // Slippage row
        settingsGrid.add(new JLabel("Slippage:"), new GridBagConstraints(0, 3, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 8), 0, 0));
        settingsGrid.add(slippageSpinner, new GridBagConstraints(1, 3, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));

        // Theme row
        settingsGrid.add(new JLabel("Theme:"), new GridBagConstraints(0, 4, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 8), 0, 0));
        settingsGrid.add(themeCombo, new GridBagConstraints(1, 4, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));

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
                capitalSpinner.setValue(strategy.getInitialCapital());
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

        strategy.setInitialCapital(((Number) capitalSpinner.getValue()).doubleValue());

        String sizingDisplay = (String) positionSizingCombo.getSelectedItem();
        strategy.setPositionSizingType(displayToPositionSizingType(sizingDisplay));
        strategy.setPositionSizingValue(displayToPositionSizingValue(sizingDisplay));

        strategy.setFeePercent(((Number) feeSpinner.getValue()).doubleValue());
        strategy.setSlippagePercent(((Number) slippageSpinner.getValue()).doubleValue());
    }

    // Getters for direct access

    public double getCapital() {
        return ((Number) capitalSpinner.getValue()).doubleValue();
    }

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

    private String positionSizingTypeToDisplay(PositionSizingType type, double value) {
        if (type == null) type = PositionSizingType.FIXED_PERCENT;

        return switch (type) {
            case FIXED_PERCENT -> {
                if (value == 1.0) yield "Fixed 1%";
                else if (value == 5.0) yield "Fixed 5%";
                else if (value == 20.0) yield "Fixed 20%";
                else yield "Fixed 10%";
            }
            case FIXED_DOLLAR -> {
                if (value == 100.0) yield "$100 per trade";
                else if (value == 500.0) yield "$500 per trade";
                else if (value == 2000.0) yield "$2000 per trade";
                else if (value == 3000.0) yield "$3000 per trade";
                else if (value == 5000.0) yield "$5000 per trade";
                else yield "$1000 per trade";
            }
            case RISK_PERCENT -> value == 1.0 ? "Risk 1% per trade" : "Risk 2% per trade";
            case KELLY -> "Kelly Criterion";
            case VOLATILITY -> "Volatility-based";
            case ALL_IN -> "All In (100%)";
        };
    }

    private PositionSizingType displayToPositionSizingType(String display) {
        if (display == null) return PositionSizingType.FIXED_PERCENT;

        if (display.startsWith("Fixed")) return PositionSizingType.FIXED_PERCENT;
        if (display.startsWith("$")) return PositionSizingType.FIXED_DOLLAR;
        if (display.startsWith("Risk")) return PositionSizingType.RISK_PERCENT;
        if (display.equals("Kelly Criterion")) return PositionSizingType.KELLY;
        if (display.equals("Volatility-based")) return PositionSizingType.VOLATILITY;
        if (display.equals("All In (100%)")) return PositionSizingType.ALL_IN;

        return PositionSizingType.FIXED_PERCENT;
    }

    private double displayToPositionSizingValue(String display) {
        if (display == null) return 10.0;

        return switch (display) {
            case "Fixed 1%" -> 1.0;
            case "Fixed 5%" -> 5.0;
            case "Fixed 10%" -> 10.0;
            case "Fixed 20%" -> 20.0;
            case "$100 per trade" -> 100.0;
            case "$500 per trade" -> 500.0;
            case "$1000 per trade" -> 1000.0;
            case "$2000 per trade" -> 2000.0;
            case "$3000 per trade" -> 3000.0;
            case "$5000 per trade" -> 5000.0;
            case "Risk 1% per trade" -> 1.0;
            case "Risk 2% per trade" -> 2.0;
            case "Kelly Criterion" -> 0.0;
            case "Volatility-based" -> 0.0;
            case "All In (100%)" -> 100.0;
            default -> 10.0;
        };
    }
}
