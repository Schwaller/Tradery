package com.tradery.ui.charts;

import com.tradery.indicators.IndicatorEngine;
import com.tradery.model.Candle;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.List;

/**
 * Manages optional indicator charts (RSI, MACD, ATR).
 */
public class IndicatorChartsManager {

    // Chart panels
    private org.jfree.chart.ChartPanel rsiChartPanel;
    private org.jfree.chart.ChartPanel macdChartPanel;
    private org.jfree.chart.ChartPanel atrChartPanel;
    private org.jfree.chart.ChartPanel deltaChartPanel;
    private org.jfree.chart.ChartPanel cvdChartPanel;
    private org.jfree.chart.ChartPanel volumeRatioChartPanel;
    private org.jfree.chart.ChartPanel fundingChartPanel;

    // Charts
    private JFreeChart rsiChart;
    private JFreeChart macdChart;
    private JFreeChart atrChart;
    private JFreeChart deltaChart;
    private JFreeChart cvdChart;
    private JFreeChart volumeRatioChart;
    private JFreeChart fundingChart;

    // Wrapper panels with zoom buttons
    private JPanel rsiChartWrapper;
    private JPanel macdChartWrapper;
    private JPanel atrChartWrapper;
    private JPanel deltaChartWrapper;
    private JPanel cvdChartWrapper;
    private JPanel volumeRatioChartWrapper;
    private JPanel fundingChartWrapper;

    // Zoom buttons
    private JButton rsiZoomBtn;
    private JButton macdZoomBtn;
    private JButton atrZoomBtn;
    private JButton deltaZoomBtn;
    private JButton cvdZoomBtn;
    private JButton volumeRatioZoomBtn;
    private JButton fundingZoomBtn;

    // Enable state
    private boolean rsiChartEnabled = false;
    private boolean macdChartEnabled = false;
    private boolean atrChartEnabled = false;
    private boolean deltaChartEnabled = false;
    private boolean cvdChartEnabled = false;
    private boolean volumeRatioChartEnabled = false;
    private boolean fundingChartEnabled = false;

    // Indicator parameters
    private int rsiPeriod = 14;
    private int macdFast = 12;
    private int macdSlow = 26;
    private int macdSignal = 9;
    private int atrPeriod = 14;
    private double whaleThreshold = 50000;

    // IndicatorEngine for orderflow/funding data
    private IndicatorEngine indicatorEngine;

    // Callback for layout updates
    private Runnable onLayoutChange;

    // Min chart height
    private static final int MIN_CHART_HEIGHT = 60;

    public IndicatorChartsManager() {
        initializeCharts();
    }

    public void setOnLayoutChange(Runnable callback) {
        this.onLayoutChange = callback;
    }

    private void initializeCharts() {
        // RSI chart
        rsiChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(rsiChart, "RSI");
        rsiChart.getXYPlot().getRangeAxis().setRange(0, 100);

        rsiChartPanel = new org.jfree.chart.ChartPanel(rsiChart);
        configureChartPanel(rsiChartPanel);

        // MACD chart
        macdChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(macdChart, "MACD");

        macdChartPanel = new org.jfree.chart.ChartPanel(macdChart);
        configureChartPanel(macdChartPanel);

        // ATR chart
        atrChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(atrChart, "ATR");

        atrChartPanel = new org.jfree.chart.ChartPanel(atrChart);
        configureChartPanel(atrChartPanel);

        // Delta chart (orderflow - per bar)
        deltaChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(deltaChart, "Delta");
        deltaChartPanel = new org.jfree.chart.ChartPanel(deltaChart);
        configureChartPanel(deltaChartPanel);

        // CVD chart (cumulative volume delta)
        cvdChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(cvdChart, "CVD");
        cvdChartPanel = new org.jfree.chart.ChartPanel(cvdChart);
        configureChartPanel(cvdChartPanel);

        // Volume Ratio chart (buy volume %)
        volumeRatioChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(volumeRatioChart, "Buy/Sell Ratio");
        volumeRatioChartPanel = new org.jfree.chart.ChartPanel(volumeRatioChart);
        configureChartPanel(volumeRatioChartPanel);

        // Funding chart
        fundingChart = ChartFactory.createTimeSeriesChart(
            null, null, null,
            new TimeSeriesCollection(),
            false, true, false
        );
        ChartStyles.stylizeChart(fundingChart, "Funding");
        fundingChartPanel = new org.jfree.chart.ChartPanel(fundingChart);
        configureChartPanel(fundingChartPanel);
    }

