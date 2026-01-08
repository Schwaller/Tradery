package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.HoopPattern;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable JList-based panel for selecting required/excluded hoop patterns.
 * Similar to PhaseListPanel but for hoop patterns.
 */
public class HoopPatternListPanel extends JPanel {

    private final JList<PatternListItem> patternList;
    private final DefaultListModel<PatternListItem> listModel;
    private final JButton addButton;
    private final JButton removeButton;
    private final Set<String> requiredPatternIds = new LinkedHashSet<>();
    private final Set<String> excludedPatternIds = new LinkedHashSet<>();
    private Runnable onChange;
    private final String title;

    public HoopPatternListPanel(String title) {
        this.title = title;
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);

        // Titled border
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                getFont().deriveFont(10f),
                Color.GRAY
            ),
            BorderFactory.createEmptyBorder(2, 6, 6, 6)
        ));

        // List model and JList
        listModel = new DefaultListModel<>();
        patternList = new JList<>(listModel);
        patternList.setVisibleRowCount(-1);
        patternList.setFont(patternList.getFont().deriveFont(10f));
        patternList.setCellRenderer(new PatternListCellRenderer());
        patternList.setOpaque(false);
        patternList.setBackground(new Color(0, 0, 0, 0));

        // Buttons panel
        addButton = new JButton("+");
        addButton.setFont(addButton.getFont().deriveFont(Font.BOLD, 9f));
        addButton.setMargin(new Insets(0, 4, 0, 4));
        addButton.setToolTipText("Add pattern filter");
        addButton.addActionListener(e -> showAddPopup());

        removeButton = new JButton("−");
        removeButton.setFont(removeButton.getFont().deriveFont(Font.BOLD, 9f));
        removeButton.setMargin(new Insets(0, 4, 0, 4));
        removeButton.setToolTipText("Remove selected");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeSelected());

        patternList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeButton.setEnabled(patternList.getSelectedIndex() >= 0);
            }
        });

        JPanel buttonStack = new JPanel();
        buttonStack.setLayout(new BoxLayout(buttonStack, BoxLayout.Y_AXIS));
        buttonStack.setOpaque(false);
        buttonStack.add(addButton);
        buttonStack.add(Box.createVerticalStrut(2));
        buttonStack.add(removeButton);

        JPanel buttonWrapper = new JPanel(new BorderLayout());
        buttonWrapper.setOpaque(false);
        buttonWrapper.add(buttonStack, BorderLayout.NORTH);

        add(patternList, BorderLayout.CENTER);
        add(buttonWrapper, BorderLayout.EAST);

        updateListModel();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setPatterns(List<String> required, List<String> excluded) {
        requiredPatternIds.clear();
        excludedPatternIds.clear();
        if (required != null) requiredPatternIds.addAll(required);
        if (excluded != null) excludedPatternIds.addAll(excluded);
        updateListModel();
    }

    public List<String> getRequiredPatternIds() {
        return new ArrayList<>(requiredPatternIds);
    }

    public List<String> getExcludedPatternIds() {
        return new ArrayList<>(excludedPatternIds);
    }

    private void updateListModel() {
        listModel.clear();
        HoopPatternStore store = ApplicationContext.getInstance().getHoopPatternStore();

        for (String patternId : requiredPatternIds) {
            HoopPattern pattern = store.load(patternId);
            String name = pattern != null ? pattern.getName() : patternId;
            listModel.addElement(new PatternListItem(patternId, name, true));
        }
        for (String patternId : excludedPatternIds) {
            HoopPattern pattern = store.load(patternId);
            String name = pattern != null ? pattern.getName() : patternId;
            listModel.addElement(new PatternListItem(patternId, name, false));
        }

        // Show placeholder if empty
        if (listModel.isEmpty()) {
            listModel.addElement(new PatternListItem(null, "Any pattern", true));
        }
    }

    private void showAddPopup() {
        JPopupMenu popup = new JPopupMenu();
        HoopPatternStore store = ApplicationContext.getInstance().getHoopPatternStore();
        List<HoopPattern> patterns = store.loadAll();
        patterns.sort((a, b) -> {
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (patterns.isEmpty()) {
            JMenuItem empty = new JMenuItem("No patterns defined");
            empty.setEnabled(false);
            popup.add(empty);
        } else {
            JMenu requireMenu = new JMenu("Require");
            for (HoopPattern pattern : patterns) {
                String id = pattern.getId();
                if (!requiredPatternIds.contains(id) && !excludedPatternIds.contains(id)) {
                    JMenuItem item = new JMenuItem(pattern.getName());
                    item.addActionListener(e -> {
                        requiredPatternIds.add(id);
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
            for (HoopPattern pattern : patterns) {
                String id = pattern.getId();
                if (!requiredPatternIds.contains(id) && !excludedPatternIds.contains(id)) {
                    JMenuItem item = new JMenuItem(pattern.getName());
                    item.addActionListener(e -> {
                        excludedPatternIds.add(id);
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

            if (!requiredPatternIds.isEmpty() || !excludedPatternIds.isEmpty()) {
                popup.addSeparator();
                JMenuItem clearAll = new JMenuItem("Clear all");
                clearAll.addActionListener(e -> {
                    requiredPatternIds.clear();
                    excludedPatternIds.clear();
                    updateListModel();
                    fireChange();
                });
                popup.add(clearAll);
            }
        }
        popup.show(addButton, 0, addButton.getHeight());
    }

    private void removeSelected() {
        PatternListItem selected = patternList.getSelectedValue();
        if (selected != null && selected.patternId != null) {
            if (selected.required) {
                requiredPatternIds.remove(selected.patternId);
            } else {
                excludedPatternIds.remove(selected.patternId);
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
    record PatternListItem(String patternId, String name, boolean required) {}

    // Cell renderer
    private static class PatternListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof PatternListItem item) {
                if (item.patternId == null) {
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
