package com.tradery.charts.indicator.impl;

import com.tradery.charts.indicator.IndicatorCompute;
import com.tradery.core.indicators.IndicatorEngine;
import com.tradery.core.model.Candle;

import java.util.List;

public class DailyLevelsCompute extends IndicatorCompute<DailyLevelsCompute.Result> {

    private final boolean showPrevDay;
    private final boolean showToday;

    public DailyLevelsCompute(boolean showPrevDay, boolean showToday) {
        this.showPrevDay = showPrevDay;
        this.showToday = showToday;
    }

    @Override
    public String key() {
        return "dailylevels:" + showPrevDay + ":" + showToday;
    }

    @Override
    public Result compute(IndicatorEngine engine) {
        List<Candle> candles = engine.getCandles();
        if (candles == null || candles.isEmpty()) return null;

        int len = candles.size();
        double[] prevPoc = showPrevDay ? new double[len] : null;
        double[] prevVah = showPrevDay ? new double[len] : null;
        double[] prevVal = showPrevDay ? new double[len] : null;
        double[] todayPoc = showToday ? new double[len] : null;
        double[] todayVah = showToday ? new double[len] : null;
        double[] todayVal = showToday ? new double[len] : null;

        for (int i = 0; i < len; i++) {
            if (showPrevDay) {
                prevPoc[i] = engine.getPrevDayPOCAt(i);
                prevVah[i] = engine.getPrevDayVAHAt(i);
                prevVal[i] = engine.getPrevDayVALAt(i);
            }
            if (showToday) {
                todayPoc[i] = engine.getTodayPOCAt(i);
                todayVah[i] = engine.getTodayVAHAt(i);
                todayVal[i] = engine.getTodayVALAt(i);
            }
        }
        return new Result(prevPoc, prevVah, prevVal, todayPoc, todayVah, todayVal);
    }

    public record Result(double[] prevPoc, double[] prevVah, double[] prevVal,
                          double[] todayPoc, double[] todayVah, double[] todayVal) {}
}
