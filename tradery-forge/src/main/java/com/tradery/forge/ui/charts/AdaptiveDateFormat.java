package com.tradery.forge.ui.charts;

import org.jfree.chart.axis.DateAxis;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Smart date formatter that shows contextual labels based on time boundaries.
 *
 * - At/near midnight on Jan 1: show year (e.g., "2025")
 * - At/near midnight: show date (e.g., "Jan 19")
 * - Otherwise: show time (e.g., "14:00")
 *
 * This mimics how professional trading platforms display time axes.
 */
public class AdaptiveDateFormat extends DateFormat {

    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);

    private final DateAxis dateAxis;
    private final Calendar cal;

    // Formatters
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat yearFormat;

    public AdaptiveDateFormat(DateAxis dateAxis) {
        this.dateAxis = dateAxis;

        TimeZone tz = TimeZone.getDefault();
        cal = Calendar.getInstance(tz);

        timeFormat = new SimpleDateFormat("HH:mm");
        timeFormat.setTimeZone(tz);

        dateFormat = new SimpleDateFormat("MMM d");
        dateFormat.setTimeZone(tz);

        yearFormat = new SimpleDateFormat("yyyy");
        yearFormat.setTimeZone(tz);

        this.calendar = cal;
        this.numberFormat = timeFormat.getNumberFormat();
    }

    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        cal.setTime(date);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH);

        // Get visible range to determine appropriate granularity
        long rangeMs = getVisibleRangeMs();

        // For very long ranges (> 1 year), show month/year at month boundaries
        if (rangeMs > 365 * DAY_MS) {
            if (month == Calendar.JANUARY && dayOfMonth == 1) {
                return yearFormat.format(date, toAppendTo, fieldPosition);
            }
            // Show month at start of each month
            if (dayOfMonth == 1 || dayOfMonth <= 2) {
                return dateFormat.format(date, toAppendTo, fieldPosition);
            }
            return dateFormat.format(date, toAppendTo, fieldPosition);
        }

        // For medium ranges (> 3 days), show dates
        if (rangeMs > 3 * DAY_MS) {
            // At year boundary (Jan 1), show year
            if (month == Calendar.JANUARY && dayOfMonth == 1) {
                return yearFormat.format(date, toAppendTo, fieldPosition);
            }
            return dateFormat.format(date, toAppendTo, fieldPosition);
        }

        // For shorter ranges: show time, but show date at day boundaries
        // Check if this is near midnight (within first few hours of day)
        boolean isNearMidnight = (hour == 0 && minute == 0) ||
                                  (hour < 4 && isFirstTickOfDay(date));

        if (isNearMidnight) {
            // At year boundary (Jan 1), show year
            if (month == Calendar.JANUARY && dayOfMonth == 1) {
                return yearFormat.format(date, toAppendTo, fieldPosition);
            }
            // Day boundary - show date
            return dateFormat.format(date, toAppendTo, fieldPosition);
        }

        // Normal tick - show time
        return timeFormat.format(date, toAppendTo, fieldPosition);
    }

    /**
     * Check if this tick is likely the first tick of a new day based on
     * the visible tick spacing.
     */
    private boolean isFirstTickOfDay(Date date) {
        cal.setTime(date);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // Estimate tick interval from visible range
        long rangeMs = getVisibleRangeMs();
        // Assume roughly 6-10 ticks visible
        long estimatedTickInterval = rangeMs / 8;

        // If tick interval is >= 4 hours, any early morning tick is "first of day"
        if (estimatedTickInterval >= TimeUnit.HOURS.toMillis(4)) {
            return hour < 6;
        }

        // For smaller intervals, be more precise
        if (estimatedTickInterval >= TimeUnit.HOURS.toMillis(1)) {
            return hour == 0;
        }

        // For minute-level ticks, only exact midnight
        return hour == 0 && minute == 0;
    }

    private long getVisibleRangeMs() {
        if (dateAxis == null) {
            return 7 * DAY_MS;
        }
        try {
            double lower = dateAxis.getLowerBound();
            double upper = dateAxis.getUpperBound();
            return (long) (upper - lower);
        } catch (Exception e) {
            return 7 * DAY_MS;
        }
    }

    @Override
    public Date parse(String source, ParsePosition pos) {
        return timeFormat.parse(source, pos);
    }
}
