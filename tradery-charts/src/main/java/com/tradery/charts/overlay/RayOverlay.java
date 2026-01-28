package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.indicator.IndicatorPool;
import com.tradery.charts.indicator.IndicatorSubscription;
import com.tradery.charts.indicator.impl.RaysCompute;
import com.tradery.core.indicators.RotatingRays;
import com.tradery.core.indicators.RotatingRays.Ray;
import com.tradery.core.indicators.RotatingRays.RaySet;
import com.tradery.core.model.Candle;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotating Ray Trendlines overlay.
 *
 * Resistance rays: Start from ATH, rotate clockwise to find successive peaks (descending)
 * Support rays: Start from ATL, rotate counter-clockwise to find successive troughs (ascending)
 *
 * Each ray connects two peaks/troughs and can be used to detect breakouts.
 * Rays are drawn from their start point and extrapolated to the right edge of the chart.
 *
 * Subscribes to RaysCompute for async background computation.
 */
public class RayOverlay implements ChartOverlay {

    // Resistance ray colors (blue gradient - brighter = newer)
    private static final Color[] RESISTANCE_COLORS = {
        new Color(30, 90, 180),
        new Color(50, 110, 200),
        new Color(70, 130, 220),
        new Color(90, 150, 235),
        new Color(110, 170, 250)
    };

    // Support ray colors (orange gradient - brighter = newer)
    private static final Color[] SUPPORT_COLORS = {
        new Color(180, 90, 30),
        new Color(200, 110, 50),
        new Color(220, 130, 70),
        new Color(235, 150, 90),
        new Color(250, 170, 110)
    };

    private static final BasicStroke RAY_STROKE = new BasicStroke(1.5f);
    private static final BasicStroke BROKEN_STROKE = new BasicStroke(
        1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
        10.0f, new float[]{5.0f, 5.0f}, 0.0f);

    private final int lookback;
    private final int skip;
    private final int maxRays;
    private final boolean showResistance;
    private final boolean showSupport;

    private final List<XYLineAnnotation> annotations = new ArrayList<>();
    private IndicatorSubscription<RaysCompute.Result> subscription;

    public RayOverlay() {
        this(200, 5, 5, true, true);
    }

    public RayOverlay(int lookback, int skip) {
        this(lookback, skip, 5, true, true);
    }

    public RayOverlay(int lookback, int skip, int maxRays, boolean showResistance, boolean showSupport) {
        this.lookback = lookback;
        this.skip = skip;
        this.maxRays = maxRays;
        this.showResistance = showResistance;
        this.showSupport = showSupport;
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        List<Candle> candles = provider.getCandles();
        if (candles == null || candles.size() < lookback) return;

        IndicatorPool pool = provider.getIndicatorPool();
        if (pool == null) return;

        // Clear previous annotations
        for (XYLineAnnotation ann : annotations) {
            plot.removeAnnotation(ann);
        }
        annotations.clear();

        if (subscription != null) subscription.close();
        subscription = pool.subscribe(new RaysCompute(lookback, skip, showResistance, showSupport));
        subscription.onReady(result -> {
            if (result == null) return;
            List<Candle> c = provider.getCandles();
            if (c == null || c.size() < lookback) return;

            // Clear again in case of recomputation
            for (XYLineAnnotation ann : annotations) {
                plot.removeAnnotation(ann);
            }
            annotations.clear();

            int lastIdx = c.size() - 1;
            long endTime = c.get(lastIdx).timestamp();
            double currentPrice = c.get(lastIdx).close();

            // Extend rays into the future by 20 bars
            long barInterval = lastIdx > 0 ? c.get(lastIdx).timestamp() - c.get(lastIdx - 1).timestamp() : 0;
            long futureEndTime = endTime + barInterval * 20;

            if (showResistance && result.resistance() != null) {
                drawRays(plot, c, result.resistance(), RESISTANCE_COLORS, currentPrice, futureEndTime);
            }
            if (showSupport && result.support() != null) {
                drawRays(plot, c, result.support(), SUPPORT_COLORS, currentPrice, futureEndTime);
            }

            plot.getChart().fireChartChanged();
        });
    }

    private void drawRays(XYPlot plot, List<Candle> candles, RaySet raySet,
                          Color[] colors, double currentPrice, long endTime) {
        int rayCount = Math.min(raySet.count(), maxRays);

        for (int i = 0; i < rayCount; i++) {
            Ray ray = raySet.getRay(i + 1);  // 1-indexed
            if (ray == null) continue;

            // Get ray start point
            long rayStartTime = candles.get(ray.startBar()).timestamp();
            double rayStartPrice = ray.startPrice();

            // Extrapolate ray beyond end of chart
            int lastBar = candles.size() - 1;
            int futureBars = 20;
            double rayEndPrice = ray.priceAt(lastBar + futureBars);

            // Check if ray is broken (price crossed through it)
            boolean isBroken = raySet.isResistance()
                ? currentPrice > ray.priceAt(lastBar)
                : currentPrice < ray.priceAt(lastBar);

            // Choose color (cycle through gradient)
            Color color = colors[i % colors.length];

            // Dim color if broken
            if (isBroken) {
                color = new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    128  // Semi-transparent
                );
            }

            // Create and add line annotation
            XYLineAnnotation annotation = new XYLineAnnotation(
                rayStartTime, rayStartPrice,
                endTime, rayEndPrice,
                isBroken ? BROKEN_STROKE : RAY_STROKE,
                color
            );
            plot.addAnnotation(annotation);
            annotations.add(annotation);
        }
    }

    @Override
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder("Rays");
        if (showResistance && !showSupport) {
            sb.append(" (R)");
        } else if (showSupport && !showResistance) {
            sb.append(" (S)");
        }
        return sb.toString();
    }

    @Override
    public int getDatasetCount() {
        return 0;  // Uses annotations, not datasets
    }
}
