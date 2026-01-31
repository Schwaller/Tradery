package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.TraderyApp;
import com.tradery.forge.data.sqlite.SqliteDataStore;
import com.tradery.forge.io.FileWatcher;
import com.tradery.forge.io.HoopPatternStore;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.io.StrategyStore;
import com.tradery.forge.io.WindowStateStore;
import com.tradery.core.model.ExitZone;
import com.tradery.core.model.Strategy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private JButton manageDataButton;
    private JButton settingsButton;
    private JButton phasesButton;
    private JButton hoopsButton;
    private JButton downloadDashboardButton;
    private JButton libraryButton;

    private final StrategyStore strategyStore;
    private final Map<String, ProjectWindow> openWindows = new HashMap<>();
    private volatile String lastFocusedStrategyId;
    private FileWatcher fileWatcher;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    // Static instance for API access
    private static LauncherFrame instance;

    public LauncherFrame() {
        super("Tradery - Projects");
        instance = this;

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

        // Integrated title bar look
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

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

        manageDataButton = new JButton("Manage Data");
        manageDataButton.addActionListener(e -> {
            SqliteDataStore dataStore = ApplicationContext.getInstance().getSqliteDataStore();
            DataManagementDialog.show(this, dataStore);
        });

        settingsButton = new JButton("Settings");
        settingsButton.setToolTipText("Configure theme and data storage");
        settingsButton.addActionListener(e -> {
            SettingsDialog dialog = new SettingsDialog(this);
            dialog.setVisible(true);
        });

        phasesButton = new JButton("Phases");
        phasesButton.setToolTipText("Manage market phase definitions");
        phasesButton.addActionListener(e -> openPhasesWindow());

        hoopsButton = new JButton("Hoops");
        hoopsButton.setToolTipText("Manage hoop pattern definitions");
        hoopsButton.addActionListener(e -> openHoopsWindow());

        downloadDashboardButton = new JButton("Downloads");
        downloadDashboardButton.setToolTipText("View download status and logs");
        downloadDashboardButton.addActionListener(e -> openDownloadDashboardWindow());

        libraryButton = new JButton("Library");
        libraryButton.setToolTipText("Browse published strategies in the library");
        libraryButton.addActionListener(e -> openLibraryBrowser());

        // Context menu for right-click
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openProject());
        JMenuItem renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(e -> renameProject());
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deleteProject());
        JMenuItem restoreItem = new JMenuItem("Restore to Default");
        restoreItem.addActionListener(e -> restoreSelectedPreset());

        contextMenu.add(openItem);
        contextMenu.addSeparator();
        contextMenu.add(renameItem);
        contextMenu.add(restoreItem);
        contextMenu.addSeparator();
        contextMenu.add(deleteItem);

        projectList.setComponentPopupMenu(contextMenu);
        projectList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = projectList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        projectList.setSelectedIndex(index);
                        Strategy selected = projectList.getSelectedValue();
                        // Only show "Restore to Default" for preset strategies
                        restoreItem.setVisible(selected != null && selected.isPreset());
                    }
                }
            }
        });
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Title bar area (28px height for macOS traffic lights)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        JLabel titleLabel = new JLabel(TraderyApp.APP_NAME, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Main content - full width
        JPanel mainContent = new JPanel(new BorderLayout());

        // List with scroll - no border, full width
        JScrollPane scrollPane = new JScrollPane(projectList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Wrap list with separators above and below
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(new JSeparator(), BorderLayout.NORTH);
        listWrapper.add(scrollPane, BorderLayout.CENTER);
        listWrapper.add(new JSeparator(), BorderLayout.SOUTH);

        // Button panel with left and right sections
        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        leftButtons.add(libraryButton);
        leftButtons.add(phasesButton);
        leftButtons.add(hoopsButton);
        leftButtons.add(downloadDashboardButton);
        leftButtons.add(manageDataButton);
        leftButtons.add(settingsButton);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rightButtons.add(newButton);
        rightButtons.add(openButton);
        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        mainContent.add(listWrapper, BorderLayout.CENTER);
        mainContent.add(buttonPanel, BorderLayout.SOUTH);

        contentPane.add(titleBar, BorderLayout.NORTH);
        contentPane.add(mainContent, BorderLayout.CENTER);
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
        SwingUtilities.invokeLater(() -> {
            loadProjects();

            // Check if this is a strategy.json file
            if (!path.getFileName().toString().equals("strategy.json")) {
                return;
            }

            // Extract strategy ID from path (parent directory name)
            Path parent = path.getParent();
            if (parent == null) return;
            String strategyId = parent.getFileName().toString();

            // Skip if window is already open (it handles its own reloading)
            if (openWindows.containsKey(strategyId)) {
                return;
            }

            // Load the strategy to get its name
            Strategy strategy = strategyStore.load(strategyId);
            if (strategy == null) return;

            // Ask user if they want to open it
            int result = JOptionPane.showConfirmDialog(this,
                "Strategy '" + strategy.getName() + "' was modified externally.\n\n" +
                "Open it to run the backtest?",
                "Strategy Modified",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                // Select and open the strategy
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).getId().equals(strategyId)) {
                        projectList.setSelectedIndex(i);
                        break;
                    }
                }
                openProject();
            }
        });
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
                true
            );
            // Add default exit zone with exit condition
            strategy.setExitZones(List.of(
                ExitZone.builder("Default")
                    .exitCondition("RSI(14) > 70")
                    .build()
            ));
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

    private void renameProject() {
        Strategy selected = projectList.getSelectedValue();
        if (selected == null) return;

        String newName = JOptionPane.showInputDialog(this,
            "Enter new name:",
            selected.getName());

        if (newName != null && !newName.trim().isEmpty() && !newName.equals(selected.getName())) {
            selected.setName(newName.trim());
            strategyStore.save(selected);
            loadProjects();

            // Update window title if open
            ProjectWindow window = openWindows.get(selected.getId());
            if (window != null) {
                window.setTitle(newName.trim() + " - " + TraderyApp.APP_NAME);
            }
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

    private void restorePresets() {
        int result = JOptionPane.showConfirmDialog(this,
            "Restore all preset strategies to their default settings?\n\n" +
            "This will overwrite any modifications you've made to preset strategies.",
            "Restore Presets",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            strategyStore.restoreAllPresets();
            loadProjects();
            JOptionPane.showMessageDialog(this,
                "Presets have been restored to their default settings.",
                "Presets Restored",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void restoreSelectedPreset() {
        Strategy selected = projectList.getSelectedValue();
        if (selected == null || !selected.isPreset()) return;

        int result = JOptionPane.showConfirmDialog(this,
            "Restore '" + selected.getName() + "' to its default settings?\n\n" +
            "This will overwrite any modifications you've made.",
            "Restore Preset",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            Strategy restored = strategyStore.installPreset(selected.getPresetId());
            if (restored != null) {
                loadProjects();

                // Update window if open
                ProjectWindow window = openWindows.get(selected.getId());
                if (window != null) {
                    window.reloadStrategy();
                }
            }
        }
    }

    private void openPhasesWindow() {
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        PhaseChooserFrame frame = new PhaseChooserFrame(phaseStore);
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    private void openHoopsWindow() {
        try {
            HoopPatternStore hoopPatternStore = ApplicationContext.getInstance().getHoopPatternStore();
            InteractiveHoopEditorFrame frame = new InteractiveHoopEditorFrame(hoopPatternStore);
            frame.setLocationRelativeTo(this);
            frame.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error opening Hoops window: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDownloadDashboardWindow() {
        DownloadDashboardWindow dashboard = new DownloadDashboardWindow();
        dashboard.setLocationRelativeTo(this);
        dashboard.setVisible(true);
    }

    private void openLibraryBrowser() {
        LibraryBrowserDialog dialog = new LibraryBrowserDialog(this);
        dialog.setVisible(true);
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

    /**
     * Get the singleton instance (for API access).
     */
    public static LauncherFrame getInstance() {
        return instance;
    }

    /**
     * Get info about all open project windows (for debugging API).
     */
    public List<ProjectWindow.WindowInfo> getOpenWindowsInfo() {
        List<ProjectWindow.WindowInfo> result = new ArrayList<>();
        for (ProjectWindow window : openWindows.values()) {
            result.add(window.getWindowInfo());
        }
        return result;
    }

    public String getLastFocusedStrategyId() {
        return lastFocusedStrategyId;
    }

    public void setLastFocusedStrategyId(String id) {
        lastFocusedStrategyId = id;
    }

    // ========== Public API for opening windows ==========

    /**
     * Open the Phases window (for API access).
     */
    public void openPhases() {
        SwingUtilities.invokeLater(this::openPhasesWindow);
    }

    /**
     * Open the Hoops window (for API access).
     */
    public void openHoops() {
        SwingUtilities.invokeLater(this::openHoopsWindow);
    }

    /**
     * Open the Settings dialog (for API access).
     */
    public void openSettings() {
        SwingUtilities.invokeLater(() -> {
            SettingsDialog dialog = new SettingsDialog(this);
            dialog.setVisible(true);
        });
    }

    /**
     * Open the Data Management dialog (for API access).
     */
    public void openDataManagement() {
        SwingUtilities.invokeLater(() -> {
            SqliteDataStore dataStore = ApplicationContext.getInstance().getSqliteDataStore();
            DataManagementDialog.show(this, dataStore);
        });
    }

    /**
     * Open the DSL Help dialog (for API access).
     */
    public void openDslHelp() {
        SwingUtilities.invokeLater(() -> {
            DslHelpDialog dialog = new DslHelpDialog(this);
            dialog.setVisible(true);
        });
    }

    /**
     * Open the Download Dashboard window (for API access).
     */
    public void openDownloadDashboard() {
        SwingUtilities.invokeLater(this::openDownloadDashboardWindow);
    }

    /**
     * Open a project window by strategy ID (for API access).
     * Returns true if successful, false if strategy not found.
     */
    public boolean openProjectById(String strategyId) {
        Strategy strategy = strategyStore.load(strategyId);
        if (strategy == null) {
            return false;
        }
        SwingUtilities.invokeLater(() -> {
            ProjectWindow existing = openWindows.get(strategyId);
            if (existing != null) {
                existing.bringToFront();
            } else {
                ProjectWindow window = new ProjectWindow(strategy, this::onWindowClosed);
                openWindows.put(strategyId, window);
                window.setVisible(true);
            }
        });
        return true;
    }

    /**
     * Bring the launcher window to front (for API access).
     */
    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            toFront();
            requestFocus();
        });
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

            // Show preset indicator in name
            String displayName = value.getName();
            if (value.isPreset()) {
                displayName += "  [Preset]";
            }
            nameLabel.setText(displayName);

            // Build details string
            String symbol = value.getSymbol();
            String timeframe = value.getTimeframe();
            String lastUpdated = value.getUpdated() != null
                ? value.getUpdated().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
                : "Never";

            String details = symbol + " • " + timeframe + " • Updated: " + lastUpdated;
            if (value.isPreset()) {
                details += " • v" + value.getPresetVersion();
            }
            detailsLabel.setText(details);

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
