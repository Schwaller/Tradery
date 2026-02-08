package com.tradery.desk.ui;

import com.tradery.desk.DeskConfig;
import com.tradery.ui.settings.SettingsDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings dialog for Trading Desk.
 * Extends the shared base which provides the Appearance section and modern styling.
 * Adds Chart Defaults, Data Storage, and Factory Reset sections.
 */
public class DeskSettingsDialog extends SettingsDialog {

    public DeskSettingsDialog(Window owner) {
        super(owner);
    }

    private DeskFrame getDeskFrame() {
        return (getOwner() instanceof DeskFrame df) ? df : null;
    }

    @Override
    protected List<SectionEntry> addSections() {
        return List.of(
            new SectionEntry("Chart Defaults", createChartDefaultsContent()),
            new SectionEntry("Data Storage", createDataStorageContent()),
            new SectionEntry("Factory Reset", createFactoryResetContent())
        );
    }

    private JPanel createChartDefaultsContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Summary of current chart state
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel summaryLabel = new JLabel(getChartSummary());
        panel.add(summaryLabel, gbc);

        // Info label
        gbc.gridy = 1;
        JLabel infoLabel = new JLabel("<html><small>Overlays and indicators are configured via the side panel in the chart view.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        // Reset button
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;

        JButton resetBtn = new JButton("Clear All Overlays");
        resetBtn.setToolTipText("Remove all overlays and optional indicator charts");
        resetBtn.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(this,
                "Clear all chart overlays and indicator charts?",
                "Clear Overlays",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (option == JOptionPane.OK_OPTION && getDeskFrame() != null) {
                PriceChartPanel chart = getDeskFrame().getPriceChartPanel();
                chart.clearOverlays();
                summaryLabel.setText(getChartSummary());
            }
        });
        panel.add(resetBtn, gbc);

