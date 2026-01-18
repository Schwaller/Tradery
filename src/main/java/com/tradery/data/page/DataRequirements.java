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

    public void setAggTradesPage(DataPageView<AggTrade> aggTradesPage) {
        this.aggTradesPage = aggTradesPage;
    }

    public void setFundingPage(DataPageView<FundingRate> fundingPage) {
        this.fundingPage = fundingPage;
    }

    public void setOiPage(DataPageView<OpenInterest> oiPage) {
        this.oiPage = oiPage;
    }

    public void setPremiumPage(DataPageView<PremiumIndex> premiumPage) {
        this.premiumPage = premiumPage;
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

    // ========== State Checks ==========

    /**
     * Check if ALL required data is ready.
     * This is the trigger condition for starting a backtest.
     */
    public boolean isReady() {
        // Candles are always required
        if (candlePage == null || !candlePage.isReady()) {
            return false;
        }

        // Check optional pages only if they're set (= required)
        if (aggTradesPage != null && !aggTradesPage.isReady()) {
            return false;
        }
        if (fundingPage != null && !fundingPage.isReady()) {
            return false;
        }
        if (oiPage != null && !oiPage.isReady()) {
            return false;
        }
        if (premiumPage != null && !premiumPage.isReady()) {
            return false;
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
