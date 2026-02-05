package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.AggTradesStore;
import com.tradery.forge.data.DataConfig;
import com.tradery.forge.data.PremiumIndexStore;
import com.tradery.forge.data.DataIntegrityChecker;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.core.model.DataHealth;
import com.tradery.core.model.DataStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for managing cached data (candles, aggTrades, funding).
 * Left side: tree-style navigation of symbol/timeframe
 * Right side: 2D block diagram of data completeness by month.
 */
public class DataManagementDialog extends JDialog {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");

    private final DataIntegrityChecker checker;
    private final SqliteDataStore dataStore;
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

    public DataManagementDialog(Frame owner, SqliteDataStore dataStore) {
        super(owner, "Manage Data", true);
        this.dataStore = dataStore;
        this.checker = new DataIntegrityChecker();
        this.dataDir = DataConfig.getInstance().getDataDir();
        this.aggTradesDir = DataConfig.getInstance().getDataDir();

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
                // SQLite transactions are atomic, no need to cancel
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));

        // Left side: Data browser (tree-style navigation)
        browserPanel = new DataBrowserPanel();
        browserPanel.setOnSelectionChanged(this::onSeriesSelected);

        JScrollPane browserScroll = new JScrollPane(browserPanel);
        browserScroll.setBorder(BorderFactory.createEmptyBorder());
        browserScroll.setPreferredSize(new Dimension(220, 0));

        // Storage info at bottom of left panel
        storageLabel = new JLabel();
        storageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        storageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        storageLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        updateStorageLabel();

        // Storage label with separator above
        JPanel storagePanel = new JPanel(new BorderLayout(0, 0));
        storagePanel.add(new JSeparator(), BorderLayout.NORTH);
        storagePanel.add(storageLabel, BorderLayout.CENTER);

        // Left content (browser + storage panel)
        JPanel leftContent = new JPanel(new BorderLayout(0, 0));
        leftContent.add(browserScroll, BorderLayout.CENTER);
        leftContent.add(storagePanel, BorderLayout.SOUTH);

        // Left panel with separator on the right edge
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.add(leftContent, BorderLayout.CENTER);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        add(leftPanel, BorderLayout.WEST);

        // Right side: Title + Health panel (block diagram)
        detailLabel = new JLabel("Select a data series from the left");
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.BOLD, 12f));
        detailLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        healthPanel = new DataHealthPanel(checker);
        healthPanel.setOnMonthSelected(this::onMonthSelected);

        JScrollPane healthScroll = new JScrollPane(healthPanel);
        healthScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel rightPanel = new JPanel(new BorderLayout(0, 0));
        rightPanel.add(detailLabel, BorderLayout.NORTH);
        rightPanel.add(healthScroll, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);

        // Bottom panel with actions
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        // Separator line at top
        panel.add(new JSeparator(), BorderLayout.NORTH);

        // Button wrapper with padding
        JPanel buttonWrapper = new JPanel(new BorderLayout());
        buttonWrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

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
            dispose();
        });
        buttonPanel.add(closeButton);

        buttonWrapper.add(buttonPanel, BorderLayout.EAST);

        // Progress bar (hidden by default)
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        buttonWrapper.add(progressBar, BorderLayout.SOUTH);

        panel.add(buttonWrapper, BorderLayout.CENTER);

        return panel;
    }

    private void showFetchDialog() {
        AggTradesStore aggTradesStore = ApplicationContext.getInstance().getAggTradesStore();
        PremiumIndexStore premiumIndexStore = ApplicationContext.getInstance().getPremiumIndexStore();
        FetchDataDialog.show((Frame) getOwner(), dataStore, aggTradesStore, premiumIndexStore, () -> {
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

        // Handle fundingRate selection
        if ("fundingRate".equals(resolution)) {
            healthPanel.setFundingRateData(symbol);
            detailLabel.setText(getFundingRateInfo(symbol));
            selectedHealth = null;
            updateButtons();
            return;
        }

        // Handle openInterest selection
        if ("openInterest".equals(resolution)) {
            healthPanel.setOpenInterestData(symbol);
            detailLabel.setText(getOpenInterestInfo(symbol));
            selectedHealth = null;
            updateButtons();
            return;
        }

        // Handle premiumIndex selection
        if ("premiumIndex".equals(resolution)) {
            healthPanel.setPremiumIndexData(symbol);
            detailLabel.setText(getPremiumIndexInfo(symbol));
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
        // Don't overwrite aggTrades, funding rate, OI, or premium index info
        if ("aggTrades".equals(currentResolution) || "fundingRate".equals(currentResolution) ||
            "openInterest".equals(currentResolution) || "premiumIndex".equals(currentResolution)) {
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

        // TODO: Repair functionality needs to be reimplemented for SQLite
        // For now, show not implemented message
        JOptionPane.showMessageDialog(this,
            "Repair functionality is being migrated to SQLite.\n" +
            "Use 'Fetch Data' to re-download missing data.",
            "Not Implemented", JOptionPane.INFORMATION_MESSAGE);
        progressBar.setVisible(false);
        repairButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void deleteSelectedMonth() {
        if (selectedHealth == null || currentSymbol == null || currentResolution == null) return;

        // TODO: Delete functionality needs to be reimplemented for SQLite
        JOptionPane.showMessageDialog(this,
            "Delete functionality is being migrated to SQLite.\n" +
            "Data deletion will be available in a future update.",
            "Not Implemented", JOptionPane.INFORMATION_MESSAGE);
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
     * Get info string for funding rate data.
     */
    private String getFundingRateInfo(String symbol) {
        File fundingDir = new File(dataDir, symbol + "/funding");
        if (!fundingDir.exists()) {
            return symbol + " / Funding Rate - No data. Funding rates are auto-fetched when needed.";
        }

        File[] csvFiles = fundingDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            return symbol + " / Funding Rate - No data. Funding rates are auto-fetched when needed.";
        }

        // Find month range and count
        String minMonth = null;
        String maxMonth = null;
        int totalRates = 0;
        long totalSize = 0;

        for (File f : csvFiles) {
            String name = f.getName().replace(".csv", "");
            totalSize += f.length();

            if (minMonth == null || name.compareTo(minMonth) < 0) minMonth = name;
            if (maxMonth == null || name.compareTo(maxMonth) > 0) maxMonth = name;

            // Count lines (minus header)
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f))) {
                int lines = 0;
                while (reader.readLine() != null) lines++;
                totalRates += Math.max(0, lines - 1);
            } catch (java.io.IOException e) {
                // Ignore
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" / Funding Rate - ");
        sb.append(csvFiles.length).append(" months (");
        sb.append(minMonth).append(" to ").append(maxMonth).append("), ");
        sb.append(totalRates).append(" rates, ");
        sb.append(formatSize(totalSize));
        sb.append(" [8h resolution]");
        return sb.toString();
    }

    private String getOpenInterestInfo(String symbol) {
        File oiDir = new File(dataDir, symbol + "/openinterest");
        if (!oiDir.exists()) {
            return symbol + " / Open Interest - No data. OI is auto-fetched during backtest.";
        }

        File[] csvFiles = oiDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            return symbol + " / Open Interest - No data. OI is auto-fetched during backtest.";
        }

        // Find month range and count
        String minMonth = null;
        String maxMonth = null;
        int totalRecords = 0;
        long totalSize = 0;

        for (File f : csvFiles) {
            String name = f.getName().replace(".csv", "");
            totalSize += f.length();

            if (minMonth == null || name.compareTo(minMonth) < 0) minMonth = name;
            if (maxMonth == null || name.compareTo(maxMonth) > 0) maxMonth = name;

            // Count lines (minus header)
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f))) {
                int lines = 0;
                while (reader.readLine() != null) lines++;
                totalRecords += Math.max(0, lines - 1);
            } catch (java.io.IOException e) {
                // Ignore
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" / Open Interest - ");
        sb.append(csvFiles.length).append(" months (");
        sb.append(minMonth).append(" to ").append(maxMonth).append("), ");
        sb.append(totalRecords).append(" records, ");
        sb.append(formatSize(totalSize));
        sb.append(" [5m resolution]");
        return sb.toString();
    }

    private String getPremiumIndexInfo(String symbol) {
        File premiumDir = new File(dataDir, symbol + "/premium");
        if (!premiumDir.exists()) {
            return symbol + " / Premium Index - No data. Premium index is auto-fetched when needed.";
        }

        File[] intervalDirs = premiumDir.listFiles(File::isDirectory);
        if (intervalDirs == null || intervalDirs.length == 0) {
            return symbol + " / Premium Index - No data. Premium index is auto-fetched when needed.";
        }

        // Aggregate stats across all intervals
        String minMonth = null;
        String maxMonth = null;
        int totalRecords = 0;
        long totalSize = 0;
        java.util.Set<String> intervals = new java.util.TreeSet<>();

        for (File intervalDir : intervalDirs) {
            intervals.add(intervalDir.getName());
            File[] csvFiles = intervalDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (csvFiles == null) continue;

            for (File f : csvFiles) {
                String name = f.getName().replace(".csv", "");
                totalSize += f.length();

                if (minMonth == null || name.compareTo(minMonth) < 0) minMonth = name;
                if (maxMonth == null || name.compareTo(maxMonth) > 0) maxMonth = name;

                // Count lines (minus header)
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(f))) {
                    int lines = 0;
                    while (reader.readLine() != null) lines++;
                    totalRecords += Math.max(0, lines - 1);
                } catch (java.io.IOException e) {
                    // Ignore
                }
            }
        }

        if (minMonth == null) {
            return symbol + " / Premium Index - No data. Premium index is auto-fetched when needed.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" / Premium Index - ");
        sb.append(minMonth).append(" to ").append(maxMonth).append(", ");
        sb.append(totalRecords).append(" records, ");
        sb.append(formatSize(totalSize));
        sb.append(" [").append(String.join(", ", intervals)).append("]");
        return sb.toString();
    }

    /**
     * Show the dialog.
     */
    public static void show(Frame owner, SqliteDataStore dataStore) {
        DataManagementDialog dialog = new DataManagementDialog(owner, dataStore);
        dialog.setVisible(true);
    }
}
