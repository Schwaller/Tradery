package com.tradery.desk.ui;

import com.tradery.ui.controls.indicators.*;

import javax.swing.*;
import java.awt.*;

import static com.tradery.ui.controls.IndicatorSelectorPanel.*;

/**
 * Vertical side panel with market info header and categorized chart indicator/overlay toggles.
 * Uses shared indicator row components from tradery-ui-common.
 */
public class IndicatorSidePanel extends JPanel {

    private final PriceChartPanel chartPanel;


    // Overlays - Dynamic multi-instance SMA/EMA
    private static final Color[] DESK_PALETTE = {
        new Color(0x4E79A7), new Color(0xF28E2B), new Color(0xE15759), new Color(0x76B7B2),
        new Color(0x59A14F), new Color(0xEDC948), new Color(0xB07AA1), new Color(0xFF9DA7)
    };
    private final DynamicOverlayPanel smaPanel = new DynamicOverlayPanel("SMA", 20, 5, 200, DESK_PALETTE);
    private final DynamicOverlayPanel emaPanel = new DynamicOverlayPanel("EMA", 20, 5, 200, DESK_PALETTE);
    private final PeriodMultiplierRow bb = new PeriodMultiplierRow("Bollinger", null, 20, 5, 100, "\u03C3:", 2.0, 0.5, 4.0, 0.5);
    private final IndicatorToggleRow vwap = new IndicatorToggleRow("VWAP", "Volume Weighted Average Price");
    private final IndicatorToggleRow ichimoku = new IndicatorToggleRow("Ichimoku Cloud");
    private final PeriodMultiplierRow supertrend = new PeriodMultiplierRow("Supertrend", null, 10, 5, 50, "\u00D7", 3.0, 1.0, 5.0, 0.5);
    private final PeriodIndicatorRow highLow = new PeriodIndicatorRow("High/Low", 20, 5, 200);
    private final PeriodIndicatorRow mayer = new PeriodIndicatorRow("Mayer Multiple", 200, 50, 365);
    private final FloatingPocRow poc = new FloatingPocRow();
    private final IndicatorToggleRow daily = new IndicatorToggleRow("Daily POC/VAH/VAL");
    private final KeltnerRow keltner = new KeltnerRow();
    private final DonchianRow donchian = new DonchianRow();
    private final RayRow rays = new RayRow();
    private final PeriodMultiplierRow atrBands = new PeriodMultiplierRow("ATR Bands", null, 14, 5, 50, "\u00D7", 2.0, 0.5, 5.0, 0.5);
    private final PivotPointsRow pivots = new PivotPointsRow();

    // Indicators
    private final PeriodIndicatorRow rsi = new PeriodIndicatorRow("RSI", 14, 2, 50);
    private final MacdRow macd = new MacdRow();
    private final PeriodIndicatorRow atr = new PeriodIndicatorRow("ATR", 14, 2, 50);
    private final StochasticRow stochastic = new StochasticRow();
    private final PeriodIndicatorRow adx = new PeriodIndicatorRow("ADX", "Average Directional Index", 14, 2, 50);
    private final PeriodIndicatorRow rangePos = new PeriodIndicatorRow("Range Position", 200, 5, 500);

    // Orderflow
    private final IndicatorToggleRow delta = new IndicatorToggleRow("Delta (per bar)");
    private final IndicatorToggleRow tradeCount = new IndicatorToggleRow("Trade Count");
    private final IndicatorToggleRow buySell = new IndicatorToggleRow("Buy/Sell Volume");

    // Core
    private final IndicatorToggleRow volume = new IndicatorToggleRow("Volume");

    public IndicatorSidePanel(PriceChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(140, 0));

        // EMA colors continue after SMA colors
        emaPanel.setColorOffset(2); // default 2 SMA periods

        JPanel content = createContentPanel();

        // Overlays
        content.add(createSectionHeader("OVERLAYS"));
        content.add(smaPanel);
        content.add(emaPanel);
        for (JPanel row : new JPanel[]{bb, vwap, ichimoku, supertrend,
                highLow, mayer, poc, daily, keltner, donchian, rays, atrBands, pivots}) {
            content.add(row);
        }
        content.add(createSectionSeparator());

        // Indicator Charts
        content.add(createSectionHeader("INDICATOR CHARTS"));
        for (JPanel row : new JPanel[]{rsi, macd, atr, stochastic, adx, rangePos}) {
            content.add(row);
        }
        content.add(createSectionSeparator());

        // Orderflow
        content.add(createSectionHeader("ORDERFLOW CHARTS"));
        for (JPanel row : new JPanel[]{delta, tradeCount, buySell}) {
            content.add(row);
        }
        content.add(createSectionSeparator());

