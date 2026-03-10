package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeEscalation;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.store.ChallengeStore;

import java.util.List;

/**
 * Seeds the challenge store with default challenges if it's empty.
 */
public final class DefaultChallengeSeeder {

    private DefaultChallengeSeeder() {}

    public static void seedIfEmpty(ChallengeStore store) {
        if (!store.listChallenges().isEmpty()) return;

        List<ChallengeOutput.Field> sentimentFields = List.of(
            ChallengeOutput.Field.text("headline", "Headline", true),
            ChallengeOutput.Field.text("explanation", "Explanation"),
            ChallengeOutput.Field.score("sentiment", "Sentiment", -1.0, 1.0),
            ChallengeOutput.Field.score("confidence", "Confidence %", 0, 100)
        );

        // 1. US Markets
        Challenge c1 = new Challenge("us-markets", "US Markets");
        c1.setDescription("How are the US markets doing right now? Cover the S&P 500, Nasdaq, Dow Jones, "
            + "and any major movers. Include recent Fed policy, economic data releases, earnings season impact, "
            + "and key risks. What's the overall mood?");
        ChallengeOutput usOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        usOutput.setFields(sentimentFields);
        usOutput.setReasonDetail(ChallengeOutput.ReasonDetail.BRIEF);
        c1.setOutput(usOutput);
        c1.setEscalations(List.of(createEsc("Run", "standard", null, false, "US market analysis")));
        c1.setRefreshInterval(java.time.Duration.ofDays(1));
        c1.setDisplayOrder(1);
        store.saveChallenge(c1);

        // 2. Crypto Markets
        Challenge c2 = new Challenge("crypto-markets", "Crypto Markets");
        c2.setDescription("How are the crypto markets doing right now? Cover Bitcoin, Ethereum, "
            + "and the broader altcoin market. Include on-chain metrics, funding rates, exchange flows, "
            + "regulatory developments, and institutional activity. What's the overall mood?");
        ChallengeOutput cryptoOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        cryptoOutput.setFields(sentimentFields);
        cryptoOutput.setReasonDetail(ChallengeOutput.ReasonDetail.BRIEF);
        c2.setOutput(cryptoOutput);
        c2.setEscalations(List.of(createEsc("Run", "standard", null, false, "Crypto market analysis")));
        c2.setRefreshInterval(java.time.Duration.ofDays(1));
        c2.setDisplayOrder(2);
        store.saveChallenge(c2);

        // 3. Active Wars & Conflicts
        Challenge c3 = new Challenge("active-wars", "Active Wars & Conflicts");
        c3.setDescription("Survey all currently active wars and armed conflicts worldwide, "
            + "plus any that ended in the last 6 months. "
            + "Provide a headline summarizing the global situation, "
            + "list the high-intensity conflicts, medium-intensity conflicts, and low-intensity conflicts separately, "
            + "list any recently ended conflicts, and rate the overall global conflict intensity.");
        ChallengeOutput warsOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        warsOutput.setFields(List.of(
            ChallengeOutput.Field.text("headline", "Headline", true),
            ChallengeOutput.Field.text("high_intensity", "High Intensity"),
            ChallengeOutput.Field.text("medium_intensity", "Medium Intensity"),
            ChallengeOutput.Field.text("low_intensity", "Low Intensity"),
            ChallengeOutput.Field.text("recently_ended", "Recently Ended"),
            ChallengeOutput.Field.score("global_intensity", "Global Intensity", 0, 10)
        ));
        warsOutput.setReasonDetail(ChallengeOutput.ReasonDetail.BRIEF);
        c3.setOutput(warsOutput);
        c3.setEscalations(List.of(createEsc("Run", "standard", null, false, "Conflicts survey")));
        c3.setRefreshInterval(java.time.Duration.ofDays(7));
        c3.setDisplayOrder(3);
        store.saveChallenge(c3);

        // 4. War Market Impact (structured list mode)
        Challenge c4 = new Challenge("war-impact", "War Market Impact");
        c4.setDescription("For each currently active war or major armed conflict, estimate its market impact. "
            + "Consider direct and indirect effects on commodity prices, trade disruption, sanctions, "
            + "supply chain risks, and investor sentiment. "
            + "List ALL active conflicts you know about.");
        ChallengeOutput impactOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        impactOutput.setListMode(true);
        impactOutput.setListBehavior(ChallengeOutput.ListBehavior.TRACKING);
        impactOutput.setFields(List.of(
            ChallengeOutput.Field.text("name", "Conflict", true),
            ChallengeOutput.Field.score("intensity", "Intensity", 0, 10),
            ChallengeOutput.Field.score("oil_impact", "Oil Impact", -10, 10),
            ChallengeOutput.Field.score("us_impact", "US Market", -10, 10),
            ChallengeOutput.Field.score("eu_impact", "EU Market", -10, 10),
            ChallengeOutput.Field.score("asia_impact", "Asia Market", -10, 10)
        ));
        impactOutput.setReasonDetail(ChallengeOutput.ReasonDetail.BRIEF);
        c4.setOutput(impactOutput);
        c4.setEscalations(List.of(createEsc("Run", "standard", null, false, "War impact analysis")));
        c4.setRefreshInterval(java.time.Duration.ofDays(3));
        c4.setDisplayOrder(4);
        store.saveChallenge(c4);

        // 5. Meme Coins (structured list mode)
        Challenge c5 = new Challenge("meme-coins", "Meme Coins");
        c5.setDescription("Find the hippest and most talked-about meme coins right now. "
            + "Only include coins with a market cap of at least $1M. "
            + "Rank them by short-term potential (hype, momentum, community strength, catalysts). "
            + "Include the current market cap and highlight standout sizes.");
        ChallengeOutput memeOutput = new ChallengeOutput(ChallengeOutput.Type.STRUCTURED);
        memeOutput.setListMode(true);
        memeOutput.setListBehavior(ChallengeOutput.ListBehavior.SNAPSHOT);
        memeOutput.setFields(List.of(
            ChallengeOutput.Field.text("name", "Coin", true),
            ChallengeOutput.Field.text("ticker", "Ticker"),
            ChallengeOutput.Field.score("potential", "Potential", 0, 10),
            ChallengeOutput.Field.score("hype", "Hype", 0, 10),
            ChallengeOutput.Field.number("market_cap_m", "Mcap ($M)", 1, 50000),
            ChallengeOutput.Field.score("risk", "Risk", 0, 10)
        ));
        memeOutput.setReasonDetail(ChallengeOutput.ReasonDetail.BRIEF);
        c5.setOutput(memeOutput);
        c5.setEscalations(List.of(createEsc("Run", "standard", null, false, "Meme coin scanner")));
        c5.setRefreshInterval(java.time.Duration.ofDays(1));
        c5.setDisplayOrder(5);
        store.saveChallenge(c5);
    }

    private static ChallengeEscalation createEsc(String label, String tier, String pipeline,
                                                   boolean verify, String description) {
        ChallengeEscalation esc = new ChallengeEscalation(label, tier);
        esc.setPipeline(pipeline);
        esc.setVerify(verify);
        esc.setDescription(description);
        return esc;
    }
}
