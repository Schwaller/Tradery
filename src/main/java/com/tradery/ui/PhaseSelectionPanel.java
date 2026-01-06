package com.tradery.ui;

import com.tradery.io.PhaseStore;
import com.tradery.model.Phase;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel with checkboxes for selecting required phases for a strategy.
 * Displayed in the strategy editor.
 */
public class PhaseSelectionPanel extends JPanel {

    private final PhaseStore phaseStore;
    private final Map<String, JCheckBox> phaseCheckboxes = new LinkedHashMap<>();
    private JPanel checkboxPanel;
    private JButton manageButton;

    private Strategy strategy;
    private Runnable onChange;
    private boolean suppressChangeEvents = false;

    public PhaseSelectionPanel(PhaseStore phaseStore) {
        this.phaseStore = phaseStore;
        setLayout(new BorderLayout(0, 4));
        setOpaque(false);
        initializeComponents();
    }

    private void initializeComponents() {
        // Header with label and manage button
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel label = new JLabel("Required Phases");
        label.setForeground(Color.GRAY);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11));
        header.add(label, BorderLayout.WEST);

        manageButton = new JButton("Manage...");
        manageButton.setFont(manageButton.getFont().deriveFont(10f));
        manageButton.setMargin(new Insets(2, 6, 2, 6));
        manageButton.addActionListener(e -> openPhaseManager());
        header.add(manageButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Checkbox panel
        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        checkboxPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setPreferredSize(new Dimension(0, 60));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);

        refreshPhaseList();
    }

    /**
     * Refresh the list of available phases from the store.
     */
    public void refreshPhaseList() {
        checkboxPanel.removeAll();
        phaseCheckboxes.clear();

        List<Phase> phases = phaseStore.loadAll();
        phases.sort((a, b) -> {
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (phases.isEmpty()) {
            JLabel emptyLabel = new JLabel("No phases defined");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 11));
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkboxPanel.add(emptyLabel);
        } else {
            for (Phase phase : phases) {
                JCheckBox cb = new JCheckBox(formatPhaseLabel(phase));
                cb.setFont(cb.getFont().deriveFont(11f));
                cb.setOpaque(false);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.setToolTipText(phase.getCondition());
                cb.addActionListener(e -> fireChange());
                phaseCheckboxes.put(phase.getId(), cb);
                checkboxPanel.add(cb);
            }
        }

        // Re-apply selection if strategy is set
        if (strategy != null) {
            applySelectionFromStrategy();
        }

        checkboxPanel.revalidate();
        checkboxPanel.repaint();
    }

    private String formatPhaseLabel(Phase phase) {
        String name = phase.getName() != null ? phase.getName() : "Unnamed";
        String tf = phase.getTimeframe() != null ? phase.getTimeframe() : "?";
        return name + " (" + tf + ")";
    }

    private void applySelectionFromStrategy() {
        if (strategy == null) return;

        suppressChangeEvents = true;
        try {
            // Uncheck all first
            for (JCheckBox cb : phaseCheckboxes.values()) {
                cb.setSelected(false);
            }

            // Check required phases
            for (String phaseId : strategy.getRequiredPhaseIds()) {
                JCheckBox cb = phaseCheckboxes.get(phaseId);
                if (cb != null) {
                    cb.setSelected(true);
                }
            }
        } finally {
            suppressChangeEvents = false;
        }
    }

    public void loadFrom(Strategy strategy) {
        this.strategy = strategy;
        applySelectionFromStrategy();
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;

        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : phaseCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        strategy.setRequiredPhaseIds(selected);
    }

    private void openPhaseManager() {
        PhaseChooserFrame frame = new PhaseChooserFrame(phaseStore);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                refreshPhaseList();
            }
        });
        frame.setVisible(true);
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }
}
