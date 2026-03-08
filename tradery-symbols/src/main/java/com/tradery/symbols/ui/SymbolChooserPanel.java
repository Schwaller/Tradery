package com.tradery.symbols.ui;

import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.symbols.model.SymbolEntry;
import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.service.SymbolService.MatrixEntry;
import com.tradery.ui.controls.BorderlessTable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exchange × Coin matrix panel.
 * Rows = coins (base assets), columns = exchange/market combos.
 * Each cell shows all available quote currencies; tooltip reveals the actual pair ID.
 */
public class SymbolChooserPanel extends JPanel {

    private static final int MATRIX_LIMIT = 10000;
    private static final int DEBOUNCE_MS = 300;
    private static final String ALL_QUOTES = "All";
    private static final List<String> PREFERRED_QUOTES = List.of("USDT", "USD", "BUSD", "BTC", "ETH", "EUR");

    private final SymbolService service;
    private final JTextField searchField;
    private final JComboBox<String> quoteCombo;
    private final JTable table;
    private final MatrixTableModel tableModel;
    private final SyncStatusPanel syncStatusPanel;

    private javax.swing.Timer debounceTimer;
    private Consumer<SymbolEntry> selectionCallback;

    public SymbolChooserPanel(SymbolService service) {
        this.service = service;
        setLayout(new BorderLayout(0, 4));

        // --- Filter bar ---
        JPanel filterPanel = new JPanel(new BorderLayout(12, 0));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Search field (left, stretches)
        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 11f));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Type coin name, symbol, or ticker...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { debouncedSearch(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        filterPanel.add(searchPanel, BorderLayout.CENTER);

        // Quote filter combo (right)
        JPanel quotePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        quotePanel.setOpaque(false);
        JLabel quoteLabel = new JLabel("Quote:");
        quoteLabel.setFont(quoteLabel.getFont().deriveFont(Font.BOLD, 11f));
        quotePanel.add(quoteLabel);
        quoteCombo = new JComboBox<>();
        populateQuotes();
        quoteCombo.addActionListener(e -> triggerSearch());
        quotePanel.add(quoteCombo);
        filterPanel.add(quotePanel, BorderLayout.EAST);

        add(filterPanel, BorderLayout.NORTH);

        // --- Matrix table ---
        tableModel = new MatrixTableModel();
        table = new BorderlessTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row >= 0 && col >= MatrixTableModel.FIXED_COLS) {
                    return tableModel.getTooltip(row, col);
                }
                return null;
            }
        };
        // Need to register so tooltips work
        ToolTipManager.sharedInstance().registerComponent(table);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setCellSelectionEnabled(true);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setDefaultRenderer(Object.class, new MatrixCellRenderer());

        // Click-to-sort on fixed column headers (Coin, Rank, Cap)
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            private int lastSortCol = -1;
            private boolean ascending = true;

            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0 && col < MatrixTableModel.FIXED_COLS) {
                    if (col == lastSortCol) {
                        ascending = !ascending;
                    } else {
                        lastSortCol = col;
                        ascending = true;
                    }
                    tableModel.sortBy(col, ascending);
                }
            }
        });

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

        JScrollPane scrollPane = new JScrollPane(table,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // --- Status bar ---
        syncStatusPanel = new SyncStatusPanel(service);
        add(syncStatusPanel, BorderLayout.SOUTH);

        // Initial load
        triggerSearch();

        // Auto-focus search
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
        int col = table.getSelectedColumn();
        if (row >= 0 && col >= MatrixTableModel.FIXED_COLS) {
            String quoteFilter = getSelectedQuote();
            return tableModel.getEntryAt(row, col, quoteFilter);
        }
        return null;
    }

    private String getSelectedQuote() {
        String sel = (String) quoteCombo.getSelectedItem();
        return ALL_QUOTES.equals(sel) ? null : sel;
    }

    private void populateQuotes() {
        quoteCombo.addItem(ALL_QUOTES);
        List<String> allQuotes = service.getAllQuoteCurrencies();
        // Preferred quotes first
        for (String pq : PREFERRED_QUOTES) {
            if (allQuotes.contains(pq)) quoteCombo.addItem(pq);
        }
        for (String q : allQuotes) {
            if (!PREFERRED_QUOTES.contains(q)) quoteCombo.addItem(q);
        }
        // Default to All
        quoteCombo.setSelectedItem(ALL_QUOTES);
    }

    private void debouncedSearch() {
        if (debounceTimer != null) debounceTimer.stop();
        debounceTimer = new javax.swing.Timer(DEBOUNCE_MS, e -> triggerSearch());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void triggerSearch() {
        String query = searchField.getText().trim();
        String quote = getSelectedQuote(); // null = all

        new SwingWorker<List<MatrixEntry>, Void>() {
            @Override
            protected List<MatrixEntry> doInBackground() {
                return service.getMatrix(quote, query.isEmpty() ? null : query, MATRIX_LIMIT);
            }

            @Override
            protected void done() {
                try {
                    tableModel.setData(get());
                    updateColumnWidths();
                } catch (Exception ex) {
                    tableModel.setData(List.of());
                }
            }
        }.execute();
    }

    private void updateColumnWidths() {
        if (tableModel.getColumnCount() == 0) return;
        // Coin column
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(0).setMinWidth(120);
        // Rank column
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setMinWidth(40);
        table.getColumnModel().getColumn(1).setMaxWidth(60);
        // Cap column
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setMinWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        // Exchange columns
        for (int i = MatrixTableModel.FIXED_COLS; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(120);
            table.getColumnModel().getColumn(i).setMinWidth(80);
        }
    }

    private void fireSelection() {
        SymbolEntry entry = getSelectedEntry();
        if (entry != null && selectionCallback != null) {
            selectionCallback.accept(entry);
        }
    }

    // --- Matrix Table Model ---

    private static class MatrixTableModel extends AbstractTableModel {

        private List<ExchangeMarket> columns = new ArrayList<>();
        private List<CoinRow> rows = new ArrayList<>();

        record ExchangeMarket(String exchange, String marketType) {
            String displayName() {
                return formatExchange(exchange) + " " + formatMarket(marketType);
            }
        }

        /** One quote→symbol mapping within a cell. */
        record QuotePair(String quote, String symbol) {}

        /** A cell: all available quote/symbol pairs for one coin on one exchange/market. */
        record CellData(List<QuotePair> pairs) {
            String displayText() {
                if (pairs.isEmpty()) return "";
                StringJoiner sj = new StringJoiner("  ");
                for (QuotePair qp : pairs) sj.add(qp.quote);
                return sj.toString();
            }

            String tooltipText() {
                if (pairs.isEmpty()) return null;
                StringBuilder sb = new StringBuilder("<html>");
                for (int i = 0; i < pairs.size(); i++) {
                    QuotePair qp = pairs.get(i);
                    if (i > 0) sb.append("<br>");
                    sb.append("<b>").append(qp.quote).append("</b> → ").append(qp.symbol);
                }
                sb.append("</html>");
                return sb.toString();
            }

            /** Pick best quote pair given a filter (null = pick preferred). */
            QuotePair bestPair(String quoteFilter) {
                if (pairs.isEmpty()) return null;
                if (quoteFilter != null) {
                    for (QuotePair qp : pairs) {
                        if (qp.quote.equals(quoteFilter)) return qp;
                    }
                    return null;
                }
                // No filter — pick first from preferred order
                for (String pref : List.of("USDT", "USD", "BUSD", "BTC", "ETH")) {
                    for (QuotePair qp : pairs) {
                        if (qp.quote.equals(pref)) return qp;
                    }
                }
                return pairs.get(0);
            }
        }

        static class CoinRow {
            final Set<String> bases = new LinkedHashSet<>();
            final String coinName;
            final String coingeckoId;
            final double marketCapUsd;
            final int marketCapRank;
            final Map<ExchangeMarket, CellData> cells = new HashMap<>();

            CoinRow(String base, String coinName, String coingeckoId,
                    double marketCapUsd, int marketCapRank) {
                this.bases.add(base);
                this.coinName = coinName;
                this.coingeckoId = coingeckoId;
                this.marketCapUsd = marketCapUsd;
                this.marketCapRank = marketCapRank;
            }

            String displayName() {
                String baseStr = String.join("/", bases);
                if (coinName != null && !coinName.isEmpty()) {
                    return coinName + " (" + baseStr + ")";
                }
                return baseStr;
            }

            String rankDisplay() {
                if (marketCapRank <= 0) return "";
                return "#" + marketCapRank;
            }

            String capDisplay() {
                return formatMarketCap(marketCapUsd);
            }

            private static String formatMarketCap(double usd) {
                if (usd <= 0) return "";
                if (usd >= 1_000_000_000_000L) return String.format("$%.1fT", usd / 1_000_000_000_000.0);
                if (usd >= 1_000_000_000L) return String.format("$%.1fB", usd / 1_000_000_000.0);
                if (usd >= 1_000_000L) return String.format("$%.1fM", usd / 1_000_000.0);
                if (usd >= 1_000) return String.format("$%.0fK", usd / 1_000.0);
                return String.format("$%.0f", usd);
            }
        }

        void setData(List<MatrixEntry> entries) {
            // Discover columns
            LinkedHashSet<ExchangeMarket> colSet = new LinkedHashSet<>();
            for (MatrixEntry e : entries) {
                colSet.add(new ExchangeMarket(e.exchange(), e.marketType()));
            }
            columns = new ArrayList<>(colSet);

            // Group by coingecko_id (merges BTC/XBT etc.), fall back to base_symbol
            LinkedHashMap<String, CoinRow> rowMap = new LinkedHashMap<>();
            for (MatrixEntry e : entries) {
                String groupKey = (e.coingeckoId() != null && !e.coingeckoId().isEmpty())
                    ? "cg:" + e.coingeckoId()
                    : "base:" + e.base();

                CoinRow row = rowMap.computeIfAbsent(groupKey,
                    k -> new CoinRow(e.base(), e.coinName(), e.coingeckoId(),
                        e.marketCapUsd(), e.marketCapRank()));
                row.bases.add(e.base()); // collect aliases (BTC, XBT)

                ExchangeMarket em = new ExchangeMarket(e.exchange(), e.marketType());
                CellData cell = row.cells.computeIfAbsent(em, k -> new CellData(new ArrayList<>()));
                cell.pairs().add(new QuotePair(e.quote(), e.symbol()));
            }
            // Sort by market cap rank (ranked first, unranked last, then alphabetically)
            rows = new ArrayList<>(rowMap.values());
            rows.sort((a, b) -> {
                if (a.marketCapRank > 0 && b.marketCapRank > 0) return Integer.compare(a.marketCapRank, b.marketCapRank);
                if (a.marketCapRank > 0) return -1;
                if (b.marketCapRank > 0) return 1;
                return a.bases.iterator().next().compareToIgnoreCase(b.bases.iterator().next());
            });

            fireTableStructureChanged();
        }

        /** Get entry for selection. quoteFilter=null means pick best available. */
        SymbolEntry getEntryAt(int row, int col, String quoteFilter) {
            if (row < 0 || row >= rows.size() || col < FIXED_COLS || col >= FIXED_COLS + columns.size()) return null;
            CoinRow coinRow = rows.get(row);
            ExchangeMarket em = columns.get(col - FIXED_COLS);
            CellData cell = coinRow.cells.get(em);
            if (cell == null) return null;

            QuotePair qp = cell.bestPair(quoteFilter);
            if (qp == null) return null;

            return new SymbolEntry(qp.symbol, em.exchange, em.marketType,
                coinRow.bases.iterator().next(), qp.quote, coinRow.coingeckoId, List.of());
        }

        String getTooltip(int row, int col) {
            if (row < 0 || row >= rows.size() || col < FIXED_COLS || col >= FIXED_COLS + columns.size()) return null;
            CoinRow coinRow = rows.get(row);
            ExchangeMarket em = columns.get(col - FIXED_COLS);
            CellData cell = coinRow.cells.get(em);
            return cell != null ? cell.tooltipText() : null;
        }

        boolean hasData(int row, int col) {
            if (row < 0 || row >= rows.size() || col < FIXED_COLS || col >= FIXED_COLS + columns.size()) return false;
            CoinRow coinRow = rows.get(row);
            ExchangeMarket em = columns.get(col - FIXED_COLS);
            CellData cell = coinRow.cells.get(em);
            return cell != null && !cell.pairs().isEmpty();
        }

        /** Number of fixed columns before exchange data columns. */
        static final int FIXED_COLS = 3; // Coin, Rank, Cap

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return FIXED_COLS + columns.size(); }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case 0 -> "Coin";
                case 1 -> "Rank";
                case 2 -> "Cap";
                default -> columns.get(col - FIXED_COLS).displayName();
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            CoinRow coinRow = rows.get(row);
            return switch (col) {
                case 0 -> coinRow.displayName();
                case 1 -> coinRow.rankDisplay();
                case 2 -> coinRow.capDisplay();
                default -> {
                    ExchangeMarket em = columns.get(col - FIXED_COLS);
                    CellData cell = coinRow.cells.get(em);
                    yield cell != null ? cell.displayText() : "";
                }
            };
        }

        /** Sort rows by a fixed column. */
        void sortBy(int col, boolean ascending) {
            rows.sort((a, b) -> {
                int cmp = switch (col) {
                    case 0 -> a.displayName().compareToIgnoreCase(b.displayName());
                    case 1, 2 -> {
                        // Sort by rank — 0 means unranked, push to end
                        if (a.marketCapRank > 0 && b.marketCapRank > 0)
                            yield Integer.compare(a.marketCapRank, b.marketCapRank);
                        if (a.marketCapRank > 0) yield -1;
                        if (b.marketCapRank > 0) yield 1;
                        yield a.displayName().compareToIgnoreCase(b.displayName());
                    }
                    default -> 0;
                };
                return ascending ? cmp : -cmp;
            });
            fireTableDataChanged();
        }
    }

    // --- Cell Renderer ---

    private static class MatrixCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String text = value != null ? value.toString() : "";

            if (col == 0) {
                // Coin name
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                if (!isSelected) c.setForeground(UIManager.getColor("Table.foreground"));
                setHorizontalAlignment(LEFT);
            } else if (col == 1) {
                // Rank
                c.setFont(c.getFont().deriveFont(Font.PLAIN, 11f));
                if (text.isEmpty()) {
                    if (!isSelected) c.setForeground(UIManager.getColor("Label.disabledForeground"));
                } else {
                    if (!isSelected) c.setForeground(UIManager.getColor("Table.foreground"));
                }
                setHorizontalAlignment(RIGHT);
            } else if (col == 2) {
                // Cap
                c.setFont(c.getFont().deriveFont(Font.PLAIN, 11f));
                if (text.isEmpty()) {
                    if (!isSelected) c.setForeground(UIManager.getColor("Label.disabledForeground"));
                } else {
                    if (!isSelected) c.setForeground(UIManager.getColor("Table.foreground"));
                }
                setHorizontalAlignment(RIGHT);
            } else {
                // Exchange cells
                if (text.isEmpty()) {
                    setText("\u2014");
                    if (!isSelected) c.setForeground(UIManager.getColor("Label.disabledForeground"));
                } else {
                    c.setFont(c.getFont().deriveFont(Font.PLAIN, 11f));
                    if (!isSelected) c.setForeground(UIManager.getColor("Table.foreground"));
                }
                setHorizontalAlignment(CENTER);
            }

            return c;
        }
    }

    // --- Display name helpers ---

    private static String formatExchange(String configKey) {
        return Exchange.formatDisplayName(configKey);
    }

    private static String formatMarket(String configKey) {
        DataMarketType mt = DataMarketType.fromConfigKey(configKey);
        return mt != null ? mt.getDisplayName() : configKey;
    }
}
