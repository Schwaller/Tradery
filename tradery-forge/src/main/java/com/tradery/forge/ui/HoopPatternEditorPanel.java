package com.tradery.forge.ui;

import com.tradery.core.model.Hoop;
import com.tradery.core.model.HoopPattern;
import com.tradery.core.model.PriceSmoothingType;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.ui.base.ConfigurationPanel;
import com.tradery.symbols.ui.SymbolComboBox;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;

/**
 * Panel for editing a single hoop pattern definition.
 */
public class HoopPatternEditorPanel extends ConfigurationPanel {

    private JTextField nameField;
    private JTextArea descriptionArea;
    private SymbolComboBox symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JSpinner cooldownSpinner;
    private JCheckBox allowOverlapCheck;
    private JComboBox<PriceSmoothingType> smoothingTypeCombo;
    private JSpinner smoothingPeriodSpinner;
    private JLabel smoothingPeriodLabel;
    private HoopListPanel hoopListPanel;

    private HoopPattern pattern;

    private static final String[] TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    public HoopPatternEditorPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        nameField = new JTextField();
        nameField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        descriptionArea = new JTextArea(2, 20);
        descriptionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        symbolCombo = new SymbolComboBox(ApplicationContext.getInstance().getSymbolService());

        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h"); // Default to hourly

        cooldownSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
        cooldownSpinner.setPreferredSize(new Dimension(80, cooldownSpinner.getPreferredSize().height));

        allowOverlapCheck = new JCheckBox("Allow overlapping patterns");

        smoothingTypeCombo = new JComboBox<>(PriceSmoothingType.values());
        smoothingTypeCombo.setSelectedItem(PriceSmoothingType.NONE);

        smoothingPeriodSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        smoothingPeriodSpinner.setPreferredSize(new Dimension(60, smoothingPeriodSpinner.getPreferredSize().height));

        smoothingPeriodLabel = new JLabel("Period:");
        smoothingPeriodLabel.setForeground(Color.GRAY);

        hoopListPanel = new HoopListPanel();
        hoopListPanel.setOnChange(this::fireChange);

