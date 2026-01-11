package com.tradery.ui;

import com.tradery.model.Trade;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Window showing comprehensive trade details with tree-like DCA grouping.
 */
public class TradeDetailsWindow extends JDialog {

    private JTable table;
    private TreeDetailedTableModel tableModel;
    private final List<Trade> trades;
    private final String strategyName;

    public TradeDetailsWindow(Frame parent, List<Trade> trades, String strategyName) {
        super(parent, "Trade Details - " + strategyName, false);
        this.trades = trades != null ? trades : new ArrayList<>();
        this.strategyName = strategyName;

        // Integrated title bar look (macOS)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initializeComponents();
        layoutComponents();

        setSize(1150, 500);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        tableModel = new TreeDetailedTableModel(trades);
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Column widths
        int[] widths = {50, 45, 50, 130, 130, 100, 100, 75, 75, 70, 65, 55, 55, 50, 35, 80, 80, 70};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderers
        table.getColumnModel().getColumn(0).setCellRenderer(new TreeCellRenderer(tableModel));           // #
        table.getColumnModel().getColumn(1).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER));  // Entries
        table.getColumnModel().getColumn(2).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER));  // Side
        table.getColumnModel().getColumn(3).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Time
        table.getColumnModel().getColumn(4).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Time
        table.getColumnModel().getColumn(5).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Price
        table.getColumnModel().getColumn(6).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Price
        table.getColumnModel().getColumn(7).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Quantity
        table.getColumnModel().getColumn(8).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Value
        table.getColumnModel().getColumn(9).setCellRenderer(new PnlCellRenderer(tableModel));   // P&L
        table.getColumnModel().getColumn(10).setCellRenderer(new PnlCellRenderer(tableModel));  // Return
        table.getColumnModel().getColumn(11).setCellRenderer(new MfeCellRenderer(tableModel));  // MFE
        table.getColumnModel().getColumn(12).setCellRenderer(new MaeCellRenderer(tableModel));  // MAE
        table.getColumnModel().getColumn(13).setCellRenderer(new CaptureCellRenderer(tableModel));  // Capture
        table.getColumnModel().getColumn(14).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));  // Duration
        table.getColumnModel().getColumn(15).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));  // Commission
        table.getColumnModel().getColumn(16).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER)); // Exit Reason
        table.getColumnModel().getColumn(17).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER)); // Zone

        // Click handling for expand/collapse
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    TableRow tableRow = tableModel.getRowAt(row);
                    if (tableRow.isGroup && e.getX() < 20) {
                        tableModel.toggleExpand(row);
                    }
                }
            }
        });
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        // Extra top padding for macOS transparent title bar
        contentPane.setBorder(BorderFactory.createEmptyBorder(28, 12, 12, 12));

        // Top panel with title and summary
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));

        // Title label (since macOS title bar is hidden)
        JLabel titleLabel = new JLabel("Trade Details - " + strategyName);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        topPanel.add(titleLabel, BorderLayout.NORTH);

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

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        topPanel.add(headerPanel, BorderLayout.CENTER);
        contentPane.add(topPanel, BorderLayout.NORTH);
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
     * Represents a row in the tree table
     */
    private static class TableRow {
        boolean isGroup;
        boolean isChild;
        boolean expanded;
        int groupIndex;
        List<Trade> trades;
        Trade singleTrade;
        int childIndex;

        static TableRow single(int index, Trade trade) {
            TableRow r = new TableRow();
            r.isGroup = false;
            r.isChild = false;
            r.groupIndex = index;
            r.singleTrade = trade;
            r.trades = List.of(trade);
            return r;
        }

        static TableRow group(int index, List<Trade> trades) {
            TableRow r = new TableRow();
            r.isGroup = true;
            r.isChild = false;
            r.expanded = false;
            r.groupIndex = index;
            r.trades = trades;
            return r;
        }

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
            return trades.stream().filter(t -> t.pnl() != null).mapToDouble(Trade::pnl).sum();
        }

        double getTotalCommission() {
            return trades.stream().filter(t -> t.commission() != null).mapToDouble(Trade::commission).sum();
        }

        double getTotalQuantity() {
            return trades.stream().mapToDouble(Trade::quantity).sum();
        }

        double getTotalValue() {
            return trades.stream().mapToDouble(Trade::value).sum();
        }

        double getAvgEntryPrice() {
            double totalValue = 0;
            double totalQty = 0;
            for (Trade t : trades) {
                totalValue += t.entryPrice() * t.quantity();
                totalQty += t.quantity();
            }
            return totalQty > 0 ? totalValue / totalQty : 0;
        }

        double getAvgPnlPercent() {
            double avgEntry = getAvgEntryPrice();
            double totalQty = getTotalQuantity();
            double totalPnl = getTotalPnl();
            return avgEntry > 0 ? (totalPnl / (avgEntry * totalQty)) * 100 : 0;
        }

        long getFirstEntryTime() {
            return trades.stream().mapToLong(Trade::entryTime).min().orElse(0);
        }

        Long getExitTime() {
            return trades.isEmpty() ? null : trades.getFirst().exitTime();
        }

        Double getExitPrice() {
            return trades.isEmpty() ? null : trades.getFirst().exitPrice();
        }

        String getSide() {
            return trades.isEmpty() ? "long" : trades.getFirst().side();
        }

        String getExitReason() {
            return trades.isEmpty() ? null : trades.getFirst().exitReason();
        }

        String getExitZone() {
            return trades.isEmpty() ? null : trades.getFirst().exitZone();
        }

        boolean isRejected() {
            return singleTrade != null && "rejected".equals(singleTrade.exitReason());
        }

        Double getMfe() {
            if (singleTrade != null) {
                return singleTrade.mfe();
            }
            // For groups, return the best MFE across all trades
            Double best = null;
            for (Trade t : trades) {
                if (t.mfe() != null && (best == null || t.mfe() > best)) {
                    best = t.mfe();
                }
            }
            return best;
        }

        Double getMae() {
            if (singleTrade != null) {
                return singleTrade.mae();
            }
            // For groups, return the worst MAE across all trades
            Double worst = null;
            for (Trade t : trades) {
                if (t.mae() != null && (worst == null || t.mae() < worst)) {
                    worst = t.mae();
                }
            }
            return worst;
        }

        Double getCaptureRatio() {
            if (singleTrade != null) {
                return singleTrade.captureRatio();
            }
            // For groups, calculate aggregate capture ratio
            Double mfe = getMfe();
            if (mfe == null || mfe <= 0) return null;
            double avgPnl = getAvgPnlPercent();
            return avgPnl / mfe;
        }

        Integer getDuration() {
            if (singleTrade != null) {
                return singleTrade.duration();
            }
            // For groups, return duration from first entry to last exit
            if (trades.isEmpty()) return null;
            int firstEntry = trades.stream().mapToInt(Trade::entryBar).min().orElse(0);
            int lastExit = trades.stream()
                .filter(t -> t.exitBar() != null)
                .mapToInt(Trade::exitBar)
                .max().orElse(firstEntry);
            return lastExit - firstEntry;
        }
    }

    /**
     * Tree-based detailed table model
     */
    private static class TreeDetailedTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "#", "Entries", "Side", "Entry Time", "Exit Time", "Entry $", "Exit $",
            "Quantity", "Value", "P&L", "Return", "MFE", "MAE", "Capture", "Dur",
            "Commission", "Exit Reason", "Zone"
        };
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        private List<TableRow> visibleRows = new ArrayList<>();
        private List<TableRow> groupRows = new ArrayList<>();

        TreeDetailedTableModel(List<Trade> trades) {
            buildGroups(trades != null ? trades : new ArrayList<>());
            rebuildVisibleRows();
        }

        private void buildGroups(List<Trade> allTrades) {
            groupRows.clear();

            List<Trade> validTrades = allTrades.stream()
                .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
                .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
                .toList();

            // Group trades by groupId
            java.util.Map<String, List<Trade>> tradesByGroup = new java.util.LinkedHashMap<>();
            for (Trade t : validTrades) {
                String groupId = t.groupId() != null ? t.groupId() : "single-" + t.id();
                tradesByGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(t);
            }

            List<Trade> rejectedTrades = allTrades.stream()
                .filter(t -> "rejected".equals(t.exitReason()))
                .toList();

            int index = 1;
            for (List<Trade> group : tradesByGroup.values()) {
                if (group.size() == 1) {
                    groupRows.add(TableRow.single(index, group.getFirst()));
                } else {
                    groupRows.add(TableRow.group(index, group));
                }
                index++;
            }

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

            if (row.isGroup) {
                return switch (columnIndex) {
                    case 0 -> (row.expanded ? "▼ " : "▶ ") + row.groupIndex;
                    case 1 -> row.trades.size();  // Entries count
                    case 2 -> row.getSide().toUpperCase();
                    case 3 -> DATE_FORMAT.format(new Date(row.getFirstEntryTime()));
                    case 4 -> row.getExitTime() != null ? DATE_FORMAT.format(new Date(row.getExitTime())) : "-";
                    case 5 -> formatPrice(row.getAvgEntryPrice()) + " (avg)";
                    case 6 -> row.getExitPrice() != null ? formatPrice(row.getExitPrice()) : "-";
                    case 7 -> String.format("%.6f", row.getTotalQuantity());
                    case 8 -> String.format("%.2f", row.getTotalValue());
                    case 9 -> String.format("%+.2f", row.getTotalPnl());
                    case 10 -> String.format("%+.2f%%", row.getAvgPnlPercent());
                    case 11 -> formatMfe(row.getMfe());       // MFE
                    case 12 -> formatMae(row.getMae());       // MAE
                    case 13 -> formatCapture(row.getCaptureRatio());  // Capture
                    case 14 -> row.getDuration() != null ? String.valueOf(row.getDuration()) : "-";  // Duration
                    case 15 -> String.format("%.2f", row.getTotalCommission());
                    case 16 -> formatExitReason(row.getExitReason());
                    case 17 -> row.getExitZone() != null ? row.getExitZone() : "-";
                    default -> "";
                };
            } else {
                Trade trade = row.singleTrade;
                boolean isRejected = row.isRejected();
                String prefix = row.isChild ? "    " + row.groupIndex + "." + row.childIndex : "  " + row.groupIndex;

                return switch (columnIndex) {
                    case 0 -> prefix;
                    case 1 -> "";  // Entries (empty for single/child)
                    case 2 -> trade.side().toUpperCase();
                    case 3 -> DATE_FORMAT.format(new Date(trade.entryTime()));
                    case 4 -> isRejected ? "-" : (trade.exitTime() != null ? DATE_FORMAT.format(new Date(trade.exitTime())) : "-");
                    case 5 -> formatPrice(trade.entryPrice());
                    case 6 -> isRejected ? "-" : (trade.exitPrice() != null ? formatPrice(trade.exitPrice()) : "-");
                    case 7 -> isRejected ? "-" : String.format("%.6f", trade.quantity());
                    case 8 -> isRejected ? "-" : String.format("%.2f", trade.value());
                    case 9 -> isRejected ? "NO CAPITAL" : (trade.pnl() != null ? String.format("%+.2f", trade.pnl()) : "-");
                    case 10 -> isRejected ? "-" : (trade.pnlPercent() != null ? String.format("%+.2f%%", trade.pnlPercent()) : "-");
                    case 11 -> isRejected ? "-" : formatMfe(trade.mfe());       // MFE
                    case 12 -> isRejected ? "-" : formatMae(trade.mae());       // MAE
                    case 13 -> isRejected ? "-" : formatCapture(trade.captureRatio());  // Capture
                    case 14 -> isRejected ? "-" : (trade.duration() != null ? String.valueOf(trade.duration()) : "-");  // Duration
                    case 15 -> isRejected ? "-" : (trade.commission() != null ? String.format("%.2f", trade.commission()) : "-");
                    case 16 -> formatExitReason(trade.exitReason());
                    case 17 -> trade.exitZone() != null ? trade.exitZone() : "-";
                    default -> "";
                };
            }
        }

        private String formatMfe(Double mfe) {
            return mfe != null ? String.format("+%.1f%%", mfe) : "-";
        }

        private String formatMae(Double mae) {
            return mae != null ? String.format("%.1f%%", mae) : "-";
        }

        private String formatCapture(Double capture) {
            return capture != null ? String.format("%.0f%%", capture * 100) : "-";
        }

        private String formatPrice(double price) {
            if (price >= 100) return String.format("%,.2f", price);
            else if (price >= 1) return String.format("%.4f", price);
            else return String.format("%.8f", price);
        }

        private String formatExitReason(String reason) {
            if (reason == null) return "-";
            return switch (reason) {
                case "signal" -> "Signal";
                case "stop_loss" -> "Stop Loss";
                case "take_profit" -> "Take Profit";
                case "trailing_stop" -> "Trail Stop";
                case "zone_exit" -> "Zone Exit";
                case "rejected" -> "Rejected";
                default -> reason;
            };
        }
    }

    /**
     * Renderer for the tree column
     */
    private static class TreeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        TreeCellRenderer(TreeDetailedTableModel model) {
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
     * Cell renderer that handles groups and children
     */
    private static class TradeRowRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;
        private final int alignment;

        TradeRowRenderer(TreeDetailedTableModel model, int alignment) {
            this.model = model;
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(alignment);

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
        private final TreeDetailedTableModel model;

        PnlCellRenderer(TreeDetailedTableModel model) {
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
                if (value instanceof String s && !s.equals("-")) {
                    if (s.startsWith("+")) setForeground(new Color(120, 180, 120));
                    else if (s.startsWith("-")) setForeground(new Color(200, 120, 120));
                    else setForeground(Color.GRAY);
                }
            } else if (value instanceof String s && !s.equals("-")) {
                if (s.startsWith("+")) setForeground(new Color(76, 175, 80));
                else if (s.startsWith("-")) setForeground(new Color(244, 67, 54));
                else setForeground(Color.GRAY);
            } else {
                setForeground(Color.GRAY);
            }

            return this;
        }
    }

    /**
     * Cell renderer for MFE column (green)
     */
    private static class MfeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        MfeCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(120, 180, 120));
            } else {
                setForeground(new Color(76, 175, 80));
            }

            return this;
        }
    }

    /**
     * Cell renderer for MAE column (red)
     */
    private static class MaeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        MaeCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(200, 120, 120));
            } else {
                setForeground(new Color(244, 67, 54));
            }

            return this;
        }
    }

    /**
     * Cell renderer for Capture ratio column
     */
    private static class CaptureCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        CaptureCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (value instanceof String s) {
                // Color based on capture percentage: >70% green, 40-70% yellow, <40% red
                try {
                    int pct = Integer.parseInt(s.replace("%", ""));
                    if (tableRow.isChild) {
                        if (pct >= 70) setForeground(new Color(120, 180, 120));
                        else if (pct >= 40) setForeground(new Color(180, 180, 100));
                        else setForeground(new Color(200, 120, 120));
                    } else {
                        if (pct >= 70) setForeground(new Color(76, 175, 80));
                        else if (pct >= 40) setForeground(new Color(255, 193, 7));
                        else setForeground(new Color(244, 67, 54));
                    }
                } catch (NumberFormatException e) {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }
}
