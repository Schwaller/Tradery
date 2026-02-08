package com.tradery.trader.ui;

import com.tradery.exchange.model.AssetInfo;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Browse available assets with search/filter.
 */
public class AssetBrowserPanel extends JPanel {

    private final AssetTableModel tableModel = new AssetTableModel();
    private final JTable table;
    private final JTextField searchField = new JTextField();
    private final TableRowSorter<AssetTableModel> sorter;
    private Consumer<String> onAssetSelected;

    public AssetBrowserPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Search field
        searchField.putClientProperty("JTextField.placeholderText", "Search assets...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        add(searchField, BorderLayout.NORTH);

        // Table
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Double-click to select
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onAssetSelected != null) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        int modelRow = table.convertRowIndexToModel(row);
                        onAssetSelected.accept(tableModel.assets.get(modelRow).symbol());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    public void update(List<AssetInfo> assets) {
        SwingUtilities.invokeLater(() -> tableModel.setAssets(assets));
    }

    public void setOnAssetSelected(Consumer<String> handler) {
        this.onAssetSelected = handler;
    }

    private void filter() {
        String text = searchField.getText().trim().toUpperCase();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
        }
    }

    private static class AssetTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Max Lev", "Sz Dec"};
        private List<AssetInfo> assets = new ArrayList<>();

        void setAssets(List<AssetInfo> assets) {
            this.assets = new ArrayList<>(assets);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return assets.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            AssetInfo a = assets.get(row);
            return switch (col) {
                case 0 -> a.symbol();
                case 1 -> (int) a.maxLeverage() + "x";
                case 2 -> a.szDecimals();
                default -> "";
            };
        }
    }
}
