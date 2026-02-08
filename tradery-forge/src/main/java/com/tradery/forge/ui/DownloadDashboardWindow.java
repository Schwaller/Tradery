package com.tradery.forge.ui;

import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.TraderyApp;
import com.tradery.data.page.DataType;
import com.tradery.data.page.PageState;
import com.tradery.forge.data.log.DownloadEvent;
import com.tradery.forge.data.log.DownloadLogStore;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;
import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.service.SymbolService.ExchangeCoverage;
import com.tradery.symbols.service.SymbolService.ExchangeSyncInfo;
import com.tradery.ui.dashboard.DashboardPageInfo;
import com.tradery.ui.dashboard.DashboardSection;
import com.tradery.ui.dashboard.DashboardWindow;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.BorderlessTable;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.dashboard.PageLogEntry;

import static com.tradery.forge.ui.UIColors.STATUS_ERROR;
import static com.tradery.forge.ui.UIColors.STATUS_READY;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Forge download dashboard. Extends the shared DashboardWindow,
 * mapping forge-specific page types to common DashboardPageInfo.
 * Adds forge-specific sections: overview (timeline + log), symbol DB.
 * Page detail is handled by the base class's built-in PageDetailPanel.
 */
public class DownloadDashboardWindow extends DashboardWindow {

    private static final String SECTION_OVERVIEW = "overview";
    private static final String SECTION_SYMBOL_DB = "symbol_db";

    private static DownloadDashboardWindow instance;

    // Forge-specific panels
    private DataTimelinePanel timelinePanel;
    private DownloadLogPanel logPanel;

    // Symbol DB labels
    private JLabel symDbStatusLabel;
    private JLabel symDbPairCountLabel;
    private JLabel symDbLastSyncLabel;
    private JLabel symDbPathLabel;
    private CoverageTableModel coverageTableModel;
    private DefaultListModel<String> symDbLogModel;

    public DownloadDashboardWindow() {
        super("Download Dashboard - " + TraderyApp.APP_NAME);
        timelinePanel = new DataTimelinePanel();
        logPanel = new DownloadLogPanel();
        initialize();
    }

    public static void showWindow() {
        showWindow(null);
    }

