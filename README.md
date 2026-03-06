# ArkoAPI v3 — Comprehensive Minecraft Server Network API

A production-grade, multi-module Java API for Minecraft server networks running **Paper** and **Velocity**. Designed for **1000+ concurrent players** with PostgreSQL, Redis, Oraxen integration, and a massive set of utilities.

---

## Modules

| Module | Description |
|--------|-------------|
| `arkoapi-common` | Shared utilities: text, cooldowns, caching, messaging, events, config, registry, validation |
| `arkoapi-database` | PostgreSQL connection pooling (HikariCP), fluent query builder, migrations (Flyway), repository pattern, transactions |
| `arkoapi-paper` | Paper plugin framework: menus (9+ types), commands, dialogs, items, tasks (Folia-safe), player utilities |
| `arkoapi-velocity` | Velocity proxy framework: commands, player sessions, server transfers, messaging |
| `arkoapi-oraxen` | Oraxen wrapper: custom items, fonts/glyphs, GLSL text effects, custom sounds |

---

## Quick Start

### Depend on ArkoAPI

```xml
<!-- In your plugin's pom.xml -->
<dependency>
    <groupId>dk.arko</groupId>
    <artifactId>arkoapi-paper</artifactId>
    <version>3.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Paper Plugin

```java
public class MyPlugin extends ArkoPlugin {

    @Override
    protected void onPluginEnable() {
        // Auto-connects to DB from config.yml
        db().migrate("my_plugin");

        // Register commands
        commands().registerCommand(new RankCommand());

        // Done! All services available via db(), scheduler(), redis(), etc.
    }

    @Override
    protected void onPluginDisable() {
        // Cleanup handled automatically
    }
}
```

### Velocity Plugin

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0")
public class MyPlugin extends ArkoVelocityPlugin {

    @Inject
    public MyPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDir) {
        super(server, logger, dataDir);
    }

    @Override
    protected void onProxyEnable() {
        initDatabase(new PoolConfig().host("localhost").database("minecraft")
                .username("mc").password("pass"));
        initRedis("redis://localhost:6379", "proxy-1");
        commands().registerCommand(new ServerCommand());
    }

    @Override
    protected void onProxyDisable() {}
}
```

---

## Feature Reference

### 1. Database — `arkoapi-database`

#### Connection Pooling
```java
// Auto-configured from config.yml, or manual:
ConnectionPool.PoolConfig config = PoolConfig.large()  // Preset for 500-2000 players
    .host("db.arkomc.dk").database("prison").username("mc").password("secret");
db().connect(config);

// Multiple pools for different databases:
db().createPool("analytics", PoolConfig.small().database("analytics"));
```

#### Fluent Query Builder
```java
// SELECT with joins, ordering, pagination
List<PlayerData> topPlayers = QueryBuilder.select("players")
    .columns("uuid", "name", "balance")
    .where("balance", ">", 1000)
    .whereNotNull("last_login")
    .orderBy("balance", false)
    .limit(10)
    .executeAsync(db().pool(), row -> new PlayerData(
        (UUID) row.get("uuid"),
        (String) row.get("name"),
        ((Number) row.get("balance")).longValue()
    )).join();

// UPSERT (INSERT ON CONFLICT UPDATE)
QueryBuilder.upsert("players", "uuid")
    .value("uuid", player.getUniqueId())
    .value("name", player.getName())
    .value("balance", 1000)
    .value("last_login", new Timestamp(System.currentTimeMillis()))
    .executeUpdateAsync(db().pool());

// Batch insert (1000s of rows efficiently)
QueryBuilder.batchUpsert(db().pool(), "player_stats",
    List.of("uuid", "stat_key", "value"),
    List.of("uuid", "stat_key"),
    rows
);
```

