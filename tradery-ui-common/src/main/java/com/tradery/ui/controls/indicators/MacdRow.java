package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD indicator config row with checkbox + fast/slow/signal slider+spinners.
 */
public class MacdRow extends JPanel {

    private final JCheckBox checkbox;
    private final JSlider fastSlider, slowSlider, signalSlider;
    private final JSpinner fastSpinner, slowSpinner, signalSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public MacdRow() {
        this(12, 26, 9);
    }

    public MacdRow(int defaultFast, int defaultSlow, int defaultSignal) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("MACD");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        add(checkbox);
        add(Box.createHorizontalGlue());

        fastSlider = createSlider(defaultFast, 2, 50);
        fastSpinner = createSpinner(defaultFast, 2, 50);
        add(fastSlider);
        add(fastSpinner);

        slowSlider = createSlider(defaultSlow, 5, 100);
        slowSpinner = createSpinner(defaultSlow, 5, 100);
        add(slowSlider);
        add(slowSpinner);

        signalSlider = createSlider(defaultSignal, 2, 50);
        signalSpinner = createSpinner(defaultSignal, 2, 50);
        add(signalSlider);
        add(signalSpinner);

        syncPair(fastSlider, fastSpinner);
        syncPair(slowSlider, slowSpinner);
        syncPair(signalSlider, signalSpinner);

        checkbox.addActionListener(e -> {
            updateParamVisibility();
            fireChange();
        });
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        for (Component c : getComponents()) {
            if (c != checkbox) c.setVisible(on);
        }
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
    public int getFast() { return (int) fastSpinner.getValue(); }
    public int getSlow() { return (int) slowSpinner.getValue(); }
    public int getSignal() { return (int) signalSpinner.getValue(); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
