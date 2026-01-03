package com.tradery.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExitZone matching logic.
 */
class ExitZoneTest {

    @Test
    @DisplayName("Zone with both bounds matches P&L within range")
    void matchesWithinBounds() {
        ExitZone zone = new ExitZone("Test", -5.0, 5.0, "", "none", null, "none", null, false, 0);

        assertTrue(zone.matches(0.0), "0% should match [-5, 5)");
        assertTrue(zone.matches(-5.0), "Lower bound -5% should be inclusive");
        assertTrue(zone.matches(4.99), "Just below upper bound should match");
        assertTrue(zone.matches(-4.0), "-4% should match");
        assertTrue(zone.matches(3.0), "3% should match");
    }

    @Test
    @DisplayName("Zone with both bounds excludes P&L outside range")
    void excludesOutsideBounds() {
        ExitZone zone = new ExitZone("Test", -5.0, 5.0, "", "none", null, "none", null, false, 0);

        assertFalse(zone.matches(-5.1), "Below lower bound should not match");
        assertFalse(zone.matches(-10.0), "Well below lower bound should not match");
        assertFalse(zone.matches(5.0), "Upper bound 5% should be exclusive");
        assertFalse(zone.matches(10.0), "Above upper bound should not match");
    }

    @Test
    @DisplayName("Zone with null lower bound matches any P&L below max")
    void matchesWithNullLowerBound() {
        ExitZone zone = new ExitZone("Failure", null, -5.0, "", "none", null, "none", null, true, 0);

        assertTrue(zone.matches(-10.0), "-10% should match (< -5%)");
        assertTrue(zone.matches(-100.0), "-100% should match (< -5%)");
        assertTrue(zone.matches(-5.1), "Just below -5% should match");

        assertFalse(zone.matches(-5.0), "-5% should not match (exclusive upper)");
        assertFalse(zone.matches(-4.0), "-4% should not match (> -5%)");
        assertFalse(zone.matches(0.0), "0% should not match");
    }

    @Test
    @DisplayName("Zone with null upper bound matches any P&L above min")
    void matchesWithNullUpperBound() {
        ExitZone zone = new ExitZone("Profit", 5.0, null, "", "trailing_percent", 1.0, "none", null, false, 0);

        assertTrue(zone.matches(5.0), "5% should match (inclusive lower)");
        assertTrue(zone.matches(10.0), "10% should match");
        assertTrue(zone.matches(100.0), "100% should match");

        assertFalse(zone.matches(4.99), "Just below 5% should not match");
        assertFalse(zone.matches(0.0), "0% should not match");
        assertFalse(zone.matches(-5.0), "-5% should not match");
    }

    @Test
    @DisplayName("Zone with both null bounds matches any P&L")
    void matchesWithBothNullBounds() {
        ExitZone zone = new ExitZone("CatchAll", null, null, "", "none", null, "none", null, false, 0);

        assertTrue(zone.matches(-100.0));
        assertTrue(zone.matches(-5.0));
        assertTrue(zone.matches(0.0));
        assertTrue(zone.matches(5.0));
        assertTrue(zone.matches(100.0));
    }

    @Test
    @DisplayName("Default zone matches any P&L")
    void defaultZoneMatchesAll() {
        ExitZone zone = ExitZone.defaultZone();

        assertTrue(zone.matches(-50.0));
        assertTrue(zone.matches(0.0));
        assertTrue(zone.matches(50.0));
    }

    @Test
    @DisplayName("Builder creates zone with correct properties")
    void builderCreatesCorrectZone() {
        ExitZone zone = ExitZone.builder("TestZone")
            .minPnl(-10.0)
            .maxPnl(10.0)
            .stopLoss("trailing_percent", 2.0)
            .takeProfit("fixed_percent", 5.0)
            .exitImmediately(true)
            .minBarsBeforeExit(5)
            .build();

        assertEquals("TestZone", zone.name());
        assertEquals(-10.0, zone.minPnlPercent());
        assertEquals(10.0, zone.maxPnlPercent());
        assertEquals("trailing_percent", zone.stopLossType());
        assertEquals(2.0, zone.stopLossValue());
        assertEquals("fixed_percent", zone.takeProfitType());
        assertEquals(5.0, zone.takeProfitValue());
        assertTrue(zone.exitImmediately());
        assertEquals(5, zone.minBarsBeforeExit());
    }

    @Test
    @DisplayName("Adjacent zones have no gaps or overlaps")
    void adjacentZonesNoGapsOrOverlaps() {
        ExitZone failure = new ExitZone("Failure", null, -5.0, "", "none", null, "none", null, true, 0);
        ExitZone normal = new ExitZone("Default", -5.0, 2.0, "", "none", null, "none", null, false, 0);
        ExitZone profit = new ExitZone("Protect", 2.0, null, "", "trailing_percent", 1.0, "none", null, false, 0);

        // Test boundary at -5%
        assertTrue(failure.matches(-5.1), "-5.1% matches Failure");
        assertFalse(failure.matches(-5.0), "-5% does not match Failure (exclusive)");
        assertTrue(normal.matches(-5.0), "-5% matches Default (inclusive)");

        // Test boundary at 2%
        assertTrue(normal.matches(1.99), "1.99% matches Default");
        assertFalse(normal.matches(2.0), "2% does not match Default (exclusive)");
        assertTrue(profit.matches(2.0), "2% matches Protect (inclusive)");

        // Verify exactly one zone matches at boundaries
        double[] testPoints = {-10.0, -5.0, -2.5, 0.0, 1.0, 2.0, 5.0, 10.0};
        for (double pnl : testPoints) {
            int matchCount = 0;
            if (failure.matches(pnl)) matchCount++;
            if (normal.matches(pnl)) matchCount++;
            if (profit.matches(pnl)) matchCount++;
            assertEquals(1, matchCount, "Exactly one zone should match for P&L " + pnl + "%");
        }
    }
}