#### Repository Pattern
```java
public class PlayerRepository extends Repository<UUID, PlayerData> {
    public PlayerRepository(ConnectionPool pool) {
        super(pool, "players", "uuid");
    }

    @Override
    protected PlayerData fromRow(Map<String, Object> row) {
        return new PlayerData((UUID) row.get("uuid"), (String) row.get("name"),
                ((Number) row.get("balance")).longValue());
    }

    @Override
    protected Map<String, Object> toRow(PlayerData entity) {
        return Map.of("uuid", entity.uuid(), "name", entity.name(), "balance", entity.balance());
    }

    @Override
    protected UUID getId(PlayerData entity) { return entity.uuid(); }
}

// Usage:
PlayerRepository repo = new PlayerRepository(db().pool());
repo.saveAsync(playerData);                        // Upsert
repo.findByIdAsync(uuid);                          // Find by UUID
repo.findPage(0, 20, "balance", false);            // Paginated leaderboard
repo.saveBatchAsync(List.of(p1, p2, p3));          // Batch save
```

#### Migrations (Flyway)
```sql
-- Place in: src/main/resources/db/migrations/myplugin/V1__create_tables.sql
CREATE TABLE IF NOT EXISTS players (
    uuid       UUID PRIMARY KEY,
    name       VARCHAR(16) NOT NULL,
    balance    BIGINT DEFAULT 0,
    last_login TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_players_balance ON players(balance DESC);
```

```java
db().migrate("myplugin"); // Runs all pending migrations
```

#### Transactions
```java
db().transactions().executeAsync(conn -> {
    // All statements in one transaction — auto-commits or rolls back
    PreparedStatement ps1 = conn.prepareStatement("UPDATE players SET balance = balance - ? WHERE uuid = ?");
    ps1.setLong(1, amount);
    ps1.setObject(2, sender);
    ps1.executeUpdate();

    PreparedStatement ps2 = conn.prepareStatement("UPDATE players SET balance = balance + ? WHERE uuid = ?");
    ps2.setLong(1, amount);
    ps2.setObject(2, receiver);
    ps2.executeUpdate();
});
```

---

### 2. Menu System — 9+ Menu Types

```java
// ═══ Standard Chest Menu ═══
Menu menu = Menus.chest("<gradient:#FF6B6B:#4ECDC4>Min Menu", 3);
menu.setItem(13, ItemBuilder.of(Material.DIAMOND)
    .name("<aqua>Klik mig!")
    .lore("<gray>En fantastisk genstand")
    .glow()
    .build(),
    ctx -> {
        ctx.player().sendMessage(TextUtil.parse("<green>Du klikkede!"));
        PlayerUtils.playSoundClick(ctx.player());
    });
menu.fillBorder(ItemBuilder.filler());
menu.open(player);

// ═══ Paginated Menu (auto-navigation) ═══
PaginatedMenuImpl shop = Menus.paginated("<gold>Butik", 6);
shop.setContentSlots(10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34);
shop.setItems(shopItems);  // Can be hundreds of items
shop.fillBorder(ItemBuilder.filler());
shop.open(player);         // Auto-generates pages with navigation

// ═══ Category/Tabbed Menu ═══
CategoryMenuImpl catMenu = Menus.category("<yellow>Kosmetik", 6);
catMenu.addCategory("Nametags", nametagIcon, nametagItems);
catMenu.addCategory("Badges", badgeIcon, badgeItems);
catMenu.addCategory("Farver", colorIcon, colorItems);
catMenu.open(player);

// ═══ Confirmation Dialog ═══
Menus.confirmation("<red>Slet alt?",
    ctx -> { deleteAll(); ctx.close(); },
    ctx -> ctx.close()
).open(player);

// ═══ Other Types ═══
Menus.scrolling("Logs", 6);          // Vertical/horizontal scrolling
Menus.animated("Effects", 3);        // Frame-based animation
Menus.filterable("Alle Items", 6);   // Search/filter
Menus.hopper("Hurtig Menu");         // 5-slot hopper
Menus.dispenser("3x3 Gitter");      // 3x3 dispenser

// ═══ Pattern-Based Layout ═══
menu.setPattern(new String[]{
    "XOOOOOOOX",
    "O       O",
    "O       O",
    "XOOOOOOOX"
}, Map.of('X', MenuItem.of(redGlass), 'O', MenuItem.of(grayGlass)));
```

