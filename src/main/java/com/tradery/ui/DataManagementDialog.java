package com.tradery.ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Dialog for managing cached OHLC data.
 * Shows disk space usage per symbol/timeframe and allows deletion.
 */
public class DataManagementDialog extends JDialog {

    private final File dataDir;
    private final List<DataEntry> entries = new ArrayList<>();
    private final JTable dataTable;
    private final JLabel totalSizeLabel;
    private final DataTableModel tableModel;

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");

    public DataManagementDialog(Frame owner) {
        super(owner, "Data Management", true);
        this.dataDir = new File(System.getProperty("user.home") + "/.tradery/data");

        setSize(600, 450);
        setLocationRelativeTo(owner);

        // Load data entries
        loadDataEntries();

        // Create table
        tableModel = new DataTableModel();
        dataTable = new JTable(tableModel);
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dataTable.setRowHeight(24);

        // Set column widths
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Symbol
        dataTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Timeframe
        dataTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Size
        dataTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Files
        dataTable.getColumnModel().getColumn(4).setPreferredWidth(150); // Date Range

        // Right-align size column
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        dataTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        dataTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);

        JScrollPane tableScroll = new JScrollPane(dataTable);

        // Total size label
        totalSizeLabel = new JLabel();
        updateTotalSize();

        // Buttons
        JButton deleteBtn = new JButton("Delete Selected");
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton deleteAllBtn = new JButton("Delete All");
        deleteAllBtn.addActionListener(e -> deleteAll());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(deleteBtn);
        buttonPanel.add(deleteAllBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(closeBtn);

        // Info panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        infoPanel.add(totalSizeLabel, BorderLayout.WEST);
        infoPanel.add(new JLabel("Select rows and click Delete to remove cached data"), BorderLayout.EAST);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        mainPanel.add(new JLabel("Cached OHLC Data:"), BorderLayout.NORTH);
        mainPanel.add(tableScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(infoPanel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void loadDataEntries() {
        entries.clear();

        if (!dataDir.exists()) return;

        File[] symbolDirs = dataDir.listFiles(File::isDirectory);
        if (symbolDirs == null) return;

        for (File symbolDir : symbolDirs) {
            String symbol = symbolDir.getName();
            File[] timeframeDirs = symbolDir.listFiles(File::isDirectory);
            if (timeframeDirs == null) continue;

            for (File tfDir : timeframeDirs) {
                String timeframe = tfDir.getName();
                DataEntry entry = new DataEntry();
                entry.symbol = symbol;
                entry.timeframe = timeframe;
                entry.directory = tfDir;

                // Calculate size and count files
                File[] files = tfDir.listFiles((dir, name) -> name.endsWith(".csv"));
                if (files != null) {
                    entry.fileCount = files.length;
                    long totalBytes = 0;
                    String minDate = null;
                    String maxDate = null;

                    for (File f : files) {
                        totalBytes += f.length();
                        String name = f.getName().replace(".csv", "");
                        if (minDate == null || name.compareTo(minDate) < 0) minDate = name;
                        if (maxDate == null || name.compareTo(maxDate) > 0) maxDate = name;
                    }

                    entry.sizeBytes = totalBytes;
                    entry.dateRange = (minDate != null && maxDate != null)
                        ? minDate + " to " + maxDate
                        : "-";
                }

                entries.add(entry);
            }
        }

        // Sort by symbol, then timeframe
        entries.sort(Comparator.comparing((DataEntry e) -> e.symbol)
            .thenComparing(e -> e.timeframe));
    }

    private void updateTotalSize() {
        long total = entries.stream().mapToLong(e -> e.sizeBytes).sum();
        totalSizeLabel.setText("Total: " + formatSize(total));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        if (bytes < 1024 * 1024 * 1024) return SIZE_FORMAT.format(bytes / (1024.0 * 1024)) + " MB";
        return SIZE_FORMAT.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    private void deleteSelected() {
        int[] rows = dataTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select data to delete.");
            return;
        }

        StringBuilder sb = new StringBuilder("Delete cached data for:\n\n");
        long totalSize = 0;
        for (int row : rows) {
            DataEntry entry = entries.get(row);
            sb.append("  • ").append(entry.symbol).append(" / ").append(entry.timeframe)
              .append(" (").append(formatSize(entry.sizeBytes)).append(")\n");
            totalSize += entry.sizeBytes;
        }
        sb.append("\nTotal: ").append(formatSize(totalSize));

        int confirm = JOptionPane.showConfirmDialog(this,
            sb.toString(),
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Delete in reverse order to maintain indices
            List<DataEntry> toDelete = new ArrayList<>();
            for (int row : rows) {
                toDelete.add(entries.get(row));
            }

            for (DataEntry entry : toDelete) {
                deleteDirectory(entry.directory);
                // Also delete parent if empty
                File parent = entry.directory.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    String[] remaining = parent.list();
                    if (remaining != null && remaining.length == 0) {
                        parent.delete();
                    }
                }
            }

            refresh();
            JOptionPane.showMessageDialog(this,
                "Deleted " + toDelete.size() + " cached data folder(s).",
                "Deleted", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteAll() {
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No cached data to delete.");
            return;
        }

        long totalSize = entries.stream().mapToLong(e -> e.sizeBytes).sum();

        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete ALL cached OHLC data?\n\n" +
            "This will remove " + entries.size() + " folder(s) totaling " + formatSize(totalSize) + ".\n" +
            "Data will be re-downloaded from Binance when needed.",
            "Confirm Delete All",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            deleteDirectory(dataDir);
            dataDir.mkdirs();
            refresh();
            JOptionPane.showMessageDialog(this,
                "All cached data deleted.",
                "Deleted", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        dir.delete();
    }

    private void refresh() {
        loadDataEntries();
        tableModel.fireTableDataChanged();
        updateTotalSize();
    }

    private class DataTableModel extends AbstractTableModel {
        private final String[] columns = {"Symbol", "Timeframe", "Size", "Files", "Date Range"};

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
            DataEntry entry = entries.get(row);
            return switch (column) {
                case 0 -> entry.symbol;
                case 1 -> entry.timeframe;
                case 2 -> formatSize(entry.sizeBytes);
                case 3 -> entry.fileCount;
                case 4 -> entry.dateRange;
                default -> "";
            };
        }
    }

    private static class DataEntry {
        String symbol;
        String timeframe;
        File directory;
        long sizeBytes;
        int fileCount;
        String dateRange;
    }

    /**
     * Show the data management dialog
     */
    public static void show(Frame owner) {
        DataManagementDialog dialog = new DataManagementDialog(owner);
        dialog.setVisible(true);
    }
}
