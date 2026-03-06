package dk.arko.api.database.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Production-grade PostgreSQL connection pool manager using HikariCP.
 * Supports multiple named pools, monitoring, and network-optimized configuration.
 *
 * Default configuration is tuned for 1000+ concurrent players:
 * - Maximum pool size: 20 (covers heavy async DB load)
 * - Minimum idle: 10 (fast connection acquisition)
 * - Connection timeout: 5s (fail-fast on overload)
 * - Idle timeout: 10min
 * - Max lifetime: 30min (respects PG max_connections rotation)
 * - Leak detection: 30s
 */
public class ConnectionPool implements AutoCloseable {

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Logger logger;
    private HikariDataSource primaryPool;

    public ConnectionPool(Logger logger) {
        this.logger = logger;
    }

    // ─── Pool Creation ─────────────────────────────────────────

    /**
     * Create and register the primary connection pool.
     */
    public HikariDataSource createPrimary(PoolConfig config) {
        primaryPool = createPool("primary", config);
        return primaryPool;
    }

    /**
     * Create a named connection pool (for multi-database setups).
     */
    public HikariDataSource createPool(String name, PoolConfig config) {
        if (pools.containsKey(name)) {
            throw new IllegalStateException("Pool '" + name + "' already exists");
        }

        HikariConfig hikari = new HikariConfig();

        // Connection
        hikari.setJdbcUrl("jdbc:postgresql://" + config.host + ":" + config.port + "/" + config.database);
        hikari.setUsername(config.username);
        hikari.setPassword(config.password);
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setPoolName("ArkoAPI-" + name);

        // Pool sizing
        hikari.setMaximumPoolSize(config.maxPoolSize);
        hikari.setMinimumIdle(config.minIdle);

        // Timeouts
        hikari.setConnectionTimeout(config.connectionTimeoutMs);
        hikari.setIdleTimeout(config.idleTimeoutMs);
        hikari.setMaxLifetime(config.maxLifetimeMs);
        hikari.setKeepaliveTime(config.keepaliveTimeMs);
        hikari.setValidationTimeout(config.validationTimeoutMs);

        // Leak detection
        hikari.setLeakDetectionThreshold(config.leakDetectionMs);

        // PostgreSQL-specific optimizations
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "256");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");
        hikari.addDataSourceProperty("reWriteBatchedInserts", "true");
        hikari.addDataSourceProperty("ApplicationName", "ArkoAPI-" + name);

        // TCP keepalive
        hikari.addDataSourceProperty("tcpKeepAlive", "true");

        // Schema
        if (config.schema != null) {
            hikari.setSchema(config.schema);
            hikari.setConnectionInitSql("SET search_path TO " + config.schema + ",public");
        }

