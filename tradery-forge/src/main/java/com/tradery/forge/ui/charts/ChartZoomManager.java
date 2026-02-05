package com.tradery.forge.ui.charts;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.awt.*;

/**
 * Manages zoom state and fixed-width scrolling mode for charts.
 */
public class ChartZoomManager {

    private static final int MIN_CHART_HEIGHT = 60;
    private static final int PIXELS_PER_CANDLE = 4;

    // Core chart zoom state (-1 = none zoomed)
    private int zoomedChartIndex = -1;

    // Indicator chart zoom state (-1 = none, 0=RSI, 1=MACD, 2=ATR)
    private int indicatorZoomedIndex = -1;

    // Full screen state (-1 = none, index = chart in full screen)
    private int fullScreenChartIndex = -1;
    private int fullScreenIndicatorIndex = -1;

    // Fixed width mode support
    private boolean fixedWidthMode = false;
    private boolean fitYAxisToVisible = true;

    // Core chart enable state (default all on)
    private boolean volumeChartEnabled = true;
    private boolean equityChartEnabled = true;
    private boolean comparisonChartEnabled = true;
    private boolean capitalUsageChartEnabled = true;
    private boolean tradePLChartEnabled = true;

    // Chart wrappers and zoom buttons
    private JPanel[] chartWrappers;
    private JButton[] zoomButtons;
    private JButton[] fullScreenButtons;
    private JButton[] closeButtons; // Close button for exiting full-screen mode

    // Scrollbar for fixed-width mode
    private JScrollBar timeScrollBar;

    // Reference to indicator manager for updates
    private IndicatorChartsManager indicatorManager;

    // Callback for layout updates
    private Runnable onLayoutChange;

    public ChartZoomManager() {
        chartWrappers = new JPanel[6];
        zoomButtons = new JButton[6];
        fullScreenButtons = new JButton[6];
        closeButtons = new JButton[6];
    }

    public void setIndicatorManager(IndicatorChartsManager manager) {
        this.indicatorManager = manager;
    }

    public void setOnLayoutChange(Runnable callback) {
        this.onLayoutChange = callback;
    }

    // ===== Core Chart Toggles =====

    public void setVolumeChartEnabled(boolean enabled) {
        this.volumeChartEnabled = enabled;
        if (onLayoutChange != null) onLayoutChange.run();
    }

    public boolean isVolumeChartEnabled() {
        return volumeChartEnabled;
    }

    public void setEquityChartEnabled(boolean enabled) {
        this.equityChartEnabled = enabled;
        if (onLayoutChange != null) onLayoutChange.run();
    }

    public boolean isEquityChartEnabled() {
        return equityChartEnabled;
    }

    public void setComparisonChartEnabled(boolean enabled) {
        this.comparisonChartEnabled = enabled;
        if (onLayoutChange != null) onLayoutChange.run();
    }

    public boolean isComparisonChartEnabled() {
        return comparisonChartEnabled;
    }

    public void setCapitalUsageChartEnabled(boolean enabled) {
        this.capitalUsageChartEnabled = enabled;
        if (onLayoutChange != null) onLayoutChange.run();
    }

    public boolean isCapitalUsageChartEnabled() {
        return capitalUsageChartEnabled;
    }

    public void setTradePLChartEnabled(boolean enabled) {
        this.tradePLChartEnabled = enabled;
        if (onLayoutChange != null) onLayoutChange.run();
    }

    public boolean isTradePLChartEnabled() {
        return tradePLChartEnabled;
    }

    /**
     * Create wrapper panels for core charts with zoom buttons.
     */
    public void createWrappers(org.jfree.chart.ChartPanel[] chartPanels) {
        for (int i = 0; i < chartPanels.length && i < 6; i++) {
            chartWrappers[i] = createChartWrapper(chartPanels[i], i);
        }
    }

