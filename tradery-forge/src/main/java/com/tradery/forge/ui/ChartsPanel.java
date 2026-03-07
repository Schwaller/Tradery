package com.tradery.forge.ui;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartInteractionManager;
import com.tradery.charts.core.ChartLifecycleManager;
import com.tradery.charts.renderer.TraderyCandlestickRenderer;
import com.tradery.core.model.Candle;
import com.tradery.core.model.Trade;
import com.tradery.charts.core.IndicatorType;
import com.tradery.charts.indicator.IndicatorChartsManager;
import com.tradery.ui.controls.ChartConfig;
import com.tradery.forge.ui.charts.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chart panel showing price candlesticks and equity curve.
 * Delegates to specialized managers for overlays, indicators, zoom, and crosshairs.
 */
public class ChartsPanel extends JPanel {

    // Core chart panels
    private org.jfree.chart.ChartPanel priceChartPanel;
    private org.jfree.chart.ChartPanel equityChartPanel;
    private org.jfree.chart.ChartPanel comparisonChartPanel;
    private org.jfree.chart.ChartPanel capitalUsageChartPanel;
    private org.jfree.chart.ChartPanel tradePLChartPanel;
    private org.jfree.chart.ChartPanel volumeChartPanel;

    // Core charts
    private JFreeChart priceChart;
    private JFreeChart equityChart;
    private JFreeChart comparisonChart;
    private JFreeChart capitalUsageChart;
    private JFreeChart tradePLChart;
    private JFreeChart volumeChart;

    // Managers
    private ForgeOverlayManager overlayManager;
    private ForgeDataProvider forgeDataProvider;
    private final IndicatorChartsManager indicatorManager;
    private final ChartZoomManager zoomManager;
    private final ChartLifecycleManager lifecycleManager;
    private final ChartInteractionManager interactionManager;
    private final SplitPaneLayoutManager splitLayoutManager;

    // Status callback
    private Consumer<String> onStatusUpdate;

    // Current data
    private List<Candle> currentCandles;
    private List<Trade> currentTrades;

    // Highlight state
    private List<org.jfree.chart.annotations.XYAnnotation> highlightAnnotations = new java.util.ArrayList<>();

    // Price opacity slider (overlaid on price chart)
    private JSlider priceOpacitySlider;

    // UI components
    private JPanel chartsContainer;
    private JPanel mainPanel;

    public ChartsPanel() {
        setLayout(new BorderLayout());
        setBorder(null);

        // Initialize non-chart-dependent managers first
        IndicatorDataService chartDataService = new IndicatorDataService();
        indicatorManager = new IndicatorChartsManager(
            chartDataService,
            ForgeChartProviders.premiumDataSource(),
            ForgeChartProviders.spectrumDataSource(),
            ForgeChartProviders.spectrumConfig()
        );
        zoomManager = new ChartZoomManager();
        interactionManager = new ChartInteractionManager();
        lifecycleManager = new ChartLifecycleManager(new ChartCoordinator(), interactionManager);
        interactionManager.setAxisPositionSupplier(() -> ChartConfig.getInstance().getPriceAxisPosition());
        splitLayoutManager = new SplitPaneLayoutManager();

        initializeCharts();

        // Initialize overlay manager with price chart
        overlayManager = new ForgeOverlayManager(priceChart);

        // Initialize ForgeDataProvider for tradery-charts integration
        forgeDataProvider = new ForgeDataProvider(new IndicatorDataService());
        overlayManager.setChartDataProvider(forgeDataProvider);

        // Wire footprint and DVP providers from forge data service
        overlayManager.setFootprintProfileProvider(ForgeChartProviders.footprintProfileProvider());
        overlayManager.setDailyProfileProvider(ForgeChartProviders.dailyProfileProvider());

        // Wire status listener for forge UI (footprint status bar)
        if (overlayManager.getFootprintHeatmapOverlay() != null) {
            overlayManager.getFootprintHeatmapOverlay().setStatusListener((status, detail, count) -> {
                var ctx = com.tradery.forge.ApplicationContext.getInstance();
                if (ctx != null) {
                    ctx.setFootprintStatus(
                        com.tradery.forge.ApplicationContext.ProfileStatus.valueOf(status), detail, count);
                }
            });
        }

        setupManagers();
        setupScrollableContainer();

        // Listen for chart config changes (axis position, etc.)
        ChartConfig.getInstance().addChangeListener(this::refreshTheme);
    }

    public void setOnStatusUpdate(Consumer<String> callback) {
        this.onStatusUpdate = callback;
        lifecycleManager.getCoordinator().setOnStatusUpdate(callback);
    }

    /**
     * Apply saved chart configuration from ChartConfig.
     * Call this after the panel is fully initialized.
     */
    public void applySavedConfig() {
        ChartConfig config = ChartConfig.getInstance();
        indicatorManager.applyConfig(config);

        // Apply core chart settings
        setVolumeChartEnabled(config.isVolumeChartEnabled());
        setEquityChartEnabled(config.isEquityChartEnabled());
        setComparisonChartEnabled(config.isComparisonChartEnabled());
        setCapitalUsageChartEnabled(config.isCapitalUsageChartEnabled());
        setTradePLChartEnabled(config.isTradePLChartEnabled());

        // Note: Overlays are applied in updateCharts() when candles are available
    }

    /**
     * Apply saved overlay configuration. Call this after candles are loaded.
     */
    public void applySavedOverlays(List<Candle> candles) {
        ChartConfig config = ChartConfig.getInstance();

        // Apply multiple SMA overlays
        overlayManager.clearAllSmaOverlays();
        for (int period : config.getSmaPeriods()) {
            overlayManager.addSmaOverlay(period, candles);
        }

        // Apply multiple EMA overlays
        overlayManager.clearAllEmaOverlays();
        for (int period : config.getEmaPeriods()) {
            overlayManager.addEmaOverlay(period, candles);
        }

        if (config.isBollingerEnabled()) {
            overlayManager.setBollingerOverlay(config.getBollingerPeriod(), config.getBollingerStdDev(), candles);
        }
        if (config.isHighLowEnabled()) {
            overlayManager.setHighLowOverlay(config.getHighLowPeriod(), candles);
        }
        if (config.isMayerEnabled()) {
            overlayManager.setMayerMultipleEnabled(true, config.getMayerPeriod());
        }
        if (config.isDailyPocEnabled()) {
            overlayManager.setDailyPocOverlay(candles);
        }
        if (config.isFloatingPocEnabled()) {
            overlayManager.setFloatingPocOverlay(candles, config.getFloatingPocPeriod());
        }
        if (config.isVwapEnabled()) {
            overlayManager.setVwapOverlay(candles);
        }
        if (config.isRayOverlayEnabled()) {
            overlayManager.setRayOverlay(true, config.getRayLookback(), config.getRaySkip(), candles);
            overlayManager.setRayShowHistoric(config.isRayHistoricEnabled());
        }
        if (config.isIchimokuEnabled()) {
            overlayManager.setIchimokuOverlay(
                    config.getIchimokuConversionPeriod(),
                    config.getIchimokuBasePeriod(),
                    config.getIchimokuSpanBPeriod(),
                    config.getIchimokuDisplacement(),
                    candles
            );
        }
        if (config.isDailyVolumeProfileEnabled()) {
            overlayManager.setDailyVolumeProfileOverlay(
                    candles,
                    config.getDailyVolumeProfileBins(),
                    70.0,
                    config.getDailyVolumeProfileWidth()
            );
        }
        // Pivot Points overlay (tradery-charts)
        if (config.isPivotPointsEnabled()) {
            setPivotPointsOverlay(config.isPivotPointsShowR3S3());
        }
        // ATR Bands overlay (tradery-charts)
        if (config.isAtrBandsEnabled()) {
            setAtrBandsOverlay(config.getAtrBandsPeriod(), config.getAtrBandsMultiplier());
        }
        // Supertrend overlay (tradery-charts)
        if (config.isSupertrendEnabled()) {
            setSupertrendOverlay(config.getSupertrendPeriod(), config.getSupertrendMultiplier());
        }
        // Keltner Channel overlay (tradery-charts)
        if (config.isKeltnerEnabled()) {
            setKeltnerOverlay(config.getKeltnerEmaPeriod(), config.getKeltnerAtrPeriod(), config.getKeltnerMultiplier());
        }
        // Donchian Channel overlay (tradery-charts)
        if (config.isDonchianEnabled()) {
            setDonchianOverlay(config.getDonchianPeriod(), config.isDonchianShowMiddle());
        }
        // Note: Footprint heatmap is restored when aggTrades arrive (via refreshOrderflowCharts)

        // Phase overlays are applied asynchronously by ProjectWindow after candles load
    }

