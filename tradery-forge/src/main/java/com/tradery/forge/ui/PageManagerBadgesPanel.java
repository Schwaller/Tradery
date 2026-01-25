package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel showing badges for each page manager with checked out page count and loading state.
 * Displays in the status bar to give visibility into background data loading.
 */
public class PageManagerBadgesPanel extends JPanel {

    private final BadgeLabel candlesBadge;
    private final BadgeLabel fundingBadge;
    private final BadgeLabel oiBadge;
    private final BadgeLabel aggTradesBadge;
    private final BadgeLabel premiumBadge;
    private final BadgeLabel indicatorsBadge;

    private final Timer refreshTimer;

    // Colors for states
    private static final Color COLOR_IDLE_BG = new Color(80, 80, 80);
    private static final Color COLOR_IDLE_FG = new Color(160, 160, 160);
    private static final Color COLOR_READY_BG = new Color(39, 174, 96);
    private static final Color COLOR_READY_FG = Color.WHITE;
    private static final Color COLOR_LOADING_BG = new Color(241, 196, 15);
    private static final Color COLOR_LOADING_FG = new Color(40, 40, 40);
    private static final Color COLOR_ERROR_BG = new Color(231, 76, 60);
    private static final Color COLOR_ERROR_FG = Color.WHITE;

    public PageManagerBadgesPanel() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        setOpaque(false);

        // Create badges for each page manager
        candlesBadge = new BadgeLabel("Candles");
        fundingBadge = new BadgeLabel("Funding");
        oiBadge = new BadgeLabel("OI");
        aggTradesBadge = new BadgeLabel("AggTrades");
        premiumBadge = new BadgeLabel("Premium");
        indicatorsBadge = new BadgeLabel("Indicators");

        add(candlesBadge);
        add(fundingBadge);
        add(oiBadge);
        add(aggTradesBadge);
        add(premiumBadge);
        add(indicatorsBadge);

        // Context menu for right-click to open Download Dashboard
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem openDashboardItem = new JMenuItem("Open Download Dashboard...");
        openDashboardItem.addActionListener(e -> openDownloadDashboard());
        contextMenu.add(openDashboardItem);
        setComponentPopupMenu(contextMenu);

        // Also add to each badge
        for (Component comp : getComponents()) {
            if (comp instanceof JComponent jcomp) {
                jcomp.setComponentPopupMenu(contextMenu);
            }
        }

        // Refresh every 500ms
        refreshTimer = new Timer(500, e -> refresh());
        refreshTimer.start();

