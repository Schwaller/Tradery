package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.overlay.ChartOverlay;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultHighLowDataset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Candlestick/line chart for price data.
 * Supports overlays (SMA, EMA, Bollinger Bands, etc.).
 */
public class CandlestickChart extends SyncedChart {

    private boolean candlestickMode = false;
    private int priceOpacity = 255;
    private final List<ChartOverlay> overlays = new ArrayList<>();

    // Base dataset index for overlays (after price dataset)
    private static final int OVERLAY_START_INDEX = 1;

    public CandlestickChart(ChartCoordinator coordinator) {
        this(coordinator, "Price");
    }

    public CandlestickChart(ChartCoordinator coordinator, String title) {
        super(coordinator, title);
    }

    @Override
    protected JFreeChart createChart() {
        DateAxis domainAxis = new DateAxis("");
        NumberAxis rangeAxis = new NumberAxis("Price");
        rangeAxis.setAutoRangeIncludesZero(false);

        XYPlot plot = new XYPlot(null, domainAxis, rangeAxis, null);

        return new JFreeChart(null, null, plot, false);
    }

    @Override
    public void updateData(ChartDataProvider provider) {
        if (!provider.hasCandles()) return;

        XYPlot plot = getPlot();
        List<Candle> candles = provider.getCandles();

        // Clear old data
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            plot.setDataset(i, null);
        }
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, title);

        // Create price data
        if (candlestickMode) {
            createCandlestickData(plot, candles);
        } else {
            createLineData(plot, candles);
        }

        // Apply overlays
        int datasetIndex = OVERLAY_START_INDEX;
        for (ChartOverlay overlay : overlays) {
            overlay.apply(plot, provider, datasetIndex);
            datasetIndex += overlay.getDatasetCount();
        }
    }

    private void createLineData(XYPlot plot, List<Candle> candles) {
        TimeSeries series = new TimeSeries("Price");
        for (Candle c : candles) {
            series.addOrUpdate(new Millisecond(new Date(c.timestamp())), c.close());
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        plot.setDataset(0, dataset);

        Color lineColor = applyOpacity(ChartStyles.PRICE_LINE_COLOR);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, lineColor);
        renderer.setSeriesStroke(0, ChartStyles.LINE_STROKE);
        plot.setRenderer(0, renderer);
    }

    private void createCandlestickData(XYPlot plot, List<Candle> candles) {
        int size = candles.size();
        Date[] dates = new Date[size];
        double[] high = new double[size];
        double[] low = new double[size];
        double[] open = new double[size];
        double[] close = new double[size];
        double[] volume = new double[size];

        for (int i = 0; i < size; i++) {
            Candle c = candles.get(i);
            dates[i] = new Date(c.timestamp());
            high[i] = c.high();
            low[i] = c.low();
            open[i] = c.open();
            close[i] = c.close();
            volume[i] = c.volume();
        }

        DefaultHighLowDataset dataset = new DefaultHighLowDataset(
            "Price", dates, high, low, open, close, volume);
        plot.setDataset(0, dataset);

        CandlestickRenderer renderer = new CandlestickRenderer();
        Color upColor = applyOpacity(ChartStyles.CANDLE_UP_COLOR);
        Color downColor = applyOpacity(ChartStyles.CANDLE_DOWN_COLOR);
        renderer.setUpPaint(upColor);
        renderer.setDownPaint(downColor);
        renderer.setUseOutlinePaint(false);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        plot.setRenderer(0, renderer);
    }

    private Color applyOpacity(Color color) {
        if (priceOpacity >= 255) return color;
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), priceOpacity);
    }

    // ===== Configuration =====

    /**
     * Set candlestick mode (true) or line mode (false).
     */
    public void setCandlestickMode(boolean candlestickMode) {
        this.candlestickMode = candlestickMode;
    }

    /**
     * Get current chart mode.
     */
    public boolean isCandlestickMode() {
        return candlestickMode;
    }

    /**
     * Set price opacity (0-255).
     */
    public void setPriceOpacity(int opacity) {
        this.priceOpacity = Math.max(0, Math.min(255, opacity));
    }

    // ===== Overlay Management =====

    /**
     * Add an overlay to this chart.
     */
    public void addOverlay(ChartOverlay overlay) {
        overlays.add(overlay);
    }

    /**
     * Remove an overlay from this chart.
     */
    public void removeOverlay(ChartOverlay overlay) {
        overlays.remove(overlay);
    }

    /**
     * Clear all overlays.
     */
    public void clearOverlays() {
        overlays.clear();
    }

    /**
     * Get all overlays.
     */
    public List<ChartOverlay> getOverlays() {
        return new ArrayList<>(overlays);
    }
}
