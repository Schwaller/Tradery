package com.tradery.desk.ui;

import com.tradery.dataclient.page.DataServiceConnection;
import com.tradery.dataclient.page.RemoteCandlePageManager;
import com.tradery.desk.DeskAppContext;
import com.tradery.desk.DeskConfig;
import com.tradery.desk.feed.CandleAggregator;
import com.tradery.desk.signal.SignalEvaluator;
import com.tradery.desk.strategy.PublishedStrategy;
import com.tradery.ui.dashboard.DashboardPageInfo;
import com.tradery.ui.dashboard.DashboardSection;
import com.tradery.ui.dashboard.DashboardWindow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Desk status dashboard. Extends the shared DashboardWindow,
 * mapping remote page types to common DashboardPageInfo (same sidebar as forge).
 * Adds desk-specific sections: overview, connection, alerts, system.
 */
public class DeskStatusWindow extends DashboardWindow {

    private static final AtomicReference<DeskStatusWindow> INSTANCE = new AtomicReference<>();

    private static final String SECTION_OVERVIEW = "overview";
    private static final String SECTION_CONNECTION = "connection";
    private static final String SECTION_ALERTS = "alerts";
    private static final String SECTION_SYSTEM = "system";

    private static final Color COLOR_OK = new Color(60, 140, 60);
    private static final Color COLOR_WARNING = new Color(180, 160, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_IDLE = new Color(100, 100, 100);

    // Overview labels
    private JLabel ovConnectionLabel;
    private JLabel ovStrategiesLabel;
    private JLabel ovSignalsLabel;
    private JLabel ovHeapLabel;

    // Connection labels
    private JLabel connStateLabel;
    private JLabel connReconnectLabel;
    private JLabel connUrlLabel;

    // Alerts labels
    private JLabel alertDesktopLabel;
    private JLabel alertAudioLabel;
    private JLabel alertConsoleLabel;
    private JLabel alertWebhookLabel;
    private JLabel alertWebhookUrlLabel;
    private JLabel alertSignalCountLabel;

    // System labels
    private JLabel sysHeapLabel;
    private JLabel sysUptimeLabel;

    // Strategy section (dynamic)
    private JPanel strategyPanel;
    private final Map<String, StrategyCardLabels> strategyLabels = new HashMap<>();

    public static void showWindow() {
        showOrCreate(INSTANCE, DeskStatusWindow::new);
    }

    private DeskStatusWindow() {
        super("Desk Status");
        initialize();
    }

    @Override
    protected void onWindowClosed() {
        INSTANCE.set(null);
    }

    @Override
    protected int getSidebarWidth() {
        return 300;
    }

    // ========== Sections ==========

    @Override
    protected List<DashboardSection> collectSections() {
        List<DashboardSection> sections = new ArrayList<>();

        sections.add(new DashboardSection(SECTION_OVERVIEW, "Overview", buildOverviewCard()));
        sections.add(new DashboardSection(SECTION_CONNECTION, "Connection", buildConnectionCard()));
        sections.add(new DashboardSection(SECTION_ALERTS, "Alerts", buildAlertsCard()));
        sections.add(new DashboardSection(SECTION_SYSTEM, "System", buildSystemCard()));

        // Bottom buttons
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> onRefresh());
        bottomButtons.add(refreshBtn);

        return sections;
    }

    // ========== Pages (from RemoteCandlePageManager) ==========

    @Override
    protected List<DashboardPageInfo> collectPages() {
        List<DashboardPageInfo> result = new ArrayList<>();
        DeskAppContext ctx = DeskAppContext.getInstance();

        RemoteCandlePageManager pageMgr = ctx.getCandlePageManager();
        if (pageMgr != null) {
            for (RemoteCandlePageManager.PageInfo page : pageMgr.getActivePages()) {
                // Show market type prefix: Perps vs Spot
                String marketType = "spot".equalsIgnoreCase(page.marketType()) ? "Spot" : "Perps";
                String displayName = marketType + " " + page.symbol();
                if (page.timeframe() != null) displayName += "/" + page.timeframe();

                result.add(new DashboardPageInfo(
                    page.key(),
                    displayName,
                    page.dataType().getDisplayName(),
                    mapPageState(page.state()),
                    page.listenerCount(),
                    page.recordCount(),
                    page.loadProgress(),
                    page.liveEnabled(),
                    page.consumers() != null ? page.consumers() : java.util.List.of()
                ));
            }
        }

        return result;
    }

