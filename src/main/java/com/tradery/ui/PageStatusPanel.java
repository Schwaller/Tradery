package com.tradery.ui;

import com.tradery.data.DataType;
import com.tradery.data.PageState;
import com.tradery.data.page.DataPageManager;

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
        contentPanel.removeAll();
        pageRows.clear();

        // Add Overview row at the top
        addOverviewRow();
        contentPanel.add(Box.createVerticalStrut(8));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        contentPanel.add(sep);
        contentPanel.add(Box.createVerticalStrut(8));

        // Group pages by data type
        for (DataType dataType : DataType.values()) {
            List<DataPageManager.PageInfo> typePages = pages.stream()
                .filter(p -> p.dataType() == dataType)
                .toList();

            if (!typePages.isEmpty()) {
                addDataTypeSection(dataType, typePages);
            }
        }

        // Show "no active pages" if nothing is tracked (after overview)
        if (pages.isEmpty()) {
            JLabel emptyLabel = new JLabel("No active data pages");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
            contentPanel.add(emptyLabel);
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void addOverviewRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel("\uD83D\uDCCA"); // chart icon
        iconLabel.setFont(iconLabel.getFont().deriveFont(14f));
        row.add(iconLabel, BorderLayout.WEST);

        JLabel nameLabel = new JLabel("Overview");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        row.add(nameLabel, BorderLayout.CENTER);

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
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(new EmptyBorder(4, 0, 4, 0));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Header
        JPanel headerPanel = createHeaderPanel(dataType, pages);
        section.add(headerPanel);

        // Pages list
        if (!pages.isEmpty()) {
            section.add(Box.createVerticalStrut(4));
            for (DataPageManager.PageInfo page : pages) {
                PageRowPanel row = createPageRow(page, dataType);
                pageRows.add(row);
                section.add(row);
                section.add(Box.createVerticalStrut(2));
            }
        }

        contentPanel.add(section);
        contentPanel.add(Box.createVerticalStrut(8));
    }

    private JPanel createHeaderPanel(DataType dataType, List<DataPageManager.PageInfo> pages) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        // Left: Icon and name
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(getIconForDataType(dataType));
        iconLabel.setFont(iconLabel.getFont().deriveFont(14f));
        leftPanel.add(iconLabel);

        JLabel nameLabel = new JLabel(dataType.getDisplayName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        leftPanel.add(nameLabel);

        // Count badge
        JLabel countLabel = new JLabel(" (" + pages.size() + ")");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 10f));
        countLabel.setForeground(Color.GRAY);
        leftPanel.add(countLabel);

        header.add(leftPanel, BorderLayout.WEST);

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
}
