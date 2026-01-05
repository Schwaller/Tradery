package com.tradery.ui;

import com.tradery.model.ExitZone;
import com.tradery.model.StopLossType;
import com.tradery.model.Strategy;
import com.tradery.model.TakeProfitType;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

    private static final String[] SL_TYPES = {"No SL", "Clear SL", "SL %", "Trail %", "SL ATR", "Trail ATR"};
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

        add(headerPanel, BorderLayout.NORTH);
        add(zoneListPanel, BorderLayout.CENTER);
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
            List<ExitZone> zones = strategy != null ? strategy.getExitZones() : List.of(ExitZone.defaultZone());

            // Update existing editors or add new ones
            for (int i = 0; i < zones.size(); i++) {
                ExitZone zone = zones.get(i);
                if (i < zoneEditors.size()) {
                    // Update existing editor
                    zoneEditors.get(i).updateFrom(zone);
                } else {
                    // Add new editor
                    addZoneEditor(zone);
                }
            }

            // Remove extra editors if we have more than needed
            while (zoneEditors.size() > zones.size()) {
                ZoneEditor editor = zoneEditors.remove(zoneEditors.size() - 1);
                zoneListPanel.remove(editor);
                // Also remove the spacer before it if present
                int lastIdx = zoneListPanel.getComponentCount() - 1;
                if (lastIdx >= 0 && zoneListPanel.getComponent(lastIdx) instanceof Box.Filler) {
                    zoneListPanel.remove(lastIdx);
                }
            }

            // Ensure vertical glue is at the end (only add if not already there)
            int componentCount = zoneListPanel.getComponentCount();
            boolean hasGlue = componentCount > 0 &&
                zoneListPanel.getComponent(componentCount - 1) instanceof Box.Filler &&
                ((Box.Filler) zoneListPanel.getComponent(componentCount - 1)).getMaximumSize().height == Short.MAX_VALUE;
            if (!hasGlue) {
                zoneListPanel.add(Box.createVerticalGlue());
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

    private JButton createInfoButton() {
        JButton btn = new JButton("\u24D8"); // circled i
        btn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        Color normal = UIManager.getColor("Label.disabledForeground");
        Color hover = UIManager.getColor("Component.accentColor");
        if (normal == null) normal = Color.GRAY;
        if (hover == null) hover = new Color(70, 130, 180);
        final Color normalColor = normal;
        final Color hoverColor = hover;
        btn.setForeground(normalColor);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("DSL Reference");
        btn.addActionListener(e -> DslHelpDialog.show(this));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setForeground(hoverColor);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setForeground(normalColor);
            }
        });
        return btn;
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
        private JPanel exitConditionScroll;
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
                updateMaxSize();
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
                updateMaxSize();
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
            double slVal = zone != null && zone.stopLossValue() != null ? zone.stopLossValue() : 2.0;
            slValueSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0.1, slVal), 0.1, 100.0, 0.5));
            slTypeCombo.setSelectedIndex(slTypeToIndex(zone != null ? zone.stopLossType() : StopLossType.NONE));
            slValueSpinner.setVisible(slTypeCombo.getSelectedIndex() > 1); // Hide for No SL and Clear SL
            slTypeCombo.addActionListener(e -> {
                slValueSpinner.setVisible(slTypeCombo.getSelectedIndex() > 1); // Hide for No SL and Clear SL
                revalidate();
                updateMaxSize();
                repaint();
                onChange.run();
            });
            slValueSpinner.addChangeListener(e -> onChange.run());

            tpTypeCombo = new JComboBox<>(TP_TYPES);
            tpTypeCombo.setPreferredSize(slTypeCombo.getPreferredSize());
            double tpVal = zone != null && zone.takeProfitValue() != null ? zone.takeProfitValue() : 5.0;
            tpValueSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0.1, tpVal), 0.1, 100.0, 0.5));
            tpTypeCombo.setSelectedIndex(tpTypeToIndex(zone != null ? zone.takeProfitType() : TakeProfitType.NONE));
            tpValueSpinner.setVisible(tpTypeCombo.getSelectedIndex() > 0);
            tpTypeCombo.addActionListener(e -> {
                tpValueSpinner.setVisible(tpTypeCombo.getSelectedIndex() > 0);
                revalidate();
                updateMaxSize();
                repaint();
                onChange.run();
            });
            tpValueSpinner.addChangeListener(e -> onChange.run());

            minBarsSpinner = new JSpinner(new SpinnerNumberModel(
                zone != null ? zone.minBarsBeforeExit() : 0, 0, 1000, 1));
            minBarsSpinner.addChangeListener(e -> onChange.run());

            // Exit condition scroll pane with info button overlay
            JScrollPane rawScroll = new JScrollPane(exitConditionArea);

            JLayeredPane exitLayered = new JLayeredPane();
            exitLayered.setPreferredSize(new Dimension(180, 24));

            JButton exitInfoBtn = createInfoButton();
            exitLayered.add(rawScroll, JLayeredPane.DEFAULT_LAYER);
            exitLayered.add(exitInfoBtn, JLayeredPane.PALETTE_LAYER);

            exitLayered.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    rawScroll.setBounds(0, 0, exitLayered.getWidth(), exitLayered.getHeight());
                    exitInfoBtn.setBounds(exitLayered.getWidth() - 20, exitLayered.getHeight() - 18, 16, 16);
                }
            });

            exitConditionScroll = new JPanel(new BorderLayout());
            exitConditionScroll.add(exitLayered, BorderLayout.CENTER);
            exitConditionScroll.setPreferredSize(new Dimension(180, 24));

            // Exit immediately toggle - hides exit condition and SL/TP
            exitImmediatelyCheckbox.addActionListener(e -> {
                boolean immediate = exitImmediatelyCheckbox.isSelected();
                exitConditionScroll.setVisible(!immediate);
                slTypeCombo.setVisible(!immediate);
                slValueSpinner.setVisible(!immediate && slTypeCombo.getSelectedIndex() > 1);
                tpTypeCombo.setVisible(!immediate);
                tpValueSpinner.setVisible(!immediate && tpTypeCombo.getSelectedIndex() > 0);
                revalidate();
                updateMaxSize();
                repaint();
                onChange.run();
            });

            // Set initial visibility based on exitImmediately and type selection
            boolean exitImmediate = zone != null && zone.exitImmediately();
            exitConditionScroll.setVisible(!exitImmediate);
            slTypeCombo.setVisible(!exitImmediate);
            slValueSpinner.setVisible(!exitImmediate && slTypeCombo.getSelectedIndex() > 1);
            tpTypeCombo.setVisible(!exitImmediate);
            tpValueSpinner.setVisible(!exitImmediate && tpTypeCombo.getSelectedIndex() > 0);

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

            // Exit condition row - expands vertically
            centerPanel.add(exitConditionScroll, new GridBagConstraints(0, row++, 2, 1, 1, 1, WEST, BOTH, new Insets(0, 0, 4, 0), 0, 0));

            // Stop Loss row
            centerPanel.add(slTypeCombo, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 4, 4), 0, 0));
            centerPanel.add(slValueSpinner, new GridBagConstraints(1, row++, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 4, 0), 0, 0));

            // Take Profit row
            centerPanel.add(tpTypeCombo, new GridBagConstraints(0, row, 1, 1, 0, 0, WEST, NONE, new Insets(0, 0, 0, 4), 0, 0));
            centerPanel.add(tpValueSpinner, new GridBagConstraints(1, row, 1, 1, 1, 0, WEST, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

            add(topRow, BorderLayout.NORTH);
            add(centerPanel, BorderLayout.CENTER);

            // Only constrain height when exit condition is hidden
            updateMaxSize();
        }

        private void updateMaxSize() {
            if (exitConditionScroll.isVisible()) {
                // Allow expansion when exit condition is visible
                setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            } else {
                // Lock height when exit condition is hidden
                setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            }
        }

        private DocumentListener docListener(Runnable onChange) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { onChange.run(); }
                public void removeUpdate(DocumentEvent e) { onChange.run(); }
                public void changedUpdate(DocumentEvent e) { onChange.run(); }
            };
        }

        private int slTypeToIndex(StopLossType type) {
            if (type == null) return 0;
            return switch (type) {
                case CLEAR -> 1;
                case FIXED_PERCENT -> 2;
                case TRAILING_PERCENT -> 3;
                case FIXED_ATR -> 4;
                case TRAILING_ATR -> 5;
                default -> 0;
            };
        }

        private StopLossType indexToSlType(int index) {
            return switch (index) {
                case 1 -> StopLossType.CLEAR;
                case 2 -> StopLossType.FIXED_PERCENT;
                case 3 -> StopLossType.TRAILING_PERCENT;
                case 4 -> StopLossType.FIXED_ATR;
                case 5 -> StopLossType.TRAILING_ATR;
                default -> StopLossType.NONE;
            };
        }

        private int tpTypeToIndex(TakeProfitType type) {
            if (type == null) return 0;
            return switch (type) {
                case FIXED_PERCENT -> 1;
                case FIXED_ATR -> 2;
                default -> 0;
            };
        }

        private TakeProfitType indexToTpType(int index) {
            return switch (index) {
                case 1 -> TakeProfitType.FIXED_PERCENT;
                case 2 -> TakeProfitType.FIXED_ATR;
                default -> TakeProfitType.NONE;
            };
        }

        ExitZone toExitZone() {
            StopLossType slType = indexToSlType(slTypeCombo.getSelectedIndex());
            TakeProfitType tpType = indexToTpType(tpTypeCombo.getSelectedIndex());
            // No value for NONE or CLEAR
            boolean slNeedsValue = slType != StopLossType.NONE && slType != StopLossType.CLEAR;
            return new ExitZone(
                nameField.getText().trim(),
                hasMinPnl.isSelected() ? ((Number) minPnlSpinner.getValue()).doubleValue() : null,
                hasMaxPnl.isSelected() ? ((Number) maxPnlSpinner.getValue()).doubleValue() : null,
                exitConditionArea.getText().trim(),
                slType,
                slNeedsValue ? ((Number) slValueSpinner.getValue()).doubleValue() : null,
                tpType,
                tpType == TakeProfitType.NONE ? null : ((Number) tpValueSpinner.getValue()).doubleValue(),
                exitImmediatelyCheckbox.isSelected(),
                ((Number) minBarsSpinner.getValue()).intValue()
            );
        }

        void updateFrom(ExitZone zone) {
            if (zone == null) return;

            // Only update fields if values differ to avoid unnecessary events
            if (!nameField.getText().equals(zone.name())) {
                nameField.setText(zone.name());
            }

            boolean hasMin = zone.minPnlPercent() != null;
            if (hasMinPnl.isSelected() != hasMin) {
                hasMinPnl.setSelected(hasMin);
                minPnlSpinner.setVisible(hasMin);
            }
            if (hasMin && !minPnlSpinner.getValue().equals(zone.minPnlPercent())) {
                minPnlSpinner.setValue(zone.minPnlPercent());
            }

            boolean hasMax = zone.maxPnlPercent() != null;
            if (hasMaxPnl.isSelected() != hasMax) {
                hasMaxPnl.setSelected(hasMax);
                maxPnlSpinner.setVisible(hasMax);
            }
            if (hasMax && !maxPnlSpinner.getValue().equals(zone.maxPnlPercent())) {
                maxPnlSpinner.setValue(zone.maxPnlPercent());
            }

            if (!exitConditionArea.getText().equals(zone.exitCondition())) {
                exitConditionArea.setText(zone.exitCondition());
            }

            if (exitImmediatelyCheckbox.isSelected() != zone.exitImmediately()) {
                exitImmediatelyCheckbox.setSelected(zone.exitImmediately());
                boolean immediate = zone.exitImmediately();
                exitConditionScroll.setVisible(!immediate);
                slTypeCombo.setVisible(!immediate);
                tpTypeCombo.setVisible(!immediate);
            }

            int slIdx = slTypeToIndex(zone.stopLossType());
            if (slTypeCombo.getSelectedIndex() != slIdx) {
                slTypeCombo.setSelectedIndex(slIdx);
                slValueSpinner.setVisible(slIdx > 1); // Hide for No SL and Clear SL
            }
            if (zone.stopLossValue() != null && !slValueSpinner.getValue().equals(zone.stopLossValue())) {
                slValueSpinner.setValue(zone.stopLossValue());
            }

            int tpIdx = tpTypeToIndex(zone.takeProfitType());
            if (tpTypeCombo.getSelectedIndex() != tpIdx) {
                tpTypeCombo.setSelectedIndex(tpIdx);
                tpValueSpinner.setVisible(tpIdx > 0);
            }
            if (zone.takeProfitValue() != null && !tpValueSpinner.getValue().equals(zone.takeProfitValue())) {
                tpValueSpinner.setValue(zone.takeProfitValue());
            }

            if (!minBarsSpinner.getValue().equals(zone.minBarsBeforeExit())) {
                minBarsSpinner.setValue(zone.minBarsBeforeExit());
            }

            revalidate();
            updateMaxSize();
            repaint();
        }
    }
}
