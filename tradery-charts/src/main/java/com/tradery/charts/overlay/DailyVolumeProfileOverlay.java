package com.tradery.charts.overlay;

import com.tradery.core.indicators.VolumeProfile;
import com.tradery.core.model.Candle;
import com.tradery.ui.controls.ChartConfig;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Overlay for daily volume profile histograms on the price chart.
 * Uses a {@link DailyProfileProvider} for data service profiles when available,
 * falling back to candle-based computation.
 */
public class DailyVolumeProfileOverlay {

    private static final Logger log = LoggerFactory.getLogger(DailyVolumeProfileOverlay.class);

    private final JFreeChart priceChart;
    private final DailyProfileProvider dailyProfileProvider; // nullable — candle fallback if null

    // Settings
    private boolean enabled = false;
    private int numBins = 24;
    private double valueAreaPct = 70.0;
    private int maxDays = 30;
    private int histogramWidth = 60;

    // Current data context
    private List<Candle> currentCandles;
    private String currentSymbol;
    private String currentTimeframe;
    private long currentStartTime;
    private long currentEndTime;

    // Computed result
    private List<DailyVolumeProfileAnnotation.DayProfile> dayProfiles;

    // Current annotation
    private DailyVolumeProfileAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    // Background computation
    private final ExecutorService computeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DVP-Compute");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger computeGeneration = new AtomicInteger(0);

