package com.tradery.ui.controls;

import com.tradery.ui.controls.indicators.*;

import javax.swing.*;
import java.awt.*;
import java.util.EnumSet;
import java.util.List;

/**
 * Shared indicator/overlay selector content used by both Forge (popup) and Desk (side panel).
 * Owns all row declarations and section layout. Consumers pass a Feature set to control
 * which sections appear, then wire their own apply logic via accessors.
 */
public class IndicatorSelectorContent {

    /**
     * Feature flags control which sections appear in the selector.
     */
    public enum Feature {
        OVERLAYS_BASIC,        // SMA, EMA, BB, VWAP, Ichimoku, Supertrend, HighLow, Mayer,
                               // POC, DailyPOC, Keltner, Donchian, Rays, ATR Bands, Pivots
        DAILY_VOLUME_PROFILE,
        FOOTPRINT_HEATMAP,     // Complex sub-controls (split/delta toggle, bucket buttons, global norm)
        INDICATORS_BASIC,      // RSI, MACD, ATR, Stochastic, ADX, Range Position
        ORDERFLOW_BASIC,       // Delta, Trade Count, Buy/Sell Volume
        ORDERFLOW_ADVANCED,    // CVD, Whale, Retail
        FUNDING,
        OI,
        PREMIUM,
        SENTIMENT,             // Fear & Greed
        SPECTRUM,              // Trade Size Spectrum + color/bucket mode selectors
        HOLDING_COSTS,
        CORE_CHARTS,           // Volume
        BACKTEST_CHARTS        // Equity, Comparison, Capital Usage, Trade P&L
    }

    private final EnumSet<Feature> features;

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

    // Footprint heatmap sub-controls
    private JCheckBox footprintHeatmapCheckbox;
    private JToggleButton footprintSplitButton;
    private JToggleButton footprintDeltaButton;
    private ButtonGroup footprintViewGroup;
    private JToggleButton footprintAuto10Button;
    private JToggleButton footprintAuto20Button;
    private JToggleButton footprintAuto40Button;
    private JToggleButton[] footprintGridButtons = new JToggleButton[4];
    private ButtonGroup footprintBucketGroup;
    private JCheckBox footprintGlobalNormCheckbox;
    private JPanel footprintOptionsRow;
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

    // Funding / OI / Premium / Holding Costs / Sentiment / Spectrum
    private final IndicatorToggleRow funding = new IndicatorToggleRow("Funding Rate");
    private final IndicatorToggleRow oi = new IndicatorToggleRow("Open Interest", "Show OI value and change chart (Binance 5m data)");
    private final IndicatorToggleRow premium = new IndicatorToggleRow("Premium Index", "Show futures premium vs spot index (leading indicator)");
    private final IndicatorToggleRow holdingCostCumulative = new IndicatorToggleRow("Cumulative Holding Costs", "Show running total of funding fees/margin interest");
    private final IndicatorToggleRow holdingCostEvents = new IndicatorToggleRow("Holding Cost Events", "Show individual funding fee/interest charges per trade");
    private final IndicatorToggleRow fearGreed = new IndicatorToggleRow("Fear & Greed", "Show Crypto Fear & Greed Index (0-100 sentiment)");
    private final IndicatorToggleRow spectrum = new IndicatorToggleRow("Trade Size Spectrum", "Show trade size distribution heatmap (log10 buckets, requires aggTrades)");
    private final JComboBox<SpectrumColorMode> spectrumColorModeCombo = new JComboBox<>(SpectrumColorMode.values());
    private final JComboBox<SpectrumBucketMode> spectrumBucketModeCombo = new JComboBox<>(SpectrumBucketMode.values());

    // Core charts
    private final IndicatorToggleRow volumeChart = new IndicatorToggleRow("Volume", "Show volume chart");
    private final IndicatorToggleRow equityChart = new IndicatorToggleRow("Equity", "Show portfolio equity chart");
    private final IndicatorToggleRow comparisonChart = new IndicatorToggleRow("Strategy vs Buy & Hold", "Show strategy comparison chart");
    private final IndicatorToggleRow capitalUsageChart = new IndicatorToggleRow("Capital Usage", "Show capital usage percentage chart");
    private final IndicatorToggleRow tradePLChart = new IndicatorToggleRow("Trade P&L", "Show individual trade P&L chart");

