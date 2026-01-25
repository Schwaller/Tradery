package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Console output for signals.
 * Logs to both SLF4J logger and stdout with formatting.
 */
public class ConsoleLogger implements AlertOutput {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLogger.class);
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private boolean enabled = true;

    public ConsoleLogger() {
    }

    public ConsoleLogger(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void send(SignalEvent signal) {
        if (!enabled) {
            return;
        }

        String time = TIME_FORMAT.format(signal.timestamp());
        String prefix = signal.type() == SignalEvent.SignalType.ENTRY ? ">>>" : "<<<";

        // Format: >>> [12:30:45] ENTRY RSI Reversal v2 BTCUSDT 1h @ 42,350.00
        String message = String.format("%s [%s] %s %s v%d %s %s @ %,.2f",
            prefix,
            time,
            signal.type().getLabel(),
            signal.strategyName(),
            signal.strategyVersion(),
            signal.symbol(),
            signal.timeframe(),
            signal.price()
        );

        // Log with appropriate level
        if (signal.type() == SignalEvent.SignalType.ENTRY) {
            log.info("\u001B[32m{}\u001B[0m", message); // Green for entry
        } else {
            log.info("\u001B[33m{}\u001B[0m", message); // Yellow for exit
        }

        // Also print to stdout for visibility
        System.out.println(message);
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
