package com.tradery.charts.indicator;

import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Trade;

import java.util.List;

/**
 * Immutable context passed to indicator charts for data access.
 */
public record ChartDataContext(
    String symbol,
    String timeframe,
    long startTime,
    long endTime,
    IndicatorDataProvider indicatorDataProvider,
    IndicatorEngine indicatorEngine,
    List<Trade> trades
) {}
