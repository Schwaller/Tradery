package com.tradery.ui.dashboard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Dashboard window with built-in sidebar showing pages grouped by category
 * and extra sections. Both forge and desk extend this, providing pages via
 * {@link #collectPages()} and sections via {@link #collectSections()}.
 * The sidebar rendering (status dots, progress bars, grouping, hover/selection)
 * is identical regardless of which app is running.
 */
public abstract class DashboardWindow extends JFrame {

    private static final int DEFAULT_REFRESH_MS = 250;

    // Sidebar colors
    private static final Color COLOR_EMPTY = new Color(100, 100, 100);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_UPDATING = new Color(140, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_SELECTED_BG = new Color(50, 80, 120);
    private static final Color COLOR_HOVER_BG = new Color(60, 60, 60);

    protected JLabel statusLabel;
    protected JPanel contentCards;
    protected CardLayout cardLayout;
    protected JPanel bottomButtons;
    private javax.swing.Timer refreshTimer;

    // Sidebar
    private JPanel sidebarContent;
    private String selectedItemKey;
    private List<DashboardSection> cachedSections;

    // Built-in detail panel for pages
    private static final String CARD_PAGE_DETAIL = "__page_detail__";
    private PageDetailPanel pageDetailPanel;
    private List<DashboardPageInfo> lastCollectedPages = List.of();

    protected DashboardWindow(String title) {
        super(title);
        initFrame();
    }

    /**
     * Must be called by subclass after its own field initialization.
     * Builds the layout and starts the refresh timer.
     */
    protected final void initialize() {
        buildLayout();
        startRefreshTimer(getRefreshIntervalMs());
    }

    // ========== Abstract methods ==========

    /** Return pages to display in sidebar, grouped by category. Called on each refresh. */
    protected abstract List<DashboardPageInfo> collectPages();

    /** Return sidebar sections with content panels. Called once during init. */
    protected abstract List<DashboardSection> collectSections();

    /** Called on each refresh tick (on EDT), after sidebar is rebuilt. */
    protected abstract void onRefresh();

    // ========== Overridable ==========

    /** Return log entries for a page key. Override to provide app-specific log data. */
    protected List<PageLogEntry> getPageLog(String pageKey) { return List.of(); }

    protected int getRefreshIntervalMs() { return DEFAULT_REFRESH_MS; }
    protected void onCleanup() {}
    protected int getSidebarWidth() { return 300; }
    protected void onWindowClosed() {}

    // ========== Frame setup ==========

    private void initFrame() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
                onWindowClosed();
            }
        });
    }

    private void buildLayout() {
        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel(getTitle(), SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Sidebar
        sidebarContent = new JPanel();
        sidebarContent.setLayout(new BoxLayout(sidebarContent, BoxLayout.Y_AXIS));
        sidebarContent.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane sidebarScroll = new JScrollPane(sidebarContent);
        sidebarScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(80, 80, 80)));
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(getSidebarWidth(), 0));
        leftPanel.add(sidebarScroll, BorderLayout.CENTER);

        // Pre-create bottomButtons so subclasses can add to it in collectSections()
        bottomButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        // Content cards
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);

        // Built-in page detail card
        pageDetailPanel = new PageDetailPanel();
        JPanel detailWrapper = new JPanel(new BorderLayout());
        detailWrapper.setBorder(new EmptyBorder(8, 8, 8, 8));
        detailWrapper.add(pageDetailPanel, BorderLayout.CENTER);
        contentCards.add(detailWrapper, CARD_PAGE_DETAIL);

        // Collect sections (once) and add their content panels as cards
        cachedSections = collectSections();
        String defaultCard = null;
        for (DashboardSection section : cachedSections) {
            contentCards.add(section.contentPanel(), section.id());
            if (defaultCard == null) defaultCard = section.id();
        }
        selectedItemKey = defaultCard;
        if (defaultCard != null) {
            cardLayout.show(contentCards, defaultCard);
        }

        // Status label (must exist before rebuildSidebar)
        statusLabel = new JLabel("Ready");

        // Build initial sidebar
        rebuildSidebar();

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(contentCards);
        splitPane.setDividerLocation(getSidebarWidth());
        splitPane.setResizeWeight(0);

        // Bottom bar
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(new JSeparator(), BorderLayout.NORTH);
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottomBar.add(bottomButtons, BorderLayout.WEST);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);
        bottomBar.add(statusLabel, BorderLayout.EAST);
        bottomContainer.add(bottomBar, BorderLayout.CENTER);

        // Top container
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(titleBar, BorderLayout.CENTER);
        topContainer.add(new JSeparator(), BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(topContainer, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    // ========== Sidebar ==========

    /** Rebuild sidebar rows from cached sections + fresh pages. */
    protected void rebuildSidebar() {
        sidebarContent.removeAll();

        // Sections at top
        for (DashboardSection section : cachedSections) {
            addSectionRow(section);
        }

        // Separator between sections and pages
        List<DashboardPageInfo> pages = collectPages();
        if (!cachedSections.isEmpty() && !pages.isEmpty()) {
            sidebarContent.add(Box.createVerticalStrut(4));
            addSeparator();
            sidebarContent.add(Box.createVerticalStrut(4));
        }

        lastCollectedPages = pages;

        // Pages grouped by category
        Map<String, List<DashboardPageInfo>> grouped = new LinkedHashMap<>();
        for (DashboardPageInfo page : pages) {
            grouped.computeIfAbsent(page.category(), k -> new ArrayList<>()).add(page);
        }

        for (var entry : grouped.entrySet()) {
            addCategoryHeader(entry.getKey(), entry.getValue());
            sidebarContent.add(Box.createVerticalStrut(4));
            for (DashboardPageInfo page : entry.getValue()) {
                addPageRow(page);
                sidebarContent.add(Box.createVerticalStrut(2));
            }
            sidebarContent.add(Box.createVerticalStrut(12));
        }

        if (pages.isEmpty() && cachedSections.isEmpty()) {
            JLabel empty = new JLabel("No active pages");
            empty.setForeground(Color.GRAY);
            empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 12f));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebarContent.add(empty);
        }

        sidebarContent.add(Box.createVerticalGlue());
        sidebarContent.revalidate();
        sidebarContent.repaint();

        // Update status bar with page counts
        int total = pages.size();
        long loading = pages.stream()
            .filter(p -> p.state() == DashboardPageInfo.State.LOADING || p.state() == DashboardPageInfo.State.UPDATING)
            .count();
        long errors = pages.stream()
            .filter(p -> p.state() == DashboardPageInfo.State.ERROR)
            .count();
        if (loading > 0) {
            statusLabel.setText(String.format("%d pages, %d loading", total, loading));
        } else if (errors > 0) {
            statusLabel.setText(String.format("%d pages, %d errors", total, errors));
        } else if (total > 0) {
            statusLabel.setText(String.format("%d pages", total));
        } else {
            statusLabel.setText("Ready");
        }
    }

    private void addSeparator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarContent.add(sep);
    }

    private void addSectionRow(DashboardSection section) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // First section gets bold label
        boolean isFirst = cachedSections.indexOf(section) == 0;
        JLabel nameLabel = new JLabel(section.label());
        nameLabel.setFont(nameLabel.getFont().deriveFont(isFirst ? Font.BOLD : Font.PLAIN, isFirst ? 12f : 11f));
        row.add(nameLabel, BorderLayout.WEST);

        // Status dot
        if (section.statusColor() != DashboardSection.StatusColor.NONE) {
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightPanel.setOpaque(false);
            JLabel dot = new JLabel("\u25CF");
            dot.setForeground(getSectionColor(section.statusColor()));
            dot.setFont(dot.getFont().deriveFont(9f));
            rightPanel.add(dot);
            row.add(rightPanel, BorderLayout.EAST);
        }

        boolean isSelected = section.id().equals(selectedItemKey);
        row.setOpaque(isSelected);
        if (isSelected) row.setBackground(COLOR_SELECTED_BG);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectedItemKey = section.id();
                cardLayout.show(contentCards, section.id());
                rebuildSidebar();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!section.id().equals(selectedItemKey)) {
                    row.setBackground(COLOR_HOVER_BG);
                    row.setOpaque(true);
                    row.repaint();
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!section.id().equals(selectedItemKey)) {
                    row.setOpaque(false);
                    row.repaint();
                }
            }
        });

        sidebarContent.add(row);
    }

    private void addCategoryHeader(String category, List<DashboardPageInfo> pages) {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(category + " (" + pages.size() + ")");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(nameLabel, BorderLayout.WEST);

        // Overall status
        DashboardPageInfo.State overall = getOverallState(pages);
        int totalListeners = pages.stream().mapToInt(DashboardPageInfo::listenerCount).sum();
        int totalRecords = pages.stream().mapToInt(DashboardPageInfo::recordCount).sum();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        if (totalListeners > 0) {
            JLabel listenerLabel = new JLabel(totalListeners + " listener" + (totalListeners != 1 ? "s" : ""));
            listenerLabel.setFont(listenerLabel.getFont().deriveFont(Font.PLAIN, 10f));
            listenerLabel.setForeground(new Color(100, 150, 200));
            rightPanel.add(listenerLabel);
        }

        if (totalRecords > 0) {
            JLabel recordLabel = new JLabel(formatNumber(totalRecords));
            recordLabel.setFont(recordLabel.getFont().deriveFont(Font.PLAIN, 10f));
            recordLabel.setForeground(Color.GRAY);
            rightPanel.add(recordLabel);
        }

        JLabel stateLabel = new JLabel(getStateText(overall));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 10f));
        stateLabel.setForeground(getStateColor(overall));
        rightPanel.add(stateLabel);

        header.add(rightPanel, BorderLayout.EAST);
        sidebarContent.add(header);
    }

    private void addPageRow(DashboardPageInfo page) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel(page.displayName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
        row.add(nameLabel, BorderLayout.WEST);

        // Progress bar when loading/updating
        if (page.state() == DashboardPageInfo.State.LOADING || page.state() == DashboardPageInfo.State.UPDATING) {
            JProgressBar progress = new JProgressBar(0, 100);
            if (page.loadProgress() > 0 && page.loadProgress() < 100) {
                progress.setIndeterminate(false);
                progress.setValue(page.loadProgress());
                progress.setString(page.loadProgress() + "%");
                progress.setStringPainted(true);
            } else {
                progress.setIndeterminate(true);
                progress.setStringPainted(false);
            }
            progress.setPreferredSize(new Dimension(80, 14));
            row.add(progress, BorderLayout.CENTER);
        }

        // Right side: listener count, record count, live badge, status dot
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);

        if (page.listenerCount() > 0) {
            JLabel listenerLabel = new JLabel(String.valueOf(page.listenerCount()));
            listenerLabel.setFont(listenerLabel.getFont().deriveFont(Font.PLAIN, 9f));
            listenerLabel.setForeground(new Color(100, 150, 200));
            listenerLabel.setToolTipText(page.listenerCount() + " listener" + (page.listenerCount() != 1 ? "s" : ""));
            rightPanel.add(listenerLabel);
        }

        if (page.recordCount() > 0) {
            JLabel recordLabel = new JLabel(formatNumber(page.recordCount()));
            recordLabel.setFont(recordLabel.getFont().deriveFont(Font.PLAIN, 9f));
            recordLabel.setForeground(Color.GRAY);
            rightPanel.add(recordLabel);
        }

        if (page.liveEnabled()) {
            JLabel liveLabel = new JLabel("LIVE");
            liveLabel.setFont(liveLabel.getFont().deriveFont(Font.BOLD, 8f));
            liveLabel.setForeground(new Color(60, 180, 60));
            rightPanel.add(liveLabel);
        }

        JLabel statusDot = new JLabel(getStateDot(page.state()));
        statusDot.setForeground(getStateColor(page.state()));
        rightPanel.add(statusDot);

        row.add(rightPanel, BorderLayout.EAST);

        // Selection state
        boolean isSelected = page.key().equals(selectedItemKey);
        row.setOpaque(isSelected);
        if (isSelected) row.setBackground(COLOR_SELECTED_BG);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectedItemKey = page.key();
                pageDetailPanel.showPage(page, getPageLog(page.key()));
                cardLayout.show(contentCards, CARD_PAGE_DETAIL);
                rebuildSidebar();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!page.key().equals(selectedItemKey)) {
                    row.setBackground(COLOR_HOVER_BG);
                    row.setOpaque(true);
                    row.repaint();
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!page.key().equals(selectedItemKey)) {
                    row.setOpaque(false);
                    row.repaint();
                }
            }
        });

        sidebarContent.add(row);
    }

    // ========== Public API ==========

    /** Show a specific content card by name. */
    protected void showCard(String cardName) {
        cardLayout.show(contentCards, cardName);
    }

    /** Add a content card dynamically (e.g., for page detail panels). */
    protected void addContentCard(String name, JComponent panel) {
        contentCards.add(panel, name);
    }

    /** Get the currently selected item key (section id or page key). */
    public String getSelectedItemKey() {
        return selectedItemKey;
    }

    /** Programmatically select a page by key. */
    public void selectPage(String pageKey) {
        this.selectedItemKey = pageKey;
        rebuildSidebar();
    }

    /** Programmatically select a section by id. */
    public void selectSection(String sectionId) {
        this.selectedItemKey = sectionId;
        cardLayout.show(contentCards, sectionId);
        rebuildSidebar();
    }

    // ========== State helpers ==========

    private static DashboardPageInfo.State getOverallState(List<DashboardPageInfo> pages) {
        boolean anyLoading = false, anyError = false, anyUpdating = false, allReady = true;
        for (DashboardPageInfo p : pages) {
            switch (p.state()) {
                case LOADING -> { anyLoading = true; allReady = false; }
                case UPDATING -> anyUpdating = true;
                case ERROR -> { anyError = true; allReady = false; }
                case EMPTY -> allReady = false;
                case READY -> {}
            }
        }
        if (anyLoading) return DashboardPageInfo.State.LOADING;
        if (anyError) return DashboardPageInfo.State.ERROR;
        if (anyUpdating) return DashboardPageInfo.State.UPDATING;
        if (allReady) return DashboardPageInfo.State.READY;
        return DashboardPageInfo.State.EMPTY;
    }

    private static Color getStateColor(DashboardPageInfo.State state) {
        return switch (state) {
            case EMPTY -> COLOR_EMPTY;
            case LOADING -> COLOR_LOADING;
            case READY -> COLOR_READY;
            case UPDATING -> COLOR_UPDATING;
            case ERROR -> COLOR_ERROR;
        };
    }

    private static String getStateText(DashboardPageInfo.State state) {
        return switch (state) {
            case EMPTY -> "Empty";
            case LOADING -> "Loading...";
            case READY -> "Ready";
            case UPDATING -> "Updating...";
            case ERROR -> "Error";
        };
    }

    private static String getStateDot(DashboardPageInfo.State state) {
        return switch (state) {
            case EMPTY -> "\u25CB";   // empty circle
            case LOADING -> "\u25CE"; // bullseye
            case READY -> "\u25CF";   // filled circle
            case UPDATING -> "\u25D4"; // half circle
            case ERROR -> "\u2717";   // x mark
        };
    }

    private static Color getSectionColor(DashboardSection.StatusColor color) {
        return switch (color) {
            case OK -> COLOR_READY;
            case WARNING -> COLOR_UPDATING;
            case ERROR -> COLOR_ERROR;
            case IDLE -> COLOR_EMPTY;
            case NONE -> Color.GRAY;
        };
    }

    // ========== Timer ==========

    private void startRefreshTimer(int ms) {
        refreshTimer = new javax.swing.Timer(ms, e -> {
            rebuildSidebar();
            refreshDetailIfPageSelected();
            onRefresh();
        });
        refreshTimer.start();
    }

    /** Refresh the detail panel if a page is currently selected. */
    private void refreshDetailIfPageSelected() {
        if (selectedItemKey == null || pageDetailPanel == null) return;
        // Check if a page (not section) is selected
        for (DashboardSection s : cachedSections) {
            if (s.id().equals(selectedItemKey)) return;
        }
        // Find the page in last collected pages and refresh detail
        for (DashboardPageInfo page : lastCollectedPages) {
            if (page.key().equals(selectedItemKey)) {
                pageDetailPanel.showPage(page, getPageLog(page.key()));
                return;
            }
        }
    }

    private void cleanup() {
        if (refreshTimer != null) refreshTimer.stop();
        onCleanup();
    }

    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }

    // ========== UI Helpers ==========

    public static JPanel createSectionHeader(String title) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        header.add(label, BorderLayout.WEST);
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(80, 80, 80));
        header.add(sep, BorderLayout.CENTER);
        return header;
    }

    public static JLabel createBoldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    public static JLabel createValueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    public static String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    // ========== Singleton helper ==========

    public static <T extends DashboardWindow> T showOrCreate(AtomicReference<T> ref, Supplier<T> factory) {
        T instance = ref.get();
        if (instance == null || !instance.isDisplayable()) {
            instance = factory.get();
            ref.set(instance);
            instance.setVisible(true);
        } else {
            instance.toFront();
            instance.requestFocus();
        }
        return instance;
    }
}