    private void initializeCharts() {
        // Price chart
        priceChart = ChartFactory.createTimeSeriesChart(
                null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(priceChart, "Price");
        priceChartPanel = createChartPanel(priceChart);

        // Equity chart
        equityChart = ChartFactory.createTimeSeriesChart(
                null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(equityChart, "Equity");
        equityChartPanel = createChartPanel(equityChart);

        // Comparison chart
        comparisonChart = ChartFactory.createTimeSeriesChart(
                null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(comparisonChart, "Strategy vs Buy & Hold");
        comparisonChartPanel = createChartPanel(comparisonChart);

        // Capital usage chart
        capitalUsageChart = ChartFactory.createTimeSeriesChart(
                null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(capitalUsageChart, "Capital Usage");
        capitalUsageChartPanel = createChartPanel(capitalUsageChart);

        // Trade P&L chart
        tradePLChart = ChartFactory.createTimeSeriesChart(
                null, null, null, new TimeSeriesCollection(), false, true, false);
        ChartStyles.stylizeChart(tradePLChart, "Trade P&L %");
        tradePLChartPanel = createChartPanel(tradePLChart);

        // Volume chart
        volumeChart = ChartFactory.createXYBarChart(
                null, null, true, null, new XYSeriesCollection(),
                org.jfree.chart.plot.PlotOrientation.VERTICAL, false, false, false);
        ChartStyles.stylizeChart(volumeChart, "Volume");
        volumeChartPanel = createChartPanel(volumeChart);
    }

    private org.jfree.chart.ChartPanel createChartPanel(JFreeChart chart) {
        return ChartPanelFactory.create(chart);
    }

    private void setupManagers() {
        // Setup zoom manager
        zoomManager.setIndicatorManager(indicatorManager);
        zoomManager.setOnLayoutChange(this::updateChartLayout);

        org.jfree.chart.ChartPanel[] coreChartPanels = {
                priceChartPanel, volumeChartPanel, equityChartPanel,
                comparisonChartPanel, capitalUsageChartPanel, tradePLChartPanel
        };
        zoomManager.createWrappers(coreChartPanels);

        // Add price opacity slider overlaid on price chart, next to "Price" label
        addPriceOpacitySlider();

        // Setup indicator manager
        indicatorManager.setOnLayoutChange(this::updateChartLayout);
        indicatorManager.createWrappers(this::toggleIndicatorZoom, this::toggleIndicatorFullScreen, zoomManager::exitFullScreen);

        // Register all charts with lifecycle manager (crosshairs + zoom/pan + listeners)
        registerAllCharts();

        // Sync domain axes via coordinator
        lifecycleManager.getCoordinator().syncDomainAxes();
    }

    private void addPriceOpacitySlider() {
        JLayeredPane layeredPane = zoomManager.getLayeredPane(0);
        if (layeredPane == null) return;

        priceOpacitySlider = new JSlider(0, 100, ChartConfig.getInstance().getPriceOpacity());
        priceOpacitySlider.setOpaque(false);
        priceOpacitySlider.setPreferredSize(new Dimension(60, 16));
        priceOpacitySlider.setToolTipText("Price opacity: " + priceOpacitySlider.getValue() + "%");
        priceOpacitySlider.addChangeListener(e -> {
            int value = priceOpacitySlider.getValue();
            priceOpacitySlider.setToolTipText("Price opacity: " + value + "%");
            if (!priceOpacitySlider.getValueIsAdjusting()) {
                ChartConfig.getInstance().setPriceOpacity(value);
                refreshPriceChart();
            }
        });

        // Position dynamically based on plot area
        repositionPriceOpacitySlider();
        layeredPane.add(priceOpacitySlider, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                repositionPriceOpacitySlider();
            }
        });
    }

    private void repositionPriceOpacitySlider() {
        if (priceOpacitySlider == null || priceChartPanel == null) return;
        String axisPos = ChartConfig.getInstance().getPriceAxisPosition();
        int xOffset = "right".equals(axisPos) ? 0 : 60;
        var info = priceChartPanel.getChartRenderingInfo();
        if (info != null && info.getPlotInfo() != null) {
            java.awt.geom.Rectangle2D plotArea = info.getPlotInfo().getPlotArea();
            if (plotArea != null && plotArea.getHeight() > 0) {
                int plotTop = (int) plotArea.getY();
                int yOffset = (int) (plotArea.getHeight() * 0.02);
                priceOpacitySlider.setBounds(42 + xOffset, 20 + plotTop + yOffset, 60, 16);
                return;
            }
        }
        priceOpacitySlider.setBounds(42 + xOffset, 30, 60, 16);
    }

    private void toggleIndicatorZoom(int index) {
        zoomManager.toggleIndicatorZoom(index);
    }

    private void toggleIndicatorFullScreen(int index) {
        zoomManager.toggleIndicatorFullScreen(index);
    }

    private void setupScrollableContainer() {
        chartsContainer = new JPanel(new GridBagLayout());
        chartsContainer.setBorder(null);

        // Register chart wrappers with split layout manager for persistence
        registerPanelsWithSplitLayoutManager();

        updateChartLayout();

        // Time scrollbar for fixed-width mode
        JScrollBar timeScrollBar = zoomManager.createTimeScrollBar();
        timeScrollBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateVisibleTimeRange();
            }
        });

        // Set up double-click callbacks for core chart panels
        org.jfree.chart.ChartPanel[] corePanels = {
                priceChartPanel, equityChartPanel, comparisonChartPanel,
                capitalUsageChartPanel, tradePLChartPanel, volumeChartPanel
        };
        int[] coreIndices = {0, 2, 3, 4, 5, 1}; // Map to zoomManager indices
        for (int i = 0; i < corePanels.length; i++) {
            final int chartIndex = coreIndices[i];
            Runnable fullScreenCallback = () -> zoomManager.toggleFullScreen(chartIndex);
            lifecycleManager.setDoubleClickCallback(corePanels[i], fullScreenCallback);
            ChartPanelFactory.setFullScreenCallback(corePanels[i], fullScreenCallback);
        }

        // Set up double-click callbacks for indicator chart panels
        for (IndicatorType type : IndicatorType.values()) {
            org.jfree.chart.ChartPanel panel = indicatorManager.getChartPanel(type);
            final int ordinal = type.ordinal();
            Runnable fullScreenCallback = () -> zoomManager.toggleIndicatorFullScreen(ordinal);
            lifecycleManager.setDoubleClickCallback(panel, fullScreenCallback);
            ChartPanelFactory.setFullScreenCallback(panel, fullScreenCallback);
        }

        mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBorder(null);
        mainPanel.add(chartsContainer, BorderLayout.CENTER);
        mainPanel.add(timeScrollBar, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private void registerPanelsWithSplitLayoutManager() {
        // Register core chart wrappers with identifiers for divider position persistence
        JPanel[] wrappers = zoomManager.getChartWrappers();
        String[] coreIds = {"price", "volume", "equity", "comparison", "capital_usage", "trade_pl"};
        for (int i = 0; i < wrappers.length && i < coreIds.length; i++) {
            if (wrappers[i] != null) {
                splitLayoutManager.registerPanel(wrappers[i], coreIds[i]);
            }
        }

        // Register indicator chart wrappers
        for (IndicatorType type : IndicatorType.values()) {
            splitLayoutManager.registerPanel(indicatorManager.getChartWrapper(type), type.name().toLowerCase());
        }
    }

    private void registerAllCharts() {
        // Register all core charts with lifecycle manager (crosshairs + zoom/pan + listeners)
        lifecycleManager.addChart(priceChart, priceChartPanel);
        lifecycleManager.addChart(volumeChart, volumeChartPanel);
        lifecycleManager.addChart(equityChart, equityChartPanel);
        lifecycleManager.addChart(comparisonChart, comparisonChartPanel);
        lifecycleManager.addChart(capitalUsageChart, capitalUsageChartPanel);
        lifecycleManager.addChart(tradePLChart, tradePLChartPanel);

        // Register all indicator charts
        for (IndicatorType type : IndicatorType.values()) {
            lifecycleManager.addChart(indicatorManager.getChart(type), indicatorManager.getChartPanel(type));
        }
    }

    private void updateChartLayout() {
        // Save current divider positions before rebuilding layout
        splitLayoutManager.saveDividerPositions();

        // Include ALL charts so axis visibility can be controlled for each
        java.util.List<JFreeChart> allChartsList = new java.util.ArrayList<>();
        java.util.List<JPanel> allWrappersList = new java.util.ArrayList<>();
        allChartsList.add(priceChart);
        allWrappersList.add(zoomManager.getChartWrappers()[0]);
        allChartsList.add(volumeChart);
        allWrappersList.add(zoomManager.getChartWrappers()[1]);
        for (IndicatorType type : IndicatorType.values()) {
            allChartsList.add(indicatorManager.getChart(type));
            allWrappersList.add(indicatorManager.getChartWrapper(type));
        }
        allChartsList.add(equityChart); allWrappersList.add(zoomManager.getChartWrappers()[2]);
        allChartsList.add(comparisonChart); allWrappersList.add(zoomManager.getChartWrappers()[3]);
        allChartsList.add(capitalUsageChart); allWrappersList.add(zoomManager.getChartWrappers()[4]);
        allChartsList.add(tradePLChart); allWrappersList.add(zoomManager.getChartWrappers()[5]);
        JFreeChart[] allCharts = allChartsList.toArray(new JFreeChart[0]);
        JPanel[] allWrappers = allWrappersList.toArray(new JPanel[0]);

        // Check if in full-screen mode - use simple layout for single chart
        if (zoomManager.getFullScreenChartIndex() >= 0 || zoomManager.getFullScreenIndicatorIndex() >= 0) {
            // Full-screen mode: reset to GridBagLayout and use existing layout logic
            splitLayoutManager.cleanup();
            chartsContainer.setLayout(new GridBagLayout());
            zoomManager.updateChartLayout(chartsContainer, allCharts, allWrappers);
            return;
        }

        // Build list of visible charts for split pane layout
        java.util.List<JPanel> visibleCharts = buildVisibleChartsList();

        if (visibleCharts.size() <= 1) {
            // Single chart: reset to GridBagLayout and use simple layout
            splitLayoutManager.cleanup();
            chartsContainer.setLayout(new GridBagLayout());
            zoomManager.updateChartLayout(chartsContainer, allCharts, allWrappers);
            return;
        }

        // Use split pane layout for multiple charts
        chartsContainer.removeAll();
        chartsContainer.setLayout(new BorderLayout());
        chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR());

        Component splitLayout = splitLayoutManager.buildLayout(visibleCharts);
        chartsContainer.add(splitLayout, BorderLayout.CENTER);

        // Update time axis visibility - show on first and last chart
        java.util.List<org.jfree.chart.ChartPanel> visibleChartPanels = buildVisibleChartPanelsList();
        lifecycleManager.updateTimeAxisVisibility(visibleChartPanels);

        chartsContainer.revalidate();
        chartsContainer.repaint();
    }

