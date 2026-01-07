package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.io.PhaseStore;
import com.tradery.model.Phase;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable JList-based panel for selecting required/excluded phases.
 * Used by both entry (strategy-level) and exit zone phase filtering.
 */
public class PhaseListPanel extends JPanel {

    private final JList<PhaseListItem> phaseList;
    private final DefaultListModel<PhaseListItem> listModel;
    private final JButton addButton;
    private final JButton removeButton;
    private final Set<String> requiredPhaseIds = new LinkedHashSet<>();
    private final Set<String> excludedPhaseIds = new LinkedHashSet<>();
    private Runnable onChange;

    public PhaseListPanel() {
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);

        // List model and JList - no scroll, size to content
        listModel = new DefaultListModel<>();
        phaseList = new JList<>(listModel);
        phaseList.setVisibleRowCount(-1);  // Size to content
        phaseList.setFont(phaseList.getFont().deriveFont(10f));
        phaseList.setCellRenderer(new PhaseListCellRenderer());
        phaseList.setOpaque(false);
        phaseList.setBackground(new Color(0, 0, 0, 0));

        // Buttons panel - vertical at top
        addButton = new JButton("+");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD, 9f));
        addButton.setMargin(new Insets(0, 4, 0, 4));
        addButton.setToolTipText("Add phase filter");
        addButton.addActionListener(e -> showAddPopup());

        removeButton = new JButton("−");
        removeButton.setFont(removeButton.getFont().deriveFont(Font.BOLD, 9f));
        removeButton.setMargin(new Insets(0, 4, 0, 4));
        removeButton.setToolTipText("Remove selected");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeSelected());

        // Add listener after removeButton is initialized
        phaseList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(phaseList.getSelectedIndex() >= 0);
            }
        });

        // Button panel with buttons stacked at top
        JPanel buttonStack = new JPanel();
        buttonStack.setLayout(new BoxLayout(buttonStack, BoxLayout.Y_AXIS));
        buttonStack.setOpaque(false);
        buttonStack.add(addButton);
        buttonStack.add(Box.createVerticalStrut(2));
        buttonStack.add(removeButton);

        JPanel buttonWrapper = new JPanel(new BorderLayout());
        buttonWrapper.setOpaque(false);
        buttonWrapper.add(buttonStack, BorderLayout.NORTH);

        add(phaseList, BorderLayout.CENTER);
        add(buttonWrapper, BorderLayout.EAST);

        updateListModel();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setPhases(List<String> required, List<String> excluded) {
        requiredPhaseIds.clear();
        excludedPhaseIds.clear();
        if (required != null) requiredPhaseIds.addAll(required);
        if (excluded != null) excludedPhaseIds.addAll(excluded);
        updateListModel();
    }

    public List<String> getRequiredPhaseIds() {
        return new ArrayList<>(requiredPhaseIds);
    }

    public List<String> getExcludedPhaseIds() {
        return new ArrayList<>(excludedPhaseIds);
    }

    private void updateListModel() {
        listModel.clear();
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();

        for (String phaseId : requiredPhaseIds) {
            Phase phase = phaseStore.load(phaseId);
            String name = phase != null ? phase.getName() : phaseId;
            listModel.addElement(new PhaseListItem(phaseId, name, true));
        }
        for (String phaseId : excludedPhaseIds) {
            Phase phase = phaseStore.load(phaseId);
            String name = phase != null ? phase.getName() : phaseId;
            listModel.addElement(new PhaseListItem(phaseId, name, false));
        }

        // Show placeholder if empty
        if (listModel.isEmpty()) {
            listModel.addElement(new PhaseListItem(null, "Any phase", true));
        }
    }

    private void showAddPopup() {
        JPopupMenu popup = new JPopupMenu();
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        List<Phase> phases = phaseStore.loadAll();
        phases.sort((a, b) -> {
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (phases.isEmpty()) {
            JMenuItem empty = new JMenuItem("No phases defined");
            empty.setEnabled(false);
            popup.add(empty);
        } else {
            JMenu requireMenu = new JMenu("Require");
            for (Phase phase : phases) {
                String id = phase.getId();
                if (!requiredPhaseIds.contains(id) && !excludedPhaseIds.contains(id)) {
                    JMenuItem item = new JMenuItem(phase.getName());
                    item.addActionListener(e -> {
                        requiredPhaseIds.add(id);
                        updateListModel();
                        fireChange();
                    });
                    requireMenu.add(item);
                }
            }
            if (requireMenu.getItemCount() == 0) {
                JMenuItem none = new JMenuItem("(none available)");
                none.setEnabled(false);
                requireMenu.add(none);
            }
            popup.add(requireMenu);

            JMenu excludeMenu = new JMenu("Exclude (NOT)");
            for (Phase phase : phases) {
                String id = phase.getId();
                if (!requiredPhaseIds.contains(id) && !excludedPhaseIds.contains(id)) {
                    JMenuItem item = new JMenuItem(phase.getName());
                    item.addActionListener(e -> {
                        excludedPhaseIds.add(id);
                        updateListModel();
                        fireChange();
                    });
                    excludeMenu.add(item);
                }
            }
            if (excludeMenu.getItemCount() == 0) {
                JMenuItem none = new JMenuItem("(none available)");
                none.setEnabled(false);
                excludeMenu.add(none);
            }
            popup.add(excludeMenu);

            if (!requiredPhaseIds.isEmpty() || !excludedPhaseIds.isEmpty()) {
                popup.addSeparator();
                JMenuItem clearAll = new JMenuItem("Clear all");
                clearAll.addActionListener(e -> {
                    requiredPhaseIds.clear();
                    excludedPhaseIds.clear();
                    updateListModel();
                    fireChange();
                });
                popup.add(clearAll);
            }
        }
        popup.show(addButton, 0, addButton.getHeight());
    }

    private void removeSelected() {
        PhaseListItem selected = phaseList.getSelectedValue();
        if (selected != null && selected.phaseId != null) {
            if (selected.required) {
                requiredPhaseIds.remove(selected.phaseId);
            } else {
                excludedPhaseIds.remove(selected.phaseId);
            }
            updateListModel();
            fireChange();
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    // Data class for list items
    record PhaseListItem(String phaseId, String name, boolean required) {}

    // Cell renderer for the phase list
    private static class PhaseListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PhaseListItem item) {
                if (item.phaseId == null) {
                    // Placeholder "Any phase"
                    setText(item.name);
                    setForeground(Color.GRAY);
                } else {
                    String prefix = item.required ? "+" : "−";
                    setText(prefix + " " + item.name);
                    if (!isSelected) {
                        setForeground(item.required ? new Color(50, 120, 50) : new Color(160, 60, 60));
                    }
                }
            }
            return this;
        }
    }
}
