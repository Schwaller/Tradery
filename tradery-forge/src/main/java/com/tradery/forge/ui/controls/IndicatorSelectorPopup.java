package com.tradery.forge.ui.controls;

import com.tradery.forge.ui.ChartsPanel;
import com.tradery.forge.ui.charts.ChartConfig;
import com.tradery.ui.controls.IndicatorSelectorPanel;
import com.tradery.ui.controls.indicators.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.List;

/**
 * Popup dialog for selecting indicators and their parameters.
 * Uses shared indicator row components from tradery-ui-common.
 */
public class IndicatorSelectorPopup extends JDialog {

    private final ChartsPanel chartPanel;
    private final Runnable onBacktestNeeded;

    // Overlays
    private final DynamicOverlayPanel smaPanel;
    private final DynamicOverlayPanel emaPanel;
    private final PeriodMultiplierRow bb = new PeriodMultiplierRow("Bollinger", null, 20, 5, 100, "\u03C3:", 2.0, 0.5, 4.0, 0.5);
    private final PeriodIndicatorRow hl = new PeriodIndicatorRow("High/Low", 20, 5, 200);
    private final PeriodIndicatorRow mayer = new PeriodIndicatorRow("Mayer Multiple", 200, 50, 365);
    private final IndicatorToggleRow dailyPoc = new IndicatorToggleRow("Daily POC/VAH/VAL", "Show previous day's POC, VAH, VAL (Value Area)");
    private final FloatingPocRow floatingPoc = new FloatingPocRow();
    private final IndicatorToggleRow vwap = new IndicatorToggleRow("VWAP", "Volume Weighted Average Price (session)");
    private final PivotPointsRow pivotPoints = new PivotPointsRow();
    private final PeriodMultiplierRow atrBands = new PeriodMultiplierRow("ATR Bands", "Volatility bands based on ATR (close \u00B1 ATR \u00D7 multiplier)", 14, 5, 50, "\u00D7", 2.0, 0.5, 5.0, 0.5);
    private final PeriodMultiplierRow supertrend = new PeriodMultiplierRow("Supertrend", "Trend-following overlay that changes color based on trend direction", 10, 5, 50, "\u00D7", 3.0, 1.0, 5.0, 0.5);
    private final KeltnerRow keltner = new KeltnerRow();
    private final DonchianRow donchian = new DonchianRow();
    private final RayRow rays = new RayRow();
    private final IndicatorToggleRow ichimoku = new IndicatorToggleRow("Ichimoku Cloud", "Show Ichimoku Cloud (Tenkan-sen, Kijun-sen, Senkou Span A/B, Chikou Span)");
    private final DailyVolumeProfileRow dailyVolumeProfile = new DailyVolumeProfileRow();

    // Footprint Heatmap controls (complex, kept inline)
    private JCheckBox footprintHeatmapCheckbox;
    private JToggleButton footprintSplitButton;
    private JToggleButton footprintDeltaButton;
    private ButtonGroup footprintViewGroup;
    private JToggleButton footprintAuto10Button;
    private JToggleButton footprintAuto20Button;
    private JToggleButton footprintAuto40Button;
    private JToggleButton[] footprintGridButtons = new JToggleButton[4];
    private ButtonGroup footprintBucketGroup;
    private double[] currentGridOptions = new double[4];

    private static final double[] NICE_TICKS = {
        0.01, 0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000
    };

    // Oscillators
    private final PeriodIndicatorRow rsi = new PeriodIndicatorRow("RSI", 14, 2, 50);
    private final MacdRow macd = new MacdRow();
    private final PeriodIndicatorRow atr = new PeriodIndicatorRow("ATR", 14, 2, 50);
    private final StochasticRow stochastic = new StochasticRow();
    private final PeriodIndicatorRow rangePosition = new PeriodIndicatorRow("Range Position", "Shows position within range (-1 to +1), extends beyond for breakouts", 200, 5, 500);
    private final PeriodIndicatorRow adx = new PeriodIndicatorRow("ADX", "Average Directional Index with +DI/-DI (trend strength)", 14, 2, 50);

    // Orderflow
    private final IndicatorToggleRow delta = new IndicatorToggleRow("Delta (per bar)", "Show per-candle buy-sell volume difference");
    private final IndicatorToggleRow cvd = new IndicatorToggleRow("CVD (cumulative)", "Show cumulative volume delta");
    private final IndicatorToggleRow volumeRatio = new IndicatorToggleRow("Buy/Sell Volume", "Show buy/sell volume divergence around zero line");
    private final ThresholdRow whale = new ThresholdRow("Whale Delta", "Show delta from large trades only", "Min $:", 50000);
    private final ThresholdRow retail = new ThresholdRow("Retail Delta", "Show delta from trades below threshold", "Max $:", 50000);
    private final IndicatorToggleRow tradeCount = new IndicatorToggleRow("Trade Count", "Show number of trades per candle");

