package com.tradery.forge.ui.charts.indicator;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Trade;
import com.tradery.forge.ui.charts.IndicatorDataService;

import java.util.List;

/**
 * Immutable context passed to indicator charts for data access.
 */
public record ChartDataContext(
    String symbol,
    String timeframe,
    long startTime,
    long endTime,
    IndicatorDataService indicatorDataService,
    IndicatorEngine indicatorEngine,
    List<Trade> trades
) {}
