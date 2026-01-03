package com.tradery.ui;

import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Panel for configuring entry conditions and related settings.
 */
public class EntryConfigPanel extends JPanel {

    private JTextArea entryEditor;
    private JCheckBox dcaEnabledCheckbox;
    private JSpinner dcaMaxEntriesSpinner;
    private JSpinner dcaBarsBetweenSpinner;
    private JComboBox<String> dcaModeCombo;
    private JPanel dcaDetailsPanel;

    private static final String[] DCA_MODES = {"Pause", "Abort", "Continue"};

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public EntryConfigPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        entryEditor = new JTextArea(3, 20);
        entryEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        entryEditor.setLineWrap(true);
        entryEditor.setWrapStyleWord(true);

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
        dcaEnabledCheckbox.addActionListener(e -> fireChange());
        dcaMaxEntriesSpinner.addChangeListener(e -> fireChange());
        dcaBarsBetweenSpinner.addChangeListener(e -> fireChange());
        dcaModeCombo.addActionListener(e -> fireChange());
    }

    private void layoutComponents() {
        // Entry condition
        JPanel conditionPanel = new JPanel(new BorderLayout(0, 2));
        conditionPanel.setOpaque(false);
        JLabel entryLabel = new JLabel("Entry");
        entryLabel.setForeground(Color.GRAY);
        conditionPanel.add(entryLabel, BorderLayout.NORTH);
        JScrollPane entryScroll = new JScrollPane(entryEditor);
        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setOpaque(false);
        scrollWrapper.add(Box.createVerticalStrut(12), BorderLayout.NORTH);
        scrollWrapper.add(entryScroll, BorderLayout.CENTER);
        conditionPanel.add(scrollWrapper, BorderLayout.CENTER);

        // DCA section
        JPanel dcaCheckboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        dcaCheckboxPanel.setOpaque(false);
        dcaCheckboxPanel.add(dcaEnabledCheckbox);

        dcaDetailsPanel = new JPanel(new GridBagLayout());
        dcaDetailsPanel.setOpaque(false);
        dcaDetailsPanel.setVisible(false);

        JLabel maxEntriesLabel = new JLabel("Max entries:");
        maxEntriesLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(maxEntriesLabel, gbc(0, 0, false));
        dcaDetailsPanel.add(dcaMaxEntriesSpinner, gbc(1, 0, true));

        JLabel barsBetweenLabel = new JLabel("Bars between:");
        barsBetweenLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(barsBetweenLabel, gbc(0, 1, false));
        dcaDetailsPanel.add(dcaBarsBetweenSpinner, gbc(1, 1, true));

        JLabel modeLabel = new JLabel("Signal Loss:");
        modeLabel.setForeground(Color.GRAY);
        dcaDetailsPanel.add(modeLabel, gbc(0, 2, false));
        dcaDetailsPanel.add(dcaModeCombo, gbc(1, 2, true));

        JPanel dcaWrapper = new JPanel(new BorderLayout(0, 0));
        dcaWrapper.setOpaque(false);
        dcaWrapper.add(dcaCheckboxPanel, BorderLayout.NORTH);
        dcaWrapper.add(dcaDetailsPanel, BorderLayout.CENTER);
        conditionPanel.add(dcaWrapper, BorderLayout.SOUTH);

        add(conditionPanel, BorderLayout.CENTER);
    }

    private GridBagConstraints gbc(int x, int y, boolean fill) {
        return new GridBagConstraints(x, y, 1, 1, fill ? 1 : 0, 0,
            GridBagConstraints.WEST, fill ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE,
            new Insets(2, 0, 2, 4), 0, 0);
    }

    private void updateDcaVisibility() {
        dcaDetailsPanel.setVisible(dcaEnabledCheckbox.isSelected());
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

    public void loadFrom(Strategy strategy) {
        suppressChangeEvents = true;
        try {
            if (strategy != null) {
                entryEditor.setText(strategy.getEntry());
                dcaEnabledCheckbox.setSelected(strategy.isDcaEnabled());
                dcaMaxEntriesSpinner.setValue(strategy.getDcaMaxEntries());
                dcaBarsBetweenSpinner.setValue(strategy.getDcaBarsBetween());
                String mode = strategy.getDcaMode();
                dcaModeCombo.setSelectedIndex("abort".equals(mode) ? 1 : "continue".equals(mode) ? 2 : 0);
                updateDcaVisibility();
            } else {
                entryEditor.setText("");
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

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setEntry(entryEditor.getText().trim());
        strategy.setDcaEnabled(dcaEnabledCheckbox.isSelected());
        strategy.setDcaMaxEntries(((Number) dcaMaxEntriesSpinner.getValue()).intValue());
        strategy.setDcaBarsBetween(((Number) dcaBarsBetweenSpinner.getValue()).intValue());
        String[] modes = {"pause", "abort", "continue"};
        strategy.setDcaMode(modes[dcaModeCombo.getSelectedIndex()]);
    }
}