    public DailyVolumeProfileOverlay(JFreeChart priceChart, DailyProfileProvider dailyProfileProvider) {
        this.priceChart = priceChart;
        this.dailyProfileProvider = dailyProfileProvider;
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    // ===== Settings =====

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            computeGeneration.incrementAndGet();
            clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setNumBins(int numBins) {
        this.numBins = numBins;
    }

    public int getNumBins() {
        return numBins;
    }

    public void setValueAreaPct(double valueAreaPct) {
        this.valueAreaPct = valueAreaPct;
    }

    public double getValueAreaPct() {
        return valueAreaPct;
    }

    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    public int getMaxDays() {
        return maxDays;
    }

    public void setHistogramWidth(int histogramWidth) {
        this.histogramWidth = histogramWidth;
    }

    public int getHistogramWidth() {
        return histogramWidth;
    }

    // ===== Data Request =====

    /**
     * Request profile computation for given candles.
     * Non-blocking - profiles computed in background.
     */
    public void requestData(List<Candle> candles, String symbol, String timeframe,
                            long startTime, long endTime) {
        log.info("DailyVolumeProfileOverlay.requestData: candles={}, symbol={}, enabled={}",
            candles != null ? candles.size() : 0, symbol, enabled);

        if (candles == null || candles.isEmpty()) {
            clear();
            return;
        }

        if (!enabled) {
            log.info("DailyVolumeProfileOverlay: not enabled, skipping");
            clear();
            return;
        }

        this.currentCandles = candles;
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        computeInBackground();
    }

    /**
     * Compute day profiles on background thread.
     * Tries data service first, falls back to candle computation.
     */
    private void computeInBackground() {
        List<Candle> candles = List.copyOf(currentCandles);
        String symbol = currentSymbol;
        int bins = numBins;
        double vaPct = valueAreaPct;
        int days = maxDays;

        int generation = computeGeneration.incrementAndGet();

        computeExecutor.submit(() -> {
            try {
                List<DailyVolumeProfileAnnotation.DayProfile> profiles = null;

                // Try data service first
                if (dailyProfileProvider != null && dailyProfileProvider.isAvailable() && symbol != null) {
                    profiles = fetchFromProvider(symbol, candles, bins, vaPct, days);
                }

                // Fall back to candle computation
                if (profiles == null || profiles.isEmpty()) {
                    profiles = calculateDayProfilesFromCandles(candles, bins, vaPct, days);
                }

                List<DailyVolumeProfileAnnotation.DayProfile> finalProfiles = profiles;

                if (computeGeneration.get() == generation) {
                    SwingUtilities.invokeLater(() -> {
                        if (computeGeneration.get() == generation) {
                            dayProfiles = finalProfiles;
                            redraw();
                            if (onDataReady != null) {
                                onDataReady.run();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to compute daily volume profiles: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Fetch daily profiles from data service provider.
     */
    private List<DailyVolumeProfileAnnotation.DayProfile> fetchFromProvider(
            String symbol, List<Candle> candles, int bins, double vaPct, int days) {
        try {
            long rangeStart = candles.get(0).timestamp();
            long rangeEnd = candles.get(candles.size() - 1).timestamp();

            var dailyBinned = dailyProfileProvider.getDailyBinned(symbol, rangeStart, rangeEnd, bins, vaPct);
            if (dailyBinned == null || dailyBinned.isEmpty()) {
                return null;
            }

            // Limit to maxDays
            if (days > 0 && dailyBinned.size() > days) {
                dailyBinned = dailyBinned.subList(dailyBinned.size() - days, dailyBinned.size());
            }

            var profiles = new ArrayList<DailyVolumeProfileAnnotation.DayProfile>();
            for (var day : dailyBinned) {
                double[] priceLevels = day.priceLevels();
                double[] buyVolumes = day.buyVolumes();
                double[] sellVolumes = day.sellVolumes();

                if (priceLevels == null || priceLevels.length == 0) continue;

                // Compute total volumes and deltas
                double[] volumes = new double[priceLevels.length];
                double[] deltas = new double[priceLevels.length];
                double maxVol = 0;
                for (int i = 0; i < priceLevels.length; i++) {
                    double buy = buyVolumes != null && i < buyVolumes.length ? buyVolumes[i] : 0;
                    double sell = sellVolumes != null && i < sellVolumes.length ? sellVolumes[i] : 0;
                    volumes[i] = buy + sell;
                    deltas[i] = buy - sell;
                    maxVol = Math.max(maxVol, volumes[i]);
                }

                long dayEnd = day.dayStart() + 86_400_000L - 1;
                double minPrice = priceLevels[0];
                double maxPrice = priceLevels[priceLevels.length - 1];

                profiles.add(new DailyVolumeProfileAnnotation.DayProfile(
                    day.dayStart(), dayEnd, priceLevels, volumes, deltas,
                    day.poc(), day.vah(), day.val(),
                    maxVol, minPrice, maxPrice
                ));
            }
            return profiles;
        } catch (Exception e) {
            log.warn("Failed to fetch daily binned profiles for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Candle-based daily volume profile fallback.
     * Less accurate than aggTrades-based profiles but always available.
     */
    static List<DailyVolumeProfileAnnotation.DayProfile> calculateDayProfilesFromCandles(
            List<Candle> candles, int numBins, double valueAreaPct, int maxDays) {

        // Group candles by UTC day
        Map<LocalDate, List<Candle>> byDay = new LinkedHashMap<>();
        for (Candle c : candles) {
            LocalDate day = Instant.ofEpochMilli(c.timestamp())
                .atZone(ZoneOffset.UTC).toLocalDate();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(c);
        }

        var days = new ArrayList<>(byDay.keySet());
        if (maxDays > 0 && days.size() > maxDays) {
            days = new ArrayList<>(days.subList(days.size() - maxDays, days.size()));
        }

        var profiles = new ArrayList<DailyVolumeProfileAnnotation.DayProfile>();
        for (LocalDate day : days) {
            List<Candle> dayCandles = byDay.get(day);
            if (dayCandles == null || dayCandles.isEmpty()) continue;

            VolumeProfile.Result vp =
                VolumeProfile.calculate(dayCandles, dayCandles.size(), numBins, valueAreaPct);
            if (vp.priceLevels().length == 0) continue;

            long dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            double maxVol = 0;
            for (double v : vp.volumes()) maxVol = Math.max(maxVol, v);

            double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
            for (Candle c : dayCandles) {
                minPrice = Math.min(minPrice, c.low());
                maxPrice = Math.max(maxPrice, c.high());
            }

            profiles.add(new DailyVolumeProfileAnnotation.DayProfile(
                dayStart, dayEnd, vp.priceLevels(), vp.volumes(),
                vp.poc(), vp.vah(), vp.val(),
                maxVol, minPrice, maxPrice
            ));
        }
        return profiles;
    }

    // ===== Drawing =====

    /**
     * Redraw using currently available data.
     */
    public void redraw() {
        log.info("DailyVolumeProfileOverlay.redraw: enabled={}, hasData={}",
            enabled, dayProfiles != null && !dayProfiles.isEmpty());

        clear();

        if (!enabled || dayProfiles == null || dayProfiles.isEmpty()) {
            return;
        }

        XYPlot plot = priceChart.getXYPlot();
        annotation = new DailyVolumeProfileAnnotation(dayProfiles, histogramWidth);

        // Apply color mode from config
        ChartConfig config = ChartConfig.getInstance();
        String mode = config.getDailyVolumeProfileColorMode();
        try {
            annotation.setColorMode(DailyVolumeProfileAnnotation.ColorMode.valueOf(mode));
        } catch (IllegalArgumentException e) {
            // Keep default
        }

        plot.addAnnotation(annotation);

        log.info("DailyVolumeProfileOverlay.redraw: ADDED annotation with {} days", dayProfiles.size());
    }

    /**
     * Clear the annotation from the chart.
     */
    public void clear() {
        if (annotation == null) return;

        XYPlot plot = priceChart.getXYPlot();
        plot.removeAnnotation(annotation);
        annotation = null;
    }

    /**
     * Check if annotation is currently displayed.
     */
    public boolean hasAnnotation() {
        return annotation != null;
    }

    /**
     * Get current annotation (for preservation during chart updates).
     */
    public DailyVolumeProfileAnnotation getAnnotation() {
        return annotation;
    }

    // ===== Legacy API (for compatibility) =====

    /**
     * Update with candles and settings.
     * @deprecated Use requestData() + redraw() pattern instead
     */
    @Deprecated
    public void update(List<Candle> candles, String symbol, String timeframe) {
        if (candles == null || candles.isEmpty()) {
            clear();
            return;
        }
        requestData(candles, symbol, timeframe,
            candles.get(0).timestamp(),
            candles.get(candles.size() - 1).timestamp());
    }
}
