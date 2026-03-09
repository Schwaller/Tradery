package com.tradery.ai.challenges.store;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.schedule.ChallengeSubscription;

import java.util.List;

/**
 * Persistence abstraction for challenges, results, and subscriptions.
 * Implement per storage backend (SQLite, file-based, etc.).
 */
public interface ChallengeStore {

    // ==================== Challenges ====================

    List<Challenge> listChallenges();

    Challenge getChallenge(String id);

    void saveChallenge(Challenge challenge);

    void deleteChallenge(String id);

    // ==================== Results (History) ====================

    /** All results for a specific challenge+subject pair, newest first. */
    List<ChallengeResult> getResults(String challengeId, String subjectId);

    /** Latest result per challenge for a given subject. */
    List<ChallengeResult> getLatestResults(String subjectId);

    /** Most recent result for a specific challenge+subject. */
    ChallengeResult getLatestResult(String challengeId, String subjectId);

    /** Signal value history for sparkline rendering. Newest first, limited. */
    List<ChallengeResult> getSignalHistory(String challengeId, String subjectId, int limit);

    /** All results for a challenge across all subjects, oldest first, limited. */
    List<ChallengeResult> getResultsForChallenge(String challengeId, int limit);

    void saveResult(ChallengeResult result);

    // ==================== Subscriptions ====================

    /** All active subscriptions (for scheduler tick). */
    List<ChallengeSubscription> getActiveSubscriptions();

    ChallengeSubscription getSubscription(String challengeId, String subjectId);

    void saveSubscription(ChallengeSubscription subscription);

    void deleteSubscription(String challengeId, String subjectId);
}