        // Initial refresh
        refresh();
    }

    /**
     * Open the Download Dashboard window.
     */
    private void openDownloadDashboard() {
        DownloadDashboardWindow dashboard = new DownloadDashboardWindow();
        dashboard.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dashboard.setVisible(true);
    }

    public void refresh() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx == null) return;

        // Update each badge
        updateDataPageBadge(candlesBadge, "Candles", ctx.getCandlePageManager());
        updateDataPageBadge(fundingBadge, "Funding", ctx.getFundingPageManager());
        updateDataPageBadge(oiBadge, "OI", ctx.getOIPageManager());
        updateDataPageBadge(aggTradesBadge, "AggTrades", ctx.getAggTradesPageManager());
        updateDataPageBadge(premiumBadge, "Premium", ctx.getPremiumPageManager());
        updateIndicatorsBadge(ctx.getIndicatorPageManager());
    }

    private void updateDataPageBadge(BadgeLabel badge, String name, DataPageManager<?> mgr) {
        if (mgr == null) {
            updateBadge(badge, name, 0, 0, 0, java.util.Map.of());
            return;
        }

        List<DataPageManager.PageInfo> pages = mgr.getActivePages();
        int total = pages.size();
        int loading = (int) pages.stream().filter(p -> p.state() == PageState.LOADING).count();
        int ready = (int) pages.stream().filter(p -> p.state() == PageState.READY).count();

        // Collect consumer -> timeframes mapping
        java.util.Map<String, java.util.Set<String>> consumerTimeframes = new java.util.TreeMap<>();
        for (DataPageManager.PageInfo page : pages) {
            String timeframe = page.timeframe() != null ? page.timeframe() : "default";
            for (String consumer : page.consumers()) {
                consumerTimeframes.computeIfAbsent(consumer, k -> new java.util.TreeSet<>()).add(timeframe);
            }
        }

        updateBadge(badge, name, total, loading, ready, consumerTimeframes);
    }

    private void updateIndicatorsBadge(IndicatorPageManager mgr) {
        if (mgr == null) {
            updateBadge(indicatorsBadge, "Indicators", 0, 0, 0, java.util.Map.of());
            return;
        }

        List<IndicatorPageManager.IndicatorPageInfo> pages = mgr.getActivePages();
        int total = pages.size();
        int loading = (int) pages.stream().filter(p -> p.state() == PageState.LOADING).count();
        int ready = (int) pages.stream().filter(p -> p.state() == PageState.READY).count();

        // Collect consumer -> indicator types mapping
        java.util.Map<String, java.util.Set<String>> consumerIndicators = new java.util.TreeMap<>();
        for (IndicatorPageManager.IndicatorPageInfo page : pages) {
            String indicator = page.type() + "(" + page.params() + ")";
            for (String consumer : page.consumers()) {
                consumerIndicators.computeIfAbsent(consumer, k -> new java.util.TreeSet<>()).add(indicator);
            }
        }

        updateBadge(indicatorsBadge, "Indicators", total, loading, ready, consumerIndicators);
    }

    private void updateBadge(BadgeLabel badge, String name, int total, int loading, int ready,
                              java.util.Map<String, java.util.Set<String>> consumerDetails) {
        // Determine state and colors
        Color bgColor, fgColor;
        String stateText;

        if (total == 0) {
            bgColor = COLOR_IDLE_BG;
            fgColor = COLOR_IDLE_FG;
            stateText = "no pages";
        } else if (loading > 0) {
            bgColor = COLOR_LOADING_BG;
            fgColor = COLOR_LOADING_FG;
            stateText = loading + " loading, " + ready + " ready";
        } else if (ready == total) {
            bgColor = COLOR_READY_BG;
            fgColor = COLOR_READY_FG;
            stateText = "all ready";
        } else {
            bgColor = COLOR_ERROR_BG;
            fgColor = COLOR_ERROR_FG;
            stateText = (total - ready - loading) + " error";
        }

        badge.update(name, total, bgColor, fgColor);

        // Build tooltip with consumer names and their timeframes/details
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<html><b>").append(name).append("</b><br>");
        tooltip.append("Pages: ").append(total).append("<br>");
        tooltip.append("Status: ").append(stateText);
        if (!consumerDetails.isEmpty()) {
            tooltip.append("<br><br><b>Consumers:</b><br>");
            for (java.util.Map.Entry<String, java.util.Set<String>> entry : consumerDetails.entrySet()) {
                tooltip.append("• ").append(entry.getKey());
                java.util.Set<String> details = entry.getValue();
                if (!details.isEmpty()) {
                    tooltip.append(" <font color='gray'>(");
                    tooltip.append(String.join(", ", details));
                    tooltip.append(")</font>");
                }
                tooltip.append("<br>");
            }
        }
        tooltip.append("</html>");
        badge.setToolTipText(tooltip.toString());
    }

    /**
     * Stop the refresh timer when panel is no longer needed.
     */
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }

    /**
     * Custom badge label with rounded background.
     */
    private static class BadgeLabel extends JLabel {
        private Color bgColor = COLOR_IDLE_BG;
        private static final int ARC = 8;
        private static final int PAD_H = 6;
        private static final int PAD_V = 2;

        public BadgeLabel(String name) {
            super(name + " 0");
            setFont(getFont().deriveFont(Font.BOLD, 10f));
            setForeground(COLOR_IDLE_FG);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(PAD_V, PAD_H, PAD_V, PAD_H));
        }

        public void update(String name, int count, Color bg, Color fg) {
            setText(name + " " + count);
            this.bgColor = bg;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw rounded background
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
