package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.data.DataConfig;
import com.tradery.ui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

/**
 * Unified settings dialog for theme and data configuration.
 */
public class SettingsDialog extends JDialog {

    private JComboBox<String> themeCombo;
    private JLabel dataLocationLabel;
    private JLabel dataSizeLabel;
    private JButton changeLocationBtn;
    private JButton resetLocationBtn;

    public SettingsDialog(Window owner) {
        super(owner, "Settings", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initComponents();
        pack();
        setMinimumSize(new Dimension(450, 300));
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 16));
        contentPane.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Theme section
        JPanel themeSection = createThemeSection();

        // Data section
        JPanel dataSection = createDataSection();

        // Stack sections vertically
        JPanel sectionsPanel = new JPanel();
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));
        sectionsPanel.add(themeSection);
        sectionsPanel.add(Box.createVerticalStrut(12));
        sectionsPanel.add(dataSection);

        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);

        contentPane.add(sectionsPanel, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private JPanel createThemeSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Appearance",
            TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Theme row
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Theme:"), gbc);

        themeCombo = new JComboBox<>();
        for (String themeName : ThemeManager.getInstance().getAvailableThemeNames()) {
            themeCombo.addItem(themeName);
        }
        themeCombo.setSelectedItem(ThemeManager.getInstance().getCurrentThemeName());
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null) {
                ThemeManager.getInstance().setTheme(selected);
            }
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(themeCombo, gbc);

        return panel;
    }

    private JPanel createDataSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Data Storage",
            TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
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

            // Confirm if directory has existing data
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
        // Show progress dialog
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
                // Create target directory
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                // Move contents
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

                // Update config
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

                        // Restart application
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
}
