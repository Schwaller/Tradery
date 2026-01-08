package com.tradery.ui;

import com.tradery.TraderyApp;
import com.tradery.io.FileWatcher;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.Hoop;
import com.tradery.model.HoopPattern;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.List;

/**
 * Window for managing hoop pattern definitions.
 * Hoop patterns are sequential price-checkpoint patterns that can be used
 * as entry/exit triggers for strategies.
 */
public class HoopPatternChooserFrame extends JFrame {

    private JList<HoopPattern> patternList;
    private DefaultListModel<HoopPattern> listModel;
    private HoopPatternEditorPanel editorPanel;
    private JButton newButton;
    private JButton deleteButton;
    private JButton interactiveEditButton;

    private final HoopPatternStore patternStore;
    private FileWatcher fileWatcher;
    private HoopPattern currentPattern;
    private Timer autoSaveTimer;
    private boolean ignoringFileChanges = false;

    private static final int AUTO_SAVE_DELAY_MS = 500;

    public HoopPatternChooserFrame(HoopPatternStore patternStore) {
        super("Hoop Patterns - " + TraderyApp.APP_NAME);

        this.patternStore = patternStore;

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadPatterns();
        startFileWatcher();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setMinimumSize(new Dimension(700, 500));
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
        patternList = new JList<>(listModel);
        patternList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        patternList.setCellRenderer(new PatternCellRenderer());
        patternList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onPatternSelected();
            }
        });

        editorPanel = new HoopPatternEditorPanel();
        editorPanel.setOnChange(this::scheduleAutoSave);
        editorPanel.setEnabled(false);

        newButton = new JButton("New Pattern");
        newButton.addActionListener(e -> createPattern());

        deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deletePattern());
        deleteButton.setEnabled(false);

        interactiveEditButton = new JButton("Interactive Edit");
        interactiveEditButton.setToolTipText("Edit pattern with visual chart overlay");
        interactiveEditButton.addActionListener(e -> openInteractiveEditor());
        interactiveEditButton.setEnabled(false);

        // Auto-save timer
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MS, e -> saveCurrentPattern());
        autoSaveTimer.setRepeats(false);

        // Context menu for right-click
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deletePattern());
        contextMenu.add(deleteItem);
        patternList.setComponentPopupMenu(contextMenu);
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 28));
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel("Hoop Patterns", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Main split: pattern list on left, editor on right
        JScrollPane listScroll = new JScrollPane(patternList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel editorWrapper = new JPanel(new BorderLayout());
        editorWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        editorWrapper.add(editorPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setLeftComponent(listScroll);
        splitPane.setRightComponent(editorWrapper);
        splitPane.setDividerLocation(220);
        splitPane.setResizeWeight(0);

        // Center content with separator below title
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JSeparator(), BorderLayout.NORTH);
        centerPanel.add(splitPane, BorderLayout.CENTER);

        // Bottom button bar - full width with separator above
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);

        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.add(newButton);
        leftButtons.add(deleteButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.add(interactiveEditButton);

        buttonBar.add(leftButtons, BorderLayout.WEST);
        buttonBar.add(rightButtons, BorderLayout.EAST);
        bottomPanel.add(buttonBar, BorderLayout.CENTER);

        // Main layout
        contentPane.add(titleBar, BorderLayout.NORTH);
        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadPatterns() {
        List<HoopPattern> patterns = patternStore.loadAll();
        patterns.sort((a, b) -> {
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        listModel.clear();
        for (HoopPattern pattern : patterns) {
            listModel.addElement(pattern);
        }

        updateButtonStates();
    }

    private void onPatternSelected() {
        HoopPattern selected = patternList.getSelectedValue();
        if (selected != null) {
            currentPattern = selected;
            editorPanel.loadFrom(selected);
            editorPanel.setEnabled(true);
        } else {
            currentPattern = null;
            editorPanel.loadFrom(null);
            editorPanel.setEnabled(false);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = patternList.getSelectedValue() != null;
        deleteButton.setEnabled(hasSelection);
        interactiveEditButton.setEnabled(hasSelection);
    }

    private void createPattern() {
        // Generate unique ID
        String baseId = "new-pattern";
        String id = baseId;
        int counter = 1;
        while (patternStore.exists(id)) {
            id = baseId + "-" + counter++;
        }

        // Create a default pattern with one hoop
        List<Hoop> defaultHoops = new ArrayList<>();
        defaultHoops.add(new Hoop("hoop-1", -2.0, 2.0, 5, 2, Hoop.AnchorMode.ACTUAL_HIT));

        HoopPattern pattern = new HoopPattern(id, "New Pattern", defaultHoops, "BTCUSDT", "1h");
        pattern.setCreated(Instant.now());
        pattern.setUpdated(Instant.now());

        patternStore.save(pattern);
        loadPatterns();

        // Select the new pattern
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).getId().equals(id)) {
                patternList.setSelectedIndex(i);
                break;
            }
        }
    }

    private void deletePattern() {
        HoopPattern selected = patternList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(
            this,
            "Delete pattern '" + selected.getName() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            patternStore.delete(selected.getId());
            loadPatterns();
            if (listModel.size() > 0) {
                patternList.setSelectedIndex(0);
            }
        }
    }

    private void openInteractiveEditor() {
        if (currentPattern == null) return;

        InteractiveHoopEditorFrame editor = new InteractiveHoopEditorFrame(
            currentPattern,
            patternStore
        );
        editor.setLocationRelativeTo(this);
        editor.setVisible(true);
    }

    private void scheduleAutoSave() {
        if (ignoringFileChanges || currentPattern == null) return;
        autoSaveTimer.restart();
    }

    private void saveCurrentPattern() {
        if (currentPattern == null) return;

        editorPanel.applyTo(currentPattern);
        currentPattern.setUpdated(Instant.now());

        ignoringFileChanges = true;
        try {
            patternStore.save(currentPattern);

            // Update the list display
            int selectedIndex = patternList.getSelectedIndex();
            if (selectedIndex >= 0) {
                listModel.set(selectedIndex, currentPattern);
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
                patternStore.getDirectory().toPath(),
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
            String selectedId = currentPattern != null ? currentPattern.getId() : null;
            loadPatterns();

            // Re-select previous pattern
            if (selectedId != null) {
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).getId().equals(selectedId)) {
                        patternList.setSelectedIndex(i);
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
     * Custom renderer for pattern list items.
     */
    private class PatternCellRenderer extends JPanel implements ListCellRenderer<HoopPattern> {
        private final JLabel nameLabel;
        private final JLabel detailsLabel;

        public PatternCellRenderer() {
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
                JList<? extends HoopPattern> list,
                HoopPattern value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            String name = value.getName() != null ? value.getName() : "Unnamed";
            String symbol = value.getSymbol() != null ? value.getSymbol() : "?";
            String tf = value.getTimeframe() != null ? value.getTimeframe() : "?";
            int hoopCount = value.getHoops() != null ? value.getHoops().size() : 0;

            nameLabel.setText(name);
            detailsLabel.setText(symbol + " / " + tf + " (" + hoopCount + " hoops)");

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
