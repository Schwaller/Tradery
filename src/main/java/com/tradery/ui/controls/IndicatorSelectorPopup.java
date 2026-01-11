package com.tradery.ui.controls;

import com.tradery.ui.ChartsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Popup dialog for selecting indicators and their parameters.
 * Organized by category with checkboxes and parameter inputs.
 */
public class IndicatorSelectorPopup extends JDialog {

    private final ChartsPanel chartPanel;
    private final Runnable onBacktestNeeded;

    // Overlay controls
    private JCheckBox smaCheckbox;
    private JLabel smaLabel;
    private JSpinner smaSpinner;

    private JCheckBox emaCheckbox;
    private JLabel emaLabel;
    private JSpinner emaSpinner;

    private JCheckBox bbCheckbox;
    private JLabel bbPeriodLabel;
    private JSpinner bbPeriodSpinner;
    private JLabel bbStdLabel;
    private JSpinner bbStdSpinner;

    private JCheckBox hlCheckbox;
    private JLabel hlLabel;
    private JSpinner hlSpinner;

    private JCheckBox mayerCheckbox;
    private JLabel mayerLabel;
    private JSpinner mayerSpinner;

    // Oscillator controls
    private JCheckBox rsiCheckbox;
    private JLabel rsiLabel;
    private JSpinner rsiSpinner;

    private JCheckBox macdCheckbox;
    private JSpinner macdFastSpinner;
    private JSpinner macdSlowSpinner;
    private JSpinner macdSignalSpinner;

    private JCheckBox atrCheckbox;
    private JLabel atrLabel;
    private JSpinner atrSpinner;

    // Orderflow controls
    private JCheckBox deltaCheckbox;
    private JCheckBox cvdCheckbox;
    private JCheckBox volumeRatioCheckbox;
    private JLabel whaleLabel;
    private JSpinner whaleThresholdSpinner;

    // Funding checkbox
    private JCheckBox fundingCheckbox;

    // Debounce timer
    private Timer updateTimer;
    private static final int DEBOUNCE_MS = 150;

    public IndicatorSelectorPopup(Window owner, ChartsPanel chartPanel, Runnable onBacktestNeeded) {
        super(owner, "Indicators", ModalityType.MODELESS);
        this.chartPanel = chartPanel;
        this.onBacktestNeeded = onBacktestNeeded;

        setUndecorated(true);
        setResizable(false);

        initComponents();
        initDebounceTimer();
        syncFromChartPanel();
        updateControlVisibility();

        // Close on focus lost
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}

