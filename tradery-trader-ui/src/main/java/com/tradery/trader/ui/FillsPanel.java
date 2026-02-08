package com.tradery.trader.ui;

import com.tradery.exchange.model.Fill;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fill history table.
 */
public class FillsPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final FillTableModel tableModel = new FillTableModel();

    public FillsPanel() {
        setLayout(new BorderLayout());

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addFill(Fill fill) {
        SwingUtilities.invokeLater(() -> tableModel.addFill(fill));
    }

    public void update(List<Fill> fills) {
        SwingUtilities.invokeLater(() -> tableModel.setFills(fills));
    }

    private static class FillTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Symbol", "Side", "Price", "Qty", "Fee"};
        private final List<Fill> fills = new ArrayList<>();

        void addFill(Fill fill) {
            fills.add(0, fill); // Newest first
            fireTableRowsInserted(0, 0);
        }

        void setFills(List<Fill> newFills) {
            fills.clear();
            fills.addAll(newFills);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return fills.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            Fill f = fills.get(row);
            return switch (col) {
                case 0 -> TIME_FMT.format(f.timestamp());
                case 1 -> f.symbol();
                case 2 -> f.side().getValue().toUpperCase();
                case 3 -> String.format("%.2f", f.price());
                case 4 -> String.format("%.4f", f.quantity());
                case 5 -> String.format("%.4f", f.fee());
                default -> "";
            };
        }
    }
}
