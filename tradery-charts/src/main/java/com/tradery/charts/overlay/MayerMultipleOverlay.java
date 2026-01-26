package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.charts.util.RendererBuilder;
import com.tradery.core.model.Candle;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.awt.Font;
import java.util.Date;
import java.util.List;

/**
 * Mayer Multiple overlay.
 * Shows the SMA line with color coding based on the Mayer Multiple (price / SMA).
 * MM > 2.4: historically overbought (red)
 * MM < 0.8: historically oversold (green)
 * MM 0.8-2.4: neutral (blue)
 *
 * Uses IndicatorEngine.getSMA() for data.
 */
public class MayerMultipleOverlay implements ChartOverlay {

    private static final Color OVERBOUGHT_COLOR = new Color(244, 67, 54);    // Red
    private static final Color OVERSOLD_COLOR = new Color(76, 175, 80);      // Green
    private static final Color NEUTRAL_COLOR = new Color(33, 150, 243);      // Blue

    private static final double OVERBOUGHT_THRESHOLD = 2.4;
    private static final double OVERSOLD_THRESHOLD = 0.8;

    private final int period;

    public MayerMultipleOverlay() {
        this(200);  // Default 200-day SMA
    }

    public MayerMultipleOverlay(int period) {
        this.period = period;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles.isEmpty()) return;

        // Get SMA from IndicatorEngine
        double[] sma = provider.getIndicatorEngine().getSMA(period);
        if (sma == null || sma.length == 0) return;

        // Build time series for SMA line
        TimeSeries smaSeries = new TimeSeries("SMA(" + period + ")");
        for (int i = period - 1; i < candles.size() && i < sma.length; i++) {
            Candle c = candles.get(i);
            if (!Double.isNaN(sma[i])) {
                smaSeries.addOrUpdate(new Millisecond(new Date(c.timestamp())), sma[i]);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(smaSeries);

        // Calculate current Mayer Multiple
        int lastIdx = candles.size() - 1;
        double currentMM = 0;
        if (lastIdx >= 0 && lastIdx < sma.length && !Double.isNaN(sma[lastIdx]) && sma[lastIdx] > 0) {
            currentMM = candles.get(lastIdx).close() / sma[lastIdx];
        }

        // Color based on MM value
        Color color;
        if (currentMM >= OVERBOUGHT_THRESHOLD) {
            color = OVERBOUGHT_COLOR;
        } else if (currentMM <= OVERSOLD_THRESHOLD) {
            color = OVERSOLD_COLOR;
        } else {
            color = NEUTRAL_COLOR;
        }

        // Add to plot
        plot.setDataset(datasetIndex, dataset);
        plot.setRenderer(datasetIndex, RendererBuilder.lineRenderer(color, ChartStyles.MEDIUM_STROKE));

        // Add MM value annotation at the end of the line
        if (currentMM > 0 && !candles.isEmpty()) {
            Candle lastCandle = candles.get(lastIdx);
            double lastSma = sma[lastIdx];
            if (!Double.isNaN(lastSma)) {
                String label = String.format("MM: %.2f", currentMM);
                XYTextAnnotation annotation = new XYTextAnnotation(label, lastCandle.timestamp(), lastSma);
                annotation.setFont(new Font("SansSerif", Font.PLAIN, 10));
                annotation.setPaint(color);
                plot.addAnnotation(annotation);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return String.format("Mayer Multiple (%d)", period);
    }
}