        // Core
        content.add(createSectionHeader("CORE CHARTS"));
        content.add(volume);

        content.add(Box.createVerticalGlue());
        add(createScrollableContent(content), BorderLayout.CENTER);

        // Initialize default SMA/EMA periods
        smaPanel.setPeriods(java.util.List.of(20, 50));
        emaPanel.setPeriods(java.util.List.of(20));

        // Wire overlay changes
        Runnable overlayUpdate = this::updateOverlays;
        smaPanel.addChangeListener(overlayUpdate);
        emaPanel.addChangeListener(overlayUpdate);
        for (var row : new Object[]{bb, vwap, ichimoku, supertrend,
                highLow, mayer, poc, daily, keltner, donchian, rays, atrBands, pivots}) {
            if (row instanceof PeriodIndicatorRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof PeriodMultiplierRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof IndicatorToggleRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof KeltnerRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof DonchianRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof RayRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof FloatingPocRow r) r.addChangeListener(overlayUpdate);
            else if (row instanceof PivotPointsRow r) r.addChangeListener(overlayUpdate);
        }

        // Wire indicator chart toggles
        rsi.addChangeListener(() -> chartPanel.setRsiEnabled(rsi.isSelected(), rsi.getPeriod()));
        macd.addChangeListener(() -> chartPanel.setMacdEnabled(macd.isSelected(), macd.getFast(), macd.getSlow(), macd.getSignal()));
        atr.addChangeListener(() -> chartPanel.setAtrEnabled(atr.isSelected(), atr.getPeriod()));
        stochastic.addChangeListener(() -> chartPanel.setStochasticEnabled(stochastic.isSelected(), stochastic.getKPeriod(), stochastic.getDPeriod()));
        adx.addChangeListener(() -> chartPanel.setAdxEnabled(adx.isSelected(), adx.getPeriod()));
        rangePos.addChangeListener(() -> chartPanel.setRangePositionEnabled(rangePos.isSelected(), rangePos.getPeriod(), 0));
        delta.addChangeListener(() -> chartPanel.setDeltaEnabled(delta.isSelected()));
        tradeCount.addChangeListener(() -> chartPanel.setTradeCountEnabled(tradeCount.isSelected()));
        buySell.addChangeListener(() -> chartPanel.setVolumeRatioEnabled(buySell.isSelected()));
        volume.addChangeListener(() -> chartPanel.setVolumeEnabled(volume.isSelected()));

    }

    public void dispose() {
    }

    private void updateOverlays() {
        chartPanel.clearOverlays();

        // Dynamic SMA/EMA overlays
        for (int period : smaPanel.getSelectedPeriods()) {
            chartPanel.addSmaOverlay(period);
        }
        for (int period : emaPanel.getSelectedPeriods()) {
            chartPanel.addEmaOverlay(period);
        }
        // Update EMA color offset based on current SMA count
        emaPanel.setColorOffset(smaPanel.getAllPeriods().size());

        if (bb.isSelected()) chartPanel.addBollingerOverlay(bb.getPeriod(), bb.getMultiplier());
        if (vwap.isSelected()) chartPanel.addVwapOverlay();
        if (ichimoku.isSelected()) chartPanel.addIchimokuOverlay();
        if (supertrend.isSelected()) chartPanel.addSupertrendOverlay((int) supertrend.getPeriod(), supertrend.getMultiplier());
        if (highLow.isSelected()) chartPanel.addHighLowOverlay(highLow.getPeriod());
        if (mayer.isSelected()) chartPanel.addMayerMultipleOverlay(mayer.getPeriod());
        if (poc.isSelected()) chartPanel.addPocOverlay(poc.getBars());
        if (daily.isSelected()) chartPanel.addDailyLevelsOverlay();
        if (keltner.isSelected()) chartPanel.addKeltnerOverlay(keltner.getEmaPeriod(), keltner.getAtrPeriod(), keltner.getMultiplier());
        if (donchian.isSelected()) chartPanel.addDonchianOverlay(donchian.getPeriod());
        if (rays.isSelected()) chartPanel.addRayOverlay(rays.getLookback(), rays.getSkip());
        if (atrBands.isSelected()) chartPanel.addAtrBandsOverlay((int) atrBands.getPeriod(), atrBands.getMultiplier());
        if (pivots.isSelected()) chartPanel.addPivotPointsOverlay(pivots.isShowR3S3());

        chartPanel.getCandlestickChart().updateData(chartPanel.getDataProvider());
    }
}
