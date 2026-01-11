package com.tradery.ui;

import com.tradery.data.DataConfig;
import com.tradery.data.DataIntegrityChecker;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Left-side browser panel showing available data series.
 * Displays symbols with timeframes indented underneath, plus data health info.
 * Also shows AggTrades data at the bottom.
 */
public class DataBrowserPanel extends JPanel {

    // Status colors (fixed)
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
    private final File aggTradesDir;
    private final List<RowData> rows = new ArrayList<>();
    private final Map<String, Map<String, DataSummary>> dataSummaries = new HashMap<>();
    private final Map<String, AggTradesSummary> aggTradesSummaries = new HashMap<>();
    private final Map<String, FundingRateSummary> fundingRateSummaries = new HashMap<>();
    private final Map<String, OpenInterestSummary> openInterestSummaries = new HashMap<>();

    private String selectedSymbol;
    private String selectedTimeframe;
    private int hoveredRow = -1;

    private BiConsumer<String, String> onSelectionChanged;

    public DataBrowserPanel() {
        this.checker = new DataIntegrityChecker();
        this.aggTradesDir = DataConfig.getInstance().getDataDir();

        setPreferredSize(new Dimension(200, 300));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                if (row >= 0 && row < rows.size()) {
                    RowData data = rows.get(row);
                    if (data.isOpenInterest && data.symbol != null) {
                        // Open Interest row - use "openInterest" as timeframe marker
                        setSelection(data.symbol, "openInterest");
                    } else if (data.isFundingRate && data.symbol != null) {
                        // Funding rate row - use "fundingRate" as timeframe marker
                        setSelection(data.symbol, "fundingRate");
                    } else if (data.isAggTrades && data.symbol != null) {
                        // AggTrades row - use "aggTrades" as timeframe marker
                        setSelection(data.symbol, "aggTrades");
                    } else if (data.timeframe != null && !data.isAggTrades && !data.isFundingRate && !data.isOpenInterest) {
                        // Candle timeframe row
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
        aggTradesSummaries.clear();
        fundingRateSummaries.clear();
        openInterestSummaries.clear();

        // Load candle data summaries
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

        // Load aggTrades summaries
        loadAggTradesSummaries();

        // Load funding rate summaries
        loadFundingRateSummaries();

        // Load open interest summaries
        loadOpenInterestSummaries();

        rebuildRows();
        revalidate();
        repaint();
    }

    private void loadAggTradesSummaries() {
        if (!aggTradesDir.exists()) return;

        File[] symbolDirs = aggTradesDir.listFiles(File::isDirectory);
        if (symbolDirs == null) return;

        for (File symbolDir : symbolDirs) {
            // Look for aggTrades subdirectory under each symbol
            File aggDir = new File(symbolDir, "aggTrades");
            if (!aggDir.exists() || !aggDir.isDirectory()) continue;

            String symbol = symbolDir.getName();

            // AggTrades are organized in date subdirectories: aggTrades/2026-01-11/*.csv
            File[] dateDirs = aggDir.listFiles(File::isDirectory);
            if (dateDirs == null || dateDirs.length == 0) continue;

            // Find date range and count files
            String minDate = null;
            String maxDate = null;
            int fileCount = 0;
            long totalSize = 0;
            boolean hasPartial = false;

            for (File dateDir : dateDirs) {
                String date = dateDir.getName();
                File[] csvFiles = dateDir.listFiles((dir, name) -> name.endsWith(".csv"));
                if (csvFiles == null || csvFiles.length == 0) continue;

                for (File f : csvFiles) {
                    if (f.getName().contains(".partial.")) hasPartial = true;
                    fileCount++;
                    totalSize += f.length();
                }

                if (minDate == null || date.compareTo(minDate) < 0) minDate = date;
                if (maxDate == null || date.compareTo(maxDate) > 0) maxDate = date;
            }

            if (minDate != null) {
                aggTradesSummaries.put(symbol, new AggTradesSummary(
                    minDate, maxDate, dateDirs.length, totalSize, hasPartial
                ));
            }
        }
    }

    private void loadFundingRateSummaries() {
        if (!aggTradesDir.exists()) return;

        File[] symbolDirs = aggTradesDir.listFiles(File::isDirectory);
        if (symbolDirs == null) return;

        for (File symbolDir : symbolDirs) {
            // Look for funding subdirectory under each symbol
            File fundingDir = new File(symbolDir, "funding");
            if (!fundingDir.exists() || !fundingDir.isDirectory()) continue;

            String symbol = symbolDir.getName();
            File[] csvFiles = fundingDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) continue;

            // Find month range and count files
            String minMonth = null;
            String maxMonth = null;
            int rateCount = 0;
            long totalSize = 0;

            for (File f : csvFiles) {
                String name = f.getName().replace(".csv", "");
                totalSize += f.length();

                if (minMonth == null || name.compareTo(minMonth) < 0) minMonth = name;
                if (maxMonth == null || name.compareTo(maxMonth) > 0) maxMonth = name;

                // Count lines (minus header) to get rate count
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    int lines = 0;
                    while (reader.readLine() != null) lines++;
                    rateCount += Math.max(0, lines - 1); // Subtract header
                } catch (IOException e) {
                    // Ignore
                }
            }

            if (minMonth != null) {
                fundingRateSummaries.put(symbol, new FundingRateSummary(
                    minMonth, maxMonth, csvFiles.length, rateCount, totalSize
                ));
            }
        }
    }

