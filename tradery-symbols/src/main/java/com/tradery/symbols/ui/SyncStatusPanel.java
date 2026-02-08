package com.tradery.symbols.ui;

import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.service.SymbolService.ExchangeCoverage;
import com.tradery.symbols.service.SymbolService.ExchangeSyncInfo;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status panel showing per-exchange/market sync breakdown in a compact table.
 */
public class SyncStatusPanel extends JPanel {

    private final CoverageTableModel tableModel;
    private final JLabel summaryLabel;

    public SyncStatusPanel(SymbolService service) {
        setLayout(new BorderLayout(0, 0));

        tableModel = new CoverageTableModel();
        JTable table = new BorderlessTable(tableModel);
        table.setRowHeight(20);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setFont(table.getFont().deriveFont(11f));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 11f));
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(80);  // Exchange
        table.getColumnModel().getColumn(1).setPreferredWidth(50);  // Market
        table.getColumnModel().getColumn(2).setPreferredWidth(60);  // Pairs
        table.getColumnModel().getColumn(3).setPreferredWidth(90);  // Matched
        table.getColumnModel().getColumn(4).setPreferredWidth(70);  // Last Sync
        table.getColumnModel().getColumn(5).setPreferredWidth(50);  // Status

        // Dim secondary columns
        DefaultTableCellRenderer dimRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                c.setForeground(UIManager.getColor("Label.disabledForeground"));
                return c;
            }
        };
        table.getColumnModel().getColumn(1).setCellRenderer(dimRenderer); // Market
        table.getColumnModel().getColumn(4).setCellRenderer(dimRenderer); // Last Sync

        // Right-align numeric columns
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(rightRenderer); // Pairs

        // Matched column: right-aligned, dim
        DefaultTableCellRenderer matchedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                c.setForeground(UIManager.getColor("Label.disabledForeground"));
                return c;
            }
        };
        table.getColumnModel().getColumn(3).setCellRenderer(matchedRenderer);

        // Status column: colored text
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        BorderlessScrollPane scrollPane = new BorderlessScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(0, 110));
        add(scrollPane, BorderLayout.CENTER);

        // Summary label at the bottom
        summaryLabel = new JLabel();
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 11f));
        summaryLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(summaryLabel, BorderLayout.SOUTH);

        refresh(service);
    }

    public void refresh(SymbolService service) {
        if (!service.isDatabaseAvailable()) {
            tableModel.setRows(List.of());
            summaryLabel.setText("Symbol database not available");
            return;
        }

        List<ExchangeCoverage> coverage = service.getExchangeCoverage();
        List<ExchangeSyncInfo> syncInfo = service.getSyncInfo();

        // Index sync info by exchange+market
        Map<String, ExchangeSyncInfo> syncMap = new LinkedHashMap<>();
        for (ExchangeSyncInfo info : syncInfo) {
            syncMap.put(info.exchange() + "|" + info.marketType(), info);
        }

        // Build rows
        List<CoverageRow> rows = new ArrayList<>();
        int totalPairs = 0;
        int totalMatched = 0;
        for (ExchangeCoverage cov : coverage) {
            ExchangeSyncInfo info = syncMap.get(cov.exchange() + "|" + cov.marketType());
            Instant lastSync = info != null ? info.lastSync() : null;
            String status = info != null ? info.status() : null;

            // Determine display status
            String displayStatus;
            if ("error".equalsIgnoreCase(status)) {
                displayStatus = "ERROR";
            } else if (lastSync != null && Duration.between(lastSync, Instant.now()).toHours() > 24) {
                displayStatus = "STALE";
            } else if (lastSync != null) {
                displayStatus = "OK";
            } else {
                displayStatus = "—";
            }

            rows.add(new CoverageRow(
                cov.exchange(), cov.marketType(), cov.pairCount(), cov.matchedCount(),
                lastSync != null ? formatRelativeTime(lastSync) : "—",
                displayStatus
            ));
            totalPairs += cov.pairCount();
            totalMatched += cov.matchedCount();
        }

        tableModel.setRows(rows);
        summaryLabel.setText(String.format("Total: %,d pairs, %,d matched", totalPairs, totalMatched));
    }

    static String formatRelativeTime(Instant instant) {
        Duration d = Duration.between(instant, Instant.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = d.toHours();
        if (hours < 24) return hours + "h ago";
        long days = d.toDays();
        return days + "d ago";
    }

    // --- Data row ---
    record CoverageRow(String exchange, String market, int pairs, int matched, String lastSync, String status) {}

    // --- Table Model ---
    private static class CoverageTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Exchange", "Market", "Pairs", "Matched", "Last Sync", "Status"};
        private List<CoverageRow> rows = List.of();

        void setRows(List<CoverageRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CoverageRow r = rows.get(row);
            return switch (col) {
                case 0 -> r.exchange();
                case 1 -> r.market();
                case 2 -> String.format("%,d", r.pairs());
                case 3 -> String.format("%,d / %,d", r.matched(), r.pairs());
                case 4 -> r.lastSync();
                case 5 -> r.status();
                default -> "";
            };
        }
    }

    // --- Status cell renderer with colored text ---
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_OK = new Color(60, 140, 60);
        private static final Color COLOR_ERROR = new Color(180, 60, 60);
        private static final Color COLOR_STALE = new Color(180, 120, 40);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            String status = value != null ? value.toString() : "";
            c.setForeground(switch (status) {
                case "OK" -> COLOR_OK;
                case "ERROR" -> COLOR_ERROR;
                case "STALE" -> COLOR_STALE;
                default -> UIManager.getColor("Label.disabledForeground");
            });
            return c;
        }
    }
}
