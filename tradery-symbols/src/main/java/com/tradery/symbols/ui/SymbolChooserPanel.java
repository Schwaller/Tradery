package com.tradery.symbols.ui;

import com.tradery.symbols.model.SymbolEntry;
import com.tradery.symbols.service.SymbolService;
import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel with Exchange → Market → Search filter flow and a results table.
 * Fires a selection callback on double-click or Enter.
 */
public class SymbolChooserPanel extends JPanel {

    private static final int SEARCH_LIMIT = 500;
    private static final int DEBOUNCE_MS = 300;

    private final SymbolService service;
    private final JComboBox<String> exchangeCombo;
    private final JComboBox<String> marketCombo;
    private final JTextField searchField;
    private final JTable table;
    private final SymbolTableModel tableModel;
    private final SyncStatusPanel syncStatusPanel;

    private Timer debounceTimer;
    private Consumer<SymbolEntry> selectionCallback;

    public SymbolChooserPanel(SymbolService service) {
        this.service = service;
        setLayout(new BorderLayout(0, 4));

        // --- Step-by-step filter bar ---
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Row 1: Exchange and Market
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        row1.add(makeLabel("1. Exchange:"));
        exchangeCombo = new JComboBox<>();
        exchangeCombo.addItem("All Exchanges");
        for (String ex : service.getExchanges()) {
            exchangeCombo.addItem(ex);
        }
        exchangeCombo.addActionListener(e -> triggerSearch());
        row1.add(exchangeCombo);

        row1.add(Box.createHorizontalStrut(12));

        row1.add(makeLabel("2. Market:"));
        marketCombo = new JComboBox<>(new String[]{"All Markets", "spot", "perp"});
        marketCombo.addActionListener(e -> triggerSearch());
        row1.add(marketCombo);

        filterPanel.add(row1);

        // Row 2: Search
        JPanel row2 = new JPanel(new BorderLayout(6, 0));
        row2.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        row2.add(makeLabel("3. Search:"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Type coin name, symbol, or ticker...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
        });
        row2.add(searchField, BorderLayout.CENTER);

        filterPanel.add(row2);

        add(filterPanel, BorderLayout.NORTH);

        // --- Table ---
        tableModel = new SymbolTableModel();
        table = new BorderlessTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(150); // Symbol
        table.getColumnModel().getColumn(1).setPreferredWidth(80);  // Exchange
        table.getColumnModel().getColumn(2).setPreferredWidth(60);  // Market
        table.getColumnModel().getColumn(3).setPreferredWidth(70);  // Base
        table.getColumnModel().getColumn(4).setPreferredWidth(60);  // Quote

        // Dim exchange and market columns
        DefaultTableCellRenderer dimRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(1).setCellRenderer(dimRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(dimRenderer);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) fireSelection();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    fireSelection();
                    e.consume();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // --- Status bar ---
        JPanel southPanel = new JPanel(new BorderLayout(0, 0));
        southPanel.add(new JSeparator(), BorderLayout.NORTH);
        syncStatusPanel = new SyncStatusPanel(service);
        southPanel.add(syncStatusPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // Initial load
        triggerSearch();

        // Auto-focus search field when panel is shown
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(searchField::requestFocusInWindow);
            }
        });
    }

    public void setSelectionCallback(Consumer<SymbolEntry> callback) {
        this.selectionCallback = callback;
    }

    public SymbolEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        if (row >= 0) return tableModel.getEntryAt(row);
        return null;
    }

    private JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private void debouncedSearch() {
        if (debounceTimer != null) debounceTimer.stop();
        debounceTimer = new Timer(DEBOUNCE_MS, e -> triggerSearch());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void triggerSearch() {
        String query = searchField.getText().trim();
        String exchange = exchangeCombo.getSelectedIndex() == 0 ? null : (String) exchangeCombo.getSelectedItem();
        String market = marketCombo.getSelectedIndex() == 0 ? null : (String) marketCombo.getSelectedItem();

        // Run query off EDT
        new SwingWorker<List<SymbolEntry>, Void>() {
            @Override
            protected List<SymbolEntry> doInBackground() {
                return service.search(query.isEmpty() ? null : query, exchange, market, SEARCH_LIMIT);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setEntries(get());
                } catch (Exception ex) {
                    tableModel.setEntries(List.of());
                }
            }
        }.execute();
    }

    private void fireSelection() {
        SymbolEntry entry = getSelectedEntry();
        if (entry != null && selectionCallback != null) {
            selectionCallback.accept(entry);
        }
    }

    // --- Table Model ---

    private static class SymbolTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Symbol", "Exchange", "Market", "Base", "Quote"};
        private List<SymbolEntry> entries = new ArrayList<>();

        void setEntries(List<SymbolEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        SymbolEntry getEntryAt(int row) {
            return entries.get(row);
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            SymbolEntry e = entries.get(row);
            return switch (col) {
                case 0 -> e.symbol();
                case 1 -> e.exchange();
                case 2 -> e.marketType();
                case 3 -> e.base();
                case 4 -> e.quote();
                default -> "";
            };
        }
    }
}
