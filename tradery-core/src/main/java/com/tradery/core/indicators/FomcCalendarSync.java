package com.tradery.core.indicators;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syncs FOMC meeting dates from the Federal Reserve website.
 * Caches results locally and falls back to hardcoded dates if sync fails.
 */
public class FomcCalendarSync {

    private static final Logger log = LoggerFactory.getLogger(FomcCalendarSync.class);

    private static final String FED_URL = "https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm";
    private static final String CACHE_FILE = "fomc-dates.json";
    private static final long CACHE_MAX_AGE_DAYS = 30;

    private static final Path TRADERY_DIR = Path.of(System.getProperty("user.home"), ".tradery");
    private static final Path CACHE_PATH = TRADERY_DIR.resolve(CACHE_FILE);

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    // Cached dates: year -> set of meeting dates
    private static Map<Integer, Set<LocalDate>> cachedDates = null;
    private static long cacheLoadedAt = 0;
    private static long lastRefreshAttempt = 0;
    private static final long REFRESH_INTERVAL_MS = TimeUnit.DAYS.toMillis(1); // Check once per day

    /**
     * Get FOMC meeting dates for a given year.
     * Tries cache first, then fetches from Fed if needed, falls back to hardcoded.
     */
    public static Set<LocalDate> getDatesForYear(int year) {
        ensureCacheLoaded();

        if (cachedDates != null && cachedDates.containsKey(year)) {
            return cachedDates.get(year);
        }

        // Fall back to hardcoded
        return getHardcodedDates(year);
    }

    /**
     * Force a refresh from the Fed website.
     */
    public static void refresh() {
        try {
            Map<Integer, Set<LocalDate>> fetched = fetchFromFed();
            if (fetched != null && !fetched.isEmpty()) {
                cachedDates = fetched;
                cacheLoadedAt = System.currentTimeMillis();
                saveCache(fetched);
                log.info("FOMC calendar refreshed from Fed website: {} years", fetched.size());
            }
        } catch (Exception e) {
            log.warn("Failed to refresh FOMC calendar: {}", e.getMessage());
        }
    }

    private static synchronized void ensureCacheLoaded() {
        long now = System.currentTimeMillis();

        if (cachedDates != null) {
            // Check if we should try a periodic refresh (once per day)
            if (now - lastRefreshAttempt > REFRESH_INTERVAL_MS) {
                lastRefreshAttempt = now;
                // Trigger async refresh in background
                Thread refreshThread = new Thread(FomcCalendarSync::refresh);
                refreshThread.setDaemon(true);
                refreshThread.setName("FOMC-Calendar-Refresh");
                refreshThread.start();
            }
            return;
        }

        // Try to load from disk cache
        if (loadCache()) {
            log.debug("Loaded FOMC calendar from cache");
            return;
        }

        // Try to fetch from Fed
        try {
            Map<Integer, Set<LocalDate>> fetched = fetchFromFed();
            if (fetched != null && !fetched.isEmpty()) {
                cachedDates = fetched;
                cacheLoadedAt = System.currentTimeMillis();
                saveCache(fetched);
                log.info("Fetched FOMC calendar from Fed website: {} years", fetched.size());
                return;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch FOMC calendar: {}", e.getMessage());
        }

        // Fall back to hardcoded dates
        cachedDates = new HashMap<>();
        cacheLoadedAt = System.currentTimeMillis();
        log.info("Using hardcoded FOMC dates as fallback");
    }

    private static boolean loadCache() {
        try {
            if (!Files.exists(CACHE_PATH)) {
                return false;
            }

            // Check cache age
            long lastModified = Files.getLastModifiedTime(CACHE_PATH).toMillis();
            long ageMs = System.currentTimeMillis() - lastModified;

            CacheData data = mapper.readValue(CACHE_PATH.toFile(), CacheData.class);
            if (data.dates != null && !data.dates.isEmpty()) {
                cachedDates = convertFromCacheFormat(data.dates);
                cacheLoadedAt = lastModified;

                // If cache is old, trigger background refresh
                if (ageMs > TimeUnit.DAYS.toMillis(CACHE_MAX_AGE_DAYS)) {
                    new Thread(FomcCalendarSync::refresh).start();
                }
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to load FOMC cache: {}", e.getMessage());
        }
        return false;
    }

    private static void saveCache(Map<Integer, Set<LocalDate>> dates) {
        try {
            Files.createDirectories(TRADERY_DIR);
            CacheData data = new CacheData();
            data.fetchedAt = System.currentTimeMillis();
            data.source = FED_URL;
            data.dates = convertToCacheFormat(dates);
            mapper.writerWithDefaultPrettyPrinter().writeValue(CACHE_PATH.toFile(), data);
        } catch (Exception e) {
            log.warn("Failed to save FOMC cache: {}", e.getMessage());
        }
    }

    private static Map<Integer, Set<LocalDate>> fetchFromFed() throws IOException {
        Request request = new Request.Builder()
                .url(FED_URL)
                .header("User-Agent", "Plaiiin/1.0 (Trading Strategy Backtester)")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }

            String html = response.body().string();
            return parseHtml(html);
        }
    }

