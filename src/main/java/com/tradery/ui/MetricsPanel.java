package com.tradery.ui;

import com.tradery.model.PerformanceMetrics;

import javax.swing.*;
import java.awt.*;

/**
 * Panel displaying backtest performance metrics.
 * Shows key stats like win rate, profit factor, Sharpe ratio, etc.
 */
public class MetricsPanel extends JPanel {

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
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(Color.WHITE);
        setOpaque(true);

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
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
        JLabel title = new JLabel("Performance Metrics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(Color.GRAY);

        JPanel gridPanel = new JPanel(new GridLayout(0, 2, 8, 4));
        gridPanel.setOpaque(false);
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

        add(title, BorderLayout.NORTH);
        add(gridPanel, BorderLayout.CENTER);
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
}
