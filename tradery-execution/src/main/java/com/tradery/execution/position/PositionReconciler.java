package com.tradery.execution.position;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.ExchangePosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reconciles local position state with exchange state on startup/reconnect.
 */
public class PositionReconciler {

    private static final Logger log = LoggerFactory.getLogger(PositionReconciler.class);

    private final TradingClient client;
    private final PositionTracker tracker;

    public PositionReconciler(TradingClient client, PositionTracker tracker) {
        this.client = client;
        this.tracker = tracker;
    }

    /**
     * Reconcile local tracked positions with what the exchange reports.
     * Logs warnings for mismatches but does not auto-correct.
     */
    public void reconcile() throws ExchangeException {
        List<ExchangePosition> exchangePositions = client.getPositions();
        List<LivePosition> localPositions = tracker.getOpenPositions();

        log.info("Reconciling positions: {} local, {} on exchange",
                localPositions.size(), exchangePositions.size());

        // Check for positions on exchange that we don't track locally
        for (ExchangePosition ep : exchangePositions) {
            boolean found = localPositions.stream()
                    .anyMatch(lp -> lp.getSymbol().equalsIgnoreCase(ep.symbol()));
            if (!found) {
                log.warn("UNTRACKED position on exchange: {} {} {} @ {}",
                        ep.side(), ep.quantity(), ep.symbol(), ep.entryPrice());
            }
        }

        // Check for locally tracked positions that aren't on exchange
        for (LivePosition lp : localPositions) {
            boolean found = exchangePositions.stream()
                    .anyMatch(ep -> ep.symbol().equalsIgnoreCase(lp.getSymbol()));
            if (!found) {
                log.warn("PHANTOM local position (not on exchange): {} {} {} @ {}",
                        lp.getStrategyId(), lp.getSide(), lp.getQuantity(), lp.getSymbol());
            }
        }

        // Check for quantity mismatches
        for (LivePosition lp : localPositions) {
            exchangePositions.stream()
                    .filter(ep -> ep.symbol().equalsIgnoreCase(lp.getSymbol()))
                    .findFirst()
                    .ifPresent(ep -> {
                        if (Math.abs(ep.quantity() - lp.getQuantity()) > 0.0001) {
                            log.warn("QUANTITY MISMATCH for {}: local={} exchange={}",
                                    lp.getSymbol(), lp.getQuantity(), ep.quantity());
                        }
                    });
        }
    }
}
