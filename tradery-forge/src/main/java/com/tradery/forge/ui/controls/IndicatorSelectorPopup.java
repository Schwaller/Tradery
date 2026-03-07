package com.tradery.forge.ui.controls;

import com.tradery.forge.ui.ChartsPanel;
import com.tradery.ui.controls.ChartConfig;
import com.tradery.ui.controls.indicators.FootprintDisplayMode;
import com.tradery.ui.controls.indicators.FootprintHeatmapConfig;
import com.tradery.ui.controls.IndicatorSelectorContent;
import com.tradery.ui.controls.IndicatorSelectorContent.Feature;
import com.tradery.ui.controls.indicators.SpectrumBucketMode;
import com.tradery.ui.controls.indicators.SpectrumColorMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Popup dialog for selecting indicators and their parameters.
 * Delegates content assembly to shared {@link IndicatorSelectorContent}.
 */
public class IndicatorSelectorPopup extends JDialog {

    private final ChartsPanel chartPanel;
    private final Runnable onBacktestNeeded;
    private final IndicatorSelectorContent content;

    // Debounce timer
    private Timer updateTimer;
    private static final int DEBOUNCE_MS = 150;
    private boolean initializing = true;

    public IndicatorSelectorPopup(Window owner, ChartsPanel chartPanel, Runnable onBacktestNeeded) {
        super(owner, "Indicators", ModalityType.MODELESS);
        this.chartPanel = chartPanel;
        this.onBacktestNeeded = onBacktestNeeded;

        Color[] palette = com.tradery.forge.ui.charts.ChartStyles.OVERLAY_PALETTE;
        content = new IndicatorSelectorContent(palette, EnumSet.allOf(Feature.class));
        content.setRepackListener(this::pack);

        setUndecorated(true);
        setResizable(false);

        initComponents();
        initDebounceTimer();
        content.wireListeners(this::scheduleUpdate);
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

        content.buildContent(contentPane);

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
        content.smaPanel().setPeriods(config.getSmaPeriods());
        content.emaPanel().setColorOffset(config.getSmaPeriods().size());
        content.emaPanel().setPeriods(config.getEmaPeriods());

        // Overlays
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
        content.donchian().setSelected(config.isDonchianEnabled());
        content.donchian().setPeriod(config.getDonchianPeriod());
        content.donchian().setShowMiddle(config.isDonchianShowMiddle());
        content.rays().setSelected(config.isRayOverlayEnabled());
        content.rays().setLookback(config.getRayLookback());
        content.rays().setSkip(config.getRaySkip());
        content.rays().setHistoric(config.isRayHistoricEnabled());
        content.ichimoku().setSelected(config.isIchimokuEnabled());
        content.dailyVolumeProfile().setSelected(config.isDailyVolumeProfileEnabled());
        content.dailyVolumeProfile().setBins(config.getDailyVolumeProfileBins());
        String colorMode = config.getDailyVolumeProfileColorMode();
        switch (colorMode) {
            case "DELTA" -> content.dailyVolumeProfile().setColorMode("Delta");
            case "DELTA_INTENSITY" -> content.dailyVolumeProfile().setColorMode("Delta+Volume");
            default -> content.dailyVolumeProfile().setColorMode("Volume");
        }

        // Footprint
        content.footprintHeatmapCheckbox().setSelected(config.isFootprintHeatmapEnabled());
        content.footprintGlobalNormCheckbox().setSelected(config.getFootprintHeatmapConfig().isGlobalVolumeNorm());
        updateFootprintTickButtons();
        boolean isSplitMode = config.getFootprintHeatmapConfig().getDisplayMode() == FootprintDisplayMode.SPLIT;
        content.footprintSplitButton().setSelected(isSplitMode);
        content.footprintDeltaButton().setSelected(!isSplitMode);
        var fpConfig = config.getFootprintHeatmapConfig();
        if (fpConfig.getTickSizeMode() == FootprintHeatmapConfig.TickSizeMode.FIXED) {
            double fixedTick = fpConfig.getFixedTickSize();
            double[] gridOptions = content.currentGridOptions();
            int nearestIdx = 0;
            double minDiff = Math.abs(gridOptions[0] - fixedTick);
            for (int i = 1; i < 4; i++) {
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

        // Oscillators
        content.rsi().setSelected(config.isRsiEnabled());
        content.rsi().setPeriod(config.getRsiPeriod());
        content.macd().setSelected(config.isMacdEnabled());
        content.atr().setSelected(config.isAtrEnabled());
        content.atr().setPeriod(config.getAtrPeriod());
        content.stochastic().setSelected(config.isStochasticEnabled());
        content.rangePosition().setSelected(config.isRangePositionEnabled());
        content.rangePosition().setPeriod(config.getRangePositionPeriod());
        content.adx().setSelected(config.isAdxEnabled());
        content.adx().setPeriod(config.getAdxPeriod());

        // Orderflow
        content.delta().setSelected(config.isDeltaEnabled());
        content.cvd().setSelected(config.isCvdEnabled());
        content.volumeRatio().setSelected(config.isVolumeRatioEnabled());
        content.whale().setSelected(config.isWhaleEnabled());
        content.whale().setThreshold((int) config.getWhaleThreshold());
        content.retail().setSelected(config.isRetailEnabled());
        content.retail().setThreshold((int) config.getRetailThreshold());
        content.tradeCount().setSelected(config.isTradeCountEnabled());

        // Funding / OI / Premium / Holding Costs / Sentiment / Spectrum
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
        content.equityChart().setSelected(config.isEquityChartEnabled());
        content.comparisonChart().setSelected(config.isComparisonChartEnabled());
        content.capitalUsageChart().setSelected(config.isCapitalUsageChartEnabled());
        content.tradePLChart().setSelected(config.isTradePLChartEnabled());
    }

    private void updateFootprintTickButtons() {
        List<com.tradery.core.model.Candle> candles = chartPanel.getCurrentCandles();
        List<double[]> highLows = new ArrayList<>();
        if (candles != null) {
            for (var c : candles) {
                highLows.add(new double[]{c.high(), c.low()});
            }
        }
        content.updateFootprintTickButtons(highLows);
    }

    // ===== Apply changes to ChartPanel + Config =====

    private void applyChanges() {
        ChartConfig config = ChartConfig.getInstance();

        // SMA/EMA
        List<Integer> smaPeriods = content.smaPanel().getSelectedPeriods();
        List<Integer> emaPeriods = content.emaPanel().getSelectedPeriods();
        config.setSmaPeriods(smaPeriods);
        config.setEmaPeriods(emaPeriods);
        content.emaPanel().setColorOffset(content.smaPanel().getAllPeriods().size());

        // Bollinger
        if (content.bb().isSelected()) chartPanel.setBollingerOverlay(content.bb().getPeriod(), content.bb().getMultiplier(), null);
        else chartPanel.clearBollingerOverlay();
        config.setBollingerEnabled(content.bb().isSelected());
        config.setBollingerPeriod(content.bb().getPeriod());
        config.setBollingerStdDev(content.bb().getMultiplier());

        // High/Low
        if (content.hl().isSelected()) chartPanel.setHighLowOverlay(content.hl().getPeriod(), null);
        else chartPanel.clearHighLowOverlay();
        config.setHighLowEnabled(content.hl().isSelected());
        config.setHighLowPeriod(content.hl().getPeriod());

        // Mayer
        chartPanel.setMayerMultipleEnabled(content.mayer().isSelected(), content.mayer().getPeriod());
        config.setMayerEnabled(content.mayer().isSelected());
        config.setMayerPeriod(content.mayer().getPeriod());

        // Daily POC
        if (content.dailyPoc().isSelected()) chartPanel.setDailyPocOverlay(null);
        else chartPanel.clearDailyPocOverlay();
        config.setDailyPocEnabled(content.dailyPoc().isSelected());

        // Floating POC
        if (content.floatingPoc().isSelected()) chartPanel.setFloatingPocOverlay(null, content.floatingPoc().getBars());
        else chartPanel.clearFloatingPocOverlay();
        config.setFloatingPocEnabled(content.floatingPoc().isSelected());
        config.setFloatingPocPeriod(content.floatingPoc().getBars());

        // VWAP
        if (content.vwap().isSelected()) chartPanel.setVwapOverlay(null);
        else chartPanel.clearVwapOverlay();
        config.setVwapEnabled(content.vwap().isSelected());

        // Pivot Points
        if (content.pivotPoints().isSelected()) chartPanel.setPivotPointsOverlay(content.pivotPoints().isShowR3S3());
        else chartPanel.clearPivotPointsOverlay();
        config.setPivotPointsEnabled(content.pivotPoints().isSelected());
        config.setPivotPointsShowR3S3(content.pivotPoints().isShowR3S3());

        // ATR Bands
        if (content.atrBands().isSelected()) chartPanel.setAtrBandsOverlay(content.atrBands().getPeriod(), content.atrBands().getMultiplier());
        else chartPanel.clearAtrBandsOverlay();
        config.setAtrBandsEnabled(content.atrBands().isSelected());
        config.setAtrBandsPeriod(content.atrBands().getPeriod());
        config.setAtrBandsMultiplier(content.atrBands().getMultiplier());

        // Supertrend
        if (content.supertrend().isSelected()) chartPanel.setSupertrendOverlay(content.supertrend().getPeriod(), content.supertrend().getMultiplier());
        else chartPanel.clearSupertrendOverlay();
        config.setSupertrendEnabled(content.supertrend().isSelected());
        config.setSupertrendPeriod(content.supertrend().getPeriod());
        config.setSupertrendMultiplier(content.supertrend().getMultiplier());

        // Keltner
        if (content.keltner().isSelected()) chartPanel.setKeltnerOverlay(content.keltner().getEmaPeriod(), content.keltner().getAtrPeriod(), content.keltner().getMultiplier());
        else chartPanel.clearKeltnerOverlay();
        config.setKeltnerEnabled(content.keltner().isSelected());
        config.setKeltnerEmaPeriod(content.keltner().getEmaPeriod());
        config.setKeltnerAtrPeriod(content.keltner().getAtrPeriod());
        config.setKeltnerMultiplier(content.keltner().getMultiplier());

        // Donchian
        if (content.donchian().isSelected()) chartPanel.setDonchianOverlay(content.donchian().getPeriod(), content.donchian().isShowMiddle());
        else chartPanel.clearDonchianOverlay();
        config.setDonchianEnabled(content.donchian().isSelected());
        config.setDonchianPeriod(content.donchian().getPeriod());
        config.setDonchianShowMiddle(content.donchian().isShowMiddle());

        // Rays
        int rayLookback = content.rays().getLookback();
        int raySkip = content.rays().getSkip();
        boolean rayHistoric = content.rays().isHistoric();
        if (content.rays().isSelected()) {
            chartPanel.setRayOverlay(true, rayLookback, raySkip);
            chartPanel.setRayShowHistoric(rayHistoric);
        } else {
            chartPanel.clearRayOverlay();
        }
        config.setRayOverlayEnabled(content.rays().isSelected());
        config.setRayLookback(rayLookback);
        config.setRaySkip(raySkip);
        config.setRayHistoricEnabled(rayHistoric);

        // Ichimoku
        if (content.ichimoku().isSelected()) {
            chartPanel.setIchimokuOverlay(
                config.getIchimokuConversionPeriod(), config.getIchimokuBasePeriod(),
                config.getIchimokuSpanBPeriod(), config.getIchimokuDisplacement());
        } else {
            chartPanel.clearIchimokuOverlay();
        }
        config.setIchimokuEnabled(content.ichimoku().isSelected());

        // Daily Volume Profile
        int vpBins = content.dailyVolumeProfile().getBins();
        if (content.dailyVolumeProfile().isSelected()) {
            chartPanel.setDailyVolumeProfileOverlay(
                chartPanel.getCurrentCandles(), vpBins, 70.0, config.getDailyVolumeProfileWidth());
        } else {
            chartPanel.clearDailyVolumeProfileOverlay();
        }
        config.setDailyVolumeProfileEnabled(content.dailyVolumeProfile().isSelected());
        config.setDailyVolumeProfileBins(vpBins);
        String colorModeEnum = switch (content.dailyVolumeProfile().getColorMode()) {
            case "Delta" -> "DELTA";
            case "Delta+Volume" -> "DELTA_INTENSITY";
            default -> "VOLUME_INTENSITY";
        };
        config.setDailyVolumeProfileColorMode(colorModeEnum);

        // Footprint Heatmap
        var fpMode = content.isFootprintSplit() ? FootprintDisplayMode.SPLIT : FootprintDisplayMode.COMBINED;
        var fpConfig = config.getFootprintHeatmapConfig();
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
        fpConfig.setDisplayMode(fpMode);
        fpConfig.setGlobalVolumeNorm(content.footprintGlobalNormCheckbox().isSelected());
        config.setFootprintHeatmapEnabled(content.footprintHeatmapCheckbox().isSelected());
        chartPanel.setFootprintHeatmapEnabled(content.footprintHeatmapCheckbox().isSelected());
        chartPanel.refreshFootprintHeatmap();

        // Oscillators + Orderflow config
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
        config.setDeltaEnabled(content.delta().isSelected());
        config.setCvdEnabled(content.cvd().isSelected());
        config.setVolumeRatioEnabled(content.volumeRatio().isSelected());
        config.setWhaleEnabled(content.whale().isSelected());
        config.setWhaleThreshold(content.whale().getThreshold());
        config.setRetailEnabled(content.retail().isSelected());
        config.setRetailThreshold(content.retail().getThreshold());
        config.setTradeCountEnabled(content.tradeCount().isSelected());
        config.setFundingEnabled(content.funding().isSelected());
        config.setOiEnabled(content.oi().isSelected());
        config.setPremiumEnabled(content.premium().isSelected());
        config.setHoldingCostCumulativeEnabled(content.holdingCostCumulative().isSelected());
        config.setHoldingCostEventsEnabled(content.holdingCostEvents().isSelected());
        config.setFearGreedEnabled(content.fearGreed().isSelected());
        config.setSpectrumEnabled(content.spectrum().isSelected());
        config.setSpectrumColorMode((SpectrumColorMode) content.spectrumColorModeCombo().getSelectedItem());
        config.setSpectrumBucketMode((SpectrumBucketMode) content.spectrumBucketModeCombo().getSelectedItem());

        // Apply all indicator settings to the chart manager in one call
        chartPanel.getIndicatorManager().applyConfig(config);

        // Core charts
        chartPanel.setVolumeChartEnabled(content.volumeChart().isSelected());
        chartPanel.setEquityChartEnabled(content.equityChart().isSelected());
        chartPanel.setComparisonChartEnabled(content.comparisonChart().isSelected());
        chartPanel.setCapitalUsageChartEnabled(content.capitalUsageChart().isSelected());
        chartPanel.setTradePLChartEnabled(content.tradePLChart().isSelected());
        config.setVolumeChartEnabled(content.volumeChart().isSelected());
        config.setEquityChartEnabled(content.equityChart().isSelected());
        config.setComparisonChartEnabled(content.comparisonChart().isSelected());
        config.setCapitalUsageChartEnabled(content.capitalUsageChart().isSelected());
        config.setTradePLChartEnabled(content.tradePLChart().isSelected());

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
