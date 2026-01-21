package com.tradery.ui.charts;

import com.tradery.ApplicationContext;
import com.tradery.data.PageState;
import com.tradery.data.page.IndicatorPage;
import com.tradery.data.page.IndicatorPageListener;
import com.tradery.data.page.IndicatorPageManager;
import com.tradery.data.page.IndicatorType;
import com.tradery.model.Candle;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Overlay for daily volume profile histograms on the price chart.
 * Uses IndicatorPageManager for background computation - never blocks EDT.
 */
public class DailyVolumeProfileOverlay {

    private static final Logger log = LoggerFactory.getLogger(DailyVolumeProfileOverlay.class);

    private final JFreeChart priceChart;

    // Settings
    private boolean enabled = false;
    private int numBins = 24;
    private double valueAreaPct = 70.0;
    private int maxDays = 30;
    private int histogramWidth = 60;

    // Current data context
    private String currentSymbol;
    private String currentTimeframe;
    private long currentStartTime;
    private long currentEndTime;

    // Indicator page (background computed)
    private IndicatorPage<List<DailyVolumeProfileAnnotation.DayProfile>> profilePage;
    private final ProfilePageListener pageListener = new ProfilePageListener();

    // Current annotation
    private DailyVolumeProfileAnnotation annotation;

    // Callback for repaint
    private Runnable onDataReady;

    public DailyVolumeProfileOverlay(JFreeChart priceChart) {
        this.priceChart = priceChart;
    }

    public void setOnDataReady(Runnable callback) {
        this.onDataReady = callback;
    }

    // ===== Settings =====

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
            releasePage();
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
            releasePage();
            clear();
            return;
        }

        if (!enabled) {
            log.info("DailyVolumeProfileOverlay: not enabled, skipping");
            releasePage();
            clear();
            return;
        }

        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr == null) {
            log.warn("IndicatorPageManager not available");
            return;
        }

        // Check if we already have a page with the same params - don't release it
        if (profilePage != null &&
            symbol.equals(currentSymbol) &&
            timeframe.equals(currentTimeframe) &&
            startTime == currentStartTime &&
            endTime == currentEndTime) {
            log.info("DailyVolumeProfileOverlay: reusing existing page request");
            return;
        }

        // Store new context
        this.currentSymbol = symbol;
        this.currentTimeframe = timeframe;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

        // Release previous page since params changed
        releasePage();

        // Request computation with params: numBins:valueAreaPct:maxDays
        String params = numBins + ":" + valueAreaPct + ":" + maxDays;
        log.info("DailyVolumeProfileOverlay: requesting NEW page with params={}", params);
        profilePage = pageMgr.request(
            IndicatorType.DAILY_VOLUME_PROFILE, params,
            symbol, timeframe, startTime, endTime,
            pageListener,
            "DailyVolumeProfileOverlay");

        log.debug("Requested daily volume profile: {} {} {}-{}", symbol, timeframe, startTime, endTime);
    }

    /**
     * Release indicator page when no longer needed.
     */
    public void releasePage() {
        if (profilePage == null) return;

        IndicatorPageManager pageMgr = ApplicationContext.getInstance().getIndicatorPageManager();
        if (pageMgr != null) {
            pageMgr.release(profilePage, pageListener);
        }
        profilePage = null;
    }

    // ===== Drawing =====

    /**
     * Redraw using currently available data.
     */
    public void redraw() {
        log.info("DailyVolumeProfileOverlay.redraw: enabled={}, hasPage={}, hasData={}",
            enabled, profilePage != null, profilePage != null && profilePage.hasData());

        clear();

        if (!enabled || profilePage == null || !profilePage.hasData()) {
            log.info("DailyVolumeProfileOverlay.redraw: skipping (enabled={}, hasPage={}, hasData={})",
                enabled, profilePage != null, profilePage != null && profilePage.hasData());
            return;
        }

        List<DailyVolumeProfileAnnotation.DayProfile> profiles = profilePage.getData();
        if (profiles == null || profiles.isEmpty()) {
            log.info("DailyVolumeProfileOverlay.redraw: profiles empty");
            return;
        }

        XYPlot plot = priceChart.getXYPlot();
        annotation = new DailyVolumeProfileAnnotation(profiles, histogramWidth);
        plot.addAnnotation(annotation);

        log.info("DailyVolumeProfileOverlay.redraw: ADDED annotation with {} days", profiles.size());
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

    // ===== Listener =====

    private class ProfilePageListener implements IndicatorPageListener<List<DailyVolumeProfileAnnotation.DayProfile>> {
        @Override
        public void onStateChanged(IndicatorPage<List<DailyVolumeProfileAnnotation.DayProfile>> page,
                                   PageState oldState, PageState newState) {
            if (newState == PageState.READY) {
                redraw();
                if (onDataReady != null) {
                    onDataReady.run();
                }
            }
        }

        @Override
        public void onDataChanged(IndicatorPage<List<DailyVolumeProfileAnnotation.DayProfile>> page) {
            redraw();
            if (onDataReady != null) {
                onDataReady.run();
            }
        }
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
