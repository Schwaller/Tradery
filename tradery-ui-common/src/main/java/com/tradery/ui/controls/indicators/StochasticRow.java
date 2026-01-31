package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stochastic indicator config row with checkbox + K period + D period slider+spinners.
 */
public class StochasticRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel kLabel, dLabel;
    private final JSlider kSlider, dSlider;
    private final JSpinner kSpinner, dSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public StochasticRow() {
        this(14, 3);
    }

    public StochasticRow(int defaultK, int defaultD) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Stochastic");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        add(checkbox);
        add(Box.createHorizontalGlue());

        kLabel = new JLabel("K:");
        add(kLabel);
        kSlider = createSlider(defaultK, 2, 50);
        kSpinner = createSpinner(defaultK, 2, 50);
        kSpinner.setToolTipText("%K period");
        add(kSlider);
        add(kSpinner);

        dLabel = new JLabel("D:");
        add(dLabel);
        dSlider = createSlider(defaultD, 1, 20);
        dSpinner = createSpinner(defaultD, 1, 20);
        dSpinner.setToolTipText("%D smoothing period");
        add(dSlider);
        add(dSpinner);

        syncPair(kSlider, kSpinner);
        syncPair(dSlider, dSpinner);

        checkbox.addActionListener(e -> {
            updateParamVisibility();
            fireChange();
        });
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        kLabel.setVisible(on);
        kSlider.setVisible(on);
        kSpinner.setVisible(on);
        dLabel.setVisible(on);
        dSlider.setVisible(on);
        dSpinner.setVisible(on);
    }

    private JSlider createSlider(int value, int min, int max) {
        JSlider s = new JSlider(min, max, value);
        s.setPreferredSize(new Dimension(60, 20));
        s.setFocusable(false);
        return s;
    }

    private JSpinner createSpinner(int value, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        s.setPreferredSize(new Dimension(45, 24));
        return s;
    }

    private void syncPair(JSlider slider, JSpinner spinner) {
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
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getKPeriod() { return (int) kSpinner.getValue(); }
    public int getDPeriod() { return (int) dSpinner.getValue(); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
