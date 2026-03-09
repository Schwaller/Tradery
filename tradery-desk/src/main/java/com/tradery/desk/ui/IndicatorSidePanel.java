package com.tradery.desk.ui;

import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.*;
import com.tradery.charts.overlay.OverlayManager;
import com.tradery.core.model.Candle;
import com.tradery.ui.controls.ChartConfig;
import com.tradery.ui.controls.IndicatorSelectorContent;
import com.tradery.ui.controls.IndicatorSelectorContent.Feature;
import com.tradery.ui.controls.indicators.FootprintDisplayMode;
import com.tradery.ui.controls.indicators.FootprintHeatmapConfig;
import com.tradery.ui.controls.indicators.SpectrumBucketMode;
import com.tradery.ui.controls.indicators.SpectrumColorMode;

import javax.swing.*;
import java.awt.*;
import java.util.EnumSet;
import java.util.List;

import static com.tradery.ui.controls.IndicatorSelectorPanel.*;

/**
 * Vertical side panel with categorized chart indicator/overlay toggles.
 * Delegates content assembly to shared {@link IndicatorSelectorContent}.
 * All changes are persisted via {@link ChartConfig} and applied through
 * the shared {@link OverlayManager} and {@link IndicatorChartsManager}.
 */
public class IndicatorSidePanel extends JPanel {

    private static final Color[] DESK_PALETTE = {
        new Color(0x4E79A7), new Color(0xF28E2B), new Color(0xE15759), new Color(0x76B7B2),
        new Color(0x59A14F), new Color(0xEDC948), new Color(0xB07AA1), new Color(0xFF9DA7)
    };

    private final PriceChartPanel chartPanel;
    private final IndicatorSelectorContent content;

    public IndicatorSidePanel(PriceChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        setLayout(new BorderLayout());
        setBorder(null);
        setMinimumSize(new Dimension(140, 0));

        content = new IndicatorSelectorContent(DESK_PALETTE,
            EnumSet.complementOf(EnumSet.of(Feature.BACKTEST_CHARTS)));

        JPanel panel = createContentPanel();
        content.buildContent(panel);

        // Wrap content in BorderLayout.NORTH to prevent vertical stretching
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(null);
        wrapper.add(panel, BorderLayout.NORTH);
        JScrollPane scrollPane = createScrollableContent(wrapper);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Populate UI from saved config
        syncFromConfig();

        // Wire all changes through single callback
        content.wireListeners(this::applyChanges);
    }

    public void dispose() {
    }

