package com.tradery.ui;

import com.tradery.model.Candle;
import com.tradery.model.Trade;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Window showing comprehensive trade details with tree-like DCA grouping.
 */
public class TradeDetailsWindow extends JDialog {

    private JTable table;
    private TreeDetailedTableModel tableModel;
    private final List<Trade> trades;
    private final List<Candle> candles;
    private final String strategyName;

    // Detail panel components
    private JPanel detailPanel;
    private JPanel detailContentPanel;
    private JScrollPane detailScrollPane;

    // P&L progression chart
    private JPanel progressionChartPanel;
    private Trade selectedTradeForChart;


    public TradeDetailsWindow(Frame parent, List<Trade> trades, List<Candle> candles, String strategyName) {
        super(parent, "Trade Details - " + strategyName, false);
        this.trades = trades != null ? trades : new ArrayList<>();
        this.candles = candles != null ? candles : new ArrayList<>();
        this.strategyName = strategyName;

        // Integrated title bar look (macOS)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        initializeComponents();
        layoutComponents();

        setSize(1400, 700);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        tableModel = new TreeDetailedTableModel(trades);
        table = new JTable(tableModel);

        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Column widths
        int[] widths = {40, 50, 45, 130, 130, 85, 85, 70, 70, 75, 55, 60, 65, 55, 55, 75, 80, 60, 80, 75};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Renderers
        table.getColumnModel().getColumn(0).setCellRenderer(new TreeCellRenderer(tableModel));           // #
        table.getColumnModel().getColumn(1).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER));  // Entries
        table.getColumnModel().getColumn(2).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER));  // Side
        table.getColumnModel().getColumn(3).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Time
        table.getColumnModel().getColumn(4).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Time
        table.getColumnModel().getColumn(5).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Entry Price
        table.getColumnModel().getColumn(6).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Exit Price
        table.getColumnModel().getColumn(7).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Quantity
        table.getColumnModel().getColumn(8).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));   // Value
        table.getColumnModel().getColumn(9).setCellRenderer(new PnlCellRenderer(tableModel));   // P&L
        table.getColumnModel().getColumn(10).setCellRenderer(new PnlCellRenderer(tableModel));  // Return
        table.getColumnModel().getColumn(11).setCellRenderer(new MfeCellRenderer(tableModel));  // MFE
        table.getColumnModel().getColumn(12).setCellRenderer(new MaeCellRenderer(tableModel));  // MAE
        table.getColumnModel().getColumn(13).setCellRenderer(new CaptureCellRenderer(tableModel));  // Capture
        table.getColumnModel().getColumn(14).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));  // Duration
        table.getColumnModel().getColumn(15).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.RIGHT));  // Commission
        table.getColumnModel().getColumn(16).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER)); // Exit Reason
        table.getColumnModel().getColumn(17).setCellRenderer(new TradeRowRenderer(tableModel, SwingConstants.CENTER)); // Zone
        table.getColumnModel().getColumn(18).setCellRenderer(new BetterContextCellRenderer(tableModel));  // Better Entry
        table.getColumnModel().getColumn(19).setCellRenderer(new BetterContextCellRenderer(tableModel));  // Better Exit

        // Click handling for expand/collapse
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    TableRow tableRow = tableModel.getRowAt(row);
                    if (tableRow.isGroup && e.getX() < 20) {
                        tableModel.toggleExpand(row);
                    }
                }
            }
        });

        // Selection listener to update detail panel and chart
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0) {
                    TableRow tableRow = tableModel.getRowAt(selectedRow);
                    updateDetailPanel(tableRow);
                    // Update progression chart with selected trade
                    if (tableRow.singleTrade != null) {
                        selectedTradeForChart = tableRow.singleTrade;
                    } else if (tableRow.trades != null && !tableRow.trades.isEmpty()) {
                        // For groups, use the first trade or aggregate
                        selectedTradeForChart = tableRow.trades.get(0);
                    }
                    progressionChartPanel.repaint();
                } else {
                    clearDetailPanel();
                    selectedTradeForChart = null;
                    progressionChartPanel.repaint();
                }
            }
        });

        // Create detail panel
        detailPanel = createDetailPanel();

        // Create P&L progression chart panel
        progressionChartPanel = createProgressionChartPanel();
    }

    private JPanel createDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        panel.setPreferredSize(new Dimension(300, 0));

        detailContentPanel = new JPanel();
        detailContentPanel.setLayout(new BoxLayout(detailContentPanel, BoxLayout.Y_AXIS));
        detailContentPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        // Empty state
        JLabel emptyLabel = new JLabel("Select a trade to view details");
        emptyLabel.setForeground(new Color(120, 120, 120));
        emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(emptyLabel);

        detailScrollPane = new JScrollPane(detailContentPanel);
        detailScrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));
        detailScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(detailScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void updateDetailPanel(TableRow row) {
        if (row == null) {
            clearDetailPanel();
            return;
        }

        detailContentPanel.removeAll();
        SimpleDateFormat df = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        if (row.isGroup) {
            buildGroupDetail(row, df);
        } else {
            buildTradeDetail(row, df);
        }

        detailContentPanel.revalidate();
        detailContentPanel.repaint();
        SwingUtilities.invokeLater(() -> detailScrollPane.getVerticalScrollBar().setValue(0));
    }

    private void buildGroupDetail(TableRow row, SimpleDateFormat df) {
        // Header
        addHeader("DCA GROUP", new Color(100, 140, 200));
        addSpacer(8);

        // Summary metrics in a highlight box
        JPanel metricsBox = createMetricsBox(row);
        metricsBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(metricsBox);
        addSpacer(12);

        // Position details
        addSection("Position");
        addRow("Side", row.getSide().toUpperCase(), row.getSide().equals("long") ? new Color(76, 175, 80) : new Color(244, 67, 54));
        addRow("Entries", String.valueOf(row.trades.size()), null);
        addRow("Total Qty", String.format("%.6f", row.getTotalQuantity()), null);
        addRow("Total Value", String.format("$%,.2f", row.getTotalValue()), null);
        addRow("Avg Entry", "$" + formatPrice(row.getAvgEntryPrice()), null);
        if (row.getExitPrice() != null) {
            addRow("Exit Price", "$" + formatPrice(row.getExitPrice()), null);
        }
        addSpacer(12);

        // Exit info
        addSection("Exit");
        addRow("Reason", formatExitReason(row.getExitReason()), null);
        if (row.getExitZone() != null) {
            addRow("Zone", row.getExitZone(), null);
        }
        addRow("Duration", row.getDuration() + " bars", null);
        addRow("Commission", String.format("$%.2f", row.getTotalCommission()), null);
    }

    private void buildTradeDetail(TableRow row, SimpleDateFormat df) {
        Trade t = row.singleTrade;
        boolean isRejected = row.isRejected();

        // Header
        if (isRejected) {
            addHeader("REJECTED", new Color(150, 150, 150));
            addSpacer(4);
            JLabel note = new JLabel("<html><i>Signal fired but no capital available</i></html>");
            note.setFont(note.getFont().deriveFont(10f));
            note.setForeground(new Color(120, 120, 120));
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailContentPanel.add(note);
        } else {
            Color headerColor = t.pnl() != null && t.pnl() >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54);
            addHeader(t.pnl() != null && t.pnl() >= 0 ? "WINNER" : "LOSER", headerColor);

            // Big P&L display
            addSpacer(8);
            JPanel metricsBox = createTradeMetricsBox(t);
            metricsBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailContentPanel.add(metricsBox);

            // Mini candlestick chart with context bars
            if (!candles.isEmpty() && t.exitBar() != null) {
                addSpacer(12);
                JPanel chartPanel = createMiniChart(t);
                if (chartPanel != null) {
                    chartPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    detailContentPanel.add(chartPanel);
                }
            }
        }
        addSpacer(12);

        // Entry section
        addSection("Entry");
        addRow("Time", df.format(new Date(t.entryTime())), null);
        addRow("Bar", String.valueOf(t.entryBar()), null);
        addRow("Price", "$" + formatPrice(t.entryPrice()), null);
        addRow("Side", t.side().toUpperCase(), t.side().equals("long") ? new Color(76, 175, 80) : new Color(244, 67, 54));
        if (!isRejected) {
            addRow("Quantity", String.format("%.6f", t.quantity()), null);
            addRow("Value", String.format("$%,.2f", t.value()), null);
        }

        if (!isRejected && t.exitTime() != null) {
            addSpacer(12);

            // Exit section
            addSection("Exit");
            addRow("Time", df.format(new Date(t.exitTime())), null);
            addRow("Bar", String.valueOf(t.exitBar()), null);
            addRow("Price", "$" + formatPrice(t.exitPrice()), null);
            addRow("Reason", formatExitReason(t.exitReason()), null);
            if (t.exitZone() != null) {
                addRow("Zone", t.exitZone(), null);
            }
            addRow("Duration", t.duration() + " bars", null);
            if (t.commission() != null) {
                addRow("Commission", String.format("$%.2f", t.commission()), null);
            }

            // Analytics section
            if (t.mfe() != null || t.mae() != null) {
                addSpacer(12);
                addSection("Analytics");
                if (t.mfe() != null) {
                    addRow("MFE", String.format("+%.2f%%", t.mfe()), new Color(76, 175, 80));
                    addRow("  at bar", String.valueOf(t.mfeBar()), new Color(100, 100, 100));
                }
                if (t.mae() != null) {
                    addRow("MAE", String.format("%.2f%%", t.mae()), new Color(244, 67, 54));
                    addRow("  at bar", String.valueOf(t.maeBar()), new Color(100, 100, 100));
                }
                if (t.captureRatio() != null) {
                    Color captureColor = t.captureRatio() >= 0.7 ? new Color(76, 175, 80) :
                                         t.captureRatio() >= 0.4 ? new Color(255, 193, 7) : new Color(244, 67, 54);
                    addRow("Capture", String.format("%.0f%%", t.captureRatio() * 100), captureColor);
                }
            }
        }

        // Phases
        if (t.activePhasesAtEntry() != null && !t.activePhasesAtEntry().isEmpty()) {
            addSpacer(12);
            addSection("Phases at Entry");
            for (String phase : t.activePhasesAtEntry()) {
                addTag(phase, new Color(70, 130, 180));
            }
        }

        // Indicators (collapsible-style, showing key ones)
        if (t.entryIndicators() != null && !t.entryIndicators().isEmpty()) {
            addSpacer(12);
            addSection("Entry Indicators");
            t.entryIndicators().forEach((k, v) -> {
                addRow(k, formatIndicatorValue(k, v), new Color(140, 140, 140));
            });
        }
    }

    private JPanel createMetricsBox(TableRow row) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 85)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        double pnl = row.getTotalPnl();
        Color pnlColor = pnl >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54);

        JLabel pnlLabel = new JLabel(String.format("%+.2f", pnl));
        pnlLabel.setFont(pnlLabel.getFont().deriveFont(Font.BOLD, 22f));
        pnlLabel.setForeground(pnlColor);
        pnlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel returnLabel = new JLabel(String.format("%+.2f%% return", row.getAvgPnlPercent()));
        returnLabel.setFont(returnLabel.getFont().deriveFont(12f));
        returnLabel.setForeground(pnlColor);
        returnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.add(pnlLabel);
        box.add(returnLabel);

        // MFE/MAE row
        if (row.getMfe() != null || row.getMae() != null) {
            JPanel mfeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            mfeRow.setOpaque(false);
            mfeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (row.getMfe() != null) {
                JLabel mfe = new JLabel(String.format("MFE +%.1f%%", row.getMfe()));
                mfe.setFont(mfe.getFont().deriveFont(10f));
                mfe.setForeground(new Color(100, 160, 100));
                mfeRow.add(mfe);
            }
            if (row.getMae() != null) {
                JLabel mae = new JLabel(String.format("MAE %.1f%%", row.getMae()));
                mae.setFont(mae.getFont().deriveFont(10f));
                mae.setForeground(new Color(180, 100, 100));
                mfeRow.add(mae);
            }
            box.add(mfeRow);
        }

        return box;
    }

    private JPanel createTradeMetricsBox(Trade t) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 85)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        double pnl = t.pnl() != null ? t.pnl() : 0;
        Color pnlColor = pnl >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54);

        JLabel pnlLabel = new JLabel(String.format("%+.2f", pnl));
        pnlLabel.setFont(pnlLabel.getFont().deriveFont(Font.BOLD, 22f));
        pnlLabel.setForeground(pnlColor);
        pnlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel returnLabel = new JLabel(String.format("%+.2f%% return", t.pnlPercent() != null ? t.pnlPercent() : 0));
        returnLabel.setFont(returnLabel.getFont().deriveFont(12f));
        returnLabel.setForeground(pnlColor);
        returnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        box.add(pnlLabel);
        box.add(returnLabel);

        // MFE/MAE row
        if (t.mfe() != null || t.mae() != null) {
            JPanel mfeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            mfeRow.setOpaque(false);
            mfeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (t.mfe() != null) {
                JLabel mfe = new JLabel(String.format("MFE +%.1f%%", t.mfe()));
                mfe.setFont(mfe.getFont().deriveFont(10f));
                mfe.setForeground(new Color(100, 160, 100));
                mfeRow.add(mfe);
            }
            if (t.mae() != null) {
                JLabel mae = new JLabel(String.format("MAE %.1f%%", t.mae()));
                mae.setFont(mae.getFont().deriveFont(10f));
                mae.setForeground(new Color(180, 100, 100));
                mfeRow.add(mae);
            }
            if (t.captureRatio() != null) {
                JLabel capture = new JLabel(String.format("Capture %.0f%%", t.captureRatio() * 100));
                capture.setFont(capture.getFont().deriveFont(10f));
                Color captureColor = t.captureRatio() >= 0.7 ? new Color(100, 160, 100) :
                                     t.captureRatio() >= 0.4 ? new Color(180, 160, 80) : new Color(180, 100, 100);
                capture.setForeground(captureColor);
                mfeRow.add(capture);
            }
            box.add(mfeRow);
        }

        return box;
    }

    private JPanel createMiniChart(Trade t) {
        if (candles.isEmpty() || t.exitBar() == null) return null;

        int entryBar = t.entryBar();
        int exitBar = t.exitBar();
        int contextBars = Trade.CONTEXT_BARS;
        int startBar = Math.max(0, entryBar - contextBars);
        int endBar = Math.min(candles.size() - 1, exitBar + contextBars);

        if (startBar >= endBar) return null;

        // Extract candles for the range
        List<Candle> chartCandles = candles.subList(startBar, endBar + 1);
        if (chartCandles.isEmpty()) return null;

        // Find price range from candle highs/lows
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Candle c : chartCandles) {
            minPrice = Math.min(minPrice, c.low());
            maxPrice = Math.max(maxPrice, c.high());
        }
        double priceRange = maxPrice - minPrice;
        if (priceRange <= 0) return null;

        // Add padding to price range
        minPrice -= priceRange * 0.08;
        maxPrice += priceRange * 0.08;
        priceRange = maxPrice - minPrice;

        final double fMinPrice = minPrice;
        final double fPriceRange = priceRange;
        final int fEntryBar = entryBar - startBar;
        final int fExitBar = exitBar - startBar;
        final Integer fMfeBar = t.mfeBar() != null ? t.mfeBar() - startBar : null;
        final Integer fMaeBar = t.maeBar() != null ? t.maeBar() - startBar : null;
        final boolean isWinner = t.pnl() != null && t.pnl() >= 0;
        final boolean isLong = "long".equalsIgnoreCase(t.side());

        // Calculate ideal trade exit price (MFE price)
        final Double idealExitPrice;
        if (t.mfeBar() != null && t.mfeBar() >= 0 && t.mfeBar() < candles.size()) {
            Candle mfeCandle = candles.get(t.mfeBar());
            idealExitPrice = isLong ? mfeCandle.high() : mfeCandle.low();
        } else {
            idealExitPrice = null;
        }

        JPanel chartPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int margin = 10;
                int chartHeight = h - 2 * margin;

                // Calculate bar width - ensure minimum width for candlesticks
                int totalBars = chartCandles.size();
                int barWidth = Math.max(4, (w - 2 * margin) / totalBars);
                int candleBodyWidth = Math.max(2, barWidth - 2);
                int chartWidth = barWidth * totalBars;
                int offsetX = (w - chartWidth) / 2;

                // Background
                g2.setColor(new Color(30, 30, 35));
                g2.fillRect(0, 0, w, h);

                // Helper to convert price to Y coordinate
                java.util.function.ToIntFunction<Double> priceToY = price ->
                    h - margin - (int) ((price - fMinPrice) / fPriceRange * chartHeight);

                // Draw entry price horizontal line (dotted, subtle)
                if (fEntryBar >= 0 && fEntryBar < chartCandles.size()) {
                    int entryY = priceToY.applyAsInt(t.entryPrice());
                    g2.setColor(new Color(100, 180, 255, 60));
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                    g2.drawLine(offsetX, entryY, offsetX + chartWidth, entryY);
                }

                // Draw ideal trade path (dotted green line from entry to MFE)
                if (idealExitPrice != null && fMfeBar != null && fEntryBar >= 0 && fEntryBar < chartCandles.size() && fMfeBar >= 0 && fMfeBar < chartCandles.size()) {
                    int entryX = offsetX + fEntryBar * barWidth + barWidth / 2;
                    int entryY = priceToY.applyAsInt(t.entryPrice());
                    int mfeX = offsetX + fMfeBar * barWidth + barWidth / 2;
                    int mfeY = priceToY.applyAsInt(idealExitPrice);

                    g2.setColor(new Color(76, 175, 80, 120));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
                    g2.drawLine(entryX, entryY, mfeX, mfeY);

                    // Small diamond at ideal exit
                    int[] xp = {mfeX - 5, mfeX, mfeX + 5, mfeX};
                    int[] yp = {mfeY, mfeY - 5, mfeY, mfeY + 5};
                    g2.setColor(new Color(76, 175, 80, 180));
                    g2.fillPolygon(xp, yp, 4);
                }

                // Draw candlesticks
                g2.setStroke(new BasicStroke(1f));
                for (int i = 0; i < chartCandles.size(); i++) {
                    Candle c = chartCandles.get(i);
                    int x = offsetX + i * barWidth + barWidth / 2;

                    int highY = priceToY.applyAsInt(c.high());
                    int lowY = priceToY.applyAsInt(c.low());
                    int openY = priceToY.applyAsInt(c.open());
                    int closeY = priceToY.applyAsInt(c.close());

                    boolean bullish = c.close() >= c.open();
                    Color candleColor = bullish ? new Color(76, 175, 80) : new Color(244, 67, 54);

                    // Dim candles outside trade
                    if (i < fEntryBar || i > fExitBar) {
                        candleColor = new Color(candleColor.getRed(), candleColor.getGreen(), candleColor.getBlue(), 100);
                    }

                    // Draw wick
                    g2.setColor(candleColor);
                    g2.drawLine(x, highY, x, lowY);

                    // Draw body
                    int bodyTop = Math.min(openY, closeY);
                    int bodyHeight = Math.max(1, Math.abs(closeY - openY));
                    int bodyX = x - candleBodyWidth / 2;

                    if (bullish) {
                        g2.fillRect(bodyX, bodyTop, candleBodyWidth, bodyHeight);
                    } else {
                        g2.fillRect(bodyX, bodyTop, candleBodyWidth, bodyHeight);
                    }
                }

                // Entry vertical line
                if (fEntryBar >= 0 && fEntryBar < chartCandles.size()) {
                    int x = offsetX + fEntryBar * barWidth + barWidth / 2;
                    g2.setColor(new Color(100, 180, 255, 80));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(x, margin, x, h - margin);
                }

                // Exit vertical line
                if (fExitBar >= 0 && fExitBar < chartCandles.size()) {
                    int x = offsetX + fExitBar * barWidth + barWidth / 2;
                    Color exitColor = isWinner ? new Color(76, 175, 80, 80) : new Color(244, 67, 54, 80);
                    g2.setColor(exitColor);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(x, margin, x, h - margin);
                }

                // Entry marker (blue circle)
                if (fEntryBar >= 0 && fEntryBar < chartCandles.size()) {
                    int x = offsetX + fEntryBar * barWidth + barWidth / 2;
                    int y = priceToY.applyAsInt(t.entryPrice());
                    g2.setColor(new Color(100, 180, 255));
                    g2.fillOval(x - 5, y - 5, 10, 10);
                    g2.setColor(new Color(255, 255, 255, 200));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(x - 5, y - 5, 10, 10);
                }

                // Exit marker
                if (fExitBar >= 0 && fExitBar < chartCandles.size()) {
                    int x = offsetX + fExitBar * barWidth + barWidth / 2;
                    int y = priceToY.applyAsInt(t.exitPrice());
                    Color exitColor = isWinner ? new Color(76, 175, 80) : new Color(244, 67, 54);
                    g2.setColor(exitColor);
                    g2.fillOval(x - 5, y - 5, 10, 10);
                    g2.setColor(new Color(255, 255, 255, 200));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(x - 5, y - 5, 10, 10);
                }

                // MFE marker (green triangle pointing up for long, down for short)
                if (fMfeBar != null && fMfeBar >= 0 && fMfeBar < chartCandles.size()) {
                    int x = offsetX + fMfeBar * barWidth + barWidth / 2;
                    Candle c = chartCandles.get(fMfeBar);
                    double mfePrice = isLong ? c.high() : c.low();
                    int y = priceToY.applyAsInt(mfePrice);
                    g2.setColor(new Color(76, 175, 80, 200));
                    if (isLong) {
                        int[] xp = {x - 5, x + 5, x};
                        int[] yp = {y + 6, y + 6, y - 4};
                        g2.fillPolygon(xp, yp, 3);
                    } else {
                        int[] xp = {x - 5, x + 5, x};
                        int[] yp = {y - 6, y - 6, y + 4};
                        g2.fillPolygon(xp, yp, 3);
                    }
                }

                // MAE marker (red triangle pointing opposite of MFE)
                if (fMaeBar != null && fMaeBar >= 0 && fMaeBar < chartCandles.size()) {
                    int x = offsetX + fMaeBar * barWidth + barWidth / 2;
                    Candle c = chartCandles.get(fMaeBar);
                    double maePrice = isLong ? c.low() : c.high();
                    int y = priceToY.applyAsInt(maePrice);
                    g2.setColor(new Color(244, 67, 54, 200));
                    if (isLong) {
                        int[] xp = {x - 5, x + 5, x};
                        int[] yp = {y - 6, y - 6, y + 4};
                        g2.fillPolygon(xp, yp, 3);
                    } else {
                        int[] xp = {x - 5, x + 5, x};
                        int[] yp = {y + 6, y + 6, y - 4};
                        g2.fillPolygon(xp, yp, 3);
                    }
                }

                g2.dispose();
            }
        };

        chartPanel.setPreferredSize(new Dimension(280, 120));
        chartPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        chartPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));
        chartPanel.setToolTipText("<html>Entry (blue) → Exit | MFE ▲ | MAE ▼<br/>Dotted green = ideal trade path to MFE</html>");

        return chartPanel;
    }

    /**
     * Creates the P&L progression chart panel showing how the selected trade evolved over time.
     * Entry is normalized to 0%, and each bar shows the unrealized P&L %.
     */
    private JPanel createProgressionChartPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int margin = 50;
                int chartW = w - margin * 2;
                int chartH = h - 40;
                int chartY = 20;

                // Background
                g2.setColor(new Color(30, 30, 35));
                g2.fillRect(0, 0, w, h);

                if (selectedTradeForChart == null || candles.isEmpty() ||
                    selectedTradeForChart.exitBar() == null) {
                    // Empty state
                    g2.setColor(new Color(100, 100, 100));
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                    String msg = "Select a trade to view P&L progression";
                    int msgW = g2.getFontMetrics().stringWidth(msg);
                    g2.drawString(msg, (w - msgW) / 2, h / 2);
                    g2.dispose();
                    return;
                }

                Trade t = selectedTradeForChart;
                int entryBar = t.entryBar();
                int exitBar = t.exitBar();
                double entryPrice = t.entryPrice();
                boolean isLong = "long".equals(t.side());

                if (entryBar >= exitBar || entryBar >= candles.size()) {
                    g2.dispose();
                    return;
                }

                // Calculate P&L % for each bar from entry to exit
                int numBars = exitBar - entryBar + 1;
                double[] pnlPercents = new double[numBars];
                double minPnl = 0;
                double maxPnl = 0;

                for (int i = 0; i < numBars; i++) {
                    int barIdx = entryBar + i;
                    if (barIdx < candles.size()) {
                        Candle c = candles.get(barIdx);
                        double currentPrice = c.close();
                        double pnlPct;
                        if (isLong) {
                            pnlPct = ((currentPrice - entryPrice) / entryPrice) * 100;
                        } else {
                            pnlPct = ((entryPrice - currentPrice) / entryPrice) * 100;
                        }
                        pnlPercents[i] = pnlPct;
                        minPnl = Math.min(minPnl, pnlPct);
                        maxPnl = Math.max(maxPnl, pnlPct);
                    }
                }

                // Add padding to range
                double pnlRange = maxPnl - minPnl;
                if (pnlRange < 1) pnlRange = 1; // Minimum range
                minPnl -= pnlRange * 0.1;
                maxPnl += pnlRange * 0.1;
                pnlRange = maxPnl - minPnl;

                // Draw grid and zero line
                g2.setColor(new Color(50, 50, 55));
                g2.setStroke(new BasicStroke(1f));

                // Zero line
                int zeroY = chartY + (int) ((maxPnl - 0) / pnlRange * chartH);
                if (zeroY >= chartY && zeroY <= chartY + chartH) {
                    g2.setColor(new Color(80, 80, 85));
                    g2.drawLine(margin, zeroY, margin + chartW, zeroY);
                }

                // Draw horizontal grid lines
                g2.setColor(new Color(45, 45, 50));
                int numGridLines = 5;
                for (int i = 0; i <= numGridLines; i++) {
                    int y = chartY + (i * chartH / numGridLines);
                    g2.drawLine(margin, y, margin + chartW, y);

                    // Labels
                    double pnlAtLine = maxPnl - (i * pnlRange / numGridLines);
                    g2.setColor(new Color(100, 100, 100));
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                    String label = String.format("%+.1f%%", pnlAtLine);
                    g2.drawString(label, 5, y + 4);
                    g2.setColor(new Color(45, 45, 50));
                }

                // Draw P&L line
                Path2D.Double path = new Path2D.Double();
                int barWidth = Math.max(2, chartW / numBars);
                boolean first = true;

                for (int i = 0; i < numBars; i++) {
                    int x = margin + (i * chartW / numBars) + barWidth / 2;
                    int y = chartY + (int) ((maxPnl - pnlPercents[i]) / pnlRange * chartH);

                    if (first) {
                        path.moveTo(x, y);
                        first = false;
                    } else {
                        path.lineTo(x, y);
                    }
                }

                // Fill area under/over the line
                Path2D.Double areaPath = new Path2D.Double(path);
                int lastX = margin + ((numBars - 1) * chartW / numBars) + barWidth / 2;
                int firstX = margin + barWidth / 2;
                areaPath.lineTo(lastX, zeroY);
                areaPath.lineTo(firstX, zeroY);
                areaPath.closePath();

                boolean isWinner = t.pnl() != null && t.pnl() >= 0;
                Color areaColor = isWinner ? new Color(76, 175, 80, 40) : new Color(244, 67, 54, 40);
                g2.setColor(areaColor);
                g2.fill(areaPath);

                // Draw the line
                Color lineColor = isWinner ? new Color(76, 175, 80) : new Color(244, 67, 54);
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(path);

                // Entry marker (0%)
                int entryX = margin + barWidth / 2;
                int entryY = chartY + (int) ((maxPnl - 0) / pnlRange * chartH);
                g2.setColor(new Color(100, 180, 255));
                g2.fillOval(entryX - 5, entryY - 5, 10, 10);

                // Exit marker
                int exitX = margin + ((numBars - 1) * chartW / numBars) + barWidth / 2;
                int exitY = chartY + (int) ((maxPnl - pnlPercents[numBars - 1]) / pnlRange * chartH);
                g2.setColor(lineColor);
                g2.fillOval(exitX - 5, exitY - 5, 10, 10);

                // MFE marker
                if (t.mfeBar() != null && t.mfe() != null) {
                    int mfeIdx = t.mfeBar() - entryBar;
                    if (mfeIdx >= 0 && mfeIdx < numBars) {
                        int mfeX = margin + (mfeIdx * chartW / numBars) + barWidth / 2;
                        int mfeY = chartY + (int) ((maxPnl - pnlPercents[mfeIdx]) / pnlRange * chartH);
                        g2.setColor(new Color(76, 175, 80, 180));
                        int[] xp = {mfeX - 5, mfeX + 5, mfeX};
                        int[] yp = {mfeY + 5, mfeY + 5, mfeY - 5};
                        g2.fillPolygon(xp, yp, 3);
                    }
                }

                // MAE marker
                if (t.maeBar() != null && t.mae() != null) {
                    int maeIdx = t.maeBar() - entryBar;
                    if (maeIdx >= 0 && maeIdx < numBars) {
                        int maeX = margin + (maeIdx * chartW / numBars) + barWidth / 2;
                        int maeY = chartY + (int) ((maxPnl - pnlPercents[maeIdx]) / pnlRange * chartH);
                        g2.setColor(new Color(244, 67, 54, 180));
                        int[] xp = {maeX - 5, maeX + 5, maeX};
                        int[] yp = {maeY - 5, maeY - 5, maeY + 5};
                        g2.fillPolygon(xp, yp, 3);
                    }
                }

                // Title
                g2.setColor(new Color(180, 180, 180));
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                String title = String.format("P&L Progression: %s | %d bars | %+.2f%%",
                    isLong ? "LONG" : "SHORT", numBars - 1, t.pnlPercent() != null ? t.pnlPercent() : 0);
                g2.drawString(title, margin, 14);

                // Legend
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                g2.setColor(new Color(100, 180, 255));
                g2.drawString("● Entry", margin + chartW - 150, 14);
                g2.setColor(lineColor);
                g2.drawString("● Exit", margin + chartW - 100, 14);
                g2.setColor(new Color(76, 175, 80));
                g2.drawString("▲ MFE", margin + chartW - 55, 14);

                g2.dispose();
            }
        };

        panel.setPreferredSize(new Dimension(0, 150));
        panel.setMinimumSize(new Dimension(0, 100));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 65)));
        panel.setToolTipText("P&L % progression from entry (0%) to exit");

        return panel;
    }

    private void addHeader(String text, Color color) {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setForeground(color);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(header);
    }

    private void addSection(String title) {
        JLabel section = new JLabel(title.toUpperCase());
        section.setFont(section.getFont().deriveFont(Font.BOLD, 9f));
        section.setForeground(new Color(100, 100, 100));
        section.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(section);
    }

    private void addRow(String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(11f));
        labelComp.setForeground(new Color(140, 140, 140));

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueComp.getFont().deriveFont(11f));
        valueComp.setForeground(valueColor != null ? valueColor : new Color(200, 200, 200));

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);
        detailContentPanel.add(row);
    }

    private void addTag(String text, Color color) {
        JLabel tag = new JLabel(text);
        tag.setFont(tag.getFont().deriveFont(10f));
        tag.setForeground(color);
        tag.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 1),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)
        ));
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(tag);
        addSpacer(2);
    }

    private void addSpacer(int height) {
        detailContentPanel.add(Box.createRigidArea(new Dimension(0, height)));
    }

    private void clearDetailPanel() {
        detailContentPanel.removeAll();
        JLabel emptyLabel = new JLabel("Select a trade to view details");
        emptyLabel.setForeground(new Color(120, 120, 120));
        emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailContentPanel.add(emptyLabel);
        detailContentPanel.revalidate();
        detailContentPanel.repaint();
    }

    private String formatPrice(double price) {
        if (price >= 100) return String.format("%,.2f", price);
        else if (price >= 1) return String.format("%.4f", price);
        else return String.format("%.8f", price);
    }

    private String formatIndicatorValue(String name, double value) {
        if (name.contains("price") || name.equals("close") || name.equals("open") || name.equals("high") || name.equals("low")) {
            return "$" + formatPrice(value);
        } else if (name.contains("volume") || name.contains("VOLUME")) {
            return String.format("%,.0f", value);
        } else if (value >= 1000) {
            return String.format("%,.2f", value);
        } else {
            return String.format("%.4f", value);
        }
    }

    private String formatExitReason(String reason) {
        if (reason == null) return "-";
        return switch (reason) {
            case "signal" -> "Signal";
            case "stop_loss" -> "Stop Loss";
            case "take_profit" -> "Take Profit";
            case "trailing_stop" -> "Trail Stop";
            case "zone_exit" -> "Zone Exit";
            case "rejected" -> "Rejected";
            default -> reason;
        };
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        // Extra top padding for macOS transparent title bar
        contentPane.setBorder(BorderFactory.createEmptyBorder(28, 12, 12, 12));

        // Top panel with title and summary
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));

        // Title label (since macOS title bar is hidden)
        JLabel titleLabel = new JLabel("Trade Details - " + strategyName);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        topPanel.add(titleLabel, BorderLayout.NORTH);

        // Summary header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));

        int totalTrades = 0;
        int winners = 0;
        int losers = 0;
        int rejected = 0;
        double totalPnl = 0;

        for (Trade t : trades) {
            if ("rejected".equals(t.exitReason())) {
                rejected++;
            } else {
                totalTrades++;
                if (t.pnl() != null) {
                    totalPnl += t.pnl();
                    if (t.pnl() > 0) winners++;
                    else if (t.pnl() < 0) losers++;
                }
            }
        }

        double winRate = totalTrades > 0 ? (double) winners / totalTrades * 100 : 0;

        headerPanel.add(createSummaryLabel("Total Trades", String.valueOf(totalTrades)));
        headerPanel.add(createSummaryLabel("Winners", String.valueOf(winners), new Color(76, 175, 80)));
        headerPanel.add(createSummaryLabel("Losers", String.valueOf(losers), new Color(244, 67, 54)));
        headerPanel.add(createSummaryLabel("Win Rate", String.format("%.1f%%", winRate)));
        headerPanel.add(createSummaryLabel("Total P&L", String.format("%+.2f", totalPnl),
            totalPnl >= 0 ? new Color(76, 175, 80) : new Color(244, 67, 54)));
        if (rejected > 0) {
            headerPanel.add(createSummaryLabel("Rejected", String.valueOf(rejected), Color.GRAY));
        }

        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Vertical split: table on top, chart on bottom
        JSplitPane tableChartSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        tableChartSplit.setTopComponent(tableScrollPane);
        tableChartSplit.setBottomComponent(progressionChartPanel);
        tableChartSplit.setDividerLocation(300);
        tableChartSplit.setResizeWeight(0.7);
        tableChartSplit.setDividerSize(4);
        tableChartSplit.setBorder(null);

        // Horizontal split: table+chart | detail panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(tableChartSplit);
        splitPane.setRightComponent(detailPanel);
        splitPane.setDividerLocation(1100);
        splitPane.setResizeWeight(1.0); // Give extra space to table
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        topPanel.add(headerPanel, BorderLayout.CENTER);
        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(splitPane, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
    }

    private JPanel createSummaryLabel(String label, String value) {
        return createSummaryLabel(label, value, null);
    }

    private JPanel createSummaryLabel(String label, String value, Color valueColor) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setFont(labelComponent.getFont().deriveFont(11f));
        labelComponent.setForeground(Color.GRAY);

        JLabel valueComponent = new JLabel(value);
        valueComponent.setFont(valueComponent.getFont().deriveFont(Font.BOLD, 12f));
        if (valueColor != null) {
            valueComponent.setForeground(valueColor);
        }

        panel.add(labelComponent);
        panel.add(valueComponent);
        return panel;
    }

    /**
     * Represents a row in the tree table
     */
    private static class TableRow {
        boolean isGroup;
        boolean isChild;
        boolean expanded;
        int groupIndex;
        List<Trade> trades;
        Trade singleTrade;
        int childIndex;

        static TableRow single(int index, Trade trade) {
            TableRow r = new TableRow();
            r.isGroup = false;
            r.isChild = false;
            r.groupIndex = index;
            r.singleTrade = trade;
            r.trades = List.of(trade);
            return r;
        }

        static TableRow group(int index, List<Trade> trades) {
            TableRow r = new TableRow();
            r.isGroup = true;
            r.isChild = false;
            r.expanded = false;
            r.groupIndex = index;
            r.trades = trades;
            return r;
        }

        static TableRow child(int groupIndex, int childIndex, Trade trade) {
            TableRow r = new TableRow();
            r.isGroup = false;
            r.isChild = true;
            r.groupIndex = groupIndex;
            r.childIndex = childIndex;
            r.singleTrade = trade;
            r.trades = List.of(trade);
            return r;
        }

        double getTotalPnl() {
            return trades.stream().filter(t -> t.pnl() != null).mapToDouble(Trade::pnl).sum();
        }

        double getTotalCommission() {
            return trades.stream().filter(t -> t.commission() != null).mapToDouble(Trade::commission).sum();
        }

        double getTotalQuantity() {
            return trades.stream().mapToDouble(Trade::quantity).sum();
        }

        double getTotalValue() {
            return trades.stream().mapToDouble(Trade::value).sum();
        }

        double getAvgEntryPrice() {
            double totalValue = 0;
            double totalQty = 0;
            for (Trade t : trades) {
                totalValue += t.entryPrice() * t.quantity();
                totalQty += t.quantity();
            }
            return totalQty > 0 ? totalValue / totalQty : 0;
        }

        double getAvgPnlPercent() {
            double avgEntry = getAvgEntryPrice();
            double totalQty = getTotalQuantity();
            double totalPnl = getTotalPnl();
            return avgEntry > 0 ? (totalPnl / (avgEntry * totalQty)) * 100 : 0;
        }

        long getFirstEntryTime() {
            return trades.stream().mapToLong(Trade::entryTime).min().orElse(0);
        }

        Long getExitTime() {
            return trades.isEmpty() ? null : trades.getFirst().exitTime();
        }

        Double getExitPrice() {
            return trades.isEmpty() ? null : trades.getFirst().exitPrice();
        }

        String getSide() {
            return trades.isEmpty() ? "long" : trades.getFirst().side();
        }

        String getExitReason() {
            return trades.isEmpty() ? null : trades.getFirst().exitReason();
        }

        String getExitZone() {
            return trades.isEmpty() ? null : trades.getFirst().exitZone();
        }

        boolean isRejected() {
            return singleTrade != null && "rejected".equals(singleTrade.exitReason());
        }

        Double getMfe() {
            if (singleTrade != null) {
                return singleTrade.mfe();
            }
            // For groups, return the best MFE across all trades
            Double best = null;
            for (Trade t : trades) {
                if (t.mfe() != null && (best == null || t.mfe() > best)) {
                    best = t.mfe();
                }
            }
            return best;
        }

        Double getMae() {
            if (singleTrade != null) {
                return singleTrade.mae();
            }
            // For groups, return the worst MAE across all trades
            Double worst = null;
            for (Trade t : trades) {
                if (t.mae() != null && (worst == null || t.mae() < worst)) {
                    worst = t.mae();
                }
            }
            return worst;
        }

        Double getCaptureRatio() {
            if (singleTrade != null) {
                return singleTrade.captureRatio();
            }
            // For groups, calculate aggregate capture ratio
            Double mfe = getMfe();
            if (mfe == null || mfe <= 0) return null;
            double avgPnl = getAvgPnlPercent();
            return avgPnl / mfe;
        }

        Integer getDuration() {
            if (singleTrade != null) {
                return singleTrade.duration();
            }
            // For groups, return duration from first entry to last exit
            if (trades.isEmpty()) return null;
            int firstEntry = trades.stream().mapToInt(Trade::entryBar).min().orElse(0);
            int lastExit = trades.stream()
                .filter(t -> t.exitBar() != null)
                .mapToInt(Trade::exitBar)
                .max().orElse(firstEntry);
            return lastExit - firstEntry;
        }

        Double getBetterEntryImprovement() {
            if (singleTrade != null) {
                return singleTrade.betterEntryImprovement();
            }
            // For groups, return the best (highest) improvement across all trades
            Double best = null;
            for (Trade t : trades) {
                if (t.betterEntryImprovement() != null && (best == null || t.betterEntryImprovement() > best)) {
                    best = t.betterEntryImprovement();
                }
            }
            return best;
        }

        Double getBetterExitImprovement() {
            if (singleTrade != null) {
                return singleTrade.betterExitImprovement();
            }
            // For groups, return the best (highest) improvement across all trades
            Double best = null;
            for (Trade t : trades) {
                if (t.betterExitImprovement() != null && (best == null || t.betterExitImprovement() > best)) {
                    best = t.betterExitImprovement();
                }
            }
            return best;
        }
    }

    /**
     * Tree-based detailed table model
     */
    private static class TreeDetailedTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
            "#", "Entries", "Side", "Entry Time", "Exit Time", "Entry Price", "Exit Price",
            "Quantity", "Value", "Profit/Loss", "Return", "Best P&L", "Worst P&L", "Capture", "Duration",
            "Commission", "Exit Reason", "Zone", "Better Entry", "Better Exit"
        };
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        private List<TableRow> visibleRows = new ArrayList<>();
        private List<TableRow> groupRows = new ArrayList<>();

        TreeDetailedTableModel(List<Trade> trades) {
            buildGroups(trades != null ? trades : new ArrayList<>());
            rebuildVisibleRows();
        }

        private void buildGroups(List<Trade> allTrades) {
            groupRows.clear();

            List<Trade> validTrades = allTrades.stream()
                .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
                .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
                .toList();

            // Group trades by groupId
            java.util.Map<String, List<Trade>> tradesByGroup = new java.util.LinkedHashMap<>();
            for (Trade t : validTrades) {
                String groupId = t.groupId() != null ? t.groupId() : "single-" + t.id();
                tradesByGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(t);
            }

            List<Trade> rejectedTrades = allTrades.stream()
                .filter(t -> "rejected".equals(t.exitReason()))
                .toList();

            int index = 1;
            for (List<Trade> group : tradesByGroup.values()) {
                if (group.size() == 1) {
                    groupRows.add(TableRow.single(index, group.getFirst()));
                } else {
                    groupRows.add(TableRow.group(index, group));
                }
                index++;
            }

            for (Trade t : rejectedTrades) {
                groupRows.add(TableRow.single(index, t));
                index++;
            }
        }

        private void rebuildVisibleRows() {
            visibleRows.clear();
            for (TableRow row : groupRows) {
                visibleRows.add(row);
                if (row.isGroup && row.expanded) {
                    for (int i = 0; i < row.trades.size(); i++) {
                        visibleRows.add(TableRow.child(row.groupIndex, i + 1, row.trades.get(i)));
                    }
                }
            }
        }

        public void toggleExpand(int rowIndex) {
            TableRow row = visibleRows.get(rowIndex);
            if (row.isGroup) {
                row.expanded = !row.expanded;
                rebuildVisibleRows();
                fireTableDataChanged();
            }
        }

        public TableRow getRowAt(int rowIndex) {
            return visibleRows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return visibleRows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TableRow row = visibleRows.get(rowIndex);

            if (row.isGroup) {
                return switch (columnIndex) {
                    case 0 -> (row.expanded ? "▼ " : "▶ ") + row.groupIndex;
                    case 1 -> row.trades.size();  // Entries count
                    case 2 -> row.getSide().toUpperCase();
                    case 3 -> DATE_FORMAT.format(new Date(row.getFirstEntryTime()));
                    case 4 -> row.getExitTime() != null ? DATE_FORMAT.format(new Date(row.getExitTime())) : "-";
                    case 5 -> formatPrice(row.getAvgEntryPrice()) + " (avg)";
                    case 6 -> row.getExitPrice() != null ? formatPrice(row.getExitPrice()) : "-";
                    case 7 -> String.format("%.6f", row.getTotalQuantity());
                    case 8 -> String.format("%.2f", row.getTotalValue());
                    case 9 -> String.format("%+.2f", row.getTotalPnl());
                    case 10 -> String.format("%+.2f%%", row.getAvgPnlPercent());
                    case 11 -> formatMfe(row.getMfe());       // MFE
                    case 12 -> formatMae(row.getMae());       // MAE
                    case 13 -> formatCapture(row.getCaptureRatio());  // Capture
                    case 14 -> row.getDuration() != null ? String.valueOf(row.getDuration()) : "-";  // Duration
                    case 15 -> String.format("%.2f", row.getTotalCommission());
                    case 16 -> formatExitReason(row.getExitReason());
                    case 17 -> row.getExitZone() != null ? row.getExitZone() : "-";
                    case 18 -> formatBetterContext(row.getBetterEntryImprovement());  // Better Entry
                    case 19 -> formatBetterContext(row.getBetterExitImprovement());   // Better Exit
                    default -> "";
                };
            } else {
                Trade trade = row.singleTrade;
                boolean isRejected = row.isRejected();
                String prefix = row.isChild ? "    " + row.groupIndex + "." + row.childIndex : "  " + row.groupIndex;

                return switch (columnIndex) {
                    case 0 -> prefix;
                    case 1 -> "";  // Entries (empty for single/child)
                    case 2 -> trade.side().toUpperCase();
                    case 3 -> DATE_FORMAT.format(new Date(trade.entryTime()));
                    case 4 -> isRejected ? "-" : (trade.exitTime() != null ? DATE_FORMAT.format(new Date(trade.exitTime())) : "-");
                    case 5 -> formatPrice(trade.entryPrice());
                    case 6 -> isRejected ? "-" : (trade.exitPrice() != null ? formatPrice(trade.exitPrice()) : "-");
                    case 7 -> isRejected ? "-" : String.format("%.6f", trade.quantity());
                    case 8 -> isRejected ? "-" : String.format("%.2f", trade.value());
                    case 9 -> isRejected ? "NO CAPITAL" : (trade.pnl() != null ? String.format("%+.2f", trade.pnl()) : "-");
                    case 10 -> isRejected ? "-" : (trade.pnlPercent() != null ? String.format("%+.2f%%", trade.pnlPercent()) : "-");
                    case 11 -> isRejected ? "-" : formatMfe(trade.mfe());       // MFE
                    case 12 -> isRejected ? "-" : formatMae(trade.mae());       // MAE
                    case 13 -> isRejected ? "-" : formatCapture(trade.captureRatio());  // Capture
                    case 14 -> isRejected ? "-" : (trade.duration() != null ? String.valueOf(trade.duration()) : "-");  // Duration
                    case 15 -> isRejected ? "-" : (trade.commission() != null ? String.format("%.2f", trade.commission()) : "-");
                    case 16 -> formatExitReason(trade.exitReason());
                    case 17 -> trade.exitZone() != null ? trade.exitZone() : "-";
                    case 18 -> isRejected ? "-" : formatBetterContext(trade.betterEntryImprovement());  // Better Entry
                    case 19 -> isRejected ? "-" : formatBetterContext(trade.betterExitImprovement());   // Better Exit
                    default -> "";
                };
            }
        }

        private String formatMfe(Double mfe) {
            return mfe != null ? String.format("+%.1f%%", mfe) : "-";
        }

        private String formatMae(Double mae) {
            return mae != null ? String.format("%.1f%%", mae) : "-";
        }

        private String formatCapture(Double capture) {
            return capture != null ? String.format("%.0f%%", capture * 100) : "-";
        }

        private String formatBetterContext(Double improvement) {
            if (improvement == null) return "-";
            return String.format("+%.1f%%", improvement);
        }

        private String formatPrice(double price) {
            if (price >= 100) return String.format("%,.2f", price);
            else if (price >= 1) return String.format("%.4f", price);
            else return String.format("%.8f", price);
        }

        private String formatExitReason(String reason) {
            if (reason == null) return "-";
            return switch (reason) {
                case "signal" -> "Signal";
                case "stop_loss" -> "Stop Loss";
                case "take_profit" -> "Take Profit";
                case "trailing_stop" -> "Trail Stop";
                case "zone_exit" -> "Zone Exit";
                case "rejected" -> "Rejected";
                default -> reason;
            };
        }
    }

    /**
     * Renderer for the tree column
     */
    private static class TreeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        TreeCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.LEFT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected()) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(150, 150, 150));
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }

            return this;
        }
    }

    /**
     * Cell renderer that handles groups and children
     */
    private static class TradeRowRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;
        private final int alignment;

        TradeRowRenderer(TreeDetailedTableModel model, int alignment) {
            this.model = model;
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(alignment);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected()) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(150, 150, 150));
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            }

            return this;
        }
    }

    /**
     * Cell renderer for P&L columns
     */
    private static class PnlCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        PnlCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected()) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                if (value instanceof String s && !s.equals("-")) {
                    if (s.startsWith("+")) setForeground(new Color(120, 180, 120));
                    else if (s.startsWith("-")) setForeground(new Color(200, 120, 120));
                    else setForeground(Color.GRAY);
                }
            } else if (value instanceof String s && !s.equals("-")) {
                if (s.startsWith("+")) setForeground(new Color(76, 175, 80));
                else if (s.startsWith("-")) setForeground(new Color(244, 67, 54));
                else setForeground(Color.GRAY);
            } else {
                setForeground(Color.GRAY);
            }

            return this;
        }
    }

    /**
     * Cell renderer for MFE column (green)
     */
    private static class MfeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        MfeCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(120, 180, 120));
            } else {
                setForeground(new Color(76, 175, 80));
            }

            return this;
        }
    }

    /**
     * Cell renderer for MAE column (red)
     */
    private static class MaeCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        MaeCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (tableRow.isChild) {
                setForeground(new Color(200, 120, 120));
            } else {
                setForeground(new Color(244, 67, 54));
            }

            return this;
        }
    }

    /**
     * Cell renderer for Capture ratio column
     */
    private static class CaptureCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        CaptureCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (value instanceof String s) {
                // Color based on capture percentage: >70% green, 40-70% yellow, <40% red
                try {
                    int pct = Integer.parseInt(s.replace("%", ""));
                    if (tableRow.isChild) {
                        if (pct >= 70) setForeground(new Color(120, 180, 120));
                        else if (pct >= 40) setForeground(new Color(180, 180, 100));
                        else setForeground(new Color(200, 120, 120));
                    } else {
                        if (pct >= 70) setForeground(new Color(76, 175, 80));
                        else if (pct >= 40) setForeground(new Color(255, 193, 7));
                        else setForeground(new Color(244, 67, 54));
                    }
                } catch (NumberFormatException e) {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }

    /**
     * Renderer for better entry/exit context columns.
     * Shows improvement percentage with color coding (higher = more room for improvement = worse).
     */
    private static class BetterContextCellRenderer extends DefaultTableCellRenderer {
        private final TreeDetailedTableModel model;

        BetterContextCellRenderer(TreeDetailedTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);

            TableRow tableRow = model.getRowAt(row);
            if (tableRow.isRejected() || "-".equals(value)) {
                setForeground(new Color(180, 180, 180));
            } else if (value instanceof String s && s.startsWith("+")) {
                // Color based on improvement: lower is better (entry/exit was closer to optimal)
                // <1% = green (good), 1-3% = yellow (ok), >3% = red (significant room for improvement)
                try {
                    double pct = Double.parseDouble(s.replace("+", "").replace("%", ""));
                    if (tableRow.isChild) {
                        if (pct < 1.0) setForeground(new Color(120, 180, 120));
                        else if (pct < 3.0) setForeground(new Color(180, 180, 100));
                        else setForeground(new Color(200, 120, 120));
                    } else {
                        if (pct < 1.0) setForeground(new Color(76, 175, 80));
                        else if (pct < 3.0) setForeground(new Color(255, 193, 7));
                        else setForeground(new Color(244, 67, 54));
                    }
                } catch (NumberFormatException e) {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }
}
