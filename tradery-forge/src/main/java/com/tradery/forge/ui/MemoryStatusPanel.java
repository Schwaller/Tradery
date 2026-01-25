package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.page.*;

import javax.swing.*;
import java.awt.*;

/**
 * Panel showing JVM memory usage and per-manager memory breakdown.
 * Displayed in the status bar.
 */
public class MemoryStatusPanel extends JPanel {

    private final JLabel heapLabel;
    private final JLabel dataLabel;
    private final Timer refreshTimer;

    // Colors
    private static final Color BG_NORMAL = new Color(60, 60, 65);
    private static final Color BG_WARNING = new Color(180, 120, 40);
    private static final Color BG_CRITICAL = new Color(180, 60, 60);
    private static final Color FG_COLOR = new Color(200, 200, 200);

    public MemoryStatusPanel() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        setOpaque(false);

        // JVM heap label
        heapLabel = createBadge("Heap");
        add(heapLabel);

        // Data pages memory label
        dataLabel = createBadge("Data");
        add(dataLabel);

        // Refresh every 2 seconds
        refreshTimer = new Timer(2000, e -> refresh());
        refreshTimer.start();

        // Initial refresh
        refresh();
    }

    private JLabel createBadge(String name) {
        JLabel label = new JLabel(name + " --") {
            private Color bgColor = BG_NORMAL;

            public void setBackground(Color bg) {
                this.bgColor = bg;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        label.setForeground(FG_COLOR);
        label.setOpaque(false);
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return label;
    }

    public void refresh() {
        // JVM heap usage
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        double usedPct = (double) usedBytes / maxBytes * 100;

        heapLabel.setText(String.format("Heap %s/%s", formatBytes(usedBytes), formatBytes(maxBytes)));

        // Color based on usage
        Color heapBg = BG_NORMAL;
        if (usedPct > 80) heapBg = BG_CRITICAL;
        else if (usedPct > 60) heapBg = BG_WARNING;
        heapLabel.setBackground(heapBg);

        heapLabel.setToolTipText(String.format(
            "<html><b>JVM Heap Memory</b><br>" +
            "Used: %s (%.1f%%)<br>" +
            "Max: %s</html>",
            formatBytes(usedBytes), usedPct, formatBytes(maxBytes)));

        // Data pages memory
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null) {
            dataLabel.setText("Data --");
            return;
        }

        // Calculate per-manager memory
        long candlesMem = safeEstimate(ctx.getCandlePageManager());
        long fundingMem = safeEstimate(ctx.getFundingPageManager());
        long oiMem = safeEstimate(ctx.getOIPageManager());
        long aggTradesMem = safeEstimate(ctx.getAggTradesPageManager());
        long premiumMem = safeEstimate(ctx.getPremiumPageManager());
        long indicatorsMem = ctx.getIndicatorPageManager() != null
            ? ctx.getIndicatorPageManager().estimateMemoryBytes() : 0;

        long totalDataBytes = candlesMem + fundingMem + oiMem + aggTradesMem + premiumMem + indicatorsMem;
        dataLabel.setText("Data " + formatBytes(totalDataBytes));

        // Build tooltip with breakdown
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html><b>Data Memory Usage</b><br><br>");
        tooltip.append(String.format("Candles: %s<br>", formatBytes(candlesMem)));
        tooltip.append(String.format("Indicators: %s<br>", formatBytes(indicatorsMem)));
        tooltip.append(String.format("Funding: %s<br>", formatBytes(fundingMem)));
        tooltip.append(String.format("Open Interest: %s<br>", formatBytes(oiMem)));
        tooltip.append(String.format("AggTrades: %s<br>", formatBytes(aggTradesMem)));
        tooltip.append(String.format("Premium: %s<br>", formatBytes(premiumMem)));
        tooltip.append(String.format("<br><b>Total: %s</b></html>", formatBytes(totalDataBytes)));
        dataLabel.setToolTipText(tooltip.toString());
    }

    private long safeEstimate(DataPageManager<?> mgr) {
        return mgr != null ? mgr.estimateMemoryBytes() : 0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return formatWithSeparator(bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return formatWithSeparator(bytes / (1024 * 1024)) + " MB";
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Format number with ' as thousand separator (e.g., 1'234) */
    private String formatWithSeparator(long value) {
        if (value < 1000) return String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        String str = String.valueOf(value);
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append("'");
            sb.append(str.charAt(i));
        }
        return sb.toString();
    }

    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
