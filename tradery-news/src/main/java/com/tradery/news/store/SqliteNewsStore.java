package com.tradery.news.store;

import com.tradery.news.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of NewsStore.
 */
public class SqliteNewsStore implements NewsStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqliteNewsStore.class);

    private final Connection conn;

    public SqliteNewsStore(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
            log.info("Opened news database at {}", dbPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open database: " + dbPath, e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Articles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS articles (
                    id TEXT PRIMARY KEY,
                    source_url TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    source_name TEXT,
                    title TEXT NOT NULL,
                    content TEXT,
                    author TEXT,
                    summary TEXT,
                    importance TEXT DEFAULT 'MEDIUM',
                    sentiment_score REAL DEFAULT 0,
                    published_at INTEGER NOT NULL,
                    fetched_at INTEGER NOT NULL,
                    processed_at INTEGER,
                    status TEXT DEFAULT 'PENDING'
                )
                """);

            // Article topics (many-to-many)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS article_topics (
                    article_id TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    PRIMARY KEY (article_id, topic),
                    FOREIGN KEY (article_id) REFERENCES articles(id)
                )
                """);

            // Article coins
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS article_coins (
                    article_id TEXT NOT NULL,
                    coin TEXT NOT NULL,
                    PRIMARY KEY (article_id, coin),
                    FOREIGN KEY (article_id) REFERENCES articles(id)
                )
                """);

            // Article categories
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS article_categories (
                    article_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    PRIMARY KEY (article_id, category),
                    FOREIGN KEY (article_id) REFERENCES articles(id)
                )
                """);

            // Article tags
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS article_tags (
                    article_id TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY (article_id, tag),
                    FOREIGN KEY (article_id) REFERENCES articles(id)
                )
                """);

            // Events
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    start_date INTEGER,
                    end_date INTEGER,
                    sentiment_score REAL DEFAULT 0,
                    impact_score REAL DEFAULT 0,
                    story_id TEXT,
                    created_at INTEGER NOT NULL
                )
                """);

            // Entities
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entities (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    symbol TEXT,
                    description TEXT,
                    created_at INTEGER NOT NULL
                )
                """);

            // Entity aliases
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS entity_aliases (
                    entity_id TEXT NOT NULL,
                    alias TEXT NOT NULL,
                    PRIMARY KEY (entity_id, alias),
                    FOREIGN KEY (entity_id) REFERENCES entities(id)
                )
                """);

            // Relationships
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS relationships (
                    id TEXT PRIMARY KEY,
                    source_id TEXT NOT NULL,
                    target_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    strength REAL DEFAULT 1.0,
                    context TEXT,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (source_id) REFERENCES entities(id),
                    FOREIGN KEY (target_id) REFERENCES entities(id)
                )
                """);

            // Stories
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stories (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    summary TEXT,
                    type TEXT DEFAULT 'BREAKING',
                    first_seen INTEGER NOT NULL,
                    last_updated INTEGER NOT NULL,
                    peak_time INTEGER,
                    article_count INTEGER DEFAULT 1,
                    avg_sentiment REAL DEFAULT 0,
                    importance TEXT DEFAULT 'MEDIUM',
                    is_active INTEGER DEFAULT 1
                )
                """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_articles_published ON articles(published_at DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_articles_source ON articles(source_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_articles_importance ON articles(importance)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_topics_topic ON article_topics(topic)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_article_coins_coin ON article_coins(coin)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_type ON events(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_entities_symbol ON entities(symbol)");
        }
    }

    // === Articles ===

    @Override
    public void saveArticle(Article article) {
        String sql = """
            INSERT OR REPLACE INTO articles
            (id, source_url, source_id, source_name, title, content, author, summary,
             importance, sentiment_score, published_at, fetched_at, processed_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, article.id());
            ps.setString(2, article.sourceUrl());
            ps.setString(3, article.sourceId());
            ps.setString(4, article.sourceName());
            ps.setString(5, article.title());
            ps.setString(6, article.content());
            ps.setString(7, article.author());
            ps.setString(8, article.summary());
            ps.setString(9, article.importance().name());
            ps.setDouble(10, article.sentimentScore());
            ps.setLong(11, article.publishedAt().toEpochMilli());
            ps.setLong(12, article.fetchedAt().toEpochMilli());
            ps.setObject(13, article.processedAt() != null ? article.processedAt().toEpochMilli() : null);
            ps.setString(14, article.status().name());
            ps.executeUpdate();

            // Save related data
            saveArticleList("article_topics", "topic", article.id(), article.topics());
            saveArticleList("article_coins", "coin", article.id(), article.coins());
            saveArticleList("article_categories", "category", article.id(), article.categories());
            saveArticleList("article_tags", "tag", article.id(), article.tags());

        } catch (SQLException e) {
            log.error("Failed to save article {}: {}", article.id(), e.getMessage());
        }
    }

    private void saveArticleList(String table, String column, String articleId, List<String> values) throws SQLException {
        // Delete existing
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE article_id = ?")) {
            ps.setString(1, articleId);
            ps.executeUpdate();
        }

        // Insert new
        if (values != null && !values.isEmpty()) {
            String sql = "INSERT INTO " + table + " (article_id, " + column + ") VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String value : values) {
                    ps.setString(1, articleId);
                    ps.setString(2, value);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public Optional<Article> getArticle(String id) {
        String sql = "SELECT * FROM articles WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapArticle(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get article {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Article> getArticles(ArticleQuery query) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT a.* FROM articles a");
        List<Object> params = new ArrayList<>();

        // Joins for filtering
        if (query.topics() != null && !query.topics().isEmpty()) {
            sql.append(" JOIN article_topics t ON a.id = t.article_id");
        }
        if (query.coins() != null && !query.coins().isEmpty()) {
            sql.append(" JOIN article_coins c ON a.id = c.article_id");
        }

        sql.append(" WHERE 1=1");

        if (query.topics() != null && !query.topics().isEmpty()) {
            sql.append(" AND t.topic IN (").append("?,".repeat(query.topics().size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(query.topics());
        }

        if (query.coins() != null && !query.coins().isEmpty()) {
            sql.append(" AND c.coin IN (").append("?,".repeat(query.coins().size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            params.addAll(query.coins());
        }

        if (query.importance() != null && !query.importance().isEmpty()) {
            sql.append(" AND a.importance IN (").append("?,".repeat(query.importance().size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            query.importance().forEach(i -> params.add(i.name()));
        }

        if (query.since() != null) {
            sql.append(" AND a.published_at >= ?");
            params.add(query.since());
        }

        if (query.until() != null) {
            sql.append(" AND a.published_at <= ?");
            params.add(query.until());
        }

        sql.append(" ORDER BY a.published_at DESC LIMIT ?");
        params.add(query.limit());

        List<Article> articles = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                articles.add(mapArticle(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to query articles: {}", e.getMessage());
        }
        return articles;
    }

    @Override
    public boolean articleExists(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM articles WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    private Article mapArticle(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return Article.builder()
            .id(id)
            .sourceUrl(rs.getString("source_url"))
            .sourceId(rs.getString("source_id"))
            .sourceName(rs.getString("source_name"))
            .title(rs.getString("title"))
            .content(rs.getString("content"))
            .author(rs.getString("author"))
            .summary(rs.getString("summary"))
            .importance(ImportanceLevel.valueOf(rs.getString("importance")))
            .sentimentScore(rs.getDouble("sentiment_score"))
            .publishedAt(Instant.ofEpochMilli(rs.getLong("published_at")))
            .fetchedAt(Instant.ofEpochMilli(rs.getLong("fetched_at")))
            .processedAt(rs.getObject("processed_at") != null
                ? Instant.ofEpochMilli(rs.getLong("processed_at")) : null)
            .status(ProcessingStatus.valueOf(rs.getString("status")))
            .topics(getArticleList("article_topics", "topic", id))
            .coins(getArticleList("article_coins", "coin", id))
            .categories(getArticleList("article_categories", "category", id))
            .tags(getArticleList("article_tags", "tag", id))
            .eventIds(List.of())
            .entityIds(List.of())
            .build();
    }

    private List<String> getArticleList(String table, String column, String articleId) {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " + column + " FROM " + table + " WHERE article_id = ?")) {
            ps.setString(1, articleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Failed to get {} for article {}: {}", table, articleId, e.getMessage());
        }
        return values;
    }

    // === Events ===

    @Override
    public void saveEvent(NewsEvent event) {
        String sql = """
            INSERT OR REPLACE INTO events
            (id, type, title, description, start_date, end_date, sentiment_score, impact_score, story_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.id());
            ps.setString(2, event.type().name());
            ps.setString(3, event.title());
            ps.setString(4, event.description());
            ps.setObject(5, event.startDate() != null ? event.startDate().toEpochMilli() : null);
            ps.setObject(6, event.endDate() != null ? event.endDate().toEpochMilli() : null);
            ps.setDouble(7, event.sentimentScore());
            ps.setDouble(8, event.impactScore());
            ps.setString(9, event.storyId());
            ps.setLong(10, event.createdAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save event {}: {}", event.id(), e.getMessage());
        }
    }

    @Override
    public Optional<NewsEvent> getEvent(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM events WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapEvent(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get event {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<NewsEvent> getEvents(EventQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.types() != null && !query.types().isEmpty()) {
            sql.append(" AND type IN (").append("?,".repeat(query.types().size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            query.types().forEach(t -> params.add(t.name()));
        }

        if (query.since() != null) {
            sql.append(" AND start_date >= ?");
            params.add(query.since());
        }

        sql.append(" ORDER BY start_date DESC LIMIT ?");
        params.add(query.limit());

        List<NewsEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                events.add(mapEvent(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to query events: {}", e.getMessage());
        }
        return events;
    }

    private NewsEvent mapEvent(ResultSet rs) throws SQLException {
        return NewsEvent.builder()
            .id(rs.getString("id"))
            .type(EventType.valueOf(rs.getString("type")))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .startDate(rs.getObject("start_date") != null
                ? Instant.ofEpochMilli(rs.getLong("start_date")) : null)
            .endDate(rs.getObject("end_date") != null
                ? Instant.ofEpochMilli(rs.getLong("end_date")) : null)
            .sentimentScore(rs.getDouble("sentiment_score"))
            .impactScore(rs.getDouble("impact_score"))
            .storyId(rs.getString("story_id"))
            .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
            .articleIds(List.of())
            .entityIds(List.of())
            .build();
    }

    // === Entities ===

    @Override
    public void saveEntity(Entity entity) {
        String sql = """
            INSERT OR REPLACE INTO entities (id, type, name, symbol, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.id());
            ps.setString(2, entity.type().name());
            ps.setString(3, entity.name());
            ps.setString(4, entity.symbol());
            ps.setString(5, entity.description());
            ps.setLong(6, entity.createdAt().toEpochMilli());
            ps.executeUpdate();

            // Save aliases
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM entity_aliases WHERE entity_id = ?")) {
                del.setString(1, entity.id());
                del.executeUpdate();
            }
            if (entity.aliases() != null && !entity.aliases().isEmpty()) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO entity_aliases (entity_id, alias) VALUES (?, ?)")) {
                    for (String alias : entity.aliases()) {
                        ins.setString(1, entity.id());
                        ins.setString(2, alias);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to save entity {}: {}", entity.id(), e.getMessage());
        }
    }

    @Override
    public Optional<Entity> getEntity(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM entities WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapEntity(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get entity {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Entity> getEntities(EntityQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM entities WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.types() != null && !query.types().isEmpty()) {
            sql.append(" AND type IN (").append("?,".repeat(query.types().size()));
            sql.setLength(sql.length() - 1);
            sql.append(")");
            query.types().forEach(t -> params.add(t.name()));
        }

        if (query.search() != null && !query.search().isBlank()) {
            sql.append(" AND (name LIKE ? OR symbol LIKE ?)");
            params.add("%" + query.search() + "%");
            params.add("%" + query.search() + "%");
        }

        sql.append(" ORDER BY name LIMIT ?");
        params.add(query.limit());

        List<Entity> entities = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entities.add(mapEntity(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to query entities: {}", e.getMessage());
        }
        return entities;
    }

    private Entity mapEntity(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return Entity.builder()
            .id(id)
            .type(EntityType.valueOf(rs.getString("type")))
            .name(rs.getString("name"))
            .symbol(rs.getString("symbol"))
            .description(rs.getString("description"))
            .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
            .aliases(getEntityAliases(id))
            .build();
    }

    private List<String> getEntityAliases(String entityId) {
        List<String> aliases = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT alias FROM entity_aliases WHERE entity_id = ?")) {
            ps.setString(1, entityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                aliases.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Failed to get aliases for entity {}: {}", entityId, e.getMessage());
        }
        return aliases;
    }

    // === Relationships ===

    @Override
    public void saveRelationship(Relationship rel) {
        String sql = """
            INSERT OR REPLACE INTO relationships (id, source_id, target_id, type, strength, context, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rel.id());
            ps.setString(2, rel.sourceId());
            ps.setString(3, rel.targetId());
            ps.setString(4, rel.type().name());
            ps.setDouble(5, rel.strength());
            ps.setString(6, rel.context());
            ps.setLong(7, rel.createdAt().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save relationship {}: {}", rel.id(), e.getMessage());
        }
    }

    @Override
    public List<Relationship> getRelationshipsFor(String entityId) {
        List<Relationship> rels = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM relationships WHERE source_id = ? OR target_id = ?")) {
            ps.setString(1, entityId);
            ps.setString(2, entityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rels.add(Relationship.builder()
                    .id(rs.getString("id"))
                    .sourceId(rs.getString("source_id"))
                    .targetId(rs.getString("target_id"))
                    .type(RelationType.valueOf(rs.getString("type")))
                    .strength(rs.getDouble("strength"))
                    .context(rs.getString("context"))
                    .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
                    .build());
            }
        } catch (SQLException e) {
            log.error("Failed to get relationships for {}: {}", entityId, e.getMessage());
        }
        return rels;
    }

    // === Stories ===

    @Override
    public void saveStory(Story story) {
        String sql = """
            INSERT OR REPLACE INTO stories
            (id, title, summary, type, first_seen, last_updated, peak_time, article_count, avg_sentiment, importance, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, story.id());
            ps.setString(2, story.title());
            ps.setString(3, story.summary());
            ps.setString(4, story.type().name());
            ps.setLong(5, story.firstSeen().toEpochMilli());
            ps.setLong(6, story.lastUpdated().toEpochMilli());
            ps.setObject(7, story.peakTime() != null ? story.peakTime().toEpochMilli() : null);
            ps.setInt(8, story.articleCount());
            ps.setDouble(9, story.avgSentiment());
            ps.setString(10, story.importance().name());
            ps.setInt(11, story.isActive() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save story {}: {}", story.id(), e.getMessage());
        }
    }

    @Override
    public Optional<Story> getStory(String id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM stories WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapStory(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get story {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<Story> getActiveStories() {
        List<Story> stories = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM stories WHERE is_active = 1 ORDER BY last_updated DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                stories.add(mapStory(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get active stories: {}", e.getMessage());
        }
        return stories;
    }

    private Story mapStory(ResultSet rs) throws SQLException {
        return Story.builder()
            .id(rs.getString("id"))
            .title(rs.getString("title"))
            .summary(rs.getString("summary"))
            .type(StoryType.valueOf(rs.getString("type")))
            .firstSeen(Instant.ofEpochMilli(rs.getLong("first_seen")))
            .lastUpdated(Instant.ofEpochMilli(rs.getLong("last_updated")))
            .peakTime(rs.getObject("peak_time") != null
                ? Instant.ofEpochMilli(rs.getLong("peak_time")) : null)
            .articleCount(rs.getInt("article_count"))
            .avgSentiment(rs.getDouble("avg_sentiment"))
            .importance(ImportanceLevel.valueOf(rs.getString("importance")))
            .isActive(rs.getInt("is_active") == 1)
            .articleIds(List.of())
            .coins(List.of())
            .topics(List.of())
            .tags(List.of())
            .eventIds(List.of())
            .build();
    }

    // === Stats ===

    public int getArticleCount() {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM articles");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<TopicCount> getTopicCounts() {
        List<TopicCount> counts = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT topic, COUNT(*) as cnt FROM article_topics GROUP BY topic ORDER BY cnt DESC");
            while (rs.next()) {
                counts.add(new TopicCount(rs.getString("topic"), rs.getInt("cnt")));
            }
        } catch (SQLException e) {
            log.error("Failed to get topic counts: {}", e.getMessage());
        }
        return counts;
    }

    public record TopicCount(String topic, int count) {}

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                log.info("Closed news database");
            }
        } catch (SQLException e) {
            log.error("Error closing database: {}", e.getMessage());
        }
    }
}
