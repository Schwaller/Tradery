package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Floating POC row: checkbox + bars spinner.
 * 0 = today's session, >0 = rolling N-bar lookback.
 */
public class FloatingPocRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel barsLabel;
    private final JSpinner barsSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public FloatingPocRow() {
        this(0);
    }

    public FloatingPocRow(int defaultBars) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Floating POC/VAH/VAL");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("Show developing POC, VAH, VAL (0=today, N=rolling bars)");
        add(checkbox);

        barsLabel = new JLabel("Bars:");
        add(barsLabel);

        barsSpinner = new JSpinner(new SpinnerNumberModel(defaultBars, 0, 500, 10));
        barsSpinner.setPreferredSize(new Dimension(60, 25));
        barsSpinner.setToolTipText("0 = today's session, >0 = rolling N-bar lookback");
        add(barsSpinner);

        checkbox.addActionListener(e -> { updateParamVisibility(); fireChange(); });
        barsSpinner.addChangeListener(e -> fireChange());
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        barsLabel.setVisible(on);
        barsSpinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getBars() { return (int) barsSpinner.getValue(); }
    public void setBars(int bars) { barsSpinner.setValue(bars); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