    // Listener for footprint/spectrum schedule updates (set by consumer)
    private Runnable onChange;
    private Runnable repackListener;

    public IndicatorSelectorContent(Color[] palette, EnumSet<Feature> features) {
        this.features = features;
        smaPanel = new DynamicOverlayPanel("SMA", 20, 5, 200, palette);
        emaPanel = new DynamicOverlayPanel("EMA", 20, 5, 200, palette);
    }

    /** Set repack listener for dynamic SMA/EMA panels (popup needs to resize on row add/remove). */
    public void setRepackListener(Runnable listener) {
        this.repackListener = listener;
        smaPanel.setRepackListener(listener);
        emaPanel.setRepackListener(listener);
    }

    /** Add sections to target panel. Only sections matching enabled features are added. */
    public void buildContent(JPanel target) {
        if (features.contains(Feature.OVERLAYS_BASIC)) {
            target.add(IndicatorSelectorPanel.createSectionHeader("OVERLAYS"));
            target.add(smaPanel);
            target.add(emaPanel);
            for (JPanel row : new JPanel[]{bb, hl, mayer, dailyPoc, floatingPoc, vwap, pivotPoints,
                    atrBands, supertrend, keltner, donchian, rays, ichimoku}) {
                target.add(row);
            }
            if (features.contains(Feature.DAILY_VOLUME_PROFILE)) {
                target.add(dailyVolumeProfile);
            }
            if (features.contains(Feature.FOOTPRINT_HEATMAP)) {
                target.add(buildFootprintHeatmapRow());
            }
        }

        if (features.contains(Feature.INDICATORS_BASIC)) {
            target.add(IndicatorSelectorPanel.createSectionSeparator());
            target.add(IndicatorSelectorPanel.createSectionHeader("INDICATOR CHARTS"));
            for (JPanel row : new JPanel[]{rsi, macd, atr, stochastic, rangePosition, adx}) {
                target.add(row);
            }
        }

        if (features.contains(Feature.ORDERFLOW_BASIC) || features.contains(Feature.ORDERFLOW_ADVANCED)) {
            target.add(IndicatorSelectorPanel.createSectionSeparator());
            target.add(IndicatorSelectorPanel.createSectionHeader("ORDERFLOW CHARTS"));
            if (features.contains(Feature.ORDERFLOW_BASIC)) {
                target.add(delta);
            }
            if (features.contains(Feature.ORDERFLOW_ADVANCED)) {
                target.add(cvd);
            }
            if (features.contains(Feature.ORDERFLOW_BASIC)) {
                target.add(volumeRatio);
            }
            if (features.contains(Feature.ORDERFLOW_ADVANCED)) {
                target.add(whale);
                target.add(retail);
            }
            if (features.contains(Feature.ORDERFLOW_BASIC)) {
                target.add(tradeCount);
            }
        }

        if (features.contains(Feature.FUNDING)) {
            target.add(Box.createVerticalStrut(4));
            target.add(IndicatorSelectorPanel.createSectionHeader("FUNDING"));
            target.add(funding);
        }

        if (features.contains(Feature.OI)) {
            target.add(Box.createVerticalStrut(4));
            target.add(IndicatorSelectorPanel.createSectionHeader("OPEN INTEREST"));
            target.add(oi);
        }

        if (features.contains(Feature.PREMIUM)) {
            target.add(Box.createVerticalStrut(4));
            target.add(IndicatorSelectorPanel.createSectionHeader("PREMIUM INDEX"));
            target.add(premium);
        }

        if (features.contains(Feature.SENTIMENT)) {
            target.add(Box.createVerticalStrut(4));
            target.add(IndicatorSelectorPanel.createSectionHeader("SENTIMENT"));
            target.add(fearGreed);
        }

        if (features.contains(Feature.SPECTRUM)) {
            target.add(Box.createVerticalStrut(4));
            target.add(IndicatorSelectorPanel.createSectionHeader("TRADE SIZE SPECTRUM"));
            target.add(spectrum);

            JPanel spectrumOptionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            spectrumOptionsRow.setOpaque(false);
            spectrumOptionsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            JLabel colorLabel = new JLabel("Color:");
            colorLabel.setFont(colorLabel.getFont().deriveFont(11f));
            spectrumOptionsRow.add(Box.createHorizontalStrut(24));
            spectrumOptionsRow.add(colorLabel);
            spectrumColorModeCombo.setFont(spectrumColorModeCombo.getFont().deriveFont(11f));
            spectrumOptionsRow.add(spectrumColorModeCombo);
            JLabel bucketLabel = new JLabel("Bucket:");
            bucketLabel.setFont(bucketLabel.getFont().deriveFont(11f));
            spectrumOptionsRow.add(bucketLabel);
            spectrumBucketModeCombo.setFont(spectrumBucketModeCombo.getFont().deriveFont(11f));
            spectrumOptionsRow.add(spectrumBucketModeCombo);
            target.add(spectrumOptionsRow);
        }

        if (features.contains(Feature.HOLDING_COSTS)) {
            target.add(IndicatorSelectorPanel.createSectionSeparator());
            target.add(IndicatorSelectorPanel.createSectionHeader("HOLDING COSTS"));
            target.add(holdingCostCumulative);
            target.add(holdingCostEvents);
        }

        if (features.contains(Feature.CORE_CHARTS) || features.contains(Feature.BACKTEST_CHARTS)) {
            target.add(IndicatorSelectorPanel.createSectionSeparator());
            target.add(IndicatorSelectorPanel.createSectionHeader("CORE CHARTS"));
            if (features.contains(Feature.CORE_CHARTS)) {
                target.add(volumeChart);
            }
            if (features.contains(Feature.BACKTEST_CHARTS)) {
                target.add(equityChart);
                target.add(comparisonChart);
                target.add(capitalUsageChart);
                target.add(tradePLChart);
            }
        }
    }

