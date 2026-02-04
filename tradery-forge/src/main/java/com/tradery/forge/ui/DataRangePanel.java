package com.tradery.forge.ui;

import com.tradery.core.model.Strategy;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.ui.base.ConfigurationPanel;
import com.tradery.symbols.ui.SymbolComboBox;

import javax.swing.*;
import javax.swing.text.DateFormatter;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Panel for selecting data range settings: Symbol, Timeframe, Duration, End date.
 * These settings define the data window for backtesting.
 */
public class DataRangePanel extends ConfigurationPanel {

    private static final String[] TIMEFRAMES = {"10s", "15s", "1m", "5m", "15m", "1h", "4h", "1d", "1w"};

    private SymbolComboBox symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JComboBox<String> durationCombo;
    private JSpinner anchorDateSpinner;
    private JButton manageButton;
    private Runnable onManageClicked;

    public DataRangePanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        symbolCombo = new SymbolComboBox(ApplicationContext.getInstance().getSymbolService());

        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");
        timeframeCombo.addActionListener(e -> {
            updateDurationOptions();
            fireChange();
        });

        durationCombo = new JComboBox<>();
        updateDurationOptions();
        durationCombo.addActionListener(e -> fireChange());

        // Anchor date/time spinner (UTC)
        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE);
        anchorDateSpinner = new JSpinner(dateModel);
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(anchorDateSpinner, "yyyy-MM-dd HH:mm");
        ((DateFormatter) dateEditor.getTextField().getFormatter()).setFormat(utcFormat);
        anchorDateSpinner.setEditor(dateEditor);
        anchorDateSpinner.setToolTipText("End date/time for backtest data range (UTC)");

        // Wire up change listeners
        symbolCombo.addActionListener(e -> fireChange());
        anchorDateSpinner.addChangeListener(e -> fireChange());
    }

    private void layoutComponents() {
        // Header with title and manage button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Data Range");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        manageButton = new JButton("Manage...");
        manageButton.setFont(manageButton.getFont().deriveFont(11f));
        manageButton.addActionListener(e -> {
            if (onManageClicked != null) {
                onManageClicked.run();
            }
        });

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(manageButton, BorderLayout.EAST);

        // Settings grid
        JPanel settingsGrid = new JPanel(new GridBagLayout());
        settingsGrid.setOpaque(false);

        GridBagConstraints labelC = new GridBagConstraints();
        labelC.anchor = GridBagConstraints.WEST;
        labelC.insets = new Insets(2, 0, 2, 8);

        GridBagConstraints fieldC = new GridBagConstraints();
        fieldC.anchor = GridBagConstraints.WEST;
        fieldC.fill = GridBagConstraints.HORIZONTAL;
        fieldC.weightx = 1.0;
        fieldC.insets = new Insets(2, 0, 2, 0);

        // Row 0: Symbol
        labelC.gridx = 0; labelC.gridy = 0;
        settingsGrid.add(new JLabel("Symbol:"), labelC);
        fieldC.gridx = 1; fieldC.gridy = 0;
        settingsGrid.add(symbolCombo, fieldC);

        // Row 1: Timeframe
        labelC.gridx = 0; labelC.gridy = 1;
        settingsGrid.add(new JLabel("Timeframe:"), labelC);
        fieldC.gridx = 1; fieldC.gridy = 1;
        settingsGrid.add(timeframeCombo, fieldC);

        // Row 2: Duration
        labelC.gridx = 0; labelC.gridy = 2;
        settingsGrid.add(new JLabel("Duration:"), labelC);
        fieldC.gridx = 1; fieldC.gridy = 2;
        settingsGrid.add(durationCombo, fieldC);

        // Row 3: End date with [>] now button (UTC)
        labelC.gridx = 0; labelC.gridy = 3;
        settingsGrid.add(new JLabel("End (UTC):"), labelC);

        JButton nowBtn = new JButton("\u25B6");
        nowBtn.setToolTipText("Set to current date/time");
        nowBtn.setMargin(new Insets(0, 3, 0, 3));
        nowBtn.setFont(nowBtn.getFont().deriveFont(9f));
        nowBtn.setFocusPainted(false);
        nowBtn.addActionListener(e -> {
            anchorDateSpinner.setValue(new Date());
            fireChange();
        });

        JPanel endRow = new JPanel(new BorderLayout(4, 0));
        endRow.setOpaque(false);
        endRow.add(anchorDateSpinner, BorderLayout.CENTER);
        endRow.add(nowBtn, BorderLayout.EAST);

        fieldC.gridx = 1; fieldC.gridy = 3;
        settingsGrid.add(endRow, fieldC);

        add(headerPanel, BorderLayout.NORTH);
        add(settingsGrid, BorderLayout.CENTER);
    }

    /**
     * Update duration options based on selected timeframe.
     */
    private void updateDurationOptions() {
        String timeframe = (String) timeframeCombo.getSelectedItem();
        String currentDuration = (String) durationCombo.getSelectedItem();

        durationCombo.removeAllItems();

        if (timeframe == null) timeframe = "1h";

        switch (timeframe) {
            case "10s", "15s" -> {
                durationCombo.addItem("1 hour");
                durationCombo.addItem("3 hours");
                durationCombo.addItem("6 hours");
                durationCombo.addItem("12 hours");
                durationCombo.addItem("1 day");
                durationCombo.addItem("3 days");
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("4 weeks");
            }
            case "1m" -> {
                durationCombo.addItem("1 day");
                durationCombo.addItem("3 days");
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("4 weeks");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
            }
            case "5m" -> {
                durationCombo.addItem("1 week");
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
            }
            case "15m" -> {
                durationCombo.addItem("2 weeks");
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
            }
            case "1h" -> {
                durationCombo.addItem("1 month");
                durationCombo.addItem("2 months");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
            }
            case "4h" -> {
                durationCombo.addItem("1 month");
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
            }
            case "1d" -> {
                durationCombo.addItem("3 months");
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
            }
            case "1w" -> {
                durationCombo.addItem("6 months");
                durationCombo.addItem("1 year");
                durationCombo.addItem("2 years");
                durationCombo.addItem("3 years");
                durationCombo.addItem("5 years");
                durationCombo.addItem("10 years");
            }
            default -> {
                durationCombo.addItem("1 year");
            }
        }

        // Try to restore previous selection
        if (currentDuration != null) {
            for (int i = 0; i < durationCombo.getItemCount(); i++) {
                if (currentDuration.equals(durationCombo.getItemAt(i))) {
                    durationCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    /**
     * Set the strategy to load settings from
     */
    public void setStrategy(Strategy strategy) {
        if (strategy == null) return;

        setSuppressChangeEvents(true);
        try {
            symbolCombo.setSelection(strategy.getExchange(), strategy.getSymbolMarket(), strategy.getSymbol());
            timeframeCombo.setSelectedItem(strategy.getTimeframe());
            updateDurationOptions();
            durationCombo.setSelectedItem(strategy.getDuration());

            Long anchorDate = strategy.getBacktestSettings().getAnchorDate();
            anchorDateSpinner.setValue(anchorDate != null ? new Date(anchorDate) : new Date());
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;

        strategy.setExchange(symbolCombo.getExchange());
        strategy.setSymbolMarket(symbolCombo.getSymbolMarket());
        strategy.setSymbol(symbolCombo.getSelectedSymbol());
        strategy.setTimeframe((String) timeframeCombo.getSelectedItem());
        strategy.setDuration((String) durationCombo.getSelectedItem());

        Date anchorDate = (Date) anchorDateSpinner.getValue();
        strategy.getBacktestSettings().setAnchorDate(anchorDate.getTime());
    }

    // Getters for direct access

    public String getSymbol() {
        return symbolCombo.getSelectedSymbol();
    }

    public String getTimeframe() {
        return (String) timeframeCombo.getSelectedItem();
    }

    public String getDuration() {
        return (String) durationCombo.getSelectedItem();
    }

    public Long getAnchorDate() {
        Date date = (Date) anchorDateSpinner.getValue();
        return date.getTime();
    }

    public void setAnchorDate(Long timestamp) {
        if (timestamp != null) {
            anchorDateSpinner.setValue(new Date(timestamp));
        }
    }

    public void setOnManageClicked(Runnable callback) {
        this.onManageClicked = callback;
    }
}
