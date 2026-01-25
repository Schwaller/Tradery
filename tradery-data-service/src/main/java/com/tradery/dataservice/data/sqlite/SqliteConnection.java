package com.tradery.dataservice.data.sqlite;

import com.tradery.dataservice.data.DataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SQLite database connections with WAL mode for concurrent reads.
 * One database file per symbol (e.g., ~/.tradery/data/BTCUSDT.db)
 */
public class SqliteConnection {

    private static final Logger log = LoggerFactory.getLogger(SqliteConnection.class);

    // Connection pool - one connection per symbol
    private static final Map<String, SqliteConnection> instances = new ConcurrentHashMap<>();

    private final String symbol;
    private final File dbFile;
    private Connection connection;
    private final Object lock = new Object();

    private SqliteConnection(String symbol) {
        this.symbol = symbol;
        File dataDir = DataConfig.getInstance().getDataDir();
        this.dbFile = new File(dataDir, symbol + ".db");
    }

    /**
     * Get or create a connection for a symbol.
     */
    public static SqliteConnection forSymbol(String symbol) {
        return instances.computeIfAbsent(symbol, SqliteConnection::new);
    }

    /**
     * Get the database file path.
     */
    public File getDbFile() {
        return dbFile;
    }

    /**
     * Get or create the SQLite connection with WAL mode.
     *
     * NOTE: This method is NOT synchronized to avoid blocking readers.
     * SQLite with WAL mode supports concurrent reads natively.
     * Only writes are serialized via executeInTransaction().
     */
    public Connection getConnection() throws SQLException {
        // Quick check without lock for common case
        Connection conn = connection;
        if (conn != null && !conn.isClosed()) {
            return conn;
        }

        // Slow path: create connection with synchronization
        synchronized (lock) {
            // Double-check after acquiring lock
            if (connection == null || connection.isClosed()) {
                connection = createConnection();
            }
            return connection;
        }
    }

    /**
     * Create a new connection with optimal settings.
     */
    private Connection createConnection() throws SQLException {
        // Ensure parent directory exists
        File parentDir = dbFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // Configure connection with pragmas for performance
        Connection conn = DriverManager.getConnection(url);

        try (Statement stmt = conn.createStatement()) {
            // WAL mode for concurrent reads during writes
            stmt.execute("PRAGMA journal_mode=WAL");

            // Synchronous NORMAL - good balance of safety and speed
            stmt.execute("PRAGMA synchronous=NORMAL");

            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys=ON");

            // 64MB cache size for better read performance
            stmt.execute("PRAGMA cache_size=-65536");

            // Memory-map up to 128MB for faster reads
            stmt.execute("PRAGMA mmap_size=134217728");

            // Increase page size for better compression ratio
            // Only takes effect on new databases
            stmt.execute("PRAGMA page_size=4096");
        }

        log.debug("Created SQLite connection for {} at {}", symbol, dbFile.getAbsolutePath());
        return conn;
    }

    /**
     * Execute a function within a transaction.
     * Automatically commits on success, rolls back on failure.
     */
    public <T> T executeInTransaction(TransactionFunction<T> function) throws SQLException {
        Connection conn = getConnection();
        synchronized (lock) {
            boolean autoCommitOriginal = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = function.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    log.warn("Rollback failed: {}", rollbackEx.getMessage());
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Execute a void function within a transaction.
     */
    public void executeInTransaction(TransactionConsumer consumer) throws SQLException {
        executeInTransaction(conn -> {
            consumer.accept(conn);
            return null;
        });
    }

    /**
     * Close the connection for this symbol.
     */
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                    log.debug("Closed SQLite connection for {}", symbol);
                } catch (SQLException e) {
                    log.warn("Error closing connection for {}: {}", symbol, e.getMessage());
                }
                connection = null;
            }
        }
    }

    /**
     * Close all connections (call on application shutdown).
     */
    public static void closeAll() {
        for (SqliteConnection conn : instances.values()) {
            conn.close();
        }
        instances.clear();
    }

    /**
     * Check if the database exists for this symbol.
     */
    public boolean exists() {
        return dbFile.exists();
    }

    /**
     * Get the symbol associated with this connection.
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Functional interface for transactional operations returning a value.
     */
    @FunctionalInterface
    public interface TransactionFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /**
     * Functional interface for transactional operations with no return value.
     */
    @FunctionalInterface
    public interface TransactionConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
