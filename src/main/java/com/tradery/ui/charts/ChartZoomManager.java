package com.tradery.ui.charts;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.List;

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

    // Scrollbar for fixed-width mode
    private JScrollBar timeScrollBar;

    // Reference to indicator manager for updates
    private IndicatorChartsManager indicatorManager;

    // Callback for layout updates
    private Runnable onLayoutChange;

    public ChartZoomManager() {
        chartWrappers = new JPanel[6];
        zoomButtons = new JButton[6];
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
        zoomButtons[chartIndex] = zoomBtn;

        JLayeredPane layeredPane = new JLayeredPane();
        chartPanel.setBounds(0, 0, 100, 100);
        layeredPane.add(chartPanel, JLayeredPane.DEFAULT_LAYER);

        Dimension btnSize = zoomBtn.getPreferredSize();
        zoomBtn.setBounds(0, 5, btnSize.width, btnSize.height);
        layeredPane.add(zoomBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                chartPanel.setBounds(0, 0, w, h);
                Dimension btnSize = zoomBtn.getPreferredSize();
                zoomBtn.setBounds(w - btnSize.width - 12, 8, btnSize.width, btnSize.height);
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

    /**
     * Update chart container layout based on zoom state.
     */
    public void updateChartLayout(JPanel chartsContainer, JFreeChart[] allCharts, JPanel[] allWrappers) {
        chartsContainer.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

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

        // Map indicator wrappers for zoom detection
        JPanel[] indicatorWrappers = indicatorManager != null ?
            new JPanel[]{indicatorManager.getRsiChartWrapper(),
                         indicatorManager.getMacdChartWrapper(),
                         indicatorManager.getAtrChartWrapper(),
                         indicatorManager.getDeltaChartWrapper(),
                         indicatorManager.getCvdChartWrapper(),
                         indicatorManager.getVolumeRatioChartWrapper(),
                         indicatorManager.getWhaleChartWrapper(),
                         indicatorManager.getRetailChartWrapper(),
                         indicatorManager.getFundingChartWrapper()} :
            new JPanel[0];

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

            // Set weight based on zoom state - equal height for all charts
            if (isZoomed) {
                gbc.weighty = 0.65;
            } else if (zoomedChartIndex >= 0 || indicatorZoomedIndex >= 0) {
                gbc.weighty = 0.35 / (totalCharts - 1);
            } else {
                gbc.weighty = 1.0 / totalCharts;
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
                    plot.setDomainAxisLocation(org.jfree.chart.axis.AxisLocation.BOTTOM_OR_LEFT);
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
}
