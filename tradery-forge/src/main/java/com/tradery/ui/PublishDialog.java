package com.tradery.ui;

import com.tradery.model.Strategy;
import com.tradery.publish.DeskPublisher;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Dialog for publishing strategies to the library and managing published versions.
 */
public class PublishDialog extends JDialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Strategy strategy;
    private final DeskPublisher publisher;
    private final BiConsumer<String, String> onStatus;

    private JTable versionsTable;
    private DefaultTableModel tableModel;
    private JButton publishBtn;
    private JButton deleteBtn;
    private JLabel statusLabel;
    private JLabel libraryPathLabel;

    public PublishDialog(Frame owner, Strategy strategy, BiConsumer<String, String> onStatus) {
        super(owner, "Publish to Library", true);
        this.strategy = strategy;
        this.publisher = new DeskPublisher();
        this.onStatus = onStatus;

        initializeComponents();
        layoutComponents();
        loadVersions();

        setSize(500, 400);
        setLocationRelativeTo(owner);
    }

    private void initializeComponents() {
        // Versions table
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

        // Buttons
        publishBtn = new JButton("Publish New Version");
        publishBtn.setToolTipText("Publish current strategy as a new version");
        publishBtn.addActionListener(e -> publishNewVersion());

        deleteBtn = new JButton("Delete Version");
        deleteBtn.setToolTipText("Delete selected version");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> deleteSelectedVersion());

        // Enable delete when row selected
        versionsTable.getSelectionModel().addListSelectionListener(e -> {
            deleteBtn.setEnabled(versionsTable.getSelectedRow() >= 0);
        });

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

        // Library path label
        libraryPathLabel = new JLabel("Library: " + publisher.getLibraryPath());
        libraryPathLabel.setFont(libraryPathLabel.getFont().deriveFont(10f));
        libraryPathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        JLabel titleLabel = new JLabel("Strategy: " + strategy.getName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        int currentVersion = publisher.getPublishedVersion(strategy.getId());
        String versionText = currentVersion > 0
            ? "Current published version: v" + currentVersion
            : "Not yet published";
        JLabel versionLabel = new JLabel(versionText);
        headerPanel.add(versionLabel, BorderLayout.CENTER);
        headerPanel.add(libraryPathLabel, BorderLayout.SOUTH);

        content.add(headerPanel, BorderLayout.NORTH);

        // Table
        JScrollPane scrollPane = new JScrollPane(versionsTable);
        scrollPane.setPreferredSize(new Dimension(400, 200));
        content.add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new BorderLayout(8, 8));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.add(deleteBtn);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(publishBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        rightButtons.add(closeBtn);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);
        buttonPanel.add(statusLabel, BorderLayout.SOUTH);

        content.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void loadVersions() {
        tableModel.setRowCount(0);

        List<Integer> versions = publisher.listVersions(strategy.getId());
        Path strategyDir = publisher.getStrategiesDir().resolve(strategy.getId());

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

        // Scroll to bottom (latest version)
        if (tableModel.getRowCount() > 0) {
            versionsTable.scrollRectToVisible(
                versionsTable.getCellRect(tableModel.getRowCount() - 1, 0, true));
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void publishNewVersion() {
        try {
            // Ensure library exists
            publisher.ensureLibraryExists();

            // Publish
            int version = publisher.publish(strategy);

            // Update UI
            loadVersions();
            statusLabel.setText("Published v" + version + " successfully");
            statusLabel.setForeground(new Color(0, 128, 0));

            // Notify parent
            if (onStatus != null) {
                onStatus.accept(StatusManager.SOURCE_FILE_CHANGE,
                    "Published " + strategy.getName() + " v" + version + " to library");
            }

        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this,
                "Failed to publish: " + e.getMessage(),
                "Publish Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedVersion() {
        int row = versionsTable.getSelectedRow();
        if (row < 0) return;

        String versionStr = (String) tableModel.getValueAt(row, 0);
        int version = Integer.parseInt(versionStr.substring(1)); // Remove 'v' prefix

        int result = JOptionPane.showConfirmDialog(this,
            "Delete " + strategy.getName() + " " + versionStr + "?\n\n" +
            "This cannot be undone.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            publisher.deleteVersion(strategy.getId(), version);
            loadVersions();
            statusLabel.setText("Deleted " + versionStr);
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        } catch (IOException e) {
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    /**
     * Show the publish dialog.
     */
    public static void show(Frame owner, Strategy strategy, BiConsumer<String, String> onStatus) {
        PublishDialog dialog = new PublishDialog(owner, strategy, onStatus);
        dialog.setVisible(true);
    }
}
