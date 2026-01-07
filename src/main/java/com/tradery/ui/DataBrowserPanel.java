package com.tradery.ui;

import com.tradery.data.DataIntegrityChecker;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Left-side browser panel showing available data series.
 * Displays symbols with timeframes indented underneath, plus data health info.
 */
public class DataBrowserPanel extends JPanel {

    private static final Color BACKGROUND = new Color(30, 30, 35);
    private static final Color HOVER_BG = new Color(50, 50, 55);
    private static final Color SELECTED_BG = new Color(60, 80, 120);
    private static final Color SYMBOL_COLOR = new Color(220, 220, 220);
    private static final Color TIMEFRAME_COLOR = new Color(180, 180, 180);
    private static final Color INFO_COLOR = new Color(120, 120, 120);
    private static final Color COMPLETE_COLOR = new Color(76, 175, 80);
    private static final Color PARTIAL_COLOR = new Color(255, 193, 7);
    private static final Color MISSING_COLOR = new Color(100, 100, 100);

    private static final int ROW_HEIGHT = 24;
    private static final int INDENT = 20;

    // All available symbols/timeframes (even without data)
    private static final String[] ALL_SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT"
    };
    private static final String[] ALL_TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    private final DataIntegrityChecker checker;
    private final List<RowData> rows = new ArrayList<>();
    private final Map<String, Map<String, DataSummary>> dataSummaries = new HashMap<>();

    private String selectedSymbol;
    private String selectedTimeframe;
    private int hoveredRow = -1;

    private BiConsumer<String, String> onSelectionChanged;

    public DataBrowserPanel() {
        this.checker = new DataIntegrityChecker();

        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(200, 300));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                if (row >= 0 && row < rows.size()) {
                    RowData data = rows.get(row);
                    if (data.timeframe != null) {
                        setSelection(data.symbol, data.timeframe);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                if (row != hoveredRow) {
                    hoveredRow = row;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                repaint();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // Initial data load
        refreshData();
    }

    /**
     * Set the selection change callback.
     */
    public void setOnSelectionChanged(BiConsumer<String, String> callback) {
        this.onSelectionChanged = callback;
    }

    /**
     * Set the current selection programmatically.
     */
    public void setSelection(String symbol, String timeframe) {
        if (Objects.equals(symbol, selectedSymbol) && Objects.equals(timeframe, selectedTimeframe)) {
            return;
        }

        selectedSymbol = symbol;
        selectedTimeframe = timeframe;
        repaint();

        if (onSelectionChanged != null) {
            onSelectionChanged.accept(symbol, timeframe);
        }
    }

    public String getSelectedSymbol() {
        return selectedSymbol;
    }

    public String getSelectedTimeframe() {
        return selectedTimeframe;
    }

    /**
     * Refresh data summaries from the integrity checker.
     */
    public void refreshData() {
        dataSummaries.clear();

        for (String symbol : ALL_SYMBOLS) {
            Map<String, DataSummary> tfSummaries = new HashMap<>();

            for (String tf : ALL_TIMEFRAMES) {
                Optional<YearMonth[]> range = checker.getDataRange(symbol, tf);
                if (range.isPresent()) {
                    YearMonth start = range.get()[0];
                    YearMonth end = range.get()[1];

                    // Get overall health (simplified - just check a few recent months)
                    List<DataHealth> health = checker.analyzeRange(symbol, tf,
                            end.minusMonths(2), end);

                    DataStatus overallStatus = DataStatus.COMPLETE;
                    for (DataHealth h : health) {
                        if (h.status() == DataStatus.PARTIAL) {
                            overallStatus = DataStatus.PARTIAL;
                        } else if (h.status() == DataStatus.MISSING && overallStatus != DataStatus.PARTIAL) {
                            overallStatus = DataStatus.MISSING;
                        }
                    }

                    tfSummaries.put(tf, new DataSummary(start, end, overallStatus));
                }
            }

            if (!tfSummaries.isEmpty()) {
                dataSummaries.put(symbol, tfSummaries);
            }
        }

        rebuildRows();
        revalidate();
        repaint();
    }

    private void rebuildRows() {
        rows.clear();

        // Only show symbols that have data
        for (String symbol : ALL_SYMBOLS) {
            Map<String, DataSummary> tfData = dataSummaries.get(symbol);
            if (tfData == null || tfData.isEmpty()) {
                continue;  // Skip symbols without data
            }

            // Add symbol row
            rows.add(new RowData(symbol, null, true));

            // Add timeframe rows (only those with data)
            for (String tf : ALL_TIMEFRAMES) {
                if (tfData.containsKey(tf)) {
                    rows.add(new RowData(symbol, tf, true));
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            RowData row = rows.get(i);
            boolean isSelected = row.timeframe != null &&
                    row.symbol.equals(selectedSymbol) &&
                    row.timeframe.equals(selectedTimeframe);
            boolean isHovered = i == hoveredRow && row.timeframe != null;

            // Background
            if (isSelected) {
                g2.setColor(SELECTED_BG);
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            } else if (isHovered) {
                g2.setColor(HOVER_BG);
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            }

            if (row.timeframe == null) {
                // Symbol row
                drawSymbolRow(g2, row, y);
            } else {
                // Timeframe row
                drawTimeframeRow(g2, row, y);
            }

            y += ROW_HEIGHT;
        }

        g2.dispose();
    }

    private void drawSymbolRow(Graphics2D g2, RowData row, int y) {
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g2.setColor(row.hasData ? SYMBOL_COLOR : INFO_COLOR);
        g2.drawString(row.symbol, 8, y + 16);
    }

    private void drawTimeframeRow(Graphics2D g2, RowData row, int y) {
        Map<String, DataSummary> tfData = dataSummaries.get(row.symbol);
        DataSummary summary = tfData != null ? tfData.get(row.timeframe) : null;

        // Timeframe label
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.setColor(TIMEFRAME_COLOR);
        g2.drawString(row.timeframe, INDENT + 8, y + 15);

        // Status dot
        Color statusColor = MISSING_COLOR;
        if (summary != null) {
            statusColor = switch (summary.status) {
                case COMPLETE -> COMPLETE_COLOR;
                case PARTIAL -> PARTIAL_COLOR;
                default -> MISSING_COLOR;
            };
        }
        g2.setColor(statusColor);
        g2.fillOval(INDENT + 40, y + 8, 8, 8);

        // Date range
        if (summary != null) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(INFO_COLOR);
            String range = formatRange(summary.start, summary.end);
            g2.drawString(range, INDENT + 54, y + 15);
        }
    }

    private String formatRange(YearMonth start, YearMonth end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yy-MM");
        if (end.equals(YearMonth.now())) {
            return start.format(fmt) + " → now";
        }
        return start.format(fmt) + " → " + end.format(fmt);
    }

    @Override
    public Dimension getPreferredSize() {
        int height = Math.max(300, rows.size() * ROW_HEIGHT + 10);
        return new Dimension(200, height);
    }

    // Internal data classes
    private record RowData(String symbol, String timeframe, boolean hasData) {}
    private record DataSummary(YearMonth start, YearMonth end, DataStatus status) {}
}
