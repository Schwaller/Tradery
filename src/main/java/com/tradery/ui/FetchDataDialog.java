package com.tradery.ui;

import com.tradery.data.CandleStore;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for fetching new data from Binance.
 * Allows selecting symbol, timeframe, and date range.
 */
public class FetchDataDialog extends JDialog {

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT",
        "AVAXUSDT", "LINKUSDT", "ATOMUSDT", "UNIUSDT", "XLMUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    private final CandleStore candleStore;
    private final Runnable onComplete;

    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JSpinner startYearSpinner;
    private JComboBox<String> startMonthCombo;
    private JSpinner endYearSpinner;
    private JComboBox<String> endMonthCombo;
    private JProgressBar progressBar;
    private JButton fetchButton;
    private JButton cancelButton;

    private SwingWorker<Void, Void> currentWorker;

    public FetchDataDialog(Frame owner, CandleStore candleStore, Runnable onComplete) {
        super(owner, "Fetch Data", true);
        this.candleStore = candleStore;
        this.onComplete = onComplete;

        initUI();

        setSize(400, 280);
        setLocationRelativeTo(owner);
        setResizable(false);
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

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(40, 40, 45));
        formPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Symbol
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.WHITE);
        formPanel.add(symbolLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        symbolCombo = new JComboBox<>(SYMBOLS);
        formPanel.add(symbolCombo, gbc);

        // Timeframe
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel tfLabel = new JLabel("Timeframe:");
        tfLabel.setForeground(Color.WHITE);
        formPanel.add(tfLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        formPanel.add(timeframeCombo, gbc);

        // Start date
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel startLabel = new JLabel("From:");
        startLabel.setForeground(Color.WHITE);
        formPanel.add(startLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        startPanel.setOpaque(false);
        startMonthCombo = new JComboBox<>(getMonthNames());
        startMonthCombo.setSelectedIndex(0);  // January
        startYearSpinner = new JSpinner(new SpinnerNumberModel(2020, 2017, 2030, 1));
        startYearSpinner.setEditor(new JSpinner.NumberEditor(startYearSpinner, "#"));
        startPanel.add(startMonthCombo);
        startPanel.add(startYearSpinner);
        formPanel.add(startPanel, gbc);

        // End date
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel endLabel = new JLabel("To:");
        endLabel.setForeground(Color.WHITE);
        formPanel.add(endLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel endPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        endPanel.setOpaque(false);
        endMonthCombo = new JComboBox<>(getMonthNames());
        endMonthCombo.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        endYearSpinner = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2017, 2030, 1));
        endYearSpinner.setEditor(new JSpinner.NumberEditor(endYearSpinner, "#"));
        endPanel.add(endMonthCombo);
        endPanel.add(endYearSpinner);
        formPanel.add(endPanel, gbc);

        // Progress bar
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(12, 6, 6, 6);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        formPanel.add(progressBar, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.setBackground(new Color(35, 35, 40));

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> onCancel());
        buttonPanel.add(cancelButton);

        fetchButton = new JButton("Fetch Data");
        fetchButton.addActionListener(e -> startFetch());
        buttonPanel.add(fetchButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private String[] getMonthNames() {
        return new String[] {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };
    }

    private void startFetch() {
        String symbol = (String) symbolCombo.getSelectedItem();
        String timeframe = (String) timeframeCombo.getSelectedItem();
        int startMonth = startMonthCombo.getSelectedIndex() + 1;
        int startYear = (Integer) startYearSpinner.getValue();
        int endMonth = endMonthCombo.getSelectedIndex() + 1;
        int endYear = (Integer) endYearSpinner.getValue();

        YearMonth start = YearMonth.of(startYear, startMonth);
        YearMonth end = YearMonth.of(endYear, endMonth);

        if (start.isAfter(end)) {
            JOptionPane.showMessageDialog(this,
                    "Start date must be before end date",
                    "Invalid Range", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable controls
        setControlsEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Fetching " + symbol + " " + timeframe + "...");

        // Calculate time range
        long startTime = start.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = end.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

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

        // Run fetch in background
        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                candleStore.getCandles(symbol, timeframe, startTime, endTime);
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                candleStore.setProgressCallback(null);
                setControlsEnabled(true);

                try {
                    get();
                    JOptionPane.showMessageDialog(FetchDataDialog.this,
                            "Data fetched successfully!",
                            "Fetch Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    dispose();
                } catch (Exception e) {
                    if (!candleStore.isFetchCancelled()) {
                        JOptionPane.showMessageDialog(FetchDataDialog.this,
                                "Fetch failed: " + e.getMessage(),
                                "Fetch Error", JOptionPane.ERROR_MESSAGE);
                    }
                }

                currentWorker = null;
            }
        };
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentWorker != null) {
            candleStore.cancelCurrentFetch();
            currentWorker.cancel(true);
        }
        dispose();
    }

    private void setControlsEnabled(boolean enabled) {
        symbolCombo.setEnabled(enabled);
        timeframeCombo.setEnabled(enabled);
        startMonthCombo.setEnabled(enabled);
        startYearSpinner.setEnabled(enabled);
        endMonthCombo.setEnabled(enabled);
        endYearSpinner.setEnabled(enabled);
        fetchButton.setEnabled(enabled);
        cancelButton.setText(enabled ? "Cancel" : "Stop");
    }

    /**
     * Show the dialog.
     */
    public static void show(Frame owner, CandleStore candleStore, Runnable onComplete) {
        FetchDataDialog dialog = new FetchDataDialog(owner, candleStore, onComplete);
        dialog.setVisible(true);
    }
}
