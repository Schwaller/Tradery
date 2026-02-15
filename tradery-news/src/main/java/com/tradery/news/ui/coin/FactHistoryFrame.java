package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.IntelMenuBar;
import com.tradery.ui.controls.ThinSplitPane;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git-log-like viewer for the immutable facts table.
 * Master-detail layout: commit list (top) + fact detail (bottom).
 * Commits are the primary unit, with drill-down to individual facts.
 */
public class FactHistoryFrame extends JFrame {

    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d HH:mm");

    private final FactStore factStore;
    private final EntityStore entityStore;

    // Top: commit table
    private final CommitTableModel commitTableModel;
    private final JTable commitTable;

    // Bottom: fact detail table
    private final CommitFactTableModel factTableModel;
    private final JTable factTable;

    private final ThinSplitPane splitPane;
    private final JTextField searchField;
    private final JButton loadMoreBtn;
    private final JLabel statusLabel;

    private String currentSearch = null;
    private int currentOffset = 0;
    private int totalCount = 0;
    private Timer debounceTimer;

    // Cache entity name lookups
    private final Map<String, String> entityNameCache = new HashMap<>();

    private FactHistoryFrame(FactStore factStore, EntityStore entityStore, JFrame parent) {
        super("Fact History");
        this.factStore = factStore;
        this.entityStore = entityStore;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Restore window size/position
        IntelConfig config = IntelConfig.get();
        if (config.getFactHistoryWidth() > 0 && config.getFactHistoryHeight() > 0) {
            setSize(config.getFactHistoryWidth(), config.getFactHistoryHeight());
            if (config.getFactHistoryX() >= 0 && config.getFactHistoryY() >= 0) {
                setLocation(config.getFactHistoryX(), config.getFactHistoryY());
            } else {
                setLocationRelativeTo(parent);
            }
        } else {
            setSize(900, 650);
            setLocationRelativeTo(parent);
        }

        // Save window state on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                IntelConfig cfg = IntelConfig.get();
                cfg.setFactHistoryWidth(getWidth());
                cfg.setFactHistoryHeight(getHeight());
                cfg.setFactHistoryX(getX());
                cfg.setFactHistoryY(getY());
                cfg.setFactHistorySplitPosition(splitPane.getDividerLocation());
                cfg.save();
                instance = null;
            }
        });

        // Transparent title bar (macOS unified style)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // === Top: Commit table ===
        commitTableModel = new CommitTableModel();
        commitTable = new JTable(commitTableModel);
        commitTable.setRowHeight(24);
        commitTable.setShowGrid(false);
        commitTable.setIntercellSpacing(new Dimension(0, 0));
        commitTable.getTableHeader().setReorderingAllowed(false);
        commitTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commitTable.setFillsViewportHeight(true);

        // Commit column widths
        commitTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Time
        commitTable.getColumnModel().getColumn(1).setPreferredWidth(90);  // Source
        commitTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Peer
        commitTable.getColumnModel().getColumn(3).setPreferredWidth(50);  // Facts
        commitTable.getColumnModel().getColumn(4).setPreferredWidth(60);  // Entities
        commitTable.getColumnModel().getColumn(5).setPreferredWidth(300); // Summary

        // Custom renderer for Time column
        commitTable.getColumnModel().getColumn(0).setCellRenderer(new TimeCellRenderer());
        // Dim renderer for Facts and Entities count columns
        commitTable.getColumnModel().getColumn(3).setCellRenderer(new DimCellRenderer());
        commitTable.getColumnModel().getColumn(4).setCellRenderer(new DimCellRenderer());

        // Selection listener — populate fact detail on commit click
        commitTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = commitTable.getSelectedRow();
                if (row >= 0 && row < commitTableModel.getRowCount()) {
                    loadFactsForCommit(commitTableModel.getCommitAt(row));
                }
            }
        });

        // === Bottom: Fact detail table ===
        factTableModel = new CommitFactTableModel();
        factTable = new JTable(factTableModel);
        factTable.setRowHeight(24);
        factTable.setShowGrid(false);
        factTable.setIntercellSpacing(new Dimension(0, 0));
        factTable.getTableHeader().setReorderingAllowed(false);
        factTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        factTable.setFillsViewportHeight(true);

        // Fact detail column widths
        factTable.getColumnModel().getColumn(0).setPreferredWidth(150); // Entity
        factTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Attribute
        factTable.getColumnModel().getColumn(2).setPreferredWidth(300); // Value

        // Search field
        searchField = new JTextField(20);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search facts...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
        });

        // Load More button
        loadMoreBtn = new JButton("Load More (" + PAGE_SIZE + ")");
        loadMoreBtn.addActionListener(e -> loadMore());

        // Status label
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        // === Split pane ===
        JScrollPane commitScroll = new JScrollPane(commitTable);
        commitScroll.setBorder(BorderFactory.createEmptyBorder());

        JScrollPane factScroll = new JScrollPane(factTable);
        factScroll.setBorder(BorderFactory.createEmptyBorder());

        splitPane = new ThinSplitPane(JSplitPane.VERTICAL_SPLIT, commitScroll, factScroll);
        splitPane.setResizeWeight(0.6);

        // Restore split position
        if (config.getFactHistorySplitPosition() > 0) {
            splitPane.setDividerLocation(config.getFactHistorySplitPosition());
        }

        // === Layout ===
        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(createHeaderBar(), BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        mainPanel.add(headerWrapper, BorderLayout.NORTH);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        bottomBar.add(loadMoreBtn, BorderLayout.WEST);
        bottomBar.add(statusLabel, BorderLayout.EAST);
        JPanel bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.add(new JSeparator(), BorderLayout.NORTH);
        bottomWrapper.add(bottomBar, BorderLayout.CENTER);
        mainPanel.add(bottomWrapper, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setJMenuBar(IntelMenuBar.create(this));

        // Initial load
        loadInitial();
    }

    private JPanel createHeaderBar() {
        int barHeight = 52;

        JPanel headerBar = new JPanel(new GridBagLayout());
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left: traffic lights placeholder
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftContent.setOpaque(false);
        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftContent.add(buttonsPlaceholder);
        }
        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel("Fact History");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, gbc);

        // Right: Search field
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightContent.setOpaque(false);
        searchField.setPreferredSize(new Dimension(200, 28));
        rightContent.add(searchField);
        GridBagConstraints rc = new GridBagConstraints();
        rc.anchor = GridBagConstraints.EAST;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.weightx = 1.0;
        rightPanel.add(rightContent, rc);
        headerBar.add(rightPanel, gbc);

        return headerBar;
    }

    private void scheduleSearch() {
        if (debounceTimer != null) debounceTimer.stop();
        debounceTimer = new Timer(300, e -> {
            String text = searchField.getText().trim();
            currentSearch = text.isEmpty() ? null : text;
            currentOffset = 0;
            commitTableModel.clear();
            factTableModel.clear();
            loadInitial();
        });
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void loadInitial() {
        FactStore.CommitQuery query = new FactStore.CommitQuery(PAGE_SIZE, 0, currentSearch);
        new SwingWorker<CommitLoadResult, Void>() {
            @Override
            protected CommitLoadResult doInBackground() {
                List<FactStore.CommitSummary> commits = factStore.queryCommits(query);
                int count = factStore.countCommits(query);
                List<DisplayCommit> display = resolveDisplayCommits(commits);
                return new CommitLoadResult(display, count);
            }

            @Override
            protected void done() {
                try {
                    CommitLoadResult result = get();
                    commitTableModel.setCommits(result.commits);
                    totalCount = result.totalCount;
                    currentOffset = result.commits.size();
                    updateStatus();
                } catch (Exception e) {
                    System.err.println("Failed to load commit history: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void loadMore() {
        loadMoreBtn.setEnabled(false);
        FactStore.CommitQuery query = new FactStore.CommitQuery(PAGE_SIZE, currentOffset, currentSearch);
        new SwingWorker<CommitLoadResult, Void>() {
            @Override
            protected CommitLoadResult doInBackground() {
                List<FactStore.CommitSummary> commits = factStore.queryCommits(query);
                List<DisplayCommit> display = resolveDisplayCommits(commits);
                return new CommitLoadResult(display, totalCount);
            }

            @Override
            protected void done() {
                try {
                    CommitLoadResult result = get();
                    commitTableModel.addCommits(result.commits);
                    currentOffset += result.commits.size();
                    updateStatus();
                } catch (Exception e) {
                    System.err.println("Failed to load more commits: " + e.getMessage());
                } finally {
                    loadMoreBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void loadFactsForCommit(DisplayCommit commit) {
        if (commit == null) return;
        new SwingWorker<List<DisplayFact>, Void>() {
            @Override
            protected List<DisplayFact> doInBackground() {
                List<FactStore.Fact> facts = factStore.getFactsByCommitId(commit.commitId);
                return resolveDisplayFacts(facts);
            }

            @Override
            protected void done() {
                try {
                    factTableModel.setFacts(get());
                } catch (Exception e) {
                    System.err.println("Failed to load commit facts: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updateStatus() {
        int showing = commitTableModel.getRowCount();
        statusLabel.setText("Showing " + showing + " of " + String.format("%,d", totalCount));
        loadMoreBtn.setEnabled(showing < totalCount);
        loadMoreBtn.setText("Load More (" + PAGE_SIZE + ")");
    }

    private List<DisplayCommit> resolveDisplayCommits(List<FactStore.CommitSummary> commits) {
        String localPeerId = factStore.peerId();
        List<DisplayCommit> result = new ArrayList<>();
        for (FactStore.CommitSummary c : commits) {
            String peerDisplay = c.peerId().equals(localPeerId) ? "You" : c.peerId().substring(0, Math.min(8, c.peerId().length()));
            String summary = buildSummary(c);
            result.add(new DisplayCommit(c.commitId(), c.wallClock(), c.source(), peerDisplay,
                    c.factCount(), c.entityCount(), summary));
        }
        return result;
    }

    private String buildSummary(FactStore.CommitSummary commit) {
        List<String> entityIds = factStore.getEntityIdsForCommit(commit.commitId());
        // Resolve up to 5 entity names
        List<String> names = new ArrayList<>();
        int limit = Math.min(5, entityIds.size());
        for (int i = 0; i < limit; i++) {
            names.add(resolveEntityName(entityIds.get(i)));
        }

        if (commit.entityCount() <= 3 && commit.factCount() <= 10) {
            // Small commit: show entity: attributes
            List<FactStore.Fact> facts = factStore.getFactsByCommitId(commit.commitId());
            Map<String, List<String>> byEntity = new java.util.LinkedHashMap<>();
            for (FactStore.Fact f : facts) {
                byEntity.computeIfAbsent(resolveEntityName(f.entityId()), k -> new ArrayList<>())
                        .add(f.attribute());
            }
            List<String> parts = new ArrayList<>();
            for (var entry : byEntity.entrySet()) {
                String attrs = String.join(", ", entry.getValue());
                parts.add(entry.getKey() + ": " + attrs);
            }
            return String.join("; ", parts);
        } else {
            // Large commit: entity count + names
            String nameList = String.join(", ", names);
            if (entityIds.size() > 5) {
                nameList += ", ...";
            }
            return commit.entityCount() + " entities: " + nameList;
        }
    }

    private List<DisplayFact> resolveDisplayFacts(List<FactStore.Fact> facts) {
        List<DisplayFact> result = new ArrayList<>();
        for (FactStore.Fact f : facts) {
            String entityName = resolveEntityName(f.entityId());
            result.add(new DisplayFact(entityName, f.attribute(), f.value()));
        }
        return result;
    }

    private String resolveEntityName(String entityId) {
        return entityNameCache.computeIfAbsent(entityId, id -> {
            String name = factStore.getCurrent(id, "name");
            return name != null ? name : id;
        });
    }

    // ==================== SINGLETON ====================

    private static FactHistoryFrame instance;

    public static void open(FactStore factStore, EntityStore entityStore, JFrame parent) {
        if (instance != null && instance.isShowing()) {
            instance.toFront();
            instance.requestFocus();
            return;
        }
        instance = new FactHistoryFrame(factStore, entityStore, parent);
        instance.setVisible(true);
    }

    // ==================== INNER TYPES ====================

    record DisplayCommit(String commitId, long wallClock, String source, String peer,
                         int factCount, int entityCount, String summary) {}

    record DisplayFact(String entityName, String attribute, String value) {}

    private record CommitLoadResult(List<DisplayCommit> commits, int totalCount) {}

    // === Commit table model (top) ===
    private static class CommitTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Time", "Source", "Peer", "Facts", "Entities", "Summary"};
        private final List<DisplayCommit> commits = new ArrayList<>();

        void setCommits(List<DisplayCommit> newCommits) {
            commits.clear();
            commits.addAll(newCommits);
            fireTableDataChanged();
        }

        void addCommits(List<DisplayCommit> moreCommits) {
            int first = commits.size();
            commits.addAll(moreCommits);
            if (!moreCommits.isEmpty()) {
                fireTableRowsInserted(first, commits.size() - 1);
            }
        }

        void clear() {
            commits.clear();
            fireTableDataChanged();
        }

        DisplayCommit getCommitAt(int row) {
            return row >= 0 && row < commits.size() ? commits.get(row) : null;
        }

        @Override public int getRowCount() { return commits.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            DisplayCommit c = commits.get(row);
            return switch (col) {
                case 0 -> c.wallClock();
                case 1 -> c.source();
                case 2 -> c.peer();
                case 3 -> c.factCount();
                case 4 -> c.entityCount();
                case 5 -> c.summary();
                default -> "";
            };
        }
    }

    // === Fact detail table model (bottom) ===
    private static class CommitFactTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Entity", "Attribute", "Value"};
        private final List<DisplayFact> facts = new ArrayList<>();

        void setFacts(List<DisplayFact> newFacts) {
            facts.clear();
            facts.addAll(newFacts);
            fireTableDataChanged();
        }

        void clear() {
            facts.clear();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return facts.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            DisplayFact f = facts.get(row);
            return switch (col) {
                case 0 -> f.entityName();
                case 1 -> f.attribute();
                case 2 -> f.value() != null ? f.value() : "";
                default -> "";
            };
        }
    }

    // === Cell renderers ===
    private static class TimeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            if (value instanceof Long wallClock) {
                setText(formatRelativeTime(wallClock));
            } else {
                setText("");
            }
            setFont(new Font("SansSerif", Font.PLAIN, 11));
            if (!isSelected) {
                setForeground(UIManager.getColor("Label.disabledForeground"));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return this;
        }

        private String formatRelativeTime(long wallClock) {
            Instant then = Instant.ofEpochMilli(wallClock);
            Duration elapsed = Duration.between(then, Instant.now());

            if (elapsed.toMinutes() < 1) return "just now";
            if (elapsed.toMinutes() < 60) return elapsed.toMinutes() + " min ago";
            if (elapsed.toHours() < 24) return elapsed.toHours() + " hr ago";
            if (elapsed.toDays() < 7) return elapsed.toDays() + " day" + (elapsed.toDays() > 1 ? "s" : "") + " ago";

            LocalDateTime ldt = LocalDateTime.ofInstant(then, ZoneId.systemDefault());
            return ldt.format(DATE_FMT);
        }
    }

    private static class DimCellRenderer extends DefaultTableCellRenderer {
        {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setFont(new Font("SansSerif", Font.PLAIN, 11));
            if (!isSelected) {
                setForeground(UIManager.getColor("Label.disabledForeground"));
            }
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return this;
        }
    }
}
