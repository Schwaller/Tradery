package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Keltner Channel config row with checkbox + EMA period + ATR period + multiplier.
 */
public class KeltnerRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel emaLabel, atrLabel, multLabel;
    private final JSpinner emaSpinner, atrSpinner, multSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public KeltnerRow() {
        this(20, 10, 2.0);
    }

    public KeltnerRow(int defaultEma, int defaultAtr, double defaultMult) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Keltner Channel");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("EMA-based channel with ATR bands (smoother than Bollinger)");
        add(checkbox);

        emaLabel = new JLabel("EMA:");
        emaSpinner = new JSpinner(new SpinnerNumberModel(defaultEma, 5, 50, 1));
        emaSpinner.setPreferredSize(new Dimension(45, 24));
        add(emaLabel);
        add(emaSpinner);

        atrLabel = new JLabel("ATR:");
        atrSpinner = new JSpinner(new SpinnerNumberModel(defaultAtr, 5, 30, 1));
        atrSpinner.setPreferredSize(new Dimension(45, 24));
        add(atrLabel);
        add(atrSpinner);

        multLabel = new JLabel("\u00D7");
        multSpinner = new JSpinner(new SpinnerNumberModel(defaultMult, 0.5, 4.0, 0.5));
        multSpinner.setPreferredSize(new Dimension(45, 24));
        add(multLabel);
        add(multSpinner);

        emaSpinner.addChangeListener(e -> fireChange());
        atrSpinner.addChangeListener(e -> fireChange());
        multSpinner.addChangeListener(e -> fireChange());

        checkbox.addActionListener(e -> {
            updateParamVisibility();
            fireChange();
        });
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        emaLabel.setVisible(on);
        emaSpinner.setVisible(on);
        atrLabel.setVisible(on);
        atrSpinner.setVisible(on);
        multLabel.setVisible(on);
        multSpinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getEmaPeriod() { return (int) emaSpinner.getValue(); }
    public int getAtrPeriod() { return (int) atrSpinner.getValue(); }
    public double getMultiplier() { return (double) multSpinner.getValue(); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
