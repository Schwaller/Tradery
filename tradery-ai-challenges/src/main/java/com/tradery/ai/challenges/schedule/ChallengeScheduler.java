package com.tradery.ai.challenges.schedule;

import com.tradery.ai.challenges.execution.ChallengeExecutor;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Background scheduler that auto-refreshes challenge subscriptions.
 * Reusable: inject a subject resolver and store for any domain.
 */
public class ChallengeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChallengeScheduler.class);
    private static final int TICK_INTERVAL_MINUTES = 5;
    private static final int MAX_CONCURRENT_RUNS = 3;

    private final ChallengeStore store;
    private final ChallengeExecutor executor;
    private final Function<String, ChallengeSubject> subjectResolver;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicInteger activeRuns = new AtomicInteger(0);
    private ScheduledExecutorService scheduler;
    private Consumer<ChallengeResult> onResultCallback;

    /**
     * @param store           Challenge persistence
     * @param executor        Challenge execution engine
     * @param subjectResolver Resolves subject ID to domain object. Return null if subject no longer exists.
     */
    public ChallengeScheduler(ChallengeStore store, ChallengeExecutor executor,
                               Function<String, ChallengeSubject> subjectResolver) {
        this.store = store;
        this.executor = executor;
        this.subjectResolver = subjectResolver;
    }

    /** Optional callback invoked after each auto-refresh result is saved. */
    public void setOnResultCallback(Consumer<ChallengeResult> callback) {
        this.onResultCallback = callback;
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        log.info("Challenge auto-refresh {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "challenge-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 1, TICK_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("Challenge scheduler started (every {}m)", TICK_INTERVAL_MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("Challenge scheduler stopped");
        }
    }

    /**
     * Ensure a subscription exists for a challenge+subject pair.
     * Called after a manual execution to "subscribe" the subject for auto-refresh.
     */
    public void ensureSubscribed(String challengeId, String subjectId) {
        if (store.getSubscription(challengeId, subjectId) != null) return;

        Challenge challenge = store.getChallenge(challengeId);
        if (challenge == null || challenge.refreshInterval() == null) return;

        long now = System.currentTimeMillis();
        ChallengeSubscription sub = new ChallengeSubscription(
            challengeId, subjectId, now,
            now + challenge.refreshInterval().toMillis()
        );
        store.saveSubscription(sub);
        log.debug("Subscribed {}/{} for auto-refresh every {}", challengeId, subjectId, challenge.refreshInterval());
    }

    private void tick() {
        if (!enabled.get()) return;

        try {
            long now = System.currentTimeMillis();
            for (ChallengeSubscription sub : store.getActiveSubscriptions()) {
                if (sub.nextRunTimestamp() > now) continue;
                if (activeRuns.get() >= MAX_CONCURRENT_RUNS) break;

                Challenge challenge = store.getChallenge(sub.challengeId());
                if (challenge == null || !challenge.enabled() || challenge.refreshInterval() == null) continue;

                ChallengeSubject subject = subjectResolver.apply(sub.subjectId());
                if (subject == null) {
                    // Subject no longer exists — clean up subscription
                    store.deleteSubscription(sub.challengeId(), sub.subjectId());
                    continue;
                }

                runAsync(challenge, subject, sub);
            }
        } catch (Exception e) {
            log.error("Challenge scheduler tick failed", e);
        }
    }

    private void runAsync(Challenge challenge, ChallengeSubject subject, ChallengeSubscription sub) {
        activeRuns.incrementAndGet();
        Thread.ofVirtual().name("challenge-" + challenge.id() + "-" + subject.id()).start(() -> {
            try {
                log.info("Auto-refresh: {} on {}", challenge.id(), subject.name());
                ChallengeResult prev = store.getLatestResult(challenge.id(), subject.id());
                ChallengeResult result = executor.execute(challenge, subject,
                    challenge.refreshEscalationIndex(), null, prev);
                store.saveResult(result);

                // Update subscription timing
                long now = System.currentTimeMillis();
                sub.setLastRunTimestamp(now);
                sub.setNextRunTimestamp(now + challenge.refreshInterval().toMillis());
                store.saveSubscription(sub);

                if (onResultCallback != null) {
                    onResultCallback.accept(result);
                }
            } catch (Exception e) {
                log.error("Auto-refresh failed for {}/{}: {}", challenge.id(), subject.id(), e.getMessage());
            } finally {
                activeRuns.decrementAndGet();
            }
        });
    }
}
