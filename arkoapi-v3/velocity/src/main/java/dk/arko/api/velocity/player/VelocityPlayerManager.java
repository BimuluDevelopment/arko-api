package dk.arko.api.velocity.player;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dk.arko.api.common.text.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity player management with session tracking, server transfers,
 * messaging, and cross-server utilities.
 */
public class VelocityPlayerManager {

    private final ProxyServer proxy;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public VelocityPlayerManager(ProxyServer proxy) {
        this.proxy = proxy;
    }

    // ─── Session Management ────────────────────────────────────

    public PlayerSession getSession(UUID uuid) {
        return sessions.computeIfAbsent(uuid, PlayerSession::new);
    }

    public PlayerSession getSession(Player player) {
        return getSession(player.getUniqueId());
    }

    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    // ─── Server Transfer ───────────────────────────────────────

    /**
     * Send a player to a server.
     */
    public CompletableFuture<Boolean> sendToServer(Player player, String serverName) {
        Optional<RegisteredServer> server = proxy.getServer(serverName);
        if (server.isEmpty()) return CompletableFuture.completedFuture(false);
        return player.createConnectionRequest(server.get()).connectWithIndication();
    }

    /**
     * Send a player to a server with a title notification.
     */
    public CompletableFuture<Boolean> sendToServer(Player player, String serverName, String title, String subtitle) {
        player.showTitle(Title.title(
                TextUtil.parse(title), TextUtil.parse(subtitle),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(500))));
        return sendToServer(player, serverName);
    }

    /**
     * Send all players on a server to another server.
     */
    public void transferAll(String fromServer, String toServer) {
        proxy.getServer(fromServer).ifPresent(from ->
                from.getPlayersConnected().forEach(p -> sendToServer(p, toServer)));
    }

    /**
     * Get the server a player is currently on.
     */
    public Optional<String> getCurrentServer(Player player) {
        return player.getCurrentServer().map(s -> s.getServerInfo().getName());
    }

    /**
     * Get the server a player is on by UUID.
     */
    public Optional<String> getCurrentServer(UUID uuid) {
        return proxy.getPlayer(uuid).flatMap(p -> p.getCurrentServer().map(s -> s.getServerInfo().getName()));
    }

    // ─── Messaging ─────────────────────────────────────────────

    /**
     * Send a message to a player by UUID.
     */
    public void sendMessage(UUID uuid, String miniMessage) {
        proxy.getPlayer(uuid).ifPresent(p -> p.sendMessage(TextUtil.parse(miniMessage)));
    }

    /**
     * Send a message to all players.
     */
    public void broadcast(String miniMessage) {
        Component msg = TextUtil.parse(miniMessage);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(msg));
    }

    /**
     * Send a message to all players on a specific server.
     */
    public void broadcastServer(String serverName, String miniMessage) {
        Component msg = TextUtil.parse(miniMessage);
        proxy.getServer(serverName).ifPresent(s ->
                s.getPlayersConnected().forEach(p -> p.sendMessage(msg)));
    }

    /**
     * Send a title to a player.
     */
    public void sendTitle(Player player, String title, String subtitle) {
        player.showTitle(Title.title(TextUtil.parse(title), TextUtil.parse(subtitle)));
    }

    /**
     * Send an action bar to a player.
     */
    public void sendActionBar(Player player, String miniMessage) {
        player.sendActionBar(TextUtil.parse(miniMessage));
    }

    // ─── Utility ───────────────────────────────────────────────

    /**
     * Get player count on a specific server.
     */
    public int getServerPlayerCount(String serverName) {
        return proxy.getServer(serverName).map(s -> s.getPlayersConnected().size()).orElse(0);
    }

    /**
     * Get total online player count.
     */
    public int getTotalPlayerCount() {
        return proxy.getPlayerCount();
    }

    /**
     * Find the least populated server from a list.
     */
    public Optional<String> getLeastPopulated(String... serverNames) {
        return Arrays.stream(serverNames)
                .filter(name -> proxy.getServer(name).isPresent())
                .min(Comparator.comparingInt(this::getServerPlayerCount));
    }

    /**
     * Check if a player is online anywhere on the network.
     */
    public boolean isOnline(UUID uuid) {
        return proxy.getPlayer(uuid).isPresent();
    }

    /**
     * Get a player by name.
     */
    public Optional<Player> getPlayer(String name) {
        return proxy.getPlayer(name);
    }

    /**
     * Get a player by UUID.
     */
    public Optional<Player> getPlayer(UUID uuid) {
        return proxy.getPlayer(uuid);
    }

    // ─── Session Class ─────────────────────────────────────────

    /**
     * Player session data that persists across server switches.
     */
    public static class PlayerSession {
        private final UUID uuid;
        private final long loginTime;
        private final Map<String, Object> data = new ConcurrentHashMap<>();
        private String lastServer;
        private String previousServer;

        PlayerSession(UUID uuid) {
            this.uuid = uuid;
            this.loginTime = System.currentTimeMillis();
        }

        public UUID getUuid() { return uuid; }
        public long getLoginTime() { return loginTime; }
        public long getSessionDuration() { return System.currentTimeMillis() - loginTime; }

        public void set(String key, Object value) { data.put(key, value); }
        @SuppressWarnings("unchecked")
        public <T> T get(String key) { return (T) data.get(key); }
        public <T> T get(String key, T defaultValue) {
            @SuppressWarnings("unchecked") T val = (T) data.get(key);
            return val != null ? val : defaultValue;
        }
        public boolean has(String key) { return data.containsKey(key); }
        public void remove(String key) { data.remove(key); }

        public void setServer(String server) {
            this.previousServer = this.lastServer;
            this.lastServer = server;
        }
        public String getLastServer() { return lastServer; }
        public String getPreviousServer() { return previousServer; }
    }
}
