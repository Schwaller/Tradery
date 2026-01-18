package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.data.DataType;
import com.tradery.data.PageState;
import com.tradery.data.page.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Always-on-top window showing data loading status from all PageManagers.
 * Displays active pages with their state, consumer count, and data info.
 */
public class DataLoadingStatusWindow extends JDialog {

    private final JPanel contentPanel;
    private Timer refreshTimer;

    // Colors for status indicators
    private static final Color COLOR_EMPTY = new Color(100, 100, 100);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_UPDATING = new Color(140, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);

    public DataLoadingStatusWindow(Window owner) {
        super(owner, "Data Loading Status", ModalityType.MODELESS);

        setAlwaysOnTop(true);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        setResizable(true);

        // Content panel with vertical box layout
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        scrollPane.setBorder(null);
        add(scrollPane);

        pack();

        // Start refresh timer (250ms)
        refreshTimer = new Timer(250, e -> refresh());
        refreshTimer.start();
    }

    /**
     * Refresh the display based on current PageManager state.
     */
    public void refresh() {
        contentPanel.removeAll();

        ApplicationContext ctx = ApplicationContext.getInstance();

        // Add panels for each page manager
        addPageManagerPanel("Candles", DataType.CANDLES, ctx.getCandlePageManager().getActivePages());
        addPageManagerPanel("Funding", DataType.FUNDING, ctx.getFundingPageManager().getActivePages());
        addPageManagerPanel("Open Interest", DataType.OPEN_INTEREST, ctx.getOIPageManager().getActivePages());
        addPageManagerPanel("AggTrades", DataType.AGG_TRADES, ctx.getAggTradesPageManager().getActivePages());
        addPageManagerPanel("Premium Index", DataType.PREMIUM_INDEX, ctx.getPremiumPageManager().getActivePages());

        // Show "no active pages" if nothing is tracked
        if (contentPanel.getComponentCount() == 0) {
            JLabel emptyLabel = new JLabel("No active data pages");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            contentPanel.add(Box.createVerticalGlue());
            contentPanel.add(emptyLabel);
            contentPanel.add(Box.createVerticalGlue());
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void addPageManagerPanel(String name, DataType dataType, List<DataPageManager.PageInfo> pages) {
        if (pages.isEmpty()) return;

        PageManagerPanel panel = new PageManagerPanel(name, dataType, pages);
        contentPanel.add(panel);
        contentPanel.add(Box.createVerticalStrut(8));
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    /**
     * Panel displaying status for a single PageManager and its active pages.
     */
    private class PageManagerPanel extends JPanel {
        private final String managerName;
        private final DataType dataType;

        PageManagerPanel(String name, DataType dataType, List<DataPageManager.PageInfo> pages) {
            this.managerName = name;
            this.dataType = dataType;

            setLayout(new BorderLayout(0, 4));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                new EmptyBorder(8, 8, 8, 8)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

            // Header with manager name, icon, and summary
            JPanel headerPanel = createHeaderPanel(pages);
            add(headerPanel, BorderLayout.NORTH);

            // Pages list
            if (pages.size() > 1 || (pages.size() == 1 && shouldShowDetails(pages.get(0)))) {
                JPanel pagesPanel = createPagesPanel(pages);
                add(pagesPanel, BorderLayout.CENTER);
            }
        }

        private JPanel createHeaderPanel(List<DataPageManager.PageInfo> pages) {
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);

            // Left: Icon and name
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            leftPanel.setOpaque(false);

            JLabel iconLabel = new JLabel(getIconForDataType(dataType));
            iconLabel.setFont(iconLabel.getFont().deriveFont(16f));
            leftPanel.add(iconLabel);

            JLabel nameLabel = new JLabel(managerName);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
            leftPanel.add(nameLabel);

            // Count badge
            JLabel countLabel = new JLabel(" (" + pages.size() + " page" + (pages.size() != 1 ? "s" : "") + ")");
            countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 10f));
            countLabel.setForeground(Color.GRAY);
            leftPanel.add(countLabel);

            header.add(leftPanel, BorderLayout.WEST);

            // Right: Overall status
            PageState overallState = getOverallState(pages);
            int totalConsumers = pages.stream().mapToInt(DataPageManager.PageInfo::listenerCount).sum();
            int totalRecords = pages.stream().mapToInt(DataPageManager.PageInfo::recordCount).sum();

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightPanel.setOpaque(false);

            // Consumer count
            if (totalConsumers > 0) {
                JLabel consumerLabel = new JLabel(totalConsumers + " consumer" + (totalConsumers != 1 ? "s" : ""));
                consumerLabel.setFont(consumerLabel.getFont().deriveFont(Font.PLAIN, 10f));
                consumerLabel.setForeground(new Color(100, 150, 200));
                rightPanel.add(consumerLabel);
            }

            // Record count
            if (totalRecords > 0) {
                JLabel recordLabel = new JLabel(formatNumber(totalRecords) + " records");
                recordLabel.setFont(recordLabel.getFont().deriveFont(Font.PLAIN, 10f));
                recordLabel.setForeground(Color.GRAY);
                rightPanel.add(recordLabel);
            }

            // Status indicator
            JLabel statusLabel = new JLabel(getStatusText(overallState));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 10f));
            statusLabel.setForeground(getColorForState(overallState));
            rightPanel.add(statusLabel);

