package com.tradery.data.page;

import com.tradery.data.PageState;
import com.tradery.model.*;

/**
 * Tracks what data is required for a backtest and when all data is ready.
 *
 * This class aggregates multiple data pages and provides:
 * - Combined readiness check
 * - Overall state for UI display
 * - Error aggregation
 */
public class DataRequirements {

    private final String symbol;
    private final String timeframe;
    private final long startTime;
    private final long endTime;

    // Required pages (null = not required)
    private DataPageView<Candle> candlePage;           // Always required
    private DataPageView<AggTrade> aggTradesPage;      // If sub-minute or delta DSL
    private DataPageView<FundingRate> fundingPage;     // If FUNDING in DSL
    private DataPageView<OpenInterest> oiPage;         // If OI in DSL
    private DataPageView<PremiumIndex> premiumPage;    // If PREMIUM in DSL

    // Track which pages are view-only (don't block backtest)
    private boolean aggTradesViewOnly = false;
    private boolean fundingViewOnly = false;
    private boolean oiViewOnly = false;
    private boolean premiumViewOnly = false;

    // Phase candle pages keyed by "symbol:timeframe"
    private final java.util.Map<String, DataPageView<Candle>> phaseCandlePages = new java.util.HashMap<>();

    public DataRequirements(String symbol, String timeframe, long startTime, long endTime) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // ========== Setters ==========

    public void setCandlePage(DataPageView<Candle> candlePage) {
        this.candlePage = candlePage;
    }

    public void setAggTradesPage(DataPageView<AggTrade> aggTradesPage, boolean viewOnly) {
        this.aggTradesPage = aggTradesPage;
        this.aggTradesViewOnly = viewOnly;
    }

    public void setFundingPage(DataPageView<FundingRate> fundingPage, boolean viewOnly) {
        this.fundingPage = fundingPage;
        this.fundingViewOnly = viewOnly;
    }

    public void setOiPage(DataPageView<OpenInterest> oiPage, boolean viewOnly) {
        this.oiPage = oiPage;
        this.oiViewOnly = viewOnly;
    }

    public void setPremiumPage(DataPageView<PremiumIndex> premiumPage, boolean viewOnly) {
        this.premiumPage = premiumPage;
        this.premiumViewOnly = viewOnly;
    }

    /**
     * Add a phase candle page.
     * @param key Format: "symbol:timeframe"
     */
    public void addPhaseCandlePage(String key, DataPageView<Candle> page) {
        phaseCandlePages.put(key, page);
    }

    // ========== Getters ==========

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public DataPageView<Candle> getCandlePage() {
        return candlePage;
    }

    public DataPageView<AggTrade> getAggTradesPage() {
        return aggTradesPage;
    }

    public DataPageView<FundingRate> getFundingPage() {
        return fundingPage;
    }

    public DataPageView<OpenInterest> getOiPage() {
        return oiPage;
    }

    public DataPageView<PremiumIndex> getPremiumPage() {
        return premiumPage;
    }

    /**
     * Get phase candle pages map.
     */
    public java.util.Map<String, DataPageView<Candle>> getPhaseCandlePages() {
        return phaseCandlePages;
    }

    /**
     * Get phase candles as a map of symbol:timeframe -> List<Candle>.
     * Only includes pages that are ready.
     */
    public java.util.Map<String, java.util.List<Candle>> getPhaseCandles() {
        java.util.Map<String, java.util.List<Candle>> result = new java.util.HashMap<>();
        for (var entry : phaseCandlePages.entrySet()) {
            if (entry.getValue().isReady()) {
                result.put(entry.getKey(), entry.getValue().getData());
            }
        }
        return result;
    }

    // ========== Required for Backtest Checks ==========

    public boolean isAggTradesRequired() {
        return aggTradesPage != null && !aggTradesViewOnly;
    }

    public boolean isFundingRequired() {
        return fundingPage != null && !fundingViewOnly;
    }

    public boolean isOiRequired() {
        return oiPage != null && !oiViewOnly;
    }

