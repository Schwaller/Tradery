package com.tradery.news;

import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NewsTest {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");

        boolean useAi = hasArg(args, "--ai");
        boolean useScheduler = hasArg(args, "--schedule");
        int intervalMins = getIntArg(args, "--interval", 5);

        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    CRYPTO NEWS FETCHER                           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  AI extraction:  " + pad(useAi ? "ON" : "OFF (--ai)", 47) + "║");
        System.out.println("║  Scheduler:      " + pad(useScheduler ? "ON (--schedule)" : "OFF", 47) + "║");
        if (useScheduler) {
            System.out.println("║  Interval:       " + pad(intervalMins + " minutes (--interval N)", 47) + "║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");

        // Setup components
        FetcherRegistry fetchers = new FetcherRegistry();
        fetchers.registerDefaults();

        TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
        ClaudeCliProcessor ai = useAi ? new ClaudeCliProcessor() : null;
        SqliteNewsStore store = new SqliteNewsStore(dataDir.resolve("news.db"));

        if (useAi && (ai == null || !ai.isAvailable())) {
            System.out.println("⚠ Claude CLI not available. AI extraction disabled.\n");
            ai = null;
        }

        if (useScheduler) {
            runScheduler(fetchers, topics, store, ai, intervalMins);
        } else {
            runOnce(fetchers, topics, store, ai);
        }
    }

    private static void runOnce(FetcherRegistry fetchers, TopicRegistry topics,
                                 SqliteNewsStore store, ClaudeCliProcessor ai) {
        try (store; var scheduler = new FetchScheduler(fetchers, topics, store, ai)) {
            scheduler.withAiEnabled(ai != null)
                     .withArticlesPerSource(ai != null ? 10 : 20);

            var result = scheduler.fetchAndProcess();

            System.out.println("\n" + "═".repeat(70));
            System.out.println("RESULTS");
            System.out.println("═".repeat(70));
            System.out.println("New articles:      " + result.newArticles());
            System.out.println("Already existed:   " + result.existingArticles());
            System.out.println("AI processed:      " + result.aiProcessed());
            System.out.println("Errors:            " + result.errors());
            System.out.println("Duration:          " + result.duration().toMillis() + "ms");
            System.out.println("Total in database: " + store.getArticleCount());

            System.out.println("\nTOPICS:");
            for (var tc : store.getTopicCounts()) {
                System.out.printf("  %-25s %d%n", tc.topic(), tc.count());
            }
        }
    }

    private static void runScheduler(FetcherRegistry fetchers, TopicRegistry topics,
                                      SqliteNewsStore store, ClaudeCliProcessor ai, int intervalMins) {
        var scheduler = new FetchScheduler(fetchers, topics, store, ai)
            .withInterval(Duration.ofMinutes(intervalMins))
            .withAiEnabled(ai != null)
            .withArticlesPerSource(ai != null ? 3 : 10)
            .onFetchComplete(result -> {
                System.out.printf("[%s] Fetch complete: +%d new, %d existing, %d AI | DB total: %d%n",
                    LocalDateTime.now().format(TIME_FMT),
                    result.newArticles(),
                    result.existingArticles(),
                    result.aiProcessed(),
                    store.getArticleCount());
            });

        scheduler.start();

        System.out.println("Scheduler started. Press Ctrl+C to stop.\n");
        System.out.println("Fetching every " + intervalMins + " minutes...\n");

        // Keep running until interrupted
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            scheduler.close();
            store.close();
            System.out.println("Done.");
        }));

        // Block main thread
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasArg(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) return true;
        }
        return false;
    }

    private static int getIntArg(String[] args, String flag, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}
