package com.tradery.desk.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Vertical side panel with categorized chart indicator/overlay toggles.
 */
public class IndicatorSidePanel extends JPanel {

    private final PriceChartPanel chartPanel;

    // Overlays
    private final JCheckBox sma20 = cb("SMA(20)");
    private final JCheckBox sma50 = cb("SMA(50)");
    private final JCheckBox ema20 = cb("EMA(20)");
    private final JCheckBox bb = cb("BB(20,2)");
    private final JCheckBox vwap = cb("VWAP");
    private final JCheckBox ichimoku = cb("Ichimoku");
    private final JCheckBox supertrend = cb("ST(10,3)");
    private final JCheckBox highLow = cb("H/L(20)");
    private final JCheckBox mayer = cb("MM(200)");
    private final JCheckBox poc = cb("POC(20)");
    private final JCheckBox daily = cb("Daily");
    private final JCheckBox keltner = cb("Keltner");
    private final JCheckBox donchian = cb("Donchian");
    private final JCheckBox rays = cb("Rays");
    private final JCheckBox atrBands = cb("ATR Bands");
    private final JCheckBox pivots = cb("Pivots");

    // Indicators
    private final JCheckBox rsi = cb("RSI(14)");
    private final JCheckBox macd = cb("MACD");
    private final JCheckBox atr = cb("ATR(14)");
    private final JCheckBox stochastic = cb("Stoch");
    private final JCheckBox adx = cb("ADX(14)");
    private final JCheckBox rangePos = cb("RP(200)");

    // Orderflow
    private final JCheckBox delta = cb("Delta");
    private final JCheckBox tradeCount = cb("Trades");
    private final JCheckBox buySell = cb("Buy/Sell");

    // Core
    private final JCheckBox volume = cb("Volume");

    public IndicatorSidePanel(PriceChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(160, 0));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Overlays section
        content.add(createSectionHeader("OVERLAYS"));
        content.add(createGrid(sma20, sma50, ema20, bb, vwap, ichimoku, supertrend, highLow,
                mayer, poc, daily, keltner, donchian, rays, atrBands, pivots));
        content.add(createSeparator());

        // Indicator Charts section
        content.add(createSectionHeader("INDICATORS"));
        content.add(createGrid(rsi, macd, atr, stochastic, adx, rangePos));
        content.add(createSeparator());

        // Orderflow section
        content.add(createSectionHeader("ORDERFLOW"));
        content.add(createGrid(delta, tradeCount, buySell));
        content.add(createSeparator());

        // Core section
        content.add(createSectionHeader("CORE"));
        content.add(createGrid(volume));

        // Glue at bottom
        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // Wire overlay checkboxes
        for (JCheckBox cb : new JCheckBox[]{sma20, sma50, ema20, bb, vwap, ichimoku, supertrend,
                highLow, mayer, poc, daily, keltner, donchian, rays, atrBands, pivots}) {
            cb.addActionListener(e -> updateOverlays());
        }

        // Wire indicator checkboxes directly
        rsi.addActionListener(e -> chartPanel.setRsiEnabled(rsi.isSelected()));
        macd.addActionListener(e -> chartPanel.setMacdEnabled(macd.isSelected()));
        atr.addActionListener(e -> chartPanel.setAtrEnabled(atr.isSelected()));
        stochastic.addActionListener(e -> chartPanel.setStochasticEnabled(stochastic.isSelected()));
        adx.addActionListener(e -> chartPanel.setAdxEnabled(adx.isSelected()));
        rangePos.addActionListener(e -> chartPanel.setRangePositionEnabled(rangePos.isSelected()));
        delta.addActionListener(e -> chartPanel.setDeltaEnabled(delta.isSelected()));
        tradeCount.addActionListener(e -> chartPanel.setTradeCountEnabled(tradeCount.isSelected()));
        buySell.addActionListener(e -> chartPanel.setVolumeRatioEnabled(buySell.isSelected()));
        volume.addActionListener(e -> chartPanel.setVolumeEnabled(volume.isSelected()));
    }

    private void updateOverlays() {
        chartPanel.clearOverlays();

        if (sma20.isSelected()) chartPanel.addSmaOverlay(20);
        if (sma50.isSelected()) chartPanel.addSmaOverlay(50);
        if (ema20.isSelected()) chartPanel.addEmaOverlay(20);
        if (bb.isSelected()) chartPanel.addBollingerOverlay(20, 2.0);
        if (vwap.isSelected()) chartPanel.addVwapOverlay();
        if (ichimoku.isSelected()) chartPanel.addIchimokuOverlay();
        if (supertrend.isSelected()) chartPanel.addSupertrendOverlay();
        if (highLow.isSelected()) chartPanel.addHighLowOverlay();
        if (mayer.isSelected()) chartPanel.addMayerMultipleOverlay();
        if (poc.isSelected()) chartPanel.addPocOverlay();
        if (daily.isSelected()) chartPanel.addDailyLevelsOverlay();
        if (keltner.isSelected()) chartPanel.addKeltnerOverlay();
        if (donchian.isSelected()) chartPanel.addDonchianOverlay();
        if (rays.isSelected()) chartPanel.addRayOverlay();
        if (atrBands.isSelected()) chartPanel.addAtrBandsOverlay();
        if (pivots.isSelected()) chartPanel.addPivotPointsOverlay();

        chartPanel.getCandlestickChart().updateData(chartPanel.getDataProvider());
    }

    private static JCheckBox cb(String label) {
        JCheckBox checkBox = new JCheckBox(label);
        checkBox.setFocusPainted(false);
        checkBox.setFont(checkBox.getFont().deriveFont(11f));
        return checkBox;
    }

    private static JPanel createSectionHeader(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 0));
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        panel.add(label);
        return panel;
    }

    private static JSeparator createSeparator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return sep;
    }

    private static JPanel createGrid(JCheckBox... checkBoxes) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 0, 0));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, checkBoxes.length / 2 * 22 + 22));
        for (JCheckBox cb : checkBoxes) {
            grid.add(cb);
        }
        return grid;
    }
}
