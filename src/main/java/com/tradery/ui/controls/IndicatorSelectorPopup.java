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

    // Overlay controls - Multiple SMA/EMA support
    private JPanel smaOverlaysPanel;
    private JPanel emaOverlaysPanel;

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
    private JLabel floatingPocPeriodLabel;
    private JSpinner floatingPocPeriodSpinner;
    private JCheckBox vwapCheckbox;

    private JCheckBox rayCheckbox;
    private JCheckBox rayNoLimitCheckbox;
    private JCheckBox rayHistoricCheckbox;
    private JLabel rayLookbackLabel;
    private JSlider rayLookbackSlider;
    private JSpinner rayLookbackSpinner;
    private JLabel raySkipLabel;
    private JSlider raySkipSlider;
    private JSpinner raySkipSpinner;

    // Ichimoku Cloud controls
    private JCheckBox ichimokuCheckbox;

    // Daily Volume Profile controls
    private JCheckBox dailyVolumeProfileCheckbox;
    private JLabel dailyVolumeProfileBinsLabel;
    private JSpinner dailyVolumeProfileBinsSpinner;

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

    private JCheckBox adxCheckbox;
    private JLabel adxLabel;
    private JSlider adxSlider;
    private JSpinner adxSpinner;

    // Orderflow controls
    private JCheckBox deltaCheckbox;
    private JCheckBox cvdCheckbox;
    private JCheckBox volumeRatioCheckbox;
    private JCheckBox whaleCheckbox;
    private JCheckBox retailCheckbox;
    private JCheckBox tradeCountCheckbox;
    private JLabel whaleLabel;
    private JSpinner whaleThresholdSpinner;
    private JLabel retailLabel;
    private JSpinner retailThresholdSpinner;

    // Funding checkbox
    private JCheckBox fundingCheckbox;

    // Open Interest checkbox
    private JCheckBox oiCheckbox;

    // Premium Index checkbox
    private JCheckBox premiumCheckbox;

    // Holding Cost checkboxes
    private JCheckBox holdingCostCumulativeCheckbox;
    private JCheckBox holdingCostEventsCheckbox;

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
        contentPane.add(createVwapRow());
        contentPane.add(createRayOverlayRow());
        contentPane.add(createIchimokuRow());
        contentPane.add(createDailyVolumeProfileRow());

        contentPane.add(createSectionSeparator());

        // === CHARTS ===
        contentPane.add(createSectionHeader("INDICATOR CHARTS"));
        contentPane.add(createRsiRow());
        contentPane.add(createMacdRow());
        contentPane.add(createAtrRow());
        contentPane.add(createStochasticRow());
        contentPane.add(createRangePositionRow());
        contentPane.add(createAdxRow());

        contentPane.add(createSectionSeparator());

        // === ORDERFLOW ===
        contentPane.add(createSectionHeader("ORDERFLOW CHARTS"));
        contentPane.add(createDeltaRow());
        contentPane.add(createCvdRow());
        contentPane.add(createVolumeRatioRow());
        contentPane.add(createWhaleRow());
        contentPane.add(createRetailRow());
        contentPane.add(createTradeCountRow());

        contentPane.add(Box.createVerticalStrut(4));

        // === FUNDING ===
        contentPane.add(createSectionHeader("FUNDING"));
        contentPane.add(createFundingRow());

        contentPane.add(Box.createVerticalStrut(4));

        // === OPEN INTEREST ===
        contentPane.add(createSectionHeader("OPEN INTEREST"));
        contentPane.add(createOiRow());

        contentPane.add(Box.createVerticalStrut(4));

        // === PREMIUM INDEX ===
        contentPane.add(createSectionHeader("PREMIUM INDEX"));
        contentPane.add(createPremiumRow());

        contentPane.add(createSectionSeparator());

        // === HOLDING COSTS ===
        contentPane.add(createSectionHeader("HOLDING COSTS"));
        contentPane.add(createHoldingCostCumulativeRow());
        contentPane.add(createHoldingCostEventsRow());

        contentPane.add(createSectionSeparator());

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

    private JPanel createSectionSeparator() {
        JPanel separator = new JPanel();
        separator.setLayout(new BorderLayout());
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        separator.setBorder(new EmptyBorder(8, 0, 4, 0));

        JSeparator line = new JSeparator(SwingConstants.HORIZONTAL);
        separator.add(line, BorderLayout.CENTER);

        return separator;
    }

    private JPanel createSmaRow() {
        smaOverlaysPanel = new JPanel();
        smaOverlaysPanel.setLayout(new BoxLayout(smaOverlaysPanel, BoxLayout.Y_AXIS));
        smaOverlaysPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return smaOverlaysPanel;
    }

    private void rebuildSmaOverlays() {
        smaOverlaysPanel.removeAll();
        java.util.List<Integer> periods = ChartConfig.getInstance().getSmaPeriods();

        // Always show at least one row
        if (periods.isEmpty()) {
            periods = new java.util.ArrayList<>();
            periods.add(20);  // Default period
        }

        for (int i = 0; i < periods.size(); i++) {
            final int index = i;
            final int period = periods.get(index);
            boolean isFirst = (i == 0);
            boolean isLast = (i == periods.size() - 1);

            // Get color from palette (same order as OverlayManager assigns)
            Color color = com.tradery.ui.charts.ChartStyles.OVERLAY_PALETTE[index % com.tradery.ui.charts.ChartStyles.OVERLAY_PALETTE.length];

            smaOverlaysPanel.add(createOverlayRow("SMA", period, isFirst, isLast, color,
                // On checkbox change
                (enabled, newPeriod) -> {
                    if (enabled) {
                        ChartConfig.getInstance().addSmaPeriod(newPeriod);
                    } else {
                        ChartConfig.getInstance().removeSmaPeriod(newPeriod);
                    }
                    scheduleUpdate();
                },
                // On period change
                (oldPeriod, newPeriod) -> {
                    ChartConfig.getInstance().removeSmaPeriod(oldPeriod);
                    ChartConfig.getInstance().addSmaPeriod(newPeriod);
                    scheduleUpdate();
                },
                // On add
                () -> {
                    // Find next available period that doesn't exist
                    int newPeriod = findNextAvailablePeriod(ChartConfig.getInstance().getSmaPeriods());
                    ChartConfig.getInstance().addSmaPeriod(newPeriod);
                    rebuildSmaOverlays();
                    scheduleUpdate();
                },
                // On remove
                () -> {
                    ChartConfig.getInstance().removeSmaPeriod(period);
                    rebuildSmaOverlays();
                    scheduleUpdate();
                }
            ));
        }
        smaOverlaysPanel.revalidate();
        smaOverlaysPanel.repaint();
        pack();
    }

    private JPanel createEmaRow() {
        emaOverlaysPanel = new JPanel();
        emaOverlaysPanel.setLayout(new BoxLayout(emaOverlaysPanel, BoxLayout.Y_AXIS));
        emaOverlaysPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return emaOverlaysPanel;
    }

    private void rebuildEmaOverlays() {
        emaOverlaysPanel.removeAll();
        java.util.List<Integer> periods = ChartConfig.getInstance().getEmaPeriods();

        // Always show at least one row
        if (periods.isEmpty()) {
            periods = new java.util.ArrayList<>();
            periods.add(20);  // Default period
        }

        for (int i = 0; i < periods.size(); i++) {
            final int index = i;
            final int period = periods.get(index);
            boolean isFirst = (i == 0);
            boolean isLast = (i == periods.size() - 1);

            // Get color from palette (offset by SMA count so colors continue)
            int smaCount = ChartConfig.getInstance().getSmaPeriods().size();
            Color color = com.tradery.ui.charts.ChartStyles.OVERLAY_PALETTE[(smaCount + index) % com.tradery.ui.charts.ChartStyles.OVERLAY_PALETTE.length];

            emaOverlaysPanel.add(createOverlayRow("EMA", period, isFirst, isLast, color,
                // On checkbox change
                (enabled, newPeriod) -> {
                    if (enabled) {
                        ChartConfig.getInstance().addEmaPeriod(newPeriod);
                    } else {
                        ChartConfig.getInstance().removeEmaPeriod(newPeriod);
                    }
                    scheduleUpdate();
                },
                // On period change
                (oldPeriod, newPeriod) -> {
                    ChartConfig.getInstance().removeEmaPeriod(oldPeriod);
                    ChartConfig.getInstance().addEmaPeriod(newPeriod);
                    scheduleUpdate();
                },
                // On add
                () -> {
                    // Find next available period that doesn't exist
                    int newPeriod = findNextAvailablePeriod(ChartConfig.getInstance().getEmaPeriods());
                    ChartConfig.getInstance().addEmaPeriod(newPeriod);
                    rebuildEmaOverlays();
                    scheduleUpdate();
                },
                // On remove
                () -> {
                    ChartConfig.getInstance().removeEmaPeriod(period);
                    rebuildEmaOverlays();
                    scheduleUpdate();
                }
            ));
        }
        emaOverlaysPanel.revalidate();
        emaOverlaysPanel.repaint();
        pack();
    }

    private JPanel createOverlayRow(String type, int period, boolean isFirst, boolean isLast,
                                    Color color,
                                    java.util.function.BiConsumer<Boolean, Integer> onCheckboxChange,
                                    java.util.function.BiConsumer<Integer, Integer> onPeriodChange,
                                    Runnable onAdd, Runnable onRemove) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Color swatch
        JPanel colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(12, 12));
        colorBox.setBackground(color);
        colorBox.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        row.add(colorBox);

        // Checkbox with type label on every row
        JCheckBox checkbox = new JCheckBox(type);
        checkbox.setSelected(ChartConfig.getInstance().getSmaPeriods().contains(period) ||
                            ChartConfig.getInstance().getEmaPeriods().contains(period));
        row.add(checkbox);

        // Period label and slider/spinner
        JLabel label = new JLabel("Period:");
        row.add(label);

        Object[] controls = createSliderSpinner(period, 5, 200);
        JSlider slider = (JSlider) controls[0];
        JSpinner spinner = (JSpinner) controls[1];
        row.add(slider);
        row.add(spinner);

        // Track the current period for change detection
        final int[] currentPeriod = {period};

        // Wire up checkbox
        checkbox.addActionListener(e -> {
            int p = (int) spinner.getValue();
            onCheckboxChange.accept(checkbox.isSelected(), p);
        });

        // Wire up period change (on spinner change, debounced)
        spinner.addChangeListener(e -> {
            int newPeriod = (int) spinner.getValue();
            if (newPeriod != currentPeriod[0] && checkbox.isSelected()) {
                onPeriodChange.accept(currentPeriod[0], newPeriod);
                currentPeriod[0] = newPeriod;
            }
        });

        // Add button (always show)
        JButton addBtn = new JButton("+");
        addBtn.setMargin(new Insets(0, 4, 0, 4));
        addBtn.setToolTipText("Add another " + type);
        addBtn.addActionListener(e -> onAdd.run());
        row.add(addBtn);

        // Remove button (only show if not the only row)
        JButton removeBtn = new JButton("-");
        removeBtn.setMargin(new Insets(0, 4, 0, 4));
        removeBtn.setToolTipText("Remove this " + type);
        removeBtn.addActionListener(e -> onRemove.run());
        // Hide remove button if this is the only row
        int totalRows = type.equals("SMA") ?
            Math.max(1, ChartConfig.getInstance().getSmaPeriods().size()) :
            Math.max(1, ChartConfig.getInstance().getEmaPeriods().size());
        removeBtn.setVisible(totalRows > 1);
        row.add(removeBtn);

        return row;
    }

    private int findNextAvailablePeriod(java.util.List<Integer> existingPeriods) {
        // Common periods to try: 20, 50, 100, 200, then increment from 10
        int[] commonPeriods = {20, 50, 100, 200, 10, 15, 25, 30, 40, 60, 80, 150};
        for (int p : commonPeriods) {
            if (!existingPeriods.contains(p)) {
                return p;
            }
        }
        // Fallback: find any period not in use
        for (int p = 5; p <= 500; p++) {
            if (!existingPeriods.contains(p)) {
                return p;
            }
        }
        return 20; // shouldn't happen
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
        dailyPocCheckbox = new JCheckBox("Daily POC/VAH/VAL");
        dailyPocCheckbox.setToolTipText("Show previous day's POC, VAH, VAL (Value Area)");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(dailyPocCheckbox);
        dailyPocCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createFloatingPocRow() {
        floatingPocCheckbox = new JCheckBox("Floating POC/VAH/VAL");
        floatingPocCheckbox.setToolTipText("Show developing POC, VAH, VAL (0=today, N=rolling bars)");
        floatingPocPeriodLabel = new JLabel("Bars:");
        floatingPocPeriodSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 500, 10));
        floatingPocPeriodSpinner.setPreferredSize(new java.awt.Dimension(60, 25));
        floatingPocPeriodSpinner.setToolTipText("0 = today's session, >0 = rolling N-bar lookback");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(floatingPocCheckbox);
        row.add(floatingPocPeriodLabel);
        row.add(floatingPocPeriodSpinner);

        floatingPocCheckbox.addActionListener(e -> {
            boolean enabled = floatingPocCheckbox.isSelected();
            floatingPocPeriodLabel.setEnabled(enabled);
            floatingPocPeriodSpinner.setEnabled(enabled);
            scheduleUpdate();
        });
        floatingPocPeriodSpinner.addChangeListener(e -> scheduleUpdate());

        return row;
    }

    private JPanel createVwapRow() {
        vwapCheckbox = new JCheckBox("VWAP");
        vwapCheckbox.setToolTipText("Volume Weighted Average Price (session)");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(vwapCheckbox);
        vwapCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createRayOverlayRow() {
        rayCheckbox = new JCheckBox("Rotating Rays");
        rayCheckbox.setToolTipText("Show resistance/support rays from ATH/ATL");
        rayNoLimitCheckbox = new JCheckBox("Unlimited");
        rayNoLimitCheckbox.setToolTipText("Search all available history for ATH/ATL (no lookback limit)");
        rayHistoricCheckbox = new JCheckBox("Historic");
        rayHistoricCheckbox.setToolTipText("Show historic rays (how rays looked at past points)");
        rayLookbackLabel = new JLabel("Lookback:");
        Object[] lookbackControls = createSliderSpinner(200, 20, 500);
        rayLookbackSlider = (JSlider) lookbackControls[0];
        rayLookbackSpinner = (JSpinner) lookbackControls[1];
        raySkipLabel = new JLabel("Skip:");
        Object[] skipControls = createSliderSpinner(5, 0, 50);
        raySkipSlider = (JSlider) skipControls[0];
        raySkipSpinner = (JSpinner) skipControls[1];

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(rayCheckbox);
        row.add(Box.createHorizontalGlue());
        row.add(rayNoLimitCheckbox);
        row.add(rayHistoricCheckbox);
        row.add(rayLookbackLabel);
        row.add(rayLookbackSlider);
        row.add(rayLookbackSpinner);
        row.add(raySkipLabel);
        row.add(raySkipSlider);
        row.add(raySkipSpinner);

        rayCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        rayNoLimitCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        rayHistoricCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createIchimokuRow() {
        ichimokuCheckbox = new JCheckBox("Ichimoku Cloud");
        ichimokuCheckbox.setToolTipText("Show Ichimoku Cloud (Tenkan-sen, Kijun-sen, Senkou Span A/B, Chikou Span)");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(ichimokuCheckbox);
        ichimokuCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createDailyVolumeProfileRow() {
        dailyVolumeProfileCheckbox = new JCheckBox("Daily Volume Profile");
        dailyVolumeProfileCheckbox.setToolTipText("Show volume distribution histogram for each day");
        dailyVolumeProfileBinsLabel = new JLabel("Bins:");
        dailyVolumeProfileBinsSpinner = new JSpinner(new SpinnerNumberModel(96, 12, 200, 12));
        dailyVolumeProfileBinsSpinner.setPreferredSize(new Dimension(60, 24));
        dailyVolumeProfileBinsSpinner.addChangeListener(e -> scheduleUpdate());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(dailyVolumeProfileCheckbox);
        row.add(dailyVolumeProfileBinsLabel);
        row.add(dailyVolumeProfileBinsSpinner);
        dailyVolumeProfileCheckbox.addActionListener(e -> scheduleUpdate());
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

    private JPanel createAdxRow() {
        adxCheckbox = new JCheckBox("ADX");
        adxCheckbox.setToolTipText("Average Directional Index with +DI/-DI (trend strength)");
        adxLabel = new JLabel("Period:");
        Object[] controls = createSliderSpinner(14, 2, 50);
        adxSlider = (JSlider) controls[0];
        adxSpinner = (JSpinner) controls[1];
        return createIndicatorRowWithSlider(adxCheckbox, adxLabel, adxSlider, adxSpinner);
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
        retailCheckbox.setToolTipText("Show delta from trades below threshold");
        retailLabel = new JLabel("Max $:");
        retailThresholdSpinner = new JSpinner(new SpinnerNumberModel(50000, 1000, 1000000, 10000));
        retailThresholdSpinner.setPreferredSize(new Dimension(80, 24));
        retailThresholdSpinner.setToolTipText("Max trade size ($) to count as retail");

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(retailCheckbox);
        row.add(retailLabel);
        row.add(retailThresholdSpinner);
        retailCheckbox.addActionListener(e -> {
            updateControlVisibility();
            scheduleUpdate();
        });
        retailThresholdSpinner.addChangeListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createTradeCountRow() {
        tradeCountCheckbox = new JCheckBox("Trade Count");
        tradeCountCheckbox.setToolTipText("Show number of trades per candle");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(tradeCountCheckbox);
        tradeCountCheckbox.addActionListener(e -> scheduleUpdate());
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

    private JPanel createPremiumRow() {
        premiumCheckbox = new JCheckBox("Premium Index");
        premiumCheckbox.setToolTipText("Show futures premium vs spot index (leading indicator)");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(premiumCheckbox);
        premiumCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createHoldingCostCumulativeRow() {
        holdingCostCumulativeCheckbox = new JCheckBox("Cumulative Holding Costs");
        holdingCostCumulativeCheckbox.setToolTipText("Show running total of funding fees/margin interest");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(holdingCostCumulativeCheckbox);
        holdingCostCumulativeCheckbox.addActionListener(e -> scheduleUpdate());
        return row;
    }

    private JPanel createHoldingCostEventsRow() {
        holdingCostEventsCheckbox = new JCheckBox("Holding Cost Events");
        holdingCostEventsCheckbox.setToolTipText("Show individual funding fee/interest charges per trade");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(holdingCostEventsCheckbox);
        holdingCostEventsCheckbox.addActionListener(e -> scheduleUpdate());
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
        // Overlays - SMA/EMA now use chip panels, no visibility toggle needed

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

        boolean rayEnabled = rayCheckbox.isSelected();
        boolean rayNoLimit = rayNoLimitCheckbox.isSelected();
        rayNoLimitCheckbox.setVisible(rayEnabled);
        rayHistoricCheckbox.setVisible(rayEnabled);
        rayLookbackLabel.setVisible(rayEnabled && !rayNoLimit);
        rayLookbackSlider.setVisible(rayEnabled && !rayNoLimit);
        rayLookbackSpinner.setVisible(rayEnabled && !rayNoLimit);
        raySkipLabel.setVisible(rayEnabled);
        raySkipSlider.setVisible(rayEnabled);
        raySkipSpinner.setVisible(rayEnabled);

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

        // Orderflow - retail threshold visible only when retail checkbox is enabled
        boolean retailEnabled = retailCheckbox.isSelected();
        retailLabel.setVisible(retailEnabled);
        retailThresholdSpinner.setVisible(retailEnabled);

        // Repack to adjust size
        pack();
    }

    private void syncFromChartPanel() {
        ChartConfig config = ChartConfig.getInstance();

        // Overlays - rebuild chips from config
        rebuildSmaOverlays();
        rebuildEmaOverlays();

        bbCheckbox.setSelected(config.isBollingerEnabled());
        bbPeriodSpinner.setValue(config.getBollingerPeriod());
        bbStdSpinner.setValue(config.getBollingerStdDev());
        hlCheckbox.setSelected(config.isHighLowEnabled());
        hlSpinner.setValue(config.getHighLowPeriod());
        mayerCheckbox.setSelected(config.isMayerEnabled());
        mayerSpinner.setValue(config.getMayerPeriod());
        dailyPocCheckbox.setSelected(config.isDailyPocEnabled());
        floatingPocCheckbox.setSelected(config.isFloatingPocEnabled());
        floatingPocPeriodSpinner.setValue(config.getFloatingPocPeriod());
        boolean floatingPocEnabled = config.isFloatingPocEnabled();
        floatingPocPeriodLabel.setEnabled(floatingPocEnabled);
        floatingPocPeriodSpinner.setEnabled(floatingPocEnabled);
        vwapCheckbox.setSelected(config.isVwapEnabled());
        rayCheckbox.setSelected(config.isRayOverlayEnabled());
        int rayLookback = config.getRayLookback();
        rayNoLimitCheckbox.setSelected(rayLookback == 0);
        rayHistoricCheckbox.setSelected(config.isRayHistoricEnabled());
        rayLookbackSpinner.setValue(rayLookback == 0 ? 200 : rayLookback);  // Show 200 as default when switching from no-limit
        raySkipSpinner.setValue(config.getRaySkip());
        ichimokuCheckbox.setSelected(config.isIchimokuEnabled());
        dailyVolumeProfileCheckbox.setSelected(config.isDailyVolumeProfileEnabled());
        dailyVolumeProfileBinsSpinner.setValue(config.getDailyVolumeProfileBins());

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
        adxCheckbox.setSelected(config.isAdxEnabled());
        adxSpinner.setValue(config.getAdxPeriod());

        // Orderflow
        deltaCheckbox.setSelected(config.isDeltaEnabled());
        cvdCheckbox.setSelected(config.isCvdEnabled());
        volumeRatioCheckbox.setSelected(config.isVolumeRatioEnabled());
        whaleCheckbox.setSelected(config.isWhaleEnabled());
        retailCheckbox.setSelected(config.isRetailEnabled());
        tradeCountCheckbox.setSelected(config.isTradeCountEnabled());
        whaleThresholdSpinner.setValue((int) config.getWhaleThreshold());
        retailThresholdSpinner.setValue((int) config.getRetailThreshold());

        // Funding
        fundingCheckbox.setSelected(config.isFundingEnabled());

        // Open Interest
        oiCheckbox.setSelected(config.isOiEnabled());

        // Premium Index
        premiumCheckbox.setSelected(config.isPremiumEnabled());

        // Holding Costs
        holdingCostCumulativeCheckbox.setSelected(config.isHoldingCostCumulativeEnabled());
        holdingCostEventsCheckbox.setSelected(config.isHoldingCostEventsEnabled());

        // Core charts
        volumeChartCheckbox.setSelected(config.isVolumeChartEnabled());
        equityChartCheckbox.setSelected(config.isEquityChartEnabled());
        comparisonChartCheckbox.setSelected(config.isComparisonChartEnabled());
        capitalUsageChartCheckbox.setSelected(config.isCapitalUsageChartEnabled());
        tradePLChartCheckbox.setSelected(config.isTradePLChartEnabled());
    }

    private void applyChanges() {
        ChartConfig config = ChartConfig.getInstance();

        // Overlays - SMA/EMA are managed via ChartConfig in rebuildSmaOverlays/rebuildEmaOverlays
        int bbPeriod = (int) bbPeriodSpinner.getValue();
        double bbStd = (double) bbStdSpinner.getValue();
        int hlPeriod = (int) hlSpinner.getValue();
        int mayerPeriod = (int) mayerSpinner.getValue();

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
            chartPanel.setFloatingPocOverlay(null, (int) floatingPocPeriodSpinner.getValue());
        } else {
            chartPanel.clearFloatingPocOverlay();
        }

        if (vwapCheckbox.isSelected()) {
            chartPanel.setVwapOverlay(null);
        } else {
            chartPanel.clearVwapOverlay();
        }

        // Save overlay settings to config (SMA/EMA managed via chips)
        config.setBollingerEnabled(bbCheckbox.isSelected());
        config.setBollingerPeriod(bbPeriod);
        config.setBollingerStdDev(bbStd);
        config.setHighLowEnabled(hlCheckbox.isSelected());
        config.setHighLowPeriod(hlPeriod);
        config.setMayerEnabled(mayerCheckbox.isSelected());
        config.setMayerPeriod(mayerPeriod);
        config.setDailyPocEnabled(dailyPocCheckbox.isSelected());
        config.setFloatingPocEnabled(floatingPocCheckbox.isSelected());
        config.setFloatingPocPeriod((int) floatingPocPeriodSpinner.getValue());
        config.setVwapEnabled(vwapCheckbox.isSelected());

        // Ray overlay
        int rayLookback = rayNoLimitCheckbox.isSelected() ? 0 : (int) rayLookbackSpinner.getValue();
        int raySkip = (int) raySkipSpinner.getValue();
        boolean rayHistoric = rayHistoricCheckbox.isSelected();
        if (rayCheckbox.isSelected()) {
            chartPanel.setRayOverlay(true, rayLookback, raySkip);
            chartPanel.setRayShowHistoric(rayHistoric);
        } else {
            chartPanel.clearRayOverlay();
        }
        config.setRayOverlayEnabled(rayCheckbox.isSelected());
        config.setRayLookback(rayLookback);
        config.setRaySkip(raySkip);
        config.setRayHistoricEnabled(rayHistoric);

        // Ichimoku Cloud
        if (ichimokuCheckbox.isSelected()) {
            chartPanel.setIchimokuOverlay(
                config.getIchimokuConversionPeriod(),
                config.getIchimokuBasePeriod(),
                config.getIchimokuSpanBPeriod(),
                config.getIchimokuDisplacement()
            );
        } else {
            chartPanel.clearIchimokuOverlay();
        }
        config.setIchimokuEnabled(ichimokuCheckbox.isSelected());

        // Daily Volume Profile
        int volumeProfileBins = (int) dailyVolumeProfileBinsSpinner.getValue();
        if (dailyVolumeProfileCheckbox.isSelected()) {
            chartPanel.setDailyVolumeProfileOverlay(
                null,  // Will use current candles
                volumeProfileBins,
                70.0,  // Value area percentage
                config.getDailyVolumeProfileWidth()
            );
        } else {
            chartPanel.clearDailyVolumeProfileOverlay();
        }
        config.setDailyVolumeProfileEnabled(dailyVolumeProfileCheckbox.isSelected());
        config.setDailyVolumeProfileBins(volumeProfileBins);

        // Oscillators
        int rsiPeriod = (int) rsiSpinner.getValue();
        int macdFast = (int) macdFastSpinner.getValue();
        int macdSlow = (int) macdSlowSpinner.getValue();
        int macdSignal = (int) macdSignalSpinner.getValue();
        int atrPeriod = (int) atrSpinner.getValue();

        int stochasticK = (int) stochasticKSpinner.getValue();
        int stochasticD = (int) stochasticDSpinner.getValue();
        int rangePositionPeriod = (int) rangePositionSpinner.getValue();
        int adxPeriod = (int) adxSpinner.getValue();

        chartPanel.setRsiChartEnabled(rsiCheckbox.isSelected(), rsiPeriod);
        chartPanel.setMacdChartEnabled(macdCheckbox.isSelected(), macdFast, macdSlow, macdSignal);
        chartPanel.setAtrChartEnabled(atrCheckbox.isSelected(), atrPeriod);
        chartPanel.setStochasticChartEnabled(stochasticCheckbox.isSelected(), stochasticK, stochasticD);
        chartPanel.setRangePositionChartEnabled(rangePositionCheckbox.isSelected(), rangePositionPeriod);
        chartPanel.setAdxChartEnabled(adxCheckbox.isSelected(), adxPeriod);

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
        config.setAdxEnabled(adxCheckbox.isSelected());
        config.setAdxPeriod(adxPeriod);

        // Orderflow
        double threshold = ((Number) whaleThresholdSpinner.getValue()).doubleValue();
        chartPanel.setWhaleThreshold(threshold);
        chartPanel.setDeltaChartEnabled(deltaCheckbox.isSelected());
        chartPanel.setCvdChartEnabled(cvdCheckbox.isSelected());
        chartPanel.setVolumeRatioChartEnabled(volumeRatioCheckbox.isSelected());
        chartPanel.setWhaleChartEnabled(whaleCheckbox.isSelected(), threshold);
        double retailThreshold = ((Number) retailThresholdSpinner.getValue()).doubleValue();
        chartPanel.setRetailThreshold(retailThreshold);
        chartPanel.setRetailChartEnabled(retailCheckbox.isSelected(), retailThreshold);
        chartPanel.setTradeCountChartEnabled(tradeCountCheckbox.isSelected());

        // Save orderflow settings to config
        config.setDeltaEnabled(deltaCheckbox.isSelected());
        config.setCvdEnabled(cvdCheckbox.isSelected());
        config.setVolumeRatioEnabled(volumeRatioCheckbox.isSelected());
        config.setWhaleEnabled(whaleCheckbox.isSelected());
        config.setRetailEnabled(retailCheckbox.isSelected());
        config.setTradeCountEnabled(tradeCountCheckbox.isSelected());
        config.setWhaleThreshold(threshold);
        config.setRetailThreshold(retailThreshold);

        // Funding
        chartPanel.setFundingChartEnabled(fundingCheckbox.isSelected());
        config.setFundingEnabled(fundingCheckbox.isSelected());

        // Open Interest
        chartPanel.setOiChartEnabled(oiCheckbox.isSelected());
        config.setOiEnabled(oiCheckbox.isSelected());

        // Premium Index
        chartPanel.setPremiumChartEnabled(premiumCheckbox.isSelected());
        config.setPremiumEnabled(premiumCheckbox.isSelected());

        // Holding Costs
        chartPanel.setHoldingCostCumulativeChartEnabled(holdingCostCumulativeCheckbox.isSelected());
        config.setHoldingCostCumulativeEnabled(holdingCostCumulativeCheckbox.isSelected());
        chartPanel.setHoldingCostEventsChartEnabled(holdingCostEventsCheckbox.isSelected());
        config.setHoldingCostEventsEnabled(holdingCostEventsCheckbox.isSelected());

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
