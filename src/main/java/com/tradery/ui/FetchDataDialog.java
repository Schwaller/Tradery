package com.tradery.ui;

import com.tradery.data.AggTradesStore;
import com.tradery.data.CandleStore;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Dialog for fetching new data from Binance.
 * Allows selecting symbol, timeframe/data type, and date range.
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

    private static final String[] DATA_TYPES = {
        "Candles (OHLCV)", "AggTrades (for Delta)"
    };

    private final CandleStore candleStore;
    private final AggTradesStore aggTradesStore;
    private final Runnable onComplete;

    private JComboBox<String> dataTypeCombo;
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JLabel timeframeLabel;
    private JSpinner startYearSpinner;
    private JComboBox<String> startMonthCombo;
    private JSpinner endYearSpinner;
    private JComboBox<String> endMonthCombo;
    private JProgressBar progressBar;
    private JButton fetchButton;
    private JButton cancelButton;

    private SwingWorker<Void, Void> currentWorker;

    public FetchDataDialog(Frame owner, CandleStore candleStore, AggTradesStore aggTradesStore, Runnable onComplete) {
        super(owner, "Fetch Data", true);
        this.candleStore = candleStore;
        this.aggTradesStore = aggTradesStore;
        this.onComplete = onComplete;

        initUI();

        setSize(400, 320);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(40, 40, 45));


        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(40, 40, 45));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Data Type
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel dataTypeLabel = new JLabel("Data Type:");
        dataTypeLabel.setForeground(Color.WHITE);
        formPanel.add(dataTypeLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        dataTypeCombo = new JComboBox<>(DATA_TYPES);
        dataTypeCombo.addActionListener(e -> onDataTypeChanged());
        formPanel.add(dataTypeCombo, gbc);

        // Symbol
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel symbolLabel = new JLabel("Symbol:");
        symbolLabel.setForeground(Color.WHITE);
        formPanel.add(symbolLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        symbolCombo = new JComboBox<>(SYMBOLS);
        formPanel.add(symbolCombo, gbc);

        // Timeframe (only for Candles)
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        timeframeLabel = new JLabel("Timeframe:");
        timeframeLabel.setForeground(Color.WHITE);
        formPanel.add(timeframeLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        formPanel.add(timeframeCombo, gbc);

        // Start date
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
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
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
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
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
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

    private void onDataTypeChanged() {
        boolean isCandles = dataTypeCombo.getSelectedIndex() == 0;
        timeframeLabel.setVisible(isCandles);
        timeframeCombo.setVisible(isCandles);
    }

    private boolean isAggTradesSelected() {
        return dataTypeCombo.getSelectedIndex() == 1;
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

        // Calculate time range
        long startTime = start.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = end.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        boolean fetchAggTrades = isAggTradesSelected();

        // Disable controls
        setControlsEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        String dataTypeLabel = fetchAggTrades ? "aggTrades" : timeframe;
        progressBar.setString("Fetching " + symbol + " " + dataTypeLabel + "...");

        if (fetchAggTrades) {
            // Fetch aggTrades
            aggTradesStore.setProgressCallback(progress -> {
                SwingUtilities.invokeLater(() -> {
                    if (progress.estimatedTotal() > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(progress.percentComplete());
                    }
                    progressBar.setString(progress.message());
                });
            });

            currentWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    aggTradesStore.getAggTrades(symbol, startTime, endTime);
                    return null;
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    aggTradesStore.setProgressCallback(null);
                    setControlsEnabled(true);

                    try {
                        get();
                        JOptionPane.showMessageDialog(FetchDataDialog.this,
                                "AggTrades data fetched successfully!",
                                "Fetch Complete", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        dispose();
                    } catch (Exception e) {
                        if (!aggTradesStore.isFetchCancelled()) {
                            JOptionPane.showMessageDialog(FetchDataDialog.this,
                                    "Fetch failed: " + e.getMessage(),
                                    "Fetch Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    currentWorker = null;
                }
            };
        } else {
            // Fetch candles
            candleStore.setProgressCallback(progress -> {
                SwingUtilities.invokeLater(() -> {
                    if (progress.estimatedTotal() > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(progress.percentComplete());
                    }
                    progressBar.setString(progress.message());
                });
            });

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
        }
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentWorker != null) {
            candleStore.cancelCurrentFetch();
            aggTradesStore.cancelCurrentFetch();
            currentWorker.cancel(true);
        }
        dispose();
    }

    private void setControlsEnabled(boolean enabled) {
        dataTypeCombo.setEnabled(enabled);
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
    public static void show(Frame owner, CandleStore candleStore, AggTradesStore aggTradesStore, Runnable onComplete) {
        FetchDataDialog dialog = new FetchDataDialog(owner, candleStore, aggTradesStore, onComplete);
        dialog.setVisible(true);
    }
}
