package com.tradery.sharing.governance;

import com.tradery.news.ui.coin.FactStore;

import java.util.List;

/**
 * A pending submission from a remote peer, awaiting governance review.
 * Groups all pending facts from one peer into a single reviewable unit.
 *
 * @param peerId    the submitting peer's ID
 * @param facts     the pending facts from this peer
 * @param factCount number of distinct (entity_id, attribute) changes
 * @param entityIds distinct entity IDs affected
 */
public record Submission(
    String peerId,
    List<FactStore.Fact> facts,
    int factCount,
    List<String> entityIds
) {}
