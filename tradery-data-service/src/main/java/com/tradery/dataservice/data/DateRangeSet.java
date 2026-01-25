package com.tradery.dataservice.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Maintains a set of non-overlapping time ranges.
 * Thread-safe for concurrent access.
 * Used by DataInventory to track what data is cached.
 */
public final class DateRangeSet {

    public record Range(long start, long end) {
        public Range {
            if (start > end) {
                throw new IllegalArgumentException("start must be <= end");
            }
        }

        public boolean overlaps(Range other) {
            return this.start <= other.end && other.start <= this.end;
        }

        public boolean adjacent(Range other) {
            return this.end == other.start || other.end == this.start;
        }

        public boolean contains(long time) {
            return time >= start && time <= end;
        }

        public boolean containsRange(Range other) {
            return this.start <= other.start && this.end >= other.end;
        }

        public Range merge(Range other) {
            return new Range(Math.min(this.start, other.start), Math.max(this.end, other.end));
        }

        public long duration() {
            return end - start;
        }
    }

    private final List<Range> ranges = new ArrayList<>();

    /**
     * Add a range, merging with any overlapping or adjacent ranges.
     */
    public synchronized void add(long start, long end) {
        if (start > end) return;

        Range newRange = new Range(start, end);
        List<Range> merged = new ArrayList<>();
        Range current = newRange;

        for (Range existing : ranges) {
            if (existing.overlaps(current) || existing.adjacent(current)) {
                current = current.merge(existing);
            } else {
                merged.add(existing);
            }
        }

        merged.add(current);
        merged.sort(Comparator.comparingLong(Range::start));

        ranges.clear();
        ranges.addAll(merged);
    }

    /**
     * Check if the entire range is covered.
     */
    public synchronized boolean contains(long start, long end) {
        if (start > end) return true;

        Range query = new Range(start, end);
        for (Range range : ranges) {
            if (range.containsRange(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a specific timestamp is covered.
     */
    public synchronized boolean containsTime(long time) {
        for (Range range : ranges) {
            if (range.contains(time)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find gaps in coverage for a given range.
     * Returns list of ranges that are NOT covered.
     */
    public synchronized List<Range> findGaps(long start, long end) {
        if (start >= end) return Collections.emptyList();

        List<Range> gaps = new ArrayList<>();
        long current = start;

        for (Range range : ranges) {
            if (range.end < current) {
                continue; // Already past this range
            }
            if (range.start > end) {
                break; // Past the query range
            }

            // Gap before this range?
            if (range.start > current) {
                gaps.add(new Range(current, Math.min(range.start, end)));
            }

            // Move current past this range
            current = Math.max(current, range.end);
        }

        // Gap after all ranges?
        if (current < end) {
            gaps.add(new Range(current, end));
        }

        return gaps;
    }

    /**
     * Get all ranges (read-only copy).
     */
    public synchronized List<Range> getRanges() {
        return new ArrayList<>(ranges);
    }

    /**
     * Get total covered duration in milliseconds.
     */
    public synchronized long getTotalCoverage() {
        return ranges.stream().mapToLong(Range::duration).sum();
    }

    /**
     * Clear all ranges.
     */
    public synchronized void clear() {
        ranges.clear();
    }

    /**
     * Check if empty.
     */
    public synchronized boolean isEmpty() {
        return ranges.isEmpty();
    }

    /**
     * Get the earliest covered time, or -1 if empty.
     */
    public synchronized long getEarliestTime() {
        return ranges.isEmpty() ? -1 : ranges.get(0).start();
    }

    /**
     * Get the latest covered time, or -1 if empty.
     */
    public synchronized long getLatestTime() {
        return ranges.isEmpty() ? -1 : ranges.get(ranges.size() - 1).end();
    }

    @Override
    public synchronized String toString() {
        if (ranges.isEmpty()) return "DateRangeSet[]";
        return "DateRangeSet[" + ranges.size() + " ranges, " +
               (getTotalCoverage() / 3600000) + "h coverage]";
    }
}
