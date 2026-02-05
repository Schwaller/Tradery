package com.tradery.news.topic;

import java.util.List;
import java.util.Map;

/**
 * A topic in the classification taxonomy.
 */
public record Topic(
    String id,                          // e.g., "crypto", "crypto.defi"
    String name,                        // Display name
    List<String> keywords,              // Keywords that indicate this topic
    Map<String, Topic> subtopics        // Child topics
) {
    /**
     * Get the full path of this topic (e.g., "crypto.defi.lending")
     */
    public String path(String parentPath) {
        return parentPath.isEmpty() ? id : parentPath + "." + id;
    }

    /**
     * Check if text matches any keywords for this topic.
     */
    public boolean matches(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private List<String> keywords = List.of();
        private Map<String, Topic> subtopics = Map.of();

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder keywords(List<String> keywords) { this.keywords = keywords; return this; }
        public Builder subtopics(Map<String, Topic> subtopics) { this.subtopics = subtopics; return this; }

        public Topic build() {
            return new Topic(id, name, keywords, subtopics);
        }
    }
}
