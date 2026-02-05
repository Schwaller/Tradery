package com.tradery.forge.ui;

import com.tradery.core.model.Strategy;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.ui.base.ConfigurationPanel;

import java.awt.*;
import java.util.ArrayList;

/**
 * Phase selection panel for strategy-level entry filtering.
 * Wraps PhaseListPanel and adds strategy load/apply functionality.
 */
public class PhaseSelectionPanel extends ConfigurationPanel {

    private final PhaseStore phaseStore;
    private final PhaseListPanel phaseListPanel;
    private Strategy strategy;

    public PhaseSelectionPanel(PhaseStore phaseStore) {
        this.phaseStore = phaseStore;
        setLayout(new BorderLayout());
        setOpaque(false);

        phaseListPanel = new PhaseListPanel();
        phaseListPanel.setOnChange(this::fireChange);
        add(phaseListPanel, BorderLayout.CENTER);
    }

    public void loadFrom(Strategy strategy) {
        this.strategy = strategy;
        setSuppressChangeEvents(true);
        try {
            if (strategy != null) {
                phaseListPanel.setPhases(
                    strategy.getRequiredPhaseIds(),
                    strategy.getExcludedPhaseIds()
                );
            } else {
                phaseListPanel.setPhases(null, null);
            }
        } finally {
            setSuppressChangeEvents(false);
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setRequiredPhaseIds(new ArrayList<>(phaseListPanel.getRequiredPhaseIds()));
        strategy.setExcludedPhaseIds(new ArrayList<>(phaseListPanel.getExcludedPhaseIds()));
    }

    /**
     * Refresh the display (called when phases may have changed externally).
     */
    public void refreshPhaseList() {
        if (strategy != null) {
            loadFrom(strategy);
        }
    }
}
