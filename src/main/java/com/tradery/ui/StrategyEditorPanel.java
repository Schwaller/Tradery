package com.tradery.ui;

import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Panel for editing a single strategy's DSL conditions and trade management settings.
 * Used within ProjectWindow.
 */
public class StrategyEditorPanel extends JPanel {

    private JTextArea entryEditor;
    private JTextArea exitEditor;
    private JComboBox<String> stopLossTypeCombo;
    private JSpinner stopLossValueSpinner;
    private JComboBox<String> takeProfitTypeCombo;
    private JSpinner takeProfitValueSpinner;
    private JSpinner maxOpenTradesSpinner;
    private JSpinner minCandlesBetweenSpinner;
    private JCheckBox dcaEnabledCheckbox;
    private JSpinner dcaMaxEntriesSpinner;
    private JSpinner dcaBarsBetweenSpinner;
    private JComboBox<String> dcaModeCombo;
    private JPanel dcaDetailsPanel;

    private static final String[] SL_TYPES = {"No Stop Loss", "Stop Loss %", "Trailing Stop %", "Stop Loss ATR", "Trailing Stop ATR"};
    private static final String[] TP_TYPES = {"No Take Profit", "Take Profit %", "Take Profit ATR"};
    private static final String[] DCA_MODES = {"Abort if signal lost", "Continue regardless"};

