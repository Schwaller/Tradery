package com.tradery.forge.ui;

import com.tradery.core.model.Phase;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.ui.controls.BadgeListPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable panel for selecting required/excluded phases.
 * Uses BadgeListPanel for modern badge-style display.
 */
public class PhaseListPanel extends JPanel {

    private final BadgeListPanel badgePanel;
    private Runnable onChange;

    public PhaseListPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        badgePanel = new BadgeListPanel("Phases");
        badgePanel.setOnChange(() -> {
            if (onChange != null) onChange.run();
        });

        // Set up name resolver
        badgePanel.setNameResolver(this::resolvePhaseName);

        // Set up popup builder
        badgePanel.setPopupBuilder(this::buildPopupMenu);

        add(badgePanel, BorderLayout.CENTER);
    }

    private String resolvePhaseName(String phaseId) {
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        Phase phase = phaseStore.load(phaseId);
        return phase != null ? phase.getName() : phaseId;
    }

    private void buildPopupMenu(JPopupMenu popup) {
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        List<Phase> phases = phaseStore.loadAll();

        // Sort by category, then by name
        phases.sort((a, b) -> {
            String c1 = a.getCategory() != null ? a.getCategory() : "Custom";
            String c2 = b.getCategory() != null ? b.getCategory() : "Custom";
            int catCompare = c1.compareTo(c2);
            if (catCompare != 0) return catCompare;
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (phases.isEmpty()) {
            JMenuItem empty = new JMenuItem("No phases defined");
            empty.setEnabled(false);
            popup.add(empty);
            return;
        }

        JMenu requireMenu = new JMenu("Require");
        buildCategorySubmenus(requireMenu, phases, true);
        popup.add(requireMenu);

        JMenu excludeMenu = new JMenu("Exclude (NOT)");
        buildCategorySubmenus(excludeMenu, phases, false);
        popup.add(excludeMenu);

        if (badgePanel.hasSelections()) {
            popup.addSeparator();
            JMenuItem clearAll = new JMenuItem("Clear all");
            clearAll.addActionListener(e -> badgePanel.clearAll());
            popup.add(clearAll);
        }
    }

    private void buildCategorySubmenus(JMenu parentMenu, List<Phase> phases, boolean isRequired) {
        // Group phases by category
        java.util.Map<String, java.util.List<Phase>> byCategory = new java.util.LinkedHashMap<>();
        for (Phase phase : phases) {
            String cat = phase.getCategory() != null ? phase.getCategory() : "Custom";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(phase);
        }

        int totalAdded = 0;
        for (var entry : byCategory.entrySet()) {
            String category = entry.getKey();
            java.util.List<Phase> categoryPhases = entry.getValue();

            JMenu categoryMenu = new JMenu(category);
            int itemsInCategory = 0;

            for (Phase phase : categoryPhases) {
                String id = phase.getId();
                if (!badgePanel.contains(id)) {
                    JMenuItem item = new JMenuItem(phase.getName());
                    item.addActionListener(e -> {
                        if (isRequired) {
                            badgePanel.addRequired(id);
                        } else {
                            badgePanel.addExcluded(id);
                        }
                    });
                    categoryMenu.add(item);
                    itemsInCategory++;
                }
            }

            if (itemsInCategory > 0) {
                parentMenu.add(categoryMenu);
                totalAdded += itemsInCategory;
            }
        }

        if (totalAdded == 0) {
            JMenuItem none = new JMenuItem("(none available)");
            none.setEnabled(false);
            parentMenu.add(none);
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setPhases(List<String> required, List<String> excluded) {
        badgePanel.setItems(required, excluded);
    }

    public List<String> getRequiredPhaseIds() {
        return badgePanel.getRequiredIds();
    }

    public List<String> getExcludedPhaseIds() {
        return badgePanel.getExcludedIds();
    }
}
