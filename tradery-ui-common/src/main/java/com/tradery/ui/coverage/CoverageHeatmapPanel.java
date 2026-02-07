package com.tradery.ui.coverage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.YearMonth;
import java.util.*;
import java.util.List;

/**
 * Hourly-resolution coverage heatmap.
 * Fixed 31-column grid (one per day-of-month), one row-block per month (newest on top).
 * Day columns expand to fill available width. Vertical scroll only.
 */
public class CoverageHeatmapPanel extends JPanel implements Scrollable {

    private static final int DAY_HEIGHT = 48;
    private static final int HOUR_HEIGHT = 2;
    private static final int MONTH_LABEL_WIDTH = 60;
    private static final int HEADER_HEIGHT = 16;
    private static final int MONTH_GAP = 8;
    private static final int PADDING = 8;
    private static final int DAY_GAP = 2; // gap between day columns

    private static final Color MISSING_COLOR = new Color(30, 30, 30);

    // Sorted newest-first
    private final List<YearMonth> months = new ArrayList<>();
    // [day-1][hour] per month — null means non-existent day
    private final Map<YearMonth, CoverageLevel[][]> data = new LinkedHashMap<>();

    public CoverageHeatmapPanel() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateTooltip(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setToolTipText(null);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /**
     * Set coverage data. The panel groups slices by year-month and repaints.
     */
    public void setData(List<CoverageSlice> slices) {
        months.clear();
        data.clear();

        if (slices == null || slices.isEmpty()) {
            revalidate();
            repaint();
            return;
        }

        // Group slices by YearMonth
        Map<YearMonth, List<CoverageSlice>> grouped = new TreeMap<>(Comparator.reverseOrder());
        for (CoverageSlice s : slices) {
            YearMonth ym = YearMonth.of(s.year(), s.month());
            grouped.computeIfAbsent(ym, k -> new ArrayList<>()).add(s);
        }

        for (Map.Entry<YearMonth, List<CoverageSlice>> entry : grouped.entrySet()) {
            YearMonth ym = entry.getKey();
            months.add(ym);

            int daysInMonth = ym.lengthOfMonth();
            CoverageLevel[][] grid = new CoverageLevel[31][24];
            // Initialize valid days to MISSING
            for (int d = 0; d < daysInMonth; d++) {
                Arrays.fill(grid[d], CoverageLevel.MISSING);
            }

            for (CoverageSlice s : entry.getValue()) {
                int d = s.day() - 1;
                int h = s.hour();
                if (d >= 0 && d < daysInMonth && h >= 0 && h < 24) {
                    grid[d][h] = s.level();
                }
            }

            data.put(ym, grid);
        }

        revalidate();
        repaint();
    }

    // ========== Layout ==========

    /** Compute day column width from actual panel width. */
    private int dayWidth() {
        int gridWidth = getWidth() - PADDING * 2 - MONTH_LABEL_WIDTH;
        return Math.max(4, gridWidth / 31);
    }

    @Override
    public Dimension getPreferredSize() {
        int height;
        if (months.isEmpty()) {
            height = 60;
        } else {
            height = PADDING + HEADER_HEIGHT + months.size() * (DAY_HEIGHT + MONTH_GAP) + PADDING;
        }
        // Width doesn't matter much — we track viewport width via Scrollable
        return new Dimension(100, height);
    }

    // ========== Scrollable — track viewport width, scroll vertically ==========

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? DAY_HEIGHT + MONTH_GAP : 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // always fill viewport width
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        // Only fill height if content is shorter than viewport
        Container viewport = getParent();
        if (viewport instanceof JViewport) {
            return getPreferredSize().height < viewport.getHeight();
        }
        return false;
    }

    // ========== Painting ==========

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (months.isEmpty()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color accentColor = getAccentColor();
        Color partialColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 128);
        Color fg = UIManager.getColor("Label.foreground");
        Color dimFg = UIManager.getColor("Label.disabledForeground");
        if (dimFg == null) dimFg = Color.GRAY;

        int dw = dayWidth();
        int gridX = PADDING + MONTH_LABEL_WIDTH;

        // Draw day number header (1-31)
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g2.setColor(dimFg);
        FontMetrics fm = g2.getFontMetrics();
        for (int d = 0; d < 31; d++) {
            String label = String.valueOf(d + 1);
            int lw = fm.stringWidth(label);
            int cx = gridX + d * dw + (dw - DAY_GAP - lw) / 2;
            g2.drawString(label, cx, PADDING + HEADER_HEIGHT - 3);
        }

        // Draw each month row
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        int y = PADDING + HEADER_HEIGHT;

        for (YearMonth ym : months) {
            CoverageLevel[][] grid = data.get(ym);
            int daysInMonth = ym.lengthOfMonth();

            // Month label
            g2.setColor(fg);
            String monthLabel = String.format("%d-%02d", ym.getYear(), ym.getMonthValue());
            g2.drawString(monthLabel, PADDING, y + DAY_HEIGHT / 2 + 4);

            // Draw day columns
            for (int d = 0; d < 31; d++) {
                int cellX = gridX + d * dw;

                if (d >= daysInMonth) {
                    continue;
                }

                for (int h = 0; h < 24; h++) {
                    CoverageLevel level = grid[d][h];
                    Color c = switch (level) {
                        case FULL -> accentColor;
                        case PARTIAL -> partialColor;
                        case MISSING -> MISSING_COLOR;
                    };

                    g2.setColor(c);
                    g2.fillRect(cellX, y + h * HOUR_HEIGHT, dw - DAY_GAP, HOUR_HEIGHT - 1);
                }
            }

            y += DAY_HEIGHT + MONTH_GAP;
        }

        g2.dispose();
    }

    // ========== Tooltip ==========

    private void updateTooltip(int mx, int my) {
        int dw = dayWidth();
        int gridX = PADDING + MONTH_LABEL_WIDTH;
        int y = PADDING + HEADER_HEIGHT;

        for (YearMonth ym : months) {
            int daysInMonth = ym.lengthOfMonth();

            if (my >= y && my < y + DAY_HEIGHT) {
                int d = (mx - gridX) / dw;
                if (d < 0 || d >= daysInMonth || mx < gridX) {
                    setToolTipText(null);
                    return;
                }
                int h = (my - y) / HOUR_HEIGHT;
                if (h < 0 || h >= 24) {
                    setToolTipText(null);
                    return;
                }

                CoverageLevel[][] grid = data.get(ym);
                CoverageLevel level = grid[d][h];
                String status = switch (level) {
                    case FULL -> "loaded";
                    case PARTIAL -> "partial";
                    case MISSING -> "missing";
                };

                String monthName = ym.getMonth().toString();
                monthName = monthName.charAt(0) + monthName.substring(1).toLowerCase();
                String tip = String.format("%s %d, %02d:00\u2013%02d:00 \u2014 %s",
                        monthName, d + 1, h, h + 1, status);
                setToolTipText(tip);
                return;
            }

            y += DAY_HEIGHT + MONTH_GAP;
        }

        setToolTipText(null);
    }

    private Color getAccentColor() {
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent == null) {
            accent = new Color(0, 120, 212);
        }
        return accent;
    }
}
