package com.tradery.forge.ui;

import com.tradery.forge.data.DataType;
import com.tradery.forge.data.PageState;
import com.tradery.forge.data.log.DownloadEvent;
import com.tradery.forge.data.log.DownloadLogStore;
import com.tradery.forge.data.page.DataPageManager;
import com.tradery.forge.data.page.IndicatorPageManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel showing detailed information about a selected data page.
 */
public class PageDetailPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private String selectedPageKey;
    private DataType selectedDataType;
    private String logPageKey; // actual key used for log lookups (may differ for indicators)

    // Info labels
    private JLabel pageKeyLabel;
    private JLabel dataTypeLabel;
    private JLabel stateLabel;
    private JLabel recordCountLabel;
    private JLabel lastSyncLabel;
    private JLabel errorLabel;

    // Progress bar
    private JProgressBar progressBar;

    // Consumers list
    private DefaultListModel<String> consumersModel;
    private JList<String> consumersList;

    // Page log
    private DefaultListModel<String> logModel;
    private JList<String> logList;

    public PageDetailPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // Top: section header + info panel
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.setOpaque(false);
        topPanel.add(createSectionHeader("Page Information"), BorderLayout.NORTH);
        topPanel.add(createInfoPanel(), BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Split pane for consumers and log
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(createConsumersSection());
        splitPane.setBottomComponent(createLogSection());
        splitPane.setDividerLocation(120);
        splitPane.setResizeWeight(0.3);

        add(splitPane, BorderLayout.CENTER);

        // Initial state
        showEmptyState();
    }

    private JPanel createSectionHeader(String title) {
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

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(2, 4, 2, 8);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.anchor = GridBagConstraints.WEST;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.weightx = 1.0;
        valueConstraints.insets = new Insets(2, 0, 2, 4);
        valueConstraints.gridwidth = GridBagConstraints.REMAINDER;

        int row = 0;

        // Page Key
        labelConstraints.gridy = row;
        panel.add(createLabel("Page Key:"), labelConstraints);
        valueConstraints.gridy = row++;
        pageKeyLabel = createValueLabel();
        panel.add(pageKeyLabel, valueConstraints);

        // Data Type
        labelConstraints.gridy = row;
        panel.add(createLabel("Data Type:"), labelConstraints);
        valueConstraints.gridy = row++;
        dataTypeLabel = createValueLabel();
        panel.add(dataTypeLabel, valueConstraints);

        // State
        labelConstraints.gridy = row;
        panel.add(createLabel("State:"), labelConstraints);
        valueConstraints.gridy = row++;
        stateLabel = createValueLabel();
        panel.add(stateLabel, valueConstraints);

        // Record Count
        labelConstraints.gridy = row;
        panel.add(createLabel("Records:"), labelConstraints);
        valueConstraints.gridy = row++;
        recordCountLabel = createValueLabel();
        panel.add(recordCountLabel, valueConstraints);

        // Last Sync
        labelConstraints.gridy = row;
        panel.add(createLabel("Last Sync:"), labelConstraints);
        valueConstraints.gridy = row++;
        lastSyncLabel = createValueLabel();
        panel.add(lastSyncLabel, valueConstraints);

        // Error (only shown when there's an error)
        labelConstraints.gridy = row;
        panel.add(createLabel("Error:"), labelConstraints);
        valueConstraints.gridy = row++;
        errorLabel = createValueLabel();
        errorLabel.setForeground(new Color(180, 80, 80));
        panel.add(errorLabel, valueConstraints);

        // Progress bar
        labelConstraints.gridy = row;
        valueConstraints.gridy = row++;
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        valueConstraints.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(progressBar, valueConstraints);

        return panel;
    }

    private JPanel createConsumersSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        panel.add(createSectionHeader("Consumers"), BorderLayout.NORTH);

        consumersModel = new DefaultListModel<>();
        consumersList = new JList<>(consumersModel);
        consumersList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JScrollPane scrollPane = new JScrollPane(consumersList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        panel.add(createSectionHeader("Recent Events"), BorderLayout.NORTH);

        logModel = new DefaultListModel<>();
        logList = new JList<>(logModel);
        logList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logList.setCellRenderer(new LogCellRenderer());

        JScrollPane scrollPane = new JScrollPane(logList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private JLabel createValueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        return label;
    }

    /**
     * Set the selected page to display.
     */
    public void setSelectedPage(String pageKey, DataType dataType) {
        this.selectedPageKey = pageKey;
        this.selectedDataType = dataType;

        if (pageKey == null) {
            showEmptyState();
        }
    }

    /**
     * Refresh the display with current page data.
     */
    public void refresh(List<DataPageManager.PageInfo> allPages) {
        refresh(allPages, List.of());
    }

    /**
     * Refresh the display with current page and indicator data.
     */
    public void refresh(List<DataPageManager.PageInfo> allPages,
                        List<IndicatorPageManager.IndicatorPageInfo> indicatorPages) {
        if (selectedPageKey == null) {
            showEmptyState();
            return;
        }

        // Check indicator pages first (keys start with "indicator:")
        if (selectedPageKey.startsWith("indicator:")) {
            for (IndicatorPageManager.IndicatorPageInfo ind : indicatorPages) {
                String indKey = "indicator:" + ind.type() + "(" + ind.params() + ")";
                if (indKey.equals(selectedPageKey)) {
                    refreshIndicator(ind);
                    return;
                }
            }
            showEmptyState();
            return;
        }

        // Find the selected page
        DataPageManager.PageInfo selectedPage = null;
        for (DataPageManager.PageInfo page : allPages) {
            if (page.key().equals(selectedPageKey)) {
                selectedPage = page;
                break;
            }
        }

        if (selectedPage == null) {
            showEmptyState();
            return;
        }

        // Update info labels
        logPageKey = selectedPage.key();
        pageKeyLabel.setText(selectedPage.key());
        dataTypeLabel.setText(selectedPage.dataType().getDisplayName());
        stateLabel.setText(selectedPage.state().name());
        stateLabel.setForeground(getColorForState(selectedPage.state()));
        recordCountLabel.setText(formatNumber(selectedPage.recordCount()));
        lastSyncLabel.setText("-");  // Not available in PageInfo

        // Show/hide progress bar with percentage
        boolean isLoading = selectedPage.state() == PageState.LOADING ||
                            selectedPage.state() == PageState.UPDATING;
        progressBar.setVisible(isLoading);
        if (isLoading) {
            int progress = selectedPage.loadProgress();
            if (progress > 0 && progress < 100) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(progress);
                progressBar.setString(progress + "%");
                progressBar.setStringPainted(true);
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setStringPainted(false);
            }
        }

        // Error message (would need to be in PageInfo)
        errorLabel.setVisible(selectedPage.state() == PageState.ERROR);
        if (selectedPage.state() == PageState.ERROR) {
            errorLabel.setText("Check log for details");
        }

        // Update consumers list
        consumersModel.clear();
        if (selectedPage.consumers() != null) {
            for (String consumer : selectedPage.consumers()) {
                consumersModel.addElement(consumer);
            }
        }
        if (consumersModel.isEmpty()) {
            consumersModel.addElement("(no consumers)");
        }

        // Update log
        updateLog();
    }

    private void updateLog() {
        logModel.clear();

        String key = logPageKey != null ? logPageKey : selectedPageKey;
        if (key == null) return;

        List<DownloadEvent> events = DownloadLogStore.getInstance().getPageLog(key);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

        int maxEvents = 20;
        int count = 0;
        for (DownloadEvent event : events) {
            if (count++ >= maxEvents) break;

            String time = formatter.format(Instant.ofEpochMilli(event.timestamp()));
            String eventName = formatEventType(event.eventType());
            String entry = String.format("[%s] %s - %s", time, eventName, event.message());
            logModel.addElement(entry);
        }

        if (logModel.isEmpty()) {
            logModel.addElement("(no events)");
        }
    }

    private String formatEventType(DownloadEvent.EventType type) {
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
        };
    }

    private void refreshIndicator(IndicatorPageManager.IndicatorPageInfo ind) {
        logPageKey = ind.key();
        pageKeyLabel.setText(ind.key());
        dataTypeLabel.setText("Indicator: " + ind.type() + "(" + ind.params() + ")");
        stateLabel.setText(ind.state().name());
        stateLabel.setForeground(getColorForState(ind.state()));
        recordCountLabel.setText(ind.hasData() ? "Yes" : "No");
        lastSyncLabel.setText(ind.symbol() + "/" + ind.timeframe());

        boolean isLoading = ind.state() == PageState.LOADING;
        progressBar.setVisible(isLoading);
        if (isLoading) {
            int progress = ind.loadProgress();
            if (progress > 0 && progress < 100) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(progress);
                progressBar.setString(progress + "%");
                progressBar.setStringPainted(true);
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setStringPainted(false);
            }
        }

        errorLabel.setVisible(ind.state() == PageState.ERROR);
        if (ind.state() == PageState.ERROR) {
            errorLabel.setText("Check log for details");
        }

        consumersModel.clear();
        if (ind.consumers() != null) {
            for (String consumer : ind.consumers()) {
                consumersModel.addElement(consumer);
            }
        }
        if (consumersModel.isEmpty()) {
            consumersModel.addElement("(no consumers)");
        }

        updateLog();
    }

    private void showEmptyState() {
        logPageKey = null;
        pageKeyLabel.setText("-");
        dataTypeLabel.setText("-");
        stateLabel.setText("-");
        stateLabel.setForeground(Color.GRAY);
        recordCountLabel.setText("-");
        lastSyncLabel.setText("-");
        errorLabel.setText("-");
        errorLabel.setVisible(false);
        progressBar.setVisible(false);

        consumersModel.clear();
        consumersModel.addElement("Select a page from the tree");

        logModel.clear();
        logModel.addElement("Select a page to view events");
    }

    private Color getColorForState(PageState state) {
        return switch (state) {
            case EMPTY -> new Color(100, 100, 100);
            case LOADING -> new Color(100, 100, 180);
            case READY -> new Color(60, 140, 60);
            case UPDATING -> new Color(140, 140, 60);
            case ERROR -> new Color(180, 80, 80);
        };
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%,.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%,.1fK", n / 1_000.0);
        return String.format("%,d", n);
    }

    // ========== Log Cell Renderer ==========

    private class LogCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String text = value.toString();

            if (!isSelected) {
                if (text.contains("Error")) {
                    setForeground(new Color(180, 80, 80));
                } else if (text.contains("Loading complete") || text.contains("Update complete")) {
                    setForeground(new Color(60, 140, 60));
                } else if (text.contains("Start loading") || text.contains("Start update")) {
                    setForeground(new Color(100, 100, 180));
                } else {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }
}
