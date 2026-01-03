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
 * Panel for configuring exit conditions, exit zones, and SL/TP settings.
 */
public class ExitConfigPanel extends JPanel {

    private JTextArea exitEditor;
    private JCheckBox exitZonesEnabledCheckbox;
    private JPanel exitZonesPanel;
    private JPanel zoneListPanel;
    private List<ZoneEditor> zoneEditors = new ArrayList<>();
    private JSpinner minBarsBeforeExitSpinner;
    private JComboBox<String> stopLossTypeCombo;
    private JSpinner stopLossValueSpinner;
    private JComboBox<String> takeProfitTypeCombo;
    private JSpinner takeProfitValueSpinner;

    private static final String[] SL_TYPES = {"No Stop Loss", "Stop Loss %", "Trailing Stop %", "Stop Loss ATR", "Trailing Stop ATR"};
    private static final String[] TP_TYPES = {"No Take Profit", "Take Profit %", "Take Profit ATR"};

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public ExitConfigPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        exitEditor = new JTextArea(3, 20);
        exitEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        exitEditor.setLineWrap(true);
        exitEditor.setWrapStyleWord(true);

        exitZonesEnabledCheckbox = new JCheckBox("Exit Zones");
        exitZonesEnabledCheckbox.addActionListener(e -> updateExitZonesVisibility());

        minBarsBeforeExitSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

