package com.tradery.forge.ui;

import com.tradery.forge.data.AggTradesStore;
import com.tradery.forge.data.SyncEstimator;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for syncing aggregated trade data from Binance.
 * Shows progress with estimated time and supports cancellation.
 */
public class AggTradesSyncDialog extends JDialog {

    private final AggTradesStore aggTradesStore;
    private final String symbol;
    private final long startTime;
    private final long endTime;
    private final Runnable onComplete;

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel estimateLabel;
    private JButton cancelButton;

    private SwingWorker<Void, Void> syncWorker;
    private boolean cancelled = false;

    public AggTradesSyncDialog(Window owner, AggTradesStore aggTradesStore,
                               String symbol, long startTime, long endTime,
                               Runnable onComplete) {
        super(owner, "Syncing Trade Data", ModalityType.APPLICATION_MODAL);
        this.aggTradesStore = aggTradesStore;
        this.symbol = symbol;
        this.startTime = startTime;
        this.endTime = endTime;
        this.onComplete = onComplete;

        initUI();

        setSize(400, 180);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(40, 40, 45));

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

        // Title spacer
        JPanel titleSpacer = new JPanel();
        titleSpacer.setPreferredSize(new Dimension(0, 28));
        titleSpacer.setOpaque(false);
        add(titleSpacer, BorderLayout.NORTH);

        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(40, 40, 45));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // Status label
        statusLabel = new JLabel("Syncing " + symbol + " trade data...");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(statusLabel);

        contentPanel.add(Box.createVerticalStrut(8));

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Connecting...");
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        contentPanel.add(progressBar);

        contentPanel.add(Box.createVerticalStrut(8));

        // Estimate label
        String estimate = SyncEstimator.estimateSyncTime(symbol, startTime, endTime);
        estimateLabel = new JLabel("Estimated time: " + estimate);
        estimateLabel.setForeground(Color.GRAY);
        estimateLabel.setFont(estimateLabel.getFont().deriveFont(Font.ITALIC, 11f));
        estimateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(estimateLabel);

        add(contentPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.setBackground(new Color(35, 35, 40));

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> onCancel());
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Start the sync operation.
     */
    public void startSync() {
        // Set up progress callback
        aggTradesStore.setProgressCallback(progress -> {
            SwingUtilities.invokeLater(() -> {
                if (progress.estimatedTotal() > 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(progress.percentComplete());
                }
                progressBar.setString(progress.message());
            });
        });

        // Run sync in background
        syncWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                aggTradesStore.getAggTrades(symbol, startTime, endTime);
                return null;
            }

            @Override
            protected void done() {
                aggTradesStore.setProgressCallback(null);

                if (!cancelled) {
                    try {
                        get();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    } catch (Exception e) {
                        if (!aggTradesStore.isFetchCancelled()) {
                            JOptionPane.showMessageDialog(AggTradesSyncDialog.this,
                                    "Sync failed: " + e.getMessage(),
                                    "Sync Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }

                dispose();
            }
        };
        syncWorker.execute();
    }

    private void onCancel() {
        cancelled = true;
        if (syncWorker != null) {
            aggTradesStore.cancelCurrentFetch();
            syncWorker.cancel(true);
        }
        dispose();
    }

    /**
     * Show the dialog and start syncing.
     */
    public static void showAndSync(Window owner, AggTradesStore aggTradesStore,
                                    String symbol, long startTime, long endTime,
                                    Runnable onComplete) {
        AggTradesSyncDialog dialog = new AggTradesSyncDialog(
            owner, aggTradesStore, symbol, startTime, endTime, onComplete);
        dialog.startSync();
        dialog.setVisible(true);
    }
}
