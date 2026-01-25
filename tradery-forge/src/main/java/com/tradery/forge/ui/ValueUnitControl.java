package com.tradery.forge.ui;

import com.tradery.core.model.OffsetUnit;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable control for value + unit selection.
 * Displays: [unit dropdown] [value spinner]
 * Matches exit zone style with dropdown first.
 *
 * When unit is MARKET, the value spinner is hidden.
 */
public class ValueUnitControl extends JPanel {

    private final JSpinner valueSpinner;
    private final JComboBox<String> unitCombo;
    private final List<Runnable> changeListeners = new ArrayList<>();

    // Labels match exit zone style
    private static final String[] UNITS = {"Market", "Offset %", "Offset ATR"};

    public ValueUnitControl() {
        this(0.0, -50.0, 50.0, 0.1);
    }

    public ValueUnitControl(double initialValue, double min, double max, double step) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);

        unitCombo = new JComboBox<>(UNITS);
        unitCombo.setPreferredSize(new Dimension(85, unitCombo.getPreferredSize().height));

        valueSpinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, step));
        valueSpinner.setPreferredSize(new Dimension(70, valueSpinner.getPreferredSize().height));

        // Dropdown first, then value (matches exit zone style)
        add(unitCombo);
        add(valueSpinner);

        // Wire listeners
        unitCombo.addActionListener(e -> {
            updateVisibility();
            fireChange();
        });
        valueSpinner.addChangeListener(e -> fireChange());

        updateVisibility();
    }

    private void updateVisibility() {
        boolean isMarket = unitCombo.getSelectedIndex() == 0;
        valueSpinner.setVisible(!isMarket);
        revalidate();
    }

    public double getValue() {
        return ((Number) valueSpinner.getValue()).doubleValue();
    }

    public void setValue(double value) {
        valueSpinner.setValue(value);
    }

    public OffsetUnit getUnit() {
        return switch (unitCombo.getSelectedIndex()) {
            case 1 -> OffsetUnit.PERCENT;
            case 2 -> OffsetUnit.ATR;
            default -> OffsetUnit.MARKET;
        };
    }

    public void setUnit(OffsetUnit unit) {
        if (unit == null) unit = OffsetUnit.MARKET;
        unitCombo.setSelectedIndex(switch (unit) {
            case PERCENT -> 1;
            case ATR -> 2;
            default -> 0;
        });
        updateVisibility();
    }

    public void setValueAndUnit(Double value, OffsetUnit unit) {
        if (value != null) {
            valueSpinner.setValue(value);
        }
        setUnit(unit);
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
        valueSpinner.setEnabled(enabled);
        unitCombo.setEnabled(enabled);
    }
}