    private JPanel createChartWrapper(org.jfree.chart.ChartPanel chartPanel, int chartIndex) {
        JButton zoomBtn = new JButton("\u2922"); // ⤢
        zoomBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomBtn.setMargin(new Insets(2, 4, 1, 4));
        zoomBtn.setFocusPainted(false);
        zoomBtn.setToolTipText("Zoom chart");
        zoomBtn.addActionListener(e -> toggleZoom(chartIndex));
        zoomBtn.setVisible(false); // Hidden by default
        zoomButtons[chartIndex] = zoomBtn;

        JButton fsBtn = new JButton("\u25a1"); // □ (empty square)
        fsBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        fsBtn.setMargin(new Insets(2, 4, 1, 4));
        fsBtn.setFocusPainted(false);
        fsBtn.setToolTipText("Full screen (hide other charts)");
        fsBtn.addActionListener(e -> toggleFullScreen(chartIndex));
        fsBtn.setVisible(false); // Hidden by default
        fullScreenButtons[chartIndex] = fsBtn;

        // Close button for exiting full-screen mode (top-left)
        JButton closeBtn = new JButton("\u2715"); // ✕
        closeBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        closeBtn.setMargin(new Insets(2, 4, 1, 4));
        closeBtn.setFocusPainted(false);
        closeBtn.setToolTipText("Show all charts");
        closeBtn.addActionListener(e -> exitFullScreen());
        closeBtn.setVisible(false); // Only visible in full-screen mode
        closeButtons[chartIndex] = closeBtn;

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension zoomBtnSize = zoomBtn.getPreferredSize();
        Dimension fsBtnSize = fsBtn.getPreferredSize();
        Dimension closeBtnSize = closeBtn.getPreferredSize();
        zoomBtn.setBounds(0, 5, zoomBtnSize.width, zoomBtnSize.height);
        fsBtn.setBounds(0, 5, fsBtnSize.width, fsBtnSize.height);
        closeBtn.setBounds(8, 8, closeBtnSize.width, closeBtnSize.height);
        layeredPane.add(zoomBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(fsBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(closeBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension zbs = zoomBtn.getPreferredSize();
                Dimension fsbs = fsBtn.getPreferredSize();
                Dimension cbs = closeBtn.getPreferredSize();
                // Position zoom button at right edge
                zoomBtn.setBounds(w - zbs.width - 12, 8, zbs.width, zbs.height);
                // Position full screen button to the left of zoom button
                fsBtn.setBounds(w - zbs.width - 12 - fsbs.width - 4, 8, fsbs.width, fsbs.height);
                // Position close button at top-left
                closeBtn.setBounds(8, 8, cbs.width, cbs.height);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBorder(null);
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
        wrapper.setPreferredSize(new Dimension(100, MIN_CHART_HEIGHT));
        return wrapper;
    }

    /**
     * Create scrollbar for fixed-width mode.
     */
    public JScrollBar createTimeScrollBar() {
        timeScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        timeScrollBar.setVisible(false);
        return timeScrollBar;
    }

    /**
     * Toggle zoom state for a core chart.
     */
    public void toggleZoom(int chartIndex) {
        if (zoomedChartIndex == chartIndex) {
            zoomedChartIndex = -1;
        } else {
            zoomedChartIndex = chartIndex;
            indicatorZoomedIndex = -1;
        }
        updateZoomButtonStates();
        if (indicatorManager != null) {
            indicatorManager.updateZoomButtonStates(indicatorZoomedIndex);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    /**
     * Toggle zoom state for an indicator chart.
     */
    public void toggleIndicatorZoom(int index) {
        if (indicatorZoomedIndex == index) {
            indicatorZoomedIndex = -1;
        } else {
            indicatorZoomedIndex = index;
            zoomedChartIndex = -1;
        }
        updateZoomButtonStates();
        if (indicatorManager != null) {
            indicatorManager.updateZoomButtonStates(indicatorZoomedIndex);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    /**
     * Toggle full screen state for a core chart.
     */
    public void toggleFullScreen(int chartIndex) {
        if (fullScreenChartIndex == chartIndex) {
            fullScreenChartIndex = -1;
        } else {
            fullScreenChartIndex = chartIndex;
            fullScreenIndicatorIndex = -1;
            // Reset zoom states when entering full screen
            zoomedChartIndex = -1;
            indicatorZoomedIndex = -1;
        }
        updateFullScreenButtonStates();
        updateCloseButtonVisibility();
        updateZoomButtonStates();
        if (indicatorManager != null) {
            indicatorManager.updateFullScreenButtonStates(fullScreenIndicatorIndex);
            indicatorManager.updateZoomButtonStates(indicatorZoomedIndex);
            indicatorManager.updateCloseButtonVisibility(fullScreenIndicatorIndex);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    /**
     * Exit full screen mode for any chart.
     */
    public void exitFullScreen() {
        fullScreenChartIndex = -1;
        fullScreenIndicatorIndex = -1;
        updateFullScreenButtonStates();
        updateCloseButtonVisibility();
        updateZoomButtonStates();
        if (indicatorManager != null) {
            indicatorManager.updateFullScreenButtonStates(-1);
            indicatorManager.updateZoomButtonStates(-1);
            indicatorManager.updateCloseButtonVisibility(-1);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    /**
     * Toggle full screen state for an indicator chart.
     */
    public void toggleIndicatorFullScreen(int index) {
        if (fullScreenIndicatorIndex == index) {
            fullScreenIndicatorIndex = -1;
        } else {
            fullScreenIndicatorIndex = index;
            fullScreenChartIndex = -1;
            // Reset zoom states when entering full screen
            zoomedChartIndex = -1;
            indicatorZoomedIndex = -1;
        }
        updateFullScreenButtonStates();
        updateCloseButtonVisibility();
        updateZoomButtonStates();
        if (indicatorManager != null) {
            indicatorManager.updateFullScreenButtonStates(fullScreenIndicatorIndex);
            indicatorManager.updateZoomButtonStates(indicatorZoomedIndex);
            indicatorManager.updateCloseButtonVisibility(fullScreenIndicatorIndex);
        }
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    private void updateZoomButtonStates() {
        for (int i = 0; i < zoomButtons.length; i++) {
            if (zoomButtons[i] != null) {
                if (zoomedChartIndex == i) {
                    zoomButtons[i].setText("\u2921"); // ⤡
                    zoomButtons[i].setToolTipText("Restore chart size");
                } else {
                    zoomButtons[i].setText("\u2922"); // ⤢
                    zoomButtons[i].setToolTipText("Zoom chart");
                }
            }
        }
    }

    private void updateFullScreenButtonStates() {
        for (int i = 0; i < fullScreenButtons.length; i++) {
            if (fullScreenButtons[i] != null) {
                if (fullScreenChartIndex == i) {
                    fullScreenButtons[i].setText("\u25a0"); // ■ (filled square)
                    fullScreenButtons[i].setToolTipText("Exit full screen");
                } else {
                    fullScreenButtons[i].setText("\u25a1"); // □ (empty square)
                    fullScreenButtons[i].setToolTipText("Full screen (hide other charts)");
                }
            }
        }
    }

    private void updateCloseButtonVisibility() {
        boolean inFullScreen = fullScreenChartIndex >= 0 || fullScreenIndicatorIndex >= 0;
        for (int i = 0; i < closeButtons.length; i++) {
            if (closeButtons[i] != null) {
                // Show close button only on the full-screen chart
                closeButtons[i].setVisible(fullScreenChartIndex == i);
            }
        }
    }

    /**
     * Update chart container layout based on zoom state.
     */
    public void updateChartLayout(JPanel chartsContainer, JFreeChart[] allCharts, JPanel[] allWrappers) {
        chartsContainer.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Map indicator wrappers for full screen / zoom detection
        JPanel[] indicatorWrappers = indicatorManager != null ?
            new JPanel[]{indicatorManager.getRsiChartWrapper(),
                         indicatorManager.getMacdChartWrapper(),
                         indicatorManager.getAtrChartWrapper(),
                         indicatorManager.getDeltaChartWrapper(),
                         indicatorManager.getCvdChartWrapper(),
                         indicatorManager.getVolumeRatioChartWrapper(),
                         indicatorManager.getWhaleChartWrapper(),
                         indicatorManager.getRetailChartWrapper(),
                         indicatorManager.getFundingChartWrapper(),
                         indicatorManager.getOiChartWrapper(),
                         indicatorManager.getStochasticChartWrapper(),
                         indicatorManager.getRangePositionChartWrapper(),
                         indicatorManager.getAdxChartWrapper(),
                         indicatorManager.getTradeCountChartWrapper(),
                         indicatorManager.getPremiumChartWrapper()} :
            new JPanel[0];

        // If in full screen mode, only show the full screen chart
        if (fullScreenChartIndex >= 0 && fullScreenChartIndex < 6 && chartWrappers[fullScreenChartIndex] != null) {
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            chartsContainer.add(chartWrappers[fullScreenChartIndex], gbc);
            chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR);

            // Show time labels on the full screen chart
            for (int i = 0; i < allCharts.length; i++) {
                if (allCharts[i] != null && allWrappers[i] != null) {
                    XYPlot plot = allCharts[i].getXYPlot();
                    if (plot.getDomainAxis() instanceof DateAxis axis) {
                        boolean showLabels = (allWrappers[i] == chartWrappers[fullScreenChartIndex]);
                        axis.setTickLabelsVisible(showLabels);
                        axis.setTickMarksVisible(showLabels);
                        // Price chart (index 0) has time labels at top
                        plot.setDomainAxisLocation(fullScreenChartIndex == 0
                            ? org.jfree.chart.axis.AxisLocation.TOP_OR_RIGHT
                            : org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
                    }
                }
            }

            chartsContainer.revalidate();
            chartsContainer.repaint();
            return;
        }

        // If in full screen mode for indicator chart
        if (fullScreenIndicatorIndex >= 0 && indicatorWrappers.length > fullScreenIndicatorIndex && indicatorWrappers[fullScreenIndicatorIndex] != null) {
            gbc.gridy = 0;
            gbc.weighty = 1.0;
            chartsContainer.add(indicatorWrappers[fullScreenIndicatorIndex], gbc);
            chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR);

            // Show time labels on the full screen chart (indicators stay at bottom)
            for (int i = 0; i < allCharts.length; i++) {
                if (allCharts[i] != null && allWrappers[i] != null) {
                    XYPlot plot = allCharts[i].getXYPlot();
                    if (plot.getDomainAxis() instanceof DateAxis axis) {
                        boolean showLabels = (allWrappers[i] == indicatorWrappers[fullScreenIndicatorIndex]);
                        axis.setTickLabelsVisible(showLabels);
                        axis.setTickMarksVisible(showLabels);
                        plot.setDomainAxisLocation(org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
                    }
                }
            }

            chartsContainer.revalidate();
            chartsContainer.repaint();
            return;
        }

        // Build list of visible charts in order:
        // Price (always shown), [Volume], [RSI], [MACD], [ATR], [Delta], [CVD], [VolumeRatio], [Funding], [Equity], [Comparison], [CapitalUsage], [TradeP&L]
        java.util.List<JPanel> visibleCharts = new java.util.ArrayList<>();
        visibleCharts.add(chartWrappers[0]); // Price - always shown

        if (volumeChartEnabled) {
            visibleCharts.add(chartWrappers[1]); // Volume
        }

        if (indicatorManager != null) {
            if (indicatorManager.isRsiChartEnabled()) {
                visibleCharts.add(indicatorManager.getRsiChartWrapper());
            }
            if (indicatorManager.isMacdChartEnabled()) {
                visibleCharts.add(indicatorManager.getMacdChartWrapper());
            }
            if (indicatorManager.isAtrChartEnabled()) {
                visibleCharts.add(indicatorManager.getAtrChartWrapper());
            }
            // Orderflow charts - each has its own panel
            if (indicatorManager.isDeltaChartEnabled()) {
                visibleCharts.add(indicatorManager.getDeltaChartWrapper());
            }
            if (indicatorManager.isCvdChartEnabled()) {
                visibleCharts.add(indicatorManager.getCvdChartWrapper());
            }
            if (indicatorManager.isVolumeRatioChartEnabled()) {
                visibleCharts.add(indicatorManager.getVolumeRatioChartWrapper());
            }
            if (indicatorManager.isWhaleChartEnabled()) {
                visibleCharts.add(indicatorManager.getWhaleChartWrapper());
            }
            if (indicatorManager.isRetailChartEnabled()) {
                visibleCharts.add(indicatorManager.getRetailChartWrapper());
            }
            if (indicatorManager.isFundingChartEnabled()) {
                visibleCharts.add(indicatorManager.getFundingChartWrapper());
            }
            if (indicatorManager.isOiChartEnabled()) {
                visibleCharts.add(indicatorManager.getOiChartWrapper());
            }
            if (indicatorManager.isStochasticChartEnabled()) {
                visibleCharts.add(indicatorManager.getStochasticChartWrapper());
            }
            if (indicatorManager.isRangePositionChartEnabled()) {
                visibleCharts.add(indicatorManager.getRangePositionChartWrapper());
            }
            if (indicatorManager.isAdxChartEnabled()) {
                visibleCharts.add(indicatorManager.getAdxChartWrapper());
            }
            if (indicatorManager.isTradeCountChartEnabled()) {
                visibleCharts.add(indicatorManager.getTradeCountChartWrapper());
            }
            if (indicatorManager.isPremiumChartEnabled()) {
                visibleCharts.add(indicatorManager.getPremiumChartWrapper());
            }
        }

        if (equityChartEnabled) {
            visibleCharts.add(chartWrappers[2]); // Equity
        }
        if (comparisonChartEnabled) {
            visibleCharts.add(chartWrappers[3]); // Comparison
        }
        if (capitalUsageChartEnabled) {
            visibleCharts.add(chartWrappers[4]); // Capital Usage
        }
        if (tradePLChartEnabled) {
            visibleCharts.add(chartWrappers[5]); // Trade P&L
        }

        int totalCharts = visibleCharts.size();
        for (int i = 0; i < totalCharts; i++) {
            gbc.gridy = i;
            JPanel panel = visibleCharts.get(i);

            boolean isZoomed = false;

            // Check core chart zoom
            if (zoomedChartIndex >= 0 && zoomedChartIndex < 6) {
                for (int j = 0; j < 6; j++) {
                    if (chartWrappers[j] == panel && j == zoomedChartIndex) {
                        isZoomed = true;
                        break;
                    }
                }
            }

            // Check indicator chart zoom
            if (indicatorZoomedIndex >= 0 && indicatorWrappers.length > 0) {
                for (int j = 0; j < indicatorWrappers.length; j++) {
                    if (indicatorWrappers[j] == panel && j == indicatorZoomedIndex) {
                        isZoomed = true;
                        break;
                    }
                }
            }

            // Set weight based on zoom state
            // Price chart (first) gets extra weight to compensate for time axis labels
            boolean isPriceChart = (panel == chartWrappers[0]);
            double priceChartBonus = 0.35; // Extra weight for price chart

            if (isZoomed) {
                gbc.weighty = 0.65;
            } else if (zoomedChartIndex >= 0 || indicatorZoomedIndex >= 0) {
                gbc.weighty = 0.35 / (totalCharts - 1);
            } else {
                double baseWeight = 1.0 / (totalCharts + priceChartBonus);
                gbc.weighty = isPriceChart ? baseWeight * (1 + priceChartBonus) : baseWeight;
            }
            chartsContainer.add(panel, gbc);
        }
        chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR);

        // Show time labels only on first and last chart
        for (int i = 0; i < allCharts.length; i++) {
            if (allCharts[i] != null && allWrappers[i] != null) {
                XYPlot plot = allCharts[i].getXYPlot();
                if (plot.getDomainAxis() instanceof DateAxis axis) {
                    boolean isFirst = (allWrappers[i] == visibleCharts.get(0));
                    boolean isLast = (allWrappers[i] == visibleCharts.get(visibleCharts.size() - 1));
                    boolean showLabels = isFirst || isLast;
                    axis.setTickLabelsVisible(showLabels);
                    axis.setTickMarksVisible(showLabels);
                    // Price chart (first) has time labels at top, others at bottom
                    plot.setDomainAxisLocation(isFirst
                        ? org.jfree.chart.axis.AxisLocation.TOP_OR_RIGHT
                        : org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
                }
            }
        }

        chartsContainer.revalidate();
        chartsContainer.repaint();
    }

    // ===== Fixed Width Mode =====

    public void setFixedWidthMode(boolean enabled) {
        this.fixedWidthMode = enabled;
    }

    public boolean isFixedWidthMode() {
        return fixedWidthMode;
    }

    public void setFitYAxisToVisible(boolean enabled) {
        this.fitYAxisToVisible = enabled;
    }

    public boolean isFitYAxisToVisible() {
        return fitYAxisToVisible;
    }

    /**
     * Update fixed-width mode scrollbar configuration.
     */
    public void updateFixedWidthMode(int containerWidth, int candleCount, Runnable updateVisibleRange) {
        if (candleCount == 0) {
            timeScrollBar.setVisible(false);
            return;
        }

        if (fixedWidthMode) {
            int viewportWidth = containerWidth > 0 ? containerWidth : 800;
            int visibleCandles = Math.max(1, viewportWidth / PIXELS_PER_CANDLE);

            if (visibleCandles < candleCount) {
                boolean wasVisible = timeScrollBar.isVisible();
                int oldValue = timeScrollBar.getValue();
                int oldMax = timeScrollBar.getMaximum();

                timeScrollBar.setMinimum(0);
                timeScrollBar.setMaximum(candleCount);
                timeScrollBar.setVisibleAmount(visibleCandles);
                timeScrollBar.setBlockIncrement(visibleCandles / 2);
                timeScrollBar.setUnitIncrement(10);

                if (!wasVisible || oldMax != candleCount) {
                    timeScrollBar.setValue(candleCount - visibleCandles);
                } else {
                    timeScrollBar.setValue(Math.min(oldValue, candleCount - visibleCandles));
                }

                timeScrollBar.setVisible(true);
                updateVisibleRange.run();
            } else {
                timeScrollBar.setVisible(false);
            }
        } else {
            timeScrollBar.setVisible(false);
        }
    }

    public JScrollBar getTimeScrollBar() {
        return timeScrollBar;
    }

    // ===== Accessors =====

    public JPanel[] getChartWrappers() {
        return chartWrappers;
    }

    public int getZoomedChartIndex() {
        return zoomedChartIndex;
    }

    public int getIndicatorZoomedIndex() {
        return indicatorZoomedIndex;
    }

    public int getFullScreenChartIndex() {
        return fullScreenChartIndex;
    }

    public int getFullScreenIndicatorIndex() {
        return fullScreenIndicatorIndex;
    }
}
