package com.tradery.ui;

import com.tradery.data.DataIntegrityChecker;
import com.tradery.model.DataHealth;
import com.tradery.model.DataStatus;
import com.tradery.model.Gap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * 2D block diagram visualization of data health.
 * Shows months as colored blocks organized by year.
 */
public class DataHealthPanel extends JPanel {

    // Colors for status blocks
    private static final Color COMPLETE_COLOR = new Color(76, 175, 80);
    private static final Color PARTIAL_COLOR_HIGH = new Color(255, 193, 7);  // Yellow (>75%)
    private static final Color PARTIAL_COLOR_MED = new Color(255, 140, 0);   // Orange (50-75%)
    private static final Color PARTIAL_COLOR_LOW = new Color(244, 67, 54);   // Red (<50%)
    private static final Color MISSING_COLOR = new Color(60, 60, 65);
    private static final Color UNKNOWN_COLOR = new Color(100, 100, 100);
    private static final Color HOVER_BORDER = new Color(255, 255, 255, 180);
    private static final Color SELECTED_BORDER = new Color(0, 150, 255);

    private static final int BLOCK_WIDTH = 36;
    private static final int BLOCK_HEIGHT = 24;
    private static final int BLOCK_GAP = 4;
    private static final int YEAR_LABEL_WIDTH = 50;
    private static final int PADDING = 16;

    // Daily details view
    private static final int DAY_BLOCK_SIZE = 22;
    private static final int DAY_BLOCK_GAP = 3;
    private static final int DAILY_VIEW_HEIGHT = 120;

    private static final String[] MONTH_LABELS = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final DataIntegrityChecker checker;

    private String symbol;
    private String resolution;
    private List<DataHealth> healthData = new ArrayList<>();
    private Map<YearMonth, Rectangle> blockBounds = new HashMap<>();

    private YearMonth hoveredMonth;
    private YearMonth selectedMonth;
    private Consumer<DataHealth> onMonthSelected;
    private String customMessage;

    // Daily details for selected month
    private record DayInfo(LocalDate date, DataStatus status, String details) {}
    private List<DayInfo> dailyDetails = new ArrayList<>();
    private Map<LocalDate, Rectangle> dayBounds = new HashMap<>();
    private LocalDate hoveredDay;

