package com.tradery.forge.ui;

import com.tradery.forge.TraderyApp;
import com.tradery.forge.data.DataConfig;
import com.tradery.forge.io.WindowStateStore;
import com.tradery.forge.ui.charts.ChartConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Forge settings dialog. Extends the shared base for header, Appearance section,
 * and button bar, then adds forge-specific sections.
 */
public class SettingsDialog extends com.tradery.ui.settings.SettingsDialog {

    private JLabel dataLocationLabel;
    private JLabel dataSizeLabel;
    private JButton changeLocationBtn;
    private JButton resetLocationBtn;

    public SettingsDialog(Window owner) {
        super(owner);
    }

    @Override
    protected List<SectionEntry> addSections() {
        return List.of(
            new SectionEntry("AI Terminal", createAiTerminalContent()),
            new SectionEntry("Chart Defaults", createChartsContent()),
            new SectionEntry("Data Storage", createDataContent()),
            new SectionEntry("Factory Reset", createFactoryResetContent())
        );
    }

    private JPanel createAiTerminalContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Terminal Mode:"), gbc);

        JComboBox<String> aiTerminalModeCombo = new JComboBox<>(new String[]{"Integrated", "External (OS Terminal)"});
        String currentMode = WindowStateStore.getInstance().getAiTerminalMode();
        aiTerminalModeCombo.setSelectedIndex("external".equals(currentMode) ? 1 : 0);
        aiTerminalModeCombo.addActionListener(e -> {
            int selected = aiTerminalModeCombo.getSelectedIndex();
            String mode = selected == 1 ? "external" : "integrated";
            WindowStateStore.getInstance().setAiTerminalMode(mode);
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(aiTerminalModeCombo, gbc);

        // Info label
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel infoLabel = new JLabel("<html><small>Integrated: Terminal embedded in project window.<br>External: Opens Claude/Codex in macOS Terminal.app.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        return panel;
    }

    private JPanel createChartsContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        ChartConfig config = ChartConfig.getInstance();

        // Price axis position
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Price Axis:"), gbc);

        JComboBox<String> axisPositionCombo = new JComboBox<>(new String[]{"Left", "Right", "Both"});
        String currentPosition = config.getPriceAxisPosition();
        axisPositionCombo.setSelectedIndex(
            "right".equals(currentPosition) ? 1 :
            "both".equals(currentPosition) ? 2 : 0
        );
        axisPositionCombo.addActionListener(e -> {
            int selected = axisPositionCombo.getSelectedIndex();
            String position = switch (selected) {
                case 1 -> "right";
                case 2 -> "both";
                default -> "left";
            };
            ChartConfig.getInstance().setPriceAxisPosition(position);
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(axisPositionCombo, gbc);

        // Status summary
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel summaryLabel = new JLabel(getChartConfigSummary(config));
        panel.add(summaryLabel, gbc);

        // Info label
        gbc.gridy = 2;
        JLabel infoLabel = new JLabel("<html><small>Chart visibility is remembered between sessions.<br>Change which charts are shown using the Indicators button in chart view.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        // Reset button
        gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;

        JButton resetChartsBtn = new JButton("Reset Charts to Defaults");
        resetChartsBtn.setToolTipText("Disable all overlays and optional charts, enable core charts");
        resetChartsBtn.addActionListener(e -> {
            int option = JOptionPane.showConfirmDialog(this,
                "<html>Reset all chart settings to defaults?<br><br>" +
                "This will:<br>" +
                "• Disable all overlay indicators (SMA, EMA, etc.)<br>" +
                "• Disable all optional charts (RSI, MACD, etc.)<br>" +
                "• Enable all core charts (Volume, Equity, etc.)<br><br>" +
                "You will need to reopen any project windows to see changes.</html>",
                "Reset Chart Defaults",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (option == JOptionPane.OK_OPTION) {
                ChartConfig.getInstance().resetToDefaults();
                summaryLabel.setText(getChartConfigSummary(ChartConfig.getInstance()));
                JOptionPane.showMessageDialog(this,
                    "Chart settings reset to defaults.\nReopen project windows to apply changes.",
                    "Chart Settings Reset",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
        panel.add(resetChartsBtn, gbc);

        return panel;
    }

    private String getChartConfigSummary(ChartConfig config) {
        int overlaysEnabled = 0;
        if (config.isSmaEnabled()) overlaysEnabled++;
        if (config.isEmaEnabled()) overlaysEnabled++;
        if (config.isBollingerEnabled()) overlaysEnabled++;
        if (config.isHighLowEnabled()) overlaysEnabled++;
        if (config.isMayerEnabled()) overlaysEnabled++;

        int indicatorChartsEnabled = 0;
        if (config.isRsiEnabled()) indicatorChartsEnabled++;
        if (config.isMacdEnabled()) indicatorChartsEnabled++;
        if (config.isAtrEnabled()) indicatorChartsEnabled++;
        if (config.isDeltaEnabled()) indicatorChartsEnabled++;
        if (config.isCvdEnabled()) indicatorChartsEnabled++;
        if (config.isVolumeRatioEnabled()) indicatorChartsEnabled++;
        if (config.isWhaleEnabled()) indicatorChartsEnabled++;
        if (config.isRetailEnabled()) indicatorChartsEnabled++;
        if (config.isFundingEnabled()) indicatorChartsEnabled++;

        int coreChartsEnabled = 0;
        if (config.isVolumeChartEnabled()) coreChartsEnabled++;
        if (config.isEquityChartEnabled()) coreChartsEnabled++;
        if (config.isComparisonChartEnabled()) coreChartsEnabled++;
        if (config.isCapitalUsageChartEnabled()) coreChartsEnabled++;
        if (config.isTradePLChartEnabled()) coreChartsEnabled++;

        return String.format("%d overlays, %d indicator charts, %d/5 core charts enabled",
            overlaysEnabled, indicatorChartsEnabled, coreChartsEnabled);
    }

    private JPanel createDataContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        DataConfig config = DataConfig.getInstance();

        // Location row
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Location:"), gbc);

        dataLocationLabel = new JLabel(truncatePath(config.getDataDir().getAbsolutePath(), 40));
        dataLocationLabel.setToolTipText(config.getDataDir().getAbsolutePath());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(dataLocationLabel, gbc);

        // Size row
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Size:"), gbc);

        dataSizeLabel = new JLabel("Calculating...");
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(dataSizeLabel, gbc);

        // Calculate size in background
        SwingWorker<String, Void> sizeWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return config.getTotalDataSizeFormatted();
            }
            @Override
            protected void done() {
                try {
                    dataSizeLabel.setText(get());
                } catch (Exception e) {
                    dataSizeLabel.setText("Unknown");
                }
            }
        };
        sizeWorker.execute();

        // Info label
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><small>Large data files (candles, aggTrades, funding) can be moved to save space.<br>Strategies and settings stay in ~/.tradery for AI access.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        // Buttons row
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        changeLocationBtn = new JButton("Change Location...");
        changeLocationBtn.addActionListener(e -> changeDataLocation());
        buttonRow.add(changeLocationBtn);

        resetLocationBtn = new JButton("Reset to Default");
        resetLocationBtn.setEnabled(!config.isDefaultLocation());
        resetLocationBtn.addActionListener(e -> resetDataLocation());
        buttonRow.add(resetLocationBtn);

        panel.add(buttonRow, gbc);

        return panel;
    }

    private void changeDataLocation() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Data Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(DataConfig.getInstance().getDataDir().getParentFile());

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File newDir = chooser.getSelectedFile();

            DataConfig config = DataConfig.getInstance();
            File oldDir = config.getDataDir();

            int option = JOptionPane.showConfirmDialog(this,
                "<html>Move data from:<br><b>" + oldDir.getAbsolutePath() + "</b><br><br>" +
                "To new location:<br><b>" + newDir.getAbsolutePath() + "</b><br><br>" +
                "This will move all candle, aggTrades, and funding data.<br>" +
                "The application will restart after moving.</html>",
                "Confirm Data Move",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (option == JOptionPane.OK_OPTION) {
                moveDataAndRestart(oldDir, newDir);
            }
        }
    }

    private void resetDataLocation() {
        DataConfig config = DataConfig.getInstance();
        File oldDir = config.getDataDir();
        File defaultDir = config.getDefaultDataDir();

        int option = JOptionPane.showConfirmDialog(this,
            "<html>Move data back to default location:<br><b>" + defaultDir.getAbsolutePath() + "</b><br><br>" +
            "The application will restart after moving.</html>",
            "Confirm Reset",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            moveDataAndRestart(oldDir, defaultDir);
        }
    }

    private void moveDataAndRestart(File sourceDir, File targetDir) {
        JDialog progressDialog = new JDialog(this, "Moving Data...", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Moving data files...");
        progressBar.setStringPainted(true);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        progressPanel.add(new JLabel("Please wait while data is being moved..."), BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.setContentPane(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                if (sourceDir.exists()) {
                    File[] files = sourceDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            File dest = new File(targetDir, file.getName());
                            if (!dest.exists()) {
                                file.renameTo(dest);
                            }
                        }
                    }
                }

                DataConfig.getInstance().setDataDir(targetDir);
                return true;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Data moved successfully. The application will now restart.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                        System.exit(0);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                        "Failed to move data: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private String truncatePath(String path, int maxLen) {
        if (path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - maxLen + 3);
    }

    private JPanel createFactoryResetContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Info label
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><small>Select data categories to clear. This cannot be undone.</small></html>");
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(infoLabel, gbc);

        // Checkboxes for each data category
        File userDir = TraderyApp.USER_DIR;
        DataConfig dataConfig = DataConfig.getInstance();

        // Strategies
        JCheckBox strategiesCheck = new JCheckBox("Strategies & Backtest Results");
        File strategiesDir = new File(userDir, "strategies");
        strategiesCheck.setToolTipText("Delete all strategies and backtest history. Sample strategies will be restored on restart.");
        gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 1.0;
        panel.add(strategiesCheck, gbc);

        JLabel strategiesSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(strategiesSize, gbc);

        // Custom Phases
        JCheckBox phasesCheck = new JCheckBox("Phases (Custom & Built-in)");
        File phasesDir = new File(userDir, "phases");
        phasesCheck.setToolTipText("Delete all phases. Built-in phases will be restored on restart.");
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0;
        panel.add(phasesCheck, gbc);

        JLabel phasesSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(phasesSize, gbc);

        // Custom Hoops
        JCheckBox hoopsCheck = new JCheckBox("Custom Hoop Patterns");
        File hoopsDir = new File(userDir, "hoops");
        hoopsCheck.setToolTipText("Delete custom hoop pattern definitions");
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 1.0;
        panel.add(hoopsCheck, gbc);

        JLabel hoopsSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(hoopsSize, gbc);

        // Market Data (all data directories combined)
        JCheckBox marketDataCheck = new JCheckBox("Market Data (Candles, Funding, OI, AggTrades)");
        marketDataCheck.setToolTipText("Delete all cached market data - will be re-downloaded as needed");
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 1.0;
        panel.add(marketDataCheck, gbc);

        JLabel marketDataSize = new JLabel("...");
        gbc.gridx = 1; gbc.weightx = 0;
        panel.add(marketDataSize, gbc);

        // App Settings
        JCheckBox settingsCheck = new JCheckBox("App Settings (Theme, Window Positions, Chart Config)");
        settingsCheck.setToolTipText("Reset all app preferences to defaults");
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 1.0;
        panel.add(settingsCheck, gbc);

        // Calculate sizes in background
        SwingWorker<Void, Void> sizeWorker = new SwingWorker<>() {
            private String strategiesSizeText;
            private String phasesSizeText;
            private String hoopsSizeText;
            private String marketDataSizeText;

            @Override
            protected Void doInBackground() {
                strategiesSizeText = formatDirectorySize(strategiesDir);
                phasesSizeText = formatDirectorySize(phasesDir);
                hoopsSizeText = formatDirectorySize(hoopsDir);

                long marketDataBytes = calculateDirectorySize(dataConfig.getDataDir());
                marketDataBytes += calculateDirectorySize(new File(userDir, "aggtrades"));
                marketDataBytes += calculateDirectorySize(new File(userDir, "funding"));
                marketDataBytes += calculateDirectorySize(new File(userDir, "openinterest"));
                marketDataSizeText = formatSize(marketDataBytes);

                return null;
            }

            @Override
            protected void done() {
                strategiesSize.setText(strategiesSizeText);
                phasesSize.setText(phasesSizeText);
                hoopsSize.setText(hoopsSizeText);
                marketDataSize.setText(marketDataSizeText);
            }
        };
        sizeWorker.execute();

        // Reset button
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(12, 8, 4, 8);

        JButton resetButton = new JButton("Clear Selected Data...");
        resetButton.addActionListener(e -> {
            List<String> selected = new ArrayList<>();
            if (strategiesCheck.isSelected()) selected.add("Strategies & Backtest Results");
            if (phasesCheck.isSelected()) selected.add("Phases");
            if (hoopsCheck.isSelected()) selected.add("Custom Hoop Patterns");
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
                message.append("• ").append(item).append("<br>");
            }
            message.append("<br><b>This cannot be undone!</b>");

            boolean willRestore = strategiesCheck.isSelected() || phasesCheck.isSelected();
            if (willRestore) {
                message.append("<br><br><i>Note: ");
                if (strategiesCheck.isSelected() && phasesCheck.isSelected()) {
                    message.append("Sample strategies and built-in phases");
                } else if (strategiesCheck.isSelected()) {
                    message.append("Sample strategies");
                } else {
                    message.append("Built-in phases");
                }
                message.append(" will be restored on restart.</i>");
            }

            if (settingsCheck.isSelected() || strategiesCheck.isSelected() || phasesCheck.isSelected()) {
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
                performReset(
                    strategiesCheck.isSelected(),
                    phasesCheck.isSelected(),
                    hoopsCheck.isSelected(),
                    marketDataCheck.isSelected(),
                    settingsCheck.isSelected()
                );
            }
        });
        panel.add(resetButton, gbc);

        return panel;
    }

    private void performReset(boolean strategies, boolean phases, boolean hoops,
                              boolean marketData, boolean settings) {
        File userDir = TraderyApp.USER_DIR;
        DataConfig dataConfig = DataConfig.getInstance();

        JDialog progressDialog = new JDialog(this, "Clearing Data...", true);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Deleting files...");
        progressBar.setStringPainted(true);

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        progressPanel.add(new JLabel("Please wait..."), BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        progressDialog.setContentPane(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

        boolean needsRestart = strategies || phases || settings;

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    if (strategies) {
                        deleteDirectoryContents(new File(userDir, "strategies"));
                    }
                    if (phases) {
                        deleteDirectoryContents(new File(userDir, "phases"));
                    }
                    if (hoops) {
                        deleteDirectoryContents(new File(userDir, "hoops"));
                    }
                    if (marketData) {
                        deleteDirectoryContents(dataConfig.getDataDir());
                        deleteDirectoryContents(new File(userDir, "aggtrades"));
                        deleteDirectoryContents(new File(userDir, "funding"));
                        deleteDirectoryContents(new File(userDir, "openinterest"));
                    }
                    if (settings) {
                        new File(userDir, "window-state.json").delete();
                        new File(userDir, "chart-config.json").delete();
                        new File(userDir, "theme.txt").delete();
                        new File(userDir, "data-location.txt").delete();
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    if (get()) {
                        if (needsRestart) {
                            JOptionPane.showMessageDialog(SettingsDialog.this,
                                "Data cleared successfully. The application will now restart.",
                                "Reset Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                            System.exit(0);
                        } else {
                            JOptionPane.showMessageDialog(SettingsDialog.this,
                                "Data cleared successfully.",
                                "Reset Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Some files could not be deleted. They may be in use.",
                            "Partial Reset",
                            JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                        "Error during reset: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private void deleteDirectoryContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryContents(file);
                }
                file.delete();
            }
        }
    }

    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    private String formatDirectorySize(File dir) {
        return formatSize(calculateDirectorySize(dir));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
