package com.tradery.trader.ui;

import com.tradery.exchange.model.AccountState;

import javax.swing.*;
import java.awt.*;

/**
 * Displays account balance, equity, margin, and withdrawable amount.
 */
public class AccountPanel extends JPanel {

    private final JLabel balanceValue = createValueLabel();
    private final JLabel equityValue = createValueLabel();
    private final JLabel marginValue = createValueLabel();
    private final JLabel unrealizedPnlValue = createValueLabel();
    private final JLabel withdrawableValue = createValueLabel();

    public AccountPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 0, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(gbc, row++, "Balance", balanceValue);
        addRow(gbc, row++, "Equity", equityValue);
        addRow(gbc, row++, "Margin Used", marginValue);
        addRow(gbc, row++, "Unrealized PnL", unrealizedPnlValue);
        addRow(gbc, row++, "Withdrawable", withdrawableValue);

        // Push content to top
        gbc.gridy = row;
        gbc.weighty = 1.0;
        add(Box.createVerticalGlue(), gbc);
    }

    public void update(AccountState state) {
        if (state == null) return;
        SwingUtilities.invokeLater(() -> {
            balanceValue.setText(formatUsd(state.balance()));
            equityValue.setText(formatUsd(state.equity()));
            marginValue.setText(formatUsd(state.usedMargin()));
            unrealizedPnlValue.setText(formatPnl(state.unrealizedPnl()));
            withdrawableValue.setText(formatUsd(state.withdrawable()));
        });
    }

    private void addRow(GridBagConstraints gbc, int row, String label, JLabel value) {
        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        lbl.setFont(lbl.getFont().deriveFont(11f));
        add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        add(value, gbc);
    }

    private JLabel createValueLabel() {
        JLabel label = new JLabel("—");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        return label;
    }

    private String formatUsd(double value) {
        return String.format("$%,.2f", value);
    }

    private String formatPnl(double value) {
        String text = String.format("%s$%,.2f", value >= 0 ? "+" : "-", Math.abs(value));
        return text;
    }
}
