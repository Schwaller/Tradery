package com.tradery.forge.ui;

import com.tradery.core.model.HoopPatternSettings;
import com.tradery.core.model.Strategy;
import com.tradery.forge.ui.base.ConfigurationPanel;

import java.awt.*;

/**
 * Panel for configuring entry hoop pattern settings in a strategy.
 * Hoops are always AND'ed with DSL conditions (like phases).
 * Exit hoop patterns are configured per exit zone.
 */
public class HoopPatternSelectionPanel extends ConfigurationPanel {

    private HoopPatternListPanel entryPatternsPanel;

    public HoopPatternSelectionPanel() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        // Pattern list panel - hoops are always AND'ed with DSL
        entryPatternsPanel = new HoopPatternListPanel("Patterns");
        entryPatternsPanel.setOnChange(this::fireChange);
    }

    private void layoutComponents() {
        add(entryPatternsPanel, BorderLayout.CENTER);
    }

    public void loadFrom(Strategy strategy) {
        setSuppressChangeEvents(true);
        try {
            if (strategy != null) {
                HoopPatternSettings settings = strategy.getHoopPatternSettings();
                entryPatternsPanel.setPatterns(
                    settings.getRequiredEntryPatternIds(),
                    settings.getExcludedEntryPatternIds()
                );
            } else {
                entryPatternsPanel.setPatterns(null, null);
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        HoopPatternSettings settings = strategy.getHoopPatternSettings();
        settings.setRequiredEntryPatternIds(entryPatternsPanel.getRequiredPatternIds());
        settings.setExcludedEntryPatternIds(entryPatternsPanel.getExcludedPatternIds());
    }
}
