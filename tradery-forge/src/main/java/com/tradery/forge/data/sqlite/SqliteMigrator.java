package com.tradery.forge.data.sqlite;

import com.tradery.core.model.*;
import com.tradery.forge.data.DataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Migrates CSV data files to SQLite databases.
 * One database per symbol (e.g., BTCUSDT.db).
 *
 * Migration is incremental - only migrates data that hasn't been migrated yet.
 */
public class SqliteMigrator {

    private static final Logger log = LoggerFactory.getLogger(SqliteMigrator.class);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SqliteDataStore dataStore;
    private final File dataDir;
    private Consumer<MigrationProgress> progressCallback;

    public SqliteMigrator(SqliteDataStore dataStore) {
        this.dataStore = dataStore;
        this.dataDir = DataConfig.getInstance().getDataDir();
    }

    /**
     * Set a callback for migration progress updates.
     */
    public void setProgressCallback(Consumer<MigrationProgress> callback) {
        this.progressCallback = callback;
    }

    /**
     * Discover all symbols that have CSV data.
     */
    public List<String> discoverSymbols() {
        List<String> symbols = new ArrayList<>();

        File[] dirs = dataDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return symbols;
        }

        for (File dir : dirs) {
            String name = dir.getName();
            // Skip special directories
            if (name.equals("funding") || name.equals("openinterest") || name.equals("premium")) {
                continue;
            }
            // Check if it looks like a symbol directory (has timeframe subdirs or aggTrades)
            File[] contents = dir.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    if (f.isDirectory() && (isTimeframe(f.getName()) || f.getName().equals("aggTrades") ||
                        f.getName().equals("funding") || f.getName().equals("openinterest"))) {
                        symbols.add(name);
                        break;
                    }
                }
            }
        }

        return symbols;
    }

    private boolean isTimeframe(String name) {
        return name.equals("1m") || name.equals("5m") || name.equals("15m") ||
               name.equals("30m") || name.equals("1h") || name.equals("4h") ||
               name.equals("1d") || name.equals("1w");
    }

    /**
     * Migrate all data for a single symbol.
     */
    public MigrationResult migrateSymbol(String symbol) {
        MigrationResult result = new MigrationResult(symbol);
        long startTime = System.currentTimeMillis();

        try {
            reportProgress(symbol, "Starting migration...", 0);

            // Migrate candles
            result.candleCount = migrateCandles(symbol);

            // Migrate funding rates
            result.fundingCount = migrateFundingRates(symbol);

            // Migrate open interest
            result.oiCount = migrateOpenInterest(symbol);

            // Migrate premium index
            result.premiumCount = migratePremiumIndex(symbol);

            // Migrate aggregated trades (largest, do last)
            result.aggTradeCount = migrateAggTrades(symbol);

            result.success = true;
            result.durationMs = System.currentTimeMillis() - startTime;

            reportProgress(symbol, "Migration complete!", 100);
            log.info("Migrated {} - {} candles, {} aggTrades, {} funding, {} OI, {} premium in {}ms",
                symbol, result.candleCount, result.aggTradeCount, result.fundingCount,
                result.oiCount, result.premiumCount, result.durationMs);

        } catch (Exception e) {
            result.success = false;
            result.error = e.getMessage();
            result.durationMs = System.currentTimeMillis() - startTime;
            log.error("Migration failed for {}: {}", symbol, e.getMessage(), e);
        }

        return result;
    }

    /**
     * Migrate all symbols.
     */
    public List<MigrationResult> migrateAll() {
        List<String> symbols = discoverSymbols();
        List<MigrationResult> results = new ArrayList<>();

        log.info("Starting migration for {} symbols", symbols.size());

        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            log.info("Migrating {}/{}: {}", i + 1, symbols.size(), symbol);
            results.add(migrateSymbol(symbol));
        }

        return results;
    }

    // ========== Candle Migration ==========

    private int migrateCandles(String symbol) throws IOException, SQLException {
        int totalCount = 0;
        File symbolDir = new File(dataDir, symbol);

        if (!symbolDir.exists()) {
            return 0;
        }

        // Find all timeframe directories
        File[] timeframeDirs = symbolDir.listFiles(f -> f.isDirectory() && isTimeframe(f.getName()));
        if (timeframeDirs == null) {
            return 0;
        }

        for (File tfDir : timeframeDirs) {
            String timeframe = tfDir.getName();
            reportProgress(symbol, "Migrating " + timeframe + " candles...", 25);

            // Find all CSV files (both complete and partial)
            File[] csvFiles = tfDir.listFiles(f -> f.getName().endsWith(".csv"));
            if (csvFiles == null) {
                continue;
            }

            List<Candle> allCandles = new ArrayList<>();

            for (File csvFile : csvFiles) {
                List<Candle> candles = loadCandleCsv(csvFile);
                allCandles.addAll(candles);
            }

            if (!allCandles.isEmpty()) {
                // Sort and deduplicate
                Map<Long, Candle> candleMap = new LinkedHashMap<>();
                for (Candle c : allCandles) {
                    candleMap.put(c.timestamp(), c);
                }
                List<Candle> deduped = new ArrayList<>(candleMap.values());
                deduped.sort(Comparator.comparingLong(Candle::timestamp));

                // Insert into SQLite
                dataStore.saveCandles(symbol, timeframe, deduped);

                // Record coverage
                if (!deduped.isEmpty()) {
                    long start = deduped.get(0).timestamp();
                    long end = deduped.get(deduped.size() - 1).timestamp();
                    dataStore.addCoverage(symbol, "candles", timeframe, start, end, true);
                }

                totalCount += deduped.size();
                log.debug("Migrated {} {} candles for {}", deduped.size(), timeframe, symbol);
            }
        }

        return totalCount;
    }

    private List<Candle> loadCandleCsv(File file) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("timestamp")) continue;
                }
                if (line.isBlank()) continue;

                try {
                    candles.add(Candle.fromCsv(line));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        return candles;
    }

    // ========== Funding Rate Migration ==========

    private int migrateFundingRates(String symbol) throws IOException, SQLException {
        File fundingDir = new File(dataDir, symbol + "/funding");

        if (!fundingDir.exists()) {
            return 0;
        }

        reportProgress(symbol, "Migrating funding rates...", 40);

        File[] csvFiles = fundingDir.listFiles(f -> f.getName().endsWith(".csv"));
        if (csvFiles == null) {
            return 0;
        }

        List<FundingRate> allRates = new ArrayList<>();

        for (File csvFile : csvFiles) {
            List<FundingRate> rates = loadFundingCsv(csvFile);
            allRates.addAll(rates);
        }

        if (allRates.isEmpty()) {
            return 0;
        }

        // Sort and deduplicate
        Map<Long, FundingRate> rateMap = new LinkedHashMap<>();
        for (FundingRate fr : allRates) {
            rateMap.put(fr.fundingTime(), fr);
        }
        List<FundingRate> deduped = new ArrayList<>(rateMap.values());
        deduped.sort(Comparator.comparingLong(FundingRate::fundingTime));

        // Insert into SQLite
        dataStore.saveFundingRates(symbol, deduped);

        // Record coverage
        if (!deduped.isEmpty()) {
            long start = deduped.get(0).fundingTime();
            long end = deduped.get(deduped.size() - 1).fundingTime();
            dataStore.addCoverage(symbol, "funding_rates", "", start, end, true);
        }

        log.debug("Migrated {} funding rates for {}", deduped.size(), symbol);
        return deduped.size();
    }

    private List<FundingRate> loadFundingCsv(File file) throws IOException {
        List<FundingRate> rates = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("symbol")) continue;
                }
                if (line.isBlank()) continue;

                try {
                    rates.add(FundingRate.fromCsv(line));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        return rates;
    }

    // ========== Open Interest Migration ==========

    private int migrateOpenInterest(String symbol) throws IOException, SQLException {
        File oiDir = new File(dataDir, symbol + "/openinterest");

        if (!oiDir.exists()) {
            return 0;
        }

        reportProgress(symbol, "Migrating open interest...", 50);

        File[] csvFiles = oiDir.listFiles(f -> f.getName().endsWith(".csv"));
        if (csvFiles == null) {
            return 0;
        }

        List<OpenInterest> allOi = new ArrayList<>();

        for (File csvFile : csvFiles) {
            List<OpenInterest> oi = loadOpenInterestCsv(csvFile);
            allOi.addAll(oi);
        }

        if (allOi.isEmpty()) {
            return 0;
        }

        // Sort and deduplicate
        Map<Long, OpenInterest> oiMap = new LinkedHashMap<>();
        for (OpenInterest oi : allOi) {
            oiMap.put(oi.timestamp(), oi);
        }
        List<OpenInterest> deduped = new ArrayList<>(oiMap.values());
        deduped.sort(Comparator.comparingLong(OpenInterest::timestamp));

        // Insert into SQLite
        dataStore.saveOpenInterest(symbol, deduped);

        // Record coverage
        if (!deduped.isEmpty()) {
            long start = deduped.get(0).timestamp();
            long end = deduped.get(deduped.size() - 1).timestamp();
            dataStore.addCoverage(symbol, "open_interest", "", start, end, true);
        }

        log.debug("Migrated {} open interest records for {}", deduped.size(), symbol);
        return deduped.size();
    }

    private List<OpenInterest> loadOpenInterestCsv(File file) throws IOException {
        List<OpenInterest> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("symbol")) continue;
                }
                if (line.isBlank()) continue;

                try {
                    records.add(OpenInterest.fromCsv(line));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        return records;
    }

    // ========== Premium Index Migration ==========

    private int migratePremiumIndex(String symbol) throws IOException, SQLException {
        File premiumDir = new File(dataDir, symbol + "/premium");

        if (!premiumDir.exists()) {
            return 0;
        }

        reportProgress(symbol, "Migrating premium index...", 60);

        int totalCount = 0;

        // Premium is organized by interval (e.g., 1h, 5m)
        File[] intervalDirs = premiumDir.listFiles(File::isDirectory);
        if (intervalDirs == null) {
            return 0;
        }

        for (File intervalDir : intervalDirs) {
            String interval = intervalDir.getName();

            File[] csvFiles = intervalDir.listFiles(f -> f.getName().endsWith(".csv"));
            if (csvFiles == null) {
                continue;
            }

            List<PremiumIndex> allPremium = new ArrayList<>();

            for (File csvFile : csvFiles) {
                List<PremiumIndex> premium = loadPremiumIndexCsv(csvFile);
                allPremium.addAll(premium);
            }

            if (allPremium.isEmpty()) {
                continue;
            }

            // Sort and deduplicate
            Map<Long, PremiumIndex> premiumMap = new LinkedHashMap<>();
            for (PremiumIndex pi : allPremium) {
                premiumMap.put(pi.openTime(), pi);
            }
            List<PremiumIndex> deduped = new ArrayList<>(premiumMap.values());
            deduped.sort(Comparator.comparingLong(PremiumIndex::openTime));

            // Insert into SQLite
            dataStore.savePremiumIndex(symbol, interval, deduped);

            // Record coverage
            if (!deduped.isEmpty()) {
                long start = deduped.get(0).openTime();
                long end = deduped.get(deduped.size() - 1).openTime();
                dataStore.addCoverage(symbol, "premium_index", interval, start, end, true);
            }

            totalCount += deduped.size();
        }

        log.debug("Migrated {} premium index records for {}", totalCount, symbol);
        return totalCount;
    }

    private List<PremiumIndex> loadPremiumIndexCsv(File file) throws IOException {
        List<PremiumIndex> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("openTime")) continue;
                }
                if (line.isBlank()) continue;

                try {
                    records.add(PremiumIndex.fromCsv(line));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        return records;
    }

    // ========== Aggregated Trades Migration ==========

    private long migrateAggTrades(String symbol) throws IOException, SQLException {
        File aggTradesDir = new File(dataDir, symbol + "/aggTrades");

        if (!aggTradesDir.exists()) {
            return 0;
        }

        reportProgress(symbol, "Migrating aggregated trades...", 70);

        long totalCount = 0;

        // AggTrades are organized by date/hour
        File[] dateDirs = aggTradesDir.listFiles(File::isDirectory);
        if (dateDirs == null) {
            return 0;
        }

        Arrays.sort(dateDirs, Comparator.comparing(File::getName));

        for (int i = 0; i < dateDirs.length; i++) {
            File dateDir = dateDirs[i];

            // Progress for each day
            int progress = 70 + (int)((i * 25.0) / dateDirs.length);
            reportProgress(symbol, "Migrating aggTrades " + dateDir.getName() + "...", progress);

            File[] csvFiles = dateDir.listFiles(f -> f.getName().endsWith(".csv"));
            if (csvFiles == null) {
                continue;
            }

            for (File csvFile : csvFiles) {
                List<AggTrade> trades = loadAggTradesCsv(csvFile);

                if (!trades.isEmpty()) {
                    // Insert directly - primary key handles deduplication
                    dataStore.saveAggTrades(symbol, trades);
                    totalCount += trades.size();
                }
            }
        }

        // Record coverage based on what we have
        if (totalCount > 0) {
            try {
                long[] range = dataStore.forSymbol(symbol).aggTrades().getTimeRange();
                if (range != null) {
                    dataStore.addCoverage(symbol, "agg_trades", "", range[0], range[1], true);
                }
            } catch (SQLException e) {
                log.warn("Failed to record aggTrades coverage: {}", e.getMessage());
            }
        }

        log.debug("Migrated {} aggregated trades for {}", formatCount(totalCount), symbol);
        return totalCount;
    }

    private List<AggTrade> loadAggTradesCsv(File file) throws IOException {
        List<AggTrade> trades = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith("aggTradeId")) continue;
                }
                if (line.isBlank()) continue;

                try {
                    trades.add(AggTrade.fromCsv(line));
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        return trades;
    }

    // ========== Utility Methods ==========

    private void reportProgress(String symbol, String message, int percent) {
        if (progressCallback != null) {
            progressCallback.accept(new MigrationProgress(symbol, message, percent));
        }
    }

    private String formatCount(long count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }

    /**
     * Migration progress update.
     */
    public record MigrationProgress(
        String symbol,
        String message,
        int percentComplete
    ) {}

    /**
     * Migration result for a single symbol.
     */
    public static class MigrationResult {
        public final String symbol;
        public boolean success;
        public String error;
        public int candleCount;
        public long aggTradeCount;
        public int fundingCount;
        public int oiCount;
        public int premiumCount;
        public long durationMs;

        public MigrationResult(String symbol) {
            this.symbol = symbol;
        }

        public long totalRecords() {
            return candleCount + aggTradeCount + fundingCount + oiCount + premiumCount;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("%s: %d records in %dms", symbol, totalRecords(), durationMs);
            } else {
                return String.format("%s: FAILED - %s", symbol, error);
            }
        }
    }
}
