package com.tradery.forge.ui;

import com.tradery.core.model.Phase;
import com.tradery.forge.TraderyApp;

import javax.swing.*;
import java.awt.*;

/**
 * Separate window for displaying the phase preview chart.
 * Shows historical data with highlighted regions where the phase is active.
 */
public class PhasePreviewWindow extends JFrame {

    private final PhasePreviewChart chart;
    private Phase currentPhase;

    public PhasePreviewWindow() {
        super("Phase Preview - " + TraderyApp.APP_NAME);

        initializeFrame();
        chart = new PhasePreviewChart();
        add(chart, BorderLayout.CENTER);
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 500);
        setMinimumSize(new Dimension(600, 300));
        setLocationRelativeTo(null);

        // macOS styling
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
    }

    /**
     * Update the chart for a phase.
     */
    public void setPhase(Phase phase) {
        this.currentPhase = phase;
        if (phase != null) {
            setTitle("Phase Preview: " + phase.getName() + " - " + TraderyApp.APP_NAME);
        } else {
            setTitle("Phase Preview - " + TraderyApp.APP_NAME);
        }
        chart.setPhase(phase);
    }

    /**
     * Refresh the chart with the current phase.
     */
    public void refresh() {
        chart.setPhase(currentPhase);
    }

    /**
     * Get the current phase being displayed.
     */
    public Phase getPhase() {
        return currentPhase;
    }
}
