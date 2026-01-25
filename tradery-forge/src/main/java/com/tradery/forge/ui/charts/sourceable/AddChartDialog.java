package com.tradery.forge.ui.charts.sourceable;

import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.DataSourceSelection;
import com.tradery.core.model.Exchange;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog for adding a new sourceable chart.
 * Allows selecting chart type, name, and data sources.
 */
public class AddChartDialog extends JDialog {

    private JComboBox<SourceableChartType> typeCombo;
    private JTextField nameField;
    private JTextArea descriptionArea;
    private Map<Exchange, Map<DataMarketType, JCheckBox>> sourceCheckboxes;
    private JCheckBox allSourcesCheckbox;

    private final SourceableChartsManager chartsManager;
    private final Consumer<SourceableChartInstance> onChartAdded;

    public AddChartDialog(Frame owner, SourceableChartsManager chartsManager,
                          Consumer<SourceableChartInstance> onChartAdded) {
        super(owner, "Add Chart", true);
        this.chartsManager = chartsManager;
        this.onChartAdded = onChartAdded;

        initUI();

        setSize(450, 500);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));

        // Main form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        // Chart Type section
        JPanel typePanel = createLabeledRow("Chart Type:", createTypeCombo());
        formPanel.add(typePanel);
        formPanel.add(Box.createVerticalStrut(8));

        // Description (read-only, shows selected chart type info)
        descriptionArea = new JTextArea(2, 30);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBackground(UIManager.getColor("Panel.background"));
        descriptionArea.setFont(descriptionArea.getFont().deriveFont(Font.ITALIC, 11f));
        descriptionArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        updateDescription();

        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descPanel.add(descriptionArea, BorderLayout.CENTER);
        formPanel.add(descPanel);
        formPanel.add(Box.createVerticalStrut(12));

        // Name section
        nameField = new JTextField();
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JPanel namePanel = createLabeledRow("Name:", nameField);
        formPanel.add(namePanel);
        formPanel.add(Box.createVerticalStrut(16));

        // Data Sources section
        JPanel sourcesPanel = createSourcesPanel();
        formPanel.add(sourcesPanel);

        add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        JButton addButton = new JButton("Add Chart");
        addButton.addActionListener(e -> addChart());
        buttonPanel.add(addButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Set default name based on chart type
        updateDefaultName();
    }

    private JComboBox<SourceableChartType> createTypeCombo() {
        typeCombo = new JComboBox<>(SourceableChartType.values());
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SourceableChartType type) {
                    setText(type.getDisplayName());
                }
                return this;
            }
        });
        typeCombo.addActionListener(e -> {
            updateDescription();
            updateDefaultName();
            updateSourcesVisibility();
        });
        typeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return typeCombo;
    }

    private JPanel createLabeledRow(String label, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel labelComponent = new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(90, 24));
        panel.add(labelComponent, BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSourcesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Data Sources",
            TitledBorder.LEFT, TitledBorder.TOP));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // All sources checkbox
        allSourcesCheckbox = new JCheckBox("Use all available sources (combined)");
        allSourcesCheckbox.setSelected(true);
        allSourcesCheckbox.addActionListener(e -> updateSourceCheckboxesEnabled());
        panel.add(allSourcesCheckbox);
        panel.add(Box.createVerticalStrut(8));

        // Exchange/MarketType grid
        sourceCheckboxes = new HashMap<>();

        JPanel gridPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Header row
        gbc.gridx = 0; gbc.gridy = 0;
        gridPanel.add(new JLabel("Exchange"), gbc);

        gbc.gridx = 1;
        gridPanel.add(new JLabel("SPOT"), gbc);

        gbc.gridx = 2;
        gridPanel.add(new JLabel("PERP"), gbc);

        gbc.gridx = 3;
        gridPanel.add(new JLabel("DATED"), gbc);

        // Exchange rows
        int row = 1;
        for (Exchange exchange : Exchange.values()) {
            Map<DataMarketType, JCheckBox> marketCheckboxes = new HashMap<>();

            gbc.gridx = 0; gbc.gridy = row;
            gridPanel.add(new JLabel(exchange.getDisplayName()), gbc);

            int col = 1;
            for (DataMarketType marketType : DataMarketType.values()) {
                gbc.gridx = col;
                JCheckBox checkbox = new JCheckBox();
                checkbox.setEnabled(false); // Disabled by default (all sources selected)

                // Pre-select Binance PERP as common default
                if (exchange == Exchange.BINANCE && marketType == DataMarketType.FUTURES_PERP) {
                    checkbox.setSelected(true);
                }

                marketCheckboxes.put(marketType, checkbox);
                gridPanel.add(checkbox, gbc);
                col++;
            }

            sourceCheckboxes.put(exchange, marketCheckboxes);
            row++;
        }

        panel.add(gridPanel);

        return panel;
    }

    private void updateDescription() {
        SourceableChartType type = (SourceableChartType) typeCombo.getSelectedItem();
        if (type != null) {
            descriptionArea.setText(type.getDescription());
        }
    }

    private void updateDefaultName() {
        SourceableChartType type = (SourceableChartType) typeCombo.getSelectedItem();
        if (type != null && (nameField.getText().isEmpty() || isDefaultName(nameField.getText()))) {
            nameField.setText(type.getDisplayName());
        }
    }

    private boolean isDefaultName(String name) {
        for (SourceableChartType type : SourceableChartType.values()) {
            if (type.getDisplayName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void updateSourcesVisibility() {
        SourceableChartType type = (SourceableChartType) typeCombo.getSelectedItem();
        if (type == null) return;

        // Some chart types don't support multi-exchange (funding, premium, OI)
        boolean supportsMultiExchange = type.supportsMultiExchange();

        allSourcesCheckbox.setEnabled(supportsMultiExchange);
        if (!supportsMultiExchange) {
            allSourcesCheckbox.setSelected(false);
            // For single-exchange charts, enable the checkboxes
            updateSourceCheckboxesEnabled();
        }
    }

    private void updateSourceCheckboxesEnabled() {
        boolean enabled = !allSourcesCheckbox.isSelected();
        for (Map<DataMarketType, JCheckBox> marketMap : sourceCheckboxes.values()) {
            for (JCheckBox checkbox : marketMap.values()) {
                checkbox.setEnabled(enabled);
            }
        }
    }

    private void addChart() {
        SourceableChartType type = (SourceableChartType) typeCombo.getSelectedItem();
        String name = nameField.getText().trim();

        if (type == null) {
            JOptionPane.showMessageDialog(this, "Please select a chart type.",
                "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a chart name.",
                "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Build data source selection
        DataSourceSelection sources;
        if (allSourcesCheckbox.isSelected()) {
            sources = DataSourceSelection.allEnabled();
        } else {
            sources = new DataSourceSelection();
            for (Map.Entry<Exchange, Map<DataMarketType, JCheckBox>> entry : sourceCheckboxes.entrySet()) {
                Exchange exchange = entry.getKey();
                for (Map.Entry<DataMarketType, JCheckBox> marketEntry : entry.getValue().entrySet()) {
                    if (marketEntry.getValue().isSelected()) {
                        sources.addSource(exchange, marketEntry.getKey());
                    }
                }
            }

            if (sources.getSources().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select at least one data source.",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Create and add the chart
        SourceableChartInstance chart = chartsManager.createChart(type, name, sources);

        if (onChartAdded != null) {
            onChartAdded.accept(chart);
        }

        dispose();
    }

    /**
     * Show the dialog.
     */
    public static void show(Frame owner, SourceableChartsManager chartsManager,
                            Consumer<SourceableChartInstance> onChartAdded) {
        AddChartDialog dialog = new AddChartDialog(owner, chartsManager, onChartAdded);
        dialog.setVisible(true);
    }
}
