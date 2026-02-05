package com.tradery.charts.overlay;

import com.tradery.charts.core.ChartDataProvider;
import com.tradery.charts.util.ChartStyles;
import com.tradery.core.model.Candle;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Last price overlay.
 * Draws a horizontal line at the last candle's close price with a price label.
 */
public class LastPriceOverlay implements ChartOverlay {

    private static final Color LINE_COLOR = new Color(100, 149, 237);  // Cornflower blue
    private static final Color LABEL_BG_COLOR = new Color(100, 149, 237);
    private static final Color LABEL_TEXT_COLOR = Color.WHITE;
    private static final BasicStroke LINE_STROKE = new BasicStroke(
        1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
        10.0f, new float[]{4.0f, 4.0f}, 0.0f);  // Dashed line
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private final DecimalFormat priceFormat;
    private ValueMarker marker;

    public LastPriceOverlay() {
        this.priceFormat = new DecimalFormat("#,##0.00");
    }

    public LastPriceOverlay(String formatPattern) {
        this.priceFormat = new DecimalFormat(formatPattern);
    }

    @Override
    public void apply(XYPlot plot, ChartDataProvider provider, int datasetIndex) {
        // Remove old marker if exists
        if (marker != null) {
            plot.removeRangeMarker(marker);
        }

        List<Candle> candles = provider.getCandles();
        if (candles == null || candles.isEmpty()) return;

        // Get the last candle's close price
        Candle lastCandle = candles.get(candles.size() - 1);
        double lastPrice = lastCandle.close();

        // Determine color based on price direction
        Color lineColor = LINE_COLOR;
        Color labelBgColor = LABEL_BG_COLOR;

        if (candles.size() >= 2) {
            Candle prevCandle = candles.get(candles.size() - 2);
            if (lastPrice > prevCandle.close()) {
                lineColor = ChartStyles.CANDLE_UP_COLOR;
                labelBgColor = ChartStyles.CANDLE_UP_COLOR;
            } else if (lastPrice < prevCandle.close()) {
                lineColor = ChartStyles.CANDLE_DOWN_COLOR;
                labelBgColor = ChartStyles.CANDLE_DOWN_COLOR;
            }
        }

        // Create the value marker (horizontal line)
        marker = new ValueMarker(lastPrice);
        marker.setPaint(lineColor);
        marker.setStroke(LINE_STROKE);

        // Add price label
        String priceLabel = priceFormat.format(lastPrice);
        marker.setLabel(priceLabel);
        marker.setLabelFont(LABEL_FONT);
        marker.setLabelPaint(LABEL_TEXT_COLOR);
        marker.setLabelBackgroundColor(labelBgColor);
        marker.setLabelAnchor(RectangleAnchor.RIGHT);
        marker.setLabelTextAnchor(TextAnchor.CENTER_RIGHT);
        marker.setLabelOffsetType(org.jfree.chart.ui.LengthAdjustmentType.EXPAND);

        // Add marker to plot
        plot.addRangeMarker(marker);
    }

    @Override
    public String getDisplayName() {
        return "Last Price";
    }

    @Override
    public int getDatasetCount() {
        return 0;  // Markers don't use datasets
    }

    @Override
    public void close() {
        marker = null;
    }
}
