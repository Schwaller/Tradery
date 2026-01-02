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
 * Window showing comprehensive trade details.
 * Displays all trade information in a detailed table.
 */
public class TradeDetailsWindow extends JDialog {

    private JTable table;
    private DetailedTradeTableModel tableModel;
    private final List<Trade> trades;

    public TradeDetailsWindow(Frame parent, List<Trade> trades) {
        super(parent, "Trade Details", false);
        this.trades = trades != null ? trades : new ArrayList<>();

        initializeComponents();
        layoutComponents();

        setSize(950, 500);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        tableModel = new DetailedTradeTableModel(trades);
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Column widths
        int[] widths = {35, 50, 110, 110, 75, 75, 80, 80, 75, 75, 70, 80};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderers
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        // Apply renderers (# left, Side center, dates right, prices right, etc.)
        table.getColumnModel().getColumn(0).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.LEFT));    // #
        table.getColumnModel().getColumn(1).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER));  // Side
        table.getColumnModel().getColumn(2).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Time
        table.getColumnModel().getColumn(3).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Time
        table.getColumnModel().getColumn(4).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Price
        table.getColumnModel().getColumn(5).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Price
        table.getColumnModel().getColumn(6).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Quantity
        table.getColumnModel().getColumn(7).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Value
        table.getColumnModel().getColumn(8).setCellRenderer(new PnlCellRenderer(tableModel));  // P&L
        table.getColumnModel().getColumn(9).setCellRenderer(new PnlCellRenderer(tableModel));  // Return
        table.getColumnModel().getColumn(10).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));  // Commission
        table.getColumnModel().getColumn(11).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER)); // Exit Reason
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Summary header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));

        int totalTrades = 0;
        int winners = 0;
        int losers = 0;
        int rejected = 0;
        double totalPnl = 0;

        for (Trade t : trades) {
            if ("rejected".equals(t.exitReason())) {
                rejected++;
            } else {
                totalTrades++;
                if (t.pnl() != null) {
                    totalPnl += t.pnl();
                    if (t.pnl() > 0) winners++;
                    else if (t.pnl() < 0) losers++;
                }
            }
        }

        double winRate = totalTrades > 0 ? (double) winners / totalTrades * 100 : 0;

        headerPanel.add(createSummaryLabel("Total Trades", String.valueOf(totalTrades)));
        headerPanel.add(createSummaryLabel("Winners", String.valueOf(winners), new Color(76, 175, 80)));
        headerPanel.add(createSummaryLabel("Losers", String.valueOf(losers), new Color(244, 67, 54)));
        headerPanel.add(createSummaryLabel("Win Rate", String.format("%.1f%%", winRate)));
        headerPanel.add(createSummaryLabel("Total P&L", String.format("%+.2f", totalPnl),
            totalPnl >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54)));
        if (rejected > 0) {
            headerPanel.add(createSummaryLabel("Rejected", String.valueOf(rejected), Color.GRAY));
        }

        // Table
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        contentPane.add(headerPanel, BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private JPanel createSummaryLabel(String label, String value) {
        return createSummaryLabel(label, value, null);
    }

    private JPanel createSummaryLabel(String label, String value, Color valueColor) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setFont(labelComponent.getFont().deriveFont(11f));
        labelComponent.setForeground(Color.GRAY);

        JLabel valueComponent = new JLabel(value);
        valueComponent.setFont(valueComponent.getFont().deriveFont(Font.BOLD, 12f));
        if (valueColor != null) {
            valueComponent.setForeground(valueColor);
        }

        panel.add(labelComponent);
        panel.add(valueComponent);
        return panel;
    }

    /**
     * Detailed table model with all trade fields
     */
    private static class DetailedTradeTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "#", "Side", "Entry Time", "Exit Time", "Entry $", "Exit $",
            "Quantity", "Value", "P&L", "Return", "Commission", "Exit Reason"
        };
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        private final List<Trade> trades;

        DetailedTradeTableModel(List<Trade> trades) {
            this.trades = trades != null ? trades : new ArrayList<>();
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
                case 1 -> trade.side().toUpperCase();
                case 2 -> DATE_FORMAT.format(new Date(trade.entryTime()));
                case 3 -> isRejected ? "-" : (trade.exitTime() != null ?
                    DATE_FORMAT.format(new Date(trade.exitTime())) : "-");
                case 4 -> formatPrice(trade.entryPrice());
                case 5 -> isRejected ? "-" : (trade.exitPrice() != null ?
                    formatPrice(trade.exitPrice()) : "-");
                case 6 -> isRejected ? "-" : String.format("%.6f", trade.quantity());
                case 7 -> isRejected ? "-" : String.format("%.2f", trade.value());
                case 8 -> isRejected ? "NO CAPITAL" : (trade.pnl() != null ?
                    String.format("%+.2f", trade.pnl()) : "-");
                case 9 -> isRejected ? "-" : (trade.pnlPercent() != null ?
                    String.format("%+.2f%%", trade.pnlPercent()) : "-");
                case 10 -> isRejected ? "-" : (trade.commission() != null ?
                    String.format("%.2f", trade.commission()) : "-");
                case 11 -> formatExitReason(trade.exitReason());
                default -> "";
            };
        }

        public Trade getTradeAt(int rowIndex) {
            return trades.get(rowIndex);
        }

        private String formatPrice(double price) {
            if (price >= 100) {
                return String.format("%,.2f", price);
            } else if (price >= 1) {
                return String.format("%.4f", price);
            } else {
                return String.format("%.8f", price);
            }
        }

        private String formatExitReason(String reason) {
            if (reason == null) return "-";
            return switch (reason) {
                case "signal" -> "Signal";
                case "stop_loss" -> "Stop Loss";
                case "take_profit" -> "Take Profit";
                case "trailing_stop" -> "Trail Stop";
                case "rejected" -> "Rejected";
                default -> reason;
            };
        }
    }

    /**
     * Cell renderer that grays out rejected trades
     */
    private static class TradeRowRenderer extends DefaultTableCellRenderer {
        private final DetailedTradeTableModel model;
        private final int alignment;

        TradeRowRenderer(DetailedTradeTableModel model, int alignment) {
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
                setForeground(new Color(180, 180, 180));
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }

            return this;
        }
    }

    /**
     * Cell renderer for P&L columns
     */
    private static class PnlCellRenderer extends DefaultTableCellRenderer {
        private final DetailedTradeTableModel model;

        PnlCellRenderer(DetailedTradeTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.RIGHT);

            Trade trade = model.getTradeAt(row);
            if ("rejected".equals(trade.exitReason())) {
                setForeground(new Color(180, 180, 180));
            } else if (value instanceof String s && !s.equals("-")) {
                if (s.startsWith("+")) {
                    setForeground(new Color(76, 175, 80));
                } else if (s.startsWith("-")) {
                    setForeground(new Color(244, 67, 54));
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
