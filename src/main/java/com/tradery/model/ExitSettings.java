package com.tradery.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Exit-side settings including zones and evaluation mode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExitSettings {

    private List<ExitZone> zones = new ArrayList<>();
    private ZoneEvaluation evaluation = ZoneEvaluation.CANDLE_CLOSE;

    public ExitSettings() {}

    public ExitSettings(List<ExitZone> zones, ZoneEvaluation evaluation) {
        this.zones = zones != null ? zones : new ArrayList<>();
        this.evaluation = evaluation != null ? evaluation : ZoneEvaluation.CANDLE_CLOSE;
    }

    public List<ExitZone> getZones() {
        if (zones == null) {
            zones = new ArrayList<>();
        }
        if (zones.isEmpty()) {
            zones.add(ExitZone.defaultZone());
        }
        return zones;
    }

    public void setZones(List<ExitZone> zones) {
        this.zones = zones != null ? zones : new ArrayList<>();
    }

    public ZoneEvaluation getEvaluation() {
        return evaluation != null ? evaluation : ZoneEvaluation.CANDLE_CLOSE;
    }

    public void setEvaluation(ZoneEvaluation evaluation) {
        this.evaluation = evaluation;
    }

    /**
     * Find the exit zone that matches the given P&L percentage.
     */
    @JsonIgnore
    public ExitZone findMatchingZone(double pnlPercent) {
        for (ExitZone zone : getZones()) {
            if (zone.matches(pnlPercent)) {
                return zone;
            }
        }
        List<ExitZone> z = getZones();
        return z.isEmpty() ? null : z.get(0);
    }

    @JsonIgnore
    public boolean hasMultipleZones() {
        return getZones().size() > 1;
    }

    /**
     * Check for overlapping zones and return warnings.
     * Zones overlap if their P&L ranges intersect.
     *
     * @return List of warning messages for overlapping zones (empty if none)
     */
    @JsonIgnore
    public List<String> findOverlappingZones() {
        List<String> warnings = new ArrayList<>();
        List<ExitZone> zoneList = getZones();

        for (int i = 0; i < zoneList.size(); i++) {
            for (int j = i + 1; j < zoneList.size(); j++) {
                ExitZone a = zoneList.get(i);
                ExitZone b = zoneList.get(j);

                if (zonesOverlap(a, b)) {
                    warnings.add(String.format(
                        "Exit zones '%s' [%s, %s) and '%s' [%s, %s) have overlapping P&L ranges",
                        a.name(),
                        a.minPnlPercent() != null ? a.minPnlPercent() + "%" : "-∞",
                        a.maxPnlPercent() != null ? a.maxPnlPercent() + "%" : "+∞",
                        b.name(),
                        b.minPnlPercent() != null ? b.minPnlPercent() + "%" : "-∞",
                        b.maxPnlPercent() != null ? b.maxPnlPercent() + "%" : "+∞"
                    ));
                }
            }
        }

        return warnings;
    }

    /**
     * Check if two zones have overlapping P&L ranges.
     * Ranges use inclusive lower bound [min and exclusive upper bound max).
     */
    private boolean zonesOverlap(ExitZone a, ExitZone b) {
        // Get bounds, treating null as -∞ for min and +∞ for max
        Double minA = a.minPnlPercent();
        Double maxA = a.maxPnlPercent();
        Double minB = b.minPnlPercent();
        Double maxB = b.maxPnlPercent();

        // Two ranges [minA, maxA) and [minB, maxB) overlap if:
        // max(minA, minB) < min(maxA, maxB)

        // Effective lower bound of intersection
        double effectiveMin;
        if (minA == null && minB == null) {
            effectiveMin = Double.NEGATIVE_INFINITY;
        } else if (minA == null) {
            effectiveMin = minB;
        } else if (minB == null) {
            effectiveMin = minA;
        } else {
            effectiveMin = Math.max(minA, minB);
        }

        // Effective upper bound of intersection
        double effectiveMax;
        if (maxA == null && maxB == null) {
            effectiveMax = Double.POSITIVE_INFINITY;
        } else if (maxA == null) {
            effectiveMax = maxB;
        } else if (maxB == null) {
            effectiveMax = maxA;
        } else {
            effectiveMax = Math.min(maxA, maxB);
        }

        // Ranges overlap if effectiveMin < effectiveMax
        return effectiveMin < effectiveMax;
    }

    public static ExitSettings defaults() {
        List<ExitZone> defaultZones = new ArrayList<>();
        defaultZones.add(ExitZone.defaultZone());
        return new ExitSettings(defaultZones, ZoneEvaluation.CANDLE_CLOSE);
    }
}
