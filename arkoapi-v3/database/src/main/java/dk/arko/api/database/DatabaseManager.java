package dk.arko.api.database;

import dk.arko.api.database.migration.MigrationManager;
import dk.arko.api.database.pool.ConnectionPool;
import dk.arko.api.database.transaction.TransactionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Central database manager that combines connection pooling, migrations,
 * transactions, and monitoring into a single entry point.
 *
 * Usage in your plugin:
 *   DatabaseManager db = new DatabaseManager(logger);
 *   db.connect(new PoolConfig().host("localhost").database("minecraft").username("mc").password("pass"));
 *   db.migrate("my_plugin");
 *   // Use db.pool(), db.transactions(), db.query()...
 */
public class DatabaseManager implements AutoCloseable {

    private final Logger logger;
    private final ConnectionPool pool;
    private final MigrationManager migrations;
    private final TransactionManager transactions;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
        this.pool = new ConnectionPool(logger);
        this.migrations = new MigrationManager(pool, logger);
        this.transactions = new TransactionManager(pool);
    }

    /**
     * Connect to the primary database.
     */
    public void connect(ConnectionPool.PoolConfig config) {
        pool.createPrimary(config);
        logger.info("[ArkoAPI DB] Connected to PostgreSQL at " + config.host + ":" + config.port + "/" + config.database);
    }

    /**
     * Create additional named pools.
     */
    public void createPool(String name, ConnectionPool.PoolConfig config) {
        pool.createPool(name, config);
    }

    /**
     * Run migrations for a plugin.
     */
    public void migrate(String pluginName) {
        migrations.migrate(pluginName);
    }

    /**
     * Run migrations with a custom path.
     */
    public void migrate(String pluginName, String path) {
        migrations.migrate(pluginName, path);
    }

    /**
     * Run migrations with a specific schema.
     */
    public void migrate(String pluginName, String path, String schema) {
        migrations.migrate(pluginName, path, schema);
    }

    /**
     * Get a connection from the primary pool.
     */
    public Connection getConnection() throws SQLException {
        return pool.getConnection();
    }

    /**
     * Get a connection from a named pool.
     */
    public Connection getConnection(String poolName) throws SQLException {
        return pool.getConnection(poolName);
    }

    /**
     * Access the connection pool directly.
     */
    public ConnectionPool pool() {
        return pool;
    }

    /**
     * Access the migration manager.
     */
    public MigrationManager migrations() {
        return migrations;
    }

    /**
     * Access the transaction manager.
     */
    public TransactionManager transactions() {
        return transactions;
    }

    /**
     * Log pool statistics.
     */
    public void logStats() {
        pool.logStats();
    }

    /**
     * Check if the database is reachable.
     */
    public boolean isHealthy() {
        try (Connection conn = pool.getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        pool.close();
        logger.info("[ArkoAPI DB] Database manager shut down");
    }
}
