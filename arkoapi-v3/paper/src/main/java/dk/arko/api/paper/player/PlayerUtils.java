package dk.arko.api.paper.player;

import dk.arko.api.common.text.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive player utility class for titles, actionbar, bossbar,
 * sounds, particles, and more.
 */
public final class PlayerUtils {

    private static final Map<UUID, Map<String, BossBar>> activeBossBars = new ConcurrentHashMap<>();

    private PlayerUtils() {}

    // ─── Titles ────────────────────────────────────────────────

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(TextUtil.parse(title), TextUtil.parse(subtitle), times));
    }

    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    public static void sendTitle(Player player, Component title, Component subtitle) {
        player.showTitle(Title.title(title, subtitle));
    }

    public static void clearTitle(Player player) { player.clearTitle(); }

    // ─── Action Bar ────────────────────────────────────────────

    public static void sendActionBar(Player player, String miniMessage) {
        player.sendActionBar(TextUtil.parse(miniMessage));
    }

    public static void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    public static void sendActionBar(Player player, String miniMessage, Map<String, String> placeholders) {
        player.sendActionBar(TextUtil.parse(miniMessage, placeholders));
    }

    // ─── Boss Bars ─────────────────────────────────────────────

    public static BossBar showBossBar(Player player, String id, String title, float progress,
                                       BossBar.Color color, BossBar.Overlay overlay) {
        hideBossBar(player, id);
        BossBar bar = BossBar.bossBar(TextUtil.parse(title), Math.max(0, Math.min(1, progress)), color, overlay);
        player.showBossBar(bar);
        activeBossBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(id, bar);
        return bar;
    }

    public static BossBar showBossBar(Player player, String id, String title, float progress) {
        return showBossBar(player, id, title, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    public static void updateBossBar(Player player, String id, String title, float progress) {
        Map<String, BossBar> bars = activeBossBars.get(player.getUniqueId());
        if (bars == null) return;
        BossBar bar = bars.get(id);
        if (bar == null) return;
        bar.name(TextUtil.parse(title));
        bar.progress(Math.max(0, Math.min(1, progress)));
    }

    public static void updateBossBarProgress(Player player, String id, float progress) {
        Map<String, BossBar> bars = activeBossBars.get(player.getUniqueId());
        if (bars == null) return;
        BossBar bar = bars.get(id);
        if (bar != null) bar.progress(Math.max(0, Math.min(1, progress)));
    }

    public static void hideBossBar(Player player, String id) {
        Map<String, BossBar> bars = activeBossBars.get(player.getUniqueId());
        if (bars == null) return;
        BossBar bar = bars.remove(id);
        if (bar != null) player.hideBossBar(bar);
    }

    public static void hideAllBossBars(Player player) {
        Map<String, BossBar> bars = activeBossBars.remove(player.getUniqueId());
        if (bars != null) bars.values().forEach(player::hideBossBar);
    }

    // ─── Sounds ────────────────────────────────────────────────

    public static void playSound(Player player, org.bukkit.Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void playSound(Player player, org.bukkit.Sound sound) {
        playSound(player, sound, 1.0f, 1.0f);
    }

    public static void playSound(Player player, String soundKey, float volume, float pitch) {
        player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.MASTER, volume, pitch));
    }

    public static void playSoundSuccess(Player player) {
        playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    public static void playSoundError(Player player) {
        playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    public static void playSoundClick(Player player) {
        playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    public static void playSoundLevelUp(Player player) {
        playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public static void playSoundNotification(Player player) {
        playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

    // ─── Particles ─────────────────────────────────────────────

    public static void spawnParticle(Player player, Particle particle, Location location, int count,
                                      double offsetX, double offsetY, double offsetZ, double speed) {
        player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
    }

    public static void spawnParticle(Player player, Particle particle, Location location, int count) {
        spawnParticle(player, particle, location, count, 0.2, 0.2, 0.2, 0.05);
    }

    public static void spawnParticleCircle(Player player, Particle particle, Location center,
                                            double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location point = new Location(center.getWorld(), x, center.getY(), z);
            player.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    public static void spawnParticleLine(Player player, Particle particle, Location from, Location to, int points) {
        double dx = (to.getX() - from.getX()) / points;
        double dy = (to.getY() - from.getY()) / points;
        double dz = (to.getZ() - from.getZ()) / points;
        for (int i = 0; i <= points; i++) {
            Location point = from.clone().add(dx * i, dy * i, dz * i);
            player.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    public static void spawnDustParticle(Player player, Location location, Color color, float size, int count) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        player.spawnParticle(Particle.DUST, location, count, 0.2, 0.2, 0.2, 0, dust);
    }

    // ─── Messages ──────────────────────────────────────────────

    public static void sendMessage(Player player, String miniMessage) {
        player.sendMessage(TextUtil.parse(miniMessage));
    }

    public static void sendMessage(Player player, String miniMessage, Map<String, String> placeholders) {
        player.sendMessage(TextUtil.parse(miniMessage, placeholders));
    }

    public static void sendMessage(Player player, Component message) {
        player.sendMessage(message);
    }

    /**
     * Send a centered message (approximate for Minecraft chat).
     */
    public static void sendCenteredMessage(Player player, String miniMessage) {
        player.sendMessage(TextUtil.parse(TextUtil.centerText(miniMessage, 80)));
    }

    // ─── Utility ───────────────────────────────────────────────

    /**
     * Teleport with a title.
     */
    public static void teleportWithTitle(Player player, Location location, String title, String subtitle) {
        player.teleportAsync(location).thenAccept(success -> {
            if (success) sendTitle(player, title, subtitle, 5, 40, 10);
        });
    }

    /**
     * Clean up all player state (call on quit).
     */
    public static void cleanup(Player player) {
        hideAllBossBars(player);
    }
}
