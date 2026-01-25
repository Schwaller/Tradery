package com.tradery.forge.ui.controls;

import com.tradery.core.model.Candle;
import com.tradery.forge.ui.ChartsPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel containing indicator controls.
 * Shows a single "Indicators" button that opens a popup for configuring all indicators.
 */
public class IndicatorControlsPanel extends JPanel {

    private JButton indicatorsButton;

    // Reference to charts panel for updating overlays
    private ChartsPanel chartPanel;

    // Callback when backtest needs to refresh
    private Runnable onBacktestNeeded;

    // Current candles for overlay calculation
    private List<Candle> currentCandles;

    public IndicatorControlsPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);
        initializeComponents();
    }

    public void setChartPanel(ChartsPanel chartPanel) {
        this.chartPanel = chartPanel;
    }

    public void setOnBacktestNeeded(Runnable callback) {
        this.onBacktestNeeded = callback;
    }

    public void setCurrentCandles(List<Candle> candles) {
        this.currentCandles = candles;
    }

    private void initializeComponents() {
        // Single "Indicators" button with dropdown arrow
        indicatorsButton = new JButton("Indicators ▾");
        indicatorsButton.setToolTipText("Configure chart overlays and indicator panels");
        indicatorsButton.addActionListener(e -> showIndicatorPopup());
        add(indicatorsButton);
    }

    private void showIndicatorPopup() {
        if (chartPanel == null) return;
        IndicatorSelectorPopup.showBelow(indicatorsButton, chartPanel, onBacktestNeeded);
    }

    /**
     * Refresh all overlay indicators with current candle data.
     * Call this after loading new data or running a backtest.
     */
    public void refreshOverlays() {
        // Overlays are now managed directly by ChartsPanel
        // This method is kept for backward compatibility
    }

    /**
     * Stop all timers (for cleanup).
     */
    public void stopTimers() {
        // No timers to stop - popup handles its own debouncing
    }
}
