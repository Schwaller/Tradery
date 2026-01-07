package com.tradery.ui.controls;

import com.tradery.model.Candle;
import com.tradery.ui.ChartsPanel;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;

/**
 * Panel containing all indicator controls (overlays and chart panels).
 * Extracted from ProjectWindow to reduce its complexity.
 */
public class IndicatorControlsPanel extends JPanel {

    // Overlay indicator controls
    private JCheckBox smaCheckbox;
    private JSlider smaSlider;
    private JSpinner smaSpinner;
    private Timer smaDebounceTimer;

    private JCheckBox emaCheckbox;
    private JSlider emaSlider;
    private JSpinner emaSpinner;
    private Timer emaDebounceTimer;

    private JCheckBox bbCheckbox;
    private JSlider bbSlider;
    private JSpinner bbSpinner;
    private Timer bbDebounceTimer;

    private JCheckBox hlCheckbox;
    private JSlider hlSlider;
    private JSpinner hlSpinner;
    private Timer hlDebounceTimer;

    private JCheckBox mayerCheckbox;

    // Indicator chart panel controls
    private JCheckBox rsiChartCheckbox;
    private JSpinner rsiSpinner;

    private JCheckBox macdChartCheckbox;
    private JSpinner macdFastSpinner;
    private JSpinner macdSlowSpinner;
    private JSpinner macdSignalSpinner;

    private JCheckBox atrChartCheckbox;
    private JSpinner atrSpinner;

    // Reference to charts panel for updating overlays
    private ChartsPanel chartPanel;

    // Callback when backtest needs to refresh
    private Runnable onBacktestNeeded;

    // Current candles for overlay calculation
    private List<Candle> currentCandles;

    private static final int DEBOUNCE_MS = 100;

