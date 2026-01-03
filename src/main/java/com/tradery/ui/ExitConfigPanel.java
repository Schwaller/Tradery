package com.tradery.ui;

import com.tradery.model.ExitZone;
import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.awt.GridBagConstraints.*;

/**
 * Panel for configuring exit zones. All exit configuration is now zone-based.
 */
public class ExitConfigPanel extends JPanel {

    private JPanel zoneListPanel;
    private List<ZoneEditor> zoneEditors = new ArrayList<>();

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    private static final String[] SL_TYPES = {"No SL", "SL %", "Trail %", "SL ATR", "Trail ATR"};
    private static final String[] TP_TYPES = {"No TP", "TP %", "TP ATR"};

    public ExitConfigPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        layoutComponents();
    }

    private void layoutComponents() {
        // Header with label and add button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel label = new JLabel("Exit");
        label.setForeground(Color.GRAY);
        headerPanel.add(label, BorderLayout.WEST);

        JButton addZoneBtn = new JButton("+");
        addZoneBtn.setMargin(new Insets(0, 6, 0, 6));
        addZoneBtn.addActionListener(e -> {
            ExitZone newZone = ExitZone.builder("Zone " + (zoneEditors.size() + 1))
                .build();
            addZoneEditor(newZone);
        });
        headerPanel.add(addZoneBtn, BorderLayout.EAST);

        // Zone list - vertical layout
        zoneListPanel = new JPanel();
        zoneListPanel.setLayout(new BoxLayout(zoneListPanel, BoxLayout.Y_AXIS));
        zoneListPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(zoneListPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void addZoneEditor(ExitZone zone) {
        final ZoneEditor[] editorHolder = new ZoneEditor[1];
        ZoneEditor editor = new ZoneEditor(zone, () -> {
            if (zoneEditors.size() > 1) {
                removeZoneEditor(editorHolder[0]);
                fireChange();
            }
        }, this::fireChange);
        editorHolder[0] = editor;
        zoneEditors.add(editor);
        if (!zoneEditors.isEmpty() && zoneEditors.size() > 1) {
            zoneListPanel.add(Box.createVerticalStrut(40));
        }
        zoneListPanel.add(editor);
        zoneListPanel.revalidate();
        zoneListPanel.repaint();
        fireChange();
    }

    private void removeZoneEditor(ZoneEditor editor) {
        zoneEditors.remove(editor);
        zoneListPanel.remove(editor);
        zoneListPanel.revalidate();
        zoneListPanel.repaint();
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void loadFrom(Strategy strategy) {
        suppressChangeEvents = true;
        try {
            zoneEditors.clear();
            zoneListPanel.removeAll();

            if (strategy != null) {
                // Load zones (always at least one due to Strategy.getExitZones())
                for (ExitZone zone : strategy.getExitZones()) {
                    addZoneEditor(zone);
                }
            } else {
                addZoneEditor(ExitZone.defaultZone());
            }

            // Push zones to top
            zoneListPanel.add(Box.createVerticalGlue());

            zoneListPanel.revalidate();
            zoneListPanel.repaint();
        } finally {
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;

        List<ExitZone> zones = new ArrayList<>();
        for (ZoneEditor editor : zoneEditors) {
            zones.add(editor.toExitZone());
        }
        strategy.setExitZones(zones);
    }

    /**
     * Inner panel for editing a single exit zone with full configuration.
     */
    private class ZoneEditor extends JPanel {
        private JTextField nameField;
        private JSpinner minPnlSpinner;
        private JSpinner maxPnlSpinner;
        private JCheckBox hasMinPnl;
        private JCheckBox hasMaxPnl;
        private JTextArea exitConditionArea;
        private JScrollPane exitConditionScroll;
        private JCheckBox exitImmediatelyCheckbox;
        private JComboBox<String> slTypeCombo;
        private JSpinner slValueSpinner;
        private JComboBox<String> tpTypeCombo;
        private JSpinner tpValueSpinner;
        private JSpinner minBarsSpinner;

        ZoneEditor(ExitZone zone, Runnable onRemove, Runnable onChange) {
            setLayout(new BorderLayout(4, 4));
            setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            // Top row: Name and remove button
            JPanel topRow = new JPanel(new BorderLayout(4, 0));
            topRow.setOpaque(false);

            nameField = new JTextField(zone != null ? zone.name() : "Default", 10);
            nameField.getDocument().addDocumentListener(docListener(onChange));

            JButton removeBtn = new JButton("×");
            removeBtn.setMargin(new Insets(0, 4, 0, 4));
            removeBtn.addActionListener(e -> onRemove.run());

            topRow.add(nameField, BorderLayout.CENTER);
            topRow.add(removeBtn, BorderLayout.EAST);

            // Initialize all components
            hasMinPnl = new JCheckBox("Min %:");
            minPnlSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.minPnlPercent() != null ? zone.minPnlPercent() : -5.0,
                -100.0, 100.0, 0.5));
            hasMinPnl.setSelected(zone != null && zone.minPnlPercent() != null);
            minPnlSpinner.setVisible(hasMinPnl.isSelected());
            hasMinPnl.addActionListener(e -> {
                minPnlSpinner.setVisible(hasMinPnl.isSelected());
                revalidate();
                repaint();
                onChange.run();
            });
            minPnlSpinner.addChangeListener(e -> onChange.run());

            hasMaxPnl = new JCheckBox("Max %:");
            maxPnlSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.maxPnlPercent() != null ? zone.maxPnlPercent() : 0.0,
                -100.0, 100.0, 0.5));
            hasMaxPnl.setSelected(zone != null && zone.maxPnlPercent() != null);
            maxPnlSpinner.setVisible(hasMaxPnl.isSelected());
            hasMaxPnl.addActionListener(e -> {
                maxPnlSpinner.setVisible(hasMaxPnl.isSelected());
                revalidate();
                repaint();
                onChange.run();
            });
            maxPnlSpinner.addChangeListener(e -> onChange.run());

            exitImmediatelyCheckbox = new JCheckBox("Exit Immediately");
            exitImmediatelyCheckbox.setSelected(zone != null && zone.exitImmediately());

            exitConditionArea = new JTextArea(zone != null ? zone.exitCondition() : "", 1, 15);
            exitConditionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            exitConditionArea.setEnabled(!exitImmediatelyCheckbox.isSelected());
            exitConditionArea.getDocument().addDocumentListener(docListener(onChange));

            slTypeCombo = new JComboBox<>(SL_TYPES);
            slValueSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.stopLossValue() != null ? zone.stopLossValue() : 2.0,
                0.1, 100.0, 0.5));
            slTypeCombo.setSelectedIndex(slTypeToIndex(zone != null ? zone.stopLossType() : "none"));
            slValueSpinner.setVisible(slTypeCombo.getSelectedIndex() > 0);
            slTypeCombo.addActionListener(e -> {
                slValueSpinner.setVisible(slTypeCombo.getSelectedIndex() > 0);
                revalidate();
                repaint();
                onChange.run();
            });
            slValueSpinner.addChangeListener(e -> onChange.run());

            tpTypeCombo = new JComboBox<>(TP_TYPES);
            tpTypeCombo.setPreferredSize(slTypeCombo.getPreferredSize());
            tpValueSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.takeProfitValue() != null ? zone.takeProfitValue() : 5.0,
                0.1, 100.0, 0.5));
            tpTypeCombo.setSelectedIndex(tpTypeToIndex(zone != null ? zone.takeProfitType() : "none"));
            tpValueSpinner.setVisible(tpTypeCombo.getSelectedIndex() > 0);
            tpTypeCombo.addActionListener(e -> {
                tpValueSpinner.setVisible(tpTypeCombo.getSelectedIndex() > 0);
                revalidate();
                repaint();
                onChange.run();
            });
            tpValueSpinner.addChangeListener(e -> onChange.run());

            minBarsSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null ? zone.minBarsBeforeExit() : 0, 0, 1000, 1));
            minBarsSpinner.addChangeListener(e -> onChange.run());

            // Exit condition scroll pane (created here so we can control visibility)
            exitConditionScroll = new JScrollPane(exitConditionArea);
            exitConditionScroll.setPreferredSize(new Dimension(180, 24));

            // Exit immediately toggle - hides exit condition and SL/TP
            exitImmediatelyCheckbox.addActionListener(e -> {
                boolean immediate = exitImmediatelyCheckbox.isSelected();
                exitConditionScroll.setVisible(!immediate);
                slTypeCombo.setVisible(!immediate);
                slValueSpinner.setVisible(!immediate);
                tpTypeCombo.setVisible(!immediate);
                tpValueSpinner.setVisible(!immediate);
                revalidate();
                repaint();
                onChange.run();
            });

            // Set initial visibility based on exitImmediately
            boolean exitImmediate = zone != null && zone.exitImmediately();
            exitConditionScroll.setVisible(!exitImmediate);
            slTypeCombo.setVisible(!exitImmediate);
            slValueSpinner.setVisible(!exitImmediate);
            tpTypeCombo.setVisible(!exitImmediate);
            tpValueSpinner.setVisible(!exitImmediate);

            // Build rows - each attribute on its own line
            JPanel centerPanel = new JPanel(new GridBagLayout());
            centerPanel.setOpaque(false);
            int row = 0;

            // Min P&L row
            centerPanel.add(hasMinPnl, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 4, 4), 0, 0));
            centerPanel.add(minPnlSpinner, new GridBagConstraints(1, row++, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0));

            // Max P&L row
            centerPanel.add(hasMaxPnl, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 4, 4), 0, 0));
            centerPanel.add(maxPnlSpinner, new GridBagConstraints(1, row++, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0));

            // Min bars row
            JLabel barsLabel = new JLabel("Min Bars:");
            barsLabel.setForeground(Color.GRAY);
            centerPanel.add(barsLabel, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 4, 4), 0, 0));
            centerPanel.add(minBarsSpinner, new GridBagConstraints(1, row++, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0));

            // Exit immediately row
            centerPanel.add(exitImmediatelyCheckbox, new GridBagConstraints(0, row++, 2, 1, 1, 0, WEST, NONE, new Insets(0, 0, 4, 0), 0, 0));

            // Exit condition row - takes extra vertical space
            centerPanel.add(exitConditionScroll, new GridBagConstraints(0, row++, 2, 1, 1, 1, WEST, BOTH, new Insets(0, 0, 4, 0), 0, 0));

            // Stop Loss row
            centerPanel.add(slTypeCombo, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 4, 4), 0, 0));
            centerPanel.add(slValueSpinner, new GridBagConstraints(1, row++, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0));

            // Take Profit row (last row - no bottom inset)
            centerPanel.add(tpTypeCombo, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 0, 4), 0, 0));
            centerPanel.add(tpValueSpinner, new GridBagConstraints(1, row, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            add(topRow, BorderLayout.NORTH);
            add(centerPanel, BorderLayout.CENTER);
        }

        private DocumentListener docListener(Runnable onChange) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { onChange.run(); }
                public void removeUpdate(DocumentEvent e) { onChange.run(); }
                public void changedUpdate(DocumentEvent e) { onChange.run(); }
            };
        }

        private int slTypeToIndex(String type) {
            if (type == null) return 0;
            return switch (type) {
                case "fixed_percent" -> 1;
                case "trailing_percent" -> 2;
                case "fixed_atr" -> 3;
                case "trailing_atr" -> 4;
                default -> 0;
            };
        }

        private String indexToSlType(int index) {
            return switch (index) {
                case 1 -> "fixed_percent";
                case 2 -> "trailing_percent";
                case 3 -> "fixed_atr";
                case 4 -> "trailing_atr";
                default -> "none";
            };
        }

        private int tpTypeToIndex(String type) {
            if (type == null) return 0;
            return switch (type) {
                case "fixed_percent" -> 1;
                case "fixed_atr" -> 2;
                default -> 0;
            };
        }

        private String indexToTpType(int index) {
            return switch (index) {
                case 1 -> "fixed_percent";
                case 2 -> "fixed_atr";
                default -> "none";
            };
        }

        ExitZone toExitZone() {
            String slType = indexToSlType(slTypeCombo.getSelectedIndex());
            String tpType = indexToTpType(tpTypeCombo.getSelectedIndex());
            return new ExitZone(
                nameField.getText().trim(),
                hasMinPnl.isSelected() ? ((Number) minPnlSpinner.getValue()).doubleValue() : null,
                hasMaxPnl.isSelected() ? ((Number) maxPnlSpinner.getValue()).doubleValue() : null,
                exitConditionArea.getText().trim(),
                slType,
                "none".equals(slType) ? null : ((Number) slValueSpinner.getValue()).doubleValue(),
                tpType,
                "none".equals(tpType) ? null : ((Number) tpValueSpinner.getValue()).doubleValue(),
                exitImmediatelyCheckbox.isSelected(),
                ((Number) minBarsSpinner.getValue()).intValue()
            );
        }
    }
}
