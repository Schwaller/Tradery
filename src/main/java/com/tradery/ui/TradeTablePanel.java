package com.tradery.ui;

import com.tradery.model.Trade;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Panel displaying the list of trades from a backtest.
 * Shows entry/exit times, prices, P&L, etc.
 */
public class TradeTablePanel extends JPanel {

    private JTable table;
    private TradeTableModel tableModel;

    public TradeTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        initializeTable();
        layoutComponents();
    }

    private void initializeTable() {
        tableModel = new TradeTableModel();
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(30);   // #
        table.getColumnModel().getColumn(1).setPreferredWidth(100);  // Entry Time
        table.getColumnModel().getColumn(2).setPreferredWidth(100);  // Exit Time
        table.getColumnModel().getColumn(3).setPreferredWidth(70);   // Entry Price
        table.getColumnModel().getColumn(4).setPreferredWidth(70);   // Exit Price
        table.getColumnModel().getColumn(5).setPreferredWidth(70);   // P&L
        table.getColumnModel().getColumn(6).setPreferredWidth(60);   // Return

        // Custom renderers
        TradeRowRenderer rightRenderer = new TradeRowRenderer(tableModel, SwingConstants.RIGHT);
        TradeRowRenderer leftRenderer = new TradeRowRenderer(tableModel, SwingConstants.LEFT);
        table.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);   // #
        table.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);  // Entry time
        table.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);  // Exit time
        table.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);  // Entry $
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);  // Exit $
        table.getColumnModel().getColumn(5).setCellRenderer(new PnlCellRenderer(tableModel));
        table.getColumnModel().getColumn(6).setCellRenderer(new PnlCellRenderer(tableModel));
    }

    private void layoutComponents() {
        JLabel title = new JLabel("Trades");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        JScrollPane scrollPane = new JScrollPane(table);

        add(title, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Update table with new trades
     */
    public void updateTrades(List<Trade> trades) {
        tableModel.setTrades(trades);
    }

    /**
     * Clear all trades
     */
    public void clear() {
        tableModel.setTrades(new ArrayList<>());
    }

    /**
     * Table model for trades
     */
    private static class TradeTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Entry", "Exit", "Entry $", "Exit $", "P&L", "Return"};
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d HH:mm");

        private List<Trade> trades = new ArrayList<>();

        public void setTrades(List<Trade> trades) {
            this.trades = trades != null ? trades : new ArrayList<>();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return trades.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Trade trade = trades.get(rowIndex);
            boolean isRejected = "rejected".equals(trade.exitReason());

            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> DATE_FORMAT.format(new Date(trade.entryTime()));
                case 2 -> isRejected ? "-" : (trade.exitTime() != null ?
                    DATE_FORMAT.format(new Date(trade.exitTime())) : "-");
                case 3 -> formatPrice(trade.entryPrice());
                case 4 -> isRejected ? "-" : (trade.exitPrice() != null ?
                    formatPrice(trade.exitPrice()) : "-");
                case 5 -> isRejected ? "NO CAPITAL" : (trade.pnl() != null ?
                    String.format("%+.2f", trade.pnl()) : "-");
                case 6 -> isRejected ? "-" : (trade.pnlPercent() != null ?
                    String.format("%+.2f%%", trade.pnlPercent()) : "-");
                default -> "";
            };
        }

        public Trade getTradeAt(int rowIndex) {
            return trades.get(rowIndex);
        }

        private String formatPrice(double price) {
            if (price >= 100) {
                return String.format("%,.0f", price);  // Full dollars, no decimals
            } else if (price >= 1) {
                return String.format("%.2f", price);
            } else {
                return String.format("%.6f", price);
            }
        }
    }

    /**
     * Cell renderer that grays out rejected trades
     */
    private static class TradeRowRenderer extends DefaultTableCellRenderer {
        private final TradeTableModel model;
        private final int alignment;

        TradeRowRenderer(TradeTableModel model, int alignment) {
            this.model = model;
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(alignment);

            Trade trade = model.getTradeAt(row);
            if ("rejected".equals(trade.exitReason())) {
                setForeground(new Color(180, 180, 180));  // Gray for rejected
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }

            return this;
        }
    }

    /**
     * Cell renderer for P&L columns (green for positive, red for negative)
     */
    private static class PnlCellRenderer extends DefaultTableCellRenderer {
        private final TradeTableModel model;

        PnlCellRenderer(TradeTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.RIGHT);

            Trade trade = model.getTradeAt(row);
            if ("rejected".equals(trade.exitReason())) {
                setForeground(new Color(180, 180, 180));  // Gray for rejected
            } else if (value instanceof String s && !s.equals("-")) {
                if (s.startsWith("+")) {
                    setForeground(new Color(76, 175, 80));  // Green
                } else if (s.startsWith("-")) {
                    setForeground(new Color(244, 67, 54)); // Red
                } else {
                    setForeground(Color.GRAY);
                }
            } else {
                setForeground(Color.GRAY);
            }

            return this;
        }
    }
}
