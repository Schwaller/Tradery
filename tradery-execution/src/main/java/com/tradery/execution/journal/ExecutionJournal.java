package com.tradery.execution.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Append-only JSONL daily execution log.
 * Files: ~/.tradery/trading/journal/2026-02-08.jsonl
 */
public class ExecutionJournal {

    private static final Logger log = LoggerFactory.getLogger(ExecutionJournal.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path journalDir;
    private final ObjectMapper mapper;
    private final List<Consumer<ExecutionEvent>> listeners = new CopyOnWriteArrayList<>();

    private volatile LocalDate currentDate;
    private volatile BufferedWriter currentWriter;

    public ExecutionJournal(Path baseDir) {
        this.journalDir = baseDir.resolve("trading").resolve("journal");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(journalDir);
        } catch (IOException e) {
            log.error("Failed to create journal directory: {}", journalDir, e);
        }
    }

    /**
     * Log an execution event.
     */
    public synchronized void log(ExecutionEvent event) {
        try {
            ensureWriter();
            String json = mapper.writeValueAsString(event);
            currentWriter.write(json);
            currentWriter.newLine();
            currentWriter.flush();
        } catch (IOException e) {
            log.error("Failed to write journal event: {}", e.getMessage());
        }

        // Notify listeners
        listeners.forEach(l -> {
            try { l.accept(event); } catch (Exception e) { log.warn("Journal listener error", e); }
        });
    }

    /**
     * Subscribe to journal events (for UI display).
     */
    public void subscribe(Consumer<ExecutionEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Read today's journal entries.
     */
    public List<ExecutionEvent> readToday() {
        return readDate(LocalDate.now());
    }

    /**
     * Read journal entries for a specific date.
     */
    public List<ExecutionEvent> readDate(LocalDate date) {
        Path file = journalDir.resolve(date.format(DATE_FORMAT) + ".jsonl");
        List<ExecutionEvent> events = new ArrayList<>();
        if (!Files.exists(file)) return events;

        try {
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    events.add(mapper.readValue(line, ExecutionEvent.class));
                }
            }
        } catch (IOException e) {
            log.error("Failed to read journal file: {}", file, e);
        }
        return events;
    }

    public synchronized void close() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                log.error("Failed to close journal writer", e);
            }
        }
    }

    private void ensureWriter() throws IOException {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            if (currentWriter != null) {
                currentWriter.close();
            }
            Path file = journalDir.resolve(today.format(DATE_FORMAT) + ".jsonl");
            currentWriter = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            currentDate = today;
        }
    }
}
