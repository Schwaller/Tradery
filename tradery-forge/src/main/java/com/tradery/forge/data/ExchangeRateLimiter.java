package com.tradery.forge.data;

/**
 * Rate limiter interface for exchange API requests.
 * Each exchange has different rate limits.
 */
public interface ExchangeRateLimiter {

    /**
     * Acquire permission to make a request.
     * Blocks if necessary to respect rate limits.
     */
    void acquire();

    /**
     * Acquire permission with timeout.
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if permission was acquired, false if timeout
     */
    boolean tryAcquire(long timeoutMs);

    /**
     * Get the minimum delay between requests in milliseconds.
     */
    long getMinDelayMs();

    /**
     * Get the maximum requests per minute for this rate limiter.
     */
    int getRequestsPerMinute();

    /**
     * Simple rate limiter with fixed delay between requests.
     */
    static ExchangeRateLimiter fixedDelay(long delayMs) {
        return new FixedDelayRateLimiter(delayMs);
    }
}

/**
 * Simple fixed-delay rate limiter implementation.
 */
class FixedDelayRateLimiter implements ExchangeRateLimiter {
    private final long delayMs;
    private long lastRequestTime = 0;

    FixedDelayRateLimiter(long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < delayMs) {
            try {
                Thread.sleep(delayMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    @Override
    public synchronized boolean tryAcquire(long timeoutMs) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        long waitTime = delayMs - elapsed;

        if (waitTime <= 0) {
            lastRequestTime = now;
            return true;
        }

        if (waitTime > timeoutMs) {
            return false;
        }

        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        lastRequestTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public long getMinDelayMs() {
        return delayMs;
    }

    @Override
    public int getRequestsPerMinute() {
        return (int) (60000 / delayMs);
    }
}
