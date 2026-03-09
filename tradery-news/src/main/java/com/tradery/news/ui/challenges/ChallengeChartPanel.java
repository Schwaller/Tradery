package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight line chart for challenge fields over time.
 * Single-entity mode: one line per field. Multi-entity mode: one line per entity.
 * Both modes share the same crosshair, adaptive time axis, and grid rendering.
 */
public class ChallengeChartPanel extends JPanel {

    private static final int CHART_HEIGHT = 200;
    private static final int LEFT_MARGIN = 10;
    private static final int RIGHT_MARGIN = 50;
    private static final int TOP_MARGIN = 22;
    private static final int BOTTOM_MARGIN = 18;

    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);

    private static final Color[] FIELD_COLORS = {
        new Color(88, 157, 246),   // blue
        new Color(80, 190, 120),   // green
        new Color(230, 160, 60),   // amber
        new Color(200, 100, 160),  // pink
        new Color(140, 120, 220),  // purple
        new Color(100, 200, 200),  // teal
        new Color(220, 120, 80),   // orange
        new Color(160, 160, 100),  // olive
    };

    record DataPoint(long timestamp, double rawValue, double normalized) {}
    record EntitySeries(String name, Color color, List<DataPoint> points) {}

    private final String label;
    private final Color lineColor;
    private final double minVal;
    private final double maxVal;
    private final ChallengeOutput.Field.GapMode gapMode;

    // Single-entity mode
    private final List<DataPoint> points = new ArrayList<>();

    // Multi-entity mode
    private final List<EntitySeries> entitySeries = new ArrayList<>();
    private final List<Long> allTimestamps = new ArrayList<>();

    // Crosshair state — index into points (single) or allTimestamps (multi)
    private int hoveredIndex = -1;

    /** Single-entity: one field, one line. */
    public ChallengeChartPanel(ChallengeOutput.Field field, List<ChallengeResult> results, int colorIndex) {
        setOpaque(true);
        setPreferredSize(new Dimension(0, CHART_HEIGHT));
        setMinimumSize(new Dimension(0, CHART_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, CHART_HEIGHT));

        this.label = field.label() != null ? field.label() : field.name();
        this.lineColor = FIELD_COLORS[colorIndex % FIELD_COLORS.length];
        this.minVal = field.minValue();
        this.maxVal = field.maxValue();
        this.gapMode = field.gapMode();

        double range = maxVal - minVal;
        for (ChallengeResult r : results) {
            if (r.fields() == null || !r.fields().containsKey(field.name())) continue;
            try {
                double val = Double.parseDouble(r.fields().get(field.name()));
                double norm = range > 0 ? (val - minVal) / range : 0.5;
                norm = Math.max(0, Math.min(1, norm));
                points.add(new DataPoint(r.timestamp(), val, norm));
            } catch (NumberFormatException ignored) {}
        }

        installMouseHandler();
    }

    /** Multi-entity: one field, one line per entity across runs. */
    public ChallengeChartPanel(ChallengeOutput.Field field, ChallengeOutput.Field nameField,
                               List<ChallengeResult> results, int colorIndex) {
        setOpaque(true);
        setPreferredSize(new Dimension(0, CHART_HEIGHT));
        setMinimumSize(new Dimension(0, CHART_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, CHART_HEIGHT));

        this.label = field.label() != null ? field.label() : field.name();
        this.lineColor = FIELD_COLORS[colorIndex % FIELD_COLORS.length];
        this.minVal = field.minValue();
        this.maxVal = field.maxValue();
        this.gapMode = field.gapMode();

        double range = maxVal - minVal;
        LinkedHashMap<String, List<DataPoint>> entityData = new LinkedHashMap<>();

        for (ChallengeResult r : results) {
            if (r.itemResults() == null) continue;
            for (Map<String, String> item : r.itemResults()) {
                if ("removed".equals(item.get("_status"))) continue; // skip removed items
                String name = nameField != null ? item.getOrDefault(nameField.name(), "?") : "?";
                String valStr = item.get(field.name());
                if (valStr == null) continue;
                try {
                    double val = Double.parseDouble(valStr);
                    double norm = range > 0 ? (val - minVal) / range : 0.5;
                    norm = Math.max(0, Math.min(1, norm));
                    entityData.computeIfAbsent(name, k -> new ArrayList<>())
                        .add(new DataPoint(r.timestamp(), val, norm));
                    if (!allTimestamps.contains(r.timestamp())) allTimestamps.add(r.timestamp());
                } catch (NumberFormatException ignored) {}
            }
        }
        allTimestamps.sort(Long::compare);

        // For ZERO mode: fill missing timestamps with 0 so lines drop to zero
        if (gapMode == ChallengeOutput.Field.GapMode.ZERO && allTimestamps.size() > 1) {
            double zeroNorm = range > 0 ? (0 - minVal) / range : 0;
            zeroNorm = Math.max(0, Math.min(1, zeroNorm));
            for (var entry : entityData.entrySet()) {
                List<DataPoint> pts = entry.getValue();
                java.util.Set<Long> existing = new java.util.HashSet<>();
                for (DataPoint dp : pts) existing.add(dp.timestamp);
                for (long ts : allTimestamps) {
                    if (!existing.contains(ts)) {
                        pts.add(new DataPoint(ts, 0, zeroNorm));
                    }
                }
                pts.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
            }
        }

        int ci = 0;
        for (var entry : entityData.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Color c = FIELD_COLORS[ci % FIELD_COLORS.length];
                entitySeries.add(new EntitySeries(entry.getKey(), c, entry.getValue()));
                ci++;
            }
        }

        installMouseHandler();
    }

    private void installMouseHandler() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newIdx = findNearestIndex(e.getX());
                if (newIdx != hoveredIndex) {
                    hoveredIndex = newIdx;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredIndex != -1) {
                    hoveredIndex = -1;
                    repaint();
                }
            }
        };
        addMouseMotionListener(mouseHandler);
        addMouseListener(mouseHandler);
    }

    private boolean isMultiEntity() {
        return !entitySeries.isEmpty();
    }

    public boolean hasData() {
        if (isMultiEntity()) return !allTimestamps.isEmpty() && !entitySeries.isEmpty();
        return points.size() >= 2;
    }

    /**
     * Find nearest data point / timestamp index for the given x pixel.
     */
    private int findNearestIndex(int mouseX) {
        List<Long> timestamps = isMultiEntity() ? allTimestamps : pointTimestamps();
        if (timestamps.size() < 2) return timestamps.size() == 1 ? 0 : -1;

        int chartW = getWidth() - LEFT_MARGIN - RIGHT_MARGIN;
        if (chartW <= 0) return -1;

        long minTime = timestamps.getFirst();
        long maxTime = timestamps.getLast();
        long timeRange = maxTime - minTime;
        if (timeRange <= 0) return 0;

        double frac = (mouseX - LEFT_MARGIN) / (double) chartW;
        long mouseTs = minTime + (long) (frac * timeRange);

        // Binary search
        int lo = 0, hi = timestamps.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps.get(mid) < mouseTs) lo = mid + 1;
            else hi = mid;
        }
        if (lo > 0 && Math.abs(timestamps.get(lo - 1) - mouseTs) < Math.abs(timestamps.get(lo) - mouseTs)) {
            return lo - 1;
        }
        return lo;
    }

    private List<Long> pointTimestamps() {
        return points.stream().map(DataPoint::timestamp).toList();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        Color bg = UIManager.getColor("Panel.background");
        g2.setColor(bg);
        g2.fillRect(0, 0, w, h);

        int chartX = LEFT_MARGIN;
        int chartY = TOP_MARGIN;
        int chartW = w - LEFT_MARGIN - RIGHT_MARGIN;
        int chartH = h - TOP_MARGIN - BOTTOM_MARGIN;
        if (chartW < 30 || chartH < 20) { g2.dispose(); return; }

        Color gridColor = darker(bg, 0.06f);
        Color textColor = UIManager.getColor("Label.disabledForeground");
        if (textColor == null) textColor = Color.GRAY;
        Color crosshairColor = new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 120);

        // Grid + Y-axis
        paintGrid(g2, chartX, chartY, chartW, chartH, gridColor, textColor);

        if (isMultiEntity()) {
            paintMultiEntity(g2, chartX, chartY, chartW, chartH, bg, gridColor, textColor, crosshairColor);
        } else {
            paintSingleEntity(g2, chartX, chartY, chartW, chartH, bg, gridColor, textColor, crosshairColor);
        }

        g2.dispose();
    }

    private void paintGrid(Graphics2D g2, int chartX, int chartY, int chartW, int chartH,
                           Color gridColor, Color textColor) {
        // Title
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.setColor(lineColor);
        g2.drawString(label, chartX + 2, 13);

        // Grid lines + right Y-axis labels
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        FontMetrics fm = g2.getFontMetrics();
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= 4; i++) {
            double frac = i / 4.0;
            int y = chartY + chartH - (int) (chartH * frac);
            g2.setColor(gridColor);
            g2.drawLine(chartX, y, chartX + chartW, y);
            double val = minVal + (maxVal - minVal) * frac;
            g2.setColor(textColor);
            g2.drawString(formatValue(val), chartX + chartW + 6, y + fm.getAscent() / 2 - 1);
        }
    }

    // ==================== Single Entity ====================

    private void paintSingleEntity(Graphics2D g2, int chartX, int chartY, int chartW, int chartH,
                                   Color bg, Color gridColor, Color textColor, Color crosshairColor) {
        if (points.size() < 2) return;

        // Latest value next to title
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        int titleW = g2.getFontMetrics().stringWidth(label);
        DataPoint lastPt = points.getLast();
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(textColor);
        g2.drawString(formatValue(lastPt.rawValue), chartX + 2 + titleW + 6, 13);

        long minTime = points.getFirst().timestamp;
        long maxTime = points.getLast().timestamp;
        long timeRange = maxTime - minTime;
        if (timeRange <= 0) return;

        // Time axis
        drawTimeAxis(g2, chartX, chartY, chartW, chartH, minTime, timeRange, points, textColor, gridColor);

        // Area fill + line
        Path2D linePath = new Path2D.Double();
        Path2D fillPath = new Path2D.Double();
        boolean first = true;
        for (DataPoint dp : points) {
            int x = tsToX(dp.timestamp, chartX, chartW, minTime, timeRange);
            int y = normToY(dp.normalized, chartY, chartH);
            if (first) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartY + chartH);
                fillPath.lineTo(x, y);
                first = false;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(tsToX(lastPt.timestamp, chartX, chartW, minTime, timeRange), chartY + chartH);
        fillPath.closePath();

        g2.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 18));
        g2.fill(fillPath);
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(linePath);

        // Dots
        for (int i = 0; i < points.size(); i++) {
            DataPoint dp = points.get(i);
            int x = tsToX(dp.timestamp, chartX, chartW, minTime, timeRange);
            int y = normToY(dp.normalized, chartY, chartH);
            int r = (i == hoveredIndex) ? 4 : 2;
            g2.setColor(bg);
            g2.fillOval(x - r - 1, y - r - 1, r * 2 + 3, r * 2 + 3);
            g2.setColor(lineColor);
            g2.fillOval(x - r, y - r, r * 2 + 1, r * 2 + 1);
        }

        // Crosshair
        if (hoveredIndex >= 0 && hoveredIndex < points.size()) {
            DataPoint hovered = points.get(hoveredIndex);
            int cx = tsToX(hovered.timestamp, chartX, chartW, minTime, timeRange);
            int cy = normToY(hovered.normalized, chartY, chartH);
            paintCrosshair(g2, chartX, chartY, chartW, chartH, cx, bg, textColor, crosshairColor,
                hovered.timestamp, List.of(Map.entry(lineColor, hovered)));
        }
    }

    // ==================== Multi Entity ====================

    private void paintMultiEntity(Graphics2D g2, int chartX, int chartY, int chartW, int chartH,
                                  Color bg, Color gridColor, Color textColor, Color crosshairColor) {
        if (allTimestamps.isEmpty()) return;

        // Legend next to title
        paintEntityLegend(g2, chartX, textColor);

        long minTime, maxTime, timeRange;
        if (allTimestamps.size() == 1) {
            // Single timestamp — pad time range so dots render centered
            minTime = allTimestamps.getFirst() - DAY_MS;
            maxTime = allTimestamps.getFirst() + DAY_MS;
            timeRange = maxTime - minTime;
        } else {
            minTime = allTimestamps.getFirst();
            maxTime = allTimestamps.getLast();
            timeRange = maxTime - minTime;
        }
        if (timeRange <= 0) return;

        // Time axis
        List<DataPoint> timeTicks = allTimestamps.stream()
            .map(ts -> new DataPoint(ts, 0, 0)).toList();
        drawTimeAxis(g2, chartX, chartY, chartW, chartH, minTime, timeRange, timeTicks, textColor, gridColor);

        // Lines + dots per entity
        Stroke solidStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Stroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0, new float[]{4, 4}, 0);

        for (EntitySeries es : entitySeries) {
            if (es.points.size() >= 2) {
                if (gapMode == ChallengeOutput.Field.GapMode.GAP) {
                    // GAP mode: solid segments between consecutive present points,
                    // dashed segments where timestamps are missing
                    java.util.Set<Long> presentTs = new java.util.HashSet<>();
                    for (DataPoint dp : es.points) presentTs.add(dp.timestamp);

                    for (int i = 0; i < es.points.size() - 1; i++) {
                        DataPoint a = es.points.get(i);
                        DataPoint b = es.points.get(i + 1);
                        int x1 = tsToX(a.timestamp, chartX, chartW, minTime, timeRange);
                        int y1 = normToY(a.normalized, chartY, chartH);
                        int x2 = tsToX(b.timestamp, chartX, chartW, minTime, timeRange);
                        int y2 = normToY(b.normalized, chartY, chartH);

                        // Check if there's a gap (missing timestamps between a and b)
                        boolean hasGap = false;
                        for (long ts : allTimestamps) {
                            if (ts > a.timestamp && ts < b.timestamp && !presentTs.contains(ts)) {
                                hasGap = true;
                                break;
                            }
                        }

                        g2.setColor(hasGap ? new Color(es.color.getRed(), es.color.getGreen(),
                            es.color.getBlue(), 100) : es.color);
                        g2.setStroke(hasGap ? dashedStroke : solidStroke);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                } else {
                    // CONNECT or ZERO: continuous solid line
                    Path2D path = new Path2D.Double();
                    boolean first = true;
                    for (DataPoint dp : es.points) {
                        int x = tsToX(dp.timestamp, chartX, chartW, minTime, timeRange);
                        int y = normToY(dp.normalized, chartY, chartH);
                        if (first) { path.moveTo(x, y); first = false; }
                        else path.lineTo(x, y);
                    }
                    g2.setColor(es.color);
                    g2.setStroke(solidStroke);
                    g2.draw(path);
                }
            }

            // Dots (always, at actual data points)
            for (DataPoint dp : es.points) {
                int x = tsToX(dp.timestamp, chartX, chartW, minTime, timeRange);
                int y = normToY(dp.normalized, chartY, chartH);
                g2.setColor(bg);
                g2.fillOval(x - 3, y - 3, 7, 7);
                g2.setColor(es.color);
                g2.fillOval(x - 2, y - 2, 5, 5);
            }
        }

        // Crosshair — snap to nearest timestamp, show all entity values
        if (hoveredIndex >= 0 && hoveredIndex < allTimestamps.size()) {
            long hoveredTs = allTimestamps.get(hoveredIndex);
            int cx = tsToX(hoveredTs, chartX, chartW, minTime, timeRange);

            // Collect all entity values at this timestamp
            List<Map.Entry<Color, DataPoint>> hoveredPoints = new ArrayList<>();
            for (EntitySeries es : entitySeries) {
                for (DataPoint dp : es.points) {
                    if (dp.timestamp == hoveredTs) {
                        hoveredPoints.add(Map.entry(es.color, dp));
                        break;
                    }
                }
            }

            // Enlarge hovered dots
            for (var entry : hoveredPoints) {
                DataPoint dp = entry.getValue();
                int x = tsToX(dp.timestamp, chartX, chartW, minTime, timeRange);
                int y = normToY(dp.normalized, chartY, chartH);
                g2.setColor(bg);
                g2.fillOval(x - 5, y - 5, 11, 11);
                g2.setColor(entry.getKey());
                g2.fillOval(x - 4, y - 4, 9, 9);
            }

            paintCrosshair(g2, chartX, chartY, chartW, chartH, cx, bg, textColor, crosshairColor,
                hoveredTs, hoveredPoints);
        }
    }

    private void paintEntityLegend(Graphics2D g2, int chartX, Color textColor) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        int x = chartX + 2 + g2.getFontMetrics().stringWidth(label) + 12;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        FontMetrics fm = g2.getFontMetrics();

        for (EntitySeries es : entitySeries) {
            String name = es.name.length() > 18 ? es.name.substring(0, 16) + ".." : es.name;
            int nameW = fm.stringWidth(name) + 12;
            if (x + nameW > getWidth() - RIGHT_MARGIN) break;

            g2.setColor(es.color);
            g2.fillOval(x, 9, 6, 6);
            g2.setColor(textColor);
            g2.drawString(name, x + 9, 13);
            x += nameW;
        }
    }

    // ==================== Shared Rendering ====================

    /**
     * Crosshair at cx with value pills for each hovered point.
     */
    private void paintCrosshair(Graphics2D g2, int chartX, int chartY, int chartW, int chartH,
                                int cx, Color bg, Color textColor, Color crosshairColor,
                                long timestamp, List<Map.Entry<Color, DataPoint>> hoveredPoints) {
        // Vertical dashed line
        g2.setColor(crosshairColor);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 3}, 0));
        g2.drawLine(cx, chartY, cx, chartY + chartH);

        // Value pills on right axis — stack them if multiple
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        int pillY = 0;
        for (var entry : hoveredPoints) {
            Color c = entry.getKey();
            DataPoint dp = entry.getValue();
            int cy = normToY(dp.normalized, chartY, chartH);

            // Horizontal line to right axis
            g2.setColor(crosshairColor);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 3}, 0));
            g2.drawLine(cx, cy, chartX + chartW, cy);

            // Value pill
            String valStr = formatValue(dp.rawValue);
            int labelW = fm.stringWidth(valStr) + 6;
            int labelH = fm.getHeight() + 2;
            int labelX = chartX + chartW + 2;
            // Avoid overlapping pills
            int targetY = cy - labelH / 2;
            if (pillY > 0 && targetY < pillY + 2) targetY = pillY + 2;
            g2.setColor(c);
            g2.fillRoundRect(labelX, targetY, labelW, labelH, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(valStr, labelX + 3, targetY + fm.getAscent() + 1);
            pillY = targetY + labelH;
        }

        // Time pill below x-axis
        String timeStr = formatTimestamp(timestamp);
        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
        fm = g2.getFontMetrics();
        int timeLabelW = fm.stringWidth(timeStr) + 6;
        int timeLabelH = fm.getHeight() + 2;
        int timeLabelX = cx - timeLabelW / 2;
        int timeLabelY = chartY + chartH + 1;
        g2.setColor(darker(bg, 0.15f));
        g2.fillRoundRect(timeLabelX, timeLabelY, timeLabelW, timeLabelH, 4, 4);
        g2.setColor(textColor);
        g2.drawString(timeStr, timeLabelX + 3, timeLabelY + fm.getAscent() + 1);
    }

    /**
     * Adaptive time axis from data points.
     */
    private void drawTimeAxis(Graphics2D g2, int chartX, int chartY, int chartW, int chartH,
                              long minTime, long timeRange, List<DataPoint> ticks,
                              Color textColor, Color gridColor) {
        if (ticks.isEmpty()) return;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        FontMetrics fm = g2.getFontMetrics();

        int maxLabels = Math.max(2, chartW / 80);
        int step = Math.max(1, (ticks.size() - 1) / maxLabels);

        String prevDateStr = null;
        for (int i = 0; i < ticks.size(); i += step) {
            long ts = ticks.get(i).timestamp;
            int x = tsToX(ts, chartX, chartW, minTime, timeRange);
            String lbl = formatAdaptiveTimeLabel(ts, timeRange, prevDateStr);
            prevDateStr = formatDateOnly(ts);
            int strW = fm.stringWidth(lbl);
            g2.setColor(textColor);
            g2.drawString(lbl, Math.max(chartX, Math.min(x - strW / 2, chartX + chartW - strW)),
                chartY + chartH + fm.getAscent() + 3);
            g2.setColor(gridColor);
            g2.drawLine(x, chartY + chartH, x, chartY + chartH + 2);
        }

        // Ensure last tick is labeled
        int lastIdx = ticks.size() - 1;
        if (lastIdx > 0 && lastIdx % step != 0) {
            long ts = ticks.get(lastIdx).timestamp;
            int x = tsToX(ts, chartX, chartW, minTime, timeRange);
            String lbl = formatAdaptiveTimeLabel(ts, timeRange, prevDateStr);
            int strW = fm.stringWidth(lbl);
            g2.setColor(textColor);
            g2.drawString(lbl, x - strW, chartY + chartH + fm.getAscent() + 3);
        }
    }

    // ==================== Formatting ====================

    private String formatAdaptiveTimeLabel(long timestamp, long timeRange, String prevDateStr) {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        String currentDate = formatDateOnly(timestamp);

        if (timeRange > 30 * DAY_MS) {
            if (ldt.getMonthValue() == 1 && ldt.getDayOfMonth() <= 2) {
                return String.valueOf(ldt.getYear());
            }
            return ldt.format(DateTimeFormatter.ofPattern("MMM d"));
        }
        if (timeRange > 3 * DAY_MS) {
            return ldt.format(DateTimeFormatter.ofPattern("MMM d"));
        }
        if (!currentDate.equals(prevDateStr)) {
            return ldt.format(DateTimeFormatter.ofPattern("MMM d"));
        }
        return ldt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatDateOnly(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String formatTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"));
    }

    private int tsToX(long timestamp, int chartX, int chartW, long minTime, long timeRange) {
        return chartX + (int) ((timestamp - minTime) * chartW / (double) timeRange);
    }

    private int normToY(double normalized, int chartY, int chartH) {
        return chartY + chartH - (int) (normalized * chartH);
    }

    private static String formatValue(double val) {
        if (val == (long) val) return String.valueOf((long) val);
        if (Math.abs(val) < 10) return String.format("%.2f", val);
        return String.format("%.1f", val);
    }

    private static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int) (c.getRed() * (1 - factor))),
            Math.max(0, (int) (c.getGreen() * (1 - factor))),
            Math.max(0, (int) (c.getBlue() * (1 - factor)))
        );
    }

    // ==================== Factory ====================

    /**
     * Creates chart panels for all numeric fields in a challenge.
     * For list mode: one chart per metric, each entity as a separate line.
     * For single mode: one chart per metric.
     */
    public static List<ChallengeChartPanel> createCharts(
            com.tradery.ai.challenges.model.Challenge challenge,
            List<ChallengeResult> results) {
        List<ChallengeChartPanel> charts = new ArrayList<>();
        if (challenge == null || challenge.output().fields() == null) return charts;
        // Snapshot lists don't get temporal charts
        if (challenge.output().isSnapshot()) return charts;

        List<ChallengeOutput.Field> numericFields = challenge.output().fields().stream()
            .filter(f -> f.type() == ChallengeOutput.Field.FieldType.SCORE
                      || f.type() == ChallengeOutput.Field.FieldType.NUMBER)
            .toList();

        if (challenge.output().listMode()) {
            ChallengeOutput.Field nameField = null;
            for (ChallengeOutput.Field f : challenge.output().fields()) {
                if (f.primary()) { nameField = f; break; }
            }
            if (nameField == null && !challenge.output().fields().isEmpty()) {
                nameField = challenge.output().fields().getFirst();
            }

            int colorIdx = 0;
            for (ChallengeOutput.Field field : numericFields) {
                ChallengeChartPanel chart = new ChallengeChartPanel(field, nameField, results, colorIdx);
                if (chart.hasData()) charts.add(chart);
                colorIdx++;
            }
        } else {
            int colorIdx = 0;
            for (ChallengeOutput.Field field : numericFields) {
                ChallengeChartPanel chart = new ChallengeChartPanel(field, results, colorIdx);
                if (chart.hasData()) charts.add(chart);
                colorIdx++;
            }
        }
        return charts;
    }
}
