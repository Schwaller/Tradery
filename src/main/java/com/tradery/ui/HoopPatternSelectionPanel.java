package com.tradery.ui;

import com.tradery.model.HoopPatternSettings;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring entry hoop pattern settings in a strategy.
 * Exit hoop patterns are configured per exit zone.
 */
public class HoopPatternSelectionPanel extends JPanel {

    private JComboBox<HoopPatternSettings.CombineMode> entryModeCombo;
    private HoopPatternListPanel entryPatternsPanel;

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public HoopPatternSelectionPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                "Entry Hoop Patterns",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                getFont().deriveFont(Font.BOLD, 11f),
                UIManager.getColor("Label.foreground")
            ),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Mode combo
        entryModeCombo = new JComboBox<>(HoopPatternSettings.CombineMode.values());
        entryModeCombo.setRenderer(new CombineModeRenderer());
        entryModeCombo.addActionListener(e -> fireChange());

        // Pattern list panel
        entryPatternsPanel = new HoopPatternListPanel("Patterns");
        entryPatternsPanel.setOnChange(this::fireChange);
    }

    private void layoutComponents() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Entry mode row
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel entryModeLabel = new JLabel("Mode:");
        entryModeLabel.setForeground(Color.GRAY);
        topPanel.add(entryModeLabel, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        topPanel.add(entryModeCombo, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Pattern list
        add(entryPatternsPanel, BorderLayout.CENTER);
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
                HoopPatternSettings settings = strategy.getHoopPatternSettings();
                entryModeCombo.setSelectedItem(settings.getEntryMode());
                entryPatternsPanel.setPatterns(
                    settings.getRequiredEntryPatternIds(),
                    settings.getExcludedEntryPatternIds()
                );
            } else {
                entryModeCombo.setSelectedItem(HoopPatternSettings.CombineMode.DSL_ONLY);
                entryPatternsPanel.setPatterns(null, null);
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        HoopPatternSettings settings = strategy.getHoopPatternSettings();
        settings.setEntryMode((HoopPatternSettings.CombineMode) entryModeCombo.getSelectedItem());
        settings.setRequiredEntryPatternIds(entryPatternsPanel.getRequiredPatternIds());
        settings.setExcludedEntryPatternIds(entryPatternsPanel.getExcludedPatternIds());
    }

    /**
     * Renderer for CombineMode combo box items.
     */
    private static class CombineModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HoopPatternSettings.CombineMode mode) {
                String text = switch (mode) {
                    case DSL_ONLY -> "DSL Only";
                    case HOOP_ONLY -> "Hoop Only";
                    case AND -> "DSL AND Hoop";
                    case OR -> "DSL OR Hoop";
                };
                setText(text);
            }
            return this;
        }
    }
}