    /**
     * Parse FOMC dates from Fed website HTML.
     * The page has a structured format with year headers and date rows.
     */
    private static Map<Integer, Set<LocalDate>> parseHtml(String html) {
        Map<Integer, Set<LocalDate>> result = new HashMap<>();

        // Pattern to find year sections (e.g., "2025" or "2026")
        // The Fed page uses div/panel structure with years
        Pattern yearPattern = Pattern.compile("<div[^>]*class=\"[^\"]*panel[^\"]*\"[^>]*>[\\s\\S]*?<div[^>]*class=\"[^\"]*panel-heading[^\"]*\"[^>]*>\\s*(\\d{4})\\s*</div>([\\s\\S]*?)</div>\\s*</div>");

        // Simpler approach: find year headings and extract dates after them
        // The Fed page has: <div class="panel-heading">2025</div> followed by meeting dates
        Pattern simpleYearPattern = Pattern.compile("panel-heading[^>]*>\\s*(\\d{4})\\s*<");
        Pattern datePattern = Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})(?:[-/](\\d{1,2}))?");

        // Split HTML by year sections
        String[] yearSections = html.split("(?=panel-heading)");

        int currentYear = 0;
        for (String section : yearSections) {
            // Check if this section starts with a year
            Matcher yearMatcher = simpleYearPattern.matcher(section);
            if (yearMatcher.find()) {
                currentYear = Integer.parseInt(yearMatcher.group(1));
                if (currentYear < 2020 || currentYear > 2030) {
                    currentYear = 0; // Invalid year
                    continue;
                }
            }

            if (currentYear == 0) continue;

            // Find all date patterns in this section
            Matcher dateMatcher = datePattern.matcher(section);
            Set<LocalDate> dates = result.computeIfAbsent(currentYear, k -> new HashSet<>());

            while (dateMatcher.find()) {
                try {
                    Month month = Month.valueOf(dateMatcher.group(1).toUpperCase());
                    int day1 = Integer.parseInt(dateMatcher.group(2));
                    dates.add(LocalDate.of(currentYear, month, day1));

                    // If there's a second day (e.g., "28-29")
                    if (dateMatcher.group(3) != null) {
                        int day2 = Integer.parseInt(dateMatcher.group(3));
                        // Handle month boundary (e.g., April 30 - May 1)
                        if (day2 < day1) {
                            // Second day is in next month
                            Month nextMonth = month.plus(1);
                            int nextYear = month == Month.DECEMBER ? currentYear + 1 : currentYear;
                            dates.add(LocalDate.of(nextYear, nextMonth, day2));
                        } else {
                            dates.add(LocalDate.of(currentYear, month, day2));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse date: {}", e.getMessage());
                }
            }
        }

        return result;
    }

    // ========== Hardcoded Fallback ==========

    private static Set<LocalDate> getHardcodedDates(int year) {
        Set<LocalDate> dates = new HashSet<>();

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
                addMeetingDays(dates, 2025, 10, 28, 29);  // Oct 28-29
                addMeetingDays(dates, 2025, 12, 9, 10);   // Dec 9-10
            }
            case 2026 -> {
                addMeetingDays(dates, 2026, 1, 27, 28);   // Jan 27-28
                addMeetingDays(dates, 2026, 3, 17, 18);   // Mar 17-18
                addMeetingDays(dates, 2026, 4, 28, 29);   // Apr 28-29
                addMeetingDays(dates, 2026, 6, 16, 17);   // Jun 16-17
                addMeetingDays(dates, 2026, 7, 28, 29);   // Jul 28-29
                addMeetingDays(dates, 2026, 9, 15, 16);   // Sep 15-16
                addMeetingDays(dates, 2026, 10, 27, 28);  // Oct 27-28
                addMeetingDays(dates, 2026, 12, 8, 9);    // Dec 8-9
            }
            case 2027 -> {
                addMeetingDays(dates, 2027, 1, 26, 27);   // Jan 26-27
                addMeetingDays(dates, 2027, 3, 16, 17);   // Mar 16-17
                addMeetingDays(dates, 2027, 4, 27, 28);   // Apr 27-28
                addMeetingDays(dates, 2027, 6, 8, 9);     // Jun 8-9
                addMeetingDays(dates, 2027, 7, 27, 28);   // Jul 27-28
                addMeetingDays(dates, 2027, 9, 14, 15);   // Sep 14-15
                addMeetingDays(dates, 2027, 10, 26, 27);  // Oct 26-27
                addMeetingDays(dates, 2027, 12, 7, 8);    // Dec 7-8
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

    // ========== Cache Format ==========

    private static Map<Integer, List<String>> convertToCacheFormat(Map<Integer, Set<LocalDate>> dates) {
        Map<Integer, List<String>> result = new TreeMap<>();
        for (var entry : dates.entrySet()) {
            List<String> dateStrings = entry.getValue().stream()
                    .sorted()
                    .map(LocalDate::toString)
                    .toList();
            result.put(entry.getKey(), dateStrings);
        }
        return result;
    }

    private static Map<Integer, Set<LocalDate>> convertFromCacheFormat(Map<Integer, List<String>> cached) {
        Map<Integer, Set<LocalDate>> result = new HashMap<>();
        for (var entry : cached.entrySet()) {
            Set<LocalDate> dates = new HashSet<>();
            for (String s : entry.getValue()) {
                dates.add(LocalDate.parse(s));
            }
            result.put(entry.getKey(), dates);
        }
        return result;
    }

    static class CacheData {
        public long fetchedAt;
        public String source;
        public Map<Integer, List<String>> dates;
    }
}
