package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.subject.ChallengeSubject;

import java.util.Map;

/**
 * A self-contained challenge subject — the challenge is its own subject.
 * Used when challenges are not targeted at a specific entity.
 */
public class StandaloneChallengeSubject implements ChallengeSubject {

    private final Challenge challenge;

    public StandaloneChallengeSubject(Challenge challenge) {
        this.challenge = challenge;
    }

    @Override
    public String id() {
        return challenge.id();
    }

    @Override
    public String name() {
        return challenge.title();
    }

    @Override
    public String typeId() {
        return "challenge";
    }

    @Override
    public Map<String, String> attributes() {
        return Map.of();
    }
}