    public DataHealthPanel(DataIntegrityChecker checker) {
        this.checker = checker;

        setBackground(new Color(30, 30, 35));
        setPreferredSize(new Dimension(500, 300));

        // Mouse handling
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                YearMonth newHovered = findMonthAt(e.getX(), e.getY());
                LocalDate newHoveredDay = findDayAt(e.getX(), e.getY());

                boolean changed = false;
                if (!Objects.equals(newHovered, hoveredMonth)) {
                    hoveredMonth = newHovered;
                    changed = true;
                }
                if (!Objects.equals(newHoveredDay, hoveredDay)) {
                    hoveredDay = newHoveredDay;
                    changed = true;
                }

                if (changed) {
                    updateTooltip(e);
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                YearMonth clicked = findMonthAt(e.getX(), e.getY());
                if (clicked != null) {
                    selectedMonth = clicked;
                    loadDailyDetails(clicked);
                    revalidate();
                    repaint();
                    notifyMonthSelected(clicked);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredMonth = null;
                hoveredDay = null;
                setToolTipText(null);
                repaint();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    /**
     * Set the symbol and resolution to display, and refresh data.
     */
    public void setData(String symbol, String resolution) {
        this.symbol = symbol;
        this.resolution = resolution;
        this.selectedMonth = null;
        this.customMessage = null;
        this.dailyDetails.clear();
        refreshData();
    }

    /**
     * Set a custom message to display instead of data.
     */
    public void setCustomMessage(String message) {
        this.symbol = null;
        this.resolution = null;
        this.customMessage = message;
        healthData.clear();
        blockBounds.clear();
        dailyDetails.clear();
        repaint();
    }

    /**
     * Load aggTrades data and show month-based summary.
     */
    public void setAggTradesData(String symbol) {
        this.symbol = symbol;
        this.resolution = "aggTrades";
        this.customMessage = null;
        this.selectedMonth = null;
        this.dailyDetails.clear();
        loadAggTradesHealth(symbol);
        repaint();
    }

    /**
     * Refresh data while preserving the current selection.
     * Used for auto-refresh to avoid losing user's month selection.
     */
    public void refreshKeepSelection() {
        YearMonth savedMonth = selectedMonth;

        if ("aggTrades".equals(resolution) && symbol != null) {
            loadAggTradesHealth(symbol);
        } else if (symbol != null && resolution != null) {
            refreshData();
        }

        // Restore selection if still valid
        if (savedMonth != null) {
            boolean stillValid = healthData.stream()
                    .anyMatch(h -> h.month().equals(savedMonth));
            if (stillValid) {
                selectedMonth = savedMonth;
                loadDailyDetails(savedMonth);
            }
        }

        revalidate();
        repaint();
    }

    private void loadAggTradesHealth(String symbol) {
        healthData.clear();
        blockBounds.clear();

        java.io.File aggDir = new java.io.File(System.getProperty("user.home") + "/.tradery/data/" + symbol + "/aggTrades");
        if (!aggDir.exists()) return;

        java.io.File[] files = aggDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) return;

        // Group files by month and count days
        Map<YearMonth, int[]> monthStats = new TreeMap<>(); // [complete, partial]

        for (java.io.File f : files) {
            String name = f.getName();
            boolean isPartial = name.endsWith(".partial.csv");
            String dateStr = name.replace(".partial.csv", "").replace(".csv", "");

            try {
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
                YearMonth ym = YearMonth.from(date);
                int[] stats = monthStats.computeIfAbsent(ym, k -> new int[2]);
                if (isPartial) stats[1]++; else stats[0]++;
            } catch (Exception e) {
                // Skip invalid filenames
            }
        }

        // Create DataHealth entries for each month
        YearMonth now = YearMonth.now();
        for (Map.Entry<YearMonth, int[]> entry : monthStats.entrySet()) {
            YearMonth month = entry.getKey();
            int complete = entry.getValue()[0];
            int partial = entry.getValue()[1];
            int total = complete + partial;
            int expectedDays = month.lengthOfMonth();
            if (month.equals(now)) {
                expectedDays = java.time.LocalDate.now().getDayOfMonth();
            }

            DataStatus status;
            if (total >= expectedDays * 0.9) {
                status = DataStatus.COMPLETE;
            } else if (total > 0) {
                status = DataStatus.PARTIAL;
            } else {
                status = DataStatus.MISSING;
            }

            healthData.add(new DataHealth(symbol, "aggTrades", month, expectedDays, total, List.of(), status));
        }
    }

    /**
     * Load daily details for the selected month.
     */
    private void loadDailyDetails(YearMonth month) {
        dailyDetails.clear();
        if (month == null || symbol == null) return;

        if ("aggTrades".equals(resolution)) {
            loadAggTradesDailyDetails(month);
        } else {
            loadCandleDailyDetails(month);
        }
    }

    private void loadAggTradesDailyDetails(YearMonth month) {
        File aggDir = new File(System.getProperty("user.home") + "/.tradery/data/" + symbol + "/aggTrades");
        if (!aggDir.exists()) return;

        LocalDate today = LocalDate.now();
        int daysInMonth = month.equals(YearMonth.now()) ? today.getDayOfMonth() : month.lengthOfMonth();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            String dateStr = date.toString(); // yyyy-MM-dd

            File completeFile = new File(aggDir, dateStr + ".csv");
            File partialFile = new File(aggDir, dateStr + ".partial.csv");

            DataStatus status;
            String details;
            if (completeFile.exists()) {
                long sizeKb = completeFile.length() / 1024;
                status = DataStatus.COMPLETE;
                details = String.format("%s - Complete (%d KB)", date, sizeKb);
            } else if (partialFile.exists()) {
                long sizeKb = partialFile.length() / 1024;
                status = DataStatus.PARTIAL;
                details = String.format("%s - Partial (%d KB)", date, sizeKb);
            } else {
                status = DataStatus.MISSING;
                details = date + " - Missing";
            }

            dailyDetails.add(new DayInfo(date, status, details));
        }
    }

    private void loadCandleDailyDetails(YearMonth month) {
        // For candles, we use the DataIntegrityChecker to get gap info
        DataHealth health = healthData.stream()
                .filter(h -> h.month().equals(month))
                .findFirst()
                .orElse(null);

        if (health == null) return;

        LocalDate today = LocalDate.now();
        int daysInMonth = month.equals(YearMonth.now()) ? today.getDayOfMonth() : month.lengthOfMonth();

        // Build a set of days that have gaps
        Set<Integer> gapDays = new HashSet<>();
        for (Gap gap : health.gaps()) {
            LocalDate gapDate = Instant.ofEpochMilli(gap.startTimestamp())
                    .atZone(ZoneOffset.UTC).toLocalDate();
            if (YearMonth.from(gapDate).equals(month)) {
                gapDays.add(gapDate.getDayOfMonth());
            }
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            DataStatus status;
            String details;

            if (gapDays.contains(day)) {
                status = DataStatus.PARTIAL;
                details = date + " - Has gaps";
            } else if (health.status() == DataStatus.COMPLETE || health.status() == DataStatus.PARTIAL) {
                status = DataStatus.COMPLETE;
                details = date + " - Complete";
            } else {
                status = DataStatus.MISSING;
                details = date + " - Missing";
            }

            dailyDetails.add(new DayInfo(date, status, details));
        }
    }

    /**
     * Refresh data from the integrity checker.
     */
    public void refreshData() {
        healthData.clear();
        blockBounds.clear();

        if (symbol == null || resolution == null) {
            repaint();
            return;
        }

        // Get available data range
        Optional<YearMonth[]> range = checker.getDataRange(symbol, resolution);
        if (range.isEmpty()) {
            repaint();
            return;
        }

        YearMonth start = range.get()[0];
        YearMonth end = range.get()[1];

        // Expand to full years - from January of earliest year to December of latest (or now if current year)
        start = start.withMonth(1);
        YearMonth now = YearMonth.now();
        if (end.getYear() == now.getYear()) {
            end = now;  // Current year: show up to current month
        } else {
            end = end.withMonth(12);  // Historical year: show full year
        }

        healthData = checker.analyzeRange(symbol, resolution, start, end);
        repaint();
    }

    /**
     * Set callback for when a month is selected.
     */
    public void setOnMonthSelected(Consumer<DataHealth> callback) {
        this.onMonthSelected = callback;
    }

    /**
     * Get the currently selected month's health data.
     */
    public DataHealth getSelectedHealth() {
        if (selectedMonth == null) return null;
        return healthData.stream()
                .filter(h -> h.month().equals(selectedMonth))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        blockBounds.clear();

        if (healthData.isEmpty()) {
            drawEmptyState(g2);
            g2.dispose();
            return;
        }

        // Group by year
        Map<Integer, List<DataHealth>> byYear = new TreeMap<>(Collections.reverseOrder());
        for (DataHealth h : healthData) {
            byYear.computeIfAbsent(h.month().getYear(), k -> new ArrayList<>()).add(h);
        }

        int y = PADDING;

        // Draw month header
        g2.setColor(new Color(150, 150, 150));
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int m = 0; m < 12; m++) {
            int x = PADDING + YEAR_LABEL_WIDTH + m * (BLOCK_WIDTH + BLOCK_GAP);
            g2.drawString(MONTH_LABELS[m], x + BLOCK_WIDTH / 2 - 3, y + 12);
        }
        y += 20;

        // Draw each year row
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (Map.Entry<Integer, List<DataHealth>> entry : byYear.entrySet()) {
            int year = entry.getKey();
            List<DataHealth> months = entry.getValue();

            // Year label
            g2.setColor(new Color(180, 180, 180));
            g2.drawString(String.valueOf(year), PADDING, y + BLOCK_HEIGHT / 2 + 4);

            // Month blocks
            for (DataHealth h : months) {
                int monthIndex = h.month().getMonthValue() - 1;
                int x = PADDING + YEAR_LABEL_WIDTH + monthIndex * (BLOCK_WIDTH + BLOCK_GAP);

                Rectangle bounds = new Rectangle(x, y, BLOCK_WIDTH, BLOCK_HEIGHT);
                blockBounds.put(h.month(), bounds);

                drawBlock(g2, bounds, h);
            }

            y += BLOCK_HEIGHT + BLOCK_GAP + 4;
        }

        // Draw legend
        y += 10;
        drawLegend(g2, PADDING, y);

        // Calculate divider position (roughly 50% split)
        int dividerY = Math.max(y + 30, getHeight() / 2 - 20);

        // Draw horizontal divider line
        y = dividerY;
        g2.setColor(new Color(60, 60, 65));
        g2.drawLine(PADDING, y, getWidth() - PADDING, y);

        // Draw daily details section (bottom half)
        y += 15;
        if (selectedMonth != null && !dailyDetails.isEmpty()) {
            drawDailyDetails(g2, y);
        } else {
            // Placeholder when no month selected
            g2.setColor(new Color(100, 100, 100));
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String msg = "Click a month above to see daily details";
            FontMetrics fm = g2.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(msg)) / 2;
            int textY = y + (getHeight() - y) / 2;
            g2.drawString(msg, textX, textY);
        }

