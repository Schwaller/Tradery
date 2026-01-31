package com.tradery.ui.controls.indicators;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-instance SMA/EMA panel with dynamic add/remove rows.
 * Each row: color swatch + checkbox + "Period:" + slider + spinner + [+] [-]
 */
public class DynamicOverlayPanel extends JPanel {

    private final String typeName;
    private final int defaultPeriod;
    private final int minPeriod;
    private final int maxPeriod;
    private final Color[] colorPalette;
    private int colorOffset;
    private final List<Runnable> changeListeners = new ArrayList<>();
    private Runnable repackListener;

    public DynamicOverlayPanel(String typeName, int defaultPeriod, int minPeriod, int maxPeriod, Color[] colorPalette) {
        this.typeName = typeName;
        this.defaultPeriod = defaultPeriod;
        this.minPeriod = minPeriod;
        this.maxPeriod = maxPeriod;
        this.colorPalette = colorPalette;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** Set color offset so EMA colors continue after SMA colors. */
    public void setColorOffset(int offset) {
        this.colorOffset = offset;
    }

    /** Set a listener called when rows are added/removed (for popup repacking). */
    public void setRepackListener(Runnable listener) {
        this.repackListener = listener;
    }

    /** Rebuild rows from a list of periods (all checked). */
    public void setPeriods(List<Integer> periods) {
        removeAll();
        List<Integer> list = new ArrayList<>(periods);
        if (list.isEmpty()) list.add(defaultPeriod);

        for (int i = 0; i < list.size(); i++) {
            add(createRow(list.get(i), i, list.size()));
        }
        revalidate();
        repaint();
        if (repackListener != null) repackListener.run();
    }

    /** Get all periods currently shown (whether checked or not). */
    public List<Integer> getAllPeriods() {
        List<Integer> result = new ArrayList<>();
        for (Component c : getComponents()) {
            if (c instanceof OverlayRow row) result.add(row.getPeriod());
        }
        return result;
    }

    /** Get only periods where checkbox is checked. */
    public List<Integer> getSelectedPeriods() {
        List<Integer> result = new ArrayList<>();
        for (Component c : getComponents()) {
            if (c instanceof OverlayRow row && row.isSelected()) result.add(row.getPeriod());
        }
        return result;
    }

    public void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChange() { changeListeners.forEach(Runnable::run); }

    private OverlayRow createRow(int period, int index, int totalRows) {
        Color color = colorPalette[(colorOffset + index) % colorPalette.length];
        OverlayRow row = new OverlayRow(period, color, totalRows > 1);
        row.addBtn.addActionListener(e -> {
            int newPeriod = findNextAvailablePeriod(getAllPeriods());
            List<Integer> periods = getAllPeriods();
            periods.add(newPeriod);
            setPeriods(periods);
            fireChange();
        });
        row.removeBtn.addActionListener(e -> {
            List<Integer> periods = getAllPeriods();
            periods.remove(Integer.valueOf(row.getPeriod()));
            setPeriods(periods);
            fireChange();
        });
        return row;
    }

    private int findNextAvailablePeriod(List<Integer> existing) {
        int[] common = {20, 50, 100, 200, 10, 15, 25, 30, 40, 60, 80, 150};
        for (int p : common) {
            if (!existing.contains(p)) return p;
        }
        for (int p = minPeriod; p <= maxPeriod; p++) {
            if (!existing.contains(p)) return p;
        }
        return defaultPeriod;
    }

    private class OverlayRow extends JPanel {
        final JCheckBox checkbox;
        final JSlider slider;
        final JSpinner spinner;
        final JButton addBtn;
        final JButton removeBtn;

        OverlayRow(int period, Color color, boolean showRemove) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel colorBox = new JPanel();
            colorBox.setPreferredSize(new Dimension(12, 12));
            colorBox.setBackground(color);
            colorBox.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            add(colorBox);

            checkbox = new JCheckBox(typeName);
            checkbox.setSelected(true);
            add(checkbox);

            JLabel label = new JLabel("Period:");
            add(label);

            slider = new JSlider(minPeriod, maxPeriod, period);
            slider.setPreferredSize(new Dimension(80, 20));
            slider.setFocusable(false);
            add(slider);

            spinner = new JSpinner(new SpinnerNumberModel(period, minPeriod, maxPeriod, 1));
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
            checkbox.addActionListener(e -> fireChange());

            addBtn = new JButton("+");
            addBtn.setMargin(new Insets(0, 4, 0, 4));
            addBtn.setToolTipText("Add another " + typeName);
            add(addBtn);

            removeBtn = new JButton("-");
            removeBtn.setMargin(new Insets(0, 4, 0, 4));
            removeBtn.setToolTipText("Remove this " + typeName);
            removeBtn.setVisible(showRemove);
            add(removeBtn);
        }

        boolean isSelected() { return checkbox.isSelected(); }
        int getPeriod() { return (int) spinner.getValue(); }
    }
}