        stopLossTypeCombo = new JComboBox<>(SL_TYPES);
        stopLossValueSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.1, 100.0, 0.5));
        stopLossTypeCombo.addActionListener(e -> updateSlSpinnerVisibility());

        takeProfitTypeCombo = new JComboBox<>(TP_TYPES);
        takeProfitValueSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 100.0, 0.5));
        takeProfitTypeCombo.addActionListener(e -> updateTpSpinnerVisibility());

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fireChange(); }
            public void removeUpdate(DocumentEvent e) { fireChange(); }
            public void changedUpdate(DocumentEvent e) { fireChange(); }
        };

        exitEditor.getDocument().addDocumentListener(docListener);
        exitZonesEnabledCheckbox.addActionListener(e -> fireChange());
        minBarsBeforeExitSpinner.addChangeListener(e -> fireChange());
        stopLossTypeCombo.addActionListener(e -> fireChange());
        stopLossValueSpinner.addChangeListener(e -> fireChange());
        takeProfitTypeCombo.addActionListener(e -> fireChange());
        takeProfitValueSpinner.addChangeListener(e -> fireChange());
    }

    private void layoutComponents() {
        // Exit condition
        JPanel conditionPanel = new JPanel(new BorderLayout(0, 2));
        conditionPanel.setOpaque(false);
        JLabel exitLabel = new JLabel("Exit Condition:");
        exitLabel.setForeground(Color.GRAY);
        conditionPanel.add(exitLabel, BorderLayout.NORTH);
        JScrollPane exitScroll = new JScrollPane(exitEditor);
        conditionPanel.add(exitScroll, BorderLayout.CENTER);

        // Exit Zones section
        JPanel exitZonesCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        exitZonesCheckboxPanel.setOpaque(false);
        exitZonesCheckboxPanel.add(exitZonesEnabledCheckbox);

        exitZonesPanel = new JPanel(new BorderLayout(0, 4));
        exitZonesPanel.setOpaque(false);
        exitZonesPanel.setVisible(false);

        zoneListPanel = new JPanel();
        zoneListPanel.setLayout(new BoxLayout(zoneListPanel, BoxLayout.Y_AXIS));
        zoneListPanel.setOpaque(false);

        JButton addZoneBtn = new JButton("+ Add Zone");
        addZoneBtn.addActionListener(e -> {
            ExitZone newZone = ExitZone.builder("Zone " + (zoneEditors.size() + 1))
                .minPnl(null)
                .maxPnl(0.0)
                .exitImmediately(false)
                .build();
            addZoneEditor(newZone);
        });

        JPanel addZoneBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        addZoneBtnPanel.setOpaque(false);
        addZoneBtnPanel.add(addZoneBtn);

        exitZonesPanel.add(zoneListPanel, BorderLayout.CENTER);
        exitZonesPanel.add(addZoneBtnPanel, BorderLayout.SOUTH);

        JPanel exitZonesWrapper = new JPanel(new BorderLayout(0, 0));
        exitZonesWrapper.setOpaque(false);
        exitZonesWrapper.add(exitZonesCheckboxPanel, BorderLayout.NORTH);
        exitZonesWrapper.add(exitZonesPanel, BorderLayout.CENTER);
        conditionPanel.add(exitZonesWrapper, BorderLayout.SOUTH);

        // Settings panel - trade settings on two lines
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        // Row 0: Min Bars Before Exit + Stop Loss
        JLabel minBarsLabel = new JLabel("Min Bars:");
        minBarsLabel.setForeground(Color.GRAY);
        c.gridx = 0; c.gridy = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        settingsPanel.add(minBarsLabel, c);
        c.gridx = 1;
        settingsPanel.add(minBarsBeforeExitSpinner, c);

        c.gridx = 2; c.insets = new Insets(2, 12, 2, 4);
        settingsPanel.add(stopLossTypeCombo, c);
        c.gridx = 3; c.insets = new Insets(2, 0, 2, 0); c.weightx = 1;
        settingsPanel.add(stopLossValueSpinner, c);

        // Row 1: Take Profit (right-aligned to match SL position)
        c.gridx = 2; c.gridy = 1; c.insets = new Insets(2, 12, 2, 4); c.weightx = 0;
        settingsPanel.add(takeProfitTypeCombo, c);
        c.gridx = 3; c.insets = new Insets(2, 0, 2, 0); c.weightx = 1;
        settingsPanel.add(takeProfitValueSpinner, c);

        add(conditionPanel, BorderLayout.CENTER);
        add(settingsPanel, BorderLayout.SOUTH);
    }

    private GridBagConstraints gbc(int x, int y, boolean fill) {
        return new GridBagConstraints(x, y, 1, 1, fill ? 1 : 0, 0,
            GridBagConstraints.WEST, fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE,
            new Insets(2, 0, 2, 4), 0, 0);
    }

    private void updateExitZonesVisibility() {
        exitZonesPanel.setVisible(exitZonesEnabledCheckbox.isSelected());
        revalidate();
        repaint();
    }

    private void updateSlSpinnerVisibility() {
        boolean hasValue = stopLossTypeCombo.getSelectedIndex() > 0;
        stopLossValueSpinner.setEnabled(hasValue);
    }

    private void updateTpSpinnerVisibility() {
        boolean hasValue = takeProfitTypeCombo.getSelectedIndex() > 0;
        takeProfitValueSpinner.setEnabled(hasValue);
    }

    private void addZoneEditor(ExitZone zone) {
        final ZoneEditor[] editorHolder = new ZoneEditor[1];
        ZoneEditor editor = new ZoneEditor(zone, () -> {
            removeZoneEditor(editorHolder[0]);
            fireChange();
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
            if (strategy != null) {
                exitEditor.setText(strategy.getExit());

                // SL type and value
                stopLossTypeCombo.setSelectedIndex(slTypeToIndex(strategy.getStopLossType()));
                stopLossValueSpinner.setValue(strategy.getStopLossValue() != null ? strategy.getStopLossValue() : 2.0);
                updateSlSpinnerVisibility();

                // TP type and value
                takeProfitTypeCombo.setSelectedIndex(tpTypeToIndex(strategy.getTakeProfitType()));
                takeProfitValueSpinner.setValue(strategy.getTakeProfitValue() != null ? strategy.getTakeProfitValue() : 5.0);
                updateTpSpinnerVisibility();

                minBarsBeforeExitSpinner.setValue(strategy.getMinBarsBeforeExit());

                // Exit zones
                zoneEditors.clear();
                zoneListPanel.removeAll();
                boolean hasZones = strategy.hasExitZones();
                exitZonesEnabledCheckbox.setSelected(hasZones);
                if (hasZones) {
                    for (ExitZone zone : strategy.getExitZones()) {
                        addZoneEditor(zone);
                    }
                }
                updateExitZonesVisibility();
            } else {
                exitEditor.setText("");
                stopLossTypeCombo.setSelectedIndex(0);
                takeProfitTypeCombo.setSelectedIndex(0);
                minBarsBeforeExitSpinner.setValue(0);
                exitZonesEnabledCheckbox.setSelected(false);
                zoneEditors.clear();
                zoneListPanel.removeAll();
                updateExitZonesVisibility();
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;

        strategy.setExit(exitEditor.getText().trim());

        String slType = indexToSlType(stopLossTypeCombo.getSelectedIndex());
        strategy.setStopLossType(slType);
        strategy.setStopLossValue("none".equals(slType) ? null : ((Number) stopLossValueSpinner.getValue()).doubleValue());

        String tpType = indexToTpType(takeProfitTypeCombo.getSelectedIndex());
        strategy.setTakeProfitType(tpType);
        strategy.setTakeProfitValue("none".equals(tpType) ? null : ((Number) takeProfitValueSpinner.getValue()).doubleValue());

        strategy.setMinBarsBeforeExit(((Number) minBarsBeforeExitSpinner.getValue()).intValue());

        // Exit zones
        if (exitZonesEnabledCheckbox.isSelected() && !zoneEditors.isEmpty()) {
            List<ExitZone> zones = new ArrayList<>();
            for (ZoneEditor editor : zoneEditors) {
                zones.add(editor.toExitZone());
            }
            strategy.setExitZones(zones);
        } else {
            strategy.setExitZones(new ArrayList<>());
        }
    }

    // Type conversion helpers

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

    /**
     * Inner panel for editing a single exit zone
     */
    private static class ZoneEditor extends JPanel {
        private JTextField nameField;
        private JSpinner minPnlSpinner;
        private JSpinner maxPnlSpinner;
        private JCheckBox hasMinPnl;
        private JCheckBox hasMaxPnl;
        private JTextArea exitConditionArea;
        private JCheckBox exitImmediatelyCheckbox;

        ZoneEditor(ExitZone zone, Runnable onRemove, Runnable onChange) {
            setLayout(new BorderLayout(4, 4));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 0, 8, 0)
            ));
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

            // Top row: Name and remove button
            JPanel topRow = new JPanel(new BorderLayout(4, 0));
            topRow.setOpaque(false);

            nameField = new JTextField(zone != null ? zone.name() : "Zone", 10);
            nameField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { onChange.run(); }
                public void removeUpdate(DocumentEvent e) { onChange.run(); }
                public void changedUpdate(DocumentEvent e) { onChange.run(); }
            });

            JButton removeBtn = new JButton("×");
            removeBtn.setMargin(new Insets(0, 4, 0, 4));
            removeBtn.addActionListener(e -> onRemove.run());

            topRow.add(nameField, BorderLayout.CENTER);
            topRow.add(removeBtn, BorderLayout.EAST);

            // P&L range row
            JPanel rangeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            rangeRow.setOpaque(false);

            hasMinPnl = new JCheckBox("Min:");
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

            hasMaxPnl = new JCheckBox("Max:");
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

            rangeRow.add(hasMinPnl);
            rangeRow.add(minPnlSpinner);
            rangeRow.add(new JLabel("%"));
            rangeRow.add(Box.createHorizontalStrut(8));
            rangeRow.add(hasMaxPnl);
            rangeRow.add(maxPnlSpinner);
            rangeRow.add(new JLabel("%"));

            // Exit immediately checkbox
            exitImmediatelyCheckbox = new JCheckBox("Exit Immediately");
            exitImmediatelyCheckbox.setSelected(zone != null && zone.exitImmediately());
            exitImmediatelyCheckbox.addActionListener(e -> {
                exitConditionArea.setEnabled(!exitImmediatelyCheckbox.isSelected());
                onChange.run();
            });

            // Exit condition
            exitConditionArea = new JTextArea(zone != null ? zone.exitCondition() : "", 1, 20);
            exitConditionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            exitConditionArea.setEnabled(!exitImmediatelyCheckbox.isSelected());
            exitConditionArea.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { onChange.run(); }
                public void removeUpdate(DocumentEvent e) { onChange.run(); }
                public void changedUpdate(DocumentEvent e) { onChange.run(); }
            });

            JPanel exitRow = new JPanel(new BorderLayout(4, 0));
            exitRow.setOpaque(false);
            exitRow.add(exitImmediatelyCheckbox, BorderLayout.WEST);
            exitRow.add(new JScrollPane(exitConditionArea), BorderLayout.CENTER);

            // Combine
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setOpaque(false);
            rangeRow.setAlignmentX(LEFT_ALIGNMENT);
            exitRow.setAlignmentX(LEFT_ALIGNMENT);
            centerPanel.add(rangeRow);
            centerPanel.add(Box.createVerticalStrut(4));
            centerPanel.add(exitRow);

            add(topRow, BorderLayout.NORTH);
            add(centerPanel, BorderLayout.CENTER);
        }

        ExitZone toExitZone() {
            return new ExitZone(
                nameField.getText().trim(),
                hasMinPnl.isSelected() ? ((Number) minPnlSpinner.getValue()).doubleValue() : null,
                hasMaxPnl.isSelected() ? ((Number) maxPnlSpinner.getValue()).doubleValue() : null,
                exitConditionArea.getText().trim(),
                "none", null,  // SL type/value (not editable in basic UI)
                "none", null,  // TP type/value (not editable in basic UI)
                exitImmediatelyCheckbox.isSelected()
            );
        }
    }
}
