package com.tradery.ui;

import com.tradery.model.ExitZone;
import com.tradery.model.Strategy;

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
    private static final int SCALE_HEIGHT = 80;

    public FlowDiagramPanel() {
        setOpaque(false);
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, SCALE_HEIGHT + 20);
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

        Font mainFont = getFont().deriveFont(Font.PLAIN, 10f);
        Font smallFont = getFont().deriveFont(Font.PLAIN, 8f);
        g2.setFont(mainFont);
        FontMetrics fm = g2.getFontMetrics();
        FontMetrics fmSmall = g2.getFontMetrics(smallFont);

        // Get exit zones
        List<ExitZone> zones = strategy.getExitZones();
        if (zones == null || zones.isEmpty()) {
            zones = List.of(new ExitZone("Default", null, null, "", null, null, null, null, false, 0, null, null, null, null, 0, List.of(), List.of()));
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

        // Layout
        int margin = 10;
        int entryBoxWidth = fm.stringWidth("Entry") + 12;
        int entryBoxHeight = fm.getHeight() + 8;  // Just enough for text
        int arrowGap = 30;
        int labelGap = 4;

        int scaleTop = (getHeight() - SCALE_HEIGHT) / 2;
        int scaleBottom = scaleTop + SCALE_HEIGHT;

        // Entry box (text height only, vertically centered)
        int entryX = margin;
        int entryY = (getHeight() - entryBoxHeight) / 2;
        int entryRight = entryX + entryBoxWidth;
        int entryCenterY = entryY + entryBoxHeight / 2;

        // Draw entry box
        g2.setColor(boxBg);
        g2.fillRoundRect(entryX, entryY, entryBoxWidth, entryBoxHeight, 6, 6);
        g2.setColor(accentColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(entryX, entryY, entryBoxWidth, entryBoxHeight, 6, 6);
        g2.setColor(textColor);
        g2.setFont(mainFont);
        String entryText = "Entry";
        int textX = entryX + (entryBoxWidth - fm.stringWidth(entryText)) / 2;
        int textY = entryY + ((entryBoxHeight - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(entryText, textX, textY);

        // Zone area starts here
        int zonesX = entryRight + arrowGap;
        int barWidth = 5;
        int labelGapBar = 6;

        // Draw 0% reference line (dashed, subtle)
        int zeroY = pnlToY(0, minPnl, maxPnl, scaleTop, scaleBottom);
        g2.setColor(new Color(180, 180, 180, 60));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3, 3}, 0));
        g2.drawLine(entryRight + 5, zeroY, zonesX + barWidth, zeroY);

        // Draw zones as thin bars with labels
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
            int zoneHeight = Math.max(zoneBotY - zoneTopY, 4);

            // Draw arrow/band from entry to zone bar
            g2.setColor(arrowColor);
            Path2D arrow = new Path2D.Float();
            arrow.moveTo(entryRight + 2, entryY);
            arrow.lineTo(zonesX - 2, zoneTopY);
            arrow.lineTo(zonesX - 2, zoneBotY);
            arrow.lineTo(entryRight + 2, entryY + entryBoxHeight);
            arrow.closePath();
            g2.fill(arrow);

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
                // Dashed line for unbounded
                g2.setColor(dimColor);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 2}, 0));
                g2.drawLine(zonesX, zoneTopY, zonesX + barWidth, zoneTopY);
            }

            // Bottom boundary line (opaque) - only if bounded
            if (!unboundedBottom) {
                g2.setColor(accentColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(zonesX, zoneBotY, zonesX + barWidth, zoneBotY);
            } else {
                // Dashed line for unbounded
                g2.setColor(dimColor);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 2}, 0));
                g2.drawLine(zonesX, zoneBotY, zonesX + barWidth, zoneBotY);
            }

            // P&L labels at actual boundary positions (first, right after bar)
            g2.setFont(smallFont);
            g2.setColor(dimColor);
            int pctLabelX = zonesX + barWidth + labelGapBar;

            // Calculate max width of percentage labels for alignment
            String maxLabel = unboundedTop ? "+∞" : formatPct(zone.maxPnlPercent());
            String minLabel = unboundedBottom ? "−∞" : formatPct(zone.minPnlPercent());
            int maxPctWidth = Math.max(fmSmall.stringWidth(maxLabel), fmSmall.stringWidth(minLabel));

            // Top boundary percentage (max)
            int maxLabelY = zoneTopY + fmSmall.getAscent() / 2;
            g2.drawString(maxLabel, pctLabelX, maxLabelY);

            // Bottom boundary percentage (min) - only if different position
            if (zoneHeight > fmSmall.getHeight() + 4) {
                int minLabelY = zoneBotY + fmSmall.getAscent() / 2;
                g2.drawString(minLabel, pctLabelX, minLabelY);
            }

            // Zone name - to the right of percentage labels, vertically centered
            String name = zone.name() != null && !zone.name().isEmpty() ? zone.name() : "Zone " + (i + 1);
            g2.setFont(mainFont);
            g2.setColor(textColor);
            int nameX = pctLabelX + maxPctWidth + 8;
            int nameLabelY = zoneTopY + (zoneHeight + fm.getAscent()) / 2 - 2;
            // Clamp label Y to visible range
            nameLabelY = Math.max(scaleTop + fm.getAscent(), Math.min(scaleBottom - 2, nameLabelY));
            g2.drawString(name, nameX, nameLabelY);
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
}
