package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily Volume Profile row: checkbox + bins spinner + color mode combo.
 */
public class DailyVolumeProfileRow extends JPanel {

    private final JCheckBox checkbox;
    private final JLabel binsLabel;
    private final JSpinner binsSpinner;
    private final JComboBox<String> colorModeCombo;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public DailyVolumeProfileRow() {
        this(96);
    }

    public DailyVolumeProfileRow(int defaultBins) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Daily Volume Profile");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("Show volume distribution histogram for each day");
        add(checkbox);

        binsLabel = new JLabel("Bins:");
        add(binsLabel);

        binsSpinner = new JSpinner(new SpinnerNumberModel(defaultBins, 12, 200, 12));
        binsSpinner.setPreferredSize(new Dimension(60, 24));
        add(binsSpinner);

        colorModeCombo = new JComboBox<>(new String[]{"Volume", "Delta", "Delta+Volume"});
        colorModeCombo.setPreferredSize(new Dimension(100, 24));
        colorModeCombo.setToolTipText("Blue/orange = volume intensity, Green/red = delta direction");
        add(colorModeCombo);

        checkbox.addActionListener(e -> { updateParamVisibility(); fireChange(); });
        binsSpinner.addChangeListener(e -> fireChange());
        colorModeCombo.addActionListener(e -> fireChange());
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        boolean on = checkbox.isSelected();
        binsLabel.setVisible(on);
        binsSpinner.setVisible(on);
        colorModeCombo.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public int getBins() { return (int) binsSpinner.getValue(); }
    public void setBins(int bins) { binsSpinner.setValue(bins); }
    public String getColorMode() { return (String) colorModeCombo.getSelectedItem(); }
    public void setColorMode(String mode) { colorModeCombo.setSelectedItem(mode); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