            header.add(rightPanel, BorderLayout.EAST);

            return header;
        }

        private JPanel createPagesPanel(List<DataPageManager.PageInfo> pages) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 16, 0, 0));

            for (DataPageManager.PageInfo page : pages) {
                panel.add(createPageRow(page));
                panel.add(Box.createVerticalStrut(2));
            }

            return panel;
        }

        private JPanel createPageRow(DataPageManager.PageInfo page) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            // Left: Symbol/timeframe
            String label = page.symbol();
            if (page.timeframe() != null) {
                label += "/" + page.timeframe();
            }
            JLabel nameLabel = new JLabel(label);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
            row.add(nameLabel, BorderLayout.WEST);

            // Center: Progress bar (only when loading)
            if (page.state() == PageState.LOADING || page.state() == PageState.UPDATING) {
                JProgressBar progress = new JProgressBar();
                progress.setIndeterminate(true);
                progress.setPreferredSize(new Dimension(60, 12));
                progress.setStringPainted(false);
                row.add(progress, BorderLayout.CENTER);
            }

            // Right: Status and counts
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightPanel.setOpaque(false);

            if (page.listenerCount() > 0) {
                JLabel listenerLabel = new JLabel(page.listenerCount() + " " + (page.listenerCount() == 1 ? "listener" : "listeners"));
                listenerLabel.setFont(listenerLabel.getFont().deriveFont(Font.PLAIN, 9f));
                listenerLabel.setForeground(new Color(100, 150, 200));
                rightPanel.add(listenerLabel);
            }

            if (page.recordCount() > 0) {
                JLabel recordLabel = new JLabel(formatNumber(page.recordCount()));
                recordLabel.setFont(recordLabel.getFont().deriveFont(Font.PLAIN, 9f));
                recordLabel.setForeground(Color.GRAY);
                rightPanel.add(recordLabel);
            }

            // Status dot
            JLabel statusDot = new JLabel(getStatusDot(page.state()));
            statusDot.setForeground(getColorForState(page.state()));
            rightPanel.add(statusDot);

            row.add(rightPanel, BorderLayout.EAST);

            return row;
        }

        private boolean shouldShowDetails(DataPageManager.PageInfo page) {
            return page.state() == PageState.LOADING || page.state() == PageState.UPDATING ||
                   page.state() == PageState.ERROR || page.listenerCount() > 1;
        }

        private PageState getOverallState(List<DataPageManager.PageInfo> pages) {
            boolean anyLoading = false;
            boolean anyError = false;
            boolean anyUpdating = false;
            boolean allReady = true;

            for (DataPageManager.PageInfo page : pages) {
                switch (page.state()) {
                    case LOADING -> { anyLoading = true; allReady = false; }
                    case UPDATING -> { anyUpdating = true; }
                    case ERROR -> { anyError = true; allReady = false; }
                    case EMPTY -> allReady = false;
                    case READY -> {}
                }
            }

            if (anyLoading) return PageState.LOADING;
            if (anyError) return PageState.ERROR;
            if (anyUpdating) return PageState.UPDATING;
            if (allReady) return PageState.READY;
            return PageState.EMPTY;
        }
    }

    // ========== Utility Methods ==========

    private static String getIconForDataType(DataType type) {
        return switch (type) {
            case CANDLES -> "\uD83D\uDCC8"; // chart
            case FUNDING -> "\uD83D\uDCB0"; // money bag
            case OPEN_INTEREST -> "\uD83D\uDCCA"; // bar chart
            case AGG_TRADES -> "\uD83D\uDD04"; // arrows
            case PREMIUM_INDEX -> "\u2696\uFE0F"; // scales
        };
    }

    private static Color getColorForState(PageState state) {
        return switch (state) {
            case EMPTY -> COLOR_EMPTY;
            case LOADING -> COLOR_LOADING;
            case READY -> COLOR_READY;
            case UPDATING -> COLOR_UPDATING;
            case ERROR -> COLOR_ERROR;
        };
    }

    private static String getStatusText(PageState state) {
        return switch (state) {
            case EMPTY -> "Empty";
            case LOADING -> "Loading...";
            case READY -> "Ready";
            case UPDATING -> "Updating...";
            case ERROR -> "Error";
        };
    }

    private static String getStatusDot(PageState state) {
        return switch (state) {
            case EMPTY -> "\u25CB"; // empty circle
            case LOADING -> "\u25CE"; // bullseye
            case READY -> "\u25CF"; // filled circle
            case UPDATING -> "\u25D4"; // half circle
            case ERROR -> "\u2717"; // x mark
        };
    }

    private static String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    /**
     * Show the window positioned relative to the given component.
     */
    public void showNear(Component component) {
        if (component != null) {
            Point location = component.getLocationOnScreen();
            setLocation(
                Math.max(0, location.x - getWidth() + component.getWidth()),
                Math.max(0, location.y - getHeight() - 5)
            );
        }
        setVisible(true);
        refresh();
    }
}
