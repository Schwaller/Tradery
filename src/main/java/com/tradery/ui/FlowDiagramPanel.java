package com.tradery.ui;

import com.tradery.model.EntryOrderType;
import com.tradery.model.ExitZone;
import com.tradery.model.StopLossType;
import com.tradery.model.Strategy;
import com.tradery.model.TakeProfitType;

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
        for (ExitZone zone : zones) {
            if (zone.minPnlPercent() != null) {
                minDefined = (minDefined == null) ? zone.minPnlPercent() : Math.min(minDefined, zone.minPnlPercent());
            }
            if (zone.maxPnlPercent() != null) {
                maxDefined = (maxDefined == null) ? zone.maxPnlPercent() : Math.max(maxDefined, zone.maxPnlPercent());
            }
        }

        // Build scale around defined boundaries, include 0 as reference
        double minPnl, maxPnl;
        if (minDefined == null && maxDefined == null) {
            // No boundaries defined - just show 0 with small range
            minPnl = -5;
            maxPnl = 5;
        } else if (minDefined == null) {
            // Only upper bounds defined
            maxPnl = Math.max(maxDefined + 2, 2);
            minPnl = Math.min(0, maxDefined) - 5;
        } else if (maxDefined == null) {
            // Only lower bounds defined
            minPnl = Math.min(minDefined - 2, -2);
            maxPnl = Math.max(0, minDefined) + 5;
        } else {
            // Both defined
            minPnl = minDefined - 2;
            maxPnl = maxDefined + 2;
        }
        // Always include 0 in range
        if (minPnl > 0) minPnl = -2;
        if (maxPnl < 0) maxPnl = 2;

        // Determine entry label based on order type
        EntryOrderType orderType = strategy.getEntrySettings().getOrderType();
        Double offsetPct = strategy.getEntrySettings().getOrderOffsetPercent();
        String entryText = switch (orderType) {
            case MARKET -> "Market Entry";
            case LIMIT -> {
                if (offsetPct != null && offsetPct != 0) {
                    yield "Limit Entry " + formatPctSigned(-offsetPct);  // negative = below signal
                }
                yield "Limit Entry";
            }
            case STOP -> {
                if (offsetPct != null && offsetPct != 0) {
                    yield "Stop Entry " + formatPctSigned(offsetPct);  // positive = above signal
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

        // Layout
        int entryBoxWidth = fm.stringWidth(entryText) + 12;
        int entryBoxHeight = fm.getHeight() + 8;  // Just enough for text
        int arrowGap = 60;
        int labelGapBar = 6;

        // Calculate bar width to fit all zone labels
        int barPadding = 8;
        int barWidth = 60;  // minimum width
        for (ExitZone zone : zones) {
            String label = formatExitLabel(zone);
            barWidth = Math.max(barWidth, fm.stringWidth(label) + barPadding * 2);
        }

        // Calculate total diagram width and center it
        int pctLabelWidth = fm.stringWidth("+100%");  // estimate for percentage labels
        int totalWidth = entryBoxWidth + arrowGap + barWidth + labelGapBar + pctLabelWidth;
        int entryX = (getWidth() - totalWidth) / 2;

        int scaleTop = (getHeight() - SCALE_HEIGHT) / 2;
        int scaleBottom = scaleTop + SCALE_HEIGHT;

        // Entry box centered at 0% P&L position
        int zeroY = pnlToY(0, minPnl, maxPnl, scaleTop, scaleBottom);
        int entryY = zeroY - entryBoxHeight / 2;
        int entryRight = entryX + entryBoxWidth;

        // Draw entry box (rounded on left, square on right)
        int radius = 6;
        Path2D entryBox = new Path2D.Float();
        entryBox.moveTo(entryX + radius, entryY);
        entryBox.lineTo(entryX + entryBoxWidth, entryY);  // top edge
        entryBox.lineTo(entryX + entryBoxWidth, entryY + entryBoxHeight);  // right edge (square)
        entryBox.lineTo(entryX + radius, entryY + entryBoxHeight);  // bottom edge
        entryBox.quadTo(entryX, entryY + entryBoxHeight, entryX, entryY + entryBoxHeight - radius);  // bottom-left corner
        entryBox.lineTo(entryX, entryY + radius);  // left edge
        entryBox.quadTo(entryX, entryY, entryX + radius, entryY);  // top-left corner
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
            Font smallFont = mainFont.deriveFont(Font.PLAIN, 10f);
            g2.setFont(smallFont);
            FontMetrics smallFm = g2.getFontMetrics();
            int expX = entryX + (entryBoxWidth - smallFm.stringWidth(expLabel)) / 2;
            int expY = entryY + entryBoxHeight + smallFm.getAscent() + 2;
            g2.drawString(expLabel, expX, expY);
            g2.setFont(mainFont);  // restore
        }

        // Zone area starts here
        int zonesX = entryRight + arrowGap;

        // Draw zones as bars with labels inside
        g2.setStroke(new BasicStroke(1.5f));

        for (int i = 0; i < zones.size(); i++) {
            ExitZone zone = zones.get(i);

            // Calculate zone Y position based on P&L
            boolean unboundedTop = zone.maxPnlPercent() == null;
            boolean unboundedBottom = zone.minPnlPercent() == null;
            double zMin = zone.minPnlPercent() != null ? zone.minPnlPercent() : minPnl;
            double zMax = zone.maxPnlPercent() != null ? zone.maxPnlPercent() : maxPnl;

            int zoneTopY = pnlToY(zMax, minPnl, maxPnl, scaleTop, scaleBottom);
            int zoneBotY = pnlToY(zMin, minPnl, maxPnl, scaleTop, scaleBottom);
            int zoneHeight = Math.max(zoneBotY - zoneTopY, MIN_ZONE_HEIGHT);

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

            // Top boundary line (opaque) - only if bounded
            if (!unboundedTop) {
                g2.setColor(accentColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(zonesX, zoneTopY, zonesX + barWidth, zoneTopY);
            } else {
                // Dotted line for unbounded
                g2.setColor(dimColor);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{1, 3}, 0));
                g2.drawLine(zonesX, zoneTopY, zonesX + barWidth, zoneTopY);
            }

            // Bottom boundary line (opaque) - only if bounded
            if (!unboundedBottom) {
                g2.setColor(accentColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(zonesX, zoneBotY, zonesX + barWidth, zoneBotY);
            } else {
                // Dotted line for unbounded
                g2.setColor(dimColor);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{1, 3}, 0));
                g2.drawLine(zonesX, zoneBotY, zonesX + barWidth, zoneBotY);
            }

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
}