        HikariDataSource ds = new HikariDataSource(hikari);
        pools.put(name, ds);
        logger.info("[ArkoAPI] Created connection pool '" + name + "' (max=" + config.maxPoolSize + ", min=" + config.minIdle + ")");
        return ds;
    }

    // ─── Connection Access ─────────────────────────────────────

    /**
     * Get a connection from the primary pool.
     */
    public Connection getConnection() throws SQLException {
        if (primaryPool == null) throw new IllegalStateException("No primary pool configured");
        return primaryPool.getConnection();
    }

    /**
     * Get a connection from a named pool.
     */
    public Connection getConnection(String poolName) throws SQLException {
        HikariDataSource ds = pools.get(poolName);
        if (ds == null) throw new IllegalStateException("Pool '" + poolName + "' not found");
        return ds.getConnection();
    }

    /**
     * Get the primary data source.
     */
    public HikariDataSource getPrimaryDataSource() {
        return primaryPool;
    }

    /**
     * Get a named data source.
     */
    public HikariDataSource getDataSource(String name) {
        return pools.get(name);
    }

    // ─── Monitoring ────────────────────────────────────────────

    /**
     * Get pool statistics.
     */
    public PoolStats getStats(String name) {
        HikariDataSource ds = pools.get(name);
        if (ds == null) return null;
        var pool = ds.getHikariPoolMXBean();
        if (pool == null) return null;
        return new PoolStats(
                name,
                pool.getTotalConnections(),
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getThreadsAwaitingConnection()
        );
    }

    public PoolStats getPrimaryStats() {
        return getStats("primary");
    }

    /**
     * Log all pool stats.
     */
    public void logStats() {
        pools.keySet().forEach(name -> {
            PoolStats stats = getStats(name);
            if (stats != null) {
                logger.info(String.format("[ArkoAPI Pool '%s'] total=%d, active=%d, idle=%d, waiting=%d",
                        stats.name, stats.total, stats.active, stats.idle, stats.waiting));
            }
        });
    }

    // ─── Lifecycle ─────────────────────────────────────────────

    @Override
    public void close() {
        pools.values().forEach(ds -> {
            if (!ds.isClosed()) ds.close();
        });
        pools.clear();
        primaryPool = null;
        logger.info("[ArkoAPI] All connection pools closed");
    }

    /**
     * Close a specific pool.
     */
    public void closePool(String name) {
        HikariDataSource ds = pools.remove(name);
        if (ds != null && !ds.isClosed()) ds.close();
    }

    // ─── Inner Classes ─────────────────────────────────────────

    /**
     * Pool configuration with sensible defaults for a large network.
     */
    public static class PoolConfig {
        public String host = "localhost";
        public int port = 5432;
        public String database = "minecraft";
        public String username = "minecraft";
        public String password = "";
        public String schema = null;

        public int maxPoolSize = 20;
        public int minIdle = 10;

        public long connectionTimeoutMs = 5_000;
        public long idleTimeoutMs = 600_000;       // 10 min
        public long maxLifetimeMs = 1_800_000;     // 30 min
        public long keepaliveTimeMs = 300_000;     // 5 min
        public long validationTimeoutMs = 3_000;
        public long leakDetectionMs = 30_000;

        public PoolConfig() {}

        public PoolConfig host(String host) { this.host = host; return this; }
        public PoolConfig port(int port) { this.port = port; return this; }
        public PoolConfig database(String database) { this.database = database; return this; }
        public PoolConfig username(String username) { this.username = username; return this; }
        public PoolConfig password(String password) { this.password = password; return this; }
        public PoolConfig schema(String schema) { this.schema = schema; return this; }
        public PoolConfig maxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; return this; }
        public PoolConfig minIdle(int minIdle) { this.minIdle = minIdle; return this; }
        public PoolConfig connectionTimeout(long ms) { this.connectionTimeoutMs = ms; return this; }
        public PoolConfig idleTimeout(long ms) { this.idleTimeoutMs = ms; return this; }
        public PoolConfig maxLifetime(long ms) { this.maxLifetimeMs = ms; return this; }
        public PoolConfig leakDetection(long ms) { this.leakDetectionMs = ms; return this; }

        /**
         * Preset: Small server (<100 players).
         */
        public static PoolConfig small() {
            PoolConfig c = new PoolConfig();
            c.maxPoolSize = 5;
            c.minIdle = 2;
            return c;
        }

        /**
         * Preset: Medium server (100-500 players).
         */
        public static PoolConfig medium() {
            PoolConfig c = new PoolConfig();
            c.maxPoolSize = 10;
            c.minIdle = 5;
            return c;
        }

        /**
         * Preset: Large network (500-2000 players).
         */
        public static PoolConfig large() {
            return new PoolConfig(); // Defaults are tuned for this
        }

        /**
         * Preset: Massive network (2000+ players).
         */
        public static PoolConfig massive() {
            PoolConfig c = new PoolConfig();
            c.maxPoolSize = 30;
            c.minIdle = 15;
            c.connectionTimeoutMs = 3_000;
            return c;
        }
    }

    public record PoolStats(String name, int total, int active, int idle, int waiting) {}
}
