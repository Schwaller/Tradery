package com.tradery.news.store;

import com.tradery.news.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for news data.
 */
public interface NewsStore {

    // Articles
    void saveArticle(Article article);
    Optional<Article> getArticle(String id);
    List<Article> getArticles(ArticleQuery query);
    boolean articleExists(String id);

    // Events
    void saveEvent(NewsEvent event);
    Optional<NewsEvent> getEvent(String id);
    List<NewsEvent> getEvents(EventQuery query);

    // Entities
    void saveEntity(Entity entity);
    Optional<Entity> getEntity(String id);
    List<Entity> getEntities(EntityQuery query);

    // Relationships
    void saveRelationship(Relationship relationship);
    List<Relationship> getRelationshipsFor(String entityId);

    // Stories
    void saveStory(Story story);
    Optional<Story> getStory(String id);
    List<Story> getActiveStories();

    // Query records
    record ArticleQuery(
        List<String> coins,
        List<String> topics,
        List<ImportanceLevel> importance,
        Long since,
        Long until,
        int limit
    ) {
        public static ArticleQuery all(int limit) {
            return new ArticleQuery(null, null, null, null, null, limit);
        }
    }

    record EventQuery(
        List<EventType> types,
        List<String> entityIds,
        Long since,
        int limit
    ) {}

    record EntityQuery(
        List<EntityType> types,
        String search,
        int limit
    ) {}
}