    /**
     * Populate UI controls from saved ChartConfig.
     * Called on startup to restore persisted state.
     */
    private void syncFromConfig() {
        ChartConfig config = ChartConfig.getInstance();

        // SMA/EMA periods
        List<Integer> smaPeriods = config.getSmaPeriods();
        if (smaPeriods.isEmpty()) {
            // Default periods for desk if nothing saved
            content.smaPanel().setPeriods(List.of(20, 50));
        } else {
            content.smaPanel().setPeriods(smaPeriods);
        }

        List<Integer> emaPeriods = config.getEmaPeriods();
        content.emaPanel().setColorOffset(content.smaPanel().getAllPeriods().size());
        if (emaPeriods.isEmpty()) {
            content.emaPanel().setPeriods(List.of(20));
        } else {
            content.emaPanel().setPeriods(emaPeriods);
        }

        // Overlay toggles + parameters
        content.bb().setSelected(config.isBollingerEnabled());
        content.bb().setPeriod(config.getBollingerPeriod());
        content.bb().setMultiplier(config.getBollingerStdDev());

        content.hl().setSelected(config.isHighLowEnabled());
        content.hl().setPeriod(config.getHighLowPeriod());

        content.mayer().setSelected(config.isMayerEnabled());
        content.mayer().setPeriod(config.getMayerPeriod());

        content.dailyPoc().setSelected(config.isDailyPocEnabled());

        content.floatingPoc().setSelected(config.isFloatingPocEnabled());
        content.floatingPoc().setBars(config.getFloatingPocPeriod());

        content.vwap().setSelected(config.isVwapEnabled());

        content.pivotPoints().setSelected(config.isPivotPointsEnabled());
        content.pivotPoints().setShowR3S3(config.isPivotPointsShowR3S3());

        content.atrBands().setSelected(config.isAtrBandsEnabled());
        content.atrBands().setPeriod(config.getAtrBandsPeriod());
        content.atrBands().setMultiplier(config.getAtrBandsMultiplier());

        content.supertrend().setSelected(config.isSupertrendEnabled());
        content.supertrend().setPeriod(config.getSupertrendPeriod());
        content.supertrend().setMultiplier(config.getSupertrendMultiplier());

        content.keltner().setSelected(config.isKeltnerEnabled());
        content.keltner().setEmaPeriod(config.getKeltnerEmaPeriod());
        content.keltner().setAtrPeriod(config.getKeltnerAtrPeriod());
        content.keltner().setMultiplier(config.getKeltnerMultiplier());

        content.donchian().setSelected(config.isDonchianEnabled());
        content.donchian().setPeriod(config.getDonchianPeriod());

        content.rays().setSelected(config.isRayOverlayEnabled());
        content.rays().setLookback(config.getRayLookback());
        content.rays().setSkip(config.getRaySkip());

        content.ichimoku().setSelected(config.isIchimokuEnabled());

        // Indicator toggles + parameters
        content.rsi().setSelected(config.isRsiEnabled());
        content.rsi().setPeriod(config.getRsiPeriod());

        content.macd().setSelected(config.isMacdEnabled());
        content.macd().setFast(config.getMacdFast());
        content.macd().setSlow(config.getMacdSlow());
        content.macd().setSignal(config.getMacdSignal());

        content.atr().setSelected(config.isAtrEnabled());
        content.atr().setPeriod(config.getAtrPeriod());

        content.stochastic().setSelected(config.isStochasticEnabled());
        content.stochastic().setKPeriod(config.getStochasticKPeriod());
        content.stochastic().setDPeriod(config.getStochasticDPeriod());

        content.rangePosition().setSelected(config.isRangePositionEnabled());
        content.rangePosition().setPeriod(config.getRangePositionPeriod());

        content.adx().setSelected(config.isAdxEnabled());
        content.adx().setPeriod(config.getAdxPeriod());

        // Daily Volume Profile
        content.dailyVolumeProfile().setSelected(config.isDailyVolumeProfileEnabled());
        content.dailyVolumeProfile().setBins(config.getDailyVolumeProfileBins());
        String colorMode = config.getDailyVolumeProfileColorMode();
        switch (colorMode != null ? colorMode : "") {
            case "DELTA" -> content.dailyVolumeProfile().setColorMode("Delta");
            case "DELTA_INTENSITY" -> content.dailyVolumeProfile().setColorMode("Delta+Volume");
            default -> content.dailyVolumeProfile().setColorMode("Volume");
        }

        // Footprint Heatmap
        content.footprintHeatmapCheckbox().setSelected(config.isFootprintHeatmapEnabled());
        content.footprintGlobalNormCheckbox().setSelected(config.getFootprintHeatmapConfig().isGlobalVolumeNorm());
        boolean isSplitMode = config.getFootprintHeatmapConfig().getDisplayMode() == FootprintDisplayMode.SPLIT;
        content.footprintSplitButton().setSelected(isSplitMode);
        content.footprintDeltaButton().setSelected(!isSplitMode);

        var fpConfig = config.getFootprintHeatmapConfig();
        if (fpConfig.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED) {
            double fixedTick = fpConfig.getFixedTickSize();
            double[] gridOptions = content.currentGridOptions();
            int nearestIdx = 0;
            double minDiff = Math.abs(gridOptions[0] - fixedTick);
            for (int i = 1; i < gridOptions.length && i < 4; i++) {
                double diff = Math.abs(gridOptions[i] - fixedTick);
                if (diff < minDiff) { minDiff = diff; nearestIdx = i; }
            }
            content.footprintGridButtons()[nearestIdx].setSelected(true);
        } else {
            int buckets = fpConfig.getTargetBuckets();
            if (buckets <= 15) content.footprintAuto10Button().setSelected(true);
            else if (buckets <= 30) content.footprintAuto20Button().setSelected(true);
            else content.footprintAuto40Button().setSelected(true);
        }
        content.updateFootprintControlVisibility();

        // Orderflow
        content.delta().setSelected(config.isDeltaEnabled());
        content.volumeRatio().setSelected(config.isVolumeRatioEnabled());
        content.tradeCount().setSelected(config.isTradeCountEnabled());

        // Orderflow Advanced
        content.cvd().setSelected(config.isCvdEnabled());
        content.whale().setSelected(config.isWhaleEnabled());
        content.whale().setThreshold((int) config.getWhaleThreshold());
        content.retail().setSelected(config.isRetailEnabled());
        content.retail().setThreshold((int) config.getRetailThreshold());

        // Market data indicators
        content.funding().setSelected(config.isFundingEnabled());
        content.oi().setSelected(config.isOiEnabled());
        content.premium().setSelected(config.isPremiumEnabled());
        content.holdingCostCumulative().setSelected(config.isHoldingCostCumulativeEnabled());
        content.holdingCostEvents().setSelected(config.isHoldingCostEventsEnabled());
        content.fearGreed().setSelected(config.isFearGreedEnabled());
        content.spectrum().setSelected(config.isSpectrumEnabled());
        content.spectrumColorModeCombo().setSelectedItem(config.getSpectrumColorMode());
        content.spectrumBucketModeCombo().setSelectedItem(config.getSpectrumBucketMode());

        // Core charts
        content.volumeChart().setSelected(config.isVolumeChartEnabled());
    }

