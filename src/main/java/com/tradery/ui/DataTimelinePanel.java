package com.tradery.ui;

import com.tradery.data.DataType;
import com.tradery.data.PageState;
import com.tradery.data.page.DataPageManager;
import com.tradery.data.page.IndicatorPageManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Panel showing data dependencies as a block diagram and data availability as timeline bars.
 *
 * Top section: Dependency diagram showing:
 *   Consumers → Indicators → Pages
 *
 * Bottom section: Timeline bars for each data type showing time range and state.
 */
public class DataTimelinePanel extends JPanel {

    // Layout constants
    private static final int ROW_HEIGHT = 22;
    private static final int BLOCK_HEIGHT = 18;
    private static final int LABEL_WIDTH = 80;
    private static final int SECTION_GAP = 12;
    private static final int BLOCK_GAP = 6;
    private static final int BLOCK_PADDING = 8;
    private static final int CONNECTOR_GAP = 4;

    // Colors
    private static final Color COLOR_EMPTY = new Color(100, 100, 100);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_UPDATING = new Color(140, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_CONSUMER = new Color(100, 140, 180);
    private static final Color COLOR_INDICATOR = new Color(140, 100, 160);
    private static final Color COLOR_PAGE = new Color(100, 160, 120);
    private static final Color COLOR_CONNECTOR = new Color(80, 80, 80);
    private static final Color COLOR_LABEL = new Color(140, 140, 140);
    private static final Color COLOR_TIMELINE_BG = new Color(40, 40, 40);

    // Data
    private List<DataPageManager.PageInfo> dataPages = new ArrayList<>();
    private List<IndicatorPageManager.IndicatorPageInfo> indicatorPages = new ArrayList<>();

    // Computed layout
    private final Map<String, Rectangle> consumerBounds = new HashMap<>();
    private final Map<String, Rectangle> indicatorBounds = new HashMap<>();
    private final Map<String, Rectangle> pageBounds = new HashMap<>();

    // Time range for timeline
    private long globalStartTime = 0;
    private long globalEndTime = 0;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");

    public DataTimelinePanel() {
        setPreferredSize(new Dimension(400, 180));
        setMinimumSize(new Dimension(300, 150));
    }

    /**
     * Update the panel with current page data.
     */
    public void update(List<DataPageManager.PageInfo> dataPages,
                       List<IndicatorPageManager.IndicatorPageInfo> indicatorPages) {
        this.dataPages = new ArrayList<>(dataPages);
        this.indicatorPages = new ArrayList<>(indicatorPages);

        // Compute global time range
        computeGlobalTimeRange();

        repaint();
    }

    private void computeGlobalTimeRange() {
        globalStartTime = Long.MAX_VALUE;
        globalEndTime = Long.MIN_VALUE;

        for (DataPageManager.PageInfo page : dataPages) {
            if (page.startTime() > 0 && page.startTime() < globalStartTime) {
                globalStartTime = page.startTime();
            }
            if (page.endTime() > globalEndTime) {
                globalEndTime = page.endTime();
            }
        }

        for (IndicatorPageManager.IndicatorPageInfo page : indicatorPages) {
            if (page.startTime() > 0 && page.startTime() < globalStartTime) {
                globalStartTime = page.startTime();
            }
            if (page.endTime() > globalEndTime) {
                globalEndTime = page.endTime();
            }
        }

        // Default to last 24 hours if no data
        if (globalStartTime == Long.MAX_VALUE || globalEndTime == Long.MIN_VALUE) {
            globalEndTime = System.currentTimeMillis();
            globalStartTime = globalEndTime - 24 * 60 * 60 * 1000L;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int y = 8;

        // Clear bounds
        consumerBounds.clear();
        indicatorBounds.clear();
        pageBounds.clear();

        // Collect unique consumers and build dependency graph
        Set<String> allConsumers = new LinkedHashSet<>();
        Map<String, Set<String>> consumerToIndicators = new HashMap<>();
        Map<String, Set<String>> indicatorToPages = new HashMap<>();

        // From indicator pages
        for (IndicatorPageManager.IndicatorPageInfo ind : indicatorPages) {
            String indicatorKey = ind.type() + "(" + ind.params() + ")";
            String pageKey = ind.symbol() + "/" + ind.timeframe();

            for (String consumer : ind.consumers()) {
                allConsumers.add(consumer);
                consumerToIndicators.computeIfAbsent(consumer, k -> new LinkedHashSet<>()).add(indicatorKey);
            }
            indicatorToPages.computeIfAbsent(indicatorKey, k -> new LinkedHashSet<>()).add(pageKey);
        }

        // From data pages (direct consumers without indicators)
        for (DataPageManager.PageInfo page : dataPages) {
            String pageKey = page.symbol() + "/" + (page.timeframe() != null ? page.timeframe() : page.dataType().name());
            for (String consumer : page.consumers()) {
                // Skip indicator page consumers
                if (!consumer.startsWith("IndicatorPage:")) {
                    allConsumers.add(consumer);
                    // Direct page consumer (no indicator)
                    consumerToIndicators.computeIfAbsent(consumer, k -> new LinkedHashSet<>()).add("→" + pageKey);
                }
            }
        }

        // Get unique indicators and pages
        Set<String> allIndicators = new LinkedHashSet<>();
        for (Set<String> inds : consumerToIndicators.values()) {
            for (String ind : inds) {
                if (!ind.startsWith("→")) {
                    allIndicators.add(ind);
                }
            }
        }

        Set<String> allPages = new LinkedHashSet<>();
        for (Set<String> pages : indicatorToPages.values()) {
            allPages.addAll(pages);
        }
        // Add direct page references
        for (Set<String> inds : consumerToIndicators.values()) {
            for (String ind : inds) {
                if (ind.startsWith("→")) {
                    allPages.add(ind.substring(1));
                }
            }
        }

        // Draw dependency diagram if we have data
        if (!allConsumers.isEmpty() || !allIndicators.isEmpty() || !allPages.isEmpty()) {
            y = drawDependencyDiagram(g2, y, width, allConsumers, allIndicators, allPages,
                    consumerToIndicators, indicatorToPages);
            y += SECTION_GAP;
        }

        // Draw separator line
        g2.setColor(new Color(60, 60, 60));
        g2.drawLine(8, y, width - 8, y);
        y += SECTION_GAP;

        // Draw data timelines
        y = drawDataTimelines(g2, y, width);

        // Draw time axis
        y += 4;
        drawTimeAxis(g2, y, width);

        g2.dispose();
    }

    private int drawDependencyDiagram(Graphics2D g2, int startY, int width,
                                       Set<String> consumers, Set<String> indicators, Set<String> pages,
                                       Map<String, Set<String>> consumerToIndicators,
                                       Map<String, Set<String>> indicatorToPages) {
        int y = startY;
        int contentWidth = width - 16;
        int x = 8;

        Font labelFont = g2.getFont().deriveFont(Font.PLAIN, 10f);
        Font blockFont = g2.getFont().deriveFont(Font.PLAIN, 9f);
        FontMetrics fm = g2.getFontMetrics(blockFont);

        // Row 1: Consumers
        if (!consumers.isEmpty()) {
            g2.setColor(COLOR_LABEL);
            g2.setFont(labelFont);
            g2.drawString("Consumers:", x, y + 13);

            int blockX = x + LABEL_WIDTH;
            for (String consumer : consumers) {
                String displayName = shortenName(consumer);
                int blockWidth = fm.stringWidth(displayName) + BLOCK_PADDING * 2;

                if (blockX + blockWidth > width - 8) {
                    // Wrap to next line
                    y += ROW_HEIGHT;
                    blockX = x + LABEL_WIDTH;
                }

                drawBlock(g2, blockX, y, blockWidth, BLOCK_HEIGHT, displayName, COLOR_CONSUMER, blockFont);
                consumerBounds.put(consumer, new Rectangle(blockX, y, blockWidth, BLOCK_HEIGHT));
                blockX += blockWidth + BLOCK_GAP;
            }
            y += ROW_HEIGHT;
        }

        // Row 2: Indicators
        if (!indicators.isEmpty()) {
            g2.setColor(COLOR_LABEL);
            g2.setFont(labelFont);
            g2.drawString("Indicators:", x, y + 13);

            int blockX = x + LABEL_WIDTH;
            for (String indicator : indicators) {
                int blockWidth = fm.stringWidth(indicator) + BLOCK_PADDING * 2;

                if (blockX + blockWidth > width - 8) {
                    y += ROW_HEIGHT;
                    blockX = x + LABEL_WIDTH;
                }

                drawBlock(g2, blockX, y, blockWidth, BLOCK_HEIGHT, indicator, COLOR_INDICATOR, blockFont);
                indicatorBounds.put(indicator, new Rectangle(blockX, y, blockWidth, BLOCK_HEIGHT));
                blockX += blockWidth + BLOCK_GAP;
            }
            y += ROW_HEIGHT;
        }

        // Row 3: Pages (data sources)
        if (!pages.isEmpty()) {
            g2.setColor(COLOR_LABEL);
            g2.setFont(labelFont);
            g2.drawString("Pages:", x, y + 13);

            int blockX = x + LABEL_WIDTH;
            for (String page : pages) {
                int blockWidth = fm.stringWidth(page) + BLOCK_PADDING * 2;

                if (blockX + blockWidth > width - 8) {
                    y += ROW_HEIGHT;
                    blockX = x + LABEL_WIDTH;
                }

                drawBlock(g2, blockX, y, blockWidth, BLOCK_HEIGHT, page, COLOR_PAGE, blockFont);
                pageBounds.put(page, new Rectangle(blockX, y, blockWidth, BLOCK_HEIGHT));
                blockX += blockWidth + BLOCK_GAP;
            }
            y += ROW_HEIGHT;
        }

        // Draw connectors between layers
        g2.setColor(COLOR_CONNECTOR);
        g2.setStroke(new BasicStroke(1f));

        // Consumer → Indicator connectors
        for (Map.Entry<String, Set<String>> entry : consumerToIndicators.entrySet()) {
            Rectangle consumerRect = consumerBounds.get(entry.getKey());
            if (consumerRect == null) continue;

            for (String indicator : entry.getValue()) {
                if (indicator.startsWith("→")) {
                    // Direct page reference
                    String pageKey = indicator.substring(1);
                    Rectangle pageRect = pageBounds.get(pageKey);
                    if (pageRect != null) {
                        drawConnector(g2, consumerRect, pageRect);
                    }
                } else {
                    Rectangle indicatorRect = indicatorBounds.get(indicator);
                    if (indicatorRect != null) {
                        drawConnector(g2, consumerRect, indicatorRect);
                    }
                }
            }
        }

        // Indicator → Page connectors
        for (Map.Entry<String, Set<String>> entry : indicatorToPages.entrySet()) {
            Rectangle indicatorRect = indicatorBounds.get(entry.getKey());
            if (indicatorRect == null) continue;

            for (String page : entry.getValue()) {
                Rectangle pageRect = pageBounds.get(page);
                if (pageRect != null) {
                    drawConnector(g2, indicatorRect, pageRect);
                }
            }
        }

        return y;
    }

    private void drawBlock(Graphics2D g2, int x, int y, int width, int height,
                           String text, Color color, Font font) {
        // Background
        g2.setColor(color.darker().darker());
        g2.fillRoundRect(x, y, width, height, 4, 4);

        // Border
        g2.setColor(color);
        g2.drawRoundRect(x, y, width, height, 4, 4);

        // Text
        g2.setFont(font);
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, textX, textY);
    }

    private void drawConnector(Graphics2D g2, Rectangle from, Rectangle to) {
        int fromX = from.x + from.width / 2;
        int fromY = from.y + from.height;
        int toX = to.x + to.width / 2;
        int toY = to.y;

        // Simple curved connector
        Path2D path = new Path2D.Float();
        path.moveTo(fromX, fromY);

        int midY = (fromY + toY) / 2;
        path.curveTo(fromX, midY, toX, midY, toX, toY);

        g2.draw(path);
    }

    private int drawDataTimelines(Graphics2D g2, int startY, int width) {
        int y = startY;
        int barAreaX = 8 + LABEL_WIDTH;
        int barAreaWidth = width - barAreaX - 8;

        Font labelFont = g2.getFont().deriveFont(Font.PLAIN, 10f);
        Font statusFont = g2.getFont().deriveFont(Font.BOLD, 9f);

        // Group data pages by type
        Map<DataType, List<DataPageManager.PageInfo>> pagesByType = new LinkedHashMap<>();
        for (DataPageManager.PageInfo page : dataPages) {
            pagesByType.computeIfAbsent(page.dataType(), k -> new ArrayList<>()).add(page);
        }

        if (pagesByType.isEmpty()) {
            g2.setColor(COLOR_LABEL);
            g2.setFont(labelFont);
            g2.drawString("No data pages active", barAreaX, y + 12);
            return y + ROW_HEIGHT;
        }

        for (Map.Entry<DataType, List<DataPageManager.PageInfo>> entry : pagesByType.entrySet()) {
            DataType dataType = entry.getKey();
            List<DataPageManager.PageInfo> pages = entry.getValue();

            // Label
            g2.setColor(COLOR_LABEL);
            g2.setFont(labelFont);
            g2.drawString(dataType.getDisplayName() + ":", 8, y + 13);

            // Draw timeline bar background
            g2.setColor(COLOR_TIMELINE_BG);
            g2.fillRoundRect(barAreaX, y + 2, barAreaWidth, BLOCK_HEIGHT, 4, 4);

            // Draw bars for each page
            for (DataPageManager.PageInfo page : pages) {
                if (page.startTime() <= 0 || page.endTime() <= 0) continue;

                int barX = timeToX(page.startTime(), barAreaX, barAreaWidth);
                int barEndX = timeToX(page.endTime(), barAreaX, barAreaWidth);
                int barWidth = Math.max(barEndX - barX, 4);

                Color stateColor = getColorForState(page.state());
                g2.setColor(stateColor.darker());
                g2.fillRoundRect(barX, y + 2, barWidth, BLOCK_HEIGHT, 4, 4);
                g2.setColor(stateColor);
                g2.drawRoundRect(barX, y + 2, barWidth, BLOCK_HEIGHT, 4, 4);

                // Status text in bar
                String statusText = getStatusText(page.state());
                g2.setFont(statusFont);
                FontMetrics fm = g2.getFontMetrics();
                if (fm.stringWidth(statusText) < barWidth - 4) {
                    g2.setColor(Color.WHITE);
                    int textX = barX + (barWidth - fm.stringWidth(statusText)) / 2;
                    int textY = y + 2 + (BLOCK_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(statusText, textX, textY);
                }
            }

            y += ROW_HEIGHT;
        }

        return y;
    }

    private void drawTimeAxis(Graphics2D g2, int y, int width) {
        int barAreaX = 8 + LABEL_WIDTH;
        int barAreaWidth = width - barAreaX - 8;

        g2.setColor(COLOR_LABEL);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));

        // Start date
        String startLabel = dateFormat.format(new Date(globalStartTime));
        g2.drawString(startLabel, barAreaX, y + 10);

        // End date
        String endLabel = dateFormat.format(new Date(globalEndTime));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(endLabel, barAreaX + barAreaWidth - fm.stringWidth(endLabel), y + 10);

        // Arrow line
        g2.setColor(new Color(60, 60, 60));
        int lineY = y + 5;
        g2.drawLine(barAreaX + fm.stringWidth(startLabel) + 4, lineY,
                barAreaX + barAreaWidth - fm.stringWidth(endLabel) - 4, lineY);

        // Arrow head
        int arrowX = barAreaX + barAreaWidth - fm.stringWidth(endLabel) - 4;
        g2.drawLine(arrowX - 4, lineY - 3, arrowX, lineY);
        g2.drawLine(arrowX - 4, lineY + 3, arrowX, lineY);
    }