        // Wire up change listeners
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fireChange(); }
            public void removeUpdate(DocumentEvent e) { fireChange(); }
            public void changedUpdate(DocumentEvent e) { fireChange(); }
        };

        nameField.getDocument().addDocumentListener(docListener);
        descriptionArea.getDocument().addDocumentListener(docListener);
        symbolCombo.addActionListener(e -> fireChange());
        timeframeCombo.addActionListener(e -> fireChange());
        cooldownSpinner.addChangeListener(e -> fireChange());
        allowOverlapCheck.addActionListener(e -> fireChange());
        smoothingTypeCombo.addActionListener(e -> {
            updateSmoothingPeriodVisibility();
            fireChange();
        });
        smoothingPeriodSpinner.addChangeListener(e -> fireChange());
    }

    private void updateSmoothingPeriodVisibility() {
        PriceSmoothingType type = (PriceSmoothingType) smoothingTypeCombo.getSelectedItem();
        boolean needsPeriod = type == PriceSmoothingType.SMA || type == PriceSmoothingType.EMA;
        smoothingPeriodLabel.setVisible(needsPeriod);
        smoothingPeriodSpinner.setVisible(needsPeriod);
    }

    private void layoutComponents() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Name
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel nameLabel = new JLabel("Name:");
        nameLabel.setForeground(Color.GRAY);
        formPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridwidth = 3;
        formPanel.add(nameField, gbc);

        // Symbol and Timeframe (same row)
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.GRAY);
        formPanel.add(symbolLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        formPanel.add(symbolCombo, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel tfLabel = new JLabel("Timeframe:");
        tfLabel.setForeground(Color.GRAY);
        formPanel.add(tfLabel, gbc);

        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        formPanel.add(timeframeCombo, gbc);

        // Cooldown and Allow Overlap (same row)
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel cooldownLabel = new JLabel("Cooldown bars:");
        cooldownLabel.setForeground(Color.GRAY);
        formPanel.add(cooldownLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(cooldownSpinner, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        formPanel.add(allowOverlapCheck, gbc);

        // Price Smoothing (new row)
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        JLabel smoothingLabel = new JLabel("Price Smoothing:");
        smoothingLabel.setForeground(Color.GRAY);
        formPanel.add(smoothingLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        formPanel.add(smoothingTypeCombo, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(smoothingPeriodLabel, gbc);

        gbc.gridx = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(smoothingPeriodSpinner, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel descLabel = new JLabel("Description:");
        descLabel.setForeground(Color.GRAY);
        formPanel.add(descLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridwidth = 3;
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(200, 50));
        formPanel.add(descScroll, gbc);

        // Hoops label
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(12, 4, 4, 4);
        JLabel hoopsLabel = new JLabel("Hoops (price checkpoints):");
        hoopsLabel.setForeground(Color.GRAY);
        hoopsLabel.setFont(hoopsLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(hoopsLabel, gbc);

        // Hoops list
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(4, 4, 4, 4);
        formPanel.add(hoopListPanel, gbc);

        add(formPanel, BorderLayout.CENTER);
    }

    public void loadFrom(HoopPattern pattern) {
        this.pattern = pattern;
        setSuppressChangeEvents(true);
        try {
            if (pattern != null) {
                nameField.setText(pattern.getName() != null ? pattern.getName() : "");
                descriptionArea.setText(pattern.getDescription() != null ? pattern.getDescription() : "");
                symbolCombo.setSelectedSymbol(pattern.getSymbol() != null ? pattern.getSymbol() : "BTCUSDT");
                timeframeCombo.setSelectedItem(pattern.getTimeframe() != null ? pattern.getTimeframe() : "1h");
                cooldownSpinner.setValue(pattern.getCooldownBars());
                allowOverlapCheck.setSelected(pattern.isAllowOverlap());
                smoothingTypeCombo.setSelectedItem(pattern.getPriceSmoothingType());
                smoothingPeriodSpinner.setValue(pattern.getPriceSmoothingPeriod());
                hoopListPanel.setHoops(new ArrayList<>(pattern.getHoops()));
                updateSmoothingPeriodVisibility();
            } else {
                nameField.setText("");
                descriptionArea.setText("");
                symbolCombo.setSelectedSymbol("BTCUSDT");
                timeframeCombo.setSelectedItem("1h");
                cooldownSpinner.setValue(0);
                allowOverlapCheck.setSelected(false);
                smoothingTypeCombo.setSelectedItem(PriceSmoothingType.NONE);
                smoothingPeriodSpinner.setValue(5);
                hoopListPanel.setHoops(new ArrayList<>());
                updateSmoothingPeriodVisibility();
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(HoopPattern pattern) {
        if (pattern == null) return;
        pattern.setName(nameField.getText().trim());
        pattern.setDescription(descriptionArea.getText().trim());
        pattern.setSymbol(symbolCombo.getSelectedSymbol());
        pattern.setTimeframe((String) timeframeCombo.getSelectedItem());
        pattern.setCooldownBars((Integer) cooldownSpinner.getValue());
        pattern.setAllowOverlap(allowOverlapCheck.isSelected());
        pattern.setPriceSmoothingType((PriceSmoothingType) smoothingTypeCombo.getSelectedItem());
        pattern.setPriceSmoothingPeriod((Integer) smoothingPeriodSpinner.getValue());
        pattern.setHoops(hoopListPanel.getHoops());
    }

    public HoopPattern getPattern() {
        if (pattern != null) {
            applyTo(pattern);
        }
        return pattern;
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        nameField.setEnabled(enabled);
        descriptionArea.setEnabled(enabled);
        symbolCombo.setEnabled(enabled);
        timeframeCombo.setEnabled(enabled);
        cooldownSpinner.setEnabled(enabled);
        allowOverlapCheck.setEnabled(enabled);
        smoothingTypeCombo.setEnabled(enabled);
        smoothingPeriodSpinner.setEnabled(enabled);
        hoopListPanel.setEnabled(enabled);
    }
}
