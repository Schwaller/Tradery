package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotating Rays row: checkbox + unlimited + historic + lookback slider + skip slider.
 * Lookback controls are hidden when "Unlimited" is checked.
 */
public class RayRow extends JPanel {

    private final JCheckBox checkbox;
    private final JCheckBox unlimitedCheckbox;
    private final JCheckBox historicCheckbox;
    private final JLabel lookbackLabel;
    private final JSlider lookbackSlider;
    private final JSpinner lookbackSpinner;
    private final JLabel skipLabel;
    private final JSlider skipSlider;
    private final JSpinner skipSpinner;
    private final List<Runnable> changeListeners = new ArrayList<>();

    public RayRow() {
        this(200, 5);
    }

    public RayRow(int defaultLookback, int defaultSkip) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        checkbox = new JCheckBox("Rotating Rays");
        checkbox.setFocusPainted(false);
        checkbox.setFont(checkbox.getFont().deriveFont(11f));
        checkbox.setToolTipText("Show resistance/support rays from ATH/ATL");
        add(checkbox);

        add(Box.createHorizontalGlue());

        unlimitedCheckbox = new JCheckBox("Unlimited");
        unlimitedCheckbox.setToolTipText("Search all available history for ATH/ATL (no lookback limit)");
        add(unlimitedCheckbox);

        historicCheckbox = new JCheckBox("Historic");
        historicCheckbox.setToolTipText("Show historic rays (how rays looked at past points)");
        add(historicCheckbox);

        lookbackLabel = new JLabel("Lookback:");
        add(lookbackLabel);
        lookbackSlider = new JSlider(20, 500, defaultLookback);
        lookbackSlider.setPreferredSize(new Dimension(80, 20));
        lookbackSlider.setFocusable(false);
        add(lookbackSlider);
        lookbackSpinner = new JSpinner(new SpinnerNumberModel(defaultLookback, 20, 500, 1));
        lookbackSpinner.setPreferredSize(new Dimension(55, 24));
        add(lookbackSpinner);

        skipLabel = new JLabel("Skip:");
        add(skipLabel);
        skipSlider = new JSlider(0, 50, defaultSkip);
        skipSlider.setPreferredSize(new Dimension(80, 20));
        skipSlider.setFocusable(false);
        add(skipSlider);
        skipSpinner = new JSpinner(new SpinnerNumberModel(defaultSkip, 0, 50, 1));
        skipSpinner.setPreferredSize(new Dimension(55, 24));
        add(skipSpinner);

        // Sync slider <-> spinner pairs
        syncPair(lookbackSlider, lookbackSpinner);
        syncPair(skipSlider, skipSpinner);

        checkbox.addActionListener(e -> { updateVisibility(); fireChange(); });
        unlimitedCheckbox.addActionListener(e -> { updateVisibility(); fireChange(); });
        historicCheckbox.addActionListener(e -> fireChange());
        updateVisibility();
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

    private void updateVisibility() {
        boolean on = checkbox.isSelected();
        boolean noLimit = unlimitedCheckbox.isSelected();
        unlimitedCheckbox.setVisible(on);
        historicCheckbox.setVisible(on);
        lookbackLabel.setVisible(on && !noLimit);
        lookbackSlider.setVisible(on && !noLimit);
        lookbackSpinner.setVisible(on && !noLimit);
        skipLabel.setVisible(on);
        skipSlider.setVisible(on);
        skipSpinner.setVisible(on);
    }

    public boolean isSelected() { return checkbox.isSelected(); }
    public void setSelected(boolean selected) { checkbox.setSelected(selected); updateVisibility(); }
    /** Returns 0 when unlimited, otherwise the lookback value. */
    public int getLookback() { return unlimitedCheckbox.isSelected() ? 0 : (int) lookbackSpinner.getValue(); }
    public void setLookback(int lookback) {
        unlimitedCheckbox.setSelected(lookback == 0);
        if (lookback > 0) lookbackSpinner.setValue(lookback);
        else lookbackSpinner.setValue(200);
        updateVisibility();
    }
    public int getSkip() { return (int) skipSpinner.getValue(); }
    public void setSkip(int skip) { skipSpinner.setValue(skip); }
    public boolean isHistoric() { return historicCheckbox.isSelected(); }
    public void setHistoric(boolean historic) { historicCheckbox.setSelected(historic); }
    public JCheckBox getCheckbox() { return checkbox; }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }
}
