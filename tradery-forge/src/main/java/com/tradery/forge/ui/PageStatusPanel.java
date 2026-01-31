package com.tradery.forge.ui;

import com.tradery.forge.data.DataType;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Compact panel showing page status grouped by data type.
 * Supports selection to show details in the detail panel.
 */
public class PageStatusPanel extends JPanel {

    // Colors for status indicators
    private static final Color COLOR_EMPTY = new Color(100, 100, 100);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_UPDATING = new Color(140, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_SELECTED_BG = new Color(50, 80, 120);
    private static final Color COLOR_HOVER_BG = new Color(60, 60, 60);

    private final BiConsumer<String, DataType> onPageSelected;
    private final JPanel contentPanel;

    // Currently selected page
    private String selectedPageKey;
    private DataType selectedDataType;

    // For tracking row components for hover/selection
    private final List<PageRowPanel> pageRows = new ArrayList<>();
    private final List<IndicatorRowPanel> indicatorRows = new ArrayList<>();

    public PageStatusPanel(BiConsumer<String, DataType> onPageSelected) {
        this.onPageSelected = onPageSelected;

        setLayout(new BorderLayout());
        // Vertical line separator on the right edge
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(80, 80, 80)));

        // Content panel with vertical box layout
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    // Special constant for "Overview" selection
    public static final String OVERVIEW_KEY = "__OVERVIEW__";

    // Track if overview is selected
    private boolean overviewSelected = true;  // Default to overview

    /**
     * Update the panel with current page data.
     */
    public void update(List<DataPageManager.PageInfo> pages) {
        update(pages, new ArrayList<>());
    }

