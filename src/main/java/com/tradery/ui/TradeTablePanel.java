package com.tradery.ui;

import com.tradery.model.Trade;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying trades from a backtest with tree-like DCA grouping.
 * DCA positions show as expandable parent rows with individual entries as children.
 */
public class TradeTablePanel extends JPanel {

    private JTable table;
    private TreeTradeTableModel tableModel;
    private JButton detailsButton;
    private List<Trade> currentTrades = new ArrayList<>();
    private String strategyName = "";

    public TradeTablePanel() {
        setLayout(new BorderLayout());

        initializeTable();
        layoutComponents();
    }

    private void initializeTable() {
        tableModel = new TreeTradeTableModel();
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(20);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);   // # (with expand icon)
        table.getColumnModel().getColumn(1).setPreferredWidth(70);   // P&L
        table.getColumnModel().getColumn(2).setPreferredWidth(60);   // Return

        // Custom renderers
        table.getColumnModel().getColumn(0).setCellRenderer(new TreeCellRenderer(tableModel));
        table.getColumnModel().getColumn(1).setCellRenderer(new PnlCellRenderer(tableModel));
        table.getColumnModel().getColumn(2).setCellRenderer(new PnlCellRenderer(tableModel));

        // Click handling for expand/collapse
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    // Check if click is on expand/collapse icon area
                    TableRow tableRow = tableModel.getRowAt(row);
                    if (tableRow.isGroup && e.getX() < 20) {
                        tableModel.toggleExpand(row);
                        return;
                    }
                }

                if (e.getClickCount() == 2 && row >= 0) {
                    openDetailsWindow();
                }
            }
        });
    }

    private void layoutComponents() {
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));

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

        JPanel headerWrapper = new JPanel(new BorderLayout(0, 0));
        headerWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        headerWrapper.add(headerPanel, BorderLayout.CENTER);

        JSeparator headerSeparator = new JSeparator();

        JPanel topWrapper = new JPanel(new BorderLayout(0, 0));
        topWrapper.add(headerWrapper, BorderLayout.CENTER);
        topWrapper.add(headerSeparator, BorderLayout.SOUTH);

        add(topWrapper, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void openDetailsWindow() {
        if (currentTrades.isEmpty()) return;

        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        Frame parentFrame = parentWindow instanceof Frame ? (Frame) parentWindow : null;

        TradeDetailsWindow detailsWindow = new TradeDetailsWindow(parentFrame, currentTrades, strategyName);
        detailsWindow.setVisible(true);
    }

    public void updateTrades(List<Trade> trades, String strategyName) {
        this.currentTrades = trades != null ? trades : new ArrayList<>();
        this.strategyName = strategyName != null ? strategyName : "";
        tableModel.setTrades(currentTrades);
        detailsButton.setEnabled(!currentTrades.isEmpty());
    }

    public void updateTrades(List<Trade> trades) {
        updateTrades(trades, "");
    }

    public void clear() {
        this.currentTrades = new ArrayList<>();
        tableModel.setTrades(currentTrades);
        detailsButton.setEnabled(false);
    }

    /**
     * Represents a row in the tree table
     */
    private static class TableRow {
        boolean isGroup;           // true if this is a DCA group parent
        boolean isChild;           // true if this is a child of a DCA group
        boolean expanded;          // for groups: whether expanded
        int groupIndex;            // 1-based index for display
        List<Trade> trades;        // all trades in this row/group
        Trade singleTrade;         // for single trades or children
        int childIndex;            // 1-based index within group (for children)

        // For single trade or child
        static TableRow single(int index, Trade trade) {
            TableRow r = new TableRow();
            r.isGroup = false;
            r.isChild = false;
            r.groupIndex = index;
            r.singleTrade = trade;
            r.trades = List.of(trade);
            return r;
        }

        // For DCA group parent
        static TableRow group(int index, List<Trade> trades) {
            TableRow r = new TableRow();
            r.isGroup = true;
            r.isChild = false;
            r.expanded = false;
            r.groupIndex = index;
            r.trades = trades;
            return r;
        }

        // For child of DCA group
        static TableRow child(int groupIndex, int childIndex, Trade trade) {
            TableRow r = new TableRow();
            r.isGroup = false;
            r.isChild = true;
            r.groupIndex = groupIndex;
            r.childIndex = childIndex;
            r.singleTrade = trade;
            r.trades = List.of(trade);
            return r;
        }

        double getTotalPnl() {
            return trades.stream()
                .filter(t -> t.pnl() != null)
                .mapToDouble(Trade::pnl)
                .sum();
        }

        double getAvgPnlPercent() {
            double totalValue = 0;
            double totalQuantity = 0;
            for (Trade t : trades) {
                totalValue += t.entryPrice() * t.quantity();
                totalQuantity += t.quantity();
            }
            double avgEntry = totalQuantity > 0 ? totalValue / totalQuantity : 0;
            double totalPnl = getTotalPnl();
            return avgEntry > 0 ? (totalPnl / (avgEntry * totalQuantity)) * 100 : 0;
        }

        boolean isRejected() {
            if (singleTrade != null) {
                return "rejected".equals(singleTrade.exitReason());
            }
            return false;
        }
    }

    /**
     * Table model with tree-like DCA grouping
     */
    private static class TreeTradeTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "P&L", "Return"};

        private List<Trade> allTrades = new ArrayList<>();
        private List<TableRow> visibleRows = new ArrayList<>();
        private List<TableRow> groupRows = new ArrayList<>();  // All groups (for expand/collapse)

        public void setTrades(List<Trade> trades) {
            this.allTrades = trades != null ? trades : new ArrayList<>();
            buildGroups();
            rebuildVisibleRows();
            fireTableDataChanged();
        }

        private void buildGroups() {
            groupRows.clear();

            // Filter valid trades and sort by entry time
            List<Trade> validTrades = allTrades.stream()
                .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
                .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
                .toList();

            // Group overlapping trades (same logic as ChartsPanel)
            List<List<Trade>> tradeGroups = new ArrayList<>();
            for (Trade t : validTrades) {
                List<Trade> matchingGroup = null;
                for (List<Trade> group : tradeGroups) {
                    Trade firstInGroup = group.getFirst();
                    if (firstInGroup.exitTime() != null && t.entryTime() < firstInGroup.exitTime()) {
                        matchingGroup = group;
                        break;
                    }
                }
                if (matchingGroup != null) {
                    matchingGroup.add(t);
                } else {
                    List<Trade> newGroup = new ArrayList<>();
                    newGroup.add(t);
                    tradeGroups.add(newGroup);
                }
            }

            // Add rejected trades as single entries
            List<Trade> rejectedTrades = allTrades.stream()
                .filter(t -> "rejected".equals(t.exitReason()))
                .toList();

            // Create TableRows
            int index = 1;
            for (List<Trade> group : tradeGroups) {
                if (group.size() == 1) {
                    groupRows.add(TableRow.single(index, group.getFirst()));
                } else {
                    groupRows.add(TableRow.group(index, group));
                }
                index++;
            }

            // Add rejected trades at the end
            for (Trade t : rejectedTrades) {
                groupRows.add(TableRow.single(index, t));
                index++;
            }
        }

        private void rebuildVisibleRows() {
            visibleRows.clear();
            for (TableRow row : groupRows) {
                visibleRows.add(row);
                if (row.isGroup && row.expanded) {
                    // Add children
                    for (int i = 0; i < row.trades.size(); i++) {
                        visibleRows.add(TableRow.child(row.groupIndex, i + 1, row.trades.get(i)));
                    }
                }
            }
        }

        public void toggleExpand(int rowIndex) {
            TableRow row = visibleRows.get(rowIndex);
            if (row.isGroup) {
                row.expanded = !row.expanded;
                rebuildVisibleRows();
                fireTableDataChanged();
            }
        }

        public TableRow getRowAt(int rowIndex) {
            return visibleRows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return visibleRows.size();
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
            TableRow row = visibleRows.get(rowIndex);

            return switch (columnIndex) {
                case 0 -> {
                    if (row.isGroup) {
                        yield (row.expanded ? "▼ " : "▶ ") + row.groupIndex + " (" + row.trades.size() + ")";
                    } else if (row.isChild) {
                        yield "    " + row.groupIndex + "." + row.childIndex;
                    } else {
                        yield "   " + row.groupIndex;  // Pad to align with group numbers
                    }
                }
                case 1 -> {
                    if (row.isRejected()) {
                        yield "NO CAPITAL";
                    }
                    double pnl = row.isGroup ? row.getTotalPnl() :
                        (row.singleTrade != null && row.singleTrade.pnl() != null ? row.singleTrade.pnl() : 0);
                    yield String.format("%+.2f", pnl);
                }
                case 2 -> {
                    if (row.isRejected()) {
                        yield "-";
                    }
                    if (row.isGroup) {
                        yield String.format("%+.2f%%", row.getAvgPnlPercent());
                    } else if (row.singleTrade != null && row.singleTrade.pnlPercent() != null) {
                        yield String.format("%+.2f%%", row.singleTrade.pnlPercent());
                    }
                    yield "-";
                }
                default -> "";
            };
        }
    }

    /**
     * Renderer for the tree column with expand/collapse icons
     */
    private static class TreeCellRenderer extends DefaultTableCellRenderer {
        private final TreeTradeTableModel model;

        TreeCellRenderer(TreeTradeTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.LEFT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected()) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(150, 150, 150));
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
        private final TreeTradeTableModel model;

        PnlCellRenderer(TreeTradeTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected()) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                // Slightly muted colors for children
                if (value instanceof String s && !s.equals("-")) {
                    if (s.startsWith("+")) {
                        setForeground(new Color(120, 180, 120));
                    } else if (s.startsWith("-")) {
                        setForeground(new Color(200, 120, 120));
                    } else {
                        setForeground(Color.GRAY);
                    }
                }
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
