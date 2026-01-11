package com.tradery.indicators;

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
     * These are the actual meeting days (typically 2 consecutive days).
     */
    private static Set<LocalDate> getFomcMeetingDates(int year) {
        Set<LocalDate> dates = new HashSet<>();

        // FOMC Schedule - 8 meetings per year, typically Tue-Wed
        switch (year) {
            case 2024 -> {
                addMeetingDays(dates, 2024, 1, 30, 31);   // Jan 30-31
                addMeetingDays(dates, 2024, 3, 19, 20);   // Mar 19-20
                addMeetingDays(dates, 2024, 4, 30, 0);    // Apr 30 - May 1
                dates.add(LocalDate.of(2024, 5, 1));
                addMeetingDays(dates, 2024, 6, 11, 12);   // Jun 11-12
                addMeetingDays(dates, 2024, 7, 30, 31);   // Jul 30-31
                addMeetingDays(dates, 2024, 9, 17, 18);   // Sep 17-18
                addMeetingDays(dates, 2024, 11, 6, 7);    // Nov 6-7
                addMeetingDays(dates, 2024, 12, 17, 18);  // Dec 17-18
            }
            case 2025 -> {
                addMeetingDays(dates, 2025, 1, 28, 29);   // Jan 28-29
                addMeetingDays(dates, 2025, 3, 18, 19);   // Mar 18-19
                addMeetingDays(dates, 2025, 5, 6, 7);     // May 6-7
                addMeetingDays(dates, 2025, 6, 17, 18);   // Jun 17-18
                addMeetingDays(dates, 2025, 7, 29, 30);   // Jul 29-30
                addMeetingDays(dates, 2025, 9, 16, 17);   // Sep 16-17
                addMeetingDays(dates, 2025, 11, 4, 5);    // Nov 4-5
                addMeetingDays(dates, 2025, 12, 16, 17);  // Dec 16-17
            }
            case 2026 -> {
                addMeetingDays(dates, 2026, 1, 27, 28);   // Jan 27-28
                addMeetingDays(dates, 2026, 3, 17, 18);   // Mar 17-18
                addMeetingDays(dates, 2026, 5, 5, 6);     // May 5-6
                addMeetingDays(dates, 2026, 6, 16, 17);   // Jun 16-17
                addMeetingDays(dates, 2026, 7, 28, 29);   // Jul 28-29
                addMeetingDays(dates, 2026, 9, 15, 16);   // Sep 15-16
                addMeetingDays(dates, 2026, 11, 3, 4);    // Nov 3-4
                addMeetingDays(dates, 2026, 12, 15, 16);  // Dec 15-16
            }
            default -> {
                // For years outside known schedule, no dates returned
            }
        }

        return dates;
    }

    private static void addMeetingDays(Set<LocalDate> dates, int year, int month, int day1, int day2) {
        dates.add(LocalDate.of(year, month, day1));
        if (day2 > 0) {
            dates.add(LocalDate.of(year, month, day2));
        }
    }
}
