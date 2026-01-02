package com.tradery.ui;

import com.tradery.model.PerformanceMetrics;

import javax.swing.*;
import java.awt.*;

/**
 * Panel displaying backtest performance metrics.
 * Shows key stats like win rate, profit factor, Sharpe ratio, etc.
 */
public class MetricsPanel extends JPanel {

    private JProgressBar progressBar;
    private JSpinner capitalSpinner;
    private JComboBox<String> positionSizingCombo;
    private JSpinner feeSpinner;
    private JSpinner slippageSpinner;

    private JLabel totalTradesLabel;
    private JLabel winRateLabel;
    private JLabel profitFactorLabel;
    private JLabel totalReturnLabel;
    private JLabel maxDrawdownLabel;
    private JLabel sharpeRatioLabel;
    private JLabel avgWinLabel;
    private JLabel avgLossLabel;
    private JLabel finalEquityLabel;
    private JLabel totalFeesLabel;

    public MetricsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Progress bar (hidden by default)
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        capitalSpinner = new JSpinner(new SpinnerNumberModel(10000, 100, 10000000, 1000));
        ((JSpinner.NumberEditor) capitalSpinner.getEditor()).getFormat().setGroupingUsed(true);

        positionSizingCombo = new JComboBox<>(new String[]{
            "Fixed 1%", "Fixed 5%", "Fixed 10%",
            "$100 per trade", "$500 per trade", "$1000 per trade",
            "Risk 1% per trade", "Risk 2% per trade",
            "Kelly Criterion", "Volatility-based"
        });