    private java.util.List<JPanel> buildVisibleChartsList() {
        java.util.List<JPanel> visibleCharts = new java.util.ArrayList<>();
        JPanel[] chartWrappers = zoomManager.getChartWrappers();

        // Price chart - always shown
        visibleCharts.add(chartWrappers[0]);

        // Volume chart
        if (zoomManager.isVolumeChartEnabled()) {
            visibleCharts.add(chartWrappers[1]);
        }

        // Indicator charts
        visibleCharts.addAll(indicatorManager.getVisibleWrappers());

        // Core charts at the end
        if (zoomManager.isEquityChartEnabled()) {
            visibleCharts.add(chartWrappers[2]);
        }
        if (zoomManager.isComparisonChartEnabled()) {
            visibleCharts.add(chartWrappers[3]);
        }
        if (zoomManager.isCapitalUsageChartEnabled()) {
            visibleCharts.add(chartWrappers[4]);
        }
        if (zoomManager.isTradePLChartEnabled()) {
            visibleCharts.add(chartWrappers[5]);
        }

        return visibleCharts;
    }

    /**
     * Build ordered list of visible chart panels (matching the visible wrappers order).
     */
    private java.util.List<org.jfree.chart.ChartPanel> buildVisibleChartPanelsList() {
        java.util.List<org.jfree.chart.ChartPanel> panels = new java.util.ArrayList<>();
        JPanel[] chartWrappers = zoomManager.getChartWrappers();

        // Price chart - always shown
        panels.add(priceChartPanel);

        // Volume chart
        if (zoomManager.isVolumeChartEnabled()) {
            panels.add(volumeChartPanel);
        }

        // Indicator charts
        for (IndicatorType type : IndicatorType.values()) {
            if (indicatorManager.isEnabled(type)) {
                panels.add(indicatorManager.getChartPanel(type));
            }
        }

        // Core charts at the end
        if (zoomManager.isEquityChartEnabled()) {
            panels.add(equityChartPanel);
        }
        if (zoomManager.isComparisonChartEnabled()) {
            panels.add(comparisonChartPanel);
        }
        if (zoomManager.isCapitalUsageChartEnabled()) {
            panels.add(capitalUsageChartPanel);
        }
        if (zoomManager.isTradePLChartEnabled()) {
            panels.add(tradePLChartPanel);
        }

        return panels;
    }

    // ===== Public API =====

    public void setFixedWidthMode(boolean enabled) {
        zoomManager.setFixedWidthMode(enabled);
        interactionManager.setFixedWidthMode(enabled, () -> zoomManager.getTimeScrollBar());
        updateFixedWidthMode();
        if (zoomManager.isFitYAxisToVisible()) {
            updateYAxisAutoRange();
        }
    }

    public void setFitYAxisToVisibleData(boolean enabled) {
        zoomManager.setFitYAxisToVisible(enabled);
        updateYAxisAutoRange();
    }

    private void updateFixedWidthMode() {
        if (currentCandles == null || currentCandles.isEmpty()) {
            zoomManager.getTimeScrollBar().setVisible(false);
            return;
        }
        zoomManager.updateFixedWidthMode(
                chartsContainer.getWidth(),
                currentCandles.size(),
                this::updateVisibleTimeRange
        );
        if (!zoomManager.isFixedWidthMode()) {
            resetDomainAxisRange();
        }
    }

    private void updateVisibleTimeRange() {
        if (!zoomManager.isFixedWidthMode() || currentCandles == null || currentCandles.isEmpty()) return;

        JScrollBar scrollBar = zoomManager.getTimeScrollBar();
        int startIndex = scrollBar.getValue();
        int visibleCandles = scrollBar.getVisibleAmount();
        int endIndex = Math.min(startIndex + visibleCandles, currentCandles.size() - 1);
        startIndex = Math.max(0, startIndex);

        if (startIndex >= currentCandles.size() || endIndex < 0) return;

        long startTime = currentCandles.get(startIndex).timestamp();
        long endTime = currentCandles.get(endIndex).timestamp();

        long range = endTime - startTime;
        long padding = range / 50;
        startTime -= padding;
        endTime += padding;

        setDomainAxisRange(startTime, endTime);

        if (zoomManager.isFitYAxisToVisible()) {
            updateYAxisAutoRange();
        }
    }

