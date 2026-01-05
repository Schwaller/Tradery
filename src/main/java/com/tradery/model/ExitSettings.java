package com.tradery.model;

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
    public ExitZone findMatchingZone(double pnlPercent) {
        for (ExitZone zone : getZones()) {
            if (zone.matches(pnlPercent)) {
                return zone;
            }
        }
        List<ExitZone> z = getZones();
        return z.isEmpty() ? null : z.get(0);
    }

    public boolean hasMultipleZones() {
        return getZones().size() > 1;
    }

    public static ExitSettings defaults() {
        List<ExitZone> defaultZones = new ArrayList<>();
        defaultZones.add(ExitZone.defaultZone());
        return new ExitSettings(defaultZones, ZoneEvaluation.CANDLE_CLOSE);
    }
}
