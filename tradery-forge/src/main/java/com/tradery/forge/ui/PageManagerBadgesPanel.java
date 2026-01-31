package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.data.DataType;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;
import com.tradery.forge.ui.controls.StatusBadge;

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

    private final StatusBadge candlesBadge;
    private final StatusBadge fundingBadge;
    private final StatusBadge oiBadge;
    private final StatusBadge aggTradesBadge;
    private final StatusBadge premiumBadge;
    private final StatusBadge indicatorsBadge;

    private final Timer refreshTimer;


    public PageManagerBadgesPanel() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        setOpaque(false);

        // Create badges for each page manager
        candlesBadge = new StatusBadge("Candles 0");
        fundingBadge = new StatusBadge("Funding 0");
        oiBadge = new StatusBadge("OI 0");
        aggTradesBadge = new StatusBadge("AggTrades 0");
        premiumBadge = new StatusBadge("Premium 0");
        indicatorsBadge = new StatusBadge("Indicators 0");

        addClickHandler(candlesBadge, DataType.CANDLES);
        addClickHandler(fundingBadge, DataType.FUNDING);
        addClickHandler(oiBadge, DataType.OPEN_INTEREST);
        addClickHandler(aggTradesBadge, DataType.AGG_TRADES);
        addClickHandler(premiumBadge, DataType.PREMIUM_INDEX);
        indicatorsBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                DownloadDashboardWindow.showWindowForIndicators();
            }
        });

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

    private void addClickHandler(StatusBadge badge, DataType dataType) {
        badge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                DownloadDashboardWindow.showWindow(dataType);
            }
        });
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

    private void updateDataPageBadge(StatusBadge badge, String name, DataPageManager<?> mgr) {
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

    private void updateBadge(StatusBadge badge, String name, int total, int loading, int ready,
                              java.util.Map<String, java.util.Set<String>> consumerDetails) {
        // Determine state and colors
        Color bgColor, fgColor;
        String stateText;

        if (total == 0) {
            bgColor = StatusBadge.BG_IDLE;
            fgColor = StatusBadge.FG_IDLE;
            stateText = "no pages";
        } else if (loading > 0) {
            bgColor = StatusBadge.BG_WARNING;
            fgColor = StatusBadge.FG_WARNING;
            stateText = loading + " loading, " + ready + " ready";
        } else if (ready == total) {
            bgColor = StatusBadge.BG_OK;
            fgColor = StatusBadge.FG_OK;
            stateText = "all ready";
        } else {
            bgColor = StatusBadge.BG_ERROR;
            fgColor = StatusBadge.FG_ERROR;
            stateText = (total - ready - loading) + " error";
        }

        badge.setText(name + " " + total);
        badge.setStatusColor(bgColor, fgColor);

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
}
