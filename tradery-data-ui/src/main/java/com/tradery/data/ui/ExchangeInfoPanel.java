package com.tradery.data.ui;

import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Panel that shows a sortable table of symbols for an exchange with spot/perp indicators
 * and available pairs (quote currencies).
 */
public class ExchangeInfoPanel extends JPanel {

    private static final Color SPOT_COLOR = new Color(78, 184, 129);
    private static final Color PERP_COLOR = new Color(86, 156, 214);
    private static final Color DIM_COLOR = new Color(80, 80, 80);

    private final JLabel statsLabel;
    private final BorderlessTable table;
    private final ExchangeTableModel model;

    public record SymbolEntry(String symbol, boolean spot, boolean perp, String pairs) {}

    public ExchangeInfoPanel() {
        super(new BorderLayout(0, 0));
        setOpaque(false);

        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        JPanel northPanel = new JPanel(new BorderLayout(0, 0));
        northPanel.setOpaque(false);
        northPanel.add(statsLabel, BorderLayout.CENTER);
        northPanel.add(new JSeparator(), BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        model = new ExchangeTableModel();
        table = new BorderlessTable(model);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        TableColumn symbolCol = table.getColumnModel().getColumn(0);
        symbolCol.setPreferredWidth(120);
        TableColumn spotCol = table.getColumnModel().getColumn(1);
        spotCol.setPreferredWidth(50);
        spotCol.setMaxWidth(60);
        TableColumn futuresCol = table.getColumnModel().getColumn(2);
        futuresCol.setPreferredWidth(60);
        futuresCol.setMaxWidth(70);
        TableColumn pairsCol = table.getColumnModel().getColumn(3);
        pairsCol.setPreferredWidth(160);

        // Dot renderers for Spot/Futures columns
        table.getColumnModel().getColumn(1).setCellRenderer(new DotRenderer(SPOT_COLOR));
        table.getColumnModel().getColumn(2).setCellRenderer(new DotRenderer(PERP_COLOR));

        // Sort on header click
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    model.sortBy(col);
                }
            }
        });

        BorderlessScrollPane scrollPane = new BorderlessScrollPane(table);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setEntries(List<SymbolEntry> entries, String statsText) {
        statsLabel.setText(statsText != null ? statsText : " ");
        model.setEntries(entries);
    }

    /**
     * Table model with click-to-sort support.
     */
    private static class ExchangeTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Symbol", "Spot", "Futures", "Pairs"};

        private List<SymbolEntry> entries = new ArrayList<>();
        private int sortColumn = -1;
        private boolean sortAscending = true;

        void setEntries(List<SymbolEntry> entries) {
            this.entries = new ArrayList<>(entries);
            if (sortColumn >= 0) {
                applySort();
            }
            fireTableDataChanged();
        }

        void sortBy(int column) {
            if (column == sortColumn) {
                sortAscending = !sortAscending;
            } else {
                sortColumn = column;
                sortAscending = true;
            }
            applySort();
            fireTableDataChanged();
        }

        private void applySort() {
            Comparator<SymbolEntry> comparator = getComparator(sortColumn);
            if (!sortAscending) comparator = comparator.reversed();
            entries.sort(comparator);
        }

        private Comparator<SymbolEntry> getComparator(int column) {
            return switch (column) {
                case 0 -> Comparator.comparing(SymbolEntry::symbol, String.CASE_INSENSITIVE_ORDER);
                case 1 -> Comparator.comparing(e -> e.spot() ? 1 : 0);
                case 2 -> Comparator.comparing(e -> e.perp() ? 1 : 0);
                case 3 -> Comparator.comparingInt(e -> {
                    String p = e.pairs();
                    if (p == null || p.isBlank()) return 0;
                    return p.split(",").length;
                });
                default -> Comparator.comparing(SymbolEntry::symbol, String.CASE_INSENSITIVE_ORDER);
            };
        }

        @Override
        public int getRowCount() {
            return entries.size();
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
            SymbolEntry e = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.symbol();
                case 1 -> e.spot();
                case 2 -> e.perp();
                case 3 -> e.pairs() != null ? e.pairs() : "";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 1, 2 -> Boolean.class;
                default -> String.class;
            };
        }
    }

    /**
     * Renders a colored dot (active color when true, dim when false).
     */
    private static class DotRenderer extends DefaultTableCellRenderer {
        private final Color activeColor;
        private boolean active;

        DotRenderer(Color activeColor) {
            this.activeColor = activeColor;
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            active = value instanceof Boolean b && b;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(active ? activeColor : DIM_COLOR);
            int size = 9;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g2.fillOval(x, y, size, size);
            g2.dispose();
        }
    }
}
