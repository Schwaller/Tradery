package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.TraderyApp;
import com.tradery.forge.data.DataType;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.log.DownloadEvent;
import com.tradery.forge.data.log.DownloadLogStore;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive Download Dashboard showing data page status, event logs, and statistics.
 * Combines the compact status view with detailed page info and event logs.
 */
public class DownloadDashboardWindow extends JFrame {

    private static final int REFRESH_INTERVAL_MS = 250;

    // Singleton instance
    private static DownloadDashboardWindow instance;

    /**
     * Show the Download Dashboard window. Creates a new instance if none exists,
     * or brings the existing window to front.
     */
    public static void showWindow() {
        showWindow(null);
    }

    /**
     * Show the Download Dashboard window and select the first page of the given data type.
     * Pass null to show the overview.
     */
    public static void showWindow(DataType dataType) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DownloadDashboardWindow();
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
        if (dataType != null) {
            instance.statusPanel.selectFirstByDataType(dataType);
        }
    }

    /**
     * Show the Download Dashboard and select the first indicator row.
     */
    public static void showWindowForIndicators() {
        if (instance == null || !instance.isDisplayable()) {
            instance = new DownloadDashboardWindow();
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
        instance.statusPanel.selectFirstIndicator();
    }

    private DataTimelinePanel timelinePanel;
    private PageStatusPanel statusPanel;
    private DownloadLogPanel logPanel;
    private PageDetailPanel detailPanel;
    private JLabel statusLabel;
    private Timer refreshTimer;

    // Content area with card layout
    private JPanel contentCards;
    private CardLayout cardLayout;
    private static final String CARD_OVERVIEW = "overview";
    private static final String CARD_DETAILS = "details";

    // Currently selected page key (for detail view)
    private String selectedPageKey;
    private DataType selectedDataType;

    public DownloadDashboardWindow() {
        super("Download Dashboard - " + TraderyApp.APP_NAME);

        initializeFrame();
        initializeComponents();
        layoutComponents();
        startRefreshTimer();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        // Clean up on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
                instance = null;
            }
        });
    }

    private void initializeComponents() {
        // Timeline panel showing dependency diagram and data availability
        timelinePanel = new DataTimelinePanel();

        // Status panel for page managers (compact view with selection)
        statusPanel = new PageStatusPanel(this::onPageSelected);

        // Log panel for events
        logPanel = new DownloadLogPanel();

        // Detail panel for selected page
        detailPanel = new PageDetailPanel();

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);
    }

    private void layoutComponents() {
        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel("Download Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Left panel: navigation (status panel only)
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        leftPanel.add(statusPanel, BorderLayout.CENTER);

        // Right panel: content area with cards
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);

        // Card 1: Overview (timeline panel + log below)
        JSplitPane overviewSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel timelineWrapper = new JPanel(new BorderLayout());
        timelineWrapper.setBorder(new EmptyBorder(8, 8, 0, 8));
        timelineWrapper.add(timelinePanel, BorderLayout.CENTER);
        overviewSplit.setTopComponent(timelineWrapper);
        JPanel logWrapper = new JPanel(new BorderLayout());
        logWrapper.setBorder(new EmptyBorder(0, 8, 8, 8));
        logWrapper.add(logPanel, BorderLayout.CENTER);
        overviewSplit.setBottomComponent(logWrapper);
        overviewSplit.setResizeWeight(0.5);
        contentCards.add(overviewSplit, CARD_OVERVIEW);

        // Card 2: Details (page details only)
        contentCards.add(createDetailTab(), CARD_DETAILS);

        // Show overview by default
        cardLayout.show(contentCards, CARD_OVERVIEW);

        // Main split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(contentCards);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0);

        // Bottom bar with separator line above
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 0));
        bottomContainer.add(new JSeparator(), BorderLayout.NORTH);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Left side: view controls
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        leftButtons.add(refreshButton);

        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> {
            DownloadLogStore.getInstance().clear();
            logPanel.refresh();
        });
        leftButtons.add(clearLogButton);

        bottomBar.add(leftButtons, BorderLayout.WEST);
        bottomBar.add(statusLabel, BorderLayout.EAST);
        bottomContainer.add(bottomBar, BorderLayout.CENTER);

        // Top container with title and separator below
        JPanel topContainer = new JPanel(new BorderLayout(0, 0));
        topContainer.add(titleBar, BorderLayout.CENTER);
        topContainer.add(new JSeparator(), BorderLayout.SOUTH);

        // Main layout
        setLayout(new BorderLayout());
        add(topContainer, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private JPanel createDetailTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(detailPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(logPanel, BorderLayout.CENTER);
        return panel;
    }

    private void onPageSelected(String pageKey, DataType dataType) {
        if (PageStatusPanel.OVERVIEW_KEY.equals(pageKey)) {
            // Show overview/timeline
            this.selectedPageKey = null;
            this.selectedDataType = null;
            cardLayout.show(contentCards, CARD_OVERVIEW);
        } else {
            // Show page details
            this.selectedPageKey = pageKey;
            this.selectedDataType = dataType;
            detailPanel.setSelectedPage(pageKey, dataType);
            cardLayout.show(contentCards, CARD_DETAILS);
        }
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> refresh());
        refreshTimer.start();
    }

    private void refresh() {
        // Collect page info from all managers
        List<DataPageManager.PageInfo> allPages = collectAllPages();
        List<IndicatorPageManager.IndicatorPageInfo> indicatorPages = collectIndicatorPages();

        // Update timeline panel
        timelinePanel.update(allPages, indicatorPages);

        // Update status panel
        statusPanel.update(allPages, indicatorPages);

        // Update detail panel if a page is selected
        if (selectedPageKey != null) {
            detailPanel.refresh(allPages, indicatorPages);
        }

        // Update log panel
        logPanel.refresh();

        // Update status
        int totalPages = allPages.size();
        int loadingCount = (int) allPages.stream()
            .filter(p -> p.state() == PageState.LOADING || p.state() == PageState.UPDATING)
            .count();
        int errorCount = (int) allPages.stream()
            .filter(p -> p.state() == PageState.ERROR)
            .count();

        String status;
        if (loadingCount > 0) {
            status = String.format("%d pages, %d loading", totalPages, loadingCount);
        } else if (errorCount > 0) {
            status = String.format("%d pages, %d errors", totalPages, errorCount);
        } else {
            status = String.format("%d pages", totalPages);
        }
        statusLabel.setText(status);
    }

    private List<DataPageManager.PageInfo> collectAllPages() {
        List<DataPageManager.PageInfo> allPages = new ArrayList<>();
        ApplicationContext ctx = ApplicationContext.getInstance();

        if (ctx.getCandlePageManager() != null) {
            allPages.addAll(ctx.getCandlePageManager().getActivePages());
        }
        if (ctx.getFundingPageManager() != null) {
            allPages.addAll(ctx.getFundingPageManager().getActivePages());
        }
        if (ctx.getOIPageManager() != null) {
            allPages.addAll(ctx.getOIPageManager().getActivePages());
        }
        if (ctx.getAggTradesPageManager() != null) {
            allPages.addAll(ctx.getAggTradesPageManager().getActivePages());
        }
        if (ctx.getPremiumPageManager() != null) {
            allPages.addAll(ctx.getPremiumPageManager().getActivePages());
        }

        return allPages;
    }

    private List<IndicatorPageManager.IndicatorPageInfo> collectIndicatorPages() {
        ApplicationContext ctx = ApplicationContext.getInstance();
        if (ctx.getIndicatorPageManager() != null) {
            return ctx.getIndicatorPageManager().getActivePages();
        }
        return new ArrayList<>();
    }

    private void cleanup() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }

    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }
}
