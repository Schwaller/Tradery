package com.tradery.ui;

import com.tradery.data.CandleStore;
import com.tradery.data.DataIntegrityChecker;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialog for viewing and repairing data health.
 * Shows 2D block diagram of data completeness by month.
 */
public class DataHealthDialog extends JDialog {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final DataIntegrityChecker checker;
    private final CandleStore candleStore;

    private JComboBox<String> symbolCombo;
    private JComboBox<String> resolutionCombo;
    private DataHealthPanel healthPanel;
    private JLabel detailLabel;
    private JButton repairButton;
    private JButton deleteButton;
    private JProgressBar progressBar;

    private DataHealth selectedHealth;

    public DataHealthDialog(Frame owner, CandleStore candleStore) {
        super(owner, "Data Health", true);
        this.candleStore = candleStore;
        this.checker = new DataIntegrityChecker();

        initUI();
        loadSymbols();

        setSize(600, 500);
        setLocationRelativeTo(owner);

        // Handle close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                candleStore.cancelCurrentFetch();
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(30, 30, 35));

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

        // Top panel with symbol/resolution selection
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Main content - health panel with scroll
        healthPanel = new DataHealthPanel(checker);
        healthPanel.setOnMonthSelected(this::onMonthSelected);

        JScrollPane scrollPane = new JScrollPane(healthPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(30, 30, 35));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with details and actions
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        panel.setBackground(new Color(40, 40, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0)); // Space for macOS title bar

        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.WHITE);
        panel.add(symbolLabel);

        symbolCombo = new JComboBox<>();
        symbolCombo.setPreferredSize(new Dimension(120, 28));
        symbolCombo.addActionListener(e -> onSymbolChanged());
        panel.add(symbolCombo);

        panel.add(Box.createHorizontalStrut(12));

        JLabel resLabel = new JLabel("Resolution:");
        resLabel.setForeground(Color.WHITE);
        panel.add(resLabel);

        resolutionCombo = new JComboBox<>();
        resolutionCombo.setPreferredSize(new Dimension(80, 28));
        resolutionCombo.addActionListener(e -> onResolutionChanged());
        panel.add(resolutionCombo);

        panel.add(Box.createHorizontalStrut(20));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        panel.add(refreshButton);

        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(40, 40, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        // Detail label
        detailLabel = new JLabel("Select a month to see details");
        detailLabel.setForeground(new Color(150, 150, 150));
        panel.add(detailLabel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        repairButton = new JButton("Repair Month");
        repairButton.setEnabled(false);
        repairButton.addActionListener(e -> repairSelectedMonth());
        buttonPanel.add(repairButton);

        deleteButton = new JButton("Delete");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedMonth());
        buttonPanel.add(deleteButton);

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

    private void loadSymbols() {
        List<String> symbols = checker.getAvailableSymbols();
        symbolCombo.removeAllItems();
        for (String s : symbols) {
            symbolCombo.addItem(s);
        }

        if (!symbols.isEmpty()) {
            symbolCombo.setSelectedIndex(0);
        }
    }

    private void onSymbolChanged() {
        String symbol = (String) symbolCombo.getSelectedItem();
        if (symbol == null) return;

        List<String> resolutions = checker.getAvailableResolutions(symbol);
        resolutionCombo.removeAllItems();
        for (String r : resolutions) {
            resolutionCombo.addItem(r);
        }

        if (!resolutions.isEmpty()) {
            resolutionCombo.setSelectedIndex(0);
        }
    }

    private void onResolutionChanged() {
        refreshData();
    }

    private void refreshData() {
        String symbol = (String) symbolCombo.getSelectedItem();
        String resolution = (String) resolutionCombo.getSelectedItem();

        if (symbol == null || resolution == null) {
            healthPanel.setData(null, null);
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
        if (selectedHealth == null) {
            detailLabel.setText("Select a month to see details");
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

        boolean canDelete = selectedHealth != null &&
                selectedHealth.status() != DataStatus.MISSING;

        repairButton.setEnabled(canRepair);
        deleteButton.setEnabled(canDelete);
    }

    private void repairSelectedMonth() {
        if (selectedHealth == null) return;

        String symbol = (String) symbolCombo.getSelectedItem();
        String resolution = (String) resolutionCombo.getSelectedItem();

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
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                candleStore.repairMonth(symbol, resolution, selectedHealth.month());
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                candleStore.setProgressCallback(null);

                try {
                    get();
                    JOptionPane.showMessageDialog(DataHealthDialog.this,
                            "Repair complete for " + selectedHealth.month().format(MONTH_FORMAT),
                            "Repair Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DataHealthDialog.this,
                            "Repair failed: " + e.getMessage(),
                            "Repair Error", JOptionPane.ERROR_MESSAGE);
                }

                refreshData();
            }
        };
        worker.execute();
    }

    private void deleteSelectedMonth() {
        if (selectedHealth == null) return;

        String symbol = (String) symbolCombo.getSelectedItem();
        String resolution = (String) resolutionCombo.getSelectedItem();

        int result = JOptionPane.showConfirmDialog(this,
                "Delete all data for " + selectedHealth.month().format(MONTH_FORMAT) + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            candleStore.deleteMonth(symbol, resolution, selectedHealth.month());
            refreshData();
        }
    }

    /**
     * Show the dialog.
     */
    public static void show(Frame owner, CandleStore candleStore) {
        DataHealthDialog dialog = new DataHealthDialog(owner, candleStore);
        dialog.setVisible(true);
    }
}