    // Funding / OI / Premium / Holding Costs
    private final IndicatorToggleRow funding = new IndicatorToggleRow("Funding Rate");
    private final IndicatorToggleRow oi = new IndicatorToggleRow("Open Interest", "Show OI value and change chart (Binance 5m data)");
    private final IndicatorToggleRow premium = new IndicatorToggleRow("Premium Index", "Show futures premium vs spot index (leading indicator)");
    private final IndicatorToggleRow holdingCostCumulative = new IndicatorToggleRow("Cumulative Holding Costs", "Show running total of funding fees/margin interest");
    private final IndicatorToggleRow holdingCostEvents = new IndicatorToggleRow("Holding Cost Events", "Show individual funding fee/interest charges per trade");

    // Core charts
    private final IndicatorToggleRow volumeChart = new IndicatorToggleRow("Volume", "Show volume chart");
    private final IndicatorToggleRow equityChart = new IndicatorToggleRow("Equity", "Show portfolio equity chart");
    private final IndicatorToggleRow comparisonChart = new IndicatorToggleRow("Strategy vs Buy & Hold", "Show strategy comparison chart");
    private final IndicatorToggleRow capitalUsageChart = new IndicatorToggleRow("Capital Usage", "Show capital usage percentage chart");
    private final IndicatorToggleRow tradePLChart = new IndicatorToggleRow("Trade P&L", "Show individual trade P&L chart");

    // Debounce timer
    private Timer updateTimer;
    private static final int DEBOUNCE_MS = 150;
    private boolean initializing = true;