    private void loadOpenInterestSummaries() {
        if (!aggTradesDir.exists()) return;

        File[] symbolDirs = aggTradesDir.listFiles(File::isDirectory);
        if (symbolDirs == null) return;

        for (File symbolDir : symbolDirs) {
            // Look for openinterest subdirectory under each symbol
            File oiDir = new File(symbolDir, "openinterest");
            if (!oiDir.exists() || !oiDir.isDirectory()) continue;

            String symbol = symbolDir.getName();
            File[] csvFiles = oiDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (csvFiles == null || csvFiles.length == 0) continue;

            // Find month range and count records
            String minMonth = null;
            String maxMonth = null;
            int recordCount = 0;
            long totalSize = 0;

            for (File f : csvFiles) {
                String name = f.getName().replace(".csv", "");
                totalSize += f.length();

                if (minMonth == null || name.compareTo(minMonth) < 0) minMonth = name;
                if (maxMonth == null || name.compareTo(maxMonth) > 0) maxMonth = name;

                // Count lines (minus header) to get record count
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    int lines = 0;
                    while (reader.readLine() != null) lines++;
                    recordCount += Math.max(0, lines - 1); // Subtract header
                } catch (IOException e) {
                    // Ignore
                }
            }

            if (minMonth != null) {
                openInterestSummaries.put(symbol, new OpenInterestSummary(
                    minMonth, maxMonth, csvFiles.length, recordCount, totalSize
                ));
            }
        }
    }

    private void rebuildRows() {
        rows.clear();

        // Candles section header
        boolean hasCandleData = !dataSummaries.isEmpty();
        if (hasCandleData) {
            rows.add(new RowData(null, null, false, true, false, false, false)); // Section header for Candles
        }

        // Candle data - only show symbols that have data
        for (String symbol : ALL_SYMBOLS) {
            Map<String, DataSummary> tfData = dataSummaries.get(symbol);
            if (tfData == null || tfData.isEmpty()) {
                continue;  // Skip symbols without data
            }

            // Add symbol row
            rows.add(new RowData(symbol, null, true, false, false, false, false));

            // Add timeframe rows (only those with data)
            for (String tf : ALL_TIMEFRAMES) {
                if (tfData.containsKey(tf)) {
                    rows.add(new RowData(symbol, tf, true, false, false, false, false));
                }
            }
        }

        // AggTrades section
        if (!aggTradesSummaries.isEmpty()) {
            rows.add(new RowData(null, null, false, true, true, false, false)); // Section header for AggTrades

            for (String symbol : ALL_SYMBOLS) {
                if (aggTradesSummaries.containsKey(symbol)) {
                    rows.add(new RowData(symbol, null, true, false, true, false, false));
                }
            }
        }

        // Funding Rate section
        if (!fundingRateSummaries.isEmpty()) {
            rows.add(new RowData(null, null, false, true, false, true, false)); // Section header for Funding Rate

            for (String symbol : ALL_SYMBOLS) {
                if (fundingRateSummaries.containsKey(symbol)) {
                    rows.add(new RowData(symbol, null, true, false, false, true, false));
                }
            }
        }

        // Open Interest section
        if (!openInterestSummaries.isEmpty()) {
            rows.add(new RowData(null, null, false, true, false, false, true)); // Section header for OI

            for (String symbol : ALL_SYMBOLS) {
                if (openInterestSummaries.containsKey(symbol)) {
                    rows.add(new RowData(symbol, null, true, false, false, false, true));
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

            // Section headers
            if (row.isSectionHeader) {
                String title = row.isOpenInterest ? "Open Interest (5m)" :
                               row.isFundingRate ? "Funding Rate (8h)" :
                               row.isAggTrades ? "Aggregated Trades" : "Candles (OHLCV)";
                drawSectionHeader(g2, title, y);
                y += ROW_HEIGHT;
                continue;
            }

            boolean isSelected;
            boolean isHovered;

            if (row.isOpenInterest && row.symbol != null) {
                // Open Interest row
                isSelected = row.symbol.equals(selectedSymbol) && "openInterest".equals(selectedTimeframe);
                isHovered = i == hoveredRow;
            } else if (row.isFundingRate && row.symbol != null) {
                // Funding rate row
                isSelected = row.symbol.equals(selectedSymbol) && "fundingRate".equals(selectedTimeframe);
                isHovered = i == hoveredRow;
            } else if (row.isAggTrades && row.symbol != null) {
                // AggTrades row
                isSelected = row.symbol.equals(selectedSymbol) && "aggTrades".equals(selectedTimeframe);
                isHovered = i == hoveredRow;
            } else if (row.timeframe != null) {
                // Candle timeframe row
                isSelected = row.symbol.equals(selectedSymbol) && row.timeframe.equals(selectedTimeframe);
                isHovered = i == hoveredRow;
            } else {
                // Symbol header row - not selectable
                isSelected = false;
                isHovered = false;
            }

            // Background
            if (isSelected) {
                g2.setColor(UIManager.getColor("List.selectionBackground"));
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            } else if (isHovered) {
                g2.setColor(UIManager.getColor("List.selectionInactiveBackground"));
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            }

            if (row.isOpenInterest) {
                drawOpenInterestRow(g2, row, y);
            } else if (row.isFundingRate) {
                drawFundingRateRow(g2, row, y);
            } else if (row.isAggTrades) {
                drawAggTradesRow(g2, row, y);
            } else if (row.timeframe == null) {
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

    private void drawSectionHeader(Graphics2D g2, String title, int y) {
        // Draw thin separator line at top
        g2.setColor(UIManager.getColor("Separator.foreground"));
        g2.drawLine(8, y + 2, getWidth() - 8, y + 2);

        // Use accent color for section title
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        Color accentColor = UIManager.getColor("Component.accentColor");
        if (accentColor == null) {
            accentColor = UIManager.getColor("Focus.color");
        }
        if (accentColor == null) {
            accentColor = new Color(0, 122, 255); // Fallback blue
        }
        g2.setColor(accentColor);
        g2.drawString(title, 8, y + 17);
    }

    private void drawSymbolRow(Graphics2D g2, RowData row, int y) {
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g2.setColor(row.hasData ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));
        g2.drawString(row.symbol, 8, y + 16);
    }

    private void drawTimeframeRow(Graphics2D g2, RowData row, int y) {
        Map<String, DataSummary> tfData = dataSummaries.get(row.symbol);
        DataSummary summary = tfData != null ? tfData.get(row.timeframe) : null;

        // Timeframe label
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.setColor(UIManager.getColor("Label.foreground"));
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
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            String range = formatRange(summary.start, summary.end);
            g2.drawString(range, INDENT + 54, y + 15);
        }
    }

    private void drawAggTradesRow(Graphics2D g2, RowData row, int y) {
        AggTradesSummary summary = aggTradesSummaries.get(row.symbol);

        // Symbol label (indented)
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.setColor(UIManager.getColor("Label.foreground"));
        g2.drawString(row.symbol, INDENT + 8, y + 15);

        if (summary != null) {
            // Status dot
            Color statusColor = summary.hasPartial ? PARTIAL_COLOR : COMPLETE_COLOR;
            g2.setColor(statusColor);
            g2.fillOval(INDENT + 75, y + 8, 8, 8);

            // Date range (show abbreviated: MM-dd → MM-dd)
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            String startShort = summary.startDate.length() > 5 ? summary.startDate.substring(5) : summary.startDate;
            String endShort = summary.endDate.length() > 5 ? summary.endDate.substring(5) : summary.endDate;
            String info = startShort + "→" + endShort + " (" + summary.fileCount + "d)";
            g2.drawString(info, INDENT + 90, y + 15);
        }
    }

    private void drawFundingRateRow(Graphics2D g2, RowData row, int y) {
        FundingRateSummary summary = fundingRateSummaries.get(row.symbol);

        // Symbol label (indented)
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.setColor(UIManager.getColor("Label.foreground"));
        g2.drawString(row.symbol, INDENT + 8, y + 15);

        if (summary != null) {
            // Status dot (always green for funding since it's auto-fetched)
            g2.setColor(COMPLETE_COLOR);
            g2.fillOval(INDENT + 75, y + 8, 8, 8);

            // Month range and rate count
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            String info = summary.startMonth + "→" + summary.endMonth + " (" + summary.rateCount + ")";
            g2.drawString(info, INDENT + 90, y + 15);
        }
    }

    private void drawOpenInterestRow(Graphics2D g2, RowData row, int y) {
        OpenInterestSummary summary = openInterestSummaries.get(row.symbol);

        // Symbol label (indented)
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.setColor(UIManager.getColor("Label.foreground"));
        g2.drawString(row.symbol, INDENT + 8, y + 15);

        if (summary != null) {
            // Status dot (always green for OI since it's auto-fetched)
            g2.setColor(COMPLETE_COLOR);
            g2.fillOval(INDENT + 75, y + 8, 8, 8);

            // Month range and record count
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(UIManager.getColor("Label.disabledForeground"));
            String info = summary.startMonth + "→" + summary.endMonth + " (" + summary.recordCount + ")";
            g2.drawString(info, INDENT + 90, y + 15);
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
    private record RowData(String symbol, String timeframe, boolean hasData, boolean isSectionHeader, boolean isAggTrades, boolean isFundingRate, boolean isOpenInterest) {}
    private record DataSummary(YearMonth start, YearMonth end, DataStatus status) {}
    private record AggTradesSummary(String startDate, String endDate, int fileCount, long totalSize, boolean hasPartial) {}
    private record FundingRateSummary(String startMonth, String endMonth, int monthCount, int rateCount, long totalSize) {}
    private record OpenInterestSummary(String startMonth, String endMonth, int monthCount, int recordCount, long totalSize) {}
}
