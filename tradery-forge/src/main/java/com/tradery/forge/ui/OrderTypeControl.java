package com.tradery.forge.ui;

import com.tradery.core.model.EntryOrderType;
import com.tradery.core.model.OffsetUnit;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Three-part control for entry order configuration.
 * Displays: [order type dropdown] [value spinner] [unit dropdown]
 *
 * Order types:
 * - Market: immediate fill (hides value/unit)
 * - Stop: breakout entry above signal price
 * - Limit: dip entry below signal price
 * - Trailing: follows price down, enters on reversal
 */
public class OrderTypeControl extends JPanel {

    private final JComboBox<String> typeCombo;
    private final JSpinner valueSpinner;
    private final JComboBox<String> unitCombo;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private static final String[] ORDER_TYPES = {"Market", "Stop", "Limit", "Trailing"};
    private static final String[] UNITS = {"%", "ATR"};

    public OrderTypeControl() {
        this(0.5, -50.0, 50.0, 0.05);
    }

    public OrderTypeControl(double initialValue, double min, double max, double step) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);

        typeCombo = new JComboBox<>(ORDER_TYPES);
        typeCombo.setPreferredSize(new Dimension(75, typeCombo.getPreferredSize().height));

        valueSpinner = new JSpinner(new SpinnerNumberModel(initialValue, min, max, step));
        valueSpinner.setPreferredSize(new Dimension(65, valueSpinner.getPreferredSize().height));

        unitCombo = new JComboBox<>(UNITS);
        unitCombo.setPreferredSize(new Dimension(70, unitCombo.getPreferredSize().height));

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
        EntryOrderType type = getOrderType();
        boolean needsValue = type != EntryOrderType.MARKET;
        boolean needsUnit = type == EntryOrderType.STOP || type == EntryOrderType.LIMIT;

        valueSpinner.setVisible(needsValue);
        unitCombo.setVisible(needsUnit);

        revalidate();
    }

    public EntryOrderType getOrderType() {
        return switch (typeCombo.getSelectedIndex()) {
            case 1 -> EntryOrderType.STOP;
            case 2 -> EntryOrderType.LIMIT;
            case 3 -> EntryOrderType.TRAILING;
            default -> EntryOrderType.MARKET;
        };
    }

    public void setOrderType(EntryOrderType type) {
        if (type == null) type = EntryOrderType.MARKET;
        typeCombo.setSelectedIndex(switch (type) {
            case STOP -> 1;
            case LIMIT -> 2;
            case TRAILING -> 3;
            default -> 0;
        });
        updateVisibility();
    }

    public double getValue() {
        return ((Number) valueSpinner.getValue()).doubleValue();
    }

    public void setValue(double value) {
        valueSpinner.setValue(value);
    }

    public OffsetUnit getUnit() {
        // For trailing, always percent (reversal %)
        if (getOrderType() == EntryOrderType.TRAILING) {
            return OffsetUnit.PERCENT;
        }
        return unitCombo.getSelectedIndex() == 1 ? OffsetUnit.ATR : OffsetUnit.PERCENT;
    }

    public void setUnit(OffsetUnit unit) {
        if (unit == null || unit == OffsetUnit.MARKET) {
            unitCombo.setSelectedIndex(0); // Default to %
        } else {
            unitCombo.setSelectedIndex(unit == OffsetUnit.ATR ? 1 : 0);
        }
    }

    public void setValues(EntryOrderType orderType, Double value, OffsetUnit unit) {
        setOrderType(orderType);
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
        typeCombo.setEnabled(enabled);
        valueSpinner.setEnabled(enabled);
        unitCombo.setEnabled(enabled);
    }
}