            @Override
            public void windowLostFocus(WindowEvent e) {
                dispose();
            }
        });

        // Close on Escape
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void initComponents() {
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        contentPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        // === OVERLAYS ===
        contentPane.add(createSectionHeader("OVERLAYS"));
        contentPane.add(createSmaRow());
        contentPane.add(createEmaRow());
        contentPane.add(createBollingerRow());
        contentPane.add(createHighLowRow());
        contentPane.add(createMayerRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === CHARTS ===
        contentPane.add(createSectionHeader("CHARTS"));
        contentPane.add(createRsiRow());
        contentPane.add(createMacdRow());
        contentPane.add(createAtrRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === ORDERFLOW ===
        contentPane.add(createSectionHeader("ORDERFLOW"));
        contentPane.add(createDeltaRow());
        contentPane.add(createCvdRow());
        contentPane.add(createVolumeRatioRow());
        contentPane.add(createWhaleThresholdRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === FUNDING ===
        contentPane.add(createSectionHeader("FUNDING"));
        contentPane.add(createFundingRow());

        setContentPane(contentPane);
        pack();
    }

    private JLabel createSectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(4, 0, 4, 0));
        return label;
    }

    private JPanel createSmaRow() {
        smaCheckbox = new JCheckBox("SMA");
        smaLabel = new JLabel("Period:");
        smaSpinner = createPeriodSpinner(20, 5, 200);
        return createIndicatorRow(smaCheckbox, smaLabel, smaSpinner);
    }

    private JPanel createEmaRow() {
        emaCheckbox = new JCheckBox("EMA");
        emaLabel = new JLabel("Period:");
        emaSpinner = createPeriodSpinner(20, 5, 200);
        return createIndicatorRow(emaCheckbox, emaLabel, emaSpinner);
    }

    private JPanel createBollingerRow() {
        bbCheckbox = new JCheckBox("Bollinger");
        bbPeriodLabel = new JLabel("Period:");
        bbPeriodSpinner = createPeriodSpinner(20, 5, 100);
        bbStdLabel = new JLabel("\u03C3:"); // sigma
        bbStdSpinner = createDoubleSpinner(2.0, 0.5, 4.0, 0.5);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(bbCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(bbPeriodLabel);
        row.add(bbPeriodSpinner);
        row.add(bbStdLabel);
        row.add(bbStdSpinner);

        bbCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        bbPeriodSpinner.addChangeListener(e -> scheduleUpdate());
        bbStdSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createHighLowRow() {
        hlCheckbox = new JCheckBox("High/Low");
        hlLabel = new JLabel("Period:");
        hlSpinner = createPeriodSpinner(20, 5, 200);
        return createIndicatorRow(hlCheckbox, hlLabel, hlSpinner);
    }

    private JPanel createMayerRow() {
        mayerCheckbox = new JCheckBox("Mayer Multiple");
        mayerLabel = new JLabel("Period:");
        mayerSpinner = createPeriodSpinner(200, 50, 365);
        return createIndicatorRow(mayerCheckbox, mayerLabel, mayerSpinner);
    }

    private JPanel createRsiRow() {
        rsiCheckbox = new JCheckBox("RSI");
        rsiLabel = new JLabel("Period:");
        rsiSpinner = createPeriodSpinner(14, 2, 50);
        return createIndicatorRow(rsiCheckbox, rsiLabel, rsiSpinner);
    }

    private JPanel createMacdRow() {
        macdCheckbox = new JCheckBox("MACD");
        macdFastSpinner = createPeriodSpinner(12, 2, 50);
        macdSlowSpinner = createPeriodSpinner(26, 5, 100);
        macdSignalSpinner = createPeriodSpinner(9, 2, 50);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(macdCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(macdFastSpinner);
        row.add(macdSlowSpinner);
        row.add(macdSignalSpinner);

        macdCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        macdFastSpinner.addChangeListener(e -> scheduleUpdate());
        macdSlowSpinner.addChangeListener(e -> scheduleUpdate());
        macdSignalSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createAtrRow() {
        atrCheckbox = new JCheckBox("ATR");
        atrLabel = new JLabel("Period:");
        atrSpinner = createPeriodSpinner(14, 2, 50);
        return createIndicatorRow(atrCheckbox, atrLabel, atrSpinner);
    }

    private JPanel createDeltaRow() {
        deltaCheckbox = new JCheckBox("Delta (per bar)");
        deltaCheckbox.setToolTipText("Show per-candle buy-sell volume difference");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(deltaCheckbox);
        deltaCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createCvdRow() {
        cvdCheckbox = new JCheckBox("CVD (cumulative)");
        cvdCheckbox.setToolTipText("Show cumulative volume delta");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(cvdCheckbox);
        cvdCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createVolumeRatioRow() {
        volumeRatioCheckbox = new JCheckBox("Buy/Sell Ratio");
        volumeRatioCheckbox.setToolTipText("Show buy volume as % of total volume");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(volumeRatioCheckbox);
        volumeRatioCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createWhaleThresholdRow() {
        whaleLabel = new JLabel("Whale threshold:");
        whaleThresholdSpinner = new JSpinner(new SpinnerNumberModel(50000, 1000, 1000000, 10000));
        whaleThresholdSpinner.setPreferredSize(new Dimension(80, 24));
        whaleThresholdSpinner.setToolTipText("Min trade size ($) to count as whale");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(whaleLabel);
        row.add(whaleThresholdSpinner);
        whaleThresholdSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createFundingRow() {
        fundingCheckbox = new JCheckBox("Funding Rate");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(fundingCheckbox);
        fundingCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createIndicatorRow(JCheckBox checkbox, JLabel label, JSpinner spinner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);
        row.add(Box.createHorizontalGlue());
        row.add(label);
        row.add(spinner);

        checkbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        spinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JSpinner createPeriodSpinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(55, 24));
        return spinner;
    }

    private JSpinner createDoubleSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(50, 24));
        return spinner;
    }

    private void initDebounceTimer() {
        updateTimer = new Timer(DEBOUNCE_MS, e -> applyChanges());
        updateTimer.setRepeats(false);
    }

    private void scheduleUpdate() {
        if (updateTimer.isRunning()) {
            updateTimer.restart();
        } else {
            updateTimer.start();
        }
    }

    private void updateControlVisibility() {
        // Overlays
        boolean smaEnabled = smaCheckbox.isSelected();
        smaLabel.setVisible(smaEnabled);
        smaSpinner.setVisible(smaEnabled);

        boolean emaEnabled = emaCheckbox.isSelected();
        emaLabel.setVisible(emaEnabled);
        emaSpinner.setVisible(emaEnabled);

        boolean bbEnabled = bbCheckbox.isSelected();
        bbPeriodLabel.setVisible(bbEnabled);
        bbPeriodSpinner.setVisible(bbEnabled);
        bbStdLabel.setVisible(bbEnabled);
        bbStdSpinner.setVisible(bbEnabled);

        boolean hlEnabled = hlCheckbox.isSelected();
        hlLabel.setVisible(hlEnabled);
        hlSpinner.setVisible(hlEnabled);

        boolean mayerEnabled = mayerCheckbox.isSelected();
        mayerLabel.setVisible(mayerEnabled);
        mayerSpinner.setVisible(mayerEnabled);

        // Oscillators
        boolean rsiEnabled = rsiCheckbox.isSelected();
        rsiLabel.setVisible(rsiEnabled);
        rsiSpinner.setVisible(rsiEnabled);

        boolean macdEnabled = macdCheckbox.isSelected();
        macdFastSpinner.setVisible(macdEnabled);
        macdSlowSpinner.setVisible(macdEnabled);
        macdSignalSpinner.setVisible(macdEnabled);

        boolean atrEnabled = atrCheckbox.isSelected();
        atrLabel.setVisible(atrEnabled);
        atrSpinner.setVisible(atrEnabled);

        // Orderflow - whale threshold always visible when any orderflow is enabled
        boolean anyOrderflow = deltaCheckbox.isSelected() || cvdCheckbox.isSelected() || volumeRatioCheckbox.isSelected();
        whaleLabel.setVisible(anyOrderflow);
        whaleThresholdSpinner.setVisible(anyOrderflow);

        // Repack to adjust size
        pack();
    }

    private void syncFromChartPanel() {
        // Overlays
        smaCheckbox.setSelected(chartPanel.isSmaEnabled());
        emaCheckbox.setSelected(chartPanel.isEmaEnabled());
        bbCheckbox.setSelected(chartPanel.isBollingerEnabled());
        hlCheckbox.setSelected(chartPanel.isHighLowEnabled());
        mayerCheckbox.setSelected(chartPanel.isMayerMultipleEnabled());

        // Oscillators
        rsiCheckbox.setSelected(chartPanel.isRsiChartEnabled());
        macdCheckbox.setSelected(chartPanel.isMacdChartEnabled());
        atrCheckbox.setSelected(chartPanel.isAtrChartEnabled());

        // Orderflow
        deltaCheckbox.setSelected(chartPanel.isDeltaChartEnabled());
        cvdCheckbox.setSelected(chartPanel.isCvdChartEnabled());
        volumeRatioCheckbox.setSelected(chartPanel.isVolumeRatioChartEnabled());

        // Funding
        fundingCheckbox.setSelected(chartPanel.isFundingChartEnabled());
    }

    private void applyChanges() {
        // Overlays
        if (smaCheckbox.isSelected()) {
            chartPanel.setSmaOverlay((int) smaSpinner.getValue(), null);
        } else {
            chartPanel.clearSmaOverlay();
        }

        if (emaCheckbox.isSelected()) {
            chartPanel.setEmaOverlay((int) emaSpinner.getValue(), null);
        } else {
            chartPanel.clearEmaOverlay();
        }

        if (bbCheckbox.isSelected()) {
            chartPanel.setBollingerOverlay((int) bbPeriodSpinner.getValue(), (double) bbStdSpinner.getValue(), null);
        } else {
            chartPanel.clearBollingerOverlay();
        }

        if (hlCheckbox.isSelected()) {
            chartPanel.setHighLowOverlay((int) hlSpinner.getValue(), null);
        } else {
            chartPanel.clearHighLowOverlay();
        }

        if (mayerCheckbox.isSelected()) {
            chartPanel.setMayerMultipleEnabled(true, (int) mayerSpinner.getValue());
        } else {
            chartPanel.setMayerMultipleEnabled(false, 200);
        }

        // Oscillators
        chartPanel.setRsiChartEnabled(rsiCheckbox.isSelected(), (int) rsiSpinner.getValue());
        chartPanel.setMacdChartEnabled(macdCheckbox.isSelected(),
            (int) macdFastSpinner.getValue(),
            (int) macdSlowSpinner.getValue(),
            (int) macdSignalSpinner.getValue());
        chartPanel.setAtrChartEnabled(atrCheckbox.isSelected(), (int) atrSpinner.getValue());

        // Orderflow
        double threshold = ((Number) whaleThresholdSpinner.getValue()).doubleValue();
        chartPanel.setDeltaChartEnabled(deltaCheckbox.isSelected(), threshold);
        chartPanel.setCvdChartEnabled(cvdCheckbox.isSelected());
        chartPanel.setVolumeRatioChartEnabled(volumeRatioCheckbox.isSelected());
        chartPanel.setWhaleThreshold(threshold);

        // Funding
        chartPanel.setFundingChartEnabled(fundingCheckbox.isSelected());

        // Trigger backtest if needed (for orderflow/funding data)
        if (onBacktestNeeded != null) {
            onBacktestNeeded.run();
        }
    }

    /**
     * Show the popup below the given component.
     */
    public static void showBelow(Component anchor, ChartsPanel chartPanel, Runnable onBacktestNeeded) {
        Window window = SwingUtilities.getWindowAncestor(anchor);
        IndicatorSelectorPopup popup = new IndicatorSelectorPopup(window, chartPanel, onBacktestNeeded);

        // Position below anchor
        Point loc = anchor.getLocationOnScreen();
        popup.setLocation(loc.x, loc.y + anchor.getHeight());
        popup.setVisible(true);
    }
}
