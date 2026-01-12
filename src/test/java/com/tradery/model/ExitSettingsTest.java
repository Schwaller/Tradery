package com.tradery.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for ExitSettings validation logic.
 */
class ExitSettingsTest {

    @Test
    @DisplayName("Non-overlapping adjacent zones produce no warnings")
    void adjacentZonesNoOverlap() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("Stop Loss").maxPnl(-5.0).exitImmediately(true).build());
        zones.add(ExitZone.builder("Normal").minPnl(-5.0).maxPnl(5.0).build());
        zones.add(ExitZone.builder("Take Profit").minPnl(5.0).build());

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertTrue(warnings.isEmpty(), "Adjacent zones should not produce overlap warnings");
    }

    @Test
    @DisplayName("Overlapping zones produce warnings")
    void overlappingZonesProduceWarnings() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("Zone A").minPnl(0.0).maxPnl(10.0).build());
        zones.add(ExitZone.builder("Zone B").minPnl(5.0).maxPnl(15.0).build());  // Overlaps A in [5, 10)

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertEquals(1, warnings.size(), "Should detect one overlap");
        assertTrue(warnings.get(0).contains("Zone A"));
        assertTrue(warnings.get(0).contains("Zone B"));
        assertTrue(warnings.get(0).contains("overlapping"));
    }

    @Test
    @DisplayName("Multiple overlaps produce multiple warnings")
    void multipleOverlapsProduceMultipleWarnings() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("Zone A").minPnl(0.0).maxPnl(10.0).build());
        zones.add(ExitZone.builder("Zone B").minPnl(5.0).maxPnl(15.0).build());  // Overlaps A
        zones.add(ExitZone.builder("Zone C").minPnl(12.0).maxPnl(20.0).build()); // Overlaps B

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertEquals(2, warnings.size(), "Should detect two overlaps: A-B and B-C");
    }

    @Test
    @DisplayName("Unbounded zones overlap with bounded zones")
    void unboundedZonesOverlap() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("All Loss").maxPnl(0.0).build());  // (-∞, 0)
        zones.add(ExitZone.builder("Deep Loss").maxPnl(-10.0).build()); // (-∞, -10) - subset of All Loss

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertEquals(1, warnings.size(), "Unbounded zones that overlap should produce warning");
    }

    @Test
    @DisplayName("Catch-all zone overlaps with all other zones")
    void catchAllZoneOverlapsAll() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("Catch All").build());  // (-∞, +∞)
        zones.add(ExitZone.builder("Stop Loss").maxPnl(-5.0).build());
        zones.add(ExitZone.builder("Take Profit").minPnl(5.0).build());

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertEquals(2, warnings.size(), "Catch-all zone should overlap with both other zones");
    }

    @Test
    @DisplayName("Single zone produces no warnings")
    void singleZoneNoWarnings() {
        List<ExitZone> zones = new ArrayList<>();
        zones.add(ExitZone.builder("Only Zone").minPnl(-10.0).maxPnl(10.0).build());

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertTrue(warnings.isEmpty(), "Single zone should not produce overlap warnings");
    }

    @Test
    @DisplayName("Empty zones list produces no warnings")
    void emptyZonesNoWarnings() {
        ExitSettings settings = new ExitSettings(new ArrayList<>(), ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        // Note: getZones() adds a default zone if empty, so there will be 1 zone
        assertTrue(warnings.isEmpty(), "Empty/default zones should not produce overlap warnings");
    }

    @Test
    @DisplayName("Touching but non-overlapping zones (exclusive upper bound)")
    void touchingButNotOverlapping() {
        List<ExitZone> zones = new ArrayList<>();
        // Zone A: [0, 5) and Zone B: [5, 10) - they touch at 5 but don't overlap
        zones.add(ExitZone.builder("Zone A").minPnl(0.0).maxPnl(5.0).build());
        zones.add(ExitZone.builder("Zone B").minPnl(5.0).maxPnl(10.0).build());

        ExitSettings settings = new ExitSettings(zones, ZoneEvaluation.CANDLE_CLOSE);
        List<String> warnings = settings.findOverlappingZones();

        assertTrue(warnings.isEmpty(), "Touching zones [0,5) and [5,10) should not overlap");
    }
}