    private static DashboardPageInfo.State mapPageState(com.tradery.dataclient.page.PageState state) {
        return switch (state) {
            case EMPTY -> DashboardPageInfo.State.EMPTY;
            case LOADING -> DashboardPageInfo.State.LOADING;
            case READY -> DashboardPageInfo.State.READY;
            case UPDATING -> DashboardPageInfo.State.UPDATING;
            case ERROR -> DashboardPageInfo.State.ERROR;
        };
    }

    // ========== Refresh ==========

    @Override
    protected void onRefresh() {
        DeskAppContext ctx = DeskAppContext.getInstance();
        refreshOverview(ctx);
        refreshConnection(ctx);
        refreshAlerts(ctx);
        refreshSystem(ctx);
        refreshStrategies(ctx);
    }

    // ========== Section cards ==========

    private JPanel buildOverviewCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints vc = valueConstraints();
        int row = 0;

        addHeader(panel, "Overview", row++);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Connection:"), lc);
        ovConnectionLabel = createValueLabel();
        panel.add(ovConnectionLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Active Strategies:"), lc);
        ovStrategiesLabel = createValueLabel();
        panel.add(ovStrategiesLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Signals Today:"), lc);
        ovSignalsLabel = createValueLabel();
        panel.add(ovSignalsLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Heap:"), lc);
        ovHeapLabel = createValueLabel();
        panel.add(ovHeapLabel, vc);

        // Strategies section (dynamic)
        lc.gridy = row; vc.gridy = row++;
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row++;
        sc.gridwidth = 2;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(16, 0, 4, 0);
        panel.add(createSectionHeader("Strategies"), sc);

        sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row++;
        sc.gridwidth = 2;
        sc.fill = GridBagConstraints.BOTH;
        sc.weightx = 1.0;
        sc.weighty = 1.0;
        strategyPanel = new JPanel();
        strategyPanel.setLayout(new BoxLayout(strategyPanel, BoxLayout.Y_AXIS));
        JScrollPane stratScroll = new JScrollPane(strategyPanel);
        stratScroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(stratScroll, sc);

        return wrapCard(panel);
    }

    private JPanel buildConnectionCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints vc = valueConstraints();
        int row = 0;

        addHeader(panel, "Connection", row++);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("WebSocket State:"), lc);
        connStateLabel = createValueLabel();
        panel.add(connStateLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Reconnect Count:"), lc);
        connReconnectLabel = createValueLabel();
        panel.add(connReconnectLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Stream URL:"), lc);
        connUrlLabel = createValueLabel();
        panel.add(connUrlLabel, vc);

        addFiller(panel, row);
        return wrapCard(panel);
    }

    private JPanel buildAlertsCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints vc = valueConstraints();
        int row = 0;

        addHeader(panel, "Alerts", row++);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Desktop:"), lc);
        alertDesktopLabel = createValueLabel();
        panel.add(alertDesktopLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Audio:"), lc);
        alertAudioLabel = createValueLabel();
        panel.add(alertAudioLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Console:"), lc);
        alertConsoleLabel = createValueLabel();
        panel.add(alertConsoleLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Webhook:"), lc);
        alertWebhookLabel = createValueLabel();
        panel.add(alertWebhookLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Webhook URL:"), lc);
        alertWebhookUrlLabel = createValueLabel();
        panel.add(alertWebhookUrlLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Signals Dispatched:"), lc);
        alertSignalCountLabel = createValueLabel();
        panel.add(alertSignalCountLabel, vc);

        addFiller(panel, row);
        return wrapCard(panel);
    }

    private JPanel buildSystemCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        GridBagConstraints lc = labelConstraints();
        GridBagConstraints vc = valueConstraints();
        int row = 0;

        addHeader(panel, "System", row++);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Heap:"), lc);
        sysHeapLabel = createValueLabel();
        panel.add(sysHeapLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        panel.add(createBoldLabel("Uptime:"), lc);
        sysUptimeLabel = createValueLabel();
        panel.add(sysUptimeLabel, vc);

        addFiller(panel, row);
        return wrapCard(panel);
    }

    // ========== Refresh logic ==========

    private void refreshOverview(DeskAppContext ctx) {
        if (ovConnectionLabel == null) return;

        DataServiceConnection conn = ctx.getPageConnection();
        String connText = conn != null ? conn.getConnectionState().name() : "N/A";
        ovConnectionLabel.setText(connText);
        ovConnectionLabel.setForeground(getConnectionColor(conn));

        ovStrategiesLabel.setText(String.valueOf(ctx.getStrategies().size()));
        ovSignalsLabel.setText(String.valueOf(ctx.getSignalCount()));

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        ovHeapLabel.setText(formatBytes(used) + " / " + formatBytes(max));
    }

    private void refreshConnection(DeskAppContext ctx) {
        if (connStateLabel == null) return;

        DataServiceConnection conn = ctx.getPageConnection();
        if (conn != null) {
            connStateLabel.setText(conn.getConnectionState().name());
            connStateLabel.setForeground(getConnectionColor(conn));
            connReconnectLabel.setText(conn.isConnected() ? "Active" : "-");
            connUrlLabel.setText(conn.isConnected() ? "Connected" : "Disconnected");
        } else {
            connStateLabel.setText("No page connection");
            connStateLabel.setForeground(COLOR_ERROR);
            connReconnectLabel.setText("-");
            connUrlLabel.setText("-");
        }
    }

    private void refreshAlerts(DeskAppContext ctx) {
        if (alertDesktopLabel == null) return;

        DeskConfig config = ctx.getConfig();
        if (config != null) {
            DeskConfig.AlertSettings alerts = config.getAlerts();
            alertDesktopLabel.setText(alerts.isDesktop() ? "Enabled" : "Disabled");
            alertDesktopLabel.setForeground(alerts.isDesktop() ? COLOR_OK : COLOR_IDLE);
            alertAudioLabel.setText(alerts.isAudio() ? "Enabled" : "Disabled");
            alertAudioLabel.setForeground(alerts.isAudio() ? COLOR_OK : COLOR_IDLE);
            alertConsoleLabel.setText(alerts.isConsole() ? "Enabled" : "Disabled");
            alertConsoleLabel.setForeground(alerts.isConsole() ? COLOR_OK : COLOR_IDLE);
            alertWebhookLabel.setText(alerts.getWebhook().isEnabled() ? "Enabled" : "Disabled");
            alertWebhookLabel.setForeground(alerts.getWebhook().isEnabled() ? COLOR_OK : COLOR_IDLE);
            String url = alerts.getWebhook().getUrl();
            alertWebhookUrlLabel.setText(url != null && !url.isEmpty() ? url : "-");
        }
        alertSignalCountLabel.setText(String.valueOf(ctx.getSignalCount()));
    }

    private void refreshSystem(DeskAppContext ctx) {
        if (sysHeapLabel == null) return;

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        double pct = (double) used / max * 100;
        sysHeapLabel.setText(String.format("%s / %s (%.1f%%)", formatBytes(used), formatBytes(max), pct));
        sysHeapLabel.setForeground(pct > 80 ? COLOR_ERROR : pct > 60 ? COLOR_WARNING : COLOR_OK);

        Instant start = ctx.getStartTime();
        if (start != null) {
            Duration uptime = Duration.between(start, Instant.now());
            long hours = uptime.toHours();
            long mins = uptime.toMinutesPart();
            long secs = uptime.toSecondsPart();
            sysUptimeLabel.setText(String.format("%dh %dm %ds", hours, mins, secs));
        } else {
            sysUptimeLabel.setText("-");
        }
    }

    private void refreshStrategies(DeskAppContext ctx) {
        Map<String, PublishedStrategy> strategies = ctx.getStrategies();

        // Rebuild strategy panel if count changed
        if (strategyLabels.size() != strategies.size()) {
            strategyPanel.removeAll();
            strategyLabels.clear();

            for (PublishedStrategy strategy : strategies.values()) {
                StrategyCardLabels labels = new StrategyCardLabels();

                JPanel row = new JPanel(new GridBagLayout());
                row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                GridBagConstraints lc = labelConstraints();
                GridBagConstraints vc = valueConstraints();
                int r = 0;

                lc.gridy = r; vc.gridy = r++;
                JLabel nameLabel = createBoldLabel(strategy.getName());
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
                GridBagConstraints nc = new GridBagConstraints();
                nc.gridx = 0; nc.gridy = 0; nc.gridwidth = 2;
                nc.anchor = GridBagConstraints.WEST;
                nc.insets = new Insets(0, 0, 4, 0);
                row.add(nameLabel, nc);

                lc.gridy = 1; vc.gridy = 1;
                row.add(createBoldLabel("Symbol:"), lc);
                labels.symbolLabel = createValueLabel();
                labels.symbolLabel.setText(strategy.getSymbol() + " " + strategy.getTimeframe());
                row.add(labels.symbolLabel, vc);

                lc.gridy = 2; vc.gridy = 2;
                row.add(createBoldLabel("Candles:"), lc);
                labels.candleCountLabel = createValueLabel();
                row.add(labels.candleCountLabel, vc);

                lc.gridy = 3; vc.gridy = 3;
                row.add(createBoldLabel("Last Eval:"), lc);
                labels.lastEvalLabel = createValueLabel();
                row.add(labels.lastEvalLabel, vc);

                strategyLabels.put(strategy.getId(), labels);
                strategyPanel.add(row);

                JSeparator sep = new JSeparator();
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                strategyPanel.add(sep);
            }

            if (strategies.isEmpty()) {
                JLabel empty = new JLabel("No strategies loaded");
                empty.setForeground(Color.GRAY);
                empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 11f));
                strategyPanel.add(empty);
            }

            strategyPanel.revalidate();
            strategyPanel.repaint();
        }

        // Update values
        for (var entry : strategyLabels.entrySet()) {
            String id = entry.getKey();
            StrategyCardLabels labels = entry.getValue();
            CandleAggregator agg = ctx.getAggregators().get(id);
            SignalEvaluator eval = ctx.getEvaluators().get(id);

            if (agg != null) {
                labels.candleCountLabel.setText(String.valueOf(agg.getHistory().size()));
            } else {
                labels.candleCountLabel.setText("-");
            }

            if (eval != null && eval.getLastEvalTime() != null) {
                Duration ago = Duration.between(eval.getLastEvalTime(), Instant.now());
                labels.lastEvalLabel.setText(ago.toSeconds() + "s ago");
            } else {
                labels.lastEvalLabel.setText("-");
            }
        }
    }

    // ========== Helpers ==========

    private Color getConnectionColor(DataServiceConnection conn) {
        if (conn == null) return COLOR_IDLE;
        return switch (conn.getConnectionState()) {
            case CONNECTED -> COLOR_OK;
            case CONNECTING, RECONNECTING -> COLOR_WARNING;
            case DISCONNECTED -> COLOR_ERROR;
        };
    }

    private JPanel wrapCard(JPanel inner) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(inner, BorderLayout.NORTH);
        return wrapper;
    }

    private GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 4, 3, 12);
        c.gridx = 0;
        return c;
    }

    private GridBagConstraints valueConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(3, 0, 3, 4);
        c.gridx = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        return c;
    }

    private void addHeader(JPanel panel, String title, int row) {
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = row;
        hc.gridwidth = 2;
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.insets = new Insets(0, 0, 12, 0);
        panel.add(createSectionHeader(title), hc);
    }

    private void addFiller(JPanel panel, int row) {
        GridBagConstraints f = new GridBagConstraints();
        f.gridy = row;
        f.weighty = 1.0;
        f.gridwidth = 2;
        panel.add(Box.createGlue(), f);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.0f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class StrategyCardLabels {
        JLabel symbolLabel;
        JLabel candleCountLabel;
        JLabel lastEvalLabel;
    }
}
