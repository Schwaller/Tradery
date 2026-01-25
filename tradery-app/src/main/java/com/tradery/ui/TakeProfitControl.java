package com.tradery.ui;

import com.tradery.model.TakeProfitType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Three-part control for take profit configuration.
 * Displays: [type dropdown] [value spinner] [unit dropdown]
 *
 * Types: None, Fixed
 * Units: %, ATR
 */
public class TakeProfitControl extends JPanel {

    private final JComboBox<String> typeCombo;
    private final JSpinner valueSpinner;
    private final JComboBox<String> unitCombo;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private static final String[] TYPES = {"None", "Fixed"};
    private static final String[] UNITS = {"%", "ATR"};

    public TakeProfitControl() {
        this(5.0, 0.1, 100.0, 0.5);
    }

    public TakeProfitControl(double initialValue, double min, double max, double step) {
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
        boolean needsValue = typeCombo.getSelectedIndex() == 1; // Fixed needs value
        valueSpinner.setVisible(needsValue);
        unitCombo.setVisible(needsValue);
        revalidate();
    }

    public TakeProfitType getTakeProfitType() {
        int typeIdx = typeCombo.getSelectedIndex();
        boolean isAtr = unitCombo.getSelectedIndex() == 1;

        return switch (typeIdx) {
            case 1 -> isAtr ? TakeProfitType.FIXED_ATR : TakeProfitType.FIXED_PERCENT;
            default -> TakeProfitType.NONE;
        };
    }

    public void setTakeProfitType(TakeProfitType type) {
        if (type == null) type = TakeProfitType.NONE;

        // Set type combo
        typeCombo.setSelectedIndex(type == TakeProfitType.NONE ? 0 : 1);

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

    public void setValues(TakeProfitType type, Double value) {
        setTakeProfitType(type);
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