---

### 3. Command System

```java
@Command(name = "rank", description = "Rank system", aliases = {"r"})
public class RankCommand {

    @Default
    public void help(Player sender) {
        sender.sendMessage(TextUtil.parse("<gold>Rank kommandoer:"));
        sender.sendMessage(TextUtil.parse("<yellow>/rank set <spiller> <rank>"));
        sender.sendMessage(TextUtil.parse("<yellow>/rank info <spiller>"));
    }

    @SubCommand(name = "set", description = "Sæt en spillers rank")
    @Permission("rank.set")
    @Cooldown(3000)  // 3 second cooldown
    public void setRank(Player sender,
                        @Arg("spiller") Player target,
                        @Arg("rank") @TabComplete({"member", "vip", "mvp", "admin"}) String rank) {
        // Set rank logic
        sender.sendMessage(TextUtil.parse("<green>Rank sat til " + rank + " for " + target.getName()));
    }

    @SubCommand(name = "info", description = "Se en spillers rank")
    @Async  // Runs off main thread
    public void info(Player sender, @Arg("spiller") @Optional Player target) {
        Player check = target != null ? target : sender;
        // Async DB lookup...
    }
}

// Register:
commands().registerCommand(new RankCommand());
```

---

### 4. Dialog API (Paper 1.21.6+)

```java
DialogBuilder.create("ban_wizard")
    .title("<red>Ban Wizard")
    .page("input")
        .body("<gray>Udfyld information om ban:")
        .textInput("reason", "Grund", "Indtast grund...")
        .dropdown("duration", "Varighed", List.of("1 dag", "7 dage", "30 dage", "Permanent"))
        .checkbox("ip_ban", "IP Ban")
    .page("confirm")
        .body("<yellow>Bekræft ban?")
        .submitButton("Bekræft Ban")
        .cancelButton("Annuller")
    .onSubmit((player, data) -> {
        String reason = data.getString("reason");
        String duration = data.getString("duration");
        boolean ipBan = data.getBoolean("ip_ban");
        // Process ban...
    })
    .onCancel(player -> player.sendMessage(TextUtil.parse("<gray>Ban annulleret.")))
    .open(player);
```

---

### 5. Cross-Server Messaging (Redis)

```java
// Publishing
redis().publish("bans", new BanMessage(uuid, reason, duration));
redis().publishTo("lobby-1", "transfers", new TransferMessage(uuid, "prison"));

// Subscribing
redis().subscribe("bans", BanMessage.class, ctx -> {
    BanMessage ban = ctx.data();
    // Handle ban on this server
    Bukkit.getPlayer(ban.uuid()).ifPresent(p -> p.kick(TextUtil.parse("<red>Du er banned!")));
});

// Key-Value store
redis().setObject("player:" + uuid + ":data", playerData, 3600);  // 1hr TTL
PlayerData data = redis().getObject("player:" + uuid + ":data", PlayerData.class);
```

---

### 6. Oraxen Integration

```java
OraxenAPI oraxen = OraxenAPI.get();

// Items
ItemStack sword = oraxen.items().get("flame_sword");
oraxen.items().give(player, "diamond_pickaxe_skin", 1);
boolean isOraxen = oraxen.items().isOraxenItem(heldItem);

// Glyphs/Fonts
Component coinIcon = oraxen.fonts().glyph("coin_icon");
Component text = oraxen.fonts().glyphText("coin_icon", "<gold>500 mønter");
Component negSpace = oraxen.fonts().negativeSpace(-8);

// Text Effects (GLSL shader)
oraxen.effects().registerEffect("rainbow", "Regnbue", "effects.rainbow", 1, "Farver");
oraxen.effects().registerEffect("fire", "Ild", "effects.fire", 2, "Elementer");
oraxen.effects().setPlayerEffect(player.getUniqueId(), "rainbow");
Component effectText = oraxen.effects().createEffectComponent("Louis", "rainbow");

// Sounds
oraxen.sounds().registerSound("levelup", "custom.levelup");
oraxen.sounds().playSound(player, "levelup");
```

