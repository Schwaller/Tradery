package com.tradery.forge.ui;

import com.tradery.core.model.PerformanceMetrics;

import javax.swing.*;
import java.awt.*;

import static com.tradery.forge.ui.UIColors.TRADE_PROFIT;
import static com.tradery.forge.ui.UIColors.TRADE_LOSS;

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
    private JLabel holdingCostsLabel;
    private JLabel maxCapitalUsageLabel;

    public MetricsPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

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
        holdingCostsLabel = createValueLabel("-");
        maxCapitalUsageLabel = createValueLabel("-");
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
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

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
        addMetricRow(gridPanel, "Holding Costs", holdingCostsLabel);
        addMetricRow(gridPanel, "Max Capital", maxCapitalUsageLabel);
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
        totalReturnLabel.setForeground(metrics.totalReturnPercent() >= 0 ? TRADE_PROFIT : TRADE_LOSS);

        maxDrawdownLabel.setText(String.format("-%.2f%%", metrics.maxDrawdownPercent()));
        maxDrawdownLabel.setForeground(TRADE_LOSS);

        sharpeRatioLabel.setText(String.format("%.2f", metrics.sharpeRatio()));
        avgWinLabel.setText(String.format("$%.2f", metrics.averageWin()));
        avgWinLabel.setForeground(TRADE_PROFIT);

        avgLossLabel.setText(String.format("$%.2f", metrics.averageLoss()));
        avgLossLabel.setForeground(TRADE_LOSS);

        totalFeesLabel.setText(String.format("$%.2f", metrics.totalFees()));
        totalFeesLabel.setForeground(TRADE_LOSS);

        // Display holding costs (can be positive = cost, negative = earnings from funding)
        double holdingCosts = metrics.totalHoldingCosts();
        if (holdingCosts == 0) {
            holdingCostsLabel.setText("-");
            holdingCostsLabel.setForeground(Color.GRAY);
        } else if (holdingCosts > 0) {
            holdingCostsLabel.setText(String.format("$%.2f", holdingCosts));
            holdingCostsLabel.setForeground(TRADE_LOSS);  // Red for costs
        } else {
            holdingCostsLabel.setText(String.format("+$%.2f", Math.abs(holdingCosts)));
            holdingCostsLabel.setForeground(TRADE_PROFIT);  // Green for earnings
        }

        maxCapitalUsageLabel.setText(String.format("$%,.0f (%.1f%%)", metrics.maxCapitalDollars(), metrics.maxCapitalUsage()));

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
        holdingCostsLabel.setText("-");
        holdingCostsLabel.setForeground(Color.GRAY);
        maxCapitalUsageLabel.setText("-");
        finalEquityLabel.setText("-");
    }
}