    /**
     * Write current UI state to ChartConfig and apply to chart.
     * Single callback for all overlay and indicator changes.
     */
    private void applyChanges() {
        ChartConfig config = ChartConfig.getInstance();

        // Write overlay state to config
        config.setSmaPeriods(content.smaPanel().getSelectedPeriods());
        config.setEmaPeriods(content.emaPanel().getSelectedPeriods());
        content.emaPanel().setColorOffset(content.smaPanel().getAllPeriods().size());

        config.setBollingerEnabled(content.bb().isSelected());
        config.setBollingerPeriod(content.bb().getPeriod());
        config.setBollingerStdDev(content.bb().getMultiplier());

        config.setHighLowEnabled(content.hl().isSelected());
        config.setHighLowPeriod(content.hl().getPeriod());

        config.setMayerEnabled(content.mayer().isSelected());
        config.setMayerPeriod(content.mayer().getPeriod());

        config.setDailyPocEnabled(content.dailyPoc().isSelected());

        config.setFloatingPocEnabled(content.floatingPoc().isSelected());
        config.setFloatingPocPeriod(content.floatingPoc().getBars());

        config.setVwapEnabled(content.vwap().isSelected());

        config.setPivotPointsEnabled(content.pivotPoints().isSelected());
        config.setPivotPointsShowR3S3(content.pivotPoints().isShowR3S3());

        config.setAtrBandsEnabled(content.atrBands().isSelected());
        config.setAtrBandsPeriod((int) content.atrBands().getPeriod());
        config.setAtrBandsMultiplier(content.atrBands().getMultiplier());

        config.setSupertrendEnabled(content.supertrend().isSelected());
        config.setSupertrendPeriod((int) content.supertrend().getPeriod());
        config.setSupertrendMultiplier(content.supertrend().getMultiplier());

        config.setKeltnerEnabled(content.keltner().isSelected());
        config.setKeltnerEmaPeriod(content.keltner().getEmaPeriod());
        config.setKeltnerAtrPeriod(content.keltner().getAtrPeriod());
        config.setKeltnerMultiplier(content.keltner().getMultiplier());

        config.setDonchianEnabled(content.donchian().isSelected());
        config.setDonchianPeriod(content.donchian().getPeriod());

        config.setRayOverlayEnabled(content.rays().isSelected());
        config.setRayLookback(content.rays().getLookback());
        config.setRaySkip(content.rays().getSkip());

        config.setIchimokuEnabled(content.ichimoku().isSelected());

        // Write indicator state to config
        config.setRsiEnabled(content.rsi().isSelected());
        config.setRsiPeriod(content.rsi().getPeriod());

        config.setMacdEnabled(content.macd().isSelected());
        config.setMacdFast(content.macd().getFast());
        config.setMacdSlow(content.macd().getSlow());
        config.setMacdSignal(content.macd().getSignal());

        config.setAtrEnabled(content.atr().isSelected());
        config.setAtrPeriod(content.atr().getPeriod());

        config.setStochasticEnabled(content.stochastic().isSelected());
        config.setStochasticKPeriod(content.stochastic().getKPeriod());
        config.setStochasticDPeriod(content.stochastic().getDPeriod());

        config.setRangePositionEnabled(content.rangePosition().isSelected());
        config.setRangePositionPeriod(content.rangePosition().getPeriod());

        config.setAdxEnabled(content.adx().isSelected());
        config.setAdxPeriod(content.adx().getPeriod());

        // Daily Volume Profile
        int vpBins = content.dailyVolumeProfile().getBins();
        config.setDailyVolumeProfileEnabled(content.dailyVolumeProfile().isSelected());
        config.setDailyVolumeProfileBins(vpBins);
        String colorModeEnum = switch (content.dailyVolumeProfile().getColorMode()) {
            case "Delta" -> "DELTA";
            case "Delta+Volume" -> "DELTA_INTENSITY";
            default -> "VOLUME";
        };
        config.setDailyVolumeProfileColorMode(colorModeEnum);

        // Footprint Heatmap
        FootprintHeatmapConfig fpConfig = config.getFootprintHeatmapConfig();
        fpConfig.setDisplayMode(content.isFootprintSplit()
            ? FootprintDisplayMode.SPLIT : FootprintDisplayMode.COMBINED);

        if (content.footprintAuto10Button().isSelected()) {
            fpConfig.setTickSizeMode(FootprintHeatmapConfig.TickSizeMode.PER_CANDLE);
            fpConfig.setTargetBuckets(10);
        } else if (content.footprintAuto20Button().isSelected()) {
            fpConfig.setTickSizeMode(FootprintHeatmapConfig.TickSizeMode.PER_CANDLE);
            fpConfig.setTargetBuckets(20);
        } else if (content.footprintAuto40Button().isSelected()) {
            fpConfig.setTickSizeMode(FootprintHeatmapConfig.TickSizeMode.PER_CANDLE);
            fpConfig.setTargetBuckets(40);
        } else {
            double[] gridOptions = content.currentGridOptions();
            JToggleButton[] gridButtons = content.footprintGridButtons();
            for (int i = 0; i < 4; i++) {
                if (gridButtons[i].isSelected()) {
                    fpConfig.setTickSizeMode(FootprintHeatmapConfig.TickSizeMode.FIXED);
                    fpConfig.setFixedTickSize(gridOptions[i]);
                    break;
                }
            }
        }

        fpConfig.setGlobalVolumeNorm(content.footprintGlobalNormCheckbox().isSelected());
        config.setFootprintHeatmapEnabled(content.footprintHeatmapCheckbox().isSelected());

        // Orderflow
        config.setDeltaEnabled(content.delta().isSelected());
        config.setVolumeRatioEnabled(content.volumeRatio().isSelected());
        config.setTradeCountEnabled(content.tradeCount().isSelected());

        // Orderflow Advanced
        config.setCvdEnabled(content.cvd().isSelected());
        config.setWhaleEnabled(content.whale().isSelected());
        config.setWhaleThreshold(content.whale().getThreshold());
        config.setRetailEnabled(content.retail().isSelected());
        config.setRetailThreshold(content.retail().getThreshold());

        // Market data indicators
        config.setFundingEnabled(content.funding().isSelected());
        config.setOiEnabled(content.oi().isSelected());
        config.setPremiumEnabled(content.premium().isSelected());
        config.setHoldingCostCumulativeEnabled(content.holdingCostCumulative().isSelected());
        config.setHoldingCostEventsEnabled(content.holdingCostEvents().isSelected());
        config.setFearGreedEnabled(content.fearGreed().isSelected());
        config.setSpectrumEnabled(content.spectrum().isSelected());
        config.setSpectrumColorMode((SpectrumColorMode) content.spectrumColorModeCombo().getSelectedItem());
        config.setSpectrumBucketMode((SpectrumBucketMode) content.spectrumBucketModeCombo().getSelectedItem());

        // Core charts
        config.setVolumeChartEnabled(content.volumeChart().isSelected());

        // Apply overlays
        List<Candle> candles = chartPanel.getDataProvider().getCandles();
        chartPanel.getOverlayManager().applyConfig(config, candles);

        // Apply indicators (handles coordinator/interactionManager registration)
        chartPanel.applyIndicatorConfig(config);

        // Refresh chart display (use updateDataset to preserve overlay annotations/datasets)
        chartPanel.getCandlestickChart().updateDataset(chartPanel.getDataProvider());
    }
}
