package com.tradery.dataservice.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class for CSV file operations.
 * Eliminates duplicate CSV parsing/writing code across Store classes.
 */
public final class CsvUtils {

    private static final Logger log = LoggerFactory.getLogger(CsvUtils.class);

    private CsvUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Read a CSV file and parse each line using the provided parser function.
     *
     * @param file          The CSV file to read
     * @param headerPrefix  The prefix that identifies a header line (e.g., "timestamp", "symbol")
     * @param parser        Function to parse each CSV line into an object
     * @param <T>           The type of objects to parse
     * @return List of parsed objects
     * @throws IOException If the file cannot be read
     */
    public static <T> List<T> readCsv(File file, String headerPrefix, Function<String, T> parser)
            throws IOException {
        List<T> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line
                if (firstLine) {
                    firstLine = false;
                    if (line.startsWith(headerPrefix)) {
                        continue;
                    }
                }

                try {
                    T item = parser.apply(line);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line: {}", line);
                }
            }
        }

        return items;
    }

    /**
     * Write items to a CSV file with a header.
     *
     * @param file      The CSV file to write
     * @param header    The CSV header line
     * @param items     The items to write
     * @param formatter Function to convert each item to a CSV line
     * @param <T>       The type of objects to write
     * @throws IOException If the file cannot be written
     */
    public static <T> void writeCsv(File file, String header, List<T> items,
                                     Function<T, String> formatter) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(header);
            for (T item : items) {
                writer.println(formatter.apply(item));
            }
        }
        log.debug("Saved {} items to {}", items.size(), file.getPath());
    }

    /**
     * Append items to an existing CSV file (no header written).
     *
     * @param file      The CSV file to append to
     * @param items     The items to append
     * @param formatter Function to convert each item to a CSV line
     * @param <T>       The type of objects to write
     * @throws IOException If the file cannot be written
     */
    public static <T> void appendCsv(File file, List<T> items,
                                      Function<T, String> formatter) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            for (T item : items) {
                writer.println(formatter.apply(item));
            }
        }
        log.debug("Appended {} items to {}", items.size(), file.getPath());
    }

    /**
     * Read a CSV file into a map, using a key extractor.
     *
     * @param file          The CSV file to read
     * @param headerPrefix  The prefix that identifies a header line
     * @param parser        Function to parse each CSV line into an object
     * @param keyExtractor  Function to extract the key from each object
     * @param <K>           The type of the map key
     * @param <T>           The type of objects to parse
     * @return Map of key to parsed object
     * @throws IOException If the file cannot be read
     */
    public static <K, T> java.util.Map<K, T> readCsvToMap(
            File file,
            String headerPrefix,
            Function<String, T> parser,
            Function<T, K> keyExtractor) throws IOException {

        java.util.Map<K, T> map = new java.util.LinkedHashMap<>();
        List<T> items = readCsv(file, headerPrefix, parser);

        for (T item : items) {
            K key = keyExtractor.apply(item);
            map.put(key, item);
        }

        return map;
    }
}
