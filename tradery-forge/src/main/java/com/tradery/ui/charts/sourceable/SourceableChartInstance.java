package com.tradery.ui.charts.sourceable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tradery.model.Candle;
import com.tradery.model.DataSourceSelection;
import com.tradery.ui.charts.ChartStyles;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of a sourceable chart.
 * Supports all chart types with type-specific settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceableChartInstance extends SourceableChart {

    private SourceableChartType type;
    private Map<String, Object> settings;

    @JsonIgnore
    private JFreeChart chart;

    @JsonIgnore
    private boolean needsUpdate = true;

    public SourceableChartInstance() {
        super();
        this.settings = new HashMap<>();
    }

    public SourceableChartInstance(SourceableChartType type, String name, DataSourceSelection sources) {
        super(name, sources);
        this.type = type;
        this.settings = new HashMap<>();
        initializeDefaultSettings();
    }

    private void initializeDefaultSettings() {
        switch (type) {
            case VOLUME -> {
                settings.put("showUpDown", true);  // Color by candle direction
            }
            case DELTA, CVD -> {
                settings.put("showZeroLine", true);
            }
            case FOOTPRINT -> {
                settings.put("tickSize", 50.0);
                settings.put("showImbalances", true);
                settings.put("showNumbers", false);
            }
            case WHALE_DELTA -> {
                settings.put("threshold", 100000.0);
            }
            case FUNDING -> {
                settings.put("showPredicted", false);
            }
            case PREMIUM -> {
                settings.put("showPercentage", true);
            }
            default -> {}
        }
    }

    @Override
    public SourceableChartType getChartType() {
        return type;
    }

    public void setType(SourceableChartType type) {
        this.type = type;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new HashMap<>();
    }

    public Object getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, Object value) {
        settings.put(key, value);
        needsUpdate = true;
    }

    @Override
    public void updateData(ChartDataContext context) {
        if (context == null || !context.hasCandles()) {
            return;
        }

        switch (type) {
            case VOLUME -> updateVolumeChart(context);
            case DELTA -> updateDeltaChart(context);
            case CVD -> updateCvdChart(context);
            case WHALE_DELTA -> updateWhaleDeltaChart(context);
            case TRADE_COUNT -> updateTradeCountChart(context);
            case FUNDING -> updateFundingChart(context);
            case PREMIUM -> updatePremiumChart(context);
            case OPEN_INTEREST -> updateOiChart(context);
            case FOOTPRINT -> updateFootprintChart(context);
        }

        needsUpdate = false;
    }

    @Override
    public JFreeChart getChart() {
        return chart;
    }

    // ===== Chart Update Methods =====

    private void updateVolumeChart(ChartDataContext context) {
        List<Candle> candles = context.getCandles();
        double[] volumes = context.getVolumeValues(getSources());

        TimeSeries series = new TimeSeries("Volume");
        boolean showUpDown = Boolean.TRUE.equals(settings.get("showUpDown"));

        for (int i = 0; i < candles.size() && i < volumes.length; i++) {
            Candle c = candles.get(i);
            series.addOrUpdate(new Millisecond(new Date(c.timestamp())), volumes[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createBarChart("Volume", dataset, candles, showUpDown);
    }

    private void updateDeltaChart(ChartDataContext context) {
        List<Candle> candles = context.getCandles();
        double[] deltas = context.getDeltaValues(getSources());

        TimeSeries series = new TimeSeries("Delta");
        for (int i = 0; i < candles.size() && i < deltas.length; i++) {
            series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), deltas[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createDeltaBarChart("Delta", dataset);
    }

    private void updateCvdChart(ChartDataContext context) {
        List<Candle> candles = context.getCandles();
        double[] cvd = context.getCvdValues(getSources());

        TimeSeries series = new TimeSeries("CVD");
        for (int i = 0; i < candles.size() && i < cvd.length; i++) {
            series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), cvd[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createLineChart("CVD", dataset);
    }

    private void updateWhaleDeltaChart(ChartDataContext context) {
        List<Candle> candles = context.getCandles();
        double threshold = settings.containsKey("threshold")
            ? ((Number) settings.get("threshold")).doubleValue()
            : 100000.0;
        double[] whaleDeltas = context.getWhaleDeltaValues(getSources(), threshold);

        TimeSeries series = new TimeSeries("Whale Delta");
        for (int i = 0; i < candles.size() && i < whaleDeltas.length; i++) {
            series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), whaleDeltas[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createDeltaBarChart("Whale Delta", dataset);
    }

    private void updateTradeCountChart(ChartDataContext context) {
        List<Candle> candles = context.getCandles();
        int[] counts = context.getTradeCountValues(getSources());

        TimeSeries series = new TimeSeries("Trades");
        for (int i = 0; i < candles.size() && i < counts.length; i++) {
            series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), counts[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createBarChart("Trade Count", dataset, candles, false);
    }

    private void updateFundingChart(ChartDataContext context) {
        // Funding data comes from IndicatorEngine
        var engine = context.getIndicatorEngine();
        if (engine == null) return;

        List<Candle> candles = context.getCandles();
        TimeSeries series = new TimeSeries("Funding");

        for (int i = 0; i < candles.size(); i++) {
            double funding = engine.getFundingAt(i);
            if (!Double.isNaN(funding)) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), funding);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createLineChart("Funding Rate (%)", dataset);
    }

    private void updatePremiumChart(ChartDataContext context) {
        var engine = context.getIndicatorEngine();
        if (engine == null) return;

        List<Candle> candles = context.getCandles();
        TimeSeries series = new TimeSeries("Premium");

        for (int i = 0; i < candles.size(); i++) {
            double premium = engine.getPremiumAt(i);
            if (!Double.isNaN(premium)) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), premium);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createLineChart("Premium (%)", dataset);
    }

    private void updateOiChart(ChartDataContext context) {
        var engine = context.getIndicatorEngine();
        if (engine == null) return;

        List<Candle> candles = context.getCandles();
        TimeSeries series = new TimeSeries("OI");

        for (int i = 0; i < candles.size(); i++) {
            double oi = engine.getOIAt(i);
            if (!Double.isNaN(oi)) {
                series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), oi);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createLineChart("Open Interest", dataset);
    }

    private void updateFootprintChart(ChartDataContext context) {
        // Footprint is more complex - for now create a placeholder
        // Full implementation would use FootprintIndicator
        TimeSeries series = new TimeSeries("Footprint Delta");
        List<Candle> candles = context.getCandles();
        double[] deltas = context.getDeltaValues(getSources());

        for (int i = 0; i < candles.size() && i < deltas.length; i++) {
            series.addOrUpdate(new Millisecond(new Date(candles.get(i).timestamp())), deltas[i]);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        chart = createDeltaBarChart("Footprint Delta", dataset);
    }

    // ===== Chart Creation Helpers =====

    private JFreeChart createBarChart(String title, TimeSeriesCollection dataset,
                                      List<Candle> candles, boolean colorByDirection) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            null, null, null, dataset, false, false, false);

        XYPlot plot = chart.getXYPlot();
        styleChart(chart, plot);

        XYBarRenderer renderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                if (colorByDirection && item < candles.size()) {
                    Candle c = candles.get(item);
                    return c.close() >= c.open()
                        ? ChartStyles.DELTA_POSITIVE
                        : ChartStyles.DELTA_NEGATIVE;
                }
                return ChartStyles.TEXT_COLOR;
            }
        };
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setShadowVisible(false);
        renderer.setMargin(0.1);
        plot.setRenderer(renderer);

        return chart;
    }

    private JFreeChart createDeltaBarChart(String title, TimeSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            null, null, null, dataset, false, false, false);

        XYPlot plot = chart.getXYPlot();
        styleChart(chart, plot);

        XYBarRenderer renderer = new XYBarRenderer() {
            @Override
            public Paint getItemPaint(int series, int item) {
                Number value = dataset.getY(series, item);
                if (value != null) {
                    return value.doubleValue() >= 0
                        ? ChartStyles.DELTA_POSITIVE
                        : ChartStyles.DELTA_NEGATIVE;
                }
                return ChartStyles.TEXT_COLOR;
            }
        };
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setShadowVisible(false);
        renderer.setMargin(0.1);
        plot.setRenderer(renderer);

        // Add zero line
        if (Boolean.TRUE.equals(settings.get("showZeroLine"))) {
            ((NumberAxis) plot.getRangeAxis()).setAutoRangeIncludesZero(true);
        }

        return chart;
    }

    private JFreeChart createLineChart(String title, TimeSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            null, null, null, dataset, false, false, false);

        XYPlot plot = chart.getXYPlot();
        styleChart(chart, plot);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, ChartStyles.CVD_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(1.5f));
        plot.setRenderer(renderer);

        return chart;
    }

    private void styleChart(JFreeChart chart, XYPlot plot) {
        chart.setBackgroundPaint(ChartStyles.BACKGROUND_COLOR);

        plot.setBackgroundPaint(ChartStyles.PLOT_BACKGROUND_COLOR);
        plot.setDomainGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        plot.setRangeGridlinePaint(ChartStyles.GRIDLINE_COLOR);
        plot.setOutlinePaint(ChartStyles.TEXT_COLOR);

        // Style axes
        Font axisFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setTickLabelPaint(ChartStyles.TEXT_COLOR);
        domainAxis.setTickLabelFont(axisFont);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setTickLabelPaint(ChartStyles.TEXT_COLOR);
        rangeAxis.setTickLabelFont(axisFont);
        rangeAxis.setAutoRangeIncludesZero(false);
    }

    public boolean needsUpdate() {
        return needsUpdate;
    }

    public void markNeedsUpdate() {
        this.needsUpdate = true;
    }
}
