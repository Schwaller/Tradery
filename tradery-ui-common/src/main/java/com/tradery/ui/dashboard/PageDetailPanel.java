package com.tradery.ui.dashboard;

import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel showing detailed information about a selected data page.
 * Works entirely from {@link DashboardPageInfo} — no forge or desk types.
 */
public class PageDetailPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // Info labels
    private final JLabel pageKeyLabel;
    private final JLabel categoryLabel;
    private final JLabel stateLabel;
    private final JLabel recordCountLabel;
    private final JLabel listenerCountLabel;
    private final JLabel errorLabel;
    private final JProgressBar progressBar;

    // Consumers list
    private final DefaultListModel<String> consumersModel;

    // Page log
    private final DefaultListModel<String> logModel;

    public PageDetailPanel() {
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // Top: info panel
        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.setOpaque(false);
        topPanel.add(DashboardWindow.createSectionHeader("Page Information"), BorderLayout.NORTH);

        JPanel infoGrid = new JPanel(new GridBagLayout());
        infoGrid.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

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
        infoGrid.add(DashboardWindow.createBoldLabel("Page Key:"), lc);
        pageKeyLabel = DashboardWindow.createValueLabel();
        infoGrid.add(pageKeyLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(DashboardWindow.createBoldLabel("Category:"), lc);
        categoryLabel = DashboardWindow.createValueLabel();
        infoGrid.add(categoryLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(DashboardWindow.createBoldLabel("State:"), lc);
        stateLabel = DashboardWindow.createValueLabel();
        infoGrid.add(stateLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(DashboardWindow.createBoldLabel("Records:"), lc);
        recordCountLabel = DashboardWindow.createValueLabel();
        infoGrid.add(recordCountLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(DashboardWindow.createBoldLabel("Listeners:"), lc);
        listenerCountLabel = DashboardWindow.createValueLabel();
        infoGrid.add(listenerCountLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        infoGrid.add(DashboardWindow.createBoldLabel("Error:"), lc);
        errorLabel = DashboardWindow.createValueLabel();
        errorLabel.setForeground(new Color(180, 80, 80));
        infoGrid.add(errorLabel, vc);

        lc.gridy = row; vc.gridy = row++;
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        vc.gridwidth = GridBagConstraints.REMAINDER;
        infoGrid.add(progressBar, vc);

        topPanel.add(infoGrid, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Split: consumers + log
        ThinSplitPane splitPane = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Consumers
        JPanel consumersSection = new JPanel(new BorderLayout(0, 6));
        consumersSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        consumersSection.add(DashboardWindow.createSectionHeader("Consumers"), BorderLayout.NORTH);
        consumersModel = new DefaultListModel<>();
        JList<String> consumersList = new JList<>(consumersModel);
        consumersList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        BorderlessScrollPane consumersScroll = new BorderlessScrollPane(consumersList);
        consumersSection.add(consumersScroll, BorderLayout.CENTER);
        splitPane.setTopComponent(consumersSection);

        // Log
        JPanel logSection = new JPanel(new BorderLayout(0, 6));
        logSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        logSection.add(DashboardWindow.createSectionHeader("Recent Events"), BorderLayout.NORTH);
        logModel = new DefaultListModel<>();
        JList<String> logList = new JList<>(logModel);
        logList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logList.setCellRenderer(new LogCellRenderer());
        BorderlessScrollPane logScroll = new BorderlessScrollPane(logList);
        logSection.add(logScroll, BorderLayout.CENTER);
        splitPane.setBottomComponent(logSection);

        splitPane.setDividerLocation(120);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        showEmptyState();
    }

    /**
     * Update the detail panel with page info and log entries.
     */
    public void showPage(DashboardPageInfo page, List<PageLogEntry> logEntries) {
        if (page == null) {
            showEmptyState();
            return;
        }

        pageKeyLabel.setText(page.key());
        categoryLabel.setText(page.category());
        stateLabel.setText(page.state().name());
        stateLabel.setForeground(getStateColor(page.state()));
        recordCountLabel.setText(DashboardWindow.formatNumber(page.recordCount()));
        listenerCountLabel.setText(String.valueOf(page.listenerCount()));

        // Progress
        boolean isLoading = page.state() == DashboardPageInfo.State.LOADING
                         || page.state() == DashboardPageInfo.State.UPDATING;
        progressBar.setVisible(isLoading);
        if (isLoading) {
            int p = page.loadProgress();
            if (p > 0 && p < 100) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(p);
                progressBar.setString(p + "%");
                progressBar.setStringPainted(true);
            } else {
                progressBar.setIndeterminate(true);
                progressBar.setStringPainted(false);
            }
        }

        // Error
        errorLabel.setVisible(page.state() == DashboardPageInfo.State.ERROR);
        if (page.state() == DashboardPageInfo.State.ERROR) {
            errorLabel.setText("Check log for details");
        }

        // Consumers
        consumersModel.clear();
        if (page.consumers() != null && !page.consumers().isEmpty()) {
            for (String consumer : page.consumers()) {
                consumersModel.addElement(consumer);
            }
        } else {
            consumersModel.addElement("(no consumers)");
        }

        // Log
        logModel.clear();
        if (logEntries != null && !logEntries.isEmpty()) {
            int max = 20;
            int count = 0;
            for (PageLogEntry entry : logEntries) {
                if (count++ >= max) break;
                String time = TIME_FORMATTER.format(Instant.ofEpochMilli(entry.timestamp()));
                logModel.addElement(String.format("[%s] %s - %s", time, entry.type(), entry.message()));
            }
        } else {
            logModel.addElement("(no events)");
        }
    }

    private void showEmptyState() {
        pageKeyLabel.setText("-");
        categoryLabel.setText("-");
        stateLabel.setText("-");
        stateLabel.setForeground(Color.GRAY);
        recordCountLabel.setText("-");
        listenerCountLabel.setText("-");
        errorLabel.setText("-");
        errorLabel.setVisible(false);
        progressBar.setVisible(false);
        consumersModel.clear();
        consumersModel.addElement("Select a page from the sidebar");
        logModel.clear();
        logModel.addElement("Select a page to view events");
    }

    private static Color getStateColor(DashboardPageInfo.State state) {
        return switch (state) {
            case EMPTY -> new Color(100, 100, 100);
            case LOADING -> new Color(100, 100, 180);
            case READY -> new Color(60, 140, 60);
            case UPDATING -> new Color(140, 140, 60);
            case ERROR -> new Color(180, 80, 80);
        };
    }

    private static class LogCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!isSelected) {
                String text = value.toString();
                if (text.contains("Error")) {
                    setForeground(new Color(180, 80, 80));
                } else if (text.contains("complete")) {
                    setForeground(new Color(60, 140, 60));
                } else if (text.contains("Start") || text.contains("loading")) {
                    setForeground(new Color(100, 100, 180));
                } else {
                    setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }
}