    private void configureChartPanel(org.jfree.chart.ChartPanel panel) {
        panel.setMouseWheelEnabled(false);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);
        panel.setMinimumDrawWidth(0);
        panel.setMinimumDrawHeight(0);
        panel.setMaximumDrawWidth(Integer.MAX_VALUE);
        panel.setMaximumDrawHeight(Integer.MAX_VALUE);
    }

    /**
     * Create wrapper panels with zoom buttons.
     * @param zoomCallback Callback to handle zoom toggle (index: 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=CVD, 5=VolumeRatio, 6=Funding)
     */
    public void createWrappers(java.util.function.IntConsumer zoomCallback) {
        rsiZoomBtn = new JButton("\u2922"); // ⤢
        macdZoomBtn = new JButton("\u2922");
        atrZoomBtn = new JButton("\u2922");
        deltaZoomBtn = new JButton("\u2922");
        cvdZoomBtn = new JButton("\u2922");
        volumeRatioZoomBtn = new JButton("\u2922");
        fundingZoomBtn = new JButton("\u2922");

        rsiChartWrapper = createChartWrapper(rsiChartPanel, rsiZoomBtn, () -> zoomCallback.accept(0));
        macdChartWrapper = createChartWrapper(macdChartPanel, macdZoomBtn, () -> zoomCallback.accept(1));
        atrChartWrapper = createChartWrapper(atrChartPanel, atrZoomBtn, () -> zoomCallback.accept(2));
        deltaChartWrapper = createChartWrapper(deltaChartPanel, deltaZoomBtn, () -> zoomCallback.accept(3));
        cvdChartWrapper = createChartWrapper(cvdChartPanel, cvdZoomBtn, () -> zoomCallback.accept(4));
        volumeRatioChartWrapper = createChartWrapper(volumeRatioChartPanel, volumeRatioZoomBtn, () -> zoomCallback.accept(5));
        fundingChartWrapper = createChartWrapper(fundingChartPanel, fundingZoomBtn, () -> zoomCallback.accept(6));
    }

    private JPanel createChartWrapper(org.jfree.chart.ChartPanel chartPanel, JButton zoomBtn, Runnable onZoom) {
        zoomBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zoomBtn.setMargin(new Insets(2, 4, 1, 4));
        zoomBtn.setFocusPainted(false);
        zoomBtn.setToolTipText("Zoom chart");
        zoomBtn.addActionListener(e -> onZoom.run());

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
                Dimension bs = zoomBtn.getPreferredSize();
                zoomBtn.setBounds(w - bs.width - 12, 8, bs.width, bs.height);
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(layeredPane, BorderLayout.CENTER);
        wrapper.setMinimumSize(new Dimension(100, MIN_CHART_HEIGHT));
        return wrapper;
    }

    // ===== RSI Methods =====

    public void setRsiChartEnabled(boolean enabled, int period) {
        this.rsiChartEnabled = enabled;
        this.rsiPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isRsiChartEnabled() {
        return rsiChartEnabled;
    }

    public void updateRsiChart(List<Candle> candles) {
        if (!rsiChartEnabled || candles == null || candles.size() < rsiPeriod + 1) {
            return;
        }

        XYPlot plot = rsiChart.getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries rsiSeries = new TimeSeries("RSI(" + rsiPeriod + ")");

        // Calculate RSI
        double[] gains = new double[candles.size()];
        double[] losses = new double[candles.size()];

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }

        // Calculate initial averages
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= rsiPeriod; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= rsiPeriod;
        avgLoss /= rsiPeriod;

        // Calculate RSI values
        for (int i = rsiPeriod; i < candles.size(); i++) {
            if (i > rsiPeriod) {
                avgGain = (avgGain * (rsiPeriod - 1) + gains[i]) / rsiPeriod;
                avgLoss = (avgLoss * (rsiPeriod - 1) + losses[i]) / rsiPeriod;
            }

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            Candle c = candles.get(i);
            rsiSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), rsi);
        }

        dataset.addSeries(rsiSeries);
        plot.setDataset(dataset);

        // Style the RSI line
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.RSI_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Add reference lines at 30, 50, 70
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "RSI");

        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();

            plot.addAnnotation(new XYLineAnnotation(startTime, 30, endTime, 30,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERSOLD));
            plot.addAnnotation(new XYLineAnnotation(startTime, 50, endTime, 50,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            plot.addAnnotation(new XYLineAnnotation(startTime, 70, endTime, 70,
                ChartStyles.DASHED_STROKE, ChartStyles.RSI_OVERBOUGHT));
        }
    }

    // ===== MACD Methods =====

    public void setMacdChartEnabled(boolean enabled, int fast, int slow, int signal) {
        this.macdChartEnabled = enabled;
        this.macdFast = fast;
        this.macdSlow = slow;
        this.macdSignal = signal;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isMacdChartEnabled() {
        return macdChartEnabled;
    }

    public void updateMacdChart(List<Candle> candles) {
        if (!macdChartEnabled || candles == null || candles.size() < macdSlow + macdSignal) {
            return;
        }

        XYPlot plot = macdChart.getXYPlot();
        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        TimeSeries macdLine = new TimeSeries("MACD");
        TimeSeries signalLine = new TimeSeries("Signal");

        // Calculate EMAs
        double[] fastEma = calculateEMA(candles, macdFast);
        double[] slowEma = calculateEMA(candles, macdSlow);
        double[] macdValues = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            macdValues[i] = fastEma[i] - slowEma[i];
        }

        // Calculate signal line (EMA of MACD)
        double[] signalValues = new double[candles.size()];
        double multiplier = 2.0 / (macdSignal + 1);
        signalValues[macdSlow - 1] = macdValues[macdSlow - 1];
        for (int i = macdSlow; i < candles.size(); i++) {
            signalValues[i] = (macdValues[i] - signalValues[i - 1]) * multiplier + signalValues[i - 1];
        }

        // Build series and histogram data
        XYSeriesCollection histogramDataset = new XYSeriesCollection();
        XYSeries histogramSeries = new XYSeries("Histogram");

        for (int i = macdSlow + macdSignal - 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            Millisecond ms = new Millisecond(new Date(c.timestamp()));
            macdLine.addOrUpdate(ms, macdValues[i]);
            signalLine.addOrUpdate(ms, signalValues[i]);
            histogramSeries.add(c.timestamp(), macdValues[i] - signalValues[i]);
        }

        lineDataset.addSeries(macdLine);
        lineDataset.addSeries(signalLine);
        histogramDataset.addSeries(histogramSeries);

        // Set up renderers
        plot.setDataset(0, histogramDataset);
        plot.setDataset(1, lineDataset);

        // Histogram renderer with color based on value
        XYBarRenderer histRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = histogramDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.MACD_HIST_POS : ChartStyles.MACD_HIST_NEG;
            }
        };
        histRenderer.setShadowVisible(false);
        histRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, histRenderer);

        // Line renderer for MACD and signal
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, ChartStyles.MACD_LINE_COLOR);
        lineRenderer.setSeriesPaint(1, ChartStyles.MACD_SIGNAL_COLOR);
        lineRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        lineRenderer.setSeriesStroke(1, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, lineRenderer);

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "MACD");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    private double[] calculateEMA(List<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        double multiplier = 2.0 / (period + 1);

        // Start with SMA for first value
        double sum = 0;
        for (int i = 0; i < period && i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        ema[period - 1] = sum / period;

        // Calculate EMA
        for (int i = period; i < candles.size(); i++) {
            ema[i] = (candles.get(i).close() - ema[i - 1]) * multiplier + ema[i - 1];
        }

        return ema;
    }

    // ===== ATR Methods =====

    public void setAtrChartEnabled(boolean enabled, int period) {
        this.atrChartEnabled = enabled;
        this.atrPeriod = period;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isAtrChartEnabled() {
        return atrChartEnabled;
    }

    public void updateAtrChart(List<Candle> candles) {
        if (!atrChartEnabled || candles == null || candles.size() < atrPeriod + 1) {
            return;
        }

        XYPlot plot = atrChart.getXYPlot();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries atrSeries = new TimeSeries("ATR(" + atrPeriod + ")");

        // Calculate True Range and ATR
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high() - candles.get(0).low();

        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double highLow = curr.high() - curr.low();
            double highClose = Math.abs(curr.high() - prev.close());
            double lowClose = Math.abs(curr.low() - prev.close());
            tr[i] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate ATR using Wilder's smoothing
        double atr = 0;
        for (int i = 0; i < atrPeriod; i++) {
            atr += tr[i];
        }
        atr /= atrPeriod;

        for (int i = atrPeriod; i < candles.size(); i++) {
            if (i > atrPeriod) {
                atr = (atr * (atrPeriod - 1) + tr[i]) / atrPeriod;
            }
            Candle c = candles.get(i);
            atrSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), atr);
        }

        dataset.addSeries(atrSeries);
        plot.setDataset(dataset);

        // Style the ATR line
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.ATR_COLOR);
        renderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(renderer);

        // Clear annotations and re-add title
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "ATR");
    }

    // ===== Delta Methods =====

    public void setIndicatorEngine(IndicatorEngine engine) {
        this.indicatorEngine = engine;
    }

    public void setDeltaChartEnabled(boolean enabled, double threshold) {
        this.deltaChartEnabled = enabled;
        this.whaleThreshold = threshold;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isDeltaChartEnabled() {
        return deltaChartEnabled;
    }

    public void setCvdChartEnabled(boolean enabled) {
        this.cvdChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isCvdChartEnabled() {
        return cvdChartEnabled;
    }

    public void setVolumeRatioChartEnabled(boolean enabled) {
        this.volumeRatioChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isVolumeRatioChartEnabled() {
        return volumeRatioChartEnabled;
    }

    public void setWhaleThreshold(double threshold) {
        this.whaleThreshold = threshold;
    }

    /**
     * Check if any orderflow chart is enabled.
     */
    public boolean isAnyOrderflowEnabled() {
        return deltaChartEnabled || cvdChartEnabled || volumeRatioChartEnabled;
    }

    public void updateDeltaChart(List<Candle> candles) {
        if (!deltaChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = deltaChart.getXYPlot();
        double[] delta = indicatorEngine.getDelta();
        if (delta == null) return;

        XYSeriesCollection deltaDataset = new XYSeriesCollection();
        XYSeries deltaSeries = new XYSeries("Delta");

        for (int i = 0; i < candles.size() && i < delta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(delta[i])) {
                deltaSeries.add(c.timestamp(), delta[i]);
            }
        }
        deltaDataset.addSeries(deltaSeries);
        plot.setDataset(0, deltaDataset);

        // Color-coded bar renderer
        final XYSeriesCollection finalDeltaDataset = deltaDataset;
        XYBarRenderer deltaRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = finalDeltaDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.DELTA_POSITIVE : ChartStyles.DELTA_NEGATIVE;
            }
        };
        deltaRenderer.setShadowVisible(false);
        deltaRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, deltaRenderer);

        // Add zero line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Delta");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
        }
    }

    public void updateCvdChart(List<Candle> candles) {
        if (!cvdChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = cvdChart.getXYPlot();
        double[] cumDelta = indicatorEngine.getCumulativeDelta();
        if (cumDelta == null) return;

        TimeSeriesCollection cvdDataset = new TimeSeriesCollection();
        TimeSeries cvdSeries = new TimeSeries("CVD");

        for (int i = 0; i < candles.size() && i < cumDelta.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(cumDelta[i])) {
                cvdSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), cumDelta[i]);
            }
        }
        cvdDataset.addSeries(cvdSeries);
        plot.setDataset(0, cvdDataset);

        XYLineAndShapeRenderer cvdRenderer = new XYLineAndShapeRenderer(true, false);
        cvdRenderer.setSeriesPaint(0, ChartStyles.CVD_COLOR);
        cvdRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(0, cvdRenderer);

        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "CVD (Cumulative Delta)");
    }

    public void updateVolumeRatioChart(List<Candle> candles) {
        if (!volumeRatioChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = volumeRatioChart.getXYPlot();
        double[] buyVolume = indicatorEngine.getBuyVolume();
        double[] sellVolume = indicatorEngine.getSellVolume();
        if (buyVolume == null || sellVolume == null) return;

        TimeSeriesCollection ratioDataset = new TimeSeriesCollection();
        TimeSeries ratioSeries = new TimeSeries("Buy %");

        for (int i = 0; i < candles.size() && i < buyVolume.length && i < sellVolume.length; i++) {
            Candle c = candles.get(i);
            double total = buyVolume[i] + sellVolume[i];
            if (total > 0) {
                double buyPercent = (buyVolume[i] / total) * 100;
                ratioSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), buyPercent);
            }
        }
        ratioDataset.addSeries(ratioSeries);
        plot.setDataset(0, ratioDataset);

        XYLineAndShapeRenderer ratioRenderer = new XYLineAndShapeRenderer(true, false);
        ratioRenderer.setSeriesPaint(0, ChartStyles.BUY_VOLUME_COLOR);
        ratioRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(0, ratioRenderer);

        // Add 50% reference line
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Buy/Sell Ratio (%)");
        plot.getRangeAxis().setRange(0, 100);
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 50, endTime, 50,
                ChartStyles.DASHED_STROKE, new Color(149, 165, 166, 150)));
        }
    }

    // ===== Funding Methods =====

    public void setFundingChartEnabled(boolean enabled) {
        this.fundingChartEnabled = enabled;
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    public boolean isFundingChartEnabled() {
        return fundingChartEnabled;
    }

    public void updateFundingChart(List<Candle> candles) {
        if (!fundingChartEnabled || candles == null || candles.isEmpty() || indicatorEngine == null) {
            return;
        }

        XYPlot plot = fundingChart.getXYPlot();

        double[] funding = indicatorEngine.getFunding();
        double[] funding8H = indicatorEngine.getFunding8H();

        // Build datasets
        XYSeriesCollection fundingDataset = new XYSeriesCollection();
        XYSeries fundingSeries = new XYSeries("Funding");

        TimeSeriesCollection avgDataset = new TimeSeriesCollection();
        TimeSeries avgSeries = new TimeSeries("Funding 8H Avg");

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(funding[i])) {
                fundingSeries.add(c.timestamp(), funding[i]);
            }
            if (!Double.isNaN(funding8H[i])) {
                avgSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), funding8H[i]);
            }
        }

        fundingDataset.addSeries(fundingSeries);
        avgDataset.addSeries(avgSeries);

        plot.setDataset(0, fundingDataset);
        plot.setDataset(1, avgDataset);

        // Funding bar renderer (orange/blue based on sign)
        XYBarRenderer fundingRenderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                double value = fundingDataset.getYValue(series, item);
                return value >= 0 ? ChartStyles.FUNDING_POSITIVE : ChartStyles.FUNDING_NEGATIVE;
            }
        };
        fundingRenderer.setShadowVisible(false);
        fundingRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(0, fundingRenderer);

        // 8H average line renderer
        XYLineAndShapeRenderer avgRenderer = new XYLineAndShapeRenderer(true, false);
        avgRenderer.setSeriesPaint(0, ChartStyles.FUNDING_8H_COLOR);
        avgRenderer.setSeriesStroke(0, ChartStyles.MEDIUM_STROKE);
        plot.setRenderer(1, avgRenderer);

        // Add reference lines at 0, 0.05, -0.05
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, "Funding Rate (%)");
        if (!candles.isEmpty()) {
            long startTime = candles.get(0).timestamp();
            long endTime = candles.get(candles.size() - 1).timestamp();
            plot.addAnnotation(new XYLineAnnotation(startTime, 0, endTime, 0,
                ChartStyles.DASHED_STROKE, ChartStyles.TEXT_COLOR));
            plot.addAnnotation(new XYLineAnnotation(startTime, 0.05, endTime, 0.05,
                ChartStyles.DASHED_STROKE, new Color(230, 126, 34, 100)));
            plot.addAnnotation(new XYLineAnnotation(startTime, -0.05, endTime, -0.05,
                ChartStyles.DASHED_STROKE, new Color(52, 152, 219, 100)));
        }
    }

    // ===== Accessors =====

    public JFreeChart getRsiChart() { return rsiChart; }
    public JFreeChart getMacdChart() { return macdChart; }
    public JFreeChart getAtrChart() { return atrChart; }
    public JFreeChart getDeltaChart() { return deltaChart; }
    public JFreeChart getFundingChart() { return fundingChart; }

    public org.jfree.chart.ChartPanel getRsiChartPanel() { return rsiChartPanel; }
    public org.jfree.chart.ChartPanel getMacdChartPanel() { return macdChartPanel; }
    public org.jfree.chart.ChartPanel getAtrChartPanel() { return atrChartPanel; }
    public org.jfree.chart.ChartPanel getDeltaChartPanel() { return deltaChartPanel; }
    public org.jfree.chart.ChartPanel getFundingChartPanel() { return fundingChartPanel; }

    public JPanel getRsiChartWrapper() { return rsiChartWrapper; }
    public JPanel getMacdChartWrapper() { return macdChartWrapper; }
    public JPanel getAtrChartWrapper() { return atrChartWrapper; }
    public JPanel getDeltaChartWrapper() { return deltaChartWrapper; }
    public JPanel getFundingChartWrapper() { return fundingChartWrapper; }

    public JButton getRsiZoomBtn() { return rsiZoomBtn; }
    public JButton getMacdZoomBtn() { return macdZoomBtn; }
    public JButton getAtrZoomBtn() { return atrZoomBtn; }
    public JButton getDeltaZoomBtn() { return deltaZoomBtn; }
    public JButton getFundingZoomBtn() { return fundingZoomBtn; }

    /**
     * Update zoom button states.
     * @param zoomedIndex Index of zoomed indicator (-1 for none, 0=RSI, 1=MACD, 2=ATR, 3=Delta, 4=Funding)
     */
    public void updateZoomButtonStates(int zoomedIndex) {
        JButton[] btns = {rsiZoomBtn, macdZoomBtn, atrZoomBtn, deltaZoomBtn, fundingZoomBtn};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] != null) {
                if (zoomedIndex == i) {
                    btns[i].setText("\u2921"); // ⤡
                    btns[i].setToolTipText("Restore chart size");
                } else {
                    btns[i].setText("\u2922"); // ⤢
                    btns[i].setToolTipText("Zoom chart");
                }
            }
        }
    }

    /**
     * Add mouse wheel listener to all chart panels.
     */
    public void addMouseWheelListener(java.awt.event.MouseWheelListener listener) {
        rsiChartPanel.addMouseWheelListener(listener);
        macdChartPanel.addMouseWheelListener(listener);
        atrChartPanel.addMouseWheelListener(listener);
        deltaChartPanel.addMouseWheelListener(listener);
        fundingChartPanel.addMouseWheelListener(listener);
    }

    /**
     * Update all enabled indicator charts with new candle data.
     */
    public void updateCharts(List<Candle> candles) {
        updateRsiChart(candles);
        updateMacdChart(candles);
        updateAtrChart(candles);
        updateDeltaChart(candles);
        updateFundingChart(candles);
    }

    /**
     * Update Y-axis auto-range for indicator charts.
     */
    public void updateYAxisAutoRange(boolean fitYAxisToVisible) {
        // MACD, ATR, Delta, Funding follow standard auto-range
        JFreeChart[] charts = {macdChart, atrChart, deltaChart, fundingChart};
        for (JFreeChart chart : charts) {
            if (chart == null) continue;
            XYPlot plot = chart.getXYPlot();
            plot.getRangeAxis().setAutoRange(true);
            if (fitYAxisToVisible) {
                plot.configureRangeAxes();
            }
        }

        // RSI: fixed 0-100 range in Full Y mode, auto in Fit Y mode
        if (rsiChart != null) {
            if (fitYAxisToVisible) {
                rsiChart.getXYPlot().getRangeAxis().setAutoRange(true);
                rsiChart.getXYPlot().configureRangeAxes();
            } else {
                rsiChart.getXYPlot().getRangeAxis().setAutoRange(false);
                rsiChart.getXYPlot().getRangeAxis().setRange(0, 100);
            }
        }
    }
}
