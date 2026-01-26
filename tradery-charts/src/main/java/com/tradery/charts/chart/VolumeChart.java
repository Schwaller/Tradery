package com.tradery.charts.chart;

import com.tradery.charts.core.ChartCoordinator;
import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.Color;
import java.awt.Paint;
import java.util.List;

/**
 * Volume bar chart that colors bars by price direction.
 */
public class VolumeChart extends SyncedChart {

    private boolean colorByDirection = true;
    private boolean useVolumeColors = false;  // Wyckoff-style gradient

    public VolumeChart(ChartCoordinator coordinator) {
        this(coordinator, "Volume");
    }

    public VolumeChart(ChartCoordinator coordinator, String title) {
        super(coordinator, title);
    }

    @Override
    protected JFreeChart createChart() {
        DateAxis domainAxis = new DateAxis("");
        NumberAxis rangeAxis = new NumberAxis("");
        rangeAxis.setAutoRangeIncludesZero(true);

        XYPlot plot = new XYPlot(null, domainAxis, rangeAxis, null);

        return new JFreeChart(null, null, plot, false);
    }

    @Override
    public void updateData(ChartDataProvider provider) {
        if (!provider.hasCandles()) return;

        XYPlot plot = getPlot();
        List<Candle> candles = provider.getCandles();

        // Clear old data
        plot.clearAnnotations();
        ChartStyles.addChartTitleAnnotation(plot, title);

        // Create volume series
        XYSeries series = new XYSeries("Volume");
        for (Candle c : candles) {
            series.add(c.timestamp(), c.volume());
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        plot.setDataset(0, dataset);

        // Create color-coded renderer
        XYBarRenderer renderer;
        if (useVolumeColors) {
            renderer = createWyckoffRenderer(candles, dataset);
        } else if (colorByDirection) {
            renderer = createDirectionRenderer(candles, dataset);
        } else {
            renderer = createSimpleRenderer();
        }

        plot.setRenderer(0, renderer);
    }

    private XYBarRenderer createSimpleRenderer() {
        XYBarRenderer renderer = new XYBarRenderer(0.0);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        renderer.setSeriesPaint(0, ChartStyles.BUY_VOLUME_COLOR);
        return renderer;
    }

    private XYBarRenderer createDirectionRenderer(List<Candle> candles, XYSeriesCollection dataset) {
        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public Paint getItemPaint(int series, int item) {
                if (item >= candles.size()) return ChartStyles.BUY_VOLUME_COLOR;
                Candle c = candles.get(item);
                return c.close() >= c.open()
                    ? ChartStyles.BUY_VOLUME_COLOR
                    : ChartStyles.SELL_VOLUME_COLOR;
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        return renderer;
    }

    private XYBarRenderer createWyckoffRenderer(List<Candle> candles, XYSeriesCollection dataset) {
        // Calculate volume percentiles for coloring
        double[] volumes = candles.stream().mapToDouble(Candle::volume).sorted().toArray();
        double[] thresholds = new double[6];
        int n = volumes.length;
        thresholds[0] = volumes[(int)(n * 0.10)];  // Ultra low
        thresholds[1] = volumes[(int)(n * 0.25)];  // Very low
        thresholds[2] = volumes[(int)(n * 0.40)];  // Low
        thresholds[3] = volumes[(int)(n * 0.60)];  // Average
        thresholds[4] = volumes[(int)(n * 0.80)];  // High
        thresholds[5] = volumes[(int)(n * 0.95)];  // Very high

        XYBarRenderer renderer = new XYBarRenderer(0.0) {
            @Override
            public Paint getItemPaint(int series, int item) {
                if (item >= candles.size()) return ChartStyles.VOLUME_COLORS[3];
                double vol = candles.get(item).volume();

                if (vol < thresholds[0]) return ChartStyles.VOLUME_COLORS[0];
                if (vol < thresholds[1]) return ChartStyles.VOLUME_COLORS[1];
                if (vol < thresholds[2]) return ChartStyles.VOLUME_COLORS[2];
                if (vol < thresholds[3]) return ChartStyles.VOLUME_COLORS[3];
                if (vol < thresholds[4]) return ChartStyles.VOLUME_COLORS[4];
                if (vol < thresholds[5]) return ChartStyles.VOLUME_COLORS[5];
                return ChartStyles.VOLUME_COLORS[6];
            }
        };
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setDrawBarOutline(false);
        return renderer;
    }

    // ===== Configuration =====

    /**
     * Set whether to color bars by price direction.
     */
    public void setColorByDirection(boolean colorByDirection) {
        this.colorByDirection = colorByDirection;
    }

    /**
     * Set whether to use Wyckoff-style volume gradient coloring.
     * This overrides colorByDirection when enabled.
     */
    public void setUseVolumeColors(boolean useVolumeColors) {
        this.useVolumeColors = useVolumeColors;
    }
}
