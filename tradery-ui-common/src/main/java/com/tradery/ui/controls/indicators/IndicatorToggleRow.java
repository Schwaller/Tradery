package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple indicator row with just a checkbox toggle (no parameters).
 * Used for VWAP, Ichimoku, Daily POC, Delta, CVD, Volume, etc.
 */
public class IndicatorToggleRow extends JPanel {

    private final JCheckBox checkbox;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public IndicatorToggleRow(String label) {
        this(label, null);
    }

    public IndicatorToggleRow(String label, String tooltip) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox(label);
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        if (tooltip != null) checkbox.setToolTipText(tooltip);
        add(checkbox);

        checkbox.addActionListener(e -> fireChange());
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
