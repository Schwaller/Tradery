package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.io.HoopPatternStore;
import com.tradery.core.model.HoopPattern;
import com.tradery.forge.ui.controls.BadgeListPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Reusable panel for selecting required/excluded hoop patterns.
 * Uses BadgeListPanel for modern badge-style display.
 */
public class HoopPatternListPanel extends JPanel {

    private final BadgeListPanel badgePanel;
    private Runnable onChange;

    public HoopPatternListPanel(String title) {
        setLayout(new BorderLayout());
        setOpaque(false);

        badgePanel = new BadgeListPanel(title);
        badgePanel.setOnChange(() -> {
            if (onChange != null) onChange.run();
        });

        // Set up name resolver
        badgePanel.setNameResolver(this::resolvePatternName);

        // Set up popup builder
        badgePanel.setPopupBuilder(this::buildPopupMenu);

        add(badgePanel, BorderLayout.CENTER);
    }

    private String resolvePatternName(String patternId) {
        HoopPatternStore store = ApplicationContext.getInstance().getHoopPatternStore();
        HoopPattern pattern = store.load(patternId);
        return pattern != null ? pattern.getName() : patternId;
    }

    private void buildPopupMenu(JPopupMenu popup) {
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
            return;
        }

        JMenu requireMenu = new JMenu("Require");
        int requireCount = 0;
        for (HoopPattern pattern : patterns) {
            String id = pattern.getId();
            if (!badgePanel.contains(id)) {
                JMenuItem item = new JMenuItem(pattern.getName());
                item.addActionListener(e -> badgePanel.addRequired(id));
                requireMenu.add(item);
                requireCount++;
            }
        }
        if (requireCount == 0) {
            JMenuItem none = new JMenuItem("(none available)");
            none.setEnabled(false);
            requireMenu.add(none);
        }
        popup.add(requireMenu);

        JMenu excludeMenu = new JMenu("Exclude (NOT)");
        int excludeCount = 0;
        for (HoopPattern pattern : patterns) {
            String id = pattern.getId();
            if (!badgePanel.contains(id)) {
                JMenuItem item = new JMenuItem(pattern.getName());
                item.addActionListener(e -> badgePanel.addExcluded(id));
                excludeMenu.add(item);
                excludeCount++;
            }
        }
        if (excludeCount == 0) {
            JMenuItem none = new JMenuItem("(none available)");
            none.setEnabled(false);
            excludeMenu.add(none);
        }
        popup.add(excludeMenu);

        if (badgePanel.hasSelections()) {
            popup.addSeparator();
            JMenuItem clearAll = new JMenuItem("Clear all");
            clearAll.addActionListener(e -> badgePanel.clearAll());
            popup.add(clearAll);
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setPatterns(List<String> required, List<String> excluded) {
        badgePanel.setItems(required, excluded);
    }

    public List<String> getRequiredPatternIds() {
        return badgePanel.getRequiredIds();
    }

    public List<String> getExcludedPatternIds() {
        return badgePanel.getExcludedIds();
    }
}
