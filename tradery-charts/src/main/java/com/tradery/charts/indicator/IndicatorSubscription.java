package com.tradery.charts.indicator;

import javax.swing.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Async handle for an indicator computation.
 * Returned by {@link IndicatorPool#subscribe(IndicatorCompute)}.
 *
 * @param <T> The indicator result type
 */
public class IndicatorSubscription<T> implements AutoCloseable {

    private volatile T data;
    private volatile boolean ready;
    private volatile boolean closed;
    private final CopyOnWriteArrayList<Consumer<T>> callbacks = new CopyOnWriteArrayList<>();
    private final Runnable onClose;

    IndicatorSubscription(Runnable onClose) {
        this.onClose = onClose;
    }

    /**
     * Get the computed data. Returns null if still computing.
     */
    public T getData() {
        return data;
    }

    /**
     * Check if the computation is complete.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Register a callback for when data becomes available.
     * Callback is invoked on the EDT via SwingUtilities.invokeLater.
     * If data is already available, callback fires immediately on EDT.
     */
    public void onReady(Consumer<T> callback) {
        if (closed) return;
        if (ready && data != null) {
            SwingUtilities.invokeLater(() -> {
                if (!closed) callback.accept(data);
            });
        } else {
            callbacks.add(callback);
        }
    }

    /**
     * Called by IndicatorPool when computation completes.
     */
    void setResult(T result) {
        if (closed) return;
        this.data = result;
        this.ready = true;
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            for (Consumer<T> cb : callbacks) {
                cb.accept(result);
            }
        });
    }

    /**
     * Release this subscription.
     */
    @Override
    public void close() {
        closed = true;
        callbacks.clear();
        if (onClose != null) {
            onClose.run();
        }
    }
}
