package com.tradery.ui;

import com.tradery.io.PhaseStore;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Phase selection panel for strategy-level entry filtering.
 * Wraps PhaseListPanel and adds strategy load/apply functionality.
 */
public class PhaseSelectionPanel extends JPanel {

    private final PhaseStore phaseStore;
    private final PhaseListPanel phaseListPanel;
    private Strategy strategy;
    private Runnable onChange;
    private boolean suppressChangeEvents = false;

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
        suppressChangeEvents = true;
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
            suppressChangeEvents = false;
        }
    }

    public void applyTo(Strategy strategy) {
        if (strategy == null) return;
        strategy.setRequiredPhaseIds(new ArrayList<>(phaseListPanel.getRequiredPhaseIds()));
        strategy.setExcludedPhaseIds(new ArrayList<>(phaseListPanel.getExcludedPhaseIds()));
    }

    private void fireChange() {
        if (!suppressChangeEvents && onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
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
