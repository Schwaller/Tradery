package com.tradery.ui.controls;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Shared layout building blocks for indicator/overlay selector panels.
 * Used by both Forge's IndicatorSelectorPopup and Desk's IndicatorSidePanel
 * to create visually consistent categorized checkbox layouts.
 *
 * <p>All methods are static factory methods that produce standard Swing components
 * with consistent fonts, sizes, alignment, and spacing.</p>
 */
public final class IndicatorSelectorPanel {

    private IndicatorSelectorPanel() {}

    // ===== Section Layout =====

    /**
     * Create a bold section header label (e.g. "OVERLAYS", "INDICATOR CHARTS").
     */
    public static JLabel createSectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(4, 0, 4, 0));
        return label;
    }

    /**
     * Create a horizontal separator between sections.
     */
    public static JPanel createSectionSeparator() {
        JPanel separator = new JPanel();
        separator.setLayout(new BorderLayout());
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setBorder(new EmptyBorder(8, 0, 4, 0));

        JSeparator line = new JSeparator(SwingConstants.HORIZONTAL);
        separator.add(line, BorderLayout.CENTER);

        return separator;
    }

    /**
     * Create the content panel with standard layout for adding sections.
     */
    public static JPanel createContentPanel() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 4, 4, 4));
        return content;
    }

    /**
     * Create a full scrollable panel with vertical box layout and standard border.
     */
    public static JScrollPane createScrollableContent(JPanel content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        return scrollPane;
    }

    // ===== Checkbox & Row Factories =====

    /**
     * Create a standard checkbox with consistent styling for indicator selectors.
     */
    public static JCheckBox createCheckbox(String label) {
        JCheckBox cb = new JCheckBox(label);
        cb.setFocusPainted(false);
        cb.setFont(cb.getFont().deriveFont(11f));
        return cb;
    }

    /**
     * Create a checkbox row (single checkbox, left-aligned).
     */
    public static JPanel createCheckboxRow(JCheckBox checkbox) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);
        return row;
    }

    /**
     * Create a row with checkbox + label + spinner.
     * The label and spinner are initially disabled and toggle with the checkbox.
     *
     * @param checkbox    the toggle checkbox
     * @param label       parameter label (e.g. "Period:")
     * @param spinner     parameter spinner
     * @param onChange    called on any change (checkbox toggle or spinner value change)
     */
    public static JPanel createIndicatorRow(JCheckBox checkbox, JLabel label, JSpinner spinner,
                                            Runnable onChange) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);
        row.add(Box.createHorizontalGlue());
        row.add(label);
        row.add(spinner);

        label.setVisible(checkbox.isSelected());
        spinner.setVisible(checkbox.isSelected());

        checkbox.addActionListener(e -> {
            label.setVisible(checkbox.isSelected());
            spinner.setVisible(checkbox.isSelected());
            onChange.run();
        });
        spinner.addChangeListener(e -> onChange.run());
        return row;
    }

    /**
     * Create a row with checkbox + label + slider + spinner (synced).
     * The label, slider and spinner toggle visibility with the checkbox.
     *
     * @param checkbox    the toggle checkbox
     * @param label       parameter label (e.g. "Period:")
     * @param slider      parameter slider
     * @param spinner     parameter spinner (should be synced with slider via {@link #createSliderSpinner})
     * @param onChange    called on any change
     */
    public static JPanel createIndicatorRowWithSlider(JCheckBox checkbox, JLabel label,
                                                      JSlider slider, JSpinner spinner,
                                                      Runnable onChange) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);
        row.add(Box.createHorizontalGlue());
        row.add(label);
        row.add(slider);
        row.add(spinner);

        label.setVisible(checkbox.isSelected());
        slider.setVisible(checkbox.isSelected());
        spinner.setVisible(checkbox.isSelected());

        checkbox.addActionListener(e -> {
            label.setVisible(checkbox.isSelected());
            slider.setVisible(checkbox.isSelected());
            spinner.setVisible(checkbox.isSelected());
            onChange.run();
        });
        return row;
    }

    // ===== Control Factories =====

    /**
     * Create a synchronized slider+spinner pair.
     * Slider changes update spinner and vice versa.
     *
     * @param value      initial value
     * @param min        minimum value
     * @param max        maximum value
     * @param onChange   called on any value change (from either slider or spinner)
     * @return array: [JSlider, JSpinner]
     */
    public static Object[] createSliderSpinner(int value, int min, int max, Runnable onChange) {
        JSlider slider = new JSlider(min, max, value);
        slider.setPreferredSize(new Dimension(80, 20));
        slider.setFocusable(false);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(55, 24));

        slider.addChangeListener(e -> {
            int sv = slider.getValue();
            if ((int) spinner.getValue() != sv) {
                spinner.setValue(sv);
            }
            onChange.run();
        });

        spinner.addChangeListener(e -> {
            int sv = (int) spinner.getValue();
            if (slider.getValue() != sv) {
                slider.setValue(sv);
            }
            onChange.run();
        });

        return new Object[]{slider, spinner};
    }

    /**
     * Create a period spinner (integer).
     */
    public static JSpinner createPeriodSpinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(55, 24));
        return spinner;
    }

    /**
     * Create a double-valued spinner (e.g. for standard deviation multiplier).
     */
    public static JSpinner createDoubleSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(50, 24));
        return spinner;
    }
}
