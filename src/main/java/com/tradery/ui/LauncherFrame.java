package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.io.FileWatcher;
import com.tradery.io.StrategyStore;
import com.tradery.io.WindowStateStore;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher window for managing projects.
 * Displays a list of all strategies with Open/New/Delete buttons.
 */
public class LauncherFrame extends JFrame {

    private JList<Strategy> projectList;
    private DefaultListModel<Strategy> listModel;
    private JButton openButton;
    private JButton newButton;
    private JButton deleteButton;

    private final StrategyStore strategyStore;
    private final Map<String, ProjectWindow> openWindows = new HashMap<>();
    private FileWatcher fileWatcher;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public LauncherFrame() {
        super("Tradery - Projects");

        strategyStore = ApplicationContext.getInstance().getStrategyStore();

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadProjects();
        startFileWatcher();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 500);
        setMinimumSize(new Dimension(300, 400));

        // Restore saved position or center on screen
        Rectangle savedBounds = WindowStateStore.getInstance().getLauncherBounds();
        if (savedBounds != null) {
            setBounds(savedBounds);
        } else {
            setLocationRelativeTo(null);
        }

        // macOS-specific settings
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);

        // Save position on move/resize
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveLauncherState();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                saveLauncherState();
            }
        });
    }

    private void saveLauncherState() {
        if (isVisible()) {
            WindowStateStore.getInstance().saveLauncherBounds(getBounds());
        }
    }

    private void initializeComponents() {
        listModel = new DefaultListModel<>();
        projectList = new JList<>(listModel);
        projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectList.setCellRenderer(new ProjectCellRenderer());
        projectList.addListSelectionListener(e -> updateButtonStates());
        projectList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openProject();
                }
            }
        });

        openButton = new JButton("Open");
        openButton.addActionListener(e -> openProject());
        openButton.setEnabled(false);

        newButton = new JButton("New Project");
        newButton.addActionListener(e -> createProject());

        deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteProject());
        deleteButton.setEnabled(false);
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setBorder(BorderFactory.createEmptyBorder(32, 12, 12, 12));
        setContentPane(contentPane);

        // Title
        JLabel titleLabel = new JLabel("Projects");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 0));

        // List with scroll
        JScrollPane scrollPane = new JScrollPane(projectList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.add(deleteButton);
        buttonPanel.add(newButton);
        buttonPanel.add(openButton);

        contentPane.add(titleLabel, BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadProjects() {
        listModel.clear();
        List<Strategy> strategies = strategyStore.loadAll();

        // Sort by last updated, newest first
        strategies.sort((a, b) -> {
            Instant aTime = a.getUpdated() != null ? a.getUpdated() : Instant.EPOCH;
            Instant bTime = b.getUpdated() != null ? b.getUpdated() : Instant.EPOCH;
            return bTime.compareTo(aTime);
        });

        for (Strategy s : strategies) {
            listModel.addElement(s);
        }

        updateButtonStates();
    }

    private void startFileWatcher() {
        Path strategiesPath = new File(TraderyApp.USER_DIR, "strategies").toPath();

        fileWatcher = FileWatcher.forDirectory(
            strategiesPath,
            this::onFileModified,
            this::onFileDeleted,
            this::onFileCreated
        );

        try {
            fileWatcher.start();
        } catch (IOException e) {
            System.err.println("Failed to start file watcher: " + e.getMessage());
        }
    }

    private void onFileModified(Path path) {
        SwingUtilities.invokeLater(this::loadProjects);
    }

    private void onFileDeleted(Path path) {
        SwingUtilities.invokeLater(() -> {
            // Extract strategy ID from filename
            String filename = path.getFileName().toString();
            String strategyId = filename.replace(".json", "");

            // Close window if open
            ProjectWindow window = openWindows.get(strategyId);
            if (window != null) {
                // Window will handle its own closure via delete callback
            }

            loadProjects();
        });
    }

    private void onFileCreated(Path path) {
        SwingUtilities.invokeLater(this::loadProjects);
    }

    private void updateButtonStates() {
        boolean hasSelection = projectList.getSelectedValue() != null;
        openButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    private void openProject() {
        Strategy selected = projectList.getSelectedValue();
        if (selected == null) return;

        String id = selected.getId();

        // Check if already open
        ProjectWindow existing = openWindows.get(id);
        if (existing != null) {
            existing.bringToFront();
            return;
        }

        // Create new window
        ProjectWindow window = new ProjectWindow(selected, this::onWindowClosed);
        openWindows.put(id, window);
        window.setVisible(true);
    }

    private void createProject() {
        String name = JOptionPane.showInputDialog(this,
            "Enter project name:",
            "New Project",
            JOptionPane.PLAIN_MESSAGE);

        if (name != null && !name.trim().isEmpty()) {
            String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-");

            // Check for duplicate
            if (strategyStore.exists(id)) {
                JOptionPane.showMessageDialog(this,
                    "A project with this ID already exists: " + id,
                    "Duplicate Project",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            Strategy strategy = new Strategy(
                id,
                name.trim(),
                "",
                "RSI(14) < 30",
                "RSI(14) > 70",
                true
            );
            strategyStore.save(strategy);

            // Reload and select the new project
            loadProjects();
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getId().equals(id)) {
                    projectList.setSelectedIndex(i);
                    break;
                }
            }

            // Open the new project
            openProject();
        }
    }

    private void deleteProject() {
        Strategy selected = projectList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(this,
            "Delete project '" + selected.getName() + "'?\n\nThis will also delete backtest results.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            // Close window if open
            ProjectWindow window = openWindows.remove(selected.getId());
            if (window != null) {
                window.dispose();
            }

            strategyStore.delete(selected.getId());
            loadProjects();
        }
    }

    private void onWindowClosed(String strategyId) {
        openWindows.remove(strategyId);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            // Restore previously open windows
            restorePreviouslyOpenWindows();
        }
    }

    private void restorePreviouslyOpenWindows() {
        List<String> openIds = WindowStateStore.getInstance().getOpenProjectIds();
        for (String strategyId : openIds) {
            // Check if strategy still exists
            Strategy strategy = strategyStore.load(strategyId);
            if (strategy != null && !openWindows.containsKey(strategyId)) {
                ProjectWindow window = new ProjectWindow(strategy, this::onWindowClosed);
                openWindows.put(strategyId, window);
                window.setVisible(true);
            }
        }
    }

    @Override
    public void dispose() {
        // Close all open project windows
        for (ProjectWindow window : openWindows.values()) {
            window.dispose();
        }
        openWindows.clear();

        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        super.dispose();
    }

    /**
     * Custom renderer for project list items
     */
    private class ProjectCellRenderer extends JPanel implements ListCellRenderer<Strategy> {
        private JLabel nameLabel;
        private JLabel detailsLabel;

        public ProjectCellRenderer() {
            setLayout(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            nameLabel = new JLabel();
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));

            detailsLabel = new JLabel();
            detailsLabel.setFont(detailsLabel.getFont().deriveFont(11f));
            detailsLabel.setForeground(Color.GRAY);

            add(nameLabel, BorderLayout.NORTH);
            add(detailsLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Strategy> list, Strategy value,
                int index, boolean isSelected, boolean cellHasFocus) {

            nameLabel.setText(value.getName());

            // Build details string
            String symbol = value.getSymbol();
            String timeframe = value.getTimeframe();
            String lastUpdated = value.getUpdated() != null
                ? value.getUpdated().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
                : "Never";

            detailsLabel.setText(symbol + " • " + timeframe + " • Updated: " + lastUpdated);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
                detailsLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                detailsLabel.setForeground(Color.GRAY);
            }

            setOpaque(true);
            return this;
        }
    }
}
