package com.tradery.ui;

import com.tradery.data.SyncEstimator;
import com.tradery.model.OrderflowSettings;
import com.tradery.model.Strategy;
import com.tradery.ui.base.ConfigurationPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring orderflow mode settings.
 * Shows mode dropdown with dynamic sync time estimates.
 * Data syncs automatically when backtest runs (like candle data).
 */
public class OrderflowSettingsPanel extends ConfigurationPanel {

    private JComboBox<ModeItem> modeCombo;

    // Current context for dynamic estimates
    private String currentSymbol = "BTCUSDT";
    private long currentStartTime = 0;
    private long currentEndTime = 0;

    public OrderflowSettingsPanel() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Mode dropdown with custom renderer for dynamic estimates
        modeCombo = new JComboBox<>();
        updateModeItems();
        modeCombo.setRenderer(new ModeItemRenderer());
        modeCombo.addActionListener(e -> {
            if (!isSuppressingChanges()) {
                fireChange();
            }
        });
    }

    private void updateModeItems() {
        setSuppressChangeEvents(true);
        ModeItem selected = (ModeItem) modeCombo.getSelectedItem();
        OrderflowSettings.Mode selectedMode = selected != null ? selected.mode : OrderflowSettings.Mode.DISABLED;

        modeCombo.removeAllItems();
        modeCombo.addItem(new ModeItem(OrderflowSettings.Mode.DISABLED, "Disabled", ""));

        // Dynamic sync estimate for Enabled mode
        String syncInfo = "";
        if (currentStartTime > 0 && currentEndTime > 0) {
            String estimate = SyncEstimator.estimateSyncTime(currentSymbol, currentStartTime, currentEndTime);
            syncInfo = "(~" + estimate + " first load)";
        }
        modeCombo.addItem(new ModeItem(OrderflowSettings.Mode.ENABLED, "Enabled", syncInfo));

        // Restore selection
        for (int i = 0; i < modeCombo.getItemCount(); i++) {
            if (modeCombo.getItemAt(i).mode == selectedMode) {
                modeCombo.setSelectedIndex(i);
                break;
            }
        }
        setSuppressChangeEvents(false);
    }

    private void layoutComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Orderflow Mode label
        gbc.gridx = 0;
        JLabel label = new JLabel("Orderflow:");
        label.setForeground(Color.GRAY);
        add(label, gbc);

        // Mode dropdown
        gbc.gridx = 1;
        gbc.insets = new Insets(2, 0, 2, 4);
        add(modeCombo, gbc);

        // Spacer
        gbc.gridx = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createHorizontalGlue(), gbc);
    }

    /**
     * Update the sync time estimate context.
     * Called when symbol or duration changes.
     */
    public void updateContext(String symbol, long startTime, long endTime) {
        this.currentSymbol = symbol;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;
        updateModeItems();
    }

    public void loadFrom(Strategy strategy) {
        setSuppressChangeEvents(true);
        try {
            if (strategy != null) {
                OrderflowSettings.Mode mode = strategy.getOrderflowMode();
                for (int i = 0; i < modeCombo.getItemCount(); i++) {
                    if (modeCombo.getItemAt(i).mode == mode) {
                        modeCombo.setSelectedIndex(i);
                        break;
                    }
                }
            } else {
                modeCombo.setSelectedIndex(0); // Disabled
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        ModeItem selected = (ModeItem) modeCombo.getSelectedItem();
        if (selected != null) {
            strategy.setOrderflowMode(selected.mode);
        }
    }

    public OrderflowSettings.Mode getSelectedMode() {
        ModeItem selected = (ModeItem) modeCombo.getSelectedItem();
        return selected != null ? selected.mode : OrderflowSettings.Mode.DISABLED;
    }

    /**
     * Mode item for the dropdown.
     */
    private record ModeItem(OrderflowSettings.Mode mode, String label, String suffix) {
        @Override
        public String toString() {
            return label + (suffix.isEmpty() ? "" : " " + suffix);
        }
    }

    /**
     * Custom renderer to show suffix in a different style.
     */
    private static class ModeItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ModeItem item) {
                if (!item.suffix.isEmpty()) {
                    // Use HTML to style the suffix
                    setText("<html>" + item.label + " <font color='gray'>" + item.suffix + "</font></html>");
                } else {
                    setText(item.label);
                }
            }

            return this;
        }
    }
}