    private int timeToX(long timestamp, int barAreaX, int barAreaWidth) {
        if (globalEndTime == globalStartTime) return barAreaX;
        double ratio = (double) (timestamp - globalStartTime) / (globalEndTime - globalStartTime);
        return barAreaX + (int) (ratio * barAreaWidth);
    }

    private String shortenName(String name) {
        // Remove common prefixes
        if (name.startsWith("com.tradery.ui.")) {
            name = name.substring("com.tradery.ui.".length());
        }
        if (name.startsWith("com.tradery.")) {
            name = name.substring("com.tradery.".length());
        }
        // Truncate if too long
        if (name.length() > 20) {
            return name.substring(0, 17) + "...";
        }
        return name;
    }

    private static Color getColorForState(PageState state) {
        return switch (state) {
            case EMPTY -> COLOR_EMPTY;
            case LOADING -> COLOR_LOADING;
            case READY -> COLOR_READY;
            case UPDATING -> COLOR_UPDATING;
            case ERROR -> COLOR_ERROR;
        };
    }

    private static String getStatusText(PageState state) {
        return switch (state) {
            case EMPTY -> "EMPTY";
            case LOADING -> "LOADING";
            case READY -> "READY";
            case UPDATING -> "UPDATING";
            case ERROR -> "ERROR";
        };
    }
}