        return panel;
    }

    private String getChartSummary() {
        if (getDeskFrame() == null) return "No chart available";
        PriceChartPanel chart = getDeskFrame().getPriceChartPanel();

        int indicators = 0;
        if (chart.isRsiEnabled()) indicators++;
        if (chart.isMacdEnabled()) indicators++;
        if (chart.isAtrEnabled()) indicators++;
        if (chart.isStochasticEnabled()) indicators++;
        if (chart.isAdxEnabled()) indicators++;
        if (chart.isRangePositionEnabled()) indicators++;
        if (chart.isDeltaEnabled()) indicators++;
        if (chart.isTradeCountEnabled()) indicators++;
        if (chart.isVolumeRatioEnabled()) indicators++;

        return String.format("Volume: %s, %d indicator charts active",
            chart.isVolumeEnabled() ? "on" : "off", indicators);
    }

    private JPanel createDataStorageContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        File userDir = new File(System.getProperty("user.home"), ".tradery");

        // Data location
        File dataDir = resolveDataDir(userDir);
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Location:"), gbc);

        JLabel locationLabel = new JLabel(truncatePath(dataDir.getAbsolutePath(), 40));
        locationLabel.setToolTipText(dataDir.getAbsolutePath());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(locationLabel, gbc);

        // Total size
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Size:"), gbc);

        JLabel sizeLabel = new JLabel("Calculating...");
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(sizeLabel, gbc);

        // Calculate in background
        SwingWorker<String, Void> sizeWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                long total = calculateDirectorySize(dataDir);
                total += calculateDirectorySize(new File(userDir, "aggtrades"));
                total += calculateDirectorySize(new File(userDir, "funding"));
                total += calculateDirectorySize(new File(userDir, "openinterest"));
                return formatSize(total);
            }
            @Override
            protected void done() {
                try { sizeLabel.setText(get()); } catch (Exception e) { sizeLabel.setText("Unknown"); }
            }
        };
        sizeWorker.execute();

        // Info label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel infoLabel = new JLabel("<html><small>Market data is managed by the data service and shared with Forge.<br>Use Forge Settings to change the data location.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        return panel;
    }

    private JPanel createFactoryResetContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        File userDir = new File(System.getProperty("user.home"), ".tradery");

        // Info label
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><small>Select data categories to clear. This cannot be undone.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        // Desk config
        JCheckBox deskConfigCheck = new JCheckBox("Desk Config (Activated Strategies, Alerts)");
        deskConfigCheck.setToolTipText("Reset desk configuration to defaults");
        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 1.0;
        panel.add(deskConfigCheck, gbc);

        JLabel deskConfigSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(deskConfigSize, gbc);

        // Market Data
        JCheckBox marketDataCheck = new JCheckBox("Market Data (Candles, Funding, OI, AggTrades)");
        marketDataCheck.setToolTipText("Delete all cached market data - will be re-downloaded as needed");
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0;
        panel.add(marketDataCheck, gbc);

        JLabel marketDataSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(marketDataSize, gbc);

        // App Settings
        JCheckBox settingsCheck = new JCheckBox("App Settings (Theme)");
        settingsCheck.setToolTipText("Reset theme to default");
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 1.0;
        panel.add(settingsCheck, gbc);

        // Calculate sizes in background
        File dataDir = resolveDataDir(userDir);
        SwingWorker<Void, Void> sizeWorker = new SwingWorker<>() {
            private String deskSizeText;
            private String marketSizeText;

            @Override
            protected Void doInBackground() {
                deskSizeText = formatSize(calculateDirectorySize(DeskConfig.DESK_DIR.toFile()));
                long marketBytes = calculateDirectorySize(dataDir);
                marketBytes += calculateDirectorySize(new File(userDir, "aggtrades"));
                marketBytes += calculateDirectorySize(new File(userDir, "funding"));
                marketBytes += calculateDirectorySize(new File(userDir, "openinterest"));
                marketSizeText = formatSize(marketBytes);
                return null;
            }

            @Override
            protected void done() {
                deskConfigSize.setText(deskSizeText);
                marketDataSize.setText(marketSizeText);
            }
        };
        sizeWorker.execute();

        // Reset button
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(12, 8, 4, 8);

        JButton resetButton = new JButton("Clear Selected Data...");
        resetButton.addActionListener(e -> {
            List<String> selected = new ArrayList<>();
            if (deskConfigCheck.isSelected()) selected.add("Desk Config");
            if (marketDataCheck.isSelected()) selected.add("Market Data");
            if (settingsCheck.isSelected()) selected.add("App Settings");

            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Please select at least one category to clear.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            StringBuilder message = new StringBuilder();
            message.append("<html>Are you sure you want to delete the following data?<br><br>");
            for (String item : selected) {
                message.append("\u2022 ").append(item).append("<br>");
            }
            message.append("<br><b>This cannot be undone!</b>");
            if (deskConfigCheck.isSelected() || settingsCheck.isSelected()) {
                message.append("<br><br>The application will restart after clearing.</html>");
            } else {
                message.append("</html>");
            }

            int option = JOptionPane.showConfirmDialog(this,
                message.toString(),
                "Confirm Factory Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (option == JOptionPane.YES_OPTION) {
                performReset(deskConfigCheck.isSelected(),
                    marketDataCheck.isSelected(),
                    settingsCheck.isSelected(),
                    userDir, dataDir);
            }
        });
        panel.add(resetButton, gbc);

        return panel;
    }

    private void performReset(boolean deskConfig, boolean marketData, boolean settings,
                              File userDir, File dataDir) {
        boolean needsRestart = deskConfig || settings;

        if (deskConfig) {
            deleteDirectoryContents(DeskConfig.DESK_DIR.toFile());
        }
        if (marketData) {
            deleteDirectoryContents(dataDir);
            deleteDirectoryContents(new File(userDir, "aggtrades"));
            deleteDirectoryContents(new File(userDir, "funding"));
            deleteDirectoryContents(new File(userDir, "openinterest"));
        }
        if (settings) {
            new File(userDir, "theme.txt").delete();
        }

        if (needsRestart) {
            JOptionPane.showMessageDialog(this,
                "Data cleared successfully. The application will now restart.",
                "Reset Complete",
                JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        } else {
            JOptionPane.showMessageDialog(this,
                "Data cleared successfully.",
                "Reset Complete",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --- Utility methods ---

    private File resolveDataDir(File userDir) {
        try {
            Path locationFile = userDir.toPath().resolve("data-location.txt");
            if (Files.exists(locationFile)) {
                String path = Files.readString(locationFile).trim();
                if (!path.isEmpty()) {
                    File dir = new File(path);
                    if (dir.exists()) return dir;
                }
            }
        } catch (Exception ignored) {}
        return new File(userDir, "data");
    }

    private String truncatePath(String path, int maxLen) {
        if (path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - maxLen + 3);
    }

    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) size += file.length();
                else if (file.isDirectory()) size += calculateDirectorySize(file);
            }
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void deleteDirectoryContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteDirectoryContents(file);
                file.delete();
            }
        }
    }
}
