package dk.arko.api.paper.plugin;

import dk.arko.api.common.cache.CacheManager;
import dk.arko.api.common.cooldown.CooldownManager;
import dk.arko.api.common.messaging.RedisMessenger;
import dk.arko.api.common.registry.ServiceRegistry;
import dk.arko.api.database.DatabaseManager;
import dk.arko.api.database.pool.ConnectionPool;
import dk.arko.api.paper.command.CommandManager;
import dk.arko.api.paper.dialog.DialogManager;
import dk.arko.api.paper.menu.MenuManager;
import dk.arko.api.paper.player.PlayerUtils;
import dk.arko.api.paper.task.PaperTaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for Paper plugins using ArkoAPI.
 * Provides automatic initialization of all API services.
 *
 * Usage:
 *   public class MyPlugin extends ArkoPlugin {
 *       @Override
 *       protected void onPluginEnable() {
 *           // Your enable logic
 *           db().migrate("my_plugin");
 *           commands().registerCommand(new MyCommand());
 *       }
 *
 *       @Override
 *       protected void onPluginDisable() {
 *           // Your disable logic
 *       }
 *   }
 */
public abstract class ArkoPlugin extends JavaPlugin implements Listener {

    private DatabaseManager databaseManager;
    private CommandManager commandManager;
    private PaperTaskScheduler taskScheduler;
    private CooldownManager cooldownManager;
    private CacheManager cacheManager;
    private RedisMessenger redisMessenger;

    @Override
    public final void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize core services
        taskScheduler = new PaperTaskScheduler(this);
        commandManager = new CommandManager(this);
        cooldownManager = new CooldownManager();
        cacheManager = new CacheManager();

        // Register menu system
        MenuManager.getInstance().register(this);
        DialogManager.getInstance().register(this);

        // Register cleanup listener
        getServer().getPluginManager().registerEvents(this, this);

        // Database (optional - only if configured)
        FileConfiguration config = getConfig();
        if (config.contains("database.host")) {
            databaseManager = new DatabaseManager(getLogger());
            ConnectionPool.PoolConfig poolConfig = new ConnectionPool.PoolConfig()
                    .host(config.getString("database.host", "localhost"))
                    .port(config.getInt("database.port", 5432))
                    .database(config.getString("database.database", "minecraft"))
                    .username(config.getString("database.username", "minecraft"))
                    .password(config.getString("database.password", ""))
                    .maxPoolSize(config.getInt("database.pool.max-size", 20))
                    .minIdle(config.getInt("database.pool.min-idle", 10));

            String schema = config.getString("database.schema");
            if (schema != null) poolConfig.schema(schema);

            databaseManager.connect(poolConfig);
        }

        // Redis (optional)
        if (config.contains("redis.uri")) {
            String redisUri = config.getString("redis.uri", "redis://localhost:6379");
            String serverId = config.getString("server-id", "server-1");
            redisMessenger = new RedisMessenger(redisUri, serverId, getLogger());

            // Heartbeat every 15 seconds
            taskScheduler.runAsyncRepeating(() -> redisMessenger.heartbeat(), 0, 300);
        }

        // Cooldown cleanup every 5 minutes
        taskScheduler.runAsyncRepeating(() -> cooldownManager.purgeExpired(), 6000, 6000);

        // Plugin-specific enable
        onPluginEnable();

        getLogger().info("[ArkoAPI] " + getName() + " v" + getPluginMeta().getVersion() + " loaded successfully");
    }

    @Override
    public final void onDisable() {
        onPluginDisable();

        // Cleanup
        taskScheduler.cancelAll();
        if (databaseManager != null) databaseManager.close();
        if (redisMessenger != null) redisMessenger.close();
        cacheManager.invalidateAll();

        getLogger().info("[ArkoAPI] " + getName() + " disabled");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerUtils.cleanup(event.getPlayer());
        cooldownManager.clearAll(event.getPlayer().getUniqueId());
    }

    // ─── Abstract Methods ──────────────────────────────────────

    protected abstract void onPluginEnable();
    protected abstract void onPluginDisable();

    // ─── Service Access ────────────────────────────────────────

    public DatabaseManager db() { return databaseManager; }
    public CommandManager commands() { return commandManager; }
    public PaperTaskScheduler scheduler() { return taskScheduler; }
    public CooldownManager cooldowns() { return cooldownManager; }
    public CacheManager cache() { return cacheManager; }
    public RedisMessenger redis() { return redisMessenger; }

    /**
     * Check if the database is configured and connected.
     */
    public boolean hasDatabase() { return databaseManager != null; }

    /**
     * Check if Redis is configured and connected.
     */
    public boolean hasRedis() { return redisMessenger != null; }
}
