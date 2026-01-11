package com.tradery.ui;

import com.tradery.model.HoopPatternSettings;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring entry hoop pattern settings in a strategy.
 * Hoops are always AND'ed with DSL conditions (like phases).
 * Exit hoop patterns are configured per exit zone.
 */
public class HoopPatternSelectionPanel extends JPanel {

    private HoopPatternListPanel entryPatternsPanel;

    private Runnable onChange;
    private boolean suppressChangeEvents = false;

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

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void loadFrom(Strategy strategy) {
        suppressChangeEvents = true;
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
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        HoopPatternSettings settings = strategy.getHoopPatternSettings();
        settings.setRequiredEntryPatternIds(entryPatternsPanel.getRequiredPatternIds());
        settings.setExcludedEntryPatternIds(entryPatternsPanel.getExcludedPatternIds());
    }
}
