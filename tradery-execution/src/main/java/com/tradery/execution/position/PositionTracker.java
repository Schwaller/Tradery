package com.tradery.execution.position;

import com.tradery.exchange.model.OrderSide;
import com.tradery.execution.journal.ExecutionJournal;
import com.tradery.execution.journal.PositionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live positions per strategy:symbol pair.
 */
public class PositionTracker {

    private static final Logger log = LoggerFactory.getLogger(PositionTracker.class);

    // Key: "strategyId:symbol"
    private final Map<String, LivePosition> positions = new ConcurrentHashMap<>();
    private final ExecutionJournal journal;

    public PositionTracker(ExecutionJournal journal) {
        this.journal = journal;
    }

    /**
     * Apply a fill to the tracked position.
     */
    public void onFill(String strategyId, String symbol, OrderSide side, double quantity, double price) {
        String key = positionKey(strategyId, symbol);
        LivePosition position = positions.get(key);

        if (position == null) {
            // New position
            position = new LivePosition(strategyId, symbol, side, quantity, price);
            positions.put(key, position);
            journal.log(PositionEvent.opened(position));
            log.info("Position opened: {} {} {} {} @ {}", strategyId, side, quantity, symbol, price);
        } else {
            double pnl = position.applyFill(side, quantity, price);

            if (position.isClosed()) {
                positions.remove(key);
                journal.log(PositionEvent.closed(position, price, pnl));
                log.info("Position closed: {} {} PnL={}", strategyId, symbol, pnl);
            } else {
                journal.log(PositionEvent.modified(position));
                log.info("Position modified: {} {} qty={} avg={}",
                        strategyId, symbol, position.getQuantity(), position.getAvgEntryPrice());
            }
        }
    }

    /**
     * Get all open positions.
     */
    public List<LivePosition> getOpenPositions() {
        return new ArrayList<>(positions.values());
    }

    /**
     * Get positions for a specific strategy.
     */
    public List<LivePosition> getPositionsForStrategy(String strategyId) {
        return positions.values().stream()
                .filter(p -> strategyId.equals(p.getStrategyId()))
                .toList();
    }

    /**
     * Get a specific position.
     */
    public Optional<LivePosition> getPosition(String strategyId, String symbol) {
        return Optional.ofNullable(positions.get(positionKey(strategyId, symbol)));
    }

    /**
     * Check if a strategy has an open position for a symbol.
     */
    public boolean hasPosition(String strategyId, String symbol) {
        return positions.containsKey(positionKey(strategyId, symbol));
    }

    /**
     * Get total number of open positions.
     */
    public int getOpenPositionCount() {
        return positions.size();
    }

    /**
     * Restore positions from persisted state (used on startup).
     */
    public void restorePosition(LivePosition position) {
        String key = positionKey(position.getStrategyId(), position.getSymbol());
        positions.put(key, position);
    }

    private String positionKey(String strategyId, String symbol) {
        return strategyId + ":" + symbol;
    }
}
