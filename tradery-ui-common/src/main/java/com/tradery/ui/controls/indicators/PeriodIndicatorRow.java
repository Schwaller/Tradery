package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicator row with checkbox + "Period:" slider+spinner.
 * Used for RSI, ATR, ADX, High/Low, Mayer, Range Position, Donchian.
 *
 * <p>Parameter controls are hidden when the checkbox is unchecked.</p>
 */
public class PeriodIndicatorRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel periodLabel;
    private final JSlider slider;
    private final JSpinner spinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public PeriodIndicatorRow(String label, int defaultPeriod, int min, int max) {
        this(label, null, defaultPeriod, min, max);
    }

    public PeriodIndicatorRow(String label, String tooltip, int defaultPeriod, int min, int max) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox(label);
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        if (tooltip != null) checkbox.setToolTipText(tooltip);
        add(checkbox);

        periodLabel = new JLabel("Period:");
        add(periodLabel);

        slider = new JSlider(min, max, defaultPeriod);
        slider.setPreferredSize(new Dimension(80, 20));
        slider.setFocusable(false);
        add(slider);

        spinner = new JSpinner(new SpinnerNumberModel(defaultPeriod, min, max, 1));
        spinner.setPreferredSize(new Dimension(55, 24));
        add(spinner);

        // Sync slider <-> spinner
        slider.addChangeListener(e -> {
            int sv = slider.getValue();
            if ((int) spinner.getValue() != sv) spinner.setValue(sv);
            fireChange();
        });
        spinner.addChangeListener(e -> {
            int sv = (int) spinner.getValue();
            if (slider.getValue() != sv) slider.setValue(sv);
            fireChange();
        });

        // Toggle param visibility
        checkbox.addActionListener(e -> {
            updateParamVisibility();
            fireChange();
        });
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        periodLabel.setVisible(on);
        slider.setVisible(on);
        spinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getPeriod() { return (int) spinner.getValue(); }
    public void setPeriod(int period) { spinner.setValue(period); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