    public static void showWindow(DataType dataType) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DownloadDashboardWindow();
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
        if (dataType != null) {
            List<DashboardPageInfo> pages = instance.collectPages();
            String category = dataType.getDisplayName();
            for (DashboardPageInfo p : pages) {
                if (category.equals(p.category())) {
                    instance.selectPage(p.key());
                    break;
                }
            }
        }
    }

    public static void showWindowForIndicators() {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DownloadDashboardWindow();
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
        List<DashboardPageInfo> pages = instance.collectPages();
        for (DashboardPageInfo p : pages) {
            if ("Indicators".equals(p.category())) {
                instance.selectPage(p.key());
                break;
            }
        }
    }

    @Override
    protected void onWindowClosed() {
        instance = null;
    }

    // ========== Sections ==========

    @Override
    protected List<DashboardSection> collectSections() {
        List<DashboardSection> sections = new ArrayList<>();

        // Overview: timeline + log
        ThinSplitPane overviewSplit = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel timelineWrapper = new JPanel(new BorderLayout());
        timelineWrapper.setBorder(new EmptyBorder(8, 8, 0, 8));
        timelineWrapper.setPreferredSize(new Dimension(0, 300));
        timelineWrapper.add(timelinePanel, BorderLayout.CENTER);
        overviewSplit.setTopComponent(timelineWrapper);
        JPanel logWrapper = new JPanel(new BorderLayout());
        logWrapper.setBorder(new EmptyBorder(0, 8, 8, 8));
        logWrapper.setPreferredSize(new Dimension(0, 300));
        logWrapper.add(logPanel, BorderLayout.CENTER);
        overviewSplit.setBottomComponent(logWrapper);
        overviewSplit.setResizeWeight(0.5);
        sections.add(new DashboardSection(SECTION_OVERVIEW, "Overview", overviewSplit));

        // Symbol Database
        sections.add(new DashboardSection(SECTION_SYMBOL_DB, "Symbol Database",
            createSymbolDbPanel(), getSymbolDbStatus()));

        // Bottom buttons
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> onRefresh());
        bottomButtons.add(refreshBtn);

        JButton clearLogBtn = new JButton("Clear Log");
        clearLogBtn.addActionListener(e -> {
            DownloadLogStore.getInstance().clear();
            logPanel.refresh();
        });
        bottomButtons.add(clearLogBtn);

        return sections;
    }

    // ========== Pages ==========

    @Override
    protected List<DashboardPageInfo> collectPages() {
        List<DashboardPageInfo> result = new ArrayList<>();
        ApplicationContext ctx = ApplicationContext.getInstance();

        // Data pages
        collectDataPages(ctx.getCandlePageManager(), result);
        collectDataPages(ctx.getFundingPageManager(), result);
        collectDataPages(ctx.getOIPageManager(), result);
        collectDataPages(ctx.getAggTradesPageManager(), result);
        collectDataPages(ctx.getPremiumPageManager(), result);

        // Indicator pages
        if (ctx.getIndicatorPageManager() != null) {
            for (IndicatorPageManager.IndicatorPageInfo ind : ctx.getIndicatorPageManager().getActivePages()) {
                result.add(new DashboardPageInfo(
                    ind.key(),  // Use actual key for log store lookup
                    ind.type() + "(" + ind.params() + ")",
                    "Indicators",
                    mapPageState(ind.state()),
                    ind.listenerCount(),
                    0,
                    ind.loadProgress(),
                    false,
                    ind.consumers() != null ? ind.consumers() : List.of()
                ));
            }
        }

        return result;
    }

    private void collectDataPages(DataPageManager<?> manager, List<DashboardPageInfo> result) {
        if (manager == null) return;
        for (DataPageManager.PageInfo page : manager.getActivePages()) {
            String marketDisplay = formatMarket(page.marketType());
            String exchangeDisplay = formatExchange(page.exchange() != null ? page.exchange() : "binance");
            String displayName = exchangeDisplay + " " + page.symbol() + " " + marketDisplay;
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
                page.consumers() != null ? page.consumers() : List.of()
            ));
        }
    }

    private static DashboardPageInfo.State mapPageState(PageState state) {
        return switch (state) {
            case EMPTY -> DashboardPageInfo.State.EMPTY;
            case LOADING -> DashboardPageInfo.State.LOADING;
            case READY -> DashboardPageInfo.State.READY;
            case UPDATING -> DashboardPageInfo.State.UPDATING;
            case ERROR -> DashboardPageInfo.State.ERROR;
        };
    }

    // ========== Page log (from DownloadLogStore) ==========

    @Override
    protected List<PageLogEntry> getPageLog(String pageKey) {
        List<DownloadEvent> events = DownloadLogStore.getInstance().getPageLog(pageKey);
        List<PageLogEntry> result = new ArrayList<>();
        for (DownloadEvent event : events) {
            result.add(new PageLogEntry(
                event.timestamp(),
                formatEventType(event.eventType()),
                event.message()
            ));
        }
        return result;
    }

    private static String formatEventType(DownloadEvent.EventType type) {
        return switch (type) {
            case PAGE_CREATED -> "Page created";
            case LOAD_STARTED -> "Start loading";
            case LOAD_COMPLETED -> "Loading complete";
            case UPDATE_STARTED -> "Start update";
            case UPDATE_COMPLETED -> "Update complete";
            case ERROR -> "Error";
            case PAGE_RELEASED -> "Page released";
            case LISTENER_ADDED -> "Consumer added";
            case LISTENER_REMOVED -> "Consumer removed";
            case CONNECTION_OPENED -> "Connected";
            case CONNECTION_CLOSED -> "Disconnected";
            case LIVE_UPDATE -> "Live update";
            case LIVE_CANDLE_CLOSED -> "Candle closed";
            case VISION_DOWNLOAD_STARTED -> "Vision download";
            case VISION_DOWNLOAD_PROGRESS -> "Vision progress";
            case VISION_DOWNLOAD_COMPLETED -> "Vision complete";
            case API_REQUEST_STARTED -> "API request";
            case API_REQUEST_COMPLETED -> "API response";
        };
    }

    // ========== Refresh ==========

    @Override
    protected void onRefresh() {
        List<DataPageManager.PageInfo> allPages = collectAllForgePages();
        List<IndicatorPageManager.IndicatorPageInfo> indicators = collectIndicatorPages();
        timelinePanel.update(allPages, indicators);
        logPanel.refresh();
        refreshSymbolDbPanel();
    }

    // ========== Forge data collection (raw types for forge-specific panels) ==========

    private List<DataPageManager.PageInfo> collectAllForgePages() {
        List<DataPageManager.PageInfo> allPages = new ArrayList<>();
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx.getCandlePageManager() != null) allPages.addAll(ctx.getCandlePageManager().getActivePages());
        if (ctx.getFundingPageManager() != null) allPages.addAll(ctx.getFundingPageManager().getActivePages());
        if (ctx.getOIPageManager() != null) allPages.addAll(ctx.getOIPageManager().getActivePages());
        if (ctx.getAggTradesPageManager() != null) allPages.addAll(ctx.getAggTradesPageManager().getActivePages());
        if (ctx.getPremiumPageManager() != null) allPages.addAll(ctx.getPremiumPageManager().getActivePages());
        return allPages;
    }

    private List<IndicatorPageManager.IndicatorPageInfo> collectIndicatorPages() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx.getIndicatorPageManager() != null) {
            return ctx.getIndicatorPageManager().getActivePages();
        }
        return new ArrayList<>();
    }

    // ========== Symbol DB ==========

    private DashboardSection.StatusColor getSymbolDbStatus() {
        SymbolService symbolService = ApplicationContext.getInstance().getSymbolService();
        if (!symbolService.isDatabaseAvailable()) return DashboardSection.StatusColor.ERROR;
        return symbolService.getSyncStatus().pairCount() > 0
            ? DashboardSection.StatusColor.OK
            : DashboardSection.StatusColor.IDLE;
    }

    private JPanel createSymbolDbPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel infoSection = new JPanel(new BorderLayout(0, 6));
        infoSection.setOpaque(false);
        infoSection.add(createSectionHeader("Symbol Database"), BorderLayout.NORTH);

        JPanel infoGrid = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(2, 4, 2, 8);
        GridBagConstraints vc = new GridBagConstraints();
        vc.anchor = GridBagConstraints.WEST;
        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.weightx = 1.0;
        vc.insets = new Insets(2, 0, 2, 4);
        vc.gridwidth = GridBagConstraints.REMAINDER;

        int row = 0;

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(createBoldLabel("Status:"), lc);
        symDbStatusLabel = new JLabel("-");
        infoGrid.add(symDbStatusLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(createBoldLabel("Pairs:"), lc);
        symDbPairCountLabel = new JLabel("-");
        infoGrid.add(symDbPairCountLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(createBoldLabel("Last Sync:"), lc);
        symDbLastSyncLabel = new JLabel("-");
        infoGrid.add(symDbLastSyncLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(createBoldLabel("Path:"), lc);
        symDbPathLabel = new JLabel("-");
        symDbPathLabel.setFont(symDbPathLabel.getFont().deriveFont(Font.PLAIN, 10f));
        symDbPathLabel.setForeground(Color.GRAY);
        infoGrid.add(symDbPathLabel, vc);

        infoSection.add(infoGrid, BorderLayout.CENTER);

        JPanel exchSection = new JPanel(new BorderLayout(0, 6));
        exchSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        exchSection.add(createSectionHeader("Exchanges"), BorderLayout.NORTH);
        coverageTableModel = new CoverageTableModel();
        JTable coverageTable = new BorderlessTable(coverageTableModel);
        coverageTable.setRowHeight(20);
        coverageTable.setFocusable(false);
        coverageTable.setRowSelectionAllowed(false);
        coverageTable.setFont(coverageTable.getFont().deriveFont(11f));
        coverageTable.getTableHeader().setFont(coverageTable.getFont().deriveFont(Font.BOLD, 11f));
        coverageTable.getTableHeader().setReorderingAllowed(false);

        coverageTable.getColumnModel().getColumn(0).setPreferredWidth(80);  // Exchange
        coverageTable.getColumnModel().getColumn(1).setPreferredWidth(50);  // Market
        coverageTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Pairs
        coverageTable.getColumnModel().getColumn(3).setPreferredWidth(90);  // Matched
        coverageTable.getColumnModel().getColumn(4).setPreferredWidth(70);  // Last Sync
        coverageTable.getColumnModel().getColumn(5).setPreferredWidth(50);  // Status

        // Dim secondary columns
        DefaultTableCellRenderer dimRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                c.setForeground(UIManager.getColor("Label.disabledForeground"));
                return c;
            }
        };
        coverageTable.getColumnModel().getColumn(1).setCellRenderer(dimRenderer);
        coverageTable.getColumnModel().getColumn(4).setCellRenderer(dimRenderer);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        coverageTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);

        DefaultTableCellRenderer matchedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                c.setForeground(UIManager.getColor("Label.disabledForeground"));
                return c;
            }
        };
        coverageTable.getColumnModel().getColumn(3).setCellRenderer(matchedRenderer);

        coverageTable.getColumnModel().getColumn(5).setCellRenderer(new StatusCellRenderer());

        BorderlessScrollPane exchScroll = new BorderlessScrollPane(coverageTable);
        exchScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        exchSection.add(exchScroll, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(infoSection, BorderLayout.NORTH);
        topPanel.add(exchSection, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);

        JPanel logSection = new JPanel(new BorderLayout(0, 6));
        logSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        logSection.add(createSectionHeader("Activity Log"), BorderLayout.NORTH);
        symDbLogModel = new DefaultListModel<>();
        JList<String> logList = new JList<>(symDbLogModel);
        logList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        BorderlessScrollPane logScroll = new BorderlessScrollPane(logList);
        logSection.add(logScroll, BorderLayout.CENTER);
        panel.add(logSection, BorderLayout.CENTER);

        return panel;
    }

    private void refreshSymbolDbPanel() {
        SymbolService symbolService = ApplicationContext.getInstance().getSymbolService();

        if (!symbolService.isDatabaseAvailable()) {
            symDbStatusLabel.setText("Unavailable");
            symDbStatusLabel.setForeground(STATUS_ERROR);
            symDbPairCountLabel.setText("-");
            symDbLastSyncLabel.setText("-");
            symDbPathLabel.setText("symbols.db not found \u2014 run data service to sync");
            coverageTableModel.setRows(List.of());
            symDbLogModel.clear();
            symDbLogModel.addElement("Database file not found");
            return;
        }

        SymbolService.SyncStatus status = symbolService.getSyncStatus();

        symDbStatusLabel.setText("Available");
        symDbStatusLabel.setForeground(STATUS_READY);
        symDbPairCountLabel.setText(String.format("%,d", status.pairCount()));

        if (status.lastSync() != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
            symDbLastSyncLabel.setText(fmt.format(status.lastSync()));
        } else {
            symDbLastSyncLabel.setText("Never");
        }

        symDbPathLabel.setText(System.getProperty("user.home") + "/.tradery/symbols.db");

        // Populate coverage table
        List<ExchangeCoverage> coverage = symbolService.getExchangeCoverage();
        List<ExchangeSyncInfo> syncInfo = symbolService.getSyncInfo();
        Map<String, ExchangeSyncInfo> syncMap = new LinkedHashMap<>();
        for (ExchangeSyncInfo info : syncInfo) {
            syncMap.put(info.exchange() + "|" + info.marketType(), info);
        }

        List<CoverageRow> rows = new ArrayList<>();
        for (ExchangeCoverage cov : coverage) {
            ExchangeSyncInfo info = syncMap.get(cov.exchange() + "|" + cov.marketType());
            Instant lastSync = info != null ? info.lastSync() : null;
            String syncStatus = info != null ? info.status() : null;

            String displayStatus;
            if ("error".equalsIgnoreCase(syncStatus)) {
                displayStatus = "ERROR";
            } else if (lastSync != null && Duration.between(lastSync, Instant.now()).toHours() > 24) {
                displayStatus = "STALE";
            } else if (lastSync != null) {
                displayStatus = "OK";
            } else {
                displayStatus = "\u2014";
            }

            rows.add(new CoverageRow(
                cov.exchange(), cov.marketType(), cov.pairCount(), cov.matchedCount(),
                lastSync != null ? formatRelativeTime(lastSync) : "\u2014",
                displayStatus
            ));
        }
        coverageTableModel.setRows(rows);

        symDbLogModel.clear();
        if (status.lastSync() != null) {
            symDbLogModel.addElement("Last sync: " + symDbLastSyncLabel.getText());
        }
        symDbLogModel.addElement("Active pairs: " + status.pairCount());
        symDbLogModel.addElement("Exchanges: " + symbolService.getExchanges().size());
        if (symDbLogModel.isEmpty()) {
            symDbLogModel.addElement("(no activity)");
        }
    }

    private static String formatRelativeTime(Instant instant) {
        Duration d = Duration.between(instant, Instant.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = d.toHours();
        if (hours < 24) return hours + "h ago";
        long days = d.toDays();
        return days + "d ago";
    }

    // --- Coverage table data ---

    record CoverageRow(String exchange, String market, int pairs, int matched, String lastSync, String status) {}

    private static class CoverageTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Exchange", "Market", "Pairs", "Matched", "Last Sync", "Status"};
        private List<CoverageRow> rows = List.of();

        void setRows(List<CoverageRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CoverageRow r = rows.get(row);
            return switch (col) {
                case 0 -> formatExchange(r.exchange());
                case 1 -> formatMarket(r.market());
                case 2 -> String.format("%,d", r.pairs());
                case 3 -> String.format("%,d / %,d", r.matched(), r.pairs());
                case 4 -> r.lastSync();
                case 5 -> r.status();
                default -> "";
            };
        }
    }

    private static String formatExchange(String configKey) {
        Exchange ex = Exchange.fromConfigKey(configKey);
        return ex != null ? ex.getDisplayName() : configKey;
    }

    private static String formatMarket(String configKey) {
        DataMarketType mt = DataMarketType.fromConfigKey(configKey);
        return mt != null ? mt.getDisplayName() : configKey;
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final Color COLOR_OK = new Color(60, 140, 60);
        private static final Color COLOR_ERROR = new Color(180, 60, 60);
        private static final Color COLOR_STALE = new Color(180, 120, 40);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            String status = value != null ? value.toString() : "";
            c.setForeground(switch (status) {
                case "OK" -> COLOR_OK;
                case "ERROR" -> COLOR_ERROR;
                case "STALE" -> COLOR_STALE;
                default -> UIManager.getColor("Label.disabledForeground");
            });
            return c;
        }
    }
}
