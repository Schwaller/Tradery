package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;
import com.tradery.data.DataIntegrityChecker;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for viewing and repairing data health.
 * Left side: tree-style navigation of symbol/timeframe
 * Right side: 2D block diagram of data completeness by month.
 */
public class DataHealthDialog extends JDialog {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");

    private final DataIntegrityChecker checker;
    private final CandleStore candleStore;
    private final File dataDir;
    private final File aggTradesDir;

    private DataBrowserPanel browserPanel;
    private DataHealthPanel healthPanel;
    private JLabel detailLabel;
    private JLabel storageLabel;
    private JButton repairButton;
    private JButton deleteButton;
    private JButton deleteSeriesButton;
    private JButton deleteAllButton;
    private JProgressBar progressBar;

    private String currentSymbol;
    private String currentResolution;
    private DataHealth selectedHealth;
    private Timer refreshTimer;

    public DataHealthDialog(Frame owner, CandleStore candleStore) {
        super(owner, "Data Health", true);
        this.candleStore = candleStore;
        this.checker = new DataIntegrityChecker();
        this.dataDir = new File(System.getProperty("user.home") + "/.tradery/data");
        this.aggTradesDir = new File(System.getProperty("user.home") + "/.tradery/data");

        initUI();

        setSize(800, 550);
        setLocationRelativeTo(owner);

        // Auto-refresh every 10 seconds to show progress while data is loading
        refreshTimer = new Timer(10000, e -> {
            browserPanel.refreshData();
            if (currentSymbol != null && currentResolution != null) {
                healthPanel.refreshKeepSelection();
            }
            updateStorageLabel();
        });
        refreshTimer.start();

        // Handle close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
                candleStore.cancelCurrentFetch();
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(40, 40, 45));


        // Left side: Data browser (tree-style navigation)
        browserPanel = new DataBrowserPanel();
        browserPanel.setOnSelectionChanged(this::onSeriesSelected);

        JScrollPane browserScroll = new JScrollPane(browserPanel);
        browserScroll.setBorder(BorderFactory.createEmptyBorder());
        browserScroll.setPreferredSize(new Dimension(220, 0));
        browserScroll.getViewport().setBackground(new Color(40, 40, 45));

        // Storage info at bottom of left panel
        storageLabel = new JLabel();
        storageLabel.setForeground(new Color(150, 150, 150));
        storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        storageLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        updateStorageLabel();

        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setBackground(new Color(40, 40, 45));
        leftPanel.add(browserScroll, BorderLayout.CENTER);
        leftPanel.add(storageLabel, BorderLayout.SOUTH);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        add(leftPanel, BorderLayout.WEST);

        // Right side: Health panel (block diagram)
        healthPanel = new DataHealthPanel(checker);
        healthPanel.setOnMonthSelected(this::onMonthSelected);

        JScrollPane healthScroll = new JScrollPane(healthPanel);
        healthScroll.setBorder(BorderFactory.createEmptyBorder());
        healthScroll.getViewport().setBackground(new Color(40, 40, 45));

        add(healthScroll, BorderLayout.CENTER);

        // Bottom panel with details and actions
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(40, 40, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        // Detail label
        detailLabel = new JLabel("Select a data series from the left");
        detailLabel.setForeground(new Color(150, 150, 150));
        panel.add(detailLabel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);

        JButton fetchButton = new JButton("Fetch New...");
        fetchButton.addActionListener(e -> showFetchDialog());
        buttonPanel.add(fetchButton);

        buttonPanel.add(Box.createHorizontalStrut(12));

        repairButton = new JButton("Repair Month");
        repairButton.setEnabled(false);
        repairButton.addActionListener(e -> repairSelectedMonth());
        buttonPanel.add(repairButton);

        deleteButton = new JButton("Delete Month");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedMonth());
        buttonPanel.add(deleteButton);

        deleteSeriesButton = new JButton("Delete Series");
        deleteSeriesButton.setEnabled(false);
        deleteSeriesButton.addActionListener(e -> deleteSelectedSeries());
        buttonPanel.add(deleteSeriesButton);

        deleteAllButton = new JButton("Delete All");
        deleteAllButton.addActionListener(e -> deleteAllData());
        buttonPanel.add(deleteAllButton);

