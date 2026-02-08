package com.tradery.trader.ui;

import com.tradery.exchange.model.MarginMode;
import com.tradery.exchange.model.TradingConfig;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Venue selection, credential entry, and risk limits configuration.
 */
public class TradingConfigPanel extends JPanel {

    private final JComboBox<String> venueCombo = new JComboBox<>(
            new String[]{"hyperliquid", "binance-futures", "jupiter"});
    private final JCheckBox testnetCheck = new JCheckBox("Testnet");
    private final JTextField addressField = new JTextField(20);
    private final JPasswordField privateKeyField = new JPasswordField(20);
    private final JCheckBox paperModeCheck = new JCheckBox("Paper Trading");

    // Risk limits
    private final JSpinner maxPositionSize = new JSpinner(new SpinnerNumberModel(10000, 100, 1000000, 1000));
    private final JSpinner maxOpenPositions = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
    private final JSpinner maxDailyLoss = new JSpinner(new SpinnerNumberModel(5.0, 0.5, 50.0, 0.5));
    private final JSpinner maxDrawdown = new JSpinner(new SpinnerNumberModel(15.0, 1.0, 50.0, 1.0));

    private Consumer<TradingConfig> onConfigChanged;

    public TradingConfigPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Venue section
        JPanel venueSection = createSection("Venue");
        addRow(venueSection, "Exchange", venueCombo);
        addRow(venueSection, "", testnetCheck);
        addRow(venueSection, "Address", addressField);
        addRow(venueSection, "Private Key", privateKeyField);
        addRow(venueSection, "", paperModeCheck);
        add(venueSection);

        add(new JSeparator());

        // Risk section
        JPanel riskSection = createSection("Risk Limits");
        addRow(riskSection, "Max Position $", maxPositionSize);
        addRow(riskSection, "Max Positions", maxOpenPositions);
        addRow(riskSection, "Max Daily Loss %", maxDailyLoss);
        addRow(riskSection, "Max Drawdown %", maxDrawdown);
        add(riskSection);

        add(Box.createVerticalGlue());
    }

    public void loadConfig(TradingConfig config) {
        venueCombo.setSelectedItem(config.getActiveVenue());
        paperModeCheck.setSelected(config.getPaperTrading().isEnabled());

        var venueConfig = config.getActiveVenueConfig();
        if (venueConfig != null) {
            testnetCheck.setSelected(venueConfig.isTestnet());
            addressField.setText(venueConfig.getAddress() != null ? venueConfig.getAddress() : "");
        }

        maxPositionSize.setValue((int) config.getRisk().getMaxPositionSizeUsd());
        maxOpenPositions.setValue(config.getRisk().getMaxOpenPositions());
        maxDailyLoss.setValue(config.getRisk().getMaxDailyLossPercent());
        maxDrawdown.setValue(config.getRisk().getMaxDrawdownPercent());
    }

    public void setOnConfigChanged(Consumer<TradingConfig> handler) {
        this.onConfigChanged = handler;
    }

    private JPanel createSection(String title) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 8, 0);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 1f));
        section.add(titleLabel, gbc);

        return section;
    }

    private void addRow(JPanel panel, String label, JComponent field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 0, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = panel.getComponentCount() / 2;

        if (!label.isEmpty()) {
            gbc.gridx = 0;
            gbc.weightx = 0;
            JLabel lbl = new JLabel(label);
            lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
            lbl.setFont(lbl.getFont().deriveFont(11f));
            panel.add(lbl, gbc);
        }

        gbc.gridx = label.isEmpty() ? 0 : 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        if (label.isEmpty()) gbc.gridwidth = 2;
        panel.add(field, gbc);
    }
}
