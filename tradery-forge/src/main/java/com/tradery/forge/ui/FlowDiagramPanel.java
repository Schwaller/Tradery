package com.tradery.forge.ui;

import com.tradery.core.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A visual diagram showing Entry box with arrows projecting to exit zones on a P&L scale.
 * Entry spans full height, arrows fan out to zones positioned by their P&L range.
 */
public class FlowDiagramPanel extends JPanel {

    private Strategy strategy;
    private static final int SCALE_HEIGHT = 160;
    private static final int UNBOUNDED_ZONE_HEIGHT = 32;

    public FlowDiagramPanel() {
        setOpaque(false);
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        revalidate();
        repaint();
    }

    private static final int MIN_ZONE_HEIGHT = 16;

    @Override
    public Dimension getPreferredSize() {
        // Calculate height based on number of zones (at least MIN_ZONE_HEIGHT each)
        int zoneCount = strategy != null && strategy.getExitZones() != null
            ? Math.max(1, strategy.getExitZones().size()) : 1;
        int minHeight = zoneCount * MIN_ZONE_HEIGHT + 40;  // 40 for margins
        return new Dimension(300, Math.max(minHeight, SCALE_HEIGHT + 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (strategy == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Colors
        Color accentColor = UIManager.getColor("Component.accentColor");
        if (accentColor == null) accentColor = new Color(100, 140, 180);
        Color boxBg = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 30);
        Color arrowColor = new Color(150, 150, 150, 180);
        Color textColor = UIManager.getColor("Label.foreground");
        if (textColor == null) textColor = Color.DARK_GRAY;
        Color dimColor = new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 100);
        Color axisColor = new Color(180, 180, 180);

        Font mainFont = getFont().deriveFont(Font.PLAIN, 12f);
        Font smallFont = mainFont.deriveFont(Font.PLAIN, 10f);
        g2.setFont(smallFont);
        FontMetrics smallFm = g2.getFontMetrics();
        g2.setFont(mainFont);
        FontMetrics fm = g2.getFontMetrics();

        // Get exit zones
        List<ExitZone> zones = strategy.getExitZones();
        if (zones == null || zones.isEmpty()) {
            zones = List.of(ExitZone.defaultZone());
        }

        // Calculate P&L range based only on actual defined boundaries
        Double minDefined = null;
        Double maxDefined = null;
        boolean hasUnboundedBottom = false;
        boolean hasUnboundedTop = false;
        for (ExitZone zone : zones) {
            if (zone.minPnlPercent() != null) {
                minDefined = (minDefined == null) ? zone.minPnlPercent() : Math.min(minDefined, zone.minPnlPercent());
            } else {
                hasUnboundedBottom = true;
            }
            if (zone.maxPnlPercent() != null) {
                maxDefined = (maxDefined == null) ? zone.maxPnlPercent() : Math.max(maxDefined, zone.maxPnlPercent());
            } else {
                hasUnboundedTop = true;
            }
        }

        // Count how many unbounded extensions we need (top/bottom)
        int unboundedExtensions = (hasUnboundedBottom ? 1 : 0) + (hasUnboundedTop ? 1 : 0);
        // The bounded portion of the scale gets the remaining pixels after unbounded zones
        int boundedPixels = SCALE_HEIGHT - unboundedExtensions * UNBOUNDED_ZONE_HEIGHT;

        // Build scale to cover only the defined boundaries
        // The P&L scale maps to the bounded pixel region; unbounded zones extend beyond in pixels
        double minPnl, maxPnl;
        if (minDefined == null && maxDefined == null) {
            minPnl = -5;
            maxPnl = 5;
        } else if (minDefined == null) {
            minPnl = maxDefined - 5;
            maxPnl = maxDefined;
        } else if (maxDefined == null) {
            minPnl = minDefined;
            maxPnl = minDefined + 5;
        } else {
            minPnl = minDefined;
            maxPnl = maxDefined;
        }
        if (minPnl >= maxPnl) { minPnl = maxPnl - 1; }

        // Determine entry label based on order type
        EntryOrderType orderType = strategy.getEntrySettings().getOrderType();
        Double offsetPct = strategy.getEntrySettings().getOrderOffsetPercent();
        String entryText = switch (orderType) {
            case MARKET -> "Market Entry";
            case LIMIT -> {
                if (offsetPct != null && offsetPct != 0) {
                    yield "Limit Entry " + formatPctSigned(-offsetPct);
                }
                yield "Limit Entry";
            }
            case STOP -> {
                if (offsetPct != null && offsetPct != 0) {
                    yield "Stop Entry " + formatPctSigned(offsetPct);
                }
                yield "Stop Entry";
            }
            case TRAILING -> {
                Double reversePct = strategy.getEntrySettings().getTrailingReversePercent();
                if (reversePct != null && reversePct != 0) {
                    yield "Trailing Entry " + formatPctSigned(reversePct);
                }
                yield "Trailing Entry";
            }
        };

        // Collect condition box content
        List<String> dslLines = parseDslConditions(strategy.getEntry());
        List<String> phaseLines = getPhaseLines();
        List<String> hoopLines = getHoopLines();

        // Calculate condition box dimensions
        int condBoxPadding = 6;
        int condBoxGap = 8;
        int condArrowGap = 30;

        // Calculate max width needed for condition boxes
        int condBoxWidth = 0;
        for (String line : dslLines) condBoxWidth = Math.max(condBoxWidth, smallFm.stringWidth(line));
        for (String line : phaseLines) condBoxWidth = Math.max(condBoxWidth, smallFm.stringWidth(line));
        for (String line : hoopLines) condBoxWidth = Math.max(condBoxWidth, smallFm.stringWidth(line));
        condBoxWidth += condBoxPadding * 2;
        condBoxWidth = Math.max(condBoxWidth, 60);

        // Count non-empty boxes
        int boxCount = 0;
        if (!dslLines.isEmpty()) boxCount++;
        if (!phaseLines.isEmpty()) boxCount++;
        if (!hoopLines.isEmpty()) boxCount++;

        // Layout
        int entryBoxWidth = fm.stringWidth(entryText) + 12;
        int entryBoxHeight = fm.getHeight() + 8;
        int arrowGap = 60;
        int labelGapBar = 6;

        // Calculate bar width to fit all zone labels
        int barPadding = 8;
        int barWidth = 60;
        for (ExitZone zone : zones) {
            String label = formatExitLabel(zone);
            barWidth = Math.max(barWidth, fm.stringWidth(label) + barPadding * 2);
        }

        // Calculate total diagram width and center it
        int pctLabelWidth = fm.stringWidth("+100%");
        int condSectionWidth = boxCount > 0 ? condBoxWidth + condArrowGap : 0;
        int totalWidth = condSectionWidth + entryBoxWidth + arrowGap + barWidth + labelGapBar + pctLabelWidth;
        int startX = (getWidth() - totalWidth) / 2;

        int scaleTop = (getHeight() - SCALE_HEIGHT) / 2;
        int scaleBottom = scaleTop + SCALE_HEIGHT;

        // The bounded P&L region sits inside the scale, with unbounded caps outside
        int boundedTop = scaleTop + (hasUnboundedTop ? UNBOUNDED_ZONE_HEIGHT : 0);
        int boundedBottom = scaleBottom - (hasUnboundedBottom ? UNBOUNDED_ZONE_HEIGHT : 0);

        // Entry box position
        int entryX = startX + condSectionWidth;
        int zeroY = pnlToY(Math.max(minPnl, Math.min(maxPnl, 0)), minPnl, maxPnl, boundedTop, boundedBottom);
        int entryY = zeroY - entryBoxHeight / 2;
        int entryRight = entryX + entryBoxWidth;

        // Draw condition boxes on the left (if any)
        if (boxCount > 0) {
            int lineHeight = smallFm.getHeight();
            List<int[]> boxBounds = new ArrayList<>();  // [y, height] for each box

            // Calculate total height of all condition boxes
            int totalCondHeight = 0;
            if (!dslLines.isEmpty()) totalCondHeight += dslLines.size() * lineHeight + condBoxPadding * 2;
            if (!phaseLines.isEmpty()) {
                if (totalCondHeight > 0) totalCondHeight += condBoxGap;
                totalCondHeight += phaseLines.size() * lineHeight + condBoxPadding * 2;
            }
            if (!hoopLines.isEmpty()) {
                if (totalCondHeight > 0) totalCondHeight += condBoxGap;
                totalCondHeight += hoopLines.size() * lineHeight + condBoxPadding * 2;
            }

            // Center condition boxes vertically in the scale area
            int condY = scaleTop + (SCALE_HEIGHT - totalCondHeight) / 2;
            int condX = startX;

            // Draw each non-empty box
            g2.setFont(smallFont);

            if (!dslLines.isEmpty()) {
                int boxHeight = dslLines.size() * lineHeight + condBoxPadding * 2;
                drawConditionBox(g2, condX, condY, condBoxWidth, boxHeight, dslLines,
                        arrowColor, accentColor, textColor, smallFm, condBoxPadding);
                boxBounds.add(new int[]{condY, boxHeight});
                condY += boxHeight + condBoxGap;
            }

            if (!phaseLines.isEmpty()) {
                int boxHeight = phaseLines.size() * lineHeight + condBoxPadding * 2;
                drawConditionBox(g2, condX, condY, condBoxWidth, boxHeight, phaseLines,
                        arrowColor, accentColor, textColor, smallFm, condBoxPadding);
                boxBounds.add(new int[]{condY, boxHeight});
                condY += boxHeight + condBoxGap;
            }

            if (!hoopLines.isEmpty()) {
                int boxHeight = hoopLines.size() * lineHeight + condBoxPadding * 2;
                drawConditionBox(g2, condX, condY, condBoxWidth, boxHeight, hoopLines,
                        arrowColor, accentColor, textColor, smallFm, condBoxPadding);
                boxBounds.add(new int[]{condY, boxHeight});
            }

            // Draw arrows from each box to entry
            g2.setColor(arrowColor);
            g2.setStroke(new BasicStroke(1.5f));
            int boxRight = condX + condBoxWidth;
            int entryCenterY = entryY + entryBoxHeight / 2;

            for (int[] bounds : boxBounds) {
                int boxCenterY = bounds[0] + bounds[1] / 2;
                // Draw line from box to entry
                Path2D arrow = new Path2D.Float();
                arrow.moveTo(boxRight, boxCenterY);
                float cpX = boxRight + condArrowGap / 2f;
                arrow.curveTo(cpX, boxCenterY, cpX, entryCenterY, entryX, entryCenterY);
                g2.draw(arrow);
            }

            g2.setFont(mainFont);
        }

        // Draw entry box (rounded on left, square on right)
        int radius = 6;
        Path2D entryBox = new Path2D.Float();
        entryBox.moveTo(entryX + radius, entryY);
        entryBox.lineTo(entryX + entryBoxWidth, entryY);
        entryBox.lineTo(entryX + entryBoxWidth, entryY + entryBoxHeight);
        entryBox.lineTo(entryX + radius, entryY + entryBoxHeight);
        entryBox.quadTo(entryX, entryY + entryBoxHeight, entryX, entryY + entryBoxHeight - radius);
        entryBox.lineTo(entryX, entryY + radius);
        entryBox.quadTo(entryX, entryY, entryX + radius, entryY);
        entryBox.closePath();

        g2.setColor(boxBg);
        g2.fill(entryBox);
        g2.setColor(accentColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(entryBox);
        g2.setColor(textColor);
        g2.setFont(mainFont);
        int textX = entryX + (entryBoxWidth - fm.stringWidth(entryText)) / 2;
        int textY = entryY + ((entryBoxHeight - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(entryText, textX, textY);

        // Expiration label below entry box (if applicable)
        Integer expirationBars = strategy.getEntrySettings().getExpirationBars();
        if (expirationBars != null && expirationBars > 0) {
            String expLabel = "Expires: " + expirationBars + " bar" + (expirationBars > 1 ? "s" : "");
            g2.setColor(dimColor);
            g2.setFont(smallFont);
            int expX = entryX + (entryBoxWidth - smallFm.stringWidth(expLabel)) / 2;
            int expY = entryY + entryBoxHeight + smallFm.getAscent() + 2;
            g2.drawString(expLabel, expX, expY);
            g2.setFont(mainFont);
        }

        // Zone area starts here
        int zonesX = entryRight + arrowGap;

        // Draw zones as bars with labels inside
        g2.setStroke(new BasicStroke(1.5f));

        for (int i = 0; i < zones.size(); i++) {
            ExitZone zone = zones.get(i);

            // Calculate zone Y position: bounded edges use P&L scale, unbounded get fixed pixel cap
            boolean unboundedTop = zone.maxPnlPercent() == null;
            boolean unboundedBottom = zone.minPnlPercent() == null;

            int zoneTopY = unboundedTop ? scaleTop
                    : pnlToY(zone.maxPnlPercent(), minPnl, maxPnl, boundedTop, boundedBottom);
            int zoneBotY = unboundedBottom ? scaleBottom
                    : pnlToY(zone.minPnlPercent(), minPnl, maxPnl, boundedTop, boundedBottom);
            int zoneHeight = zoneBotY - zoneTopY;

            // Draw curved flow band from entry to zone bar (Sankey-style)
            g2.setColor(arrowColor);

            // Control point X for bezier curves (midpoint)
            float cpX = (entryRight + 2 + zonesX) / 2f;

            // Entry connection points - align with entry box corners
            float entryTopY = entryY;
            float entryBotY = entryY + entryBoxHeight;

            // Full flow shape for fill
            Path2D flow = new Path2D.Float();
            flow.moveTo(entryRight + 2, entryTopY);
            flow.curveTo(cpX, entryTopY, cpX, zoneTopY, zonesX, zoneTopY);
            flow.lineTo(zonesX, zoneBotY);
            flow.curveTo(cpX, zoneBotY, cpX, entryBotY, entryRight + 2, entryBotY);
            flow.closePath();
            g2.fill(flow);

            // Draw white edge lines on top and bottom curves
            g2.setColor(new Color(255, 255, 255, 180));
            g2.setStroke(new BasicStroke(1f));

            // Top edge curve
            Path2D topEdge = new Path2D.Float();
            topEdge.moveTo(entryRight + 2, entryTopY);
            topEdge.curveTo(cpX, entryTopY, cpX, zoneTopY, zonesX, zoneTopY);
            g2.draw(topEdge);

            // Bottom edge curve
            Path2D botEdge = new Path2D.Float();
            botEdge.moveTo(entryRight + 2, entryBotY);
            botEdge.curveTo(cpX, entryBotY, cpX, zoneBotY, zonesX, zoneBotY);
            g2.draw(botEdge);

            // Zone bar - 50% transparent fill
            Color barFill = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80);
            g2.setColor(barFill);
            g2.fillRect(zonesX, zoneTopY, barWidth, zoneHeight);

            // Top boundary line - solid if bounded, dotted if unbounded
            g2.setColor(accentColor);
            if (!unboundedTop) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3, 4}, 0));
            }
            g2.drawLine(zonesX, zoneTopY, zonesX + barWidth, zoneTopY);

            // Bottom boundary line - solid if bounded, dotted if unbounded
            g2.setColor(accentColor);
            if (!unboundedBottom) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3, 4}, 0));
            }
            g2.drawLine(zonesX, zoneBotY, zonesX + barWidth, zoneBotY);

            // Zone name inside the bar - vertically centered
            String zoneLabel = formatExitLabel(zone);
            g2.setFont(mainFont);
            g2.setColor(textColor);
            int labelX = zonesX + barPadding;
            int labelY = zoneTopY + (zoneHeight + fm.getAscent()) / 2 - 2;
            // Clamp label Y to visible range
            labelY = Math.max(scaleTop + fm.getAscent(), Math.min(scaleBottom - 2, labelY));
            g2.drawString(zoneLabel, labelX, labelY);

            // P&L labels to the right of bar - only for defined boundaries
            g2.setColor(dimColor);
            int pctLabelX = zonesX + barWidth + labelGapBar;

            // Top boundary percentage (max) - only show if defined
            if (!unboundedTop) {
                String maxLabel = formatPct(zone.maxPnlPercent());
                int maxLabelY = zoneTopY + fm.getAscent() / 2;
                g2.drawString(maxLabel, pctLabelX, maxLabelY);
            }

            // Bottom boundary percentage (min) - only show if defined
            if (!unboundedBottom) {
                String minLabel = formatPct(zone.minPnlPercent());
                int minLabelY = zoneBotY + fm.getAscent() / 2;
                g2.drawString(minLabel, pctLabelX, minLabelY);
            }
        }


        g2.dispose();
    }

    private int pnlToY(double pnl, double minPnl, double maxPnl, int top, int bottom) {
        // Higher P&L = lower Y (top of screen)
        double ratio = (maxPnl - pnl) / (maxPnl - minPnl);
        return top + (int) (ratio * (bottom - top));
    }

    private String formatRange(ExitZone zone) {
        Double min = zone.minPnlPercent();
        Double max = zone.maxPnlPercent();

        if (min == null && max == null) {
            return "−∞ to +∞";
        } else if (min == null) {
            return "−∞ to " + formatPct(max);
        } else if (max == null) {
            return formatPct(min) + " to +∞";
        } else {
            return formatPct(min) + " to " + formatPct(max);
        }
    }

    private String formatPct(double val) {
        if (val == (int) val) {
            return (int) val + "%";
        }
        return String.format("%.1f%%", val);
    }

    private String formatPctSigned(double val) {
        String sign = val >= 0 ? "+" : "";
        if (val == (int) val) {
            return sign + (int) val + "%";
        }
        return String.format("%s%.1f%%", sign, val);
    }

    private String formatExitLabel(ExitZone zone) {
        String name = zone.name() != null && !zone.name().isEmpty() ? zone.name() : "Default";
        String type;

        if (zone.exitImmediately()) {
            type = "Immediate Exit";
        } else if (zone.stopLossType() != null && zone.stopLossType() != StopLossType.NONE) {
            type = zone.stopLossType().isTrailing() ? "Trailing SL Exit" : "SL Exit";
        } else if (zone.takeProfitType() != null && zone.takeProfitType() != TakeProfitType.NONE) {
            type = "TP Exit";
        } else if (zone.exitCondition() != null && !zone.exitCondition().isBlank()) {
            type = "Conditional Exit";
        } else {
            type = "Exit";
        }

        return type + ": " + name;
    }

    private List<String> parseDslConditions(String condition) {
        List<String> lines = new ArrayList<>();
        if (condition == null || condition.isBlank()) return lines;

        // Split by AND (case insensitive, with word boundaries)
        String[] parts = condition.split("(?i)\\s+AND\\s+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private List<String> getPhaseLines() {
        List<String> lines = new ArrayList<>();
        PhaseSettings phaseSettings = strategy.getPhaseSettings();
        if (phaseSettings == null) return lines;

        List<String> required = phaseSettings.getRequiredPhaseIds();
        List<String> excluded = phaseSettings.getExcludedPhaseIds();

        if (required != null) {
            for (String id : required) {
                lines.add("✓ " + id);
            }
        }
        if (excluded != null) {
            for (String id : excluded) {
                lines.add("✗ " + id);
            }
        }
        return lines;
    }

    private List<String> getHoopLines() {
        List<String> lines = new ArrayList<>();
        HoopPatternSettings hoopSettings = strategy.getHoopPatternSettings();
        if (hoopSettings == null) return lines;

        List<String> required = hoopSettings.getRequiredEntryPatternIds();
        List<String> excluded = hoopSettings.getExcludedEntryPatternIds();

        if (required != null) {
            for (String id : required) {
                lines.add("✓ " + id);
            }
        }
        if (excluded != null) {
            for (String id : excluded) {
                lines.add("✗ " + id);
            }
        }
        return lines;
    }

    private void drawConditionBox(Graphics2D g2, int x, int y, int width, int height,
                                   List<String> lines, Color bgColor, Color borderColor,
                                   Color textColor, FontMetrics fm, int padding) {
        // Draw background (same color as arrows)
        int radius = 4;
        g2.setColor(bgColor);
        g2.fillRoundRect(x, y, width, height, radius * 2, radius * 2);

        // Draw text lines
        g2.setColor(textColor);
        int lineHeight = fm.getHeight();
        int textY = y + padding + fm.getAscent();
        for (String line : lines) {
            g2.drawString(line, x + padding, textY);
            textY += lineHeight;
        }
    }
}
