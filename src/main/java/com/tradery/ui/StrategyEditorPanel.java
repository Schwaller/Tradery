package com.tradery.ui;

import com.tradery.model.Strategy;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for editing a single strategy's DSL conditions and trade management settings.
 * Composes EntryConfigPanel and ExitConfigPanel with a separator between them.
 */
public class StrategyEditorPanel extends JPanel {

    private TradeSettingsPanel tradeSettingsPanel;
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
        entryConfigPanel = new EntryConfigPanel();
        exitConfigPanel = new ExitConfigPanel();

        // Add padding to sub-panels
        tradeSettingsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        entryConfigPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        exitConfigPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Wire up change listeners
        tradeSettingsPanel.setOnChange(this::fireChange);
        entryConfigPanel.setOnChange(this::fireChange);
        exitConfigPanel.setOnChange(this::fireChange);
    }

    private void layoutComponents() {
        // Top section: trade settings (fixed height)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(tradeSettingsPanel, BorderLayout.NORTH);
        topPanel.add(new JSeparator(), BorderLayout.SOUTH);

        // Center: entry and exit panels side by side
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 0, 0));
        centerPanel.setOpaque(false);

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
        entryConfigPanel.loadFrom(strategy);
        exitConfigPanel.loadFrom(strategy);
    }

    /**
     * Apply current UI values to the strategy
     */
    public void applyToStrategy(Strategy strategy) {
        if (strategy == null) return;
        tradeSettingsPanel.applyTo(strategy);
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
