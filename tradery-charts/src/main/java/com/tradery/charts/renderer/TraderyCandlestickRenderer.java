package com.tradery.charts.renderer;

import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;

import java.awt.*;

/**
 * Custom candlestick renderer that draws wicks in the candle body color
 * (green for up, red for down) with no outline around bodies.
 */
public class TraderyCandlestickRenderer extends CandlestickRenderer {

    private final Color upColor;
    private final Color downColor;

    public TraderyCandlestickRenderer(Color upColor, Color downColor) {
        super();
        this.upColor = upColor;
        this.downColor = downColor;
        setUpPaint(upColor);
        setDownPaint(downColor);
        setUseOutlinePaint(false);
        setDrawVolume(false);
    }

    @Override
    public Paint getItemPaint(int row, int column) {
        XYDataset ds = getPlot().getDataset();
        if (ds instanceof OHLCDataset ohlc && column < ohlc.getItemCount(row)) {
            double open = ohlc.getOpenValue(row, column);
            double close = ohlc.getCloseValue(row, column);
            return close >= open ? upColor : downColor;
        }
        return downColor;
    }
}
