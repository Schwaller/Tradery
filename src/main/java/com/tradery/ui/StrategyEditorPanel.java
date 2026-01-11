package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing a single strategy's DSL conditions and trade management settings.
 * Composes EntryConfigPanel and ExitConfigPanel with a separator between them.
 */
public class StrategyEditorPanel extends JPanel {

    private TradeSettingsPanel tradeSettingsPanel;
    private PhaseSelectionPanel phaseSelectionPanel;
    private HoopPatternSelectionPanel hoopPatternSelectionPanel;
    private FlowDiagramPanel flowDiagramPanel;
    private EntryConfigPanel entryConfigPanel;
    private ExitConfigPanel exitConfigPanel;

    private Strategy strategy;
    private Runnable onChange;

    public StrategyEditorPanel() {
        setLayout(new BorderLayout());

        initializeComponents();
        layoutComponents();
    }

    private void initializeComponents() {
        tradeSettingsPanel = new TradeSettingsPanel();
        phaseSelectionPanel = new PhaseSelectionPanel(
            ApplicationContext.getInstance().getPhaseStore()
        );
        hoopPatternSelectionPanel = new HoopPatternSelectionPanel();
        flowDiagramPanel = new FlowDiagramPanel();
        entryConfigPanel = new EntryConfigPanel();
        exitConfigPanel = new ExitConfigPanel();

        // Add padding to sub-panels
        tradeSettingsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        phaseSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        hoopPatternSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        flowDiagramPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        entryConfigPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        exitConfigPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Wire up change listeners
        tradeSettingsPanel.setOnChange(this::fireChange);
        phaseSelectionPanel.setOnChange(this::fireChange);
        hoopPatternSelectionPanel.setOnChange(this::fireChange);
        entryConfigPanel.setOnChange(this::onEntryExitChange);
        exitConfigPanel.setOnChange(this::onEntryExitChange);
    }

    private void onEntryExitChange() {
        // Update flow diagram when entry/exit changes
        if (strategy != null) {
            exitConfigPanel.applyTo(strategy);
            flowDiagramPanel.setStrategy(strategy);
        }
        fireChange();
    }

    private void layoutComponents() {
        // Top section: trade settings + flow diagram
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        topPanel.add(tradeSettingsPanel, BorderLayout.NORTH);

        // Flow diagram between settings and entry/exit
        JPanel flowWrapper = new JPanel(new BorderLayout());
        flowWrapper.setOpaque(false);
        flowWrapper.add(new JSeparator(), BorderLayout.NORTH);
        flowWrapper.add(flowDiagramPanel, BorderLayout.CENTER);
        flowWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        topPanel.add(flowWrapper, BorderLayout.SOUTH);

        // Center: entry and exit panels side by side (50/50)
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 0, 0));
        centerPanel.setOpaque(false);

        // Entry panel with phase and hoop pattern selection injected
        entryConfigPanel.setPhaseSelectionPanel(phaseSelectionPanel);
        entryConfigPanel.setHoopPatternSelectionPanel(hoopPatternSelectionPanel);

        // Entry with separator on right
        JPanel entryWrapper = new JPanel(new BorderLayout());
        entryWrapper.setOpaque(false);
        entryWrapper.add(entryConfigPanel, BorderLayout.CENTER);
        entryWrapper.add(new JSeparator(JSeparator.VERTICAL), BorderLayout.EAST);

        centerPanel.add(entryWrapper);
        centerPanel.add(exitConfigPanel);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Set the strategy to edit
     */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
        tradeSettingsPanel.loadFrom(strategy);
        phaseSelectionPanel.loadFrom(strategy);
        hoopPatternSelectionPanel.loadFrom(strategy);
        flowDiagramPanel.setStrategy(strategy);
        entryConfigPanel.loadFrom(strategy);
        exitConfigPanel.loadFrom(strategy);
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;
        tradeSettingsPanel.applyTo(strategy);
        phaseSelectionPanel.applyTo(strategy);
        hoopPatternSelectionPanel.applyTo(strategy);
        entryConfigPanel.applyTo(strategy);
        exitConfigPanel.applyTo(strategy);
    }

    /**
     * Get the current strategy with UI values applied
     */
    public Strategy getStrategy() {
        if (strategy != null) {
            applyToStrategy(strategy);
        }
        return strategy;
    }
}
