package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.io.FileWatcher;
import com.tradery.io.PhaseStore;
import com.tradery.model.Phase;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.List;

/**
 * Window for managing phase definitions.
 * Phases are market regime conditions (ranging, trending, crash, etc.)
 * that can be used as entry filters for strategies.
 */
public class PhaseChooserFrame extends JFrame {

    private JList<Phase> phaseList;
    private DefaultListModel<Phase> listModel;
    private PhaseEditorPanel editorPanel;
    private JButton newButton;
    private JButton deleteButton;
    private JButton previewButton;
    private PhasePreviewWindow previewWindow;

    private final PhaseStore phaseStore;
    private FileWatcher fileWatcher;
    private Phase currentPhase;
    private Timer autoSaveTimer;
    private boolean ignoringFileChanges = false;

    private static final int AUTO_SAVE_DELAY_MS = 500;

    public PhaseChooserFrame(PhaseStore phaseStore) {
        super("Phases - " + TraderyApp.APP_NAME);

        this.phaseStore = phaseStore;

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadPhases();
        startFileWatcher();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(500, 400));
        setLocationRelativeTo(null);

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        // Clean up on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void initializeComponents() {
        listModel = new DefaultListModel<>();
        phaseList = new JList<>(listModel);
        phaseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        phaseList.setCellRenderer(new PhaseCellRenderer());
        phaseList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onPhaseSelected();
            }
        });

        editorPanel = new PhaseEditorPanel();
        editorPanel.setOnChange(this::scheduleAutoSave);
        editorPanel.setEnabled(false);

        newButton = new JButton("New Phase");
        newButton.addActionListener(e -> createPhase());

        deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deletePhase());
        deleteButton.setEnabled(false);

        previewButton = new JButton("Preview Chart");
        previewButton.addActionListener(e -> openPreviewWindow());
        previewButton.setEnabled(false);

        // Auto-save timer
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> saveCurrentPhase());
        autoSaveTimer.setRepeats(false);

        // Context menu for right-click
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deletePhase());
        contextMenu.add(deleteItem);
        phaseList.setComponentPopupMenu(contextMenu);
    }

    private void layoutComponents() {
        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel("Phases", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Left panel: phase list (snug to all edges)
        JPanel listPanel = new JPanel(new BorderLayout(0, 0));
        listPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        listPanel.setPreferredSize(new Dimension(200, 0));

        JScrollPane listScroll = new JScrollPane(phaseList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listPanel.add(listScroll, BorderLayout.CENTER);

        // Right panel: editor
        JPanel editorWrapper = new JPanel(new BorderLayout());
        editorWrapper.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        editorWrapper.add(editorPanel, BorderLayout.CENTER);

        // Main split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(listPanel);
        splitPane.setRightComponent(editorWrapper);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0);

        // Bottom bar with separator line above
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 0));
        bottomContainer.add(new JSeparator(), BorderLayout.NORTH);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(newButton);
        leftButtons.add(deleteButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.add(previewButton);

        bottomBar.add(leftButtons, BorderLayout.WEST);
        bottomBar.add(rightButtons, BorderLayout.EAST);
        bottomContainer.add(bottomBar, BorderLayout.CENTER);

        // Top container with title and separator below
        JPanel topContainer = new JPanel(new BorderLayout(0, 0));
        topContainer.add(titleBar, BorderLayout.CENTER);
        topContainer.add(new JSeparator(), BorderLayout.SOUTH);

        // Main layout
        setLayout(new BorderLayout());
        add(topContainer, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void loadPhases() {
        List<Phase> phases = phaseStore.loadAll();
        // Sort by category first, then by name
        phases.sort((a, b) -> {
            String c1 = a.getCategory() != null ? a.getCategory() : "Custom";
            String c2 = b.getCategory() != null ? b.getCategory() : "Custom";
            int catCompare = c1.compareTo(c2);
            if (catCompare != 0) return catCompare;
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        listModel.clear();
        for (Phase phase : phases) {
            listModel.addElement(phase);
        }

        updateButtonStates();
    }

    private void onPhaseSelected() {
        Phase selected = phaseList.getSelectedValue();
        if (selected != null) {
            currentPhase = selected;
            editorPanel.loadFrom(selected);
            // Disable editing for built-in phases
            editorPanel.setEnabled(!selected.isBuiltIn());
            // Update preview window if open
            if (previewWindow != null && previewWindow.isVisible()) {
                previewWindow.setPhase(selected);
            }
        } else {
            currentPhase = null;
            editorPanel.loadFrom(null);
            editorPanel.setEnabled(false);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        Phase selected = phaseList.getSelectedValue();
        boolean hasSelection = selected != null;
        boolean isBuiltIn = hasSelection && selected.isBuiltIn();
        deleteButton.setEnabled(hasSelection && !isBuiltIn);  // Can't delete built-in
        previewButton.setEnabled(hasSelection);
    }

    private void createPhase() {
        // Generate unique ID
        String baseId = "new-phase";
        String id = baseId;
        int counter = 1;
        while (phaseStore.exists(id)) {
            id = baseId + "-" + counter++;
        }

        Phase phase = new Phase(id, "New Phase", "close > SMA(200)", "1d", "BTCUSDT");
        phase.setCategory("Custom");
        phase.setCreated(Instant.now());
        phase.setUpdated(Instant.now());

        phaseStore.save(phase);
        loadPhases();

        // Select the new phase
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId().equals(id)) {
                phaseList.setSelectedIndex(i);
                break;
            }
        }
    }

    private void deletePhase() {
        Phase selected = phaseList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(
            this,
            "Delete phase '" + selected.getName() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            phaseStore.delete(selected.getId());
            loadPhases();
            if (listModel.size() > 0) {
                phaseList.setSelectedIndex(0);
            }
        }
    }

    private void openPreviewWindow() {
        if (currentPhase == null) return;

        if (previewWindow == null) {
            previewWindow = new PhasePreviewWindow();
        }

        previewWindow.setPhase(currentPhase);
        previewWindow.setLocationRelativeTo(this);
        previewWindow.setVisible(true);
        previewWindow.toFront();
    }

    private void scheduleAutoSave() {
        if (ignoringFileChanges || currentPhase == null) return;
        autoSaveTimer.restart();
    }

    private void saveCurrentPhase() {
        if (currentPhase == null) return;

        editorPanel.applyTo(currentPhase);
        currentPhase.setUpdated(Instant.now());

        ignoringFileChanges = true;
        try {
            phaseStore.save(currentPhase);

            // Update the list display
            int selectedIndex = phaseList.getSelectedIndex();
            if (selectedIndex >= 0) {
                listModel.set(selectedIndex, currentPhase);
            }

            // Refresh the preview window if open
            if (previewWindow != null && previewWindow.isVisible()) {
                previewWindow.setPhase(currentPhase);
            }
        } finally {
            // Reset flag after a delay to allow file system to settle
            Timer resetTimer = new Timer(200, e -> ignoringFileChanges = false);
            resetTimer.setRepeats(false);
            resetTimer.start();
        }
    }

    private void startFileWatcher() {
        try {
            fileWatcher = FileWatcher.forDirectory(
                phaseStore.getDirectory().toPath(),
                path -> onFileChanged(),  // onModified
                path -> onFileChanged(),  // onDeleted
                path -> onFileChanged()   // onCreated
            );
            fileWatcher.start();
        } catch (IOException e) {
            System.err.println("Failed to start file watcher: " + e.getMessage());
        }
    }

    private void onFileChanged() {
        if (ignoringFileChanges) return;

        SwingUtilities.invokeLater(() -> {
            String selectedId = currentPhase != null ? currentPhase.getId() : null;
            loadPhases();

            // Re-select previous phase
            if (selectedId != null) {
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).getId().equals(selectedId)) {
                        phaseList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        });
    }

    private void cleanup() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
        }
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
    }

    /**
     * Custom renderer for phase list items with category grouping.
     */
    private class PhaseCellRenderer extends JPanel implements ListCellRenderer<Phase> {
        private final JPanel headerPanel;
        private final JLabel headerLabel;
        private final JSeparator headerSeparator;
        private final JPanel contentPanel;
        private final JLabel nameLabel;
        private final JLabel detailsLabel;

        public PhaseCellRenderer() {
            setLayout(new BorderLayout(0, 0));

            // Header panel for category (with separator above for non-first groups)
            headerPanel = new JPanel(new BorderLayout(0, 0));

            // Separator line above header (hidden for first group)
            headerSeparator = new JSeparator();
            headerPanel.add(headerSeparator, BorderLayout.NORTH);

            // Category label
            JPanel labelWrapper = new JPanel(new BorderLayout());
            labelWrapper.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
            labelWrapper.setOpaque(false);
            headerLabel = new JLabel();
            headerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            headerLabel.setForeground(UIManager.getColor("Component.accentColor"));
            labelWrapper.add(headerLabel, BorderLayout.WEST);
            headerPanel.add(labelWrapper, BorderLayout.CENTER);
            headerPanel.setOpaque(false);

            // Content panel for phase info
            contentPanel = new JPanel(new BorderLayout(4, 0));
            contentPanel.setBorder(BorderFactory.createEmptyBorder(4, 16, 6, 8));
            contentPanel.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            detailsLabel = new JLabel();
            detailsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            detailsLabel.setForeground(Color.GRAY);

            contentPanel.add(nameLabel, BorderLayout.CENTER);
            contentPanel.add(detailsLabel, BorderLayout.EAST);

            add(contentPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Phase> list,
                Phase value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            String name = value.getName() != null ? value.getName() : "Unnamed";
            String category = value.getCategory() != null ? value.getCategory() : "Custom";
            String symbol = value.getSymbol() != null ? value.getSymbol() : "?";
            String tf = value.getTimeframe() != null ? value.getTimeframe() : "?";

            // Check if we need to show category header
            boolean showHeader = false;
            if (index == 0) {
                showHeader = true;
            } else if (index > 0) {
                Phase prev = list.getModel().getElementAt(index - 1);
                String prevCategory = prev.getCategory() != null ? prev.getCategory() : "Custom";
                showHeader = !category.equals(prevCategory);
            }

            // Remove header if present, then add if needed
            remove(headerPanel);
            if (showHeader) {
                headerLabel.setText(category.toUpperCase());
                // Show separator only for non-first groups
                headerSeparator.setVisible(index > 0);
                add(headerPanel, BorderLayout.NORTH);
            }

            nameLabel.setText(name);
            detailsLabel.setText(symbol + " / " + tf);

            // Only content panel gets selection background, not the header
            if (isSelected) {
                contentPanel.setBackground(list.getSelectionBackground());
                contentPanel.setOpaque(true);
                nameLabel.setForeground(list.getSelectionForeground());
                detailsLabel.setForeground(list.getSelectionForeground());
            } else {
                contentPanel.setBackground(list.getBackground());
                contentPanel.setOpaque(false);
                nameLabel.setForeground(list.getForeground());
                detailsLabel.setForeground(Color.GRAY);
            }

            // Main panel stays transparent so header doesn't get colored
            setBackground(list.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