    public boolean isPremiumRequired() {
        return premiumPage != null && !premiumViewOnly;
    }

    // ========== State Checks ==========

    /**
     * Check if ALL required data is ready for BACKTEST.
     * View-only pages (for charts) don't block the backtest.
     */
    public boolean isReady() {
        // Candles are always required
        if (candlePage == null || !candlePage.isReady()) {
            return false;
        }

        // Check optional pages only if they're set AND required for backtest (not view-only)
        if (aggTradesPage != null && !aggTradesViewOnly && !aggTradesPage.isReady()) {
            return false;
        }
        if (fundingPage != null && !fundingViewOnly && !fundingPage.isReady()) {
            return false;
        }
        if (oiPage != null && !oiViewOnly && !oiPage.isReady()) {
            return false;
        }
        if (premiumPage != null && !premiumViewOnly && !premiumPage.isReady()) {
            return false;
        }

        // Check phase candle pages (always required for backtest)
        for (DataPageView<Candle> phasePage : phaseCandlePages.values()) {
            if (!phasePage.isReady()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if any page is currently loading.
     */
    public boolean isLoading() {
        if (candlePage != null && candlePage.isLoading()) return true;
        if (aggTradesPage != null && aggTradesPage.isLoading()) return true;
        if (fundingPage != null && fundingPage.isLoading()) return true;
        if (oiPage != null && oiPage.isLoading()) return true;
        if (premiumPage != null && premiumPage.isLoading()) return true;
        for (DataPageView<Candle> phasePage : phaseCandlePages.values()) {
            if (phasePage.isLoading()) return true;
        }
        return false;
    }

    /**
     * Check if any page has an error.
     */
    public boolean hasError() {
        if (candlePage != null && candlePage.hasError()) return true;
        if (aggTradesPage != null && aggTradesPage.hasError()) return true;
        if (fundingPage != null && fundingPage.hasError()) return true;
        if (oiPage != null && oiPage.hasError()) return true;
        if (premiumPage != null && premiumPage.hasError()) return true;
        for (DataPageView<Candle> phasePage : phaseCandlePages.values()) {
            if (phasePage.hasError()) return true;
        }
        return false;
    }

    /**
     * Get overall state for UI display.
     */
    public PageState getOverallState() {
        if (isReady()) return PageState.READY;
        if (hasError()) return PageState.ERROR;
        if (isLoading()) return PageState.LOADING;
        return PageState.EMPTY;
    }

    /**
     * Get combined error message from all pages with errors.
     */
    public String getErrorMessage() {
        StringBuilder sb = new StringBuilder();
        appendError(sb, "Candles", candlePage);
        appendError(sb, "AggTrades", aggTradesPage);
        appendError(sb, "Funding", fundingPage);
        appendError(sb, "OI", oiPage);
        appendError(sb, "Premium", premiumPage);
        return sb.isEmpty() ? null : sb.toString();
    }

    private void appendError(StringBuilder sb, String name, DataPageView<?> page) {
        if (page != null && page.hasError() && page.getErrorMessage() != null) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(name).append(": ").append(page.getErrorMessage());
        }
    }

    /**
     * Get a summary of loading progress.
     * Returns format like "Candles: READY, Funding: LOADING, OI: (not required)"
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        appendStatus(sb, "Candles", candlePage);
        appendStatus(sb, "AggTrades", aggTradesPage);
        appendStatus(sb, "Funding", fundingPage);
        appendStatus(sb, "OI", oiPage);
        appendStatus(sb, "Premium", premiumPage);
        return sb.toString();
    }

    private void appendStatus(StringBuilder sb, String name, DataPageView<?> page) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(name).append(": ");
        if (page == null) {
            sb.append("(not required)");
        } else {
            sb.append(page.getState());
            if (page.isReady()) {
                sb.append(" (").append(page.getRecordCount()).append(")");
            }
        }
    }

    @Override
    public String toString() {
        return "DataRequirements[" + symbol + "/" + timeframe +
               " " + getOverallState() + "]";
    }
}
