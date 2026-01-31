package com.tradery.desk.ui;

import com.tradery.desk.signal.SignalEvent;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Panel showing recent signals in a scrolling log.
 */
public class SignalLogPanel extends JPanel {

    private static final int MAX_SIGNALS = 100;
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SignalTableModel tableModel;
    private final JTable table;
    private final JLabel emptyLabel;

    public SignalLogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel header = new JLabel("Signal Log");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 0));
        add(header, BorderLayout.NORTH);

        tableModel = new SignalTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);  // Time
        table.getColumnModel().getColumn(1).setPreferredWidth(50);  // Type
        table.getColumnModel().getColumn(2).setPreferredWidth(150); // Strategy
        table.getColumnModel().getColumn(3).setPreferredWidth(80);  // Symbol
        table.getColumnModel().getColumn(4).setPreferredWidth(100); // Price

        // Custom renderer for signal type colors
        table.getColumnModel().getColumn(1).setCellRenderer(new SignalTypeRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 200));
        add(scrollPane, BorderLayout.CENTER);

        // Empty state label
        emptyLabel = new JLabel("Waiting for signals...");
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        add(emptyLabel, BorderLayout.SOUTH);
    }

    /**
     * Add a new signal to the log.
     */
    public void addSignal(SignalEvent signal) {
        SwingUtilities.invokeLater(() -> {
            tableModel.addSignal(signal);
            emptyLabel.setVisible(false);

            // Auto-scroll to bottom
            int lastRow = table.getRowCount() - 1;
            if (lastRow >= 0) {
                table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
            }
        });
    }

    /**
     * Get a snapshot of recent signals.
     */
    public List<SignalEvent> getSignals() {
        return new ArrayList<>(tableModel.signals);
    }

    /**
     * Clear all signals.
     */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            tableModel.clear();
            emptyLabel.setVisible(true);
        });
    }

    /**
     * Table model for signals.
     */
    private static class SignalTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Type", "Strategy", "Symbol", "Price"};
        private final LinkedList<SignalEvent> signals = new LinkedList<>();

        public void addSignal(SignalEvent signal) {
            signals.addLast(signal);
            while (signals.size() > MAX_SIGNALS) {
                signals.removeFirst();
            }
            fireTableDataChanged();
        }

        public void clear() {
            signals.clear();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return signals.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int col) {
            SignalEvent s = signals.get(row);
            return switch (col) {
                case 0 -> TIME_FORMAT.format(s.timestamp());
                case 1 -> s.type().getLabel();
                case 2 -> s.strategyName() + " v" + s.strategyVersion();
                case 3 -> s.symbol() + " " + s.timeframe();
                case 4 -> String.format("%,.2f", s.price());
                default -> "";
            };
        }

        public SignalEvent getSignalAt(int row) {
            return row >= 0 && row < signals.size() ? signals.get(row) : null;
        }
    }

    /**
     * Renderer for signal type with colors.
     */
    private static class SignalTypeRenderer extends DefaultTableCellRenderer {
        private static final Color ENTRY_COLOR = new Color(0, 150, 0);
        private static final Color EXIT_COLOR = new Color(200, 120, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);

            if (!isSelected && value != null) {
                String type = value.toString();
                if ("ENTRY".equals(type)) {
                    c.setForeground(ENTRY_COLOR);
                } else if ("EXIT".equals(type)) {
                    c.setForeground(EXIT_COLOR);
                } else {
                    c.setForeground(table.getForeground());
                }
            }
            return c;
        }
    }
}
