package com.tradery.ui;

import com.tradery.model.ExitZone;
import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
        JLabel label = new JLabel("Exit Zones:");
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

        // Zone list - horizontal layout
        zoneListPanel = new JPanel();
        zoneListPanel.setLayout(new BoxLayout(zoneListPanel, BoxLayout.X_AXIS));
        zoneListPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(zoneListPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

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
            setLayout(new BorderLayout(4, 2));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 8)
            ));
            setOpaque(false);
            setPreferredSize(new Dimension(200, 220));
            setMinimumSize(new Dimension(200, 220));
            setMaximumSize(new Dimension(200, 250));

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
            minPnlSpinner.setEnabled(hasMinPnl.isSelected());
            hasMinPnl.addActionListener(e -> {
                minPnlSpinner.setEnabled(hasMinPnl.isSelected());
                onChange.run();
            });
            minPnlSpinner.addChangeListener(e -> onChange.run());

            hasMaxPnl = new JCheckBox("Max %:");
            maxPnlSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.maxPnlPercent() != null ? zone.maxPnlPercent() : 0.0,
                -100.0, 100.0, 0.5));
            hasMaxPnl.setSelected(zone != null && zone.maxPnlPercent() != null);
            maxPnlSpinner.setEnabled(hasMaxPnl.isSelected());
            hasMaxPnl.addActionListener(e -> {
                maxPnlSpinner.setEnabled(hasMaxPnl.isSelected());
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
            slValueSpinner.setEnabled(slTypeCombo.getSelectedIndex() > 0);
            slTypeCombo.addActionListener(e -> {
                slValueSpinner.setEnabled(slTypeCombo.getSelectedIndex() > 0);
                onChange.run();
            });
            slValueSpinner.addChangeListener(e -> onChange.run());

            tpTypeCombo = new JComboBox<>(TP_TYPES);
            tpValueSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null && zone.takeProfitValue() != null ? zone.takeProfitValue() : 5.0,
                0.1, 100.0, 0.5));
            tpTypeCombo.setSelectedIndex(tpTypeToIndex(zone != null ? zone.takeProfitType() : "none"));
            tpValueSpinner.setEnabled(tpTypeCombo.getSelectedIndex() > 0);
            tpTypeCombo.addActionListener(e -> {
                tpValueSpinner.setEnabled(tpTypeCombo.getSelectedIndex() > 0);
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
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(1, 0, 1, 4);
            c.gridy = 0;

            // Min P&L row
            c.gridx = 0; c.weightx = 0;
            centerPanel.add(hasMinPnl, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            centerPanel.add(minPnlSpinner, c);

            // Max P&L row
            c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            centerPanel.add(hasMaxPnl, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            centerPanel.add(maxPnlSpinner, c);

            // Min bars row
            c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            JLabel barsLabel = new JLabel("Min Bars:");
            barsLabel.setForeground(Color.GRAY);
            centerPanel.add(barsLabel, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            centerPanel.add(minBarsSpinner, c);

            // Exit immediately row
            c.gridy++; c.gridx = 0; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.NONE;
            centerPanel.add(exitImmediatelyCheckbox, c);

            // Exit condition row - takes extra vertical space
            c.gridy++; c.gridx = 0; c.gridwidth = 2; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
            centerPanel.add(exitConditionScroll, c);
            c.weighty = 0;

            // Stop Loss row
            c.gridy++; c.gridwidth = 1; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            centerPanel.add(slTypeCombo, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            centerPanel.add(slValueSpinner, c);

            // Take Profit row
            c.gridy++; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            centerPanel.add(tpTypeCombo, c);
            c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            centerPanel.add(tpValueSpinner, c);

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