        buttonPanel.add(Box.createHorizontalStrut(12));

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            candleStore.cancelCurrentFetch();
            dispose();
        });
        buttonPanel.add(closeButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        // Progress bar (hidden by default)
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        return panel;
    }

    private void showFetchDialog() {
        AggTradesStore aggTradesStore = ApplicationContext.getInstance().getAggTradesStore();
        FetchDataDialog.show((Frame) getOwner(), candleStore, aggTradesStore, () -> {
            // Refresh after fetch completes
            browserPanel.refreshData();
            if (currentSymbol != null && currentResolution != null) {
                healthPanel.refreshData();
            }
        });
    }

    private void onSeriesSelected(String symbol, String resolution) {
        currentSymbol = symbol;
        currentResolution = resolution;

        if (symbol == null || resolution == null) {
            healthPanel.setData(null, null);
            detailLabel.setText("Select a data series from the left");
            return;
        }

        // Handle aggTrades selection
        if ("aggTrades".equals(resolution)) {
            healthPanel.setAggTradesData(symbol);
            detailLabel.setText(getAggTradesInfo(symbol));
            selectedHealth = null;
            updateButtons();
            return;
        }

        healthPanel.setData(symbol, resolution);
        selectedHealth = null;
        updateDetailLabel();
        updateButtons();
    }

    private void onMonthSelected(DataHealth health) {
        selectedHealth = health;
        updateDetailLabel();
        updateButtons();
    }

    private void updateDetailLabel() {
        // Don't overwrite aggTrades info
        if ("aggTrades".equals(currentResolution)) {
            return;
        }

        if (currentSymbol == null || currentResolution == null) {
            detailLabel.setText("Select a data series from the left");
            return;
        }

        if (selectedHealth == null) {
            detailLabel.setText(currentSymbol + " / " + currentResolution + " - Click a month block to select");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(selectedHealth.month().format(MONTH_FORMAT));
        sb.append(" - ");
        sb.append(selectedHealth.status());
        sb.append(" (");
        sb.append(selectedHealth.actualCandles());
        sb.append("/");
        sb.append(selectedHealth.expectedCandles());
        sb.append(" candles, ");
        sb.append(String.format("%.1f%%", selectedHealth.completenessPercent()));
        sb.append(")");

        if (!selectedHealth.gaps().isEmpty()) {
            sb.append(" - ");
            sb.append(selectedHealth.gaps().size());
            sb.append(" gap(s)");
        }

        detailLabel.setText(sb.toString());
    }

    private void updateButtons() {
        boolean canRepair = selectedHealth != null &&
                (selectedHealth.status() == DataStatus.PARTIAL ||
                 selectedHealth.status() == DataStatus.MISSING);

        boolean canDeleteMonth = selectedHealth != null &&
                selectedHealth.status() != DataStatus.MISSING;

        boolean canDeleteSeries = currentSymbol != null && currentResolution != null;

        repairButton.setEnabled(canRepair);
        deleteButton.setEnabled(canDeleteMonth);
        deleteSeriesButton.setEnabled(canDeleteSeries);
    }

    private void repairSelectedMonth() {
        if (selectedHealth == null || currentSymbol == null || currentResolution == null) return;

        repairButton.setEnabled(false);
        deleteButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Repairing " + selectedHealth.month().format(MONTH_FORMAT) + "...");

        // Set up progress callback
        candleStore.setProgressCallback(progress -> {
            SwingUtilities.invokeLater(() -> {
                if (progress.estimatedTotal() > 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(progress.percentComplete());
                }
                progressBar.setString(progress.message());
            });
        });

        // Run repair in background
        String symbol = currentSymbol;
        String resolution = currentResolution;
        DataHealth health = selectedHealth;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                candleStore.repairMonth(symbol, resolution, health.month());
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                candleStore.setProgressCallback(null);

                try {
                    get();
                    JOptionPane.showMessageDialog(DataHealthDialog.this,
                            "Repair complete for " + health.month().format(MONTH_FORMAT),
                            "Repair Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DataHealthDialog.this,
                            "Repair failed: " + e.getMessage(),
                            "Repair Error", JOptionPane.ERROR_MESSAGE);
                }

                // Refresh both panels
                browserPanel.refreshData();
                healthPanel.refreshData();
                updateButtons();
            }
        };
        worker.execute();
    }

    private void deleteSelectedMonth() {
        if (selectedHealth == null || currentSymbol == null || currentResolution == null) return;

        int result = JOptionPane.showConfirmDialog(this,
                "Delete all data for " + selectedHealth.month().format(MONTH_FORMAT) + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            candleStore.deleteMonth(currentSymbol, currentResolution, selectedHealth.month());
            refreshAll();
        }
    }

    private void deleteSelectedSeries() {
        if (currentSymbol == null || currentResolution == null) return;

        File seriesDir = new File(dataDir, currentSymbol + "/" + currentResolution);
        long size = calculateDirectorySize(seriesDir);

        int result = JOptionPane.showConfirmDialog(this,
                "Delete all data for " + currentSymbol + " / " + currentResolution + "?\n\n" +
                "This will remove " + formatSize(size) + " of cached data.",
                "Confirm Delete Series", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            deleteDirectory(seriesDir);
            // Also delete parent symbol dir if empty
            File symbolDir = seriesDir.getParentFile();
            if (symbolDir != null && symbolDir.isDirectory()) {
                String[] remaining = symbolDir.list();
                if (remaining != null && remaining.length == 0) {
                    symbolDir.delete();
                }
            }
            currentSymbol = null;
            currentResolution = null;
            selectedHealth = null;
            refreshAll();
        }
    }

    private void deleteAllData() {
        if (!dataDir.exists()) {
            JOptionPane.showMessageDialog(this, "No cached data to delete.");
            return;
        }

        long totalSize = calculateDirectorySize(dataDir);
        if (totalSize == 0) {
            JOptionPane.showMessageDialog(this, "No cached data to delete.");
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Delete ALL cached OHLC data?\n\n" +
                "This will remove " + formatSize(totalSize) + " of data.\n" +
                "Data will be re-downloaded from Binance when needed.",
                "Confirm Delete All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            deleteDirectory(dataDir);
            dataDir.mkdirs();
            currentSymbol = null;
            currentResolution = null;
            selectedHealth = null;
            refreshAll();
            JOptionPane.showMessageDialog(this, "All cached data deleted.",
                    "Deleted", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshAll() {
        browserPanel.refreshData();
        healthPanel.refreshData();
        updateDetailLabel();
        updateButtons();
        updateStorageLabel();
    }

    private void updateStorageLabel() {
        long totalSize = calculateDirectorySize(dataDir);
        int seriesCount = countSeries();
        storageLabel.setText(formatSize(totalSize) + " (" + seriesCount + " series)");
    }

    private int countSeries() {
        int count = 0;
        if (!dataDir.exists()) return 0;

        File[] symbolDirs = dataDir.listFiles(File::isDirectory);
        if (symbolDirs == null) return 0;

        for (File symbolDir : symbolDirs) {
            File[] tfDirs = symbolDir.listFiles(File::isDirectory);
            if (tfDirs != null) {
                count += tfDirs.length;
            }
        }
        return count;
    }

    private long calculateDirectorySize(File dir) {
        if (!dir.exists()) return 0;
        if (dir.isFile()) return dir.length();

        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += calculateDirectorySize(f);
            }
        }
        return size;
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        if (bytes < 1024 * 1024 * 1024) return SIZE_FORMAT.format(bytes / (1024.0 * 1024)) + " MB";
        return SIZE_FORMAT.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    /**
     * Get info string for aggTrades data.
     */
    private String getAggTradesInfo(String symbol) {
        File symbolDir = new File(new File(aggTradesDir, symbol), "aggTrades");
        if (!symbolDir.exists()) {
            return symbol + " / AggTrades - No data. Use 'Fetch New' to download.";
        }

        File[] csvFiles = symbolDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            return symbol + " / AggTrades - No data. Use 'Fetch New' to download.";
        }

        // Find date range and stats
        String minDate = null;
        String maxDate = null;
        int completeCount = 0;
        int partialCount = 0;
        long totalSize = 0;

        for (File f : csvFiles) {
            String name = f.getName();
            boolean isPartial = name.endsWith(".partial.csv");
            String date = name.replace(".partial.csv", "").replace(".csv", "");

            if (isPartial) partialCount++; else completeCount++;
            totalSize += f.length();

            if (minDate == null || date.compareTo(minDate) < 0) minDate = date;
            if (maxDate == null || date.compareTo(maxDate) > 0) maxDate = date;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" / AggTrades - ");
        sb.append(completeCount + partialCount).append(" days (");
        sb.append(minDate).append(" to ").append(maxDate).append("), ");
        sb.append(formatSize(totalSize));
        if (partialCount > 0) {
            sb.append(" [").append(partialCount).append(" partial]");
        }
        return sb.toString();
    }

    /**
     * Show the dialog.
     */
    public static void show(Frame owner, CandleStore candleStore) {
        DataHealthDialog dialog = new DataHealthDialog(owner, candleStore);
        dialog.setVisible(true);
    }
}