    private Strategy strategy;
    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public StrategyEditorPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {

        // Entry/Exit editors
        entryEditor = new JTextArea(3, 20);
        entryEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        entryEditor.setLineWrap(true);
        entryEditor.setWrapStyleWord(true);

        exitEditor = new JTextArea(3, 20);
        exitEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        exitEditor.setLineWrap(true);
        exitEditor.setWrapStyleWord(true);

        // SL/TP fields
        stopLossTypeCombo = new JComboBox<>(SL_TYPES);
        stopLossValueSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.1, 100.0, 0.5));
        stopLossTypeCombo.addActionListener(e -> updateSlSpinnerVisibility());

        takeProfitTypeCombo = new JComboBox<>(TP_TYPES);
        takeProfitValueSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.1, 100.0, 0.5));
        takeProfitTypeCombo.addActionListener(e -> updateTpSpinnerVisibility());

        // Trade management fields
        maxOpenTradesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        minCandlesBetweenSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

        // DCA fields
        dcaEnabledCheckbox = new JCheckBox("DCA");
        dcaMaxEntriesSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        dcaBarsBetweenSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
        dcaModeCombo = new JComboBox<>(DCA_MODES);
        dcaEnabledCheckbox.addActionListener(e -> updateDcaVisibility());

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fireChange(); }
            public void removeUpdate(DocumentEvent e) { fireChange(); }
            public void changedUpdate(DocumentEvent e) { fireChange(); }
        };

        entryEditor.getDocument().addDocumentListener(docListener);
        exitEditor.getDocument().addDocumentListener(docListener);

        stopLossTypeCombo.addActionListener(e -> fireChange());
        stopLossValueSpinner.addChangeListener(e -> fireChange());
        takeProfitTypeCombo.addActionListener(e -> fireChange());
        takeProfitValueSpinner.addChangeListener(e -> fireChange());
        maxOpenTradesSpinner.addChangeListener(e -> fireChange());
        minCandlesBetweenSpinner.addChangeListener(e -> fireChange());
        dcaEnabledCheckbox.addActionListener(e -> fireChange());
        dcaMaxEntriesSpinner.addChangeListener(e -> fireChange());
        dcaBarsBetweenSpinner.addChangeListener(e -> fireChange());
        dcaModeCombo.addActionListener(e -> fireChange());
    }

    private void updateDcaVisibility() {
        boolean enabled = dcaEnabledCheckbox.isSelected();
        dcaDetailsPanel.setVisible(enabled);
        // Revalidate up the hierarchy to ensure layout recalculates
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
        revalidate();
        repaint();
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private void updateSlSpinnerVisibility() {
        boolean hasValue = stopLossTypeCombo.getSelectedIndex() > 0;
        stopLossValueSpinner.setEnabled(hasValue);
    }

    private void updateTpSpinnerVisibility() {
        boolean hasValue = takeProfitTypeCombo.getSelectedIndex() > 0;
        takeProfitValueSpinner.setEnabled(hasValue);
    }

    private void layoutComponents() {
        // Title
        JLabel title = new JLabel("Strategy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        // Entry condition
        JPanel entryPanel = new JPanel(new BorderLayout(0, 2));
        entryPanel.setOpaque(false);
        JLabel entryLabel = new JLabel("Entry Condition:");
        entryLabel.setForeground(Color.GRAY);
        entryPanel.add(entryLabel, BorderLayout.NORTH);
        JScrollPane entryScroll = new JScrollPane(entryEditor);
        entryPanel.add(entryScroll, BorderLayout.CENTER);

        // DCA checkbox on its own line
        JPanel dcaCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        dcaCheckboxPanel.setOpaque(false);
        dcaCheckboxPanel.add(dcaEnabledCheckbox);

        // DCA details panel (hidden by default, shown when DCA enabled)
        dcaDetailsPanel = new JPanel(new GridBagLayout());
        dcaDetailsPanel.setOpaque(false);
        dcaDetailsPanel.setVisible(false);

        // Max entries row
        JLabel maxEntriesLabel = new JLabel("Max entries:");
        maxEntriesLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(maxEntriesLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));
        dcaDetailsPanel.add(dcaMaxEntriesSpinner, new GridBagConstraints(1, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));

        // Bars between row
        JLabel barsBetweenLabel = new JLabel("Bars between:");
        barsBetweenLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(barsBetweenLabel, new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));
        dcaDetailsPanel.add(dcaBarsBetweenSpinner, new GridBagConstraints(1, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));

        // Mode row
        JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(modeLabel, new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));
        dcaDetailsPanel.add(dcaModeCombo, new GridBagConstraints(1, 2, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));

        JPanel dcaWrapper = new JPanel(new BorderLayout(0, 0));
        dcaWrapper.setOpaque(false);
        dcaWrapper.add(dcaCheckboxPanel, BorderLayout.NORTH);
        dcaWrapper.add(dcaDetailsPanel, BorderLayout.CENTER);
        entryPanel.add(dcaWrapper, BorderLayout.SOUTH);

        // Exit condition
        JPanel exitPanel = new JPanel(new BorderLayout(0, 2));
        exitPanel.setOpaque(false);
        JLabel exitLabel = new JLabel("Exit Condition:");
        exitLabel.setForeground(Color.GRAY);
        exitPanel.add(exitLabel, BorderLayout.NORTH);
        JScrollPane exitScroll = new JScrollPane(exitEditor);
        exitPanel.add(exitScroll, BorderLayout.CENTER);

        // Entry/Exit combined
        JPanel entryExitPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        entryExitPanel.setOpaque(false);
        entryExitPanel.add(entryPanel);
        entryExitPanel.add(exitPanel);

        // Stop-loss / Take-profit / Trade management rows
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setOpaque(false);

        // Stop Loss row
        settingsPanel.add(stopLossTypeCombo, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));
        settingsPanel.add(stopLossValueSpinner, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));

        // Take Profit row
        settingsPanel.add(takeProfitTypeCombo, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));
        settingsPanel.add(takeProfitValueSpinner, new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));

        // Max Open Trades row
        JLabel maxTradesLabel = new JLabel("Max Open Trades:");
        maxTradesLabel.setForeground(Color.GRAY);
        settingsPanel.add(maxTradesLabel, new GridBagConstraints(0, 2, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));
        settingsPanel.add(maxOpenTradesSpinner, new GridBagConstraints(1, 2, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));

        // Min Candles Between row
        JLabel minCandlesLabel = new JLabel("Min Candles Between:");
        minCandlesLabel.setForeground(Color.GRAY);
        settingsPanel.add(minCandlesLabel, new GridBagConstraints(0, 3, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 4), 0, 0));
        settingsPanel.add(minCandlesBetweenSpinner, new GridBagConstraints(1, 3, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 4), 0, 0));

        // Main content
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setOpaque(false);
        contentPanel.add(entryExitPanel, BorderLayout.CENTER);
        contentPanel.add(settingsPanel, BorderLayout.SOUTH);

        add(title, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * Set the strategy to edit
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        suppressChangeEvents = true;

        try {
            if (strategy != null) {
                entryEditor.setText(strategy.getEntry());
                exitEditor.setText(strategy.getExit());

                // Set SL type and value
                stopLossTypeCombo.setSelectedIndex(slTypeToIndex(strategy.getStopLossType()));
                stopLossValueSpinner.setValue(strategy.getStopLossValue() != null ? strategy.getStopLossValue() : 2.0);
                updateSlSpinnerVisibility();

                // Set TP type and value
                takeProfitTypeCombo.setSelectedIndex(tpTypeToIndex(strategy.getTakeProfitType()));
                takeProfitValueSpinner.setValue(strategy.getTakeProfitValue() != null ? strategy.getTakeProfitValue() : 5.0);
                updateTpSpinnerVisibility();

                // Set trade management values
                maxOpenTradesSpinner.setValue(strategy.getMaxOpenTrades());
                minCandlesBetweenSpinner.setValue(strategy.getMinCandlesBetweenTrades());

                // Set DCA values
                dcaEnabledCheckbox.setSelected(strategy.isDcaEnabled());
                dcaMaxEntriesSpinner.setValue(strategy.getDcaMaxEntries());
                dcaBarsBetweenSpinner.setValue(strategy.getDcaBarsBetween());
                dcaModeCombo.setSelectedIndex("continue_always".equals(strategy.getDcaMode()) ? 1 : 0);
                updateDcaVisibility();
            } else {
                entryEditor.setText("");
                exitEditor.setText("");
                stopLossTypeCombo.setSelectedIndex(0);
                takeProfitTypeCombo.setSelectedIndex(0);
                maxOpenTradesSpinner.setValue(1);
                minCandlesBetweenSpinner.setValue(0);
                dcaEnabledCheckbox.setSelected(false);
                dcaMaxEntriesSpinner.setValue(3);
                dcaBarsBetweenSpinner.setValue(1);
                dcaModeCombo.setSelectedIndex(0);
                updateDcaVisibility();
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;

        strategy.setEntry(entryEditor.getText().trim());
        strategy.setExit(exitEditor.getText().trim());

        String slType = indexToSlType(stopLossTypeCombo.getSelectedIndex());
        strategy.setStopLossType(slType);
        strategy.setStopLossValue("none".equals(slType) ? null : ((Number) stopLossValueSpinner.getValue()).doubleValue());

        String tpType = indexToTpType(takeProfitTypeCombo.getSelectedIndex());
        strategy.setTakeProfitType(tpType);
        strategy.setTakeProfitValue("none".equals(tpType) ? null : ((Number) takeProfitValueSpinner.getValue()).doubleValue());

        strategy.setMaxOpenTrades(((Number) maxOpenTradesSpinner.getValue()).intValue());
        strategy.setMinCandlesBetweenTrades(((Number) minCandlesBetweenSpinner.getValue()).intValue());

        strategy.setDcaEnabled(dcaEnabledCheckbox.isSelected());
        strategy.setDcaMaxEntries(((Number) dcaMaxEntriesSpinner.getValue()).intValue());
        strategy.setDcaBarsBetween(((Number) dcaBarsBetweenSpinner.getValue()).intValue());
        strategy.setDcaMode(dcaModeCombo.getSelectedIndex() == 1 ? "continue_always" : "require_signal");
    }

    /**
     * Get the current strategy with UI values applied
     */
    public Strategy getStrategy() {
        if (strategy != null) {
            applyToStrategy(strategy);
        }
        return strategy;
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
}