    /** Wire all visible row change listeners to the given callback. */
    public void wireListeners(Runnable onChange) {
        this.onChange = onChange;

        if (features.contains(Feature.OVERLAYS_BASIC)) {
            smaPanel.addChangeListener(onChange);
            emaPanel.addChangeListener(onChange);
            bb.addChangeListener(onChange);
            hl.addChangeListener(onChange);
            mayer.addChangeListener(onChange);
            dailyPoc.addChangeListener(onChange);
            floatingPoc.addChangeListener(onChange);
            vwap.addChangeListener(onChange);
            pivotPoints.addChangeListener(onChange);
            atrBands.addChangeListener(onChange);
            supertrend.addChangeListener(onChange);
            keltner.addChangeListener(onChange);
            donchian.addChangeListener(onChange);
            rays.addChangeListener(onChange);
            ichimoku.addChangeListener(onChange);
        }
        if (features.contains(Feature.DAILY_VOLUME_PROFILE)) {
            dailyVolumeProfile.addChangeListener(onChange);
        }
        // Footprint listeners are wired in buildFootprintHeatmapRow

        if (features.contains(Feature.INDICATORS_BASIC)) {
            rsi.addChangeListener(onChange);
            macd.addChangeListener(onChange);
            atr.addChangeListener(onChange);
            stochastic.addChangeListener(onChange);
            rangePosition.addChangeListener(onChange);
            adx.addChangeListener(onChange);
        }

        if (features.contains(Feature.ORDERFLOW_BASIC)) {
            delta.addChangeListener(onChange);
            volumeRatio.addChangeListener(onChange);
            tradeCount.addChangeListener(onChange);
        }
        if (features.contains(Feature.ORDERFLOW_ADVANCED)) {
            cvd.addChangeListener(onChange);
            whale.addChangeListener(onChange);
            retail.addChangeListener(onChange);
        }

        if (features.contains(Feature.FUNDING)) funding.addChangeListener(onChange);
        if (features.contains(Feature.OI)) oi.addChangeListener(onChange);
        if (features.contains(Feature.PREMIUM)) premium.addChangeListener(onChange);

        if (features.contains(Feature.SENTIMENT)) fearGreed.addChangeListener(onChange);

        if (features.contains(Feature.SPECTRUM)) {
            spectrum.addChangeListener(onChange);
            spectrumColorModeCombo.addActionListener(e -> onChange.run());
            spectrumBucketModeCombo.addActionListener(e -> onChange.run());
        }

        if (features.contains(Feature.HOLDING_COSTS)) {
            holdingCostCumulative.addChangeListener(onChange);
            holdingCostEvents.addChangeListener(onChange);
        }

        if (features.contains(Feature.CORE_CHARTS)) volumeChart.addChangeListener(onChange);
        if (features.contains(Feature.BACKTEST_CHARTS)) {
            equityChart.addChangeListener(onChange);
            comparisonChart.addChangeListener(onChange);
            capitalUsageChart.addChangeListener(onChange);
            tradePLChart.addChangeListener(onChange);
        }
    }

