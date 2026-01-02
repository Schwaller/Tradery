package com.tradery.ui;

import com.tradery.model.Trade;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified panel displaying trades from a backtest.
 * Shows only essential info (#, P&L, Return) with a Details button.
 */
public class TradeTablePanel extends JPanel {

    private JTable table;
    private TradeTableModel tableModel;
    private JButton detailsButton;
    private List<Trade> currentTrades = new ArrayList<>();

    public TradeTablePanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setOpaque(true);

        initializeTable();
        layoutComponents();
    }

    private void initializeTable() {
        tableModel = new TradeTableModel();
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);

        // Center table header
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getTableHeader().setDefaultRenderer(headerRenderer);

        // Column widths - simplified to 3 columns
        table.getColumnModel().getColumn(0).setPreferredWidth(30);   // #
        table.getColumnModel().getColumn(1).setPreferredWidth(70);   // P&L
        table.getColumnModel().getColumn(2).setPreferredWidth(60);   // Return

        // Custom renderers
        TradeRowRenderer leftRenderer = new TradeRowRenderer(tableModel, SwingConstants.LEFT);
        table.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);   // #
        table.getColumnModel().getColumn(1).setCellRenderer(new PnlCellRenderer(tableModel));
        table.getColumnModel().getColumn(2).setCellRenderer(new PnlCellRenderer(tableModel));

        // Double-click to open details
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    openDetailsWindow();
                }
            }
        });
    }

    private void layoutComponents() {
        // Header with title and Details button
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setOpaque(true);

        JLabel title = new JLabel("Trades");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        detailsButton = new JButton("Details...");
        detailsButton.setFont(detailsButton.getFont().deriveFont(11f));
        detailsButton.setEnabled(false);
        detailsButton.addActionListener(e -> openDetailsWindow());

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(detailsButton, BorderLayout.EAST);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Header with padding
        JPanel headerWrapper = new JPanel(new BorderLayout(0, 0));
        headerWrapper.setBackground(Color.WHITE);
        headerWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        headerWrapper.add(headerPanel, BorderLayout.CENTER);

        // Full-width separator below header
        JPanel headerSeparator = new JPanel();
        headerSeparator.setPreferredSize(new Dimension(0, 1));
        headerSeparator.setBackground(new Color(200, 200, 200));

        // Combine header + separator
        JPanel topWrapper = new JPanel(new BorderLayout(0, 0));
        topWrapper.setBackground(Color.WHITE);
        topWrapper.add(headerWrapper, BorderLayout.CENTER);
        topWrapper.add(headerSeparator, BorderLayout.SOUTH);

        add(topWrapper, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void openDetailsWindow() {
        if (currentTrades.isEmpty()) return;

        // Find parent frame
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        Frame parentFrame = parentWindow instanceof Frame ? (Frame) parentWindow : null;

        TradeDetailsWindow detailsWindow = new TradeDetailsWindow(parentFrame, currentTrades);
        detailsWindow.setVisible(true);
    }

    /**
     * Update table with new trades
     */
    public void updateTrades(List<Trade> trades) {
        this.currentTrades = trades != null ? trades : new ArrayList<>();
        tableModel.setTrades(currentTrades);
        detailsButton.setEnabled(!currentTrades.isEmpty());
    }

    /**
     * Clear all trades
     */
    public void clear() {
        this.currentTrades = new ArrayList<>();
        tableModel.setTrades(currentTrades);
        detailsButton.setEnabled(false);
    }

    /**
     * Simplified table model for trades - only 3 columns
     */
    private static class TradeTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "P&L", "Return"};

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
                case 1 -> isRejected ? "NO CAPITAL" : (trade.pnl() != null ?
                    String.format("%+.2f", trade.pnl()) : "-");
                case 2 -> isRejected ? "-" : (trade.pnlPercent() != null ?
                    String.format("%+.2f%%", trade.pnlPercent()) : "-");
                default -> "";
            };
        }

        public Trade getTradeAt(int rowIndex) {
            return trades.get(rowIndex);
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
