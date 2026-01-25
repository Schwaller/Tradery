package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.AggTradesStore;
import com.tradery.forge.data.BinanceClient;
import com.tradery.forge.data.BinanceVisionClient;
import com.tradery.forge.data.BinanceVisionClient.VisionDataType;
import com.tradery.forge.data.BinanceVisionClient.VisionProgress;
import com.tradery.forge.data.PremiumIndexStore;
import com.tradery.forge.data.sqlite.SqliteDataStore;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dialog for fetching new data from Binance.
 * Allows selecting symbol, timeframe/data type, and date range.
 *
 * Automatically uses Binance Vision (bulk downloads) for large data requests
 * and REST API for small requests or recent data.
 */
public class FetchDataDialog extends JDialog {

    // Use Vision when estimated API calls exceed this threshold
    // API returns max 1000 candles per request, so 10 calls = 10,000 candles
    private static final int VISION_THRESHOLD_API_CALLS = 10;

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT",
        "AVAXUSDT", "LINKUSDT", "ATOMUSDT", "UNIUSDT", "XLMUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    private static final String[] DATA_TYPES = {
        "Candles (OHLCV)", "AggTrades (for Delta)", "Premium Index"
    };

    private final SqliteDataStore dataStore;
    private final AggTradesStore aggTradesStore;
    private final PremiumIndexStore premiumIndexStore;
    private final BinanceVisionClient visionClient;
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
    private AtomicBoolean visionCancelled;
    private boolean isFetching = false;
    private boolean suppressSelectionRestart = false;

    public FetchDataDialog(Frame owner, SqliteDataStore dataStore, AggTradesStore aggTradesStore,
                           PremiumIndexStore premiumIndexStore, Runnable onComplete) {
        super(owner, "Fetch Data", true);
        this.dataStore = dataStore;
        this.aggTradesStore = aggTradesStore;
        this.premiumIndexStore = premiumIndexStore;
        this.visionClient = ApplicationContext.getInstance().getBinanceVisionClient();
        this.onComplete = onComplete;

        initUI();
        setupSelectionListeners();

        setSize(400, 360);
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Data Type
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel dataTypeLabel = new JLabel("Data Type:");
        formPanel.add(dataTypeLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        dataTypeCombo = new JComboBox<>(DATA_TYPES);
        dataTypeCombo.addActionListener(e -> onDataTypeChanged());
        formPanel.add(dataTypeCombo, gbc);

        // Symbol
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel symbolLabel = new JLabel("Symbol:");
        formPanel.add(symbolLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        symbolCombo = new JComboBox<>(SYMBOLS);
        formPanel.add(symbolCombo, gbc);

        // Timeframe (only for Candles)
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        timeframeLabel = new JLabel("Timeframe:");
        formPanel.add(timeframeLabel, gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        formPanel.add(timeframeCombo, gbc);

        // Start date
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel startLabel = new JLabel("From:");
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

    /**
     * Setup listeners on all selection controls to auto-restart fetch when selection changes.
     */
    private void setupSelectionListeners() {
        // Listener that restarts fetch if one is in progress
        Runnable onSelectionChanged = () -> {
            if (isFetching && !suppressSelectionRestart) {
                // Cancel current fetch and restart with new selection
                restartFetch();
            }
        };

        // Add listeners to all selection controls
        dataTypeCombo.addActionListener(e -> onSelectionChanged.run());
        symbolCombo.addActionListener(e -> onSelectionChanged.run());
        timeframeCombo.addActionListener(e -> onSelectionChanged.run());
        startMonthCombo.addActionListener(e -> onSelectionChanged.run());
        endMonthCombo.addActionListener(e -> onSelectionChanged.run());
        startYearSpinner.addChangeListener(e -> onSelectionChanged.run());
        endYearSpinner.addChangeListener(e -> onSelectionChanged.run());
    }

    /**
     * Cancel current fetch and immediately restart with new selection.
     */
    private void restartFetch() {
        // Cancel current operation
        cancelCurrentFetch();

        // Small delay to let cancellation propagate, then restart
        Timer restartTimer = new Timer(100, e -> {
            if (!isFetching) {
                startFetch();
            }
        });
        restartTimer.setRepeats(false);
        restartTimer.start();
    }

    /**
     * Cancel the current fetch operation without closing the dialog.
     */
    private void cancelCurrentFetch() {
        if (currentWorker != null) {
            // Cancel Vision downloads
            if (visionCancelled != null) {
                visionCancelled.set(true);
            }
            // Cancel API fetches
            aggTradesStore.cancelCurrentFetch();
            currentWorker.cancel(true);
            currentWorker = null;
        }
        isFetching = false;
    }

    private void onDataTypeChanged() {
        // Show timeframe for Candles (0) and Premium Index (2), hide for AggTrades (1)
        boolean needsTimeframe = dataTypeCombo.getSelectedIndex() != 1;
        timeframeLabel.setVisible(needsTimeframe);
        timeframeCombo.setVisible(needsTimeframe);
    }

    private boolean isAggTradesSelected() {
        return dataTypeCombo.getSelectedIndex() == 1;
    }

    private boolean isPremiumIndexSelected() {
        return dataTypeCombo.getSelectedIndex() == 2;
    }

    /**
     * Determine if Vision bulk download should be used based on estimated data volume.
     * Vision is much faster for large downloads but has overhead for small ones.
     */
    private boolean shouldUseVision(long startTime, long endTime, String timeframe, boolean isAggTrades) {
        long durationMs = endTime - startTime;
        long durationHours = durationMs / (1000 * 60 * 60);
        long durationDays = durationHours / 24;

        // AggTrades are massive - a single day can have 500K+ trades
        // Always use Vision for aggTrades if >= 3 days (would be millions of API calls)
        if (isAggTrades) {
            // AggTrades: ~10,000-50,000 trades/hour for BTCUSDT
            // Even 1 day = 240K-1.2M trades = 240-1200 API calls
            // Use Vision for anything >= 3 days
            return durationDays >= 3;
        }

        // For candles, estimate based on timeframe
        long estimatedCandles = switch (timeframe) {
            case "1m" -> durationHours * 60;      // 60 candles/hour
            case "3m" -> durationHours * 20;      // 20 candles/hour
            case "5m" -> durationHours * 12;      // 12 candles/hour
            case "15m" -> durationHours * 4;      // 4 candles/hour
            case "30m" -> durationHours * 2;      // 2 candles/hour
            case "1h" -> durationHours;           // 1 candle/hour
            case "2h" -> durationHours / 2;
            case "4h" -> durationHours / 4;
            case "6h" -> durationHours / 6;
            case "8h" -> durationHours / 8;
            case "12h" -> durationHours / 12;
            case "1d" -> durationHours / 24;
            case "3d" -> durationHours / 72;
            case "1w" -> durationHours / 168;
            default -> durationHours;             // Default to 1h
        };

        // API returns max 1000 records per request
        long estimatedApiCalls = (estimatedCandles + 999) / 1000;

        // Use Vision if we'd need more than threshold API calls
        // Also require at least 1 complete month for Vision to be worthwhile
        boolean exceedsThreshold = estimatedApiCalls > VISION_THRESHOLD_API_CALLS;
        boolean hasCompleteMonth = durationDays >= 28; // ~1 month

        return exceedsThreshold && hasCompleteMonth;
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
        boolean fetchPremium = isPremiumIndexSelected();

        // Estimate API calls needed and decide whether to use Vision
        boolean useVision = shouldUseVision(startTime, endTime, timeframe, fetchAggTrades);

        // Mark as fetching (controls stay enabled for quick switching)
        isFetching = true;
        fetchButton.setText("Restart");
        cancelButton.setText("Stop");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        String dataTypeLabel = fetchAggTrades ? "aggTrades" : (fetchPremium ? "premium " + timeframe : timeframe);
        String methodLabel = useVision ? " (Vision bulk download)" : "";
        progressBar.setString("Fetching " + symbol + " " + dataTypeLabel + methodLabel + "...");

        // Initialize cancellation flag for Vision downloads
        visionCancelled = new AtomicBoolean(false);

        if (useVision) {
            // Use Vision for large date ranges
            startVisionFetch(symbol, timeframe, start, end, fetchAggTrades, fetchPremium, startTime, endTime);
        } else {
            // Use API for small date ranges
            startApiFetch(symbol, timeframe, fetchAggTrades, fetchPremium, startTime, endTime);
        }
    }

    /**
     * Start fetch using Binance Vision bulk downloads.
     * Much faster for large date ranges (10-100x faster than API).
     */
    private void startVisionFetch(String symbol, String timeframe, YearMonth start, YearMonth end,
                                   boolean fetchAggTrades, boolean fetchPremium,
                                   long startTime, long endTime) {

        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Determine the last complete month (Vision may not have current month)
                YearMonth lastCompleteMonth = BinanceVisionClient.getLastCompleteMonth();
                YearMonth visionEnd = end.isAfter(lastCompleteMonth) ? lastCompleteMonth : end;

                // Progress callback
                java.util.function.Consumer<VisionProgress> progressCallback = progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (progress.totalMonths() > 0) {
                            progressBar.setIndeterminate(false);
                            progressBar.setValue(progress.percentComplete());
                        }
                        progressBar.setString(progress.status() + " (" + progress.recordsInserted() + " records)");
                    });
                };

                if (fetchAggTrades) {
                    // Download aggTrades via Vision
                    visionClient.downloadAggTrades(symbol, start, visionEnd, visionCancelled, progressCallback);
                } else if (fetchPremium) {
                    // Download premium index via Vision
                    visionClient.downloadPremiumIndex(symbol, timeframe, start, visionEnd, visionCancelled, progressCallback);
                } else {
                    // Download klines via Vision
                    visionClient.downloadKlines(symbol, timeframe, start, visionEnd, visionCancelled, progressCallback);
                }

                // If we need current month data, backfill with API
                if (end.isAfter(lastCompleteMonth) && !visionCancelled.get()) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(true);
                        progressBar.setString("Backfilling recent data via API...");
                    });

                    // Calculate the gap to fill
                    YearMonth currentMonth = YearMonth.now();
                    long gapStart = currentMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

                    if (fetchAggTrades) {
                        aggTradesStore.getAggTrades(symbol, gapStart, endTime);
                    } else if (fetchPremium) {
                        premiumIndexStore.getPremiumIndex(symbol, timeframe, gapStart, endTime);
                    } else {
                        // TODO: API backfill for recent gap needs BinanceClient integration
                        // For now, Vision downloads complete months; gap is covered by next run
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                isFetching = false;
                progressBar.setVisible(false);
                resetButtonState();

                try {
                    get();
                    if (!visionCancelled.get()) {
                        JOptionPane.showMessageDialog(FetchDataDialog.this,
                                "Data fetched successfully via Vision bulk download!",
                                "Fetch Complete", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        dispose();
                    }
                } catch (Exception e) {
                    if (!visionCancelled.get()) {
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

    /**
     * Start fetch using REST API (for small date ranges).
     */
    private void startApiFetch(String symbol, String timeframe, boolean fetchAggTrades,
                                boolean fetchPremium, long startTime, long endTime) {

        if (fetchPremium) {
            // Fetch premium index
            currentWorker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    premiumIndexStore.getPremiumIndex(symbol, timeframe, startTime, endTime);
                    return null;
                }

                @Override
                protected void done() {
                    isFetching = false;
                    progressBar.setVisible(false);
                    resetButtonState();

                    try {
                        get();
                        JOptionPane.showMessageDialog(FetchDataDialog.this,
                                "Premium Index data fetched successfully!",
                                "Fetch Complete", JOptionPane.INFORMATION_MESSAGE);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        dispose();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(FetchDataDialog.this,
                                "Fetch failed: " + e.getMessage(),
                                "Fetch Error", JOptionPane.ERROR_MESSAGE);
                    }

                    currentWorker = null;
                }
            };
        } else if (fetchAggTrades) {
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
                    isFetching = false;
                    progressBar.setVisible(false);
                    aggTradesStore.setProgressCallback(null);
                    resetButtonState();

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
            // For small requests, still use Vision for consistency
            // The Vision client will skip already-downloaded months
            JOptionPane.showMessageDialog(FetchDataDialog.this,
                "Please select a larger date range to use Vision bulk download.\n" +
                "Vision is now the primary method for fetching candle data.",
                "Use Vision", JOptionPane.INFORMATION_MESSAGE);
            isFetching = false;
            progressBar.setVisible(false);
            resetButtonState();
            return;
        }
        currentWorker.execute();
    }

    private void onCancel() {
        if (currentWorker != null) {
            // Cancel Vision downloads
            if (visionCancelled != null) {
                visionCancelled.set(true);
            }
            // Cancel API fetches
            aggTradesStore.cancelCurrentFetch();
            currentWorker.cancel(true);
        }
        dispose();
    }

    private void resetButtonState() {
        fetchButton.setText("Fetch Data");
        cancelButton.setText("Cancel");
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
    public static void show(Frame owner, SqliteDataStore dataStore, AggTradesStore aggTradesStore,
                            PremiumIndexStore premiumIndexStore, Runnable onComplete) {
        FetchDataDialog dialog = new FetchDataDialog(owner, dataStore, aggTradesStore, premiumIndexStore, onComplete);
        dialog.setVisible(true);
    }
}
