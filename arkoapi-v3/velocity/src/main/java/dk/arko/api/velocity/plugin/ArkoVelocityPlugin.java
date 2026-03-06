package dk.arko.api.velocity.plugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import dk.arko.api.common.cache.CacheManager;
import dk.arko.api.common.cooldown.CooldownManager;
import dk.arko.api.common.messaging.RedisMessenger;
import dk.arko.api.database.DatabaseManager;
import dk.arko.api.database.pool.ConnectionPool;
import dk.arko.api.velocity.command.VelocityCommandManager;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Base class for Velocity plugins using ArkoAPI.
 *
 * Usage:
 *   @Plugin(id = "myplugin", name = "MyPlugin", version = "1.0")
 *   public class MyPlugin extends ArkoVelocityPlugin {
 *       @Inject
 *       public MyPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
 *           super(server, logger, dataDir);
 *       }
 *
 *       @Override
 *       protected void onProxyEnable() { ... }
 *
 *       @Override
 *       protected void onProxyDisable() { ... }
 *   }
 */
public abstract class ArkoVelocityPlugin {

    protected final ProxyServer server;
    protected final Logger logger;
    protected final Path dataDirectory;

    private DatabaseManager databaseManager;
    private VelocityCommandManager commandManager;
    private CooldownManager cooldownManager;
    private CacheManager cacheManager;
    private RedisMessenger redisMessenger;

    protected ArkoVelocityPlugin(ProxyServer server, Logger logger, Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Initialize services
        commandManager = new VelocityCommandManager(server, this);
        cooldownManager = new CooldownManager();
        cacheManager = new CacheManager();

        // Register disconnect cleanup
        server.getEventManager().register(this, DisconnectEvent.class,
                e -> cooldownManager.clearAll(e.getPlayer().getUniqueId()));

        onProxyEnable();
        logger.info("[ArkoAPI-Velocity] Plugin initialized");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        onProxyDisable();
        if (databaseManager != null) databaseManager.close();
        if (redisMessenger != null) redisMessenger.close();
        cacheManager.invalidateAll();
        logger.info("[ArkoAPI-Velocity] Plugin shut down");
    }

    // ─── Abstract ──────────────────────────────────────────────

    protected abstract void onProxyEnable();
    protected abstract void onProxyDisable();

    // ─── Database Setup ────────────────────────────────────────

    /**
     * Initialize the database connection.
     */
    protected void initDatabase(ConnectionPool.PoolConfig config) {
        databaseManager = new DatabaseManager(logger);
        databaseManager.connect(config);
    }

    /**
     * Initialize Redis messaging.
     */
    protected void initRedis(String redisUri, String serverId) {
        redisMessenger = new RedisMessenger(redisUri, serverId, logger);
    }

    // ─── Service Access ────────────────────────────────────────

    public ProxyServer proxy() { return server; }
    public DatabaseManager db() { return databaseManager; }
    public VelocityCommandManager commands() { return commandManager; }
    public CooldownManager cooldowns() { return cooldownManager; }
    public CacheManager cache() { return cacheManager; }
    public RedisMessenger redis() { return redisMessenger; }
    public boolean hasDatabase() { return databaseManager != null; }
    public boolean hasRedis() { return redisMessenger != null; }
}
