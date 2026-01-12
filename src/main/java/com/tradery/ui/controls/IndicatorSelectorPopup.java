package com.tradery.ui.controls;

import com.tradery.ui.ChartsPanel;
import com.tradery.ui.charts.ChartConfig;

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
    private JSlider smaSlider;
    private JSpinner smaSpinner;

    private JCheckBox emaCheckbox;
    private JLabel emaLabel;
    private JSlider emaSlider;
    private JSpinner emaSpinner;

    private JCheckBox bbCheckbox;
    private JLabel bbPeriodLabel;
    private JSlider bbPeriodSlider;
    private JSpinner bbPeriodSpinner;
    private JLabel bbStdLabel;
    private JSpinner bbStdSpinner;

    private JCheckBox hlCheckbox;
    private JLabel hlLabel;
    private JSlider hlSlider;
    private JSpinner hlSpinner;

    private JCheckBox mayerCheckbox;
    private JLabel mayerLabel;
    private JSlider mayerSlider;
    private JSpinner mayerSpinner;

    private JCheckBox dailyPocCheckbox;
    private JCheckBox floatingPocCheckbox;

    // Oscillator controls
    private JCheckBox rsiCheckbox;
    private JLabel rsiLabel;
    private JSlider rsiSlider;
    private JSpinner rsiSpinner;

    private JCheckBox macdCheckbox;
    private JSlider macdFastSlider;
    private JSpinner macdFastSpinner;
    private JSlider macdSlowSlider;
    private JSpinner macdSlowSpinner;
    private JSlider macdSignalSlider;
    private JSpinner macdSignalSpinner;

    private JCheckBox atrCheckbox;
    private JLabel atrLabel;
    private JSlider atrSlider;
    private JSpinner atrSpinner;

    private JCheckBox stochasticCheckbox;
    private JLabel stochasticKLabel;
    private JSlider stochasticKSlider;
    private JSpinner stochasticKSpinner;
    private JLabel stochasticDLabel;
    private JSlider stochasticDSlider;
    private JSpinner stochasticDSpinner;

    private JCheckBox rangePositionCheckbox;
    private JLabel rangePositionLabel;
    private JSlider rangePositionSlider;
    private JSpinner rangePositionSpinner;

    // Orderflow controls
    private JCheckBox deltaCheckbox;
    private JCheckBox cvdCheckbox;
    private JCheckBox volumeRatioCheckbox;
    private JCheckBox whaleCheckbox;
    private JCheckBox retailCheckbox;
    private JLabel whaleLabel;
    private JSpinner whaleThresholdSpinner;

    // Funding checkbox
    private JCheckBox fundingCheckbox;

    // Open Interest checkbox
    private JCheckBox oiCheckbox;

    // Core chart checkboxes
    private JCheckBox volumeChartCheckbox;
    private JCheckBox equityChartCheckbox;
    private JCheckBox comparisonChartCheckbox;
    private JCheckBox capitalUsageChartCheckbox;
    private JCheckBox tradePLChartCheckbox;

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

        // Close on focus lost (with delay to handle focus transfer to spinners)
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}

            @Override
            public void windowLostFocus(WindowEvent e) {
                // Small delay to check if focus went to a child component
                SwingUtilities.invokeLater(() -> {
                    Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focusedWindow != IndicatorSelectorPopup.this) {
                        dispose();
                    }
                });
            }
        });

        // Close on Escape
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Add global mouse listener to close on click outside
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
                Point clickPoint = me.getLocationOnScreen();
                Rectangle popupBounds = getBounds();
                if (isVisible() && !popupBounds.contains(clickPoint)) {
                    dispose();
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
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
        contentPane.add(createDailyPocRow());
        contentPane.add(createFloatingPocRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === CHARTS ===
        contentPane.add(createSectionHeader("CHARTS"));
        contentPane.add(createRsiRow());
        contentPane.add(createMacdRow());
        contentPane.add(createAtrRow());
        contentPane.add(createStochasticRow());
        contentPane.add(createRangePositionRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === ORDERFLOW ===
        contentPane.add(createSectionHeader("ORDERFLOW"));
        contentPane.add(createDeltaRow());
        contentPane.add(createCvdRow());
        contentPane.add(createVolumeRatioRow());
        contentPane.add(createWhaleRow());
        contentPane.add(createRetailRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === FUNDING ===
        contentPane.add(createSectionHeader("FUNDING"));
        contentPane.add(createFundingRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === OPEN INTEREST ===
        contentPane.add(createSectionHeader("OPEN INTEREST"));
        contentPane.add(createOiRow());

        contentPane.add(Box.createVerticalStrut(8));

        // === CORE CHARTS ===
        contentPane.add(createSectionHeader("CORE CHARTS"));
        contentPane.add(createVolumeChartRow());
        contentPane.add(createEquityChartRow());
        contentPane.add(createComparisonChartRow());
        contentPane.add(createCapitalUsageChartRow());
        contentPane.add(createTradePLChartRow());

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
        Object[] controls = createSliderSpinner(20, 5, 200);
        smaSlider = (JSlider) controls[0];
        smaSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(smaCheckbox, smaLabel, smaSlider, smaSpinner);
    }

    private JPanel createEmaRow() {
        emaCheckbox = new JCheckBox("EMA");
        emaLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(20, 5, 200);
        emaSlider = (JSlider) controls[0];
        emaSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(emaCheckbox, emaLabel, emaSlider, emaSpinner);
    }

    private JPanel createBollingerRow() {
        bbCheckbox = new JCheckBox("Bollinger");
        bbPeriodLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(20, 5, 100);
        bbPeriodSlider = (JSlider) controls[0];
        bbPeriodSpinner = (JSpinner) controls[1];
        bbStdLabel = new JLabel("\u03C3:"); // sigma
        bbStdSpinner = createDoubleSpinner(2.0, 0.5, 4.0, 0.5);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(bbCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(bbPeriodLabel);
        row.add(bbPeriodSlider);
        row.add(bbPeriodSpinner);
        row.add(bbStdLabel);
        row.add(bbStdSpinner);

        bbCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        bbStdSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createHighLowRow() {
        hlCheckbox = new JCheckBox("High/Low");
        hlLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(20, 5, 200);
        hlSlider = (JSlider) controls[0];
        hlSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(hlCheckbox, hlLabel, hlSlider, hlSpinner);
    }

    private JPanel createMayerRow() {
        mayerCheckbox = new JCheckBox("Mayer Multiple");
        mayerLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(200, 50, 365);
        mayerSlider = (JSlider) controls[0];
        mayerSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(mayerCheckbox, mayerLabel, mayerSlider, mayerSpinner);
    }

    private JPanel createDailyPocRow() {
        dailyPocCheckbox = new JCheckBox("Daily POC");
        dailyPocCheckbox.setToolTipText("Show previous day's Point of Control");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(dailyPocCheckbox);
        dailyPocCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createFloatingPocRow() {
        floatingPocCheckbox = new JCheckBox("Floating POC");
        floatingPocCheckbox.setToolTipText("Show developing Point of Control for current day");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(floatingPocCheckbox);
        floatingPocCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createRsiRow() {
        rsiCheckbox = new JCheckBox("RSI");
        rsiLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(14, 2, 50);
        rsiSlider = (JSlider) controls[0];
        rsiSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(rsiCheckbox, rsiLabel, rsiSlider, rsiSpinner);
    }

    private JPanel createMacdRow() {
        macdCheckbox = new JCheckBox("MACD");
        Object[] fastControls = createSliderSpinner(12, 2, 50);
        macdFastSlider = (JSlider) fastControls[0];
        macdFastSpinner = (JSpinner) fastControls[1];
        Object[] slowControls = createSliderSpinner(26, 5, 100);
        macdSlowSlider = (JSlider) slowControls[0];
        macdSlowSpinner = (JSpinner) slowControls[1];
        Object[] signalControls = createSliderSpinner(9, 2, 50);
        macdSignalSlider = (JSlider) signalControls[0];
        macdSignalSpinner = (JSpinner) signalControls[1];

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(macdCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(macdFastSlider);
        row.add(macdFastSpinner);
        row.add(macdSlowSlider);
        row.add(macdSlowSpinner);
        row.add(macdSignalSlider);
        row.add(macdSignalSpinner);

        macdCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        return row;
    }

    private JPanel createAtrRow() {
        atrCheckbox = new JCheckBox("ATR");
        atrLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(14, 2, 50);
        atrSlider = (JSlider) controls[0];
        atrSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(atrCheckbox, atrLabel, atrSlider, atrSpinner);
    }

    private JPanel createStochasticRow() {
        stochasticCheckbox = new JCheckBox("Stochastic");
        stochasticKLabel = new JLabel("K:");
        Object[] kControls = createSliderSpinner(14, 2, 50);
        stochasticKSlider = (JSlider) kControls[0];
        stochasticKSpinner = (JSpinner) kControls[1];
        stochasticKSpinner.setToolTipText("%K period");
        stochasticDLabel = new JLabel("D:");
        Object[] dControls = createSliderSpinner(3, 1, 20);
        stochasticDSlider = (JSlider) dControls[0];
        stochasticDSpinner = (JSpinner) dControls[1];
        stochasticDSpinner.setToolTipText("%D smoothing period");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(stochasticCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(stochasticKLabel);
        row.add(stochasticKSlider);
        row.add(stochasticKSpinner);
        row.add(stochasticDLabel);
        row.add(stochasticDSlider);
        row.add(stochasticDSpinner);

        stochasticCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        return row;
    }

    private JPanel createRangePositionRow() {
        rangePositionCheckbox = new JCheckBox("Range Position");
        rangePositionCheckbox.setToolTipText("Shows position within range (-1 to +1), extends beyond for breakouts");
        rangePositionLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(200, 5, 500);
        rangePositionSlider = (JSlider) controls[0];
        rangePositionSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(rangePositionCheckbox, rangePositionLabel, rangePositionSlider, rangePositionSpinner);
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
        volumeRatioCheckbox = new JCheckBox("Buy/Sell Volume");
        volumeRatioCheckbox.setToolTipText("Show buy/sell volume divergence around zero line");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(volumeRatioCheckbox);
        volumeRatioCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createWhaleRow() {
        whaleCheckbox = new JCheckBox("Whale Delta");
        whaleCheckbox.setToolTipText("Show delta from large trades only");
        whaleLabel = new JLabel("Min $:");
        whaleThresholdSpinner = new JSpinner(new SpinnerNumberModel(50000, 1000, 1000000, 10000));
        whaleThresholdSpinner.setPreferredSize(new Dimension(80, 24));
        whaleThresholdSpinner.setToolTipText("Min trade size ($) to count as whale");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(whaleCheckbox);
        row.add(whaleLabel);
        row.add(whaleThresholdSpinner);
        whaleCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        whaleThresholdSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createRetailRow() {
        retailCheckbox = new JCheckBox("Retail Delta");
        retailCheckbox.setToolTipText("Show delta from trades below whale threshold");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(retailCheckbox);
        retailCheckbox.addActionListener(e -> scheduleUpdate());
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

    private JPanel createOiRow() {
        oiCheckbox = new JCheckBox("Open Interest");
        oiCheckbox.setToolTipText("Show OI value and change chart (Binance 5m data)");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(oiCheckbox);
        oiCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createVolumeChartRow() {
        volumeChartCheckbox = new JCheckBox("Volume");
        volumeChartCheckbox.setToolTipText("Show volume chart");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(volumeChartCheckbox);
        volumeChartCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createEquityChartRow() {
        equityChartCheckbox = new JCheckBox("Equity");
        equityChartCheckbox.setToolTipText("Show portfolio equity chart");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(equityChartCheckbox);
        equityChartCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createComparisonChartRow() {
        comparisonChartCheckbox = new JCheckBox("Strategy vs Buy & Hold");
        comparisonChartCheckbox.setToolTipText("Show strategy comparison chart");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(comparisonChartCheckbox);
        comparisonChartCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createCapitalUsageChartRow() {
        capitalUsageChartCheckbox = new JCheckBox("Capital Usage");
        capitalUsageChartCheckbox.setToolTipText("Show capital usage percentage chart");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(capitalUsageChartCheckbox);
        capitalUsageChartCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createTradePLChartRow() {
        tradePLChartCheckbox = new JCheckBox("Trade P&L");
        tradePLChartCheckbox.setToolTipText("Show individual trade P&L chart");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(tradePLChartCheckbox);
        tradePLChartCheckbox.addActionListener(e -> scheduleUpdate());
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

    private JPanel createIndicatorRowWithSlider(JCheckBox checkbox, JLabel label, JSlider slider, JSpinner spinner) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(checkbox);
        row.add(Box.createHorizontalGlue());
        row.add(label);
        row.add(slider);
        row.add(spinner);

        checkbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
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

    /**
     * Creates a synchronized slider+spinner pair.
     * Returns array: [JSlider, JSpinner]
     */
    private Object[] createSliderSpinner(int value, int min, int max) {
        JSlider slider = new JSlider(min, max, value);
        slider.setPreferredSize(new Dimension(80, 20));
        slider.setFocusable(false);

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        spinner.setPreferredSize(new Dimension(55, 24));

        // Synchronize slider -> spinner and trigger updates while dragging
        slider.addChangeListener(e -> {
            int sliderValue = slider.getValue();
            if ((int) spinner.getValue() != sliderValue) {
                spinner.setValue(sliderValue);
            }
            // Always schedule update (debounce handles rate limiting)
            scheduleUpdate();
        });

        // Synchronize spinner -> slider
        spinner.addChangeListener(e -> {
            int spinnerValue = (int) spinner.getValue();
            if (slider.getValue() != spinnerValue) {
                slider.setValue(spinnerValue);
            }
            scheduleUpdate();
        });

        return new Object[]{slider, spinner};
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
        smaSlider.setVisible(smaEnabled);
        smaSpinner.setVisible(smaEnabled);

        boolean emaEnabled = emaCheckbox.isSelected();
        emaLabel.setVisible(emaEnabled);
        emaSlider.setVisible(emaEnabled);
        emaSpinner.setVisible(emaEnabled);

        boolean bbEnabled = bbCheckbox.isSelected();
        bbPeriodLabel.setVisible(bbEnabled);
        bbPeriodSlider.setVisible(bbEnabled);
        bbPeriodSpinner.setVisible(bbEnabled);
        bbStdLabel.setVisible(bbEnabled);
        bbStdSpinner.setVisible(bbEnabled);

        boolean hlEnabled = hlCheckbox.isSelected();
        hlLabel.setVisible(hlEnabled);
        hlSlider.setVisible(hlEnabled);
        hlSpinner.setVisible(hlEnabled);

        boolean mayerEnabled = mayerCheckbox.isSelected();
        mayerLabel.setVisible(mayerEnabled);
        mayerSlider.setVisible(mayerEnabled);
        mayerSpinner.setVisible(mayerEnabled);

        // Oscillators
        boolean rsiEnabled = rsiCheckbox.isSelected();
        rsiLabel.setVisible(rsiEnabled);
        rsiSlider.setVisible(rsiEnabled);
        rsiSpinner.setVisible(rsiEnabled);

        boolean macdEnabled = macdCheckbox.isSelected();
        macdFastSlider.setVisible(macdEnabled);
        macdFastSpinner.setVisible(macdEnabled);
        macdSlowSlider.setVisible(macdEnabled);
        macdSlowSpinner.setVisible(macdEnabled);
        macdSignalSlider.setVisible(macdEnabled);
        macdSignalSpinner.setVisible(macdEnabled);

        boolean atrEnabled = atrCheckbox.isSelected();
        atrLabel.setVisible(atrEnabled);
        atrSlider.setVisible(atrEnabled);
        atrSpinner.setVisible(atrEnabled);

        boolean stochasticEnabled = stochasticCheckbox.isSelected();
        stochasticKLabel.setVisible(stochasticEnabled);
        stochasticKSlider.setVisible(stochasticEnabled);
        stochasticKSpinner.setVisible(stochasticEnabled);
        stochasticDLabel.setVisible(stochasticEnabled);
        stochasticDSlider.setVisible(stochasticEnabled);
        stochasticDSpinner.setVisible(stochasticEnabled);

        boolean rangePositionEnabled = rangePositionCheckbox.isSelected();
        rangePositionLabel.setVisible(rangePositionEnabled);
        rangePositionSlider.setVisible(rangePositionEnabled);
        rangePositionSpinner.setVisible(rangePositionEnabled);

        // Orderflow - whale threshold visible only when whale checkbox is enabled
        boolean whaleEnabled = whaleCheckbox.isSelected();
        whaleLabel.setVisible(whaleEnabled);
        whaleThresholdSpinner.setVisible(whaleEnabled);

        // Repack to adjust size
        pack();
    }

    private void syncFromChartPanel() {
        ChartConfig config = ChartConfig.getInstance();

        // Overlays - use ChartConfig for state and parameters
        smaCheckbox.setSelected(config.isSmaEnabled());
        smaSpinner.setValue(config.getSmaPeriod());
        emaCheckbox.setSelected(config.isEmaEnabled());
        emaSpinner.setValue(config.getEmaPeriod());
        bbCheckbox.setSelected(config.isBollingerEnabled());
        bbPeriodSpinner.setValue(config.getBollingerPeriod());
        bbStdSpinner.setValue(config.getBollingerStdDev());
        hlCheckbox.setSelected(config.isHighLowEnabled());
        hlSpinner.setValue(config.getHighLowPeriod());
        mayerCheckbox.setSelected(config.isMayerEnabled());
        mayerSpinner.setValue(config.getMayerPeriod());
        dailyPocCheckbox.setSelected(config.isDailyPocEnabled());
        floatingPocCheckbox.setSelected(config.isFloatingPocEnabled());

        // Oscillators
        rsiCheckbox.setSelected(config.isRsiEnabled());
        rsiSpinner.setValue(config.getRsiPeriod());
        macdCheckbox.setSelected(config.isMacdEnabled());
        macdFastSpinner.setValue(config.getMacdFast());
        macdSlowSpinner.setValue(config.getMacdSlow());
        macdSignalSpinner.setValue(config.getMacdSignal());
        atrCheckbox.setSelected(config.isAtrEnabled());
        atrSpinner.setValue(config.getAtrPeriod());
        stochasticCheckbox.setSelected(config.isStochasticEnabled());
        stochasticKSpinner.setValue(config.getStochasticKPeriod());
        stochasticDSpinner.setValue(config.getStochasticDPeriod());
        rangePositionCheckbox.setSelected(config.isRangePositionEnabled());
        rangePositionSpinner.setValue(config.getRangePositionPeriod());

        // Orderflow
        deltaCheckbox.setSelected(config.isDeltaEnabled());
        cvdCheckbox.setSelected(config.isCvdEnabled());
        volumeRatioCheckbox.setSelected(config.isVolumeRatioEnabled());
        whaleCheckbox.setSelected(config.isWhaleEnabled());
        retailCheckbox.setSelected(config.isRetailEnabled());
        whaleThresholdSpinner.setValue((int) config.getWhaleThreshold());

        // Funding
        fundingCheckbox.setSelected(config.isFundingEnabled());

        // Open Interest
        oiCheckbox.setSelected(config.isOiEnabled());

        // Core charts
        volumeChartCheckbox.setSelected(config.isVolumeChartEnabled());
        equityChartCheckbox.setSelected(config.isEquityChartEnabled());
        comparisonChartCheckbox.setSelected(config.isComparisonChartEnabled());
        capitalUsageChartCheckbox.setSelected(config.isCapitalUsageChartEnabled());
        tradePLChartCheckbox.setSelected(config.isTradePLChartEnabled());
    }

    private void applyChanges() {
        ChartConfig config = ChartConfig.getInstance();

        // Overlays
        int smaPeriod = (int) smaSpinner.getValue();
        int emaPeriod = (int) emaSpinner.getValue();
        int bbPeriod = (int) bbPeriodSpinner.getValue();
        double bbStd = (double) bbStdSpinner.getValue();
        int hlPeriod = (int) hlSpinner.getValue();
        int mayerPeriod = (int) mayerSpinner.getValue();

        if (smaCheckbox.isSelected()) {
            chartPanel.setSmaOverlay(smaPeriod, null);
        } else {
            chartPanel.clearSmaOverlay();
        }

        if (emaCheckbox.isSelected()) {
            chartPanel.setEmaOverlay(emaPeriod, null);
        } else {
            chartPanel.clearEmaOverlay();
        }

        if (bbCheckbox.isSelected()) {
            chartPanel.setBollingerOverlay(bbPeriod, bbStd, null);
        } else {
            chartPanel.clearBollingerOverlay();
        }

        if (hlCheckbox.isSelected()) {
            chartPanel.setHighLowOverlay(hlPeriod, null);
        } else {
            chartPanel.clearHighLowOverlay();
        }

        if (mayerCheckbox.isSelected()) {
            chartPanel.setMayerMultipleEnabled(true, mayerPeriod);
        } else {
            chartPanel.setMayerMultipleEnabled(false, 200);
        }

        if (dailyPocCheckbox.isSelected()) {
            chartPanel.setDailyPocOverlay(null);
        } else {
            chartPanel.clearDailyPocOverlay();
        }

        if (floatingPocCheckbox.isSelected()) {
            chartPanel.setFloatingPocOverlay(null);
        } else {
            chartPanel.clearFloatingPocOverlay();
        }

        // Save overlay settings to config
        config.setSmaEnabled(smaCheckbox.isSelected());
        config.setSmaPeriod(smaPeriod);
        config.setEmaEnabled(emaCheckbox.isSelected());
        config.setEmaPeriod(emaPeriod);
        config.setBollingerEnabled(bbCheckbox.isSelected());
        config.setBollingerPeriod(bbPeriod);
        config.setBollingerStdDev(bbStd);
        config.setHighLowEnabled(hlCheckbox.isSelected());
        config.setHighLowPeriod(hlPeriod);
        config.setMayerEnabled(mayerCheckbox.isSelected());
        config.setMayerPeriod(mayerPeriod);
        config.setDailyPocEnabled(dailyPocCheckbox.isSelected());
        config.setFloatingPocEnabled(floatingPocCheckbox.isSelected());

        // Oscillators
        int rsiPeriod = (int) rsiSpinner.getValue();
        int macdFast = (int) macdFastSpinner.getValue();
        int macdSlow = (int) macdSlowSpinner.getValue();
        int macdSignal = (int) macdSignalSpinner.getValue();
        int atrPeriod = (int) atrSpinner.getValue();

        int stochasticK = (int) stochasticKSpinner.getValue();
        int stochasticD = (int) stochasticDSpinner.getValue();
        int rangePositionPeriod = (int) rangePositionSpinner.getValue();

        chartPanel.setRsiChartEnabled(rsiCheckbox.isSelected(), rsiPeriod);
        chartPanel.setMacdChartEnabled(macdCheckbox.isSelected(), macdFast, macdSlow, macdSignal);
        chartPanel.setAtrChartEnabled(atrCheckbox.isSelected(), atrPeriod);
        chartPanel.setStochasticChartEnabled(stochasticCheckbox.isSelected(), stochasticK, stochasticD);
        chartPanel.setRangePositionChartEnabled(rangePositionCheckbox.isSelected(), rangePositionPeriod);

        // Save indicator settings to config
        config.setRsiEnabled(rsiCheckbox.isSelected());
        config.setRsiPeriod(rsiPeriod);
        config.setMacdEnabled(macdCheckbox.isSelected());
        config.setMacdFast(macdFast);
        config.setMacdSlow(macdSlow);
        config.setMacdSignal(macdSignal);
        config.setAtrEnabled(atrCheckbox.isSelected());
        config.setAtrPeriod(atrPeriod);
        config.setStochasticEnabled(stochasticCheckbox.isSelected());
        config.setStochasticKPeriod(stochasticK);
        config.setStochasticDPeriod(stochasticD);
        config.setRangePositionEnabled(rangePositionCheckbox.isSelected());
        config.setRangePositionPeriod(rangePositionPeriod);

        // Orderflow
        double threshold = ((Number) whaleThresholdSpinner.getValue()).doubleValue();
        chartPanel.setWhaleThreshold(threshold);
        chartPanel.setDeltaChartEnabled(deltaCheckbox.isSelected());
        chartPanel.setCvdChartEnabled(cvdCheckbox.isSelected());
        chartPanel.setVolumeRatioChartEnabled(volumeRatioCheckbox.isSelected());
        chartPanel.setWhaleChartEnabled(whaleCheckbox.isSelected(), threshold);
        chartPanel.setRetailChartEnabled(retailCheckbox.isSelected());

        // Save orderflow settings to config
        config.setDeltaEnabled(deltaCheckbox.isSelected());
        config.setCvdEnabled(cvdCheckbox.isSelected());
        config.setVolumeRatioEnabled(volumeRatioCheckbox.isSelected());
        config.setWhaleEnabled(whaleCheckbox.isSelected());
        config.setRetailEnabled(retailCheckbox.isSelected());
        config.setWhaleThreshold(threshold);

        // Funding
        chartPanel.setFundingChartEnabled(fundingCheckbox.isSelected());
        config.setFundingEnabled(fundingCheckbox.isSelected());

        // Open Interest
        chartPanel.setOiChartEnabled(oiCheckbox.isSelected());
        config.setOiEnabled(oiCheckbox.isSelected());

        // Core charts
        chartPanel.setVolumeChartEnabled(volumeChartCheckbox.isSelected());
        chartPanel.setEquityChartEnabled(equityChartCheckbox.isSelected());
        chartPanel.setComparisonChartEnabled(comparisonChartCheckbox.isSelected());
        chartPanel.setCapitalUsageChartEnabled(capitalUsageChartCheckbox.isSelected());
        chartPanel.setTradePLChartEnabled(tradePLChartCheckbox.isSelected());

        // Save core chart settings to config
        config.setVolumeChartEnabled(volumeChartCheckbox.isSelected());
        config.setEquityChartEnabled(equityChartCheckbox.isSelected());
        config.setComparisonChartEnabled(comparisonChartCheckbox.isSelected());
        config.setCapitalUsageChartEnabled(capitalUsageChartCheckbox.isSelected());
        config.setTradePLChartEnabled(tradePLChartCheckbox.isSelected());

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
