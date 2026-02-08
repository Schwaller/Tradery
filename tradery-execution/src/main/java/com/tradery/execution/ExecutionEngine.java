package com.tradery.execution;

import com.tradery.exchange.TradingClient;
import com.tradery.exchange.TradingClientFactory;
import com.tradery.exchange.exception.ExchangeException;
import com.tradery.exchange.model.*;
import com.tradery.execution.journal.ExecutionJournal;
import com.tradery.execution.order.LiveOrder;
import com.tradery.execution.order.OrderIntent;
import com.tradery.execution.order.OrderManager;
import com.tradery.execution.position.LivePosition;
import com.tradery.execution.position.PositionReconciler;
import com.tradery.execution.position.PositionTracker;
import com.tradery.execution.risk.KillSwitch;
import com.tradery.execution.risk.RiskLimits;
import com.tradery.execution.risk.RiskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Main execution orchestrator — wires together trading client, order management,
 * position tracking, risk management, and journaling.
 */
public class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final TradingConfig config;
    private TradingClient client;
    private OrderManager orderManager;
    private PositionTracker positionTracker;
    private RiskManager riskManager;
    private KillSwitch killSwitch;
    private ExecutionJournal journal;
    private PositionReconciler reconciler;

    private volatile ExecutionState state = ExecutionState.IDLE;
    private final List<Consumer<ExecutionState>> stateListeners = new CopyOnWriteArrayList<>();

    public ExecutionEngine(TradingConfig config) {
        this.config = config;
    }

    /**
     * Start the execution engine: connect to exchange, initialize components.
     */
    public void start() throws ExchangeException {
        if (state == ExecutionState.RUNNING) {
            log.warn("Execution engine already running");
            return;
        }

        log.info("Starting execution engine (venue={})", config.getActiveVenue());

        // Create trading client
        client = TradingClientFactory.create(config);
        client.connect();

        // Initialize components
        Path baseDir = Path.of(System.getProperty("user.home"), ".tradery");
        journal = new ExecutionJournal(baseDir);
        positionTracker = new PositionTracker(journal);
        orderManager = new OrderManager(client, positionTracker, journal);
        riskManager = new RiskManager(RiskLimits.fromConfig(config.getRisk()), positionTracker);
        killSwitch = new KillSwitch(client, riskManager);
        reconciler = new PositionReconciler(client, positionTracker);

        // Reconcile positions
        reconciler.reconcile();

        // Initialize equity tracking
        AccountState account = client.getAccountState();
        riskManager.updateEquity(account.equity());

        setState(ExecutionState.RUNNING);
        log.info("Execution engine started (equity={}, venue={})",
                account.equity(), client.getVenueName());
    }

    /**
     * Stop the execution engine.
     */
    public void stop() {
        if (state == ExecutionState.IDLE) return;

        log.info("Stopping execution engine");
        if (client != null) {
            client.disconnect();
        }
        if (journal != null) {
            journal.close();
        }
        setState(ExecutionState.IDLE);
    }

    /**
     * Execute an order intent after risk checks.
     */
    public void executeIntent(OrderIntent intent) {
        if (state != ExecutionState.RUNNING) {
            log.warn("Cannot execute — engine not running (state={})", state);
            return;
        }

        try {
            AccountState account = client.getAccountState();
            riskManager.updateEquity(account.equity());

            // Calculate position size (default 10% of equity for now)
            double positionSizeUsd = account.equity() * 0.10;

            // Risk checks
            List<String> rejections = riskManager.check(intent, account, positionSizeUsd);
            if (!rejections.isEmpty()) {
                log.warn("Order rejected by risk manager: {}", rejections);
                return;
            }

            // Calculate quantity from USD size
            double quantity = positionSizeUsd / intent.referencePrice();

            // Build order request
            OrderRequest request = OrderRequest.builder()
                    .symbol(intent.symbol())
                    .side(intent.side())
                    .type(intent.type())
                    .quantity(quantity)
                    .price(intent.referencePrice())
                    .reduceOnly(intent.reduceOnly())
                    .build();

            // Submit
            orderManager.submitOrder(request, intent.strategyId());

        } catch (ExchangeException e) {
            log.error("Failed to execute intent: {}", e.getMessage());
        }
    }

    /**
     * Manually place an order (from UI).
     */
    public LiveOrder placeOrder(OrderRequest request, String strategyId) throws ExchangeException {
        if (state != ExecutionState.RUNNING) {
            throw new ExchangeException("Engine not running");
        }
        return orderManager.submitOrder(request, strategyId);
    }

    /**
     * Cancel an order.
     */
    public void cancelOrder(String symbol, String orderId) throws ExchangeException {
        if (orderManager != null) {
            orderManager.cancelOrder(symbol, orderId);
        }
    }

    // --- Accessors ---

    public TradingClient getClient() { return client; }
    public OrderManager getOrderManager() { return orderManager; }
    public PositionTracker getPositionTracker() { return positionTracker; }
    public RiskManager getRiskManager() { return riskManager; }
    public KillSwitch getKillSwitch() { return killSwitch; }
    public ExecutionJournal getJournal() { return journal; }
    public ExecutionState getState() { return state; }
    public TradingConfig getConfig() { return config; }

    public void addStateListener(Consumer<ExecutionState> listener) {
        stateListeners.add(listener);
    }

    private void setState(ExecutionState newState) {
        this.state = newState;
        stateListeners.forEach(l -> {
            try { l.accept(newState); } catch (Exception e) { log.warn("State listener error", e); }
        });
    }
}
