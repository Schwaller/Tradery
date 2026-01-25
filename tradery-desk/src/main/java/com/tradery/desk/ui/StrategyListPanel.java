package com.tradery.desk.ui;

import com.tradery.desk.strategy.PublishedStrategy;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying active strategies with their versions.
 */
public class StrategyListPanel extends JPanel {

    private final StrategyTableModel tableModel;
    private final JTable table;
    private final JLabel emptyLabel;

    public StrategyListPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Active Strategies"));

        tableModel = new StrategyTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(200); // Name
        table.getColumnModel().getColumn(1).setPreferredWidth(50);  // Version
        table.getColumnModel().getColumn(2).setPreferredWidth(80);  // Symbol
        table.getColumnModel().getColumn(3).setPreferredWidth(50);  // TF

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(400, 150));
        add(scrollPane, BorderLayout.CENTER);

        // Empty state label
        emptyLabel = new JLabel("No strategies published. Publish from Forge to start.");
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setVisible(false);
        add(emptyLabel, BorderLayout.SOUTH);
    }

    /**
     * Update the strategy list.
     */
    public void setStrategies(List<PublishedStrategy> strategies) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setStrategies(strategies);
            emptyLabel.setVisible(strategies.isEmpty());
            table.setVisible(!strategies.isEmpty());
        });
    }

    /**
     * Table model for strategies.
     */
    private static class StrategyTableModel extends AbstractTableModel {
        private final String[] columns = {"Strategy", "Ver", "Symbol", "TF"};
        private List<PublishedStrategy> strategies = new ArrayList<>();

        public void setStrategies(List<PublishedStrategy> strategies) {
            this.strategies = new ArrayList<>(strategies);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return strategies.size();
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
            PublishedStrategy s = strategies.get(row);
            return switch (col) {
                case 0 -> s.getName();
                case 1 -> "v" + s.getVersion();
                case 2 -> s.getSymbol();
                case 3 -> s.getTimeframe();
                default -> "";
            };
        }
    }
}
