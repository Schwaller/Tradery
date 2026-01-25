package com.tradery.core.indicators;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Calendar-based indicators: Moon phases, US holidays, FOMC meetings.
 * Provides static utility methods for calendar calculations.
 */
public class CalendarIndicators {

    // Known new moon reference: January 6, 2000 at 18:14 UTC
    private static final long NEW_MOON_REFERENCE = 947182440000L;
    // Synodic month (moon cycle) in milliseconds: 29.53059 days
    private static final double SYNODIC_MONTH_MS = 29.53059 * 24 * 60 * 60 * 1000;

    // Caches
    private static final Map<Integer, Set<LocalDate>> usHolidayCache = new HashMap<>();
    private static final Map<Integer, Set<LocalDate>> fomcMeetingCache = new HashMap<>();

    /**
     * Get moon phase for a given timestamp.
     * Returns a value from 0 to 1 where:
     * - 0.0 = new moon
     * - 0.25 = first quarter
     * - 0.5 = full moon
     * - 0.75 = last quarter
     * - 1.0 = new moon (wraps around)
     */
    public static double getMoonPhase(long timestamp) {
        if (timestamp == 0) return Double.NaN;

        // Calculate how many synodic months since reference new moon
        double timeSinceRef = timestamp - NEW_MOON_REFERENCE;
        double phase = (timeSinceRef % SYNODIC_MONTH_MS) / SYNODIC_MONTH_MS;

        // Handle negative timestamps (before reference)
        if (phase < 0) phase += 1.0;

        return phase;
    }

    /**
     * Check if timestamp is on a US federal bank holiday.
     */
    public static boolean isUSHoliday(long timestamp) {
        if (timestamp == 0) return false;

        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        int year = date.getYear();

        // Get or compute holidays for this year
        Set<LocalDate> holidays = usHolidayCache.computeIfAbsent(year, CalendarIndicators::computeUSHolidays);

        return holidays.contains(date);
    }

    /**
     * Check if timestamp is on an FOMC meeting day.
     */
    public static boolean isFomcMeeting(long timestamp) {
        if (timestamp == 0) return false;

        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate();
        int year = date.getYear();

        Set<LocalDate> meetings = fomcMeetingCache.computeIfAbsent(year, CalendarIndicators::getFomcMeetingDates);
        return meetings.contains(date);
    }

    /**
     * Check if any FOMC meeting falls within a date range (inclusive).
     * Useful for weekly/monthly candles where we need to check if any day in the period has a meeting.
     */
    public static boolean hasFomcMeetingInRange(long startTimestamp, long endTimestamp) {
        if (startTimestamp == 0 || endTimestamp == 0) return false;

        LocalDate startDate = Instant.ofEpochMilli(startTimestamp).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = Instant.ofEpochMilli(endTimestamp).atZone(ZoneOffset.UTC).toLocalDate();

        // Check each day in the range
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            int year = current.getYear();
            Set<LocalDate> meetings = fomcMeetingCache.computeIfAbsent(year, CalendarIndicators::getFomcMeetingDates);
            if (meetings.contains(current)) {
                return true;
            }
            current = current.plusDays(1);
        }
        return false;
    }

    /**
     * Compute all US federal holidays for a given year.
     * These are days when the Federal Reserve is closed.
     */
    private static Set<LocalDate> computeUSHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        // New Year's Day - January 1 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 1, 1)));

        // MLK Day - 3rd Monday in January
        holidays.add(nthDayOfWeek(year, 1, DayOfWeek.MONDAY, 3));

        // Presidents Day - 3rd Monday in February
        holidays.add(nthDayOfWeek(year, 2, DayOfWeek.MONDAY, 3));

        // Memorial Day - Last Monday in May
        holidays.add(LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));

        // Juneteenth - June 19 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 6, 19)));

        // Independence Day - July 4 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 7, 4)));

        // Labor Day - 1st Monday in September
        holidays.add(nthDayOfWeek(year, 9, DayOfWeek.MONDAY, 1));

        // Columbus Day - 2nd Monday in October
        holidays.add(nthDayOfWeek(year, 10, DayOfWeek.MONDAY, 2));

        // Veterans Day - November 11 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 11, 11)));

        // Thanksgiving - 4th Thursday in November
        holidays.add(nthDayOfWeek(year, 11, DayOfWeek.THURSDAY, 4));

        // Christmas Day - December 25 (observed on nearest weekday if weekend)
        holidays.add(observedDate(LocalDate.of(year, 12, 25)));

        return holidays;
    }

    /**
     * Get the nth occurrence of a day of week in a month.
     */
    private static LocalDate nthDayOfWeek(int year, int month, DayOfWeek dayOfWeek, int n) {
        LocalDate first = LocalDate.of(year, month, 1).with(TemporalAdjusters.firstInMonth(dayOfWeek));
        return first.plusWeeks(n - 1);
    }

    /**
     * Get the observed date for a holiday (Friday if Saturday, Monday if Sunday).
     */
    private static LocalDate observedDate(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            return date.minusDays(1); // Friday
        } else if (dow == DayOfWeek.SUNDAY) {
            return date.plusDays(1); // Monday
        }
        return date;
    }

    /**
     * Get FOMC meeting dates for a given year.
     * Delegates to FomcCalendarSync which fetches from Fed website and caches.
     */
    private static Set<LocalDate> getFomcMeetingDates(int year) {
        return FomcCalendarSync.getDatesForYear(year);
    }
}
