package com.tradery.trader.ui;

import com.tradery.exchange.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Manual order entry panel with symbol, side, type, size, price, leverage, margin mode.
 */
public class OrderEntryPanel extends JPanel {

    private final JTextField symbolField = new JTextField("BTC", 8);
    private final JComboBox<OrderSide> sideCombo = new JComboBox<>(OrderSide.values());
    private final JComboBox<OrderType> typeCombo = new JComboBox<>(
            new OrderType[]{OrderType.MARKET, OrderType.LIMIT, OrderType.STOP_MARKET});
    private final JTextField quantityField = new JTextField("0.01", 8);
    private final JTextField priceField = new JTextField(8);
    private final JTextField triggerField = new JTextField(8);
    private final JCheckBox reduceOnlyCheck = new JCheckBox("Reduce Only");
    private final JButton submitButton = new JButton("Place Order");

    private Consumer<OrderRequest> onSubmit;

    public OrderEntryPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addField(gbc, row++, "Symbol", symbolField);
        addField(gbc, row++, "Side", sideCombo);
        addField(gbc, row++, "Type", typeCombo);
        addField(gbc, row++, "Quantity", quantityField);
        addField(gbc, row++, "Price", priceField);
        addField(gbc, row++, "Trigger", triggerField);

        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(reduceOnlyCheck, gbc);
        gbc.gridwidth = 1;

        // Submit button
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        submitButton.addActionListener(e -> submit());
        add(submitButton, gbc);
        gbc.gridwidth = 1;

        // Push content to top
        gbc.gridy = row;
        gbc.weighty = 1.0;
        add(Box.createVerticalGlue(), gbc);

        // Toggle price/trigger visibility based on order type
        typeCombo.addActionListener(e -> updateFieldVisibility());
        updateFieldVisibility();
    }

    public void setOnSubmit(Consumer<OrderRequest> handler) {
        this.onSubmit = handler;
    }

    public void setSymbol(String symbol) {
        symbolField.setText(symbol);
    }

    private void submit() {
        if (onSubmit == null) return;

        try {
            OrderType type = (OrderType) typeCombo.getSelectedItem();
            Double price = priceField.getText().isBlank() ? null : Double.parseDouble(priceField.getText());
            Double trigger = triggerField.getText().isBlank() ? null : Double.parseDouble(triggerField.getText());

            OrderRequest request = OrderRequest.builder()
                    .symbol(symbolField.getText().trim())
                    .side((OrderSide) sideCombo.getSelectedItem())
                    .type(type)
                    .quantity(Double.parseDouble(quantityField.getText()))
                    .price(price)
                    .triggerPrice(trigger)
                    .reduceOnly(reduceOnlyCheck.isSelected())
                    .build();

            onSubmit.accept(request);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid order: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateFieldVisibility() {
        OrderType type = (OrderType) typeCombo.getSelectedItem();
        boolean showPrice = type != OrderType.MARKET;
        boolean showTrigger = type == OrderType.STOP_MARKET || type == OrderType.STOP_LIMIT;
        priceField.setEnabled(showPrice);
        triggerField.setEnabled(showTrigger);
    }

    private void addField(GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridy = row;

        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        lbl.setFont(lbl.getFont().deriveFont(11f));
        add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(field, gbc);
    }
}