    private void setDomainAxisRange(long startTime, long endTime) {
        JFreeChart[] charts = {priceChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart};
        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
                dateAxis.setAutoRange(false);
                dateAxis.setRange(new Date(startTime), new Date(endTime));
            }
        }
    }

    /**
     * Snap the chart view to the most recent candle (end of data).
     */
    public void snapToNow() {
        if (currentCandles == null || currentCandles.isEmpty()) return;

        if (zoomManager.isFixedWidthMode()) {
            JScrollBar scrollBar = zoomManager.getTimeScrollBar();
            scrollBar.setValue(scrollBar.getMaximum() - scrollBar.getVisibleAmount());
        } else {
            // Show last N candles based on a reasonable window
            int totalCandles = currentCandles.size();
            int visibleCandles = Math.min(200, totalCandles);
            int startIndex = totalCandles - visibleCandles;

            long startTime = currentCandles.get(startIndex).timestamp();
            long endTime = currentCandles.get(totalCandles - 1).timestamp();
            long padding = (endTime - startTime) / 50;
            setDomainAxisRange(startTime - padding, endTime + padding);
        }
    }

    private void resetDomainAxisRange() {
        if (currentCandles == null || currentCandles.isEmpty()) return;

        long startTime = currentCandles.get(0).timestamp();
        long endTime = currentCandles.get(currentCandles.size() - 1).timestamp();
        java.util.List<JFreeChart> charts = new java.util.ArrayList<>(java.util.List.of(
                priceChart, volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart));
        charts.addAll(indicatorManager.getAllCharts());

        for (JFreeChart chart : charts) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setAutoRange(false);
                axis.setRange(startTime, endTime);
            }
        }
    }

    public void updateYAxisAutoRange() {
        boolean fitYAxis = zoomManager.isFitYAxisToVisible();
        JFreeChart[] charts = {priceChart, volumeChart, equityChart, comparisonChart, tradePLChart};

        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            ValueAxis rangeAxis = plot.getRangeAxis();

            if (fitYAxis) {
                rangeAxis.setAutoRange(true);
                plot.configureRangeAxes();
            } else {
                rangeAxis.setAutoRange(true);
                DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
                double domainLower = domainAxis.getLowerBound();
                double domainUpper = domainAxis.getUpperBound();
                domainAxis.setAutoRange(true);
                plot.configureRangeAxes();
                double fullLower = rangeAxis.getLowerBound();
                double fullUpper = rangeAxis.getUpperBound();
                domainAxis.setAutoRange(false);
                domainAxis.setRange(domainLower, domainUpper);
                rangeAxis.setAutoRange(false);
                rangeAxis.setRange(fullLower, fullUpper);
            }
        }

        // Capital usage: 0-100% when not fitting visible
        if (capitalUsageChart != null) {
            ValueAxis capitalAxis = capitalUsageChart.getXYPlot().getRangeAxis();
            if (fitYAxis) {
                capitalAxis.setAutoRange(true);
                capitalUsageChart.getXYPlot().configureRangeAxes();
            } else {
                capitalAxis.setAutoRange(false);
                capitalAxis.setRange(-5, 105);
            }
        }

        indicatorManager.updateYAxisAutoRange(fitYAxis);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (zoomManager.isFixedWidthMode()) {
            SwingUtilities.invokeLater(this::updateFixedWidthMode);
        }
    }

    // ===== Overlay Delegation =====

    public void setSmaOverlay(int period, List<Candle> candles) {
        overlayManager.setSmaOverlay(period, candles);
    }

    public void clearSmaOverlay() {
        overlayManager.clearSmaOverlay();
    }

    public boolean isSmaEnabled() {
        return overlayManager.isSmaEnabled();
    }

    // Multiple SMA overlay support
    public void addSmaOverlay(int period, List<Candle> candles) {
        overlayManager.addSmaOverlay(period, candles);
    }

    public void removeSmaOverlay(int period) {
        overlayManager.removeSmaOverlay(period);
    }

    public void setEmaOverlay(int period, List<Candle> candles) {
        overlayManager.setEmaOverlay(period, candles);
    }

    public void clearEmaOverlay() {
        overlayManager.clearEmaOverlay();
    }

    public boolean isEmaEnabled() {
        return overlayManager.isEmaEnabled();
    }

    // Multiple EMA overlay support
    public void addEmaOverlay(int period, List<Candle> candles) {
        overlayManager.addEmaOverlay(period, candles);
    }

    public void removeEmaOverlay(int period) {
        overlayManager.removeEmaOverlay(period);
    }

    public void setBollingerOverlay(int period, double stdDevMultiplier, List<Candle> candles) {
        overlayManager.setBollingerOverlay(period, stdDevMultiplier, candles);
    }

    public void clearBollingerOverlay() {
        overlayManager.clearBollingerOverlay();
    }

    public boolean isBollingerEnabled() {
        return overlayManager.isBollingerEnabled();
    }

    public void setHighLowOverlay(int period, List<Candle> candles) {
        overlayManager.setHighLowOverlay(period, candles);
    }

    public void clearHighLowOverlay() {
        overlayManager.clearHighLowOverlay();
    }

    public boolean isHighLowEnabled() {
        return overlayManager.isHighLowEnabled();
    }

    public void setMayerMultipleEnabled(boolean enabled, int period) {
        overlayManager.setMayerMultipleEnabled(enabled, period);
    }

    public boolean isMayerMultipleEnabled() {
        return overlayManager.isMayerMultipleEnabled();
    }

    public void setDailyPocOverlay(List<Candle> candles) {
        overlayManager.setDailyPocOverlay(candles);
    }

    public void clearDailyPocOverlay() {
        overlayManager.clearDailyPocOverlay();
    }

    public boolean isDailyPocEnabled() {
        return overlayManager.isDailyPocEnabled();
    }

    public void setFloatingPocOverlay(List<Candle> candles, int period) {
        overlayManager.setFloatingPocOverlay(candles, period);
    }

    public void clearFloatingPocOverlay() {
        overlayManager.clearFloatingPocOverlay();
    }

    public boolean isFloatingPocEnabled() {
        return overlayManager.isFloatingPocEnabled();
    }

    // ===== VWAP Overlay Delegation =====

    public void setVwapOverlay(List<Candle> candles) {
        overlayManager.setVwapOverlay(candles);
    }

    public void clearVwapOverlay() {
        overlayManager.clearVwapOverlay();
    }

    public boolean isVwapEnabled() {
        return overlayManager.isVwapEnabled();
    }

    // ===== Daily Volume Profile Overlay Delegation =====

    public void setDailyVolumeProfileOverlay(List<Candle> candles, int numBins, double valueAreaPct, int histogramWidth) {
        overlayManager.setDailyVolumeProfileOverlay(candles, numBins, valueAreaPct, histogramWidth);
    }

    public void setDailyVolumeProfileOverlay(List<Candle> candles) {
        overlayManager.setDailyVolumeProfileOverlay(candles);
    }

    public void clearDailyVolumeProfileOverlay() {
        overlayManager.clearDailyVolumeProfileOverlay();
    }

    public boolean isDailyVolumeProfileEnabled() {
        return overlayManager.isDailyVolumeProfileEnabled();
    }

    // ===== Footprint Heatmap Overlay Delegation =====

    public void setFootprintHeatmapEnabled(boolean enabled) {
        ChartConfig config = ChartConfig.getInstance();
        config.setFootprintHeatmapEnabled(enabled);

        if (enabled) {
            // Request data - overlay manages its own IndicatorPage
            overlayManager.updateFootprintHeatmapOverlay();
        } else {
            // Clear - overlay releases its IndicatorPage when disabled
            overlayManager.clearFootprintHeatmapOverlay();
        }
    }

    public boolean isFootprintHeatmapEnabled() {
        return overlayManager.isFootprintHeatmapEnabled();
    }

    public void clearFootprintHeatmapOverlay() {
        overlayManager.clearFootprintHeatmapOverlay();
    }

    /**
     * Refresh footprint heatmap with current config (call when display mode changes).
     */
    public void refreshFootprintHeatmap() {
        if (ChartConfig.getInstance().isFootprintHeatmapEnabled()) {
            overlayManager.updateFootprintHeatmapOverlay();
        }
    }

    // ===== Ray Overlay Delegation =====

    public void setRayOverlay(boolean enabled, int lookback, int skip) {
        overlayManager.setRayOverlay(enabled, lookback, skip, currentCandles);
    }

    public void clearRayOverlay() {
        overlayManager.clearRayOverlay();
    }

    public boolean isRayOverlayEnabled() {
        return overlayManager.isRayOverlayEnabled();
    }

    public int getRayLookback() {
        return overlayManager.getRayLookback();
    }

    public int getRaySkip() {
        return overlayManager.getRaySkip();
    }

    public void setRayShowResistance(boolean show) {
        overlayManager.setRayShowResistance(show);
    }

    public boolean isRayShowResistance() {
        return overlayManager.isRayShowResistance();
    }

    public void setRayShowSupport(boolean show) {
        overlayManager.setRayShowSupport(show);
    }

    public boolean isRayShowSupport() {
        return overlayManager.isRayShowSupport();
    }

    public void setRayShowHistoric(boolean show) {
        overlayManager.setRayShowHistoric(show);
    }

    public boolean isRayShowHistoric() {
        return overlayManager.isRayShowHistoric();
    }

    // ===== Ichimoku Cloud Overlay =====

    public void setIchimokuOverlay(int conversionPeriod, int basePeriod, int spanBPeriod, int displacement) {
        overlayManager.setIchimokuOverlay(conversionPeriod, basePeriod, spanBPeriod, displacement, currentCandles);
    }

    public void clearIchimokuOverlay() {
        overlayManager.clearIchimokuOverlay();
    }

    public boolean isIchimokuEnabled() {
        return overlayManager.isIchimokuEnabled();
    }

    // ===== Indicator Chart Access =====

    public IndicatorChartsManager getIndicatorManager() {
        return indicatorManager;
    }

    public boolean isAnyOrderflowChartEnabled() {
        return indicatorManager.isAnyOrderflowEnabled();
    }

    public void setIndicatorEngine(com.tradery.core.indicators.IndicatorEngine engine) {
        indicatorManager.setIndicatorEngine(engine);
        forgeDataProvider.setIndicatorEngine(engine);
    }

    // ===== Core Chart Toggles =====

    public void setVolumeChartEnabled(boolean enabled) {
        zoomManager.setVolumeChartEnabled(enabled);
    }

    public boolean isVolumeChartEnabled() {
        return zoomManager.isVolumeChartEnabled();
    }

    public void setEquityChartEnabled(boolean enabled) {
        zoomManager.setEquityChartEnabled(enabled);
    }

    public boolean isEquityChartEnabled() {
        return zoomManager.isEquityChartEnabled();
    }

    public void setComparisonChartEnabled(boolean enabled) {
        zoomManager.setComparisonChartEnabled(enabled);
    }

    public boolean isComparisonChartEnabled() {
        return zoomManager.isComparisonChartEnabled();
    }

    public void setCapitalUsageChartEnabled(boolean enabled) {
        zoomManager.setCapitalUsageChartEnabled(enabled);
    }

    public boolean isCapitalUsageChartEnabled() {
        return zoomManager.isCapitalUsageChartEnabled();
    }

    public void setTradePLChartEnabled(boolean enabled) {
        zoomManager.setTradePLChartEnabled(enabled);
    }

    public boolean isTradePLChartEnabled() {
        return zoomManager.isTradePLChartEnabled();
    }

    /**
     * Refresh all chart styles when theme or axis position changes.
     */
    public void refreshTheme() {
        // Re-stylize all core charts
        ChartStyles.stylizeChart(priceChart, "Price");
        ChartStyles.stylizeChart(volumeChart, "Volume");
        ChartStyles.stylizeChart(equityChart, "Equity");
        ChartStyles.stylizeChart(comparisonChart, "Strategy vs Buy & Hold");
        ChartStyles.stylizeChart(capitalUsageChart, "Capital Usage");
        ChartStyles.stylizeChart(tradePLChart, "Trade P&L");

        // Re-stylize all indicator charts
        indicatorManager.refreshTheme();

        // Re-render spectrum with current color mode
        indicatorManager.refreshSpectrumChart();

        // Update price renderer colors for new theme
        XYPlot pricePlot = priceChart.getXYPlot();
        boolean candlestickMode = ChartConfig.getInstance().isCandlestickMode();
        int priceOpacity = ChartConfig.getInstance().getPriceOpacity();
        int alpha = (int) (priceOpacity * 2.55);

        if (candlestickMode) {
            if (pricePlot.getRenderer(0) instanceof TraderyCandlestickRenderer) {
                Color baseUp = com.tradery.charts.util.ChartStyles.getTheme().getCandleUpColor();
                Color baseDown = com.tradery.charts.util.ChartStyles.getTheme().getCandleDownColor();
                Color upColor = new Color(baseUp.getRed(), baseUp.getGreen(), baseUp.getBlue(), alpha);
                Color downColor = new Color(baseDown.getRed(), baseDown.getGreen(), baseDown.getBlue(), alpha);
                TraderyCandlestickRenderer renderer = new TraderyCandlestickRenderer(upColor, downColor);
                renderer.setCandleWidth(3.0);
                pricePlot.setRenderer(0, renderer);
            }
        } else {
            if (pricePlot.getRenderer(1) instanceof XYLineAndShapeRenderer lineRenderer) {
                Color base = com.tradery.charts.util.ChartStyles.getTheme().getPriceLineColor();
                Color priceLineColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                lineRenderer.setSeriesPaint(0, priceLineColor);
            }
            // Update cloud opacity too
            if (pricePlot.getRenderer(0) instanceof XYDifferenceRenderer cloudRenderer) {
                Color baseCloud = ChartStyles.HL_CLOUD_COLOR;
                int cloudAlpha = Math.min(baseCloud.getAlpha(), alpha);
                Color cloudColor = new Color(baseCloud.getRed(), baseCloud.getGreen(), baseCloud.getBlue(), cloudAlpha);
                cloudRenderer.setPositivePaint(cloudColor);
                cloudRenderer.setNegativePaint(cloudColor);
            }
        }

        // Update container background
        chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR());

        // Force repaint and reposition slider (plot area changes after repaint)
        repaint();
        SwingUtilities.invokeLater(this::repositionPriceOpacitySlider);
    }

    // ===== tradery-charts Integration =====

    // Pivot points overlay instance (using tradery-charts)
    private com.tradery.charts.overlay.PivotPointsOverlay pivotPointsOverlay;

    /**
     * Set pivot points overlay using tradery-charts PivotPointsOverlay.
     *
     * @param showR3S3 Whether to show extended R3/S3 levels
     */
    public void setPivotPointsOverlay(boolean showR3S3) {
        clearPivotPointsOverlay();  // Remove existing if any
        pivotPointsOverlay = new com.tradery.charts.overlay.PivotPointsOverlay(showR3S3);
        overlayManager.applyChartOverlay(pivotPointsOverlay);
    }

    /**
     * Clear pivot points overlay.
     */
    public void clearPivotPointsOverlay() {
        if (pivotPointsOverlay != null) {
            overlayManager.removeChartOverlay(pivotPointsOverlay);
            pivotPointsOverlay = null;
        }
    }

    /**
     * Check if pivot points overlay is enabled.
     */
    public boolean isPivotPointsEnabled() {
        return pivotPointsOverlay != null;
    }

    // ===== ATR Bands Overlay (tradery-charts) =====

    private com.tradery.charts.overlay.AtrBandsOverlay atrBandsOverlay;

    /**
     * Set ATR Bands overlay using tradery-charts AtrBandsOverlay.
     *
     * @param period     ATR calculation period
     * @param multiplier Band width multiplier
     */
    public void setAtrBandsOverlay(int period, double multiplier) {
        clearAtrBandsOverlay();  // Remove existing if any
        atrBandsOverlay = new com.tradery.charts.overlay.AtrBandsOverlay(period, multiplier);
        overlayManager.applyChartOverlay(atrBandsOverlay);
    }

    /**
     * Clear ATR Bands overlay.
     */
    public void clearAtrBandsOverlay() {
        if (atrBandsOverlay != null) {
            overlayManager.removeChartOverlay(atrBandsOverlay);
            atrBandsOverlay = null;
        }
    }

    /**
     * Check if ATR Bands overlay is enabled.
     */
    public boolean isAtrBandsEnabled() {
        return atrBandsOverlay != null;
    }

    // ===== Supertrend Overlay (tradery-charts) =====

    private com.tradery.charts.overlay.SupertrendOverlay supertrendOverlay;

    /**
     * Set Supertrend overlay using tradery-charts SupertrendOverlay.
     *
     * @param period     ATR period for Supertrend calculation
     * @param multiplier ATR multiplier for band width
     */
    public void setSupertrendOverlay(int period, double multiplier) {
        clearSupertrendOverlay();
        supertrendOverlay = new com.tradery.charts.overlay.SupertrendOverlay(period, multiplier);
        overlayManager.applyChartOverlay(supertrendOverlay);
    }

    /**
     * Clear Supertrend overlay.
     */
    public void clearSupertrendOverlay() {
        if (supertrendOverlay != null) {
            overlayManager.removeChartOverlay(supertrendOverlay);
            supertrendOverlay = null;
        }
    }

    /**
     * Check if Supertrend overlay is enabled.
     */
    public boolean isSupertrendEnabled() {
        return supertrendOverlay != null;
    }

    // ===== Keltner Channel Overlay (tradery-charts) =====

    private com.tradery.charts.overlay.KeltnerChannelOverlay keltnerOverlay;

    /**
     * Set Keltner Channel overlay using tradery-charts KeltnerChannelOverlay.
     *
     * @param emaPeriod  EMA period for the middle line
     * @param atrPeriod  ATR period for band calculation
     * @param multiplier ATR multiplier for band width
     */
    public void setKeltnerOverlay(int emaPeriod, int atrPeriod, double multiplier) {
        clearKeltnerOverlay();
        keltnerOverlay = new com.tradery.charts.overlay.KeltnerChannelOverlay(emaPeriod, atrPeriod, multiplier);
        overlayManager.applyChartOverlay(keltnerOverlay);
    }

    /**
     * Clear Keltner Channel overlay.
     */
    public void clearKeltnerOverlay() {
        if (keltnerOverlay != null) {
            overlayManager.removeChartOverlay(keltnerOverlay);
            keltnerOverlay = null;
        }
    }

    /**
     * Check if Keltner Channel overlay is enabled.
     */
    public boolean isKeltnerEnabled() {
        return keltnerOverlay != null;
    }

    // ===== Donchian Channel Overlay (tradery-charts) =====

    private com.tradery.charts.overlay.DonchianChannelOverlay donchianOverlay;

    /**
     * Set Donchian Channel overlay using tradery-charts DonchianChannelOverlay.
     *
     * @param period     Lookback period for highest high/lowest low
     * @param showMiddle Whether to show the middle line
     */
    public void setDonchianOverlay(int period, boolean showMiddle) {
        clearDonchianOverlay();
        donchianOverlay = new com.tradery.charts.overlay.DonchianChannelOverlay(period, showMiddle);
        overlayManager.applyChartOverlay(donchianOverlay);
    }

    /**
     * Clear Donchian Channel overlay.
     */
    public void clearDonchianOverlay() {
        if (donchianOverlay != null) {
            overlayManager.removeChartOverlay(donchianOverlay);
            donchianOverlay = null;
        }
    }

    /**
     * Check if Donchian Channel overlay is enabled.
     */
    public boolean isDonchianEnabled() {
        return donchianOverlay != null;
    }

    /**
     * Apply a tradery-charts ChartOverlay to the price chart.
     * Use this for overlays from the tradery-charts module.
     *
     * @param overlay The ChartOverlay to apply
     * @return true if applied successfully
     */
    public boolean applyChartOverlay(com.tradery.charts.overlay.ChartOverlay overlay) {
        return overlayManager.applyChartOverlay(overlay);
    }

    /**
     * Remove a tradery-charts ChartOverlay from the price chart.
     *
     * @param overlay The overlay to remove
     * @return true if removed successfully
     */
    public boolean removeChartOverlay(com.tradery.charts.overlay.ChartOverlay overlay) {
        return overlayManager.removeChartOverlay(overlay);
    }

    /**
     * Clear all tradery-charts overlays from the price chart.
     */
    public void clearChartOverlays() {
        overlayManager.clearChartOverlays();
    }

    /**
     * Set phase overlays from pre-computed data.
     */
    public void setPhaseOverlays(java.util.List<ForgeOverlayManager.PhaseOverlayData> phases) {
        overlayManager.setPhaseOverlays(phases);
    }

    /**
     * Clear all phase overlays.
     */
    public void clearPhaseOverlays() {
        overlayManager.clearPhaseOverlays();
    }

    /**
     * Get the ForgeDataProvider for advanced tradery-charts usage.
     */
    public ForgeDataProvider getForgeDataProvider() {
        return forgeDataProvider;
    }

    // ===== Chart Update Methods =====

    /**
     * Set data context for indicator background computation.
     * Call this before updateCharts() when candles change.
     */
    public void setIndicatorDataContext(List<Candle> candles, String symbol, String timeframe,
                                        String marketType, long startTime, long endTime) {
        if (candles != null && !candles.isEmpty()) {
            indicatorManager.setDataContext(candles, symbol, timeframe, startTime, endTime);
            overlayManager.setDataContext(symbol, timeframe, marketType);

            // Update ForgeDataProvider for tradery-charts overlays
            forgeDataProvider.setDataContext(candles, symbol, timeframe, marketType, startTime, endTime);
        }
    }

    public void updateCharts(List<Candle> candles, List<Trade> trades, double initialCapital) {
        if (candles == null || candles.isEmpty()) return;

        this.currentCandles = candles;
        this.currentTrades = trades;
        lifecycleManager.getCoordinator().setCandles(candles);
        overlayManager.setCandles(candles);
        clearTradeHighlight();

        // Date axis format adapts automatically via AdaptiveDateFormat

        updatePriceChart(candles, trades);
        updateEquityChart(candles, trades, initialCapital);
        updateComparisonChart(candles, trades, initialCapital);
        updateCapitalUsageChart(candles, trades, initialCapital);
        updateTradePLChart(candles, trades);
        updateVolumeChart(candles);

        // Pass trades for holding cost charts
        indicatorManager.setTrades(trades);
        indicatorManager.updateCharts(candles);

        // Refresh tradery-charts overlays
        overlayManager.refreshChartOverlays();

        // Set consistent domain axis range
        long startTime = candles.get(0).timestamp();
        long endTime = candles.get(candles.size() - 1).timestamp();
        java.util.List<JFreeChart> allChartsList2 = new java.util.ArrayList<>(java.util.List.of(
                priceChart, volumeChart, equityChart, comparisonChart, capitalUsageChart, tradePLChart));
        allChartsList2.addAll(indicatorManager.getAllCharts());
        for (JFreeChart chart : allChartsList2) {
            if (chart != null) {
                DateAxis axis = (DateAxis) chart.getXYPlot().getDomainAxis();
                axis.setAutoRange(false);
                axis.setRange(startTime, endTime);
            }
        }

        if (zoomManager.isFixedWidthMode()) {
            updateFixedWidthMode();
        }
    }

    // Date axis format is now handled automatically by AdaptiveDateFormat
    // which adapts based on the visible time range

    private void updatePriceChart(List<Candle> candles, List<Trade> trades) {
        XYPlot plot = priceChart.getXYPlot();

        // Clear annotations except title and overlays (footprint heatmap, daily volume profile)
        plot.getAnnotations().stream()
                .filter(a -> !(a instanceof XYTitleAnnotation))
                .filter(a -> !(a instanceof com.tradery.charts.overlay.footprint.FootprintHeatmapAnnotation))
                .filter(a -> !(a instanceof com.tradery.charts.overlay.DailyVolumeProfileAnnotation))
                .toList()
                .forEach(plot::removeAnnotation);

        boolean candlestickMode = ChartConfig.getInstance().isCandlestickMode();
        int priceOpacity = ChartConfig.getInstance().getPriceOpacity();
        int alpha = (int) (priceOpacity * 2.55);  // Convert 0-100 to 0-255

        if (candlestickMode) {
            // Candlestick chart
            OHLCSeries ohlcSeries = new OHLCSeries("Price");
            for (Candle c : candles) {
                ohlcSeries.add(new Millisecond(new Date(c.timestamp())),
                        c.open(), c.high(), c.low(), c.close());
            }
            OHLCSeriesCollection dataset = new OHLCSeriesCollection();
            dataset.addSeries(ohlcSeries);
            plot.setDataset(dataset);

            // Apply opacity to candle colors
            Color baseUp = com.tradery.charts.util.ChartStyles.getTheme().getCandleUpColor();
            Color baseDown = com.tradery.charts.util.ChartStyles.getTheme().getCandleDownColor();
            Color upColor = new Color(baseUp.getRed(), baseUp.getGreen(), baseUp.getBlue(), alpha);
            Color downColor = new Color(baseDown.getRed(), baseDown.getGreen(), baseDown.getBlue(), alpha);

            TraderyCandlestickRenderer renderer = new TraderyCandlestickRenderer(upColor, downColor);
            renderer.setCandleWidth(3.0);  // Fixed width in pixels
            plot.setRenderer(renderer);
        } else {
            // Line chart with high/low cloud from OHLC data

            // First, add high/low cloud as background (dataset index 0)
            TimeSeries highSeries = new TimeSeries("High");
            TimeSeries lowSeries = new TimeSeries("Low");
            for (Candle c : candles) {
                Millisecond time = new Millisecond(new Date(c.timestamp()));
                highSeries.addOrUpdate(time, c.high());
                lowSeries.addOrUpdate(time, c.low());
            }
            TimeSeriesCollection cloudDataset = new TimeSeriesCollection();
            cloudDataset.addSeries(highSeries);
            cloudDataset.addSeries(lowSeries);
            plot.setDataset(0, cloudDataset);

            // Use XYDifferenceRenderer for high/low cloud fill, with opacity applied
            Color baseCloud = ChartStyles.HL_CLOUD_COLOR;
            int cloudAlpha = Math.min(baseCloud.getAlpha(), alpha);
            Color cloudColor = new Color(baseCloud.getRed(), baseCloud.getGreen(), baseCloud.getBlue(), cloudAlpha);
            XYDifferenceRenderer cloudRenderer = new XYDifferenceRenderer(
                    cloudColor, cloudColor, false);
            cloudRenderer.setSeriesPaint(0, new Color(0, 0, 0, 0));  // Invisible lines
            cloudRenderer.setSeriesPaint(1, new Color(0, 0, 0, 0));
            plot.setRenderer(0, cloudRenderer);

            // Then, add close price line on top (dataset index 1)
            TimeSeries priceSeries = new TimeSeries("Price");
            for (Candle c : candles) {
                priceSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), c.close());
            }
            TimeSeriesCollection priceDataset = new TimeSeriesCollection(priceSeries);
            plot.setDataset(1, priceDataset);

            // Apply opacity to price line
            Color base = com.tradery.charts.util.ChartStyles.getTheme().getPriceLineColor();
            Color priceLineColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);

            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            renderer.setSeriesPaint(0, priceLineColor);
            renderer.setSeriesStroke(0, ChartStyles.LINE_STROKE);
            plot.setRenderer(1, renderer);
        }

        // Add trade lines
        addTradeAnnotations(plot, trades);
    }

    private void addTradeAnnotations(XYPlot plot, List<Trade> trades) {
        if (trades == null) return;

        java.util.List<Trade> validTrades = trades.stream()
                .filter(t -> t.exitTime() != null && t.exitPrice() != null && !"rejected".equals(t.exitReason()))
                .sorted((a, b) -> Long.compare(a.entryTime(), b.entryTime()))
                .toList();

        java.util.Map<String, java.util.List<Trade>> tradesByGroup = new java.util.LinkedHashMap<>();
        for (Trade t : validTrades) {
            String groupId = t.groupId() != null ? t.groupId() : "single-" + t.id();
            tradesByGroup.computeIfAbsent(groupId, k -> new java.util.ArrayList<>()).add(t);
        }

        for (java.util.List<Trade> group : tradesByGroup.values()) {
            if (group.size() == 1) {
                Trade t = group.get(0);
                boolean isWin = t.pnl() != null && t.pnl() > 0;
                Color color = isWin ? ChartStyles.WIN_COLOR : ChartStyles.LOSS_COLOR;

                XYLineAnnotation tradeLine = new XYLineAnnotation(
                        t.entryTime(), t.entryPrice(),
                        t.exitTime(), t.exitPrice(),
                        ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(tradeLine);
            } else {
                // DCA position
                double totalValue = 0, totalQuantity = 0, totalPnl = 0;
                long firstEntryTime = Long.MAX_VALUE, lastEntryTime = Long.MIN_VALUE;

                for (Trade t : group) {
                    totalValue += t.entryPrice() * t.quantity();
                    totalQuantity += t.quantity();
                    if (t.pnl() != null) totalPnl += t.pnl();
                    firstEntryTime = Math.min(firstEntryTime, t.entryTime());
                    lastEntryTime = Math.max(lastEntryTime, t.entryTime());
                }

                double avgEntryPrice = totalValue / totalQuantity;
                boolean isWin = totalPnl > 0;
                Color color = isWin ? ChartStyles.WIN_COLOR : ChartStyles.LOSS_COLOR;
                Trade lastTrade = group.get(0);

                Color verticalColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 89);
                for (Trade t : group) {
                    XYLineAnnotation verticalLine = new XYLineAnnotation(
                            t.entryTime(), t.entryPrice(),
                            t.entryTime(), avgEntryPrice,
                            ChartStyles.THIN_STROKE, verticalColor);
                    plot.addAnnotation(verticalLine);
                }

                XYLineAnnotation avgLine = new XYLineAnnotation(
                        firstEntryTime, avgEntryPrice,
                        lastEntryTime, avgEntryPrice,
                        ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(avgLine);

                long centerTime = (firstEntryTime + lastEntryTime) / 2;
                XYLineAnnotation exitLine = new XYLineAnnotation(
                        centerTime, avgEntryPrice,
                        lastTrade.exitTime(), lastTrade.exitPrice(),
                        ChartStyles.TRADE_LINE_STROKE, color);
                plot.addAnnotation(exitLine);

                // Dots at endpoints
                final long cTime = centerTime;
                final double avgPrice = avgEntryPrice;
                final long exitTime = lastTrade.exitTime();
                final double exitPrice = lastTrade.exitPrice();
                final Color dotColor = color;
                final double dotSize = 6.0;

                plot.addAnnotation(new org.jfree.chart.annotations.AbstractXYAnnotation() {
                    @Override
                    public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                                     ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                                     org.jfree.chart.plot.PlotRenderingInfo info) {
                        double x1 = domainAxis.valueToJava2D(cTime, dataArea, plot.getDomainAxisEdge());
                        double y1 = rangeAxis.valueToJava2D(avgPrice, dataArea, plot.getRangeAxisEdge());
                        double x2 = domainAxis.valueToJava2D(exitTime, dataArea, plot.getDomainAxisEdge());
                        double y2 = rangeAxis.valueToJava2D(exitPrice, dataArea, plot.getRangeAxisEdge());
                        g2.setColor(dotColor);
                        g2.fill(new Ellipse2D.Double(x1 - dotSize / 2, y1 - dotSize / 2, dotSize, dotSize));
                        g2.fill(new Ellipse2D.Double(x2 - dotSize / 2, y2 - dotSize / 2, dotSize, dotSize));
                    }
                });
            }
        }
    }

    /**
     * Highlight specific trades on the chart (draw highlight overlay without moving view).
     */
    public void highlightTrades(List<Trade> trades) {
        clearTradeHighlight();

        if (trades == null || trades.isEmpty()) {
            // Ensure ray overlay is refreshed when clearing highlights
            overlayManager.updateRayOverlay(currentCandles);
            return;
        }

        XYPlot plot = priceChart.getXYPlot();

        // Draw highlight for each trade (no zoom - just overlay)
        BasicStroke highlightStroke = new BasicStroke(4.0f);

        for (Trade t : trades) {
            long entryTime = t.entryTime();
            long exitTime = t.exitTime() != null ? t.exitTime() : entryTime;
            double entryPrice = t.entryPrice();
            double exitPrice = t.exitPrice() != null ? t.exitPrice() : entryPrice;

            // Draw highlight line (thicker, brighter)
            XYLineAnnotation highlight = new XYLineAnnotation(
                    entryTime, entryPrice,
                    exitTime, exitPrice,
                    highlightStroke, new Color(255, 215, 0)); // Gold
            plot.addAnnotation(highlight);
            highlightAnnotations.add(highlight);

            // Draw entry/exit markers
            double markerSize = 10.0;
            org.jfree.chart.annotations.AbstractXYAnnotation entryMarker =
                    new org.jfree.chart.annotations.AbstractXYAnnotation() {
                        @Override
                        public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                                         ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                                         org.jfree.chart.plot.PlotRenderingInfo info) {
                            double x = domainAxis.valueToJava2D(entryTime, dataArea, plot.getDomainAxisEdge());
                            double y = rangeAxis.valueToJava2D(entryPrice, dataArea, plot.getRangeAxisEdge());
                            g2.setColor(new Color(255, 215, 0));
                            g2.setStroke(new BasicStroke(2.0f));
                            g2.draw(new Ellipse2D.Double(x - markerSize / 2, y - markerSize / 2, markerSize, markerSize));
                            g2.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                        }
                    };
            plot.addAnnotation(entryMarker);
            highlightAnnotations.add(entryMarker);

            if (t.exitTime() != null && t.exitPrice() != null) {
                org.jfree.chart.annotations.AbstractXYAnnotation exitMarker =
                        new org.jfree.chart.annotations.AbstractXYAnnotation() {
                            @Override
                            public void draw(java.awt.Graphics2D g2, XYPlot plot, java.awt.geom.Rectangle2D dataArea,
                                             ValueAxis domainAxis, ValueAxis rangeAxis, int rendererIndex,
                                             org.jfree.chart.plot.PlotRenderingInfo info) {
                                double x = domainAxis.valueToJava2D(exitTime, dataArea, plot.getDomainAxisEdge());
                                double y = rangeAxis.valueToJava2D(exitPrice, dataArea, plot.getRangeAxisEdge());
                                g2.setColor(new Color(255, 215, 0));
                                g2.setStroke(new BasicStroke(2.0f));
                                g2.draw(new Ellipse2D.Double(x - markerSize / 2, y - markerSize / 2, markerSize, markerSize));
                                g2.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
                            }
                        };
                plot.addAnnotation(exitMarker);
                highlightAnnotations.add(exitMarker);
            }
        }

        // Ensure ray overlay is refreshed after adding highlights
        overlayManager.updateRayOverlay(currentCandles);
    }

    /**
     * Clear any trade highlight annotations.
     */
    public void clearTradeHighlight() {
        if (highlightAnnotations.isEmpty()) return;

        XYPlot plot = priceChart.getXYPlot();
        for (org.jfree.chart.annotations.XYAnnotation ann : highlightAnnotations) {
            plot.removeAnnotation(ann);
        }
        highlightAnnotations.clear();
    }

    private void updateEquityChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries equitySeries = new TimeSeries("Equity");
        double equity = initialCapital;

        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        if (!candles.isEmpty()) {
            equitySeries.addOrUpdate(new Millisecond(new Date(candles.get(0).timestamp())), equity);
        }

        for (Candle c : candles) {
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }
            equitySeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), equity);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(equitySeries);
        XYPlot plot = equityChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, ChartStyles.EQUITY_COLOR);
    }

    private void updateComparisonChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries strategySeries = new TimeSeries("Strategy");
        TimeSeries buyHoldSeries = new TimeSeries("Buy & Hold");

        double startPrice = candles.get(0).close();
        double equity = initialCapital;

        java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();
        if (trades != null) {
            for (Trade t : trades) {
                if (t.exitTime() != null && t.pnl() != null) {
                    tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                }
            }
        }

        for (Candle c : candles) {
            if (tradePnL.containsKey(c.timestamp())) {
                equity += tradePnL.get(c.timestamp());
            }
            double strategyReturn = ((equity - initialCapital) / initialCapital) * 100;
            double buyHoldReturn = ((c.close() - startPrice) / startPrice) * 100;

            Millisecond time = new Millisecond(new Date(c.timestamp()));
            strategySeries.addOrUpdate(time, strategyReturn);
            buyHoldSeries.addOrUpdate(time, buyHoldReturn);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(strategySeries);
        dataset.addSeries(buyHoldSeries);

        XYPlot plot = comparisonChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, ChartStyles.EQUITY_COLOR);
        plot.getRenderer().setSeriesPaint(1, ChartStyles.BUY_HOLD_COLOR);
    }

    private void updateCapitalUsageChart(List<Candle> candles, List<Trade> trades, double initialCapital) {
        TimeSeries usageSeries = new TimeSeries("Capital Usage");

        List<Trade> validTrades = trades == null ? List.of() : trades.stream()
                .filter(t -> t.quantity() > 0).toList();

        if (validTrades.isEmpty()) {
            for (Candle c : candles) {
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), 0.0);
            }
        } else {
            java.util.Map<Long, Double> entryValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> exitValues = new java.util.HashMap<>();
            java.util.Map<Long, Double> tradePnL = new java.util.HashMap<>();

            for (Trade t : validTrades) {
                double tradeValue = t.entryPrice() * t.quantity();
                entryValues.merge(t.entryTime(), tradeValue, Double::sum);
                if (t.exitTime() != null) {
                    exitValues.merge(t.exitTime(), tradeValue, Double::sum);
                    if (t.pnl() != null) {
                        tradePnL.merge(t.exitTime(), t.pnl(), Double::sum);
                    }
                }
            }

            double equity = initialCapital;
            double invested = 0;

            for (Candle c : candles) {
                if (tradePnL.containsKey(c.timestamp())) {
                    equity += tradePnL.get(c.timestamp());
                }
                if (entryValues.containsKey(c.timestamp())) {
                    invested += entryValues.get(c.timestamp());
                }
                if (exitValues.containsKey(c.timestamp())) {
                    invested -= exitValues.get(c.timestamp());
                }
                invested = Math.max(0, invested);

                double usagePercent = equity > 0 ? Math.min((invested / equity) * 100, 100.0) : 0;
                usageSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), usagePercent);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(usageSeries);
        XYPlot plot = capitalUsageChart.getXYPlot();
        plot.setDataset(dataset);
        plot.getRenderer().setSeriesPaint(0, ChartStyles.CAPITAL_USAGE_COLOR);
        plot.getRangeAxis().setRange(-5, 105);
    }

    private void updateTradePLChart(List<Candle> candles, List<Trade> trades) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();

        if (trades != null && !trades.isEmpty()) {
            java.util.Map<Long, Integer> timestampToIndex = new java.util.HashMap<>();
            for (int i = 0; i < candles.size(); i++) {
                timestampToIndex.put(candles.get(i).timestamp(), i);
            }

            int tradeNum = 0;
            for (Trade t : trades) {
                if (t.exitTime() == null) continue;

                Integer entryIdx = timestampToIndex.get(t.entryTime());
                Integer exitIdx = timestampToIndex.get(t.exitTime());
                if (entryIdx == null || exitIdx == null) continue;

                TimeSeries series = new TimeSeries("Trade " + (tradeNum + 1));
                double entryPrice = t.entryPrice();

                for (int i = entryIdx; i <= exitIdx && i < candles.size(); i++) {
                    Candle c = candles.get(i);
                    double plPercent = ((c.close() - entryPrice) / entryPrice) * 100;
                    series.addOrUpdate(new Millisecond(new Date(c.timestamp())), plPercent);
                }

                dataset.addSeries(series);
                tradeNum++;
            }

            XYPlot plot = tradePLChart.getXYPlot();
            plot.setDataset(dataset);
            for (int i = 0; i < tradeNum; i++) {
                plot.getRenderer().setSeriesPaint(i, ChartStyles.RAINBOW_COLORS[i % ChartStyles.RAINBOW_COLORS.length]);
            }
        } else {
            tradePLChart.getXYPlot().setDataset(dataset);
        }
    }

    private void updateVolumeChart(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        int lookback = 20;
        double[] avgVolumes = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - lookback + 1); j <= i; j++) {
                sum += candles.get(j).volume();
                count++;
            }
            avgVolumes[i] = sum / count;
        }

        XYSeries[] volumeSeries = new XYSeries[7];
        String[] seriesNames = {"Ultra Low", "Very Low", "Low", "Average", "High", "Very High", "Ultra High"};
        for (int i = 0; i < 7; i++) {
            volumeSeries[i] = new XYSeries(seriesNames[i]);
        }

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double relVol = c.volume() / avgVolumes[i];

            int seriesIdx;
            if (relVol >= 2.2) seriesIdx = 6;
            else if (relVol >= 1.8) seriesIdx = 5;
            else if (relVol >= 1.2) seriesIdx = 4;
            else if (relVol >= 0.8) seriesIdx = 3;
            else if (relVol >= 0.5) seriesIdx = 2;
            else if (relVol >= 0.3) seriesIdx = 1;
            else seriesIdx = 0;

            volumeSeries[seriesIdx].add(c.timestamp(), c.volume());
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (XYSeries series : volumeSeries) {
            dataset.addSeries(series);
        }

        XYPlot plot = volumeChart.getXYPlot();
        plot.setDataset(dataset);

        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
        for (int i = 0; i < ChartStyles.VOLUME_COLORS.length; i++) {
            renderer.setSeriesPaint(i, ChartStyles.VOLUME_COLORS[i]);
        }
        plot.setRenderer(renderer);
    }

    public void clear() {
        priceChart.getXYPlot().setDataset(null);
        equityChart.getXYPlot().setDataset(new TimeSeriesCollection());
        comparisonChart.getXYPlot().setDataset(new TimeSeriesCollection());
        capitalUsageChart.getXYPlot().setDataset(new TimeSeriesCollection());
        tradePLChart.getXYPlot().setDataset(new TimeSeriesCollection());
        volumeChart.getXYPlot().setDataset(new XYSeriesCollection());
        // Don't call overlayManager.clearAll() here — applySavedOverlays()
        // handles clearing and re-applying each overlay type.
        // Calling clearAll() destroys data-backed pages (aggTrades) that
        // will be immediately re-requested, causing expensive re-fetches.
    }

    // ===== Chart Refresh Methods (for async VIEW data loading) =====

    /**
     * Refresh the funding chart with new data.
     * Called when VIEW tier funding data arrives asynchronously.
     */
    public void refreshFundingChart() {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            indicatorManager.updateChart(IndicatorType.FUNDING, currentCandles);
        }
    }

    /**
     * Refresh the OI chart with new data.
     * Called when VIEW tier OI data arrives asynchronously.
     */
    public void refreshOiChart() {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            indicatorManager.updateChart(IndicatorType.OI, currentCandles);
        }
    }

    /**
     * Refresh orderflow charts (delta, CVD, volume ratio, etc.) with new data.
     * Called when VIEW tier aggTrades data arrives asynchronously.
     * Note: Daily volume profile is NOT refreshed here to avoid flickering.
     * It's calculated once when enabled via applySavedOverlays() or user toggle.
     */
    public void refreshOrderflowCharts() {
        refreshOrderflowCharts(null);
    }

    /**
     * Refresh orderflow charts with optional pre-loaded aggTrades.
     * When aggTrades are provided, the footprint overlay reuses them instead of
     * loading a duplicate page from the data service.
     */
    public void refreshOrderflowCharts(java.util.List<com.tradery.core.model.AggTrade> aggTrades) {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            indicatorManager.updateCharts(currentCandles,
                IndicatorType.DELTA, IndicatorType.CVD, IndicatorType.VOLUME_RATIO,
                IndicatorType.WHALE, IndicatorType.RETAIL);

            // Update footprint heatmap if enabled
            overlayManager.updateFootprintHeatmapOverlay();
        }
    }

    /**
     * Refresh premium chart with new data.
     * Called when VIEW tier premium data arrives asynchronously.
     */
    public void refreshPremiumChart() {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            indicatorManager.updateChart(IndicatorType.PREMIUM, currentCandles);
        }
    }

    public void refreshFearGreedChart() {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            indicatorManager.updateChart(IndicatorType.FEAR_GREED, currentCandles);
        }
    }

    /**
     * Refresh the price chart (e.g., when switching between line and candlestick mode).
     * Note: Daily volume profile annotation is preserved by updatePriceChart, no need to re-apply.
     */
    public void refreshPriceChart() {
        if (currentCandles != null && !currentCandles.isEmpty()) {
            updatePriceChart(currentCandles, currentTrades);
            // Re-apply ray overlay after chart update (ray annotations are cleared)
            overlayManager.updateRayOverlay(currentCandles);
        }
    }

    /**
     * Get the current candles for external use.
     */
    public List<Candle> getCurrentCandles() {
        return currentCandles;
    }

    /**
     * Release all resources (overlay pages, indicator pages, etc.).
     * Call when the owning ProjectWindow is closing.
     */
    public void dispose() {
        if (overlayManager != null) {
            overlayManager.dispose();
        }
        if (indicatorManager != null) {
            indicatorManager.dispose();
        }
    }
}
