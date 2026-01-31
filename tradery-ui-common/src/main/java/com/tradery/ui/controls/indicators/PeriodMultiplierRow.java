package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicator row with checkbox + period spinner + multiplier spinner.
 * Used for Bollinger(period, σ), Supertrend(period, ×), ATR Bands(period, ×).
 *
 * <p>Parameter controls are hidden when the checkbox is unchecked.</p>
 */
public class PeriodMultiplierRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel periodLabel;
    private final JSlider periodSlider;
    private final JSpinner periodSpinner;
    private final JLabel multiplierLabel;
    private final JSpinner multiplierSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    /**
     * @param label             checkbox label (e.g. "Bollinger")
     * @param tooltip           optional tooltip
     * @param defaultPeriod     default period value
     * @param minPeriod         min period
     * @param maxPeriod         max period
     * @param multiplierSymbol  label for multiplier (e.g. "σ:" or "×")
     * @param defaultMult       default multiplier
     * @param minMult           min multiplier
     * @param maxMult           max multiplier
     * @param stepMult          multiplier step
     */
    public PeriodMultiplierRow(String label, String tooltip,
                               int defaultPeriod, int minPeriod, int maxPeriod,
                               String multiplierSymbol,
                               double defaultMult, double minMult, double maxMult, double stepMult) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox(label);
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        if (tooltip != null) checkbox.setToolTipText(tooltip);
        add(checkbox);

        periodLabel = new JLabel("Period:");
        add(periodLabel);

        periodSlider = new JSlider(minPeriod, maxPeriod, defaultPeriod);
        periodSlider.setPreferredSize(new Dimension(80, 20));
        periodSlider.setFocusable(false);
        add(periodSlider);

        periodSpinner = new JSpinner(new SpinnerNumberModel(defaultPeriod, minPeriod, maxPeriod, 1));
        periodSpinner.setPreferredSize(new Dimension(55, 24));
        add(periodSpinner);

        multiplierLabel = new JLabel(multiplierSymbol);
        add(multiplierLabel);

        multiplierSpinner = new JSpinner(new SpinnerNumberModel(defaultMult, minMult, maxMult, stepMult));
        multiplierSpinner.setPreferredSize(new Dimension(50, 24));
        add(multiplierSpinner);

        // Sync slider <-> spinner
        periodSlider.addChangeListener(e -> {
            int sv = periodSlider.getValue();
            if ((int) periodSpinner.getValue() != sv) periodSpinner.setValue(sv);
            fireChange();
        });
        periodSpinner.addChangeListener(e -> {
            int sv = (int) periodSpinner.getValue();
            if (periodSlider.getValue() != sv) periodSlider.setValue(sv);
            fireChange();
        });
        multiplierSpinner.addChangeListener(e -> fireChange());

        checkbox.addActionListener(e -> {
            updateParamVisibility();
            fireChange();
        });
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        periodLabel.setVisible(on);
        periodSlider.setVisible(on);
        periodSpinner.setVisible(on);
        multiplierLabel.setVisible(on);
        multiplierSpinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getPeriod() { return (int) periodSpinner.getValue(); }
    public void setPeriod(int period) { periodSpinner.setValue(period); }
    public double getMultiplier() { return (double) multiplierSpinner.getValue(); }
    public void setMultiplier(double mult) { multiplierSpinner.setValue(mult); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
