package com.tradery.ui;

import com.tradery.publish.DeskPublisher;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dialog for browsing all published strategies in the library.
 * Shows a tree of strategies with their versions.
 */
public class LibraryBrowserDialog extends JDialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final DeskPublisher publisher;

    private JTree strategyTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JTable versionsTable;
    private DefaultTableModel tableModel;
    private JButton deleteVersionBtn;
    private JButton deleteStrategyBtn;
    private JButton refreshBtn;
    private JLabel statusLabel;
    private JLabel libraryPathLabel;

    public LibraryBrowserDialog(Frame owner) {
        super(owner, "Strategy Library", true);
        this.publisher = new DeskPublisher();

        initializeComponents();
        layoutComponents();
        loadLibrary();

        setSize(650, 500);
        setLocationRelativeTo(owner);
    }

    private void initializeComponents() {
        // Tree for strategies
        rootNode = new DefaultMutableTreeNode("Library");
        treeModel = new DefaultTreeModel(rootNode);
        strategyTree = new JTree(treeModel);
        strategyTree.setRootVisible(false);
        strategyTree.setShowsRootHandles(true);
        strategyTree.addTreeSelectionListener(e -> onStrategySelected());

        // Table for versions
        String[] columns = {"Version", "Published", "Size"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        versionsTable = new JTable(tableModel);
        versionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        versionsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        versionsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        versionsTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Buttons
        deleteVersionBtn = new JButton("Delete Version");
        deleteVersionBtn.setToolTipText("Delete selected version");
        deleteVersionBtn.setEnabled(false);
        deleteVersionBtn.addActionListener(e -> deleteSelectedVersion());

        deleteStrategyBtn = new JButton("Delete Strategy");
        deleteStrategyBtn.setToolTipText("Delete entire strategy from library");
        deleteStrategyBtn.setEnabled(false);
        deleteStrategyBtn.addActionListener(e -> deleteSelectedStrategy());

        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> loadLibrary());

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        libraryPathLabel = new JLabel("Path: " + publisher.getLibraryPath());
        libraryPathLabel.setFont(libraryPathLabel.getFont().deriveFont(10f));
        libraryPathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        JLabel titleLabel = new JLabel("Published Strategies");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(libraryPathLabel, BorderLayout.SOUTH);
        content.add(headerPanel, BorderLayout.NORTH);

        // Split: Tree on left, versions table on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(200);

        // Left: Strategy tree
        JScrollPane treeScroll = new JScrollPane(strategyTree);
        treeScroll.setPreferredSize(new Dimension(200, 300));
        splitPane.setLeftComponent(treeScroll);

        // Right: Versions table
        JPanel rightPanel = new JPanel(new BorderLayout(4, 4));
        JLabel versionsLabel = new JLabel("Versions:");
        versionsLabel.setFont(versionsLabel.getFont().deriveFont(Font.BOLD));
        rightPanel.add(versionsLabel, BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(versionsTable), BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);

        content.add(splitPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new BorderLayout(8, 8));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.add(deleteVersionBtn);
        leftButtons.add(deleteStrategyBtn);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(refreshBtn);
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        rightButtons.add(closeBtn);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void loadLibrary() {
        rootNode.removeAllChildren();
        tableModel.setRowCount(0);

        Path strategiesDir = publisher.getStrategiesDir();
        if (!Files.exists(strategiesDir)) {
            statusLabel.setText("Library folder does not exist yet");
            treeModel.reload();
            return;
        }

        try (Stream<Path> dirs = Files.list(strategiesDir)) {
            List<Path> strategyDirs = dirs
                .filter(Files::isDirectory)
                .sorted()
                .toList();

            for (Path strategyDir : strategyDirs) {
                String strategyId = strategyDir.getFileName().toString();
                List<Integer> versions = publisher.listVersions(strategyId);

                if (!versions.isEmpty()) {
                    DefaultMutableTreeNode strategyNode = new DefaultMutableTreeNode(
                        new StrategyNode(strategyId, versions.size())
                    );
                    rootNode.add(strategyNode);
                }
            }

            statusLabel.setText(strategyDirs.size() + " strategies in library");
        } catch (IOException e) {
            statusLabel.setText("Error reading library: " + e.getMessage());
        }

        treeModel.reload();

        // Expand all
        for (int i = 0; i < strategyTree.getRowCount(); i++) {
            strategyTree.expandRow(i);
        }

        updateButtonStates();
    }

    private void onStrategySelected() {
        tableModel.setRowCount(0);

        TreePath path = strategyTree.getSelectionPath();
        if (path == null) {
            updateButtonStates();
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof StrategyNode strategyNode) {
            loadVersionsForStrategy(strategyNode.id);
        }

        updateButtonStates();
    }

    private void loadVersionsForStrategy(String strategyId) {
        tableModel.setRowCount(0);

        List<Integer> versions = publisher.listVersions(strategyId);
        Path strategyDir = publisher.getStrategiesDir().resolve(strategyId);

        for (int version : versions) {
            Path versionFile = strategyDir.resolve("v" + version + ".yaml");
            String dateStr = "";
            String sizeStr = "";

            try {
                if (Files.exists(versionFile)) {
                    FileTime fileTime = Files.getLastModifiedTime(versionFile);
                    Instant instant = fileTime.toInstant();
                    dateStr = DATE_FORMAT.format(instant);

                    long size = Files.size(versionFile);
                    sizeStr = formatSize(size);
                }
            } catch (IOException e) {
                // Ignore
            }

            tableModel.addRow(new Object[]{"v" + version, dateStr, sizeStr});
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void updateButtonStates() {
        TreePath path = strategyTree.getSelectionPath();
        boolean strategySelected = path != null;
        boolean versionSelected = versionsTable.getSelectedRow() >= 0;

        deleteStrategyBtn.setEnabled(strategySelected);
        deleteVersionBtn.setEnabled(strategySelected && versionSelected);
    }

    private String getSelectedStrategyId() {
        TreePath path = strategyTree.getSelectionPath();
        if (path == null) return null;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof StrategyNode strategyNode) {
            return strategyNode.id;
        }
        return null;
    }

    private void deleteSelectedVersion() {
        String strategyId = getSelectedStrategyId();
        int row = versionsTable.getSelectedRow();
        if (strategyId == null || row < 0) return;

        String versionStr = (String) tableModel.getValueAt(row, 0);
        int version = Integer.parseInt(versionStr.substring(1));

        int result = JOptionPane.showConfirmDialog(this,
            "Delete " + strategyId + " " + versionStr + "?\n\nThis cannot be undone.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) return;

        try {
            publisher.deleteVersion(strategyId, version);
            loadVersionsForStrategy(strategyId);
            loadLibrary(); // Refresh tree in case strategy now empty
            statusLabel.setText("Deleted " + strategyId + " " + versionStr);
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void deleteSelectedStrategy() {
        String strategyId = getSelectedStrategyId();
        if (strategyId == null) return;

        List<Integer> versions = publisher.listVersions(strategyId);

        int result = JOptionPane.showConfirmDialog(this,
            "Delete ALL versions of '" + strategyId + "'?\n\n" +
            "This will delete " + versions.size() + " version(s).\n" +
            "This cannot be undone.",
            "Confirm Delete Strategy",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) return;

        try {
            publisher.unpublish(strategyId);
            loadLibrary();
            statusLabel.setText("Deleted strategy: " + strategyId);
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    /**
     * Node representing a strategy in the tree.
     */
    private record StrategyNode(String id, int versionCount) {
        @Override
        public String toString() {
            return id + " (" + versionCount + " version" + (versionCount != 1 ? "s" : "") + ")";
        }
    }
}
