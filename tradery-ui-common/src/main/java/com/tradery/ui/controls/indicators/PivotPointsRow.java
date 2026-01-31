package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pivot Points row: checkbox + R3/S3 sub-checkbox.
 */
public class PivotPointsRow extends JPanel {

    private final JCheckBox checkbox;
    private final JCheckBox r3s3Checkbox;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public PivotPointsRow() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Pivot Points");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("Classic daily pivot support/resistance levels (P, R1-R2, S1-S2)");
        add(checkbox);

        r3s3Checkbox = new JCheckBox("R3/S3");
        r3s3Checkbox.setToolTipText("Show extended R3/S3 levels");
        add(r3s3Checkbox);

        checkbox.addActionListener(e -> { updateParamVisibility(); fireChange(); });
        r3s3Checkbox.addActionListener(e -> fireChange());
        updateParamVisibility();
    }

    private void updateParamVisibility() {
        r3s3Checkbox.setVisible(checkbox.isSelected());
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateParamVisibility(); }
    public boolean isShowR3S3() { return r3s3Checkbox.isSelected(); }
    public void setShowR3S3(boolean show) { r3s3Checkbox.setSelected(show); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
