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

        // Left panel: phase list with buttons
        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        listPanel.setPreferredSize(new Dimension(200, 0));

        JScrollPane listScroll = new JScrollPane(phaseList);
        listPanel.add(listScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);
        listPanel.add(buttonPanel, BorderLayout.SOUTH);

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

        // Main layout
        setLayout(new BorderLayout());
        add(titleBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    private void loadPhases() {
        List<Phase> phases = phaseStore.loadAll();
        phases.sort((a, b) -> {
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
            editorPanel.setEnabled(true);
        } else {
            currentPhase = null;
            editorPanel.loadFrom(null);
            editorPanel.setEnabled(false);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = phaseList.getSelectedValue() != null;
        deleteButton.setEnabled(hasSelection);
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
     * Custom renderer for phase list items.
     */
    private class PhaseCellRenderer extends JPanel implements ListCellRenderer<Phase> {
        private final JLabel nameLabel;
        private final JLabel detailsLabel;

        public PhaseCellRenderer() {
            setLayout(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            nameLabel = new JLabel();
            nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

            detailsLabel = new JLabel();
            detailsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            detailsLabel.setForeground(Color.GRAY);

            add(nameLabel, BorderLayout.CENTER);
            add(detailsLabel, BorderLayout.SOUTH);
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
            String symbol = value.getSymbol() != null ? value.getSymbol() : "?";
            String tf = value.getTimeframe() != null ? value.getTimeframe() : "?";

            nameLabel.setText(name);
            detailsLabel.setText(symbol + " / " + tf);

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
