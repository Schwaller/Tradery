package com.tradery.forge.ui.charts;

import com.tradery.charts.overlay.OverlayManager;
import com.tradery.charts.overlay.PhaseOverlay;
import com.tradery.core.model.Candle;
import org.jfree.chart.JFreeChart;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Forge-specific overlay manager extending the shared OverlayManager.
 * Adds Phase overlays which require forge-specific backtest integration.
 * DVP and Footprint overlays are now in the base OverlayManager via provider interfaces.
 */
public class ForgeOverlayManager extends OverlayManager {

    private final List<PhaseOverlay> phaseOverlays = new ArrayList<>();

    public ForgeOverlayManager(JFreeChart priceChart) {
        super(priceChart);
    }

    // ===== Phase Overlays =====

    /**
     * Data record for a phase overlay to be rendered.
     */
    public record PhaseOverlayData(String name, boolean[] activeState, List<Candle> candles, Color color) {}

    public void setPhaseOverlays(List<PhaseOverlayData> phases) {
        clearPhaseOverlays();
        if (phases == null || phases.isEmpty()) return;

        for (PhaseOverlayData data : phases) {
            PhaseOverlay overlay = new PhaseOverlay(data.name(), data.activeState(), data.candles(), data.color());
            if (applyChartOverlay(overlay)) {
                phaseOverlays.add(overlay);
            }
        }
    }

    public void clearPhaseOverlays() {
        for (PhaseOverlay overlay : phaseOverlays) {
            removeChartOverlay(overlay);
        }
        phaseOverlays.clear();
    }

    public boolean hasPhaseOverlays() {
        return !phaseOverlays.isEmpty();
    }

    // ===== Clear All =====

    @Override
    public void clearAll() {
        super.clearAll();
        clearPhaseOverlays();
    }
}