    /**
     * Update the panel with current page and indicator data.
     */
    public void update(List<DataPageManager.PageInfo> pages,
                       List<IndicatorPageManager.IndicatorPageInfo> indicatorPages) {
        contentPanel.removeAll();
        pageRows.clear();
        indicatorRows.clear();

        // Add Overview row at the top
        addOverviewRow();
        contentPanel.add(Box.createVerticalStrut(8));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(sep);
        contentPanel.add(Box.createVerticalStrut(8));

        // Group data pages by data type
        for (DataType dataType : DataType.values()) {
            List<DataPageManager.PageInfo> typePages = pages.stream()
                .filter(p -> p.dataType() == dataType)
                .toList();

            if (!typePages.isEmpty()) {
                addDataTypeSection(dataType, typePages);
            }
        }

        // Add indicators section if any
        if (!indicatorPages.isEmpty()) {
            addIndicatorsSection(indicatorPages);
        }

        // Show "no active pages" if nothing is tracked
        if (pages.isEmpty() && indicatorPages.isEmpty()) {
            JLabel emptyLabel = new JLabel("No active pages");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(emptyLabel);
        }

        // Add vertical glue to push content to top
        contentPanel.add(Box.createVerticalGlue());

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void addOverviewRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel nameLabel = new JLabel("Overview");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        row.add(nameLabel, BorderLayout.WEST);

        // Update background based on selection
        if (overviewSelected) {
            row.setBackground(COLOR_SELECTED_BG);
            row.setOpaque(true);
        } else {
            row.setOpaque(false);
        }

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectOverview();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!overviewSelected) {
                    row.setBackground(COLOR_HOVER_BG);
                    row.setOpaque(true);
                    row.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!overviewSelected) {
                    row.setOpaque(false);
                    row.repaint();
                }
            }
        });

        contentPanel.add(row);
    }

    private void selectOverview() {
        overviewSelected = true;
        selectedPageKey = null;
        selectedDataType = null;
        onPageSelected.accept(OVERVIEW_KEY, null);
    }

    private void addDataTypeSection(DataType dataType, List<DataPageManager.PageInfo> pages) {
        // Header - add directly to content panel
        JPanel headerPanel = createHeaderPanel(dataType, pages);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(headerPanel);

        // Pages list
        if (!pages.isEmpty()) {
            contentPanel.add(Box.createVerticalStrut(4));
            for (DataPageManager.PageInfo page : pages) {
                PageRowPanel row = createPageRow(page, dataType);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                pageRows.add(row);
                contentPanel.add(row);
                contentPanel.add(Box.createVerticalStrut(2));
            }
        }

        contentPanel.add(Box.createVerticalStrut(12));
    }

    private void addIndicatorsSection(List<IndicatorPageManager.IndicatorPageInfo> indicators) {
        // Header
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        String text = "Indicators (" + indicators.size() + ")";
        JLabel nameLabel = new JLabel(text);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        header.add(nameLabel, BorderLayout.WEST);

        // Right: overall status
        PageState overallState = getOverallIndicatorState(indicators);
        int totalConsumers = indicators.stream().mapToInt(IndicatorPageManager.IndicatorPageInfo::listenerCount).sum();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        if (totalConsumers > 0) {
            JLabel consumerLabel = new JLabel(totalConsumers + " listener" + (totalConsumers != 1 ? "s" : ""));
            consumerLabel.setFont(consumerLabel.getFont().deriveFont(Font.PLAIN, 10f));
            consumerLabel.setForeground(new Color(100, 150, 200));
            rightPanel.add(consumerLabel);
        }

        JLabel statusLabel = new JLabel(getStatusText(overallState));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 10f));
        statusLabel.setForeground(getColorForState(overallState));
        rightPanel.add(statusLabel);

        header.add(rightPanel, BorderLayout.EAST);
        contentPanel.add(header);

        // Indicator rows
        contentPanel.add(Box.createVerticalStrut(4));
        for (IndicatorPageManager.IndicatorPageInfo ind : indicators) {
            IndicatorRowPanel row = new IndicatorRowPanel(ind);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            indicatorRows.add(row);
            contentPanel.add(row);
            contentPanel.add(Box.createVerticalStrut(2));
        }

        contentPanel.add(Box.createVerticalStrut(12));
    }

    /**
     * Select the first page row matching the given data type.
     */
    public void selectFirstByDataType(DataType dataType) {
        for (PageRowPanel row : pageRows) {
            if (row.dataType == dataType) {
                selectPage(row.page.key(), row.dataType);
                return;
            }
        }
    }

    /**
     * Select the first indicator row.
     */
    public void selectFirstIndicator() {
        if (!indicatorRows.isEmpty()) {
            IndicatorRowPanel row = indicatorRows.get(0);
            selectIndicator(row.indicatorKey);
        }
    }

    private void selectIndicator(String indicatorKey) {
        this.overviewSelected = false;
        this.selectedPageKey = indicatorKey;
        this.selectedDataType = null;
        updateRowSelection();
        updateIndicatorRowSelection();
        onPageSelected.accept(indicatorKey, null);
    }

    private void updateIndicatorRowSelection() {
        for (IndicatorRowPanel row : indicatorRows) {
            row.updateSelection();
        }
    }

    private PageState getOverallIndicatorState(List<IndicatorPageManager.IndicatorPageInfo> indicators) {
        boolean anyLoading = false;
        boolean anyError = false;
        boolean allReady = true;

        for (IndicatorPageManager.IndicatorPageInfo ind : indicators) {
            switch (ind.state()) {
                case LOADING -> { anyLoading = true; allReady = false; }
                case UPDATING -> { }
                case ERROR -> { anyError = true; allReady = false; }
                case EMPTY -> allReady = false;
                case READY -> {}
            }
        }

        if (anyLoading) return PageState.LOADING;
        if (anyError) return PageState.ERROR;
        if (allReady) return PageState.READY;
        return PageState.EMPTY;
    }

    private JPanel createHeaderPanel(DataType dataType, List<DataPageManager.PageInfo> pages) {
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Left: name and count
        String text = dataType.getDisplayName() + " (" + pages.size() + ")";
        JLabel nameLabel = new JLabel(text);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));

        header.add(nameLabel, BorderLayout.WEST);

        // Right: Overall status
        PageState overallState = getOverallState(pages);
        int totalConsumers = pages.stream().mapToInt(DataPageManager.PageInfo::listenerCount).sum();
        int totalRecords = pages.stream().mapToInt(DataPageManager.PageInfo::recordCount).sum();

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setOpaque(false);

        if (totalConsumers > 0) {
            JLabel consumerLabel = new JLabel(totalConsumers + " listener" + (totalConsumers != 1 ? "s" : ""));
            consumerLabel.setFont(consumerLabel.getFont().deriveFont(Font.PLAIN, 10f));
            consumerLabel.setForeground(new Color(100, 150, 200));
            rightPanel.add(consumerLabel);
        }

        if (totalRecords > 0) {
            JLabel recordLabel = new JLabel(formatNumber(totalRecords));
            recordLabel.setFont(recordLabel.getFont().deriveFont(Font.PLAIN, 10f));
            recordLabel.setForeground(Color.GRAY);
            rightPanel.add(recordLabel);
        }

        JLabel statusLabel = new JLabel(getStatusText(overallState));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 10f));
        statusLabel.setForeground(getColorForState(overallState));
        rightPanel.add(statusLabel);

        header.add(rightPanel, BorderLayout.EAST);

        return header;
    }

    private PageRowPanel createPageRow(DataPageManager.PageInfo page, DataType dataType) {
        return new PageRowPanel(page, dataType);
    }

    /**
     * Set the selected page externally (e.g., after loading from state).
     */
    public void setSelectedPage(String pageKey, DataType dataType) {
        this.selectedPageKey = pageKey;
        this.selectedDataType = dataType;
        updateRowSelection();
    }

    private void updateRowSelection() {
        for (PageRowPanel row : pageRows) {
            row.updateSelection();
        }
    }

    private void selectPage(String pageKey, DataType dataType) {
        this.overviewSelected = false;
        this.selectedPageKey = pageKey;
        this.selectedDataType = dataType;
        updateRowSelection();
        updateIndicatorRowSelection();
        onPageSelected.accept(pageKey, dataType);
    }

    // ========== Inner class for page rows ==========

    private class PageRowPanel extends JPanel {
        private final DataPageManager.PageInfo page;
        private final DataType dataType;
        private boolean isHovered = false;

        PageRowPanel(DataPageManager.PageInfo page, DataType dataType) {
            this.page = page;
            this.dataType = dataType;

            setLayout(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Left: Symbol/timeframe
            String label = page.symbol();
            if (page.timeframe() != null) {
                label += "/" + page.timeframe();
            }
            JLabel nameLabel = new JLabel(label);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
            add(nameLabel, BorderLayout.WEST);

            // Center: Progress bar (only when loading)
            if (page.state() == PageState.LOADING || page.state() == PageState.UPDATING) {
                JProgressBar progress = new JProgressBar(0, 100);
                int loadProgress = page.loadProgress();
                if (loadProgress > 0 && loadProgress < 100) {
                    progress.setIndeterminate(false);
                    progress.setValue(loadProgress);
                    progress.setString(loadProgress + "%");
                    progress.setStringPainted(true);
                } else {
                    progress.setIndeterminate(true);
                    progress.setStringPainted(false);
                }
                progress.setPreferredSize(new Dimension(80, 14));
                add(progress, BorderLayout.CENTER);
            }

            // Right: Status and counts
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightPanel.setOpaque(false);

            if (page.listenerCount() > 0) {
                JLabel listenerLabel = new JLabel(page.listenerCount() + "");
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

            // Status dot
            JLabel statusDot = new JLabel(getStatusDot(page.state()));
            statusDot.setForeground(getColorForState(page.state()));
            rightPanel.add(statusDot);

            add(rightPanel, BorderLayout.EAST);

            // Mouse listeners for hover and selection
            // Use mousePressed instead of mouseClicked because the panel refreshes every 250ms
            // and mouseClicked requires press+release on same component
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selectPage(page.key(), dataType);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    updateBackground();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    updateBackground();
                }
            });

            updateBackground();
        }

        void updateSelection() {
            updateBackground();
        }

        private void updateBackground() {
            boolean isSelected = page.key().equals(selectedPageKey);
            if (isSelected) {
                setBackground(COLOR_SELECTED_BG);
                setOpaque(true);
            } else if (isHovered) {
                setBackground(COLOR_HOVER_BG);
                setOpaque(true);
            } else {
                setOpaque(false);
            }
            repaint();
        }
    }

    // ========== Inner class for indicator rows ==========

    private class IndicatorRowPanel extends JPanel {
        private final String indicatorKey;
        private boolean isHovered = false;

        IndicatorRowPanel(IndicatorPageManager.IndicatorPageInfo ind) {
            this.indicatorKey = "indicator:" + ind.type() + "(" + ind.params() + ")";

            setLayout(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            String label = ind.type() + "(" + ind.params() + ")";
            JLabel nameLabel = new JLabel(label);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
            add(nameLabel, BorderLayout.WEST);

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightPanel.setOpaque(false);

            if (ind.listenerCount() > 0) {
                JLabel listenerLabel = new JLabel(String.valueOf(ind.listenerCount()));
                listenerLabel.setFont(listenerLabel.getFont().deriveFont(Font.PLAIN, 9f));
                listenerLabel.setForeground(new Color(100, 150, 200));
                rightPanel.add(listenerLabel);
            }

            JLabel statusDot = new JLabel(getStatusDot(ind.state()));
            statusDot.setForeground(getColorForState(ind.state()));
            rightPanel.add(statusDot);

            add(rightPanel, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selectIndicator(indicatorKey);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    updateBackground();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    updateBackground();
                }
            });

            updateBackground();
        }

        void updateSelection() {
            updateBackground();
        }

        private void updateBackground() {
            boolean isSelected = indicatorKey.equals(selectedPageKey);
            if (isSelected) {
                setBackground(COLOR_SELECTED_BG);
                setOpaque(true);
            } else if (isHovered) {
                setBackground(COLOR_HOVER_BG);
                setOpaque(true);
            } else {
                setOpaque(false);
            }
            repaint();
        }
    }

    // ========== Utility Methods ==========

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

    private static String getIconForDataType(DataType type) {
        return "";  // No icons
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
}
