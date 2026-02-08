package com.tradery.trader.ui;

import com.tradery.exchange.model.OrderResponse;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Open and recent orders table with cancel button.
 */
public class OrdersPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final OrderTableModel tableModel = new OrderTableModel();
    private final JTable table;
    private BiConsumer<String, String> onCancelOrder; // (symbol, orderId) -> cancel

    public OrdersPanel() {
        setLayout(new BorderLayout());

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Cancel button
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton cancelBtn = new JButton("Cancel Selected");
        cancelBtn.addActionListener(e -> cancelSelected());
        buttonBar.add(cancelBtn);
        add(buttonBar, BorderLayout.SOUTH);
    }

    public void update(List<OrderResponse> orders) {
        SwingUtilities.invokeLater(() -> tableModel.setOrders(orders));
    }

    public void setOnCancelOrder(BiConsumer<String, String> handler) {
        this.onCancelOrder = handler;
    }

    private void cancelSelected() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < tableModel.orders.size() && onCancelOrder != null) {
            OrderResponse order = tableModel.orders.get(row);
            onCancelOrder.accept(order.symbol(), order.orderId());
        }
    }

    private static class OrderTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Side", "Type", "Qty", "Price", "Status", "Time"};
        private List<OrderResponse> orders = new ArrayList<>();

        void setOrders(List<OrderResponse> orders) {
            this.orders = new ArrayList<>(orders);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return orders.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            OrderResponse o = orders.get(row);
            return switch (col) {
                case 0 -> o.symbol();
                case 1 -> o.side() != null ? o.side().getValue().toUpperCase() : "";
                case 2 -> o.type() != null ? o.type().getValue() : "";
                case 3 -> String.format("%.4f", o.requestedQuantity());
                case 4 -> o.avgFillPrice() != null ? String.format("%.2f", o.avgFillPrice()) : "—";
                case 5 -> o.status().getValue();
                case 6 -> o.createdAt() != null ? TIME_FMT.format(o.createdAt()) : "";
                default -> "";
            };
        }
    }
}
