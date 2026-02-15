package com.tradery.ai.pipeline.config;

import com.tradery.ai.AiClient;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Resolves an abstract tier name (fast/standard/premium) to an available AiProfile
 * by walking the tier's fallback chain.
 */
public class TierResolver {

    private static final Logger log = LoggerFactory.getLogger(TierResolver.class);

    private final Map<String, List<String>> tiers;
    private final AiClient aiClient;

    public TierResolver(Map<String, List<String>> tiers) {
        this.tiers = tiers;
        this.aiClient = AiClient.getInstance();
    }

    /**
     * Resolve a tier name to the first available AiProfile from the fallback chain.
     * Returns null if no profile in the chain is available.
     */
    public AiProfile resolve(String tierName) {
        List<String> chain = tiers.get(tierName);
        if (chain == null || chain.isEmpty()) {
            log.warn("Unknown tier '{}', falling back to default profile", tierName);
            return AiConfig.get().getDefaultProfile();
        }

        AiConfig config = AiConfig.get();
        for (String profileId : chain) {
            AiProfile profile = config.getProfile(profileId);
            if (profile != null && aiClient.isAvailable(profile)) {
                log.debug("Tier '{}' resolved to profile '{}'", tierName, profileId);
                return profile;
            }
        }

        log.warn("No available profile for tier '{}', chain: {}", tierName, chain);
        // Last resort: try the default profile
        AiProfile defaultProfile = config.getDefaultProfile();
        if (defaultProfile != null) {
            log.debug("Tier '{}' falling back to default profile '{}'", tierName, defaultProfile.getId());
            return defaultProfile;
        }
        return null;
    }
}
