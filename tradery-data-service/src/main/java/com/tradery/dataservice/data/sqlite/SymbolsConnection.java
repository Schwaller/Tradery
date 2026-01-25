package com.tradery.dataservice.data.sqlite;

import com.tradery.dataservice.data.DataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite connection for the symbols database.
 * Single database file: ~/.tradery/symbols.db
 */
public class SymbolsConnection {

    private static final Logger log = LoggerFactory.getLogger(SymbolsConnection.class);

    private static volatile SymbolsConnection instance;
    private static final Object LOCK = new Object();

    private final File dbFile;
    private Connection connection;
    private final Object connLock = new Object();

    private SymbolsConnection() {
        File dataDir = DataConfig.getInstance().getDataDir();
        this.dbFile = new File(dataDir, "symbols.db");
    }

    /**
     * Get the singleton instance.
     */
    public static SymbolsConnection getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new SymbolsConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Get the database file path.
     */
    public File getDbFile() {
        return dbFile;
    }

    /**
     * Get or create the SQLite connection with WAL mode.
     */
    public Connection getConnection() throws SQLException {
        // Quick check without lock
        Connection conn = connection;
        if (conn != null && !conn.isClosed()) {
            return conn;
        }

        // Slow path: create connection
        synchronized (connLock) {
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
        Connection conn = DriverManager.getConnection(url);

        try (Statement stmt = conn.createStatement()) {
            // WAL mode for concurrent reads
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA cache_size=-32768"); // 32MB cache
            stmt.execute("PRAGMA mmap_size=67108864"); // 64MB memory-map
            stmt.execute("PRAGMA page_size=4096");
        }

        log.debug("Created SQLite connection for symbols.db at {}", dbFile.getAbsolutePath());
        return conn;
    }

    /**
     * Execute a function within a transaction.
     */
    public <T> T executeInTransaction(TransactionFunction<T> function) throws SQLException {
        Connection conn = getConnection();
        synchronized (connLock) {
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
     * Close the connection.
     */
    public void close() {
        synchronized (connLock) {
            if (connection != null) {
                try {
                    connection.close();
                    log.debug("Closed SQLite connection for symbols.db");
                } catch (SQLException e) {
                    log.warn("Error closing symbols.db connection: {}", e.getMessage());
                }
                connection = null;
            }
        }
    }

    /**
     * Check if the database exists.
     */
    public boolean exists() {
        return dbFile.exists();
    }

    /**
     * Initialize the database schema.
     */
    public void initializeSchema() throws SQLException {
        SymbolsSchema.initialize(getConnection());
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