---

### 7. Player Utilities

```java
// Titles, actionbar, sounds
PlayerUtils.sendTitle(player, "<gold>Velkommen!", "<gray>til ArkoMC", 10, 70, 20);
PlayerUtils.sendActionBar(player, "<green>+50 mønter");
PlayerUtils.playSoundLevelUp(player);

// Boss bars (tracked by ID for updates/removal)
PlayerUtils.showBossBar(player, "mine_progress", "<yellow>Mine Level 5", 0.75f,
        BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
PlayerUtils.updateBossBarProgress(player, "mine_progress", 0.80f);
PlayerUtils.hideBossBar(player, "mine_progress");

// Particles
PlayerUtils.spawnParticleCircle(player, Particle.FLAME, player.getLocation(), 2.0, 30);
PlayerUtils.spawnDustParticle(player, location, Color.RED, 1.5f, 10);

// Item building
ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
    .name("<gradient:#FF0000:#FFD700>Flamme Sværd")
    .lore("<gray>Legendarisk våben", "", "<red>❤ +50 Skade", "<gold>✦ Fire Aspect III")
    .enchant(Enchantment.SHARPNESS, 5)
    .unbreakable()
    .glow()
    .persistentString(myKey, "legendary")
    .build();
```

---

### 8. Task Scheduling (Folia-Safe)

```java
// Simple tasks
scheduler().runAsync(() -> savePlayerData(player));
scheduler().runSyncLater(() -> spawnParticles(), 20);  // 1 second delay
scheduler().runAsyncRepeating(() -> syncLeaderboard(), 0, 6000);  // Every 5 min

// Folia-aware entity/location tasks
scheduler().runForEntity(player, () -> player.setHealth(20));
scheduler().runAtLocation(location, () -> placeBlock());

// Task chains
scheduler().chain()
    .async(() -> data = loadFromDB())
    .sync(() -> applyData(player, data))
    .delay(20)
    .sync(() -> showWelcomeTitle(player))
    .execute();
```

---

### 9. Caching

```java
// Player data cache (auto-eviction, stats tracking)
Cache<UUID, PlayerData> playerCache = cache().createPlayerCache("player_data");
playerCache.put(uuid, data);
PlayerData cached = playerCache.getIfPresent(uuid);

// Self-loading cache
LoadingCache<UUID, PlayerStats> statsCache = cache().createLoadingCache(
    "player_stats", Duration.ofMinutes(10), 2000,
    uuid -> loadStatsFromDB(uuid)
);
PlayerStats stats = statsCache.get(uuid);  // Auto-loads if missing

// Cache statistics
cache().getAllStats().forEach((name, stats) -> logger.info(stats));
```

---

### 10. Velocity Features

```java
// Server transfers
players().sendToServer(player, "prison", "<green>Teleporterer...", "<gray>til Prison");
players().transferAll("old-lobby", "new-lobby");
players().getLeastPopulated("lobby-1", "lobby-2", "lobby-3");

// Session data (persists across server switches)
PlayerSession session = players().getSession(player);
session.set("last_game", "bedwars");
String lastGame = session.get("last_game");

// Broadcasting
players().broadcast("<red>[ALERT] <white>Server genstart om 5 minutter!");
players().broadcastServer("prison", "<gold>Ny sæson starter nu!");
```

---

## Architecture