    public IndicatorControlsPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);
        initializeComponents();
        layoutComponents();
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
        // SMA indicator controls
        smaCheckbox = new JCheckBox("SMA");
        smaCheckbox.setToolTipText("Show Simple Moving Average on price chart");

        smaSlider = new JSlider(5, 200, 50);
        smaSlider.setPreferredSize(new Dimension(100, 20));
        smaSlider.setVisible(false);

        smaSpinner = new JSpinner(new SpinnerNumberModel(50, 5, 500, 1));
        smaSpinner.setPreferredSize(new Dimension(55, 22));
        smaSpinner.setVisible(false);

        smaDebounceTimer = new Timer(DEBOUNCE_MS, e -> updateSmaOverlay());
        smaDebounceTimer.setRepeats(false);

        smaSlider.addChangeListener(e -> {
            smaSpinner.setValue(smaSlider.getValue());
            smaDebounceTimer.restart();
        });
        smaSpinner.addChangeListener(e -> {
            int val = ((Number) smaSpinner.getValue()).intValue();
            if (val >= 5 && val <= 200) {
                smaSlider.setValue(val);
            }
            smaDebounceTimer.restart();
        });

        smaCheckbox.addActionListener(e -> {
            boolean enabled = smaCheckbox.isSelected();
            smaSlider.setVisible(enabled);
            smaSpinner.setVisible(enabled);
            updateSmaOverlay();
        });

        // EMA indicator controls
        emaCheckbox = new JCheckBox("EMA");
        emaCheckbox.setToolTipText("Show Exponential Moving Average on price chart");

        emaSlider = new JSlider(5, 200, 20);
        emaSlider.setPreferredSize(new Dimension(100, 20));
        emaSlider.setVisible(false);

        emaSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 500, 1));
        emaSpinner.setPreferredSize(new Dimension(55, 22));
        emaSpinner.setVisible(false);

        emaDebounceTimer = new Timer(DEBOUNCE_MS, e -> updateEmaOverlay());
        emaDebounceTimer.setRepeats(false);

        emaSlider.addChangeListener(e -> {
            emaSpinner.setValue(emaSlider.getValue());
            emaDebounceTimer.restart();
        });
        emaSpinner.addChangeListener(e -> {
            int val = ((Number) emaSpinner.getValue()).intValue();
            if (val >= 5 && val <= 200) {
                emaSlider.setValue(val);
            }
            emaDebounceTimer.restart();
        });

        emaCheckbox.addActionListener(e -> {
            boolean enabled = emaCheckbox.isSelected();
            emaSlider.setVisible(enabled);
            emaSpinner.setVisible(enabled);
            updateEmaOverlay();
        });

        // Bollinger Bands controls
        bbCheckbox = new JCheckBox("BB");
        bbCheckbox.setToolTipText("Show Bollinger Bands on price chart");

        bbSlider = new JSlider(5, 100, 20);
        bbSlider.setPreferredSize(new Dimension(80, 20));
        bbSlider.setVisible(false);

        bbSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 200, 1));
        bbSpinner.setPreferredSize(new Dimension(50, 22));
        bbSpinner.setVisible(false);

        bbDebounceTimer = new Timer(DEBOUNCE_MS, e -> updateBbOverlay());
        bbDebounceTimer.setRepeats(false);

        bbSlider.addChangeListener(e -> {
            bbSpinner.setValue(bbSlider.getValue());
            bbDebounceTimer.restart();
        });
        bbSpinner.addChangeListener(e -> {
            int val = ((Number) bbSpinner.getValue()).intValue();
            if (val >= 5 && val <= 100) {
                bbSlider.setValue(val);
            }
            bbDebounceTimer.restart();
        });

        bbCheckbox.addActionListener(e -> {
            boolean enabled = bbCheckbox.isSelected();
            bbSlider.setVisible(enabled);
            bbSpinner.setVisible(enabled);
            updateBbOverlay();
        });

        // High/Low controls
        hlCheckbox = new JCheckBox("H/L");
        hlCheckbox.setToolTipText("Show High/Low of period on price chart");

        hlSlider = new JSlider(5, 100, 20);
        hlSlider.setPreferredSize(new Dimension(80, 20));
        hlSlider.setVisible(false);

        hlSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 200, 1));
        hlSpinner.setPreferredSize(new Dimension(50, 22));
        hlSpinner.setVisible(false);

        hlDebounceTimer = new Timer(DEBOUNCE_MS, e -> updateHlOverlay());
        hlDebounceTimer.setRepeats(false);

        hlSlider.addChangeListener(e -> {
            hlSpinner.setValue(hlSlider.getValue());
            hlDebounceTimer.restart();
        });
        hlSpinner.addChangeListener(e -> {
            int val = ((Number) hlSpinner.getValue()).intValue();
            if (val >= 5 && val <= 100) {
                hlSlider.setValue(val);
            }
            hlDebounceTimer.restart();
        });

        hlCheckbox.addActionListener(e -> {
            boolean enabled = hlCheckbox.isSelected();
            hlSlider.setVisible(enabled);
            hlSpinner.setVisible(enabled);
            updateHlOverlay();
        });

        // Mayer Multiple control
        mayerCheckbox = new JCheckBox("Mayer");
        mayerCheckbox.setToolTipText("Color-code price by Mayer Multiple (price/200-SMA)");
        mayerCheckbox.addActionListener(e -> {
            if (chartPanel != null) {
                chartPanel.setMayerMultipleEnabled(mayerCheckbox.isSelected(), 200);
            }
            triggerBacktest();
        });

        // RSI chart panel control
        rsiChartCheckbox = new JCheckBox("RSI");
        rsiChartCheckbox.setToolTipText("Show RSI indicator chart panel");
        rsiSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        rsiSpinner.setPreferredSize(new Dimension(50, 22));
        rsiSpinner.setVisible(false);
        rsiChartCheckbox.addActionListener(e -> {
            boolean enabled = rsiChartCheckbox.isSelected();
            rsiSpinner.setVisible(enabled);
            if (chartPanel != null) {
                int period = ((Number) rsiSpinner.getValue()).intValue();
                chartPanel.setRsiChartEnabled(enabled, period);
            }
            triggerBacktest();
        });
        rsiSpinner.addChangeListener(e -> {
            if (rsiChartCheckbox.isSelected() && chartPanel != null) {
                int period = ((Number) rsiSpinner.getValue()).intValue();
                chartPanel.setRsiChartEnabled(true, period);
                triggerBacktest();
            }
        });

        // MACD chart panel control
        macdChartCheckbox = new JCheckBox("MACD");
        macdChartCheckbox.setToolTipText("Show MACD indicator chart panel");
        macdFastSpinner = new JSpinner(new SpinnerNumberModel(12, 2, 50, 1));
        macdFastSpinner.setPreferredSize(new Dimension(40, 22));
        macdFastSpinner.setVisible(false);
        macdSlowSpinner = new JSpinner(new SpinnerNumberModel(26, 2, 100, 1));
        macdSlowSpinner.setPreferredSize(new Dimension(40, 22));
        macdSlowSpinner.setVisible(false);
        macdSignalSpinner = new JSpinner(new SpinnerNumberModel(9, 2, 50, 1));
        macdSignalSpinner.setPreferredSize(new Dimension(40, 22));
        macdSignalSpinner.setVisible(false);
        macdChartCheckbox.addActionListener(e -> {
            boolean enabled = macdChartCheckbox.isSelected();
            macdFastSpinner.setVisible(enabled);
            macdSlowSpinner.setVisible(enabled);
            macdSignalSpinner.setVisible(enabled);
            updateMacdChart();
        });
        ChangeListener macdChangeListener = e -> {
            if (macdChartCheckbox.isSelected()) {
                updateMacdChart();
            }
        };
        macdFastSpinner.addChangeListener(macdChangeListener);
        macdSlowSpinner.addChangeListener(macdChangeListener);
        macdSignalSpinner.addChangeListener(macdChangeListener);

        // ATR chart panel control
        atrChartCheckbox = new JCheckBox("ATR");
        atrChartCheckbox.setToolTipText("Show ATR volatility chart panel");
        atrSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        atrSpinner.setPreferredSize(new Dimension(50, 22));
        atrSpinner.setVisible(false);
        atrChartCheckbox.addActionListener(e -> {
            boolean enabled = atrChartCheckbox.isSelected();
            atrSpinner.setVisible(enabled);
            if (chartPanel != null) {
                int period = ((Number) atrSpinner.getValue()).intValue();
                chartPanel.setAtrChartEnabled(enabled, period);
            }
            triggerBacktest();
        });
        atrSpinner.addChangeListener(e -> {
            if (atrChartCheckbox.isSelected() && chartPanel != null) {
                int period = ((Number) atrSpinner.getValue()).intValue();
                chartPanel.setAtrChartEnabled(true, period);
                triggerBacktest();
            }
        });
    }

    private void layoutComponents() {
        // Overlay indicators separator
        add(createSeparator());
        add(new JLabel("Overlays:"));

        // SMA controls
        add(smaCheckbox);
        add(smaSlider);
        add(smaSpinner);

        // EMA controls
        add(emaCheckbox);
        add(emaSlider);
        add(emaSpinner);

        // Bollinger Bands controls
        add(bbCheckbox);
        add(bbSlider);
        add(bbSpinner);

        // High/Low controls
        add(hlCheckbox);
        add(hlSlider);
        add(hlSpinner);

        // Mayer Multiple control
        add(mayerCheckbox);

        // Indicator charts separator
        add(createSeparator());
        add(new JLabel("Charts:"));

        // RSI controls
        add(rsiChartCheckbox);
        add(rsiSpinner);

        // MACD controls
        add(macdChartCheckbox);
        add(macdFastSpinner);
        add(macdSlowSpinner);
        add(macdSignalSpinner);

        // ATR controls
        add(atrChartCheckbox);
        add(atrSpinner);
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        return sep;
    }

    private void triggerBacktest() {
        if (onBacktestNeeded != null) {
            onBacktestNeeded.run();
        }
    }

    // ===== Overlay Update Methods =====

    private void updateSmaOverlay() {
        if (chartPanel == null || currentCandles == null || currentCandles.isEmpty()) return;

        if (smaCheckbox.isSelected()) {
            int period = ((Number) smaSpinner.getValue()).intValue();
            chartPanel.setSmaOverlay(period, currentCandles);
        } else {
            chartPanel.clearSmaOverlay();
        }
    }

    private void updateEmaOverlay() {
        if (chartPanel == null || currentCandles == null || currentCandles.isEmpty()) return;

        if (emaCheckbox.isSelected()) {
            int period = ((Number) emaSpinner.getValue()).intValue();
            chartPanel.setEmaOverlay(period, currentCandles);
        } else {
            chartPanel.clearEmaOverlay();
        }
    }

    private void updateBbOverlay() {
        if (chartPanel == null || currentCandles == null || currentCandles.isEmpty()) return;

        if (bbCheckbox.isSelected()) {
            int period = ((Number) bbSpinner.getValue()).intValue();
            chartPanel.setBollingerOverlay(period, 2.0, currentCandles);
        } else {
            chartPanel.clearBollingerOverlay();
        }
    }

    private void updateHlOverlay() {
        if (chartPanel == null || currentCandles == null || currentCandles.isEmpty()) return;

        if (hlCheckbox.isSelected()) {
            int period = ((Number) hlSpinner.getValue()).intValue();
            chartPanel.setHighLowOverlay(period, currentCandles);
        } else {
            chartPanel.clearHighLowOverlay();
        }
    }

    private void updateMacdChart() {
        if (chartPanel == null) return;

        int fast = ((Number) macdFastSpinner.getValue()).intValue();
        int slow = ((Number) macdSlowSpinner.getValue()).intValue();
        int signal = ((Number) macdSignalSpinner.getValue()).intValue();
        chartPanel.setMacdChartEnabled(macdChartCheckbox.isSelected(), fast, slow, signal);
        triggerBacktest();
    }

    /**
     * Refresh all overlay indicators with current candle data.
     * Call this after loading new data or running a backtest.
     */
    public void refreshOverlays() {
        updateSmaOverlay();
        updateEmaOverlay();
        updateBbOverlay();
        updateHlOverlay();
    }

    /**
     * Stop all debounce timers (for cleanup).
     */
    public void stopTimers() {
        if (smaDebounceTimer != null) smaDebounceTimer.stop();
        if (emaDebounceTimer != null) emaDebounceTimer.stop();
        if (bbDebounceTimer != null) bbDebounceTimer.stop();
        if (hlDebounceTimer != null) hlDebounceTimer.stop();
    }
}