        g2.dispose();
    }

    private void drawDailyDetails(Graphics2D g2, int startY) {
        dayBounds.clear();

        // Header
        g2.setColor(new Color(180, 180, 180));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        String header = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + " - Daily View";
        g2.drawString(header, PADDING, startY);

        int y = startY + 16;
        int x = PADDING;

        // Draw day blocks in a grid (7 columns for days of week)
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        // Day of week labels
        String[] dowLabels = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            g2.setColor(new Color(120, 120, 120));
            g2.drawString(dowLabels[i], x + i * (DAY_BLOCK_SIZE + DAY_BLOCK_GAP) + (DAY_BLOCK_SIZE / 2) - 3, y);
        }
        y += 16;

        // Offset for first day of month
        LocalDate firstDay = selectedMonth.atDay(1);
        int startDow = firstDay.getDayOfWeek().getValue() - 1; // 0=Monday

        int col = startDow;
        int row = 0;

        for (DayInfo day : dailyDetails) {
            int bx = x + col * (DAY_BLOCK_SIZE + DAY_BLOCK_GAP);
            int by = y + row * (DAY_BLOCK_SIZE + DAY_BLOCK_GAP);

            // Store bounds for hover detection
            dayBounds.put(day.date(), new Rectangle(bx, by, DAY_BLOCK_SIZE, DAY_BLOCK_SIZE));

            // Draw day block
            Color color = switch (day.status()) {
                case COMPLETE -> COMPLETE_COLOR;
                case PARTIAL -> PARTIAL_COLOR_HIGH;
                case MISSING -> MISSING_COLOR;
                case UNKNOWN -> UNKNOWN_COLOR;
            };

            g2.setColor(color);
            g2.fillRoundRect(bx, by, DAY_BLOCK_SIZE, DAY_BLOCK_SIZE, 3, 3);

            // Hover effect for day
            if (day.date().equals(hoveredDay)) {
                g2.setColor(HOVER_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(bx, by, DAY_BLOCK_SIZE, DAY_BLOCK_SIZE, 3, 3);
            }

            // Day number (centered)
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            String dayNum = String.valueOf(day.date().getDayOfMonth());
            FontMetrics fm = g2.getFontMetrics();
            int textX = bx + (DAY_BLOCK_SIZE - fm.stringWidth(dayNum)) / 2;
            int textY = by + (DAY_BLOCK_SIZE + fm.getAscent()) / 2 - 2;
            g2.drawString(dayNum, textX, textY);

            col++;
            if (col >= 7) {
                col = 0;
                row++;
            }
        }
    }

    private void drawBlock(Graphics2D g2, Rectangle bounds, DataHealth health) {
        Color fillColor = getBlockColor(health);

        // Draw rounded rectangle
        RoundRectangle2D.Double rect = new RoundRectangle2D.Double(
                bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        g2.setColor(fillColor);
        g2.fill(rect);

        // Hover effect
        if (health.month().equals(hoveredMonth)) {
            g2.setColor(HOVER_BORDER);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(rect);
        }

        // Selected effect
        if (health.month().equals(selectedMonth)) {
            g2.setColor(SELECTED_BORDER);
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(rect);
        }

        // Draw percentage text for partial data
        if (health.status() == DataStatus.PARTIAL) {
            int pct = (int) health.completenessPercent();
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            String text = pct + "%";
            FontMetrics fm = g2.getFontMetrics();
            int textX = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
            int textY = bounds.y + bounds.height / 2 + fm.getAscent() / 2 - 1;
            g2.drawString(text, textX, textY);
        }
    }

    private Color getBlockColor(DataHealth health) {
        return switch (health.status()) {
            case COMPLETE -> COMPLETE_COLOR;
            case PARTIAL -> {
                double pct = health.completenessPercent();
                if (pct >= 75) yield PARTIAL_COLOR_HIGH;
                else if (pct >= 50) yield PARTIAL_COLOR_MED;
                else yield PARTIAL_COLOR_LOW;
            }
            case MISSING -> MISSING_COLOR;
            case UNKNOWN -> UNKNOWN_COLOR;
        };
    }

    private void drawLegend(Graphics2D g2, int x, int y) {
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g2.setColor(new Color(150, 150, 150));

        int legendX = x;

        // Complete
        g2.setColor(COMPLETE_COLOR);
        g2.fillRoundRect(legendX, y, 14, 14, 4, 4);
        g2.setColor(new Color(150, 150, 150));
        g2.drawString("Complete", legendX + 18, y + 11);
        legendX += 80;

        // Partial
        g2.setColor(PARTIAL_COLOR_HIGH);
        g2.fillRoundRect(legendX, y, 14, 14, 4, 4);
        g2.setColor(new Color(150, 150, 150));
        g2.drawString("Partial", legendX + 18, y + 11);
        legendX += 60;

        // Missing
        g2.setColor(MISSING_COLOR);
        g2.fillRoundRect(legendX, y, 14, 14, 4, 4);
        g2.setColor(new Color(150, 150, 150));
        g2.drawString("Missing", legendX + 18, y + 11);
    }

    private void drawEmptyState(Graphics2D g2) {
        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        String msg;
        if (customMessage != null) {
            msg = customMessage;
        } else if (symbol == null) {
            msg = "Select a symbol and resolution";
        } else {
            msg = "No data available for " + symbol + " " + resolution;
        }

        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(msg)) / 2;
        int y = getHeight() / 2;
        g2.drawString(msg, x, y);
    }

    private YearMonth findMonthAt(int x, int y) {
        for (Map.Entry<YearMonth, Rectangle> entry : blockBounds.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private LocalDate findDayAt(int x, int y) {
        for (Map.Entry<LocalDate, Rectangle> entry : dayBounds.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void updateTooltip(MouseEvent e) {
        // Day tooltip takes priority
        if (hoveredDay != null) {
            DayInfo dayInfo = dailyDetails.stream()
                    .filter(d -> d.date().equals(hoveredDay))
                    .findFirst()
                    .orElse(null);
            if (dayInfo != null) {
                setToolTipText(dayInfo.details());
                return;
            }
        }

        if (hoveredMonth == null) {
            setToolTipText(null);
            return;
        }

        DataHealth health = healthData.stream()
                .filter(h -> h.month().equals(hoveredMonth))
                .findFirst()
                .orElse(null);

        if (health == null) {
            setToolTipText(null);
            return;
        }

        StringBuilder tip = new StringBuilder("<html>");
        tip.append("<b>").append(hoveredMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).append("</b><br>");
        tip.append("Status: ").append(health.status()).append("<br>");
        tip.append("Candles: ").append(health.actualCandles()).append(" / ").append(health.expectedCandles());
        tip.append(" (").append(String.format("%.1f%%", health.completenessPercent())).append(")<br>");

        if (!health.gaps().isEmpty()) {
            tip.append("Gaps: ").append(health.gaps().size()).append("<br>");
            // Show first few gaps
            int shown = 0;
            for (Gap gap : health.gaps()) {
                if (shown++ >= 3) {
                    tip.append("  ...and ").append(health.gaps().size() - 3).append(" more<br>");
                    break;
                }
                String start = Instant.ofEpochMilli(gap.startTimestamp())
                        .atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                tip.append("  ").append(start).append(" (").append(gap.missingCount()).append(" candles)<br>");
            }
        }

        tip.append("</html>");
        setToolTipText(tip.toString());
    }

    private void notifyMonthSelected(YearMonth month) {
        if (onMonthSelected == null) return;

        DataHealth health = healthData.stream()
                .filter(h -> h.month().equals(month))
                .findFirst()
                .orElse(null);

        if (health != null) {
            onMonthSelected.accept(health);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (healthData.isEmpty()) {
            return new Dimension(500, 300);
        }

        // Calculate month overview height
        Set<Integer> years = new HashSet<>();
        for (DataHealth h : healthData) {
            years.add(h.month().getYear());
        }

        int width = PADDING * 2 + YEAR_LABEL_WIDTH + 12 * (BLOCK_WIDTH + BLOCK_GAP);
        int monthSectionHeight = PADDING + 20 + years.size() * (BLOCK_HEIGHT + BLOCK_GAP + 4) + 40;

        // Daily section always takes roughly same space as month section (50/50 split)
        int dailySectionHeight;
        if (selectedMonth != null && !dailyDetails.isEmpty()) {
            // Calculate rows needed (7 columns, first row might be offset)
            int firstDow = selectedMonth.atDay(1).getDayOfWeek().getValue() - 1;
            int totalCells = firstDow + dailyDetails.size();
            int rows = (totalCells + 6) / 7;
            dailySectionHeight = 30 + 12 + rows * (DAY_BLOCK_SIZE + DAY_BLOCK_GAP) + 20;
        } else {
            // Placeholder space when no month selected
            dailySectionHeight = monthSectionHeight;
        }

        // Ensure minimum height for daily section
        dailySectionHeight = Math.max(dailySectionHeight, 120);

        return new Dimension(width, monthSectionHeight + dailySectionHeight);
    }
}
