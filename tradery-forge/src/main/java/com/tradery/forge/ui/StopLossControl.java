package com.tradery.forge.ui;

import com.tradery.core.model.StopLossType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Three-part control for stop loss configuration.
 * Displays: [type dropdown] [value spinner] [unit dropdown]
 *
 * Types: None, Clear, Fixed, Trailing
 * Units: %, ATR
 */
public class StopLossControl extends JPanel {

    private final JComboBox<String> typeCombo;
    private final JSpinner valueSpinner;
    private final JComboBox<String> unitCombo;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private static final String[] TYPES = {"None", "Clear", "Fixed", "Trail"};
    private static final String[] UNITS = {"%", "ATR"};

    public StopLossControl() {
        this(2.0, 0.1, 100.0, 0.5);
    }

    public StopLossControl(double initialValue, double min, double max, double step) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);

        typeCombo = new JComboBox<>(TYPES);
        typeCombo.setPreferredSize(new Dimension(65, typeCombo.getPreferredSize().height));

        valueSpinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, step));
        valueSpinner.setPreferredSize(new Dimension(60, valueSpinner.getPreferredSize().height));

        unitCombo = new JComboBox<>(UNITS);
        unitCombo.setPreferredSize(new Dimension(65, unitCombo.getPreferredSize().height));

        add(typeCombo);
        add(valueSpinner);
        add(unitCombo);

        // Wire listeners
        typeCombo.addActionListener(e -> {
            updateVisibility();
            fireChange();
        });
        valueSpinner.addChangeListener(e -> fireChange());
        unitCombo.addActionListener(e -> fireChange());

        updateVisibility();
    }

    private void updateVisibility() {
        int idx = typeCombo.getSelectedIndex();
        boolean needsValue = idx >= 2; // Fixed or Trail need value
        valueSpinner.setVisible(needsValue);
        unitCombo.setVisible(needsValue);
        revalidate();
    }

    public StopLossType getStopLossType() {
        int typeIdx = typeCombo.getSelectedIndex();
        boolean isAtr = unitCombo.getSelectedIndex() == 1;

        return switch (typeIdx) {
            case 1 -> StopLossType.CLEAR;
            case 2 -> isAtr ? StopLossType.FIXED_ATR : StopLossType.FIXED_PERCENT;
            case 3 -> isAtr ? StopLossType.TRAILING_ATR : StopLossType.TRAILING_PERCENT;
            default -> StopLossType.NONE;
        };
    }

    public void setStopLossType(StopLossType type) {
        if (type == null) type = StopLossType.NONE;

        // Set type combo
        typeCombo.setSelectedIndex(switch (type) {
            case CLEAR -> 1;
            case FIXED_PERCENT, FIXED_ATR -> 2;
            case TRAILING_PERCENT, TRAILING_ATR -> 3;
            default -> 0;
        });

        // Set unit combo
        unitCombo.setSelectedIndex(type.isAtr() ? 1 : 0);

        updateVisibility();
    }

    public double getValue() {
        return ((Number) valueSpinner.getValue()).doubleValue();
    }

    public void setValue(double value) {
        valueSpinner.setValue(value);
    }

    public void setValues(StopLossType type, Double value) {
        setStopLossType(type);
        if (value != null) {
            valueSpinner.setValue(value);
        }
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChange() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        typeCombo.setEnabled(enabled);
        valueSpinner.setEnabled(enabled);
        unitCombo.setEnabled(enabled);
    }
}
