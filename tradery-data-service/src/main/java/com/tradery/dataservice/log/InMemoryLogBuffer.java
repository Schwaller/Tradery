package com.tradery.dataservice.log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe in-memory ring buffer for log lines.
 * Retains the last {@code maxLines} entries.
 */
public class InMemoryLogBuffer {

    private static final int DEFAULT_MAX_LINES = 2000;
    private static final InMemoryLogBuffer INSTANCE = new InMemoryLogBuffer(DEFAULT_MAX_LINES);

    private final int maxLines;
    private final ConcurrentLinkedDeque<String> buffer = new ConcurrentLinkedDeque<>();

    public InMemoryLogBuffer(int maxLines) {
        this.maxLines = maxLines;
    }

    public static InMemoryLogBuffer getInstance() {
        return INSTANCE;
    }

    public void add(String line) {
        buffer.addLast(line);
        while (buffer.size() > maxLines) {
            buffer.pollFirst();
        }
    }

    /**
     * Returns the last N lines from the buffer.
     */
    public List<String> getLastLines(int n) {
        List<String> all = new ArrayList<>(buffer);
        int start = Math.max(0, all.size() - n);
        return all.subList(start, all.size());
    }

    public int size() {
        return buffer.size();
    }
}
