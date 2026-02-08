package com.tradery.trader.ui;

import com.tradery.exchange.model.ExchangePosition;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Open positions table with live PnL updates.
 */
public class PositionsPanel extends JPanel {

    private final PositionTableModel tableModel = new PositionTableModel();
    private final JTable table;

    public PositionsPanel() {
        setLayout(new BorderLayout());

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // PnL column renderer (green/red)
        table.getColumnModel().getColumn(4).setCellRenderer(new PnlRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    public void update(List<ExchangePosition> positions) {
        SwingUtilities.invokeLater(() -> tableModel.setPositions(positions));
    }

    public ExchangePosition getSelectedPosition() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < tableModel.positions.size()) {
            return tableModel.positions.get(row);
        }
        return null;
    }

    private static class PositionTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Side", "Size", "Entry", "PnL", "Leverage", "Liq Price"};
        private List<ExchangePosition> positions = new ArrayList<>();

        void setPositions(List<ExchangePosition> positions) {
            this.positions = new ArrayList<>(positions);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return positions.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ExchangePosition pos = positions.get(row);
            return switch (col) {
                case 0 -> pos.symbol();
                case 1 -> pos.isLong() ? "LONG" : "SHORT";
                case 2 -> String.format("%.4f", pos.quantity());
                case 3 -> String.format("%.2f", pos.entryPrice());
                case 4 -> pos.unrealizedPnl();
                case 5 -> pos.leverage() + "x " + pos.marginMode().getValue();
                case 6 -> pos.liquidationPrice() > 0 ? String.format("%.2f", pos.liquidationPrice()) : "—";
                default -> "";
            };
        }
    }

    private static class PnlRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value instanceof Double pnl) {
                setText(String.format("%s$%.2f", pnl >= 0 ? "+" : "", pnl));
                if (!isSelected) {
                    setForeground(pnl >= 0 ? new Color(0, 180, 80) : new Color(220, 50, 50));
                }
            }
            setHorizontalAlignment(RIGHT);
            return this;
        }
    }
}