        // Fee: 0.1% default (Binance spot), range 0-1%
        feeSpinner = new JSpinner(new SpinnerNumberModel(0.10, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor feeEditor = new JSpinner.NumberEditor(feeSpinner, "0.00'%'");
        feeSpinner.setEditor(feeEditor);

        // Slippage: 0.05% default, range 0-1%
        slippageSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
        JSpinner.NumberEditor slipEditor = new JSpinner.NumberEditor(slippageSpinner, "0.00'%'");
        slippageSpinner.setEditor(slipEditor);

        totalTradesLabel = createValueLabel("-");
        winRateLabel = createValueLabel("-");
        profitFactorLabel = createValueLabel("-");
        totalReturnLabel = createValueLabel("-");
        maxDrawdownLabel = createValueLabel("-");
        sharpeRatioLabel = createValueLabel("-");
        avgWinLabel = createValueLabel("-");
        avgLossLabel = createValueLabel("-");
        finalEquityLabel = createValueLabel("-");
        totalFeesLabel = createValueLabel("-");
    }

    private JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private void layoutComponents() {
        // Top: Progress bar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.add(progressBar, BorderLayout.CENTER);

        // Capital row
        JPanel capitalPanel = new JPanel(new BorderLayout(8, 0));
        capitalPanel.add(new JLabel("Capital:"), BorderLayout.WEST);
        capitalPanel.add(capitalSpinner, BorderLayout.CENTER);

        // Size row
        JPanel sizePanel = new JPanel(new BorderLayout(8, 0));
        sizePanel.add(new JLabel("Size:"), BorderLayout.WEST);
        sizePanel.add(positionSizingCombo, BorderLayout.CENTER);

        // Fee row
        JPanel feePanel = new JPanel(new BorderLayout(8, 0));
        feePanel.add(new JLabel("Fee:"), BorderLayout.WEST);
        feePanel.add(feeSpinner, BorderLayout.CENTER);

        // Slippage row
        JPanel slippagePanel = new JPanel(new BorderLayout(8, 0));
        slippagePanel.add(new JLabel("Slippage:"), BorderLayout.WEST);
        slippagePanel.add(slippageSpinner, BorderLayout.CENTER);

        // Settings combined
        JPanel settingsPanel = new JPanel(new GridLayout(4, 1, 0, 4));
        settingsPanel.add(capitalPanel);
        settingsPanel.add(sizePanel);
        settingsPanel.add(feePanel);
        settingsPanel.add(slippagePanel);

        // Combine progress and settings
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        topPanel.add(progressPanel, BorderLayout.NORTH);
        topPanel.add(settingsPanel, BorderLayout.SOUTH);

        // Metrics section
        JPanel metricsSection = new JPanel(new BorderLayout(0, 4));

        JLabel title = new JLabel("Performance Metrics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        JPanel gridPanel = new JPanel(new GridLayout(0, 2, 8, 4));
        addMetricRow(gridPanel, "Total Trades", totalTradesLabel);
        addMetricRow(gridPanel, "Win Rate", winRateLabel);
        addMetricRow(gridPanel, "Profit Factor", profitFactorLabel);
        addMetricRow(gridPanel, "Total Return", totalReturnLabel);
        addMetricRow(gridPanel, "Max Drawdown", maxDrawdownLabel);
        addMetricRow(gridPanel, "Sharpe Ratio", sharpeRatioLabel);
        addMetricRow(gridPanel, "Avg Win", avgWinLabel);
        addMetricRow(gridPanel, "Avg Loss", avgLossLabel);
        addMetricRow(gridPanel, "Total Fees", totalFeesLabel);
        addMetricRow(gridPanel, "Final Equity", finalEquityLabel);

        metricsSection.add(title, BorderLayout.NORTH);
        metricsSection.add(gridPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(metricsSection, BorderLayout.CENTER);
    }

    private void addMetricRow(JPanel panel, String name, JLabel valueLabel) {
        JLabel nameLabel = new JLabel(name + ":");
        nameLabel.setForeground(Color.GRAY);
        panel.add(nameLabel);
        panel.add(valueLabel);
    }

    /**
     * Update displayed metrics
     */
    public void updateMetrics(PerformanceMetrics metrics) {
        if (metrics == null) {
            clear();
            return;
        }

        totalTradesLabel.setText(String.valueOf(metrics.totalTrades()));
        winRateLabel.setText(String.format("%.1f%%", metrics.winRate()));
        profitFactorLabel.setText(String.format("%.2f", metrics.profitFactor()));

        String returnPrefix = metrics.totalReturnPercent() >= 0 ? "+" : "";
        totalReturnLabel.setText(String.format("%s%.2f%%", returnPrefix, metrics.totalReturnPercent()));
        totalReturnLabel.setForeground(metrics.totalReturnPercent() >= 0 ?
            new Color(76, 175, 80) : new Color(244, 67, 54));

        maxDrawdownLabel.setText(String.format("-%.2f%%", metrics.maxDrawdownPercent()));
        maxDrawdownLabel.setForeground(new Color(244, 67, 54));

        sharpeRatioLabel.setText(String.format("%.2f", metrics.sharpeRatio()));
        avgWinLabel.setText(String.format("$%.2f", metrics.averageWin()));
        avgWinLabel.setForeground(new Color(76, 175, 80));

        avgLossLabel.setText(String.format("$%.2f", metrics.averageLoss()));
        avgLossLabel.setForeground(new Color(244, 67, 54));

        totalFeesLabel.setText(String.format("$%.2f", metrics.totalFees()));
        totalFeesLabel.setForeground(new Color(244, 67, 54));

        finalEquityLabel.setText(String.format("$%,.2f", metrics.finalEquity()));
    }

    /**
     * Clear all metrics
     */
    public void clear() {
        totalTradesLabel.setText("-");
        winRateLabel.setText("-");
        profitFactorLabel.setText("-");
        totalReturnLabel.setText("-");
        totalReturnLabel.setForeground(Color.GRAY);
        maxDrawdownLabel.setText("-");
        maxDrawdownLabel.setForeground(Color.GRAY);
        sharpeRatioLabel.setText("-");
        avgWinLabel.setText("-");
        avgWinLabel.setForeground(Color.GRAY);
        avgLossLabel.setText("-");
        avgLossLabel.setForeground(Color.GRAY);
        totalFeesLabel.setText("-");
        totalFeesLabel.setForeground(Color.GRAY);
        finalEquityLabel.setText("-");
    }

    public void setProgress(int percentage, String message) {
        SwingUtilities.invokeLater(() -> {
            if (percentage >= 100 || message.equals("Error")) {
                progressBar.setVisible(false);
            } else {
                progressBar.setVisible(true);
                progressBar.setValue(percentage);
                progressBar.setString(message);
            }
        });
    }

    public double getInitialCapital() {
        return ((Number) capitalSpinner.getValue()).doubleValue();
    }

    public String getPositionSizing() {
        return (String) positionSizingCombo.getSelectedItem();
    }

    /**
     * Get fee percentage (e.g., 0.1 for 0.1%)
     */
    public double getFeePercent() {
        return ((Number) feeSpinner.getValue()).doubleValue();
    }

    /**
     * Get slippage percentage (e.g., 0.05 for 0.05%)
     */
    public double getSlippagePercent() {
        return ((Number) slippageSpinner.getValue()).doubleValue();
    }

    /**
     * Get combined fee + slippage as decimal (e.g., 0.0015 for 0.15%)
     */
    public double getTotalCommission() {
        return (getFeePercent() + getSlippagePercent()) / 100.0;
    }
}