```
arkoapi-v3/
├── pom.xml                          # Parent POM with all dependency management
├── common/                          # Platform-independent utilities
│   └── src/main/java/dk/arko/api/common/
│       ├── cache/CacheManager       # Caffeine-based multi-layer caching
│       ├── config/ConfigManager     # JSON config with typed access
│       ├── cooldown/CooldownManager # Thread-safe per-player cooldowns
│       ├── events/EventBus          # Internal event system with priorities
│       ├── messaging/
│       │   ├── RedisMessenger       # Redis pub/sub + key-value store
│       │   └── PluginMessenger      # Plugin messaging channel abstraction
│       ├── registry/
│       │   ├── Registry             # Generic typed registry
│       │   └── ServiceRegistry      # Service locator / DI container
│       ├── scheduling/TaskScheduler # Platform-agnostic task interface
│       ├── text/TextUtil            # MiniMessage, gradients, templates
│       ├── tuple/Pair, Triple       # Immutable tuple types
│       └── validation/Validator     # Fluent input validation
├── database/                        # PostgreSQL everything
│   └── src/main/java/dk/arko/api/database/
│       ├── DatabaseManager          # Central facade
│       ├── pool/ConnectionPool      # HikariCP with presets & monitoring
│       ├── query/QueryBuilder       # Fluent SQL with async & batch
│       ├── migration/MigrationManager # Flyway per-plugin migrations
│       ├── repository/Repository    # Generic CRUD + pagination
│       └── transaction/TransactionManager # Isolation levels & savepoints
├── paper/                           # Paper server module
│   └── src/main/java/dk/arko/api/paper/
│       ├── plugin/ArkoPlugin        # Base plugin class
│       ├── menu/                    # 9+ menu types
│       │   ├── Menu, MenuItem, MenuClickContext, MenuManager, Menus
│       │   └── types/ (Chest, Paginated, Category, Scrolling, Animated,
│       │              Confirmation, Filterable, Hopper, Dispenser)
│       ├── command/CommandManager    # Annotation-based commands
│       ├── dialog/DialogBuilder     # Paper 1.21.6+ Dialog API wrapper
│       ├── item/ItemBuilder         # Fluent ItemStack builder
│       ├── player/PlayerUtils       # Titles, bossbars, particles, sounds
│       └── task/PaperTaskScheduler  # Folia-compatible scheduling
├── velocity/                        # Velocity proxy module
│   └── src/main/java/dk/arko/api/velocity/
│       ├── plugin/ArkoVelocityPlugin # Base velocity plugin class
│       ├── command/VelocityCommandManager # Annotation-based commands
│       └── player/VelocityPlayerManager   # Sessions, transfers, messaging
└── oraxen/                          # Oraxen integration module
    └── src/main/java/dk/arko/api/oraxen/
        └── OraxenAPI                # Items, fonts, effects, sounds
```

---

## Performance Tuning

### Pool Sizing Guide

| Network Size | `max-pool-size` | `min-idle` | Preset |
|---|---|---|---|
| < 100 players | 5 | 2 | `PoolConfig.small()` |
| 100-500 | 10 | 5 | `PoolConfig.medium()` |
| 500-2000 | 20 | 10 | `PoolConfig.large()` (default) |
| 2000+ | 30 | 15 | `PoolConfig.massive()` |

### Best Practices
- Always use `async` operations for database calls
- Use `batchUpsert` for saving multiple entities
- Use `LoadingCache` for frequently accessed data
- Use Redis pub/sub for cross-server communication instead of polling
- Use `PaperTaskScheduler.runForEntity()` on Folia servers
- Pre-compile message templates with `TextUtil.template()` for high-throughput formatting

---

## Build

```bash
mvn clean package
```

Output JARs:
- `paper/target/arkoapi-paper-3.0.0-SNAPSHOT.jar` — Paper plugin
- `velocity/target/arkoapi-velocity-3.0.0-SNAPSHOT.jar` — Velocity plugin

---

**Built for ArkoMC** 🇩🇰