    // ===== Footprint Heatmap (complex sub-controls) =====

    private JPanel buildFootprintHeatmapRow() {
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

        footprintGlobalNormCheckbox = new JCheckBox("Global");
        footprintGlobalNormCheckbox.setToolTipText("Color ramp uses min/max volume across all candles (on) or per candle (off)");
        footprintGlobalNormCheckbox.setFont(footprintGlobalNormCheckbox.getFont().deriveFont(11f));
        footprintGlobalNormCheckbox.addActionListener(e -> fireOnChange());

        footprintSplitButton.addActionListener(e -> { if (footprintSplitButton.isSelected()) fireOnChange(); });
        footprintDeltaButton.addActionListener(e -> { if (footprintDeltaButton.isSelected()) fireOnChange(); });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row1.add(footprintHeatmapCheckbox);
        row1.add(footprintSplitButton);
        row1.add(footprintDeltaButton);
        row1.add(Box.createHorizontalStrut(4));
        row1.add(footprintAuto10Button);
        row1.add(footprintAuto20Button);
        row1.add(footprintAuto40Button);
        for (JToggleButton btn : footprintGridButtons) row1.add(btn);

        footprintOptionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        footprintOptionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        footprintOptionsRow.add(Box.createHorizontalStrut(20));
        footprintOptionsRow.add(footprintGlobalNormCheckbox);

        footprintHeatmapCheckbox.addActionListener(e -> { updateFootprintControlVisibility(); fireOnChange(); });

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(row1);
        wrapper.add(footprintOptionsRow);
        return wrapper;
    }

