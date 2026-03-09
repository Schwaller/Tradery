package com.tradery.ai.challenges.store;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.schedule.ChallengeSubscription;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of ChallengeStore for testing and simple use cases.
 */
public class InMemoryChallengeStore implements ChallengeStore {

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final List<ChallengeResult> results = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ChallengeSubscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong resultIdSeq = new AtomicLong(1);

    @Override
    public List<Challenge> listChallenges() {
        return challenges.values().stream()
            .sorted(Comparator.comparingInt(Challenge::displayOrder))
            .toList();
    }

    @Override
    public Challenge getChallenge(String id) {
        return challenges.get(id);
    }

    @Override
    public void saveChallenge(Challenge challenge) {
        challenges.put(challenge.id(), challenge);
    }

    @Override
    public void deleteChallenge(String id) {
        challenges.remove(id);
    }

    @Override
    public List<ChallengeResult> getResults(String challengeId, String subjectId) {
        return results.stream()
            .filter(r -> challengeId.equals(r.challengeId()) && subjectId.equals(r.subjectId()))
            .sorted(Comparator.comparingLong(ChallengeResult::timestamp).reversed())
            .toList();
    }

    @Override
    public List<ChallengeResult> getLatestResults(String subjectId) {
        Map<String, ChallengeResult> latest = new LinkedHashMap<>();
        // Iterate newest-first
        List<ChallengeResult> sorted = results.stream()
            .filter(r -> subjectId.equals(r.subjectId()))
            .sorted(Comparator.comparingLong(ChallengeResult::timestamp).reversed())
            .toList();
        for (ChallengeResult r : sorted) {
            latest.putIfAbsent(r.challengeId(), r);
        }
        return new ArrayList<>(latest.values());
    }

    @Override
    public ChallengeResult getLatestResult(String challengeId, String subjectId) {
        return results.stream()
            .filter(r -> challengeId.equals(r.challengeId()) && subjectId.equals(r.subjectId()))
            .max(Comparator.comparingLong(ChallengeResult::timestamp))
            .orElse(null);
    }

    @Override
    public List<ChallengeResult> getSignalHistory(String challengeId, String subjectId, int limit) {
        return results.stream()
            .filter(r -> challengeId.equals(r.challengeId()) && subjectId.equals(r.subjectId()))
            .filter(ChallengeResult::hasSignal)
            .sorted(Comparator.comparingLong(ChallengeResult::timestamp).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public List<ChallengeResult> getResultsForChallenge(String challengeId, int limit) {
        return results.stream()
            .filter(r -> challengeId.equals(r.challengeId()))
            .sorted(Comparator.comparingLong(ChallengeResult::timestamp))
            .limit(limit)
            .toList();
    }

    @Override
    public void saveResult(ChallengeResult result) {
        if (result.id() == 0) {
            result.setId(resultIdSeq.getAndIncrement());
        }
        results.add(result);
    }

    @Override
    public List<ChallengeSubscription> getActiveSubscriptions() {
        return new ArrayList<>(subscriptions.values());
    }

    @Override
    public ChallengeSubscription getSubscription(String challengeId, String subjectId) {
        return subscriptions.get(subKey(challengeId, subjectId));
    }

    @Override
    public void saveSubscription(ChallengeSubscription subscription) {
        subscriptions.put(subKey(subscription.challengeId(), subscription.subjectId()), subscription);
    }

    @Override
    public void deleteSubscription(String challengeId, String subjectId) {
        subscriptions.remove(subKey(challengeId, subjectId));
    }

    private static String subKey(String challengeId, String subjectId) {
        return challengeId + "::" + subjectId;
    }
}
