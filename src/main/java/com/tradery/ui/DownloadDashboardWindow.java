package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.DataType;
import com.tradery.data.PageState;
import com.tradery.data.log.DownloadEvent;
import com.tradery.data.log.DownloadLogStore;
import com.tradery.data.page.DataPageManager;

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

    private PageStatusPanel statusPanel;
    private DownloadLogPanel logPanel;
    private PageDetailPanel detailPanel;
    private JLabel statusLabel;
    private Timer refreshTimer;

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
            }
        });
    }

    private void initializeComponents() {
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

        // Left panel: page status view
        JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        leftPanel.setPreferredSize(new Dimension(430, 0));
        leftPanel.add(statusPanel, BorderLayout.CENTER);

        // Right panel: detail + log tabs
        JTabbedPane rightTabs = new JTabbedPane();
        rightTabs.addTab("Page Details", createDetailTab());
        rightTabs.addTab("Event Log", createLogTab());

        // Main split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightTabs);
        splitPane.setDividerLocation(400);
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
        this.selectedPageKey = pageKey;
        this.selectedDataType = dataType;
        detailPanel.setSelectedPage(pageKey, dataType);
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> refresh());
        refreshTimer.start();
    }

    private void refresh() {
        // Collect page info from all managers
        List<DataPageManager.PageInfo> allPages = collectAllPages();

        // Update status panel
        statusPanel.update(allPages);

        // Update detail panel if a page is selected
        if (selectedPageKey != null) {
            detailPanel.refresh(allPages);
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