    private JToggleButton createFootprintBucketButton(String text, String tooltip) {
        JToggleButton btn = new JToggleButton(text);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(text.length() > 6 ? 62 : 48, 22));
        btn.setMargin(new Insets(1, 2, 1, 2));
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.addActionListener(e -> { if (btn.isSelected()) fireOnChange(); });
        return btn;
    }

    private void fireOnChange() {
        if (onChange != null) onChange.run();
    }

    /** Update footprint control visibility based on checkbox state. */
    public void updateFootprintControlVisibility() {
        if (footprintHeatmapCheckbox == null) return;
        boolean enabled = footprintHeatmapCheckbox.isSelected();
        footprintSplitButton.setVisible(enabled);
        footprintDeltaButton.setVisible(enabled);
        footprintAuto10Button.setVisible(enabled);
        footprintAuto20Button.setVisible(enabled);
        footprintAuto40Button.setVisible(enabled);
        for (JToggleButton btn : footprintGridButtons) btn.setVisible(enabled);
        footprintOptionsRow.setVisible(enabled);
    }

    /**
     * Compute tick size grid options based on recent candle range.
     * @param candleHighLows list of [high, low] pairs for recent candles
     */
    public void updateFootprintTickButtons(List<double[]> candleHighLows) {
        if (footprintGridButtons == null) return;
        currentGridOptions = computeTickSizeOptions(candleHighLows);
        for (int i = 0; i < 4; i++) {
            String label = formatTickSize(currentGridOptions[i]);
            footprintGridButtons[i].setText(label);
            footprintGridButtons[i].setToolTipText("Fixed grid: " + label + " tick size");
            footprintGridButtons[i].setPreferredSize(new Dimension(label.length() > 5 ? 52 : 44, 22));
        }
    }

    private double[] computeTickSizeOptions(List<double[]> candleHighLows) {
        double[] result = new double[4];
        if (candleHighLows == null || candleHighLows.isEmpty()) {
            result[0] = 25; result[1] = 50; result[2] = 100; result[3] = 250;
            return result;
        }
        int lookback = Math.min(14, candleHighLows.size());
        double sumRange = 0;
        for (int i = candleHighLows.size() - lookback; i < candleHighLows.size(); i++) {
            double[] hl = candleHighLows.get(i);
            sumRange += hl[0] - hl[1];
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

    // ===== Accessors =====

    // Overlays
    public DynamicOverlayPanel smaPanel() { return smaPanel; }
    public DynamicOverlayPanel emaPanel() { return emaPanel; }
    public PeriodMultiplierRow bb() { return bb; }
    public PeriodIndicatorRow hl() { return hl; }
    public PeriodIndicatorRow mayer() { return mayer; }
    public IndicatorToggleRow dailyPoc() { return dailyPoc; }
    public FloatingPocRow floatingPoc() { return floatingPoc; }
    public IndicatorToggleRow vwap() { return vwap; }
    public PivotPointsRow pivotPoints() { return pivotPoints; }
    public PeriodMultiplierRow atrBands() { return atrBands; }
    public PeriodMultiplierRow supertrend() { return supertrend; }
    public KeltnerRow keltner() { return keltner; }
    public DonchianRow donchian() { return donchian; }
    public RayRow rays() { return rays; }
    public IndicatorToggleRow ichimoku() { return ichimoku; }
    public DailyVolumeProfileRow dailyVolumeProfile() { return dailyVolumeProfile; }

    // Footprint
    public JCheckBox footprintHeatmapCheckbox() { return footprintHeatmapCheckbox; }
    public boolean isFootprintSplit() { return footprintSplitButton != null && footprintSplitButton.isSelected(); }
    public JToggleButton footprintSplitButton() { return footprintSplitButton; }
    public JToggleButton footprintDeltaButton() { return footprintDeltaButton; }
    public JToggleButton footprintAuto10Button() { return footprintAuto10Button; }
    public JToggleButton footprintAuto20Button() { return footprintAuto20Button; }
    public JToggleButton footprintAuto40Button() { return footprintAuto40Button; }
    public JToggleButton[] footprintGridButtons() { return footprintGridButtons; }
    public JCheckBox footprintGlobalNormCheckbox() { return footprintGlobalNormCheckbox; }
    public double[] currentGridOptions() { return currentGridOptions; }

    // Oscillators
    public PeriodIndicatorRow rsi() { return rsi; }
    public MacdRow macd() { return macd; }
    public PeriodIndicatorRow atr() { return atr; }
    public StochasticRow stochastic() { return stochastic; }
    public PeriodIndicatorRow rangePosition() { return rangePosition; }
    public PeriodIndicatorRow adx() { return adx; }

    // Orderflow
    public IndicatorToggleRow delta() { return delta; }
    public IndicatorToggleRow cvd() { return cvd; }
    public IndicatorToggleRow volumeRatio() { return volumeRatio; }
    public ThresholdRow whale() { return whale; }
    public ThresholdRow retail() { return retail; }
    public IndicatorToggleRow tradeCount() { return tradeCount; }

    // Funding / OI / Premium / Holding Costs / Sentiment / Spectrum
    public IndicatorToggleRow funding() { return funding; }
    public IndicatorToggleRow oi() { return oi; }
    public IndicatorToggleRow premium() { return premium; }
    public IndicatorToggleRow holdingCostCumulative() { return holdingCostCumulative; }
    public IndicatorToggleRow holdingCostEvents() { return holdingCostEvents; }
    public IndicatorToggleRow fearGreed() { return fearGreed; }
    public IndicatorToggleRow spectrum() { return spectrum; }
    public JComboBox<SpectrumColorMode> spectrumColorModeCombo() { return spectrumColorModeCombo; }
    public JComboBox<SpectrumBucketMode> spectrumBucketModeCombo() { return spectrumBucketModeCombo; }

    // Core / Backtest charts
    public IndicatorToggleRow volumeChart() { return volumeChart; }
    public IndicatorToggleRow equityChart() { return equityChart; }
    public IndicatorToggleRow comparisonChart() { return comparisonChart; }
    public IndicatorToggleRow capitalUsageChart() { return capitalUsageChart; }
    public IndicatorToggleRow tradePLChart() { return tradePLChart; }
}
