package com.tradery.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradery.model.Strategy;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for browsing strategy history and restoring previous versions.
 */
public class HistoryDialog extends JDialog {

    private final File historyDir;
    private final ObjectMapper mapper;
    private final List<HistoryEntry> entries = new ArrayList<>();
    private final JTable historyTable;
    private final JTextArea detailsArea;
    private final Consumer<Strategy> onRestore;

    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    public HistoryDialog(Frame owner, File historyDir, Consumer<Strategy> onRestore) {
        super(owner, "Strategy History", true);
        this.historyDir = historyDir;
        this.onRestore = onRestore;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());

        setSize(900, 600);
        setLocationRelativeTo(owner);

        // Load history entries
        loadHistory();

        // Create table
        HistoryTableModel tableModel = new HistoryTableModel();
        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setRowHeight(24);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails();
            }
        });

        // Set column widths
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(140); // Date
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(70);  // Return
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Win Rate
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(50);  // Trades
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(70);  // Profit Factor
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(70);  // Max DD
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Entry (truncated)

        // Color code return column
        historyTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof String s && !isSelected) {
                    try {
                        double val = Double.parseDouble(s.replace("%", ""));
                        c.setForeground(val >= 0 ? new Color(0, 150, 0) : new Color(200, 0, 0));
                    } catch (NumberFormatException ignored) {}
                }
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(historyTable);
        tableScroll.setPreferredSize(new Dimension(900, 250));

        // Details area
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setPreferredSize(new Dimension(900, 250));

        // Buttons
        JButton restoreBtn = new JButton("Restore This Version");
        restoreBtn.addActionListener(e -> restoreSelected());

        JButton compareBtn = new JButton("Compare with Current");
        compareBtn.addActionListener(e -> compareWithCurrent());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(compareBtn);
        buttonPanel.add(restoreBtn);
        buttonPanel.add(closeBtn);

        // Layout
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailsScroll);
        splitPane.setDividerLocation(280);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        mainPanel.add(new JLabel("Select a version to view details or restore:"), BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Select first row
        if (!entries.isEmpty()) {
            historyTable.setRowSelectionInterval(0, 0);
        }
    }

    private void loadHistory() {
        entries.clear();
        if (historyDir == null || !historyDir.exists()) return;

        File[] files = historyDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        // Sort by date descending (newest first)
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());

        for (File file : files) {
            try {
                JsonNode root = mapper.readTree(file);
                HistoryEntry entry = new HistoryEntry();
                entry.file = file;

                // Parse timestamp from filename
                String name = file.getName().replace(".json", "");
                try {
                    entry.timestamp = LocalDateTime.parse(name, FILE_FORMAT);
                } catch (Exception e) {
                    entry.timestamp = LocalDateTime.now();
                }

                // Extract metrics
                JsonNode metrics = root.get("metrics");
                if (metrics != null) {
                    entry.totalReturn = metrics.has("totalReturnPercent") ? metrics.get("totalReturnPercent").asDouble() : 0;
                    entry.winRate = metrics.has("winRate") ? metrics.get("winRate").asDouble() : 0;
                    entry.totalTrades = metrics.has("totalTrades") ? metrics.get("totalTrades").asInt() : 0;
                    entry.profitFactor = metrics.has("profitFactor") ? metrics.get("profitFactor").asDouble() : 0;
                    entry.maxDrawdown = metrics.has("maxDrawdownPercent") ? metrics.get("maxDrawdownPercent").asDouble() : 0;
                }

                // Extract entry condition
                JsonNode strategy = root.get("strategy");
                if (strategy != null) {
                    entry.hasStrategy = true;
                    JsonNode entryCondition = strategy.get("entryCondition");
                    if (entryCondition != null && entryCondition.has("condition")) {
                        entry.entryCondition = entryCondition.get("condition").asText();
                    } else if (strategy.has("entry")) {
                        entry.entryCondition = strategy.get("entry").asText();
                    }
                }

                // Store raw JSON for details view
                entry.rawJson = root;

                entries.add(entry);
            } catch (IOException e) {
                System.err.println("Failed to parse history file: " + file.getName());
            }
        }
    }

    private void showDetails() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= entries.size()) {
            detailsArea.setText("");
            return;
        }

        HistoryEntry entry = entries.get(row);
        StringBuilder sb = new StringBuilder();

        sb.append("=== ").append(entry.timestamp.format(DISPLAY_FORMAT)).append(" ===\n\n");

        // Show strategy details if available
        if (entry.hasStrategy && entry.rawJson != null) {
            JsonNode strategy = entry.rawJson.get("strategy");
            if (strategy != null) {
                sb.append("ENTRY CONDITION:\n");
                JsonNode entryCondition = strategy.get("entryCondition");
                if (entryCondition != null) {
                    sb.append("  ").append(entryCondition.get("condition").asText()).append("\n\n");
                }

                sb.append("EXIT ZONES:\n");
                JsonNode exitZones = strategy.get("exitZones");
                if (exitZones != null && exitZones.isArray()) {
                    for (JsonNode zone : exitZones) {
                        sb.append("  [").append(zone.get("name").asText()).append("]\n");
                        if (zone.has("exitCondition") && !zone.get("exitCondition").asText().isEmpty()) {
                            sb.append("    Exit: ").append(zone.get("exitCondition").asText()).append("\n");
                        }
                        if (zone.has("stopLossType") && !"none".equals(zone.get("stopLossType").asText())) {
                            sb.append("    SL: ").append(zone.get("stopLossType").asText())
                              .append(" @ ").append(zone.get("stopLossValue").asDouble()).append("\n");
                        }
                        if (zone.has("takeProfitType") && !"none".equals(zone.get("takeProfitType").asText())) {
                            sb.append("    TP: ").append(zone.get("takeProfitType").asText())
                              .append(" @ ").append(zone.get("takeProfitValue").asDouble()).append("\n");
                        }
                        if (zone.has("minBarsBeforeExit") && zone.get("minBarsBeforeExit").asInt() > 0) {
                            sb.append("    Min bars: ").append(zone.get("minBarsBeforeExit").asInt()).append("\n");
                        }
                    }
                }
                sb.append("\n");

                sb.append("SETTINGS:\n");
                sb.append("  Max Open Trades: ").append(strategy.has("maxOpenTrades") ? strategy.get("maxOpenTrades").asInt() : 1).append("\n");
                sb.append("  Min Candles Between: ").append(strategy.has("minCandlesBetweenTrades") ? strategy.get("minCandlesBetweenTrades").asInt() : 0).append("\n");

                JsonNode backtest = strategy.get("backtestSettings");
                if (backtest != null) {
                    sb.append("  Symbol: ").append(backtest.get("symbol").asText()).append("\n");
                    sb.append("  Timeframe: ").append(backtest.get("timeframe").asText()).append("\n");
                    sb.append("  Duration: ").append(backtest.get("duration").asText()).append("\n");
                }
            }
        }

        sb.append("\nMETRICS:\n");
        sb.append(String.format("  Total Return: %.2f%%\n", entry.totalReturn));
        sb.append(String.format("  Win Rate: %.1f%%\n", entry.winRate));
        sb.append(String.format("  Total Trades: %d\n", entry.totalTrades));
        sb.append(String.format("  Profit Factor: %.2f\n", entry.profitFactor));
        sb.append(String.format("  Max Drawdown: %.2f%%\n", entry.maxDrawdown));

        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }

    private void restoreSelected() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= entries.size()) {
            JOptionPane.showMessageDialog(this, "Please select a version to restore.");
            return;
        }

        HistoryEntry entry = entries.get(row);
        if (!entry.hasStrategy || entry.rawJson == null) {
            JOptionPane.showMessageDialog(this,
                "This history entry doesn't contain the full strategy definition.\n" +
                "Only recent backtest results include the strategy.",
                "Cannot Restore", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Restore strategy to version from " + entry.timestamp.format(DISPLAY_FORMAT) + "?\n" +
            "This will overwrite your current settings.",
            "Confirm Restore",
            JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                JsonNode strategyNode = entry.rawJson.get("strategy");
                Strategy strategy = mapper.treeToValue(strategyNode, Strategy.class);
                onRestore.accept(strategy);
                dispose();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to restore strategy: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void compareWithCurrent() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= entries.size()) {
            JOptionPane.showMessageDialog(this, "Please select a version to compare.");
            return;
        }

        // For now, just show a message - could be enhanced with a diff view
        JOptionPane.showMessageDialog(this,
            "Compare feature coming soon!\n\nFor now, review the details panel to see the historical settings.",
            "Compare", JOptionPane.INFORMATION_MESSAGE);
    }

    private class HistoryTableModel extends AbstractTableModel {
        private final String[] columns = {"Date", "Return", "Win Rate", "Trades", "PF", "Max DD", "Entry Condition"};

        @Override
        public int getRowCount() {
            return entries.size();
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
        public Object getValueAt(int row, int column) {
            HistoryEntry entry = entries.get(row);
            return switch (column) {
                case 0 -> entry.timestamp.format(DISPLAY_FORMAT);
                case 1 -> String.format("%.2f%%", entry.totalReturn);
                case 2 -> String.format("%.1f%%", entry.winRate);
                case 3 -> entry.totalTrades;
                case 4 -> String.format("%.2f", entry.profitFactor);
                case 5 -> String.format("%.2f%%", entry.maxDrawdown);
                case 6 -> truncate(entry.entryCondition, 40);
                default -> "";
            };
        }

        private String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max) + "..." : s;
        }
    }

    private static class HistoryEntry {
        File file;
        LocalDateTime timestamp;
        double totalReturn;
        double winRate;
        int totalTrades;
        double profitFactor;
        double maxDrawdown;
        String entryCondition;
        boolean hasStrategy;
        JsonNode rawJson;
    }

    /**
     * Show the history dialog for a strategy
     */
    public static void show(Frame owner, File strategyDir, Consumer<Strategy> onRestore) {
        File historyDir = new File(strategyDir, "history");
        if (!historyDir.exists() || historyDir.listFiles() == null || historyDir.listFiles().length == 0) {
            JOptionPane.showMessageDialog(owner,
                "No history available for this strategy yet.\n" +
                "History is saved each time you run a backtest.",
                "No History", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        HistoryDialog dialog = new HistoryDialog(owner, historyDir, onRestore);
        dialog.setVisible(true);
    }
}
