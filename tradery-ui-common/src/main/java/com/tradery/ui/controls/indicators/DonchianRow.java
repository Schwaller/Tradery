package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Donchian Channel row: checkbox + period spinner + "Middle" sub-checkbox.
 */
public class DonchianRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel periodLabel;
    private final JSpinner periodSpinner;
    private final JCheckBox middleCheckbox;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public DonchianRow() {
        this(20);
    }

    public DonchianRow(int defaultPeriod) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Donchian Channel");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("Highest high / lowest low channel for breakout trading");
        add(checkbox);

        periodLabel = new JLabel("Period:");
        periodSpinner = new JSpinner(new SpinnerNumberModel(defaultPeriod, 5, 100, 1));
        add(periodLabel);
        add(periodSpinner);

        middleCheckbox = new JCheckBox("Middle");
        middleCheckbox.setToolTipText("Show middle line (average of upper and lower)");
        middleCheckbox.setSelected(true);
        add(middleCheckbox);

        checkbox.addActionListener(e -> { updateParamVisibility(); fireChange(); });
        periodSpinner.addChangeListener(e -> fireChange());
        middleCheckbox.addActionListener(e -> fireChange());
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        periodLabel.setVisible(on);
        periodSpinner.setVisible(on);
        middleCheckbox.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getPeriod() { return (int) periodSpinner.getValue(); }
    public void setPeriod(int period) { periodSpinner.setValue(period); }
    public boolean isShowMiddle() { return middleCheckbox.isSelected(); }
    public void setShowMiddle(boolean show) { middleCheckbox.setSelected(show); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