    public IndicatorSelectorPopup(Window owner, ChartsPanel chartPanel, Runnable onBacktestNeeded) {
        super(owner, "Indicators", ModalityType.MODELESS);
        this.chartPanel = chartPanel;
        this.onBacktestNeeded = onBacktestNeeded;

        // Create dynamic overlay panels with Forge's color palette
        Color[] palette = com.tradery.forge.ui.charts.ChartStyles.OVERLAY_PALETTE;
        smaPanel = new DynamicOverlayPanel("SMA", 20, 5, 200, palette);
        emaPanel = new DynamicOverlayPanel("EMA", 20, 5, 200, palette);
        smaPanel.setRepackListener(this::pack);
        emaPanel.setRepackListener(this::pack);

        setUndecorated(true);
        setResizable(false);

        initComponents();
        initDebounceTimer();
        wireChangeListeners();
        syncFromChartPanel();

        initializing = false;

        // Close on focus lost
        addWindowFocusListener(new WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) {}
            @Override public void windowLostFocus(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    Window focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focusedWindow != IndicatorSelectorPopup.this) {
                        flushPendingChanges();
                        dispose();
                    }
                });
            }
        });

        // Close on Escape
        getRootPane().registerKeyboardAction(
            e -> { flushPendingChanges(); dispose(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Close on click outside
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
                Point clickPoint = me.getLocationOnScreen();
                Rectangle popupBounds = getBounds();
                if (isVisible() && !popupBounds.contains(clickPoint)) {
                    flushPendingChanges();
                    dispose();
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void flushPendingChanges() {
        if (updateTimer.isRunning()) {
            updateTimer.stop();
            applyChanges();
        }
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
        contentPane.add(smaPanel);
        contentPane.add(emaPanel);
        for (JPanel row : new JPanel[]{bb, hl, mayer, dailyPoc, floatingPoc, vwap, pivotPoints,
                atrBands, supertrend, keltner, donchian, rays, ichimoku, dailyVolumeProfile}) {
            contentPane.add(row);
        }
        contentPane.add(createFootprintHeatmapRow());

        contentPane.add(createSectionSeparator());

        // === INDICATOR CHARTS ===
        contentPane.add(createSectionHeader("INDICATOR CHARTS"));
        for (JPanel row : new JPanel[]{rsi, macd, atr, stochastic, rangePosition, adx}) {
            contentPane.add(row);
        }

        contentPane.add(createSectionSeparator());

        // === ORDERFLOW ===
        contentPane.add(createSectionHeader("ORDERFLOW CHARTS"));
        for (JPanel row : new JPanel[]{delta, cvd, volumeRatio, whale, retail, tradeCount}) {
            contentPane.add(row);
        }

        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(createSectionHeader("FUNDING"));
        contentPane.add(funding);

        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(createSectionHeader("OPEN INTEREST"));
        contentPane.add(oi);

        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(createSectionHeader("PREMIUM INDEX"));
        contentPane.add(premium);

        contentPane.add(createSectionSeparator());
        contentPane.add(createSectionHeader("HOLDING COSTS"));
        contentPane.add(holdingCostCumulative);
        contentPane.add(holdingCostEvents);

        contentPane.add(createSectionSeparator());
        contentPane.add(createSectionHeader("CORE CHARTS"));
        for (JPanel row : new JPanel[]{volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart}) {
            contentPane.add(row);
        }

        JScrollPane scrollPane = new JScrollPane(contentPane);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        setContentPane(scrollPane);
        pack();

        // Cap height to available screen space
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc != null) {
            Rectangle screenBounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxHeight = screenBounds.height - insets.top - insets.bottom - 40;
            if (getHeight() > maxHeight) {
                setSize(getWidth() + scrollPane.getVerticalScrollBar().getPreferredSize().width, maxHeight);
            }
        }
    }

    private void wireChangeListeners() {
        // All shared components fire scheduleUpdate on change
        Runnable update = this::scheduleUpdate;

        smaPanel.addChangeListener(update);
        emaPanel.addChangeListener(update);
        bb.addChangeListener(update);
        hl.addChangeListener(update);
        mayer.addChangeListener(update);
        dailyPoc.addChangeListener(update);
        floatingPoc.addChangeListener(update);
        vwap.addChangeListener(update);
        pivotPoints.addChangeListener(update);
        atrBands.addChangeListener(update);
        supertrend.addChangeListener(update);
        keltner.addChangeListener(update);
        donchian.addChangeListener(update);
        rays.addChangeListener(update);
        ichimoku.addChangeListener(update);
        dailyVolumeProfile.addChangeListener(update);
        rsi.addChangeListener(update);
        macd.addChangeListener(update);
        atr.addChangeListener(update);
        stochastic.addChangeListener(update);
        rangePosition.addChangeListener(update);
        adx.addChangeListener(update);
        delta.addChangeListener(update);
        cvd.addChangeListener(update);
        volumeRatio.addChangeListener(update);
        whale.addChangeListener(update);
        retail.addChangeListener(update);
        tradeCount.addChangeListener(update);
        funding.addChangeListener(update);
        oi.addChangeListener(update);
        premium.addChangeListener(update);
        holdingCostCumulative.addChangeListener(update);
        holdingCostEvents.addChangeListener(update);
        volumeChart.addChangeListener(update);
        equityChart.addChangeListener(update);
        comparisonChart.addChangeListener(update);
        capitalUsageChart.addChangeListener(update);
        tradePLChart.addChangeListener(update);
    }

    // ===== Footprint Heatmap (complex, kept inline) =====

    private JPanel createFootprintHeatmapRow() {
        footprintHeatmapCheckbox = new JCheckBox("Footprint");
        footprintHeatmapCheckbox.setToolTipText("Show price-level volume heatmap (requires aggTrades data)");

        footprintSplitButton = new JToggleButton("Split");
        footprintSplitButton.setToolTipText("Split view: buy volume left (green), sell volume right (red)");
        footprintSplitButton.setPreferredSize(new Dimension(50, 22));
        footprintSplitButton.setMargin(new Insets(1, 4, 1, 4));

        footprintDeltaButton = new JToggleButton("Delta");
        footprintDeltaButton.setToolTipText("Show net delta (buy - sell) as single color");
        footprintDeltaButton.setPreferredSize(new Dimension(50, 22));
        footprintDeltaButton.setMargin(new Insets(1, 4, 1, 4));

        footprintViewGroup = new ButtonGroup();
        footprintViewGroup.add(footprintSplitButton);
        footprintViewGroup.add(footprintDeltaButton);

        footprintBucketGroup = new ButtonGroup();
        footprintAuto10Button = createFootprintBucketButton("Auto(10)", "Fine detail - ~10 buckets per candle");
        footprintAuto20Button = createFootprintBucketButton("Auto(20)", "Medium detail - ~20 buckets per candle (default)");
        footprintAuto40Button = createFootprintBucketButton("Auto(40)", "Coarse view - ~40 buckets per candle");
        footprintBucketGroup.add(footprintAuto10Button);
        footprintBucketGroup.add(footprintAuto20Button);
        footprintBucketGroup.add(footprintAuto40Button);

        for (int i = 0; i < 4; i++) {
            footprintGridButtons[i] = createFootprintBucketButton("$--", "Fixed grid tick size");
            footprintBucketGroup.add(footprintGridButtons[i]);
        }

        footprintSplitButton.addActionListener(e -> { if (footprintSplitButton.isSelected()) scheduleUpdate(); });
        footprintDeltaButton.addActionListener(e -> { if (footprintDeltaButton.isSelected()) scheduleUpdate(); });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(footprintHeatmapCheckbox);
        row.add(footprintSplitButton);
        row.add(footprintDeltaButton);
        row.add(Box.createHorizontalStrut(4));
        row.add(footprintAuto10Button);
        row.add(footprintAuto20Button);
        row.add(footprintAuto40Button);
        for (JToggleButton btn : footprintGridButtons) row.add(btn);

        footprintHeatmapCheckbox.addActionListener(e -> { updateFootprintControlVisibility(); scheduleUpdate(); });
        return row;
    }

    private JToggleButton createFootprintBucketButton(String text, String tooltip) {
        JToggleButton btn = new JToggleButton(text);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(text.length() > 6 ? 62 : 48, 22));
        btn.setMargin(new Insets(1, 2, 1, 2));
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.addActionListener(e -> { if (btn.isSelected()) scheduleUpdate(); });
        return btn;
    }

    private double[] computeTickSizeOptions(List<com.tradery.core.model.Candle> candles) {
        double[] result = new double[4];
        if (candles == null || candles.isEmpty()) {
            result[0] = 25; result[1] = 50; result[2] = 100; result[3] = 250;
            return result;
        }
        int lookback = Math.min(14, candles.size());
        double sumRange = 0;
        for (int i = candles.size() - lookback; i < candles.size(); i++) {
            var c = candles.get(i);
            sumRange += c.high() - c.low();
        }
        double avgRange = sumRange / lookback;
        double idealTick = avgRange / 20;
        int idealIdx = 0;
        double minDiff = Math.abs(NICE_TICKS[0] - idealTick);
        for (int i = 1; i < NICE_TICKS.length; i++) {
            double diff = Math.abs(NICE_TICKS[i] - idealTick);
            if (diff < minDiff) { minDiff = diff; idealIdx = i; }
        }
        int startIdx = Math.max(0, Math.min(idealIdx - 1, NICE_TICKS.length - 4));
        for (int i = 0; i < 4; i++) {
            int idx = startIdx + i;
            result[i] = idx < NICE_TICKS.length ? NICE_TICKS[idx] : NICE_TICKS[NICE_TICKS.length - 1];
        }
        return result;
    }

    private String formatTickSize(double tick) {
        if (tick >= 1000) return String.format("$%.0fk", tick / 1000);
        else if (tick >= 1) return tick == Math.floor(tick) ? String.format("$%.0f", tick) : String.format("$%.1f", tick);
        else if (tick >= 0.01) return tick * 100 == Math.floor(tick * 100) ? String.format("$%.2f", tick) : String.format("$%.3f", tick);
        else return String.format("$%.4f", tick);
    }

    private void updateFootprintTickButtons() {
        List<com.tradery.core.model.Candle> candles = chartPanel.getCurrentCandles();
        currentGridOptions = computeTickSizeOptions(candles);
        for (int i = 0; i < 4; i++) {
            String label = formatTickSize(currentGridOptions[i]);
            footprintGridButtons[i].setText(label);
            footprintGridButtons[i].setToolTipText("Fixed grid: " + label + " tick size");
            footprintGridButtons[i].setPreferredSize(new Dimension(label.length() > 5 ? 52 : 44, 22));
        }
    }

    private void updateFootprintControlVisibility() {
        boolean enabled = footprintHeatmapCheckbox.isSelected();
        footprintSplitButton.setVisible(enabled);
        footprintDeltaButton.setVisible(enabled);
        footprintAuto10Button.setVisible(enabled);
        footprintAuto20Button.setVisible(enabled);
        footprintAuto40Button.setVisible(enabled);
        for (JToggleButton btn : footprintGridButtons) btn.setVisible(enabled);
    }

    // ===== Section helpers =====

    private JLabel createSectionHeader(String text) {
        return IndicatorSelectorPanel.createSectionHeader(text);
    }

    private JPanel createSectionSeparator() {
        return IndicatorSelectorPanel.createSectionSeparator();
    }

    // ===== Debounce =====

    private void initDebounceTimer() {
        updateTimer = new Timer(DEBOUNCE_MS, e -> applyChanges());
        updateTimer.setRepeats(false);
    }

    private void scheduleUpdate() {
        if (initializing) return;
        if (updateTimer.isRunning()) updateTimer.restart();
        else updateTimer.start();
    }

    // ===== Sync from config =====

    private void syncFromChartPanel() {
        ChartConfig config = ChartConfig.getInstance();

        // SMA/EMA
        smaPanel.setPeriods(config.getSmaPeriods());
        emaPanel.setColorOffset(config.getSmaPeriods().size());
        emaPanel.setPeriods(config.getEmaPeriods());

        // Overlays
        bb.setSelected(config.isBollingerEnabled());
        bb.setPeriod(config.getBollingerPeriod());
        bb.setMultiplier(config.getBollingerStdDev());
        hl.setSelected(config.isHighLowEnabled());
        hl.setPeriod(config.getHighLowPeriod());
        mayer.setSelected(config.isMayerEnabled());
        mayer.setPeriod(config.getMayerPeriod());
        dailyPoc.setSelected(config.isDailyPocEnabled());
        floatingPoc.setSelected(config.isFloatingPocEnabled());
        floatingPoc.setBars(config.getFloatingPocPeriod());
        vwap.setSelected(config.isVwapEnabled());
        pivotPoints.setSelected(config.isPivotPointsEnabled());
        pivotPoints.setShowR3S3(config.isPivotPointsShowR3S3());
        atrBands.setSelected(config.isAtrBandsEnabled());
        atrBands.setPeriod(config.getAtrBandsPeriod());
        atrBands.setMultiplier(config.getAtrBandsMultiplier());
        supertrend.setSelected(config.isSupertrendEnabled());
        supertrend.setPeriod(config.getSupertrendPeriod());
        supertrend.setMultiplier(config.getSupertrendMultiplier());
        keltner.setSelected(config.isKeltnerEnabled());
        donchian.setSelected(config.isDonchianEnabled());
        donchian.setPeriod(config.getDonchianPeriod());
        donchian.setShowMiddle(config.isDonchianShowMiddle());
        rays.setSelected(config.isRayOverlayEnabled());
        rays.setLookback(config.getRayLookback());
        rays.setSkip(config.getRaySkip());
        rays.setHistoric(config.isRayHistoricEnabled());
        ichimoku.setSelected(config.isIchimokuEnabled());
        dailyVolumeProfile.setSelected(config.isDailyVolumeProfileEnabled());
        dailyVolumeProfile.setBins(config.getDailyVolumeProfileBins());
        String colorMode = config.getDailyVolumeProfileColorMode();
        switch (colorMode) {
            case "DELTA" -> dailyVolumeProfile.setColorMode("Delta");
            case "DELTA_INTENSITY" -> dailyVolumeProfile.setColorMode("Delta+Volume");
            default -> dailyVolumeProfile.setColorMode("Volume");
        }

        // Footprint
        footprintHeatmapCheckbox.setSelected(config.isFootprintHeatmapEnabled());
        updateFootprintTickButtons();
        boolean isSplitMode = config.getFootprintHeatmapConfig().getDisplayMode() ==
            com.tradery.forge.ui.charts.footprint.FootprintDisplayMode.SPLIT;
        footprintSplitButton.setSelected(isSplitMode);
        footprintDeltaButton.setSelected(!isSplitMode);
        var fpConfig = config.getFootprintHeatmapConfig();
        if (fpConfig.getTickSizeMode() == com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig.TickSizeMode.FIXED) {
            double fixedTick = fpConfig.getFixedTickSize();
            int nearestIdx = 0;
            double minDiff = Math.abs(currentGridOptions[0] - fixedTick);
            for (int i = 1; i < 4; i++) {
                double diff = Math.abs(currentGridOptions[i] - fixedTick);
                if (diff < minDiff) { minDiff = diff; nearestIdx = i; }
            }
            footprintGridButtons[nearestIdx].setSelected(true);
        } else {
            int buckets = fpConfig.getTargetBuckets();
            if (buckets <= 15) footprintAuto10Button.setSelected(true);
            else if (buckets <= 30) footprintAuto20Button.setSelected(true);
            else footprintAuto40Button.setSelected(true);
        }
        updateFootprintControlVisibility();

        // Oscillators
        rsi.setSelected(config.isRsiEnabled());
        rsi.setPeriod(config.getRsiPeriod());
        macd.setSelected(config.isMacdEnabled());
        atr.setSelected(config.isAtrEnabled());
        atr.setPeriod(config.getAtrPeriod());
        stochastic.setSelected(config.isStochasticEnabled());
        rangePosition.setSelected(config.isRangePositionEnabled());
        rangePosition.setPeriod(config.getRangePositionPeriod());
        adx.setSelected(config.isAdxEnabled());
        adx.setPeriod(config.getAdxPeriod());

        // Orderflow
        delta.setSelected(config.isDeltaEnabled());
        cvd.setSelected(config.isCvdEnabled());
        volumeRatio.setSelected(config.isVolumeRatioEnabled());
        whale.setSelected(config.isWhaleEnabled());
        whale.setThreshold((int) config.getWhaleThreshold());
        retail.setSelected(config.isRetailEnabled());
        retail.setThreshold((int) config.getRetailThreshold());
        tradeCount.setSelected(config.isTradeCountEnabled());

        // Funding / OI / Premium / Holding Costs
        funding.setSelected(config.isFundingEnabled());
        oi.setSelected(config.isOiEnabled());
        premium.setSelected(config.isPremiumEnabled());
        holdingCostCumulative.setSelected(config.isHoldingCostCumulativeEnabled());
        holdingCostEvents.setSelected(config.isHoldingCostEventsEnabled());

        // Core charts
        volumeChart.setSelected(config.isVolumeChartEnabled());
        equityChart.setSelected(config.isEquityChartEnabled());
        comparisonChart.setSelected(config.isComparisonChartEnabled());
        capitalUsageChart.setSelected(config.isCapitalUsageChartEnabled());
        tradePLChart.setSelected(config.isTradePLChartEnabled());
    }

    // ===== Apply changes to ChartPanel + Config =====

    private void applyChanges() {
        ChartConfig config = ChartConfig.getInstance();

        // SMA/EMA - update config from panel state
        List<Integer> smaPeriods = smaPanel.getSelectedPeriods();
        List<Integer> emaPeriods = emaPanel.getSelectedPeriods();
        config.setSmaPeriods(smaPeriods);
        config.setEmaPeriods(emaPeriods);
        emaPanel.setColorOffset(smaPanel.getAllPeriods().size());

        // Bollinger
        if (bb.isSelected()) chartPanel.setBollingerOverlay(bb.getPeriod(), bb.getMultiplier(), null);
        else chartPanel.clearBollingerOverlay();
        config.setBollingerEnabled(bb.isSelected());
        config.setBollingerPeriod(bb.getPeriod());
        config.setBollingerStdDev(bb.getMultiplier());

        // High/Low
        if (hl.isSelected()) chartPanel.setHighLowOverlay(hl.getPeriod(), null);
        else chartPanel.clearHighLowOverlay();
        config.setHighLowEnabled(hl.isSelected());
        config.setHighLowPeriod(hl.getPeriod());

        // Mayer
        chartPanel.setMayerMultipleEnabled(mayer.isSelected(), mayer.getPeriod());
        config.setMayerEnabled(mayer.isSelected());
        config.setMayerPeriod(mayer.getPeriod());

        // Daily POC
        if (dailyPoc.isSelected()) chartPanel.setDailyPocOverlay(null);
        else chartPanel.clearDailyPocOverlay();
        config.setDailyPocEnabled(dailyPoc.isSelected());

        // Floating POC
        if (floatingPoc.isSelected()) chartPanel.setFloatingPocOverlay(null, floatingPoc.getBars());
        else chartPanel.clearFloatingPocOverlay();
        config.setFloatingPocEnabled(floatingPoc.isSelected());
        config.setFloatingPocPeriod(floatingPoc.getBars());

        // VWAP
        if (vwap.isSelected()) chartPanel.setVwapOverlay(null);
        else chartPanel.clearVwapOverlay();
        config.setVwapEnabled(vwap.isSelected());

        // Pivot Points
        if (pivotPoints.isSelected()) chartPanel.setPivotPointsOverlay(pivotPoints.isShowR3S3());
        else chartPanel.clearPivotPointsOverlay();
        config.setPivotPointsEnabled(pivotPoints.isSelected());
        config.setPivotPointsShowR3S3(pivotPoints.isShowR3S3());

        // ATR Bands
        if (atrBands.isSelected()) chartPanel.setAtrBandsOverlay(atrBands.getPeriod(), atrBands.getMultiplier());
        else chartPanel.clearAtrBandsOverlay();
        config.setAtrBandsEnabled(atrBands.isSelected());
        config.setAtrBandsPeriod(atrBands.getPeriod());
        config.setAtrBandsMultiplier(atrBands.getMultiplier());

        // Supertrend
        if (supertrend.isSelected()) chartPanel.setSupertrendOverlay(supertrend.getPeriod(), supertrend.getMultiplier());
        else chartPanel.clearSupertrendOverlay();
        config.setSupertrendEnabled(supertrend.isSelected());
        config.setSupertrendPeriod(supertrend.getPeriod());
        config.setSupertrendMultiplier(supertrend.getMultiplier());

        // Keltner
        if (keltner.isSelected()) chartPanel.setKeltnerOverlay(keltner.getEmaPeriod(), keltner.getAtrPeriod(), keltner.getMultiplier());
        else chartPanel.clearKeltnerOverlay();
        config.setKeltnerEnabled(keltner.isSelected());
        config.setKeltnerEmaPeriod(keltner.getEmaPeriod());
        config.setKeltnerAtrPeriod(keltner.getAtrPeriod());
        config.setKeltnerMultiplier(keltner.getMultiplier());

        // Donchian
        if (donchian.isSelected()) chartPanel.setDonchianOverlay(donchian.getPeriod(), donchian.isShowMiddle());
        else chartPanel.clearDonchianOverlay();
        config.setDonchianEnabled(donchian.isSelected());
        config.setDonchianPeriod(donchian.getPeriod());
        config.setDonchianShowMiddle(donchian.isShowMiddle());

        // Rays
        int rayLookback = rays.getLookback();
        int raySkip = rays.getSkip();
        boolean rayHistoric = rays.isHistoric();
        if (rays.isSelected()) {
            chartPanel.setRayOverlay(true, rayLookback, raySkip);
            chartPanel.setRayShowHistoric(rayHistoric);
        } else {
            chartPanel.clearRayOverlay();
        }
        config.setRayOverlayEnabled(rays.isSelected());
        config.setRayLookback(rayLookback);
        config.setRaySkip(raySkip);
        config.setRayHistoricEnabled(rayHistoric);

        // Ichimoku
        if (ichimoku.isSelected()) {
            chartPanel.setIchimokuOverlay(
                config.getIchimokuConversionPeriod(), config.getIchimokuBasePeriod(),
                config.getIchimokuSpanBPeriod(), config.getIchimokuDisplacement());
        } else {
            chartPanel.clearIchimokuOverlay();
        }
        config.setIchimokuEnabled(ichimoku.isSelected());

        // Daily Volume Profile
        int vpBins = dailyVolumeProfile.getBins();
        if (dailyVolumeProfile.isSelected()) {
            chartPanel.setDailyVolumeProfileOverlay(
                chartPanel.getCurrentCandles(), vpBins, 70.0, config.getDailyVolumeProfileWidth());
        } else {
            chartPanel.clearDailyVolumeProfileOverlay();
        }
        config.setDailyVolumeProfileEnabled(dailyVolumeProfile.isSelected());
        config.setDailyVolumeProfileBins(vpBins);
        String colorModeEnum = switch (dailyVolumeProfile.getColorMode()) {
            case "Delta" -> "DELTA";
            case "Delta+Volume" -> "DELTA_INTENSITY";
            default -> "VOLUME_INTENSITY";
        };
        config.setDailyVolumeProfileColorMode(colorModeEnum);

        // Footprint Heatmap
        var fpMode = footprintSplitButton.isSelected()
            ? com.tradery.forge.ui.charts.footprint.FootprintDisplayMode.SPLIT
            : com.tradery.forge.ui.charts.footprint.FootprintDisplayMode.COMBINED;
        var fpConfig = config.getFootprintHeatmapConfig();
        if (footprintAuto10Button.isSelected()) {
            fpConfig.setTickSizeMode(com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig.TickSizeMode.AUTO);
            fpConfig.setTargetBuckets(10);
        } else if (footprintAuto20Button.isSelected()) {
            fpConfig.setTickSizeMode(com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig.TickSizeMode.AUTO);
            fpConfig.setTargetBuckets(20);
        } else if (footprintAuto40Button.isSelected()) {
            fpConfig.setTickSizeMode(com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig.TickSizeMode.AUTO);
            fpConfig.setTargetBuckets(40);
        } else {
            for (int i = 0; i < 4; i++) {
                if (footprintGridButtons[i].isSelected()) {
                    fpConfig.setTickSizeMode(com.tradery.forge.ui.charts.footprint.FootprintHeatmapConfig.TickSizeMode.FIXED);
                    fpConfig.setFixedTickSize(currentGridOptions[i]);
                    break;
                }
            }
        }
        fpConfig.setDisplayMode(fpMode);
        config.setFootprintHeatmapEnabled(footprintHeatmapCheckbox.isSelected());
        chartPanel.setFootprintHeatmapEnabled(footprintHeatmapCheckbox.isSelected());
        chartPanel.refreshFootprintHeatmap();

        // Oscillators
        chartPanel.setRsiChartEnabled(rsi.isSelected(), rsi.getPeriod());
        chartPanel.setMacdChartEnabled(macd.isSelected(), macd.getFast(), macd.getSlow(), macd.getSignal());
        chartPanel.setAtrChartEnabled(atr.isSelected(), atr.getPeriod());
        chartPanel.setStochasticChartEnabled(stochastic.isSelected(), stochastic.getKPeriod(), stochastic.getDPeriod());
        chartPanel.setRangePositionChartEnabled(rangePosition.isSelected(), rangePosition.getPeriod());
        chartPanel.setAdxChartEnabled(adx.isSelected(), adx.getPeriod());

        config.setRsiEnabled(rsi.isSelected());
        config.setRsiPeriod(rsi.getPeriod());
        config.setMacdEnabled(macd.isSelected());
        config.setMacdFast(macd.getFast());
        config.setMacdSlow(macd.getSlow());
        config.setMacdSignal(macd.getSignal());
        config.setAtrEnabled(atr.isSelected());
        config.setAtrPeriod(atr.getPeriod());
        config.setStochasticEnabled(stochastic.isSelected());
        config.setStochasticKPeriod(stochastic.getKPeriod());
        config.setStochasticDPeriod(stochastic.getDPeriod());
        config.setRangePositionEnabled(rangePosition.isSelected());
        config.setRangePositionPeriod(rangePosition.getPeriod());
        config.setAdxEnabled(adx.isSelected());
        config.setAdxPeriod(adx.getPeriod());

        // Orderflow
        double whaleThreshold = whale.getThreshold();
        chartPanel.setWhaleThreshold(whaleThreshold);
        chartPanel.setDeltaChartEnabled(delta.isSelected());
        chartPanel.setCvdChartEnabled(cvd.isSelected());
        chartPanel.setVolumeRatioChartEnabled(volumeRatio.isSelected());
        chartPanel.setWhaleChartEnabled(whale.isSelected(), whaleThreshold);
        double retailThreshold = retail.getThreshold();
        chartPanel.setRetailThreshold(retailThreshold);
        chartPanel.setRetailChartEnabled(retail.isSelected(), retailThreshold);
        chartPanel.setTradeCountChartEnabled(tradeCount.isSelected());

        config.setDeltaEnabled(delta.isSelected());
        config.setCvdEnabled(cvd.isSelected());
        config.setVolumeRatioEnabled(volumeRatio.isSelected());
        config.setWhaleEnabled(whale.isSelected());
        config.setRetailEnabled(retail.isSelected());
        config.setTradeCountEnabled(tradeCount.isSelected());
        config.setWhaleThreshold(whaleThreshold);
        config.setRetailThreshold(retailThreshold);

        // Funding / OI / Premium
        chartPanel.setFundingChartEnabled(funding.isSelected());
        config.setFundingEnabled(funding.isSelected());
        chartPanel.setOiChartEnabled(oi.isSelected());
        config.setOiEnabled(oi.isSelected());
        chartPanel.setPremiumChartEnabled(premium.isSelected());
        config.setPremiumEnabled(premium.isSelected());

        // Holding Costs
        chartPanel.setHoldingCostCumulativeChartEnabled(holdingCostCumulative.isSelected());
        config.setHoldingCostCumulativeEnabled(holdingCostCumulative.isSelected());
        chartPanel.setHoldingCostEventsChartEnabled(holdingCostEvents.isSelected());
        config.setHoldingCostEventsEnabled(holdingCostEvents.isSelected());

        // Core charts
        chartPanel.setVolumeChartEnabled(volumeChart.isSelected());
        chartPanel.setEquityChartEnabled(equityChart.isSelected());
        chartPanel.setComparisonChartEnabled(comparisonChart.isSelected());
        chartPanel.setCapitalUsageChartEnabled(capitalUsageChart.isSelected());
        chartPanel.setTradePLChartEnabled(tradePLChart.isSelected());
        config.setVolumeChartEnabled(volumeChart.isSelected());
        config.setEquityChartEnabled(equityChart.isSelected());
        config.setComparisonChartEnabled(comparisonChart.isSelected());
        config.setCapitalUsageChartEnabled(capitalUsageChart.isSelected());
        config.setTradePLChartEnabled(tradePLChart.isSelected());

        if (onBacktestNeeded != null) onBacktestNeeded.run();
    }

    /** Show the popup below the given component, clamped to screen bounds. */
    public static void showBelow(Component anchor, ChartsPanel chartPanel, Runnable onBacktestNeeded) {
        Window window = SwingUtilities.getWindowAncestor(anchor);
        IndicatorSelectorPopup popup = new IndicatorSelectorPopup(window, chartPanel, onBacktestNeeded);
        Point loc = anchor.getLocationOnScreen();
        int x = loc.x;
        int y = loc.y + anchor.getHeight();

        // Clamp to screen
        GraphicsConfiguration gc = anchor.getGraphicsConfiguration();
        if (gc != null) {
            Rectangle screen = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxY = screen.y + screen.height - insets.bottom;
            if (y + popup.getHeight() > maxY) {
                y = maxY - popup.getHeight();
            }
        }

        popup.setLocation(x, y);
        popup.setVisible(true);
    }
}
