package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Threshold row: checkbox + dollar amount spinner.
 * Used for Whale Delta (min $) and Retail Delta (max $).
 */
public class ThresholdRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel thresholdLabel;
    private final JSpinner thresholdSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public ThresholdRow(String label, String tooltip, String thresholdPrefix, int defaultThreshold) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox(label);
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        if (tooltip != null) checkbox.setToolTipText(tooltip);
        add(checkbox);

        thresholdLabel = new JLabel(thresholdPrefix);
        add(thresholdLabel);

        thresholdSpinner = new JSpinner(new SpinnerNumberModel(defaultThreshold, 1000, 1000000, 10000));
        thresholdSpinner.setPreferredSize(new Dimension(80, 24));
        add(thresholdSpinner);

        checkbox.addActionListener(e -> { updateParamVisibility(); fireChange(); });
        thresholdSpinner.addChangeListener(e -> fireChange());
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        thresholdLabel.setVisible(on);
        thresholdSpinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getThreshold() { return (int) thresholdSpinner.getValue(); }
    public void setThreshold(int threshold) { thresholdSpinner.setValue(threshold); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
