package dk.arko.api.common.cooldown;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cooldown manager supporting categories, per-player cooldowns,
 * and automatic expiry. Optimized for 1000+ concurrent players.
 */
public class CooldownManager {

    private final Map<String, Map<UUID, Instant>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Check if a player is on cooldown for a given category.
     */
    public boolean isOnCooldown(UUID player, String category) {
        Map<UUID, Instant> categoryMap = cooldowns.get(category);
        if (categoryMap == null) return false;
        Instant expiry = categoryMap.get(player);
        if (expiry == null) return false;
        if (Instant.now().isAfter(expiry)) {
            categoryMap.remove(player);
            return false;
        }
        return true;
    }

    /**
     * Set a cooldown for a player.
     */
    public void setCooldown(UUID player, String category, Duration duration) {
        cooldowns.computeIfAbsent(category, k -> new ConcurrentHashMap<>())
                .put(player, Instant.now().plus(duration));
    }

    /**
     * Get remaining cooldown time.
     */
    public Duration getRemaining(UUID player, String category) {
        Map<UUID, Instant> categoryMap = cooldowns.get(category);
        if (categoryMap == null) return Duration.ZERO;
        Instant expiry = categoryMap.get(player);
        if (expiry == null) return Duration.ZERO;
        Duration remaining = Duration.between(Instant.now(), expiry);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Get remaining time in seconds.
     */
    public long getRemainingSeconds(UUID player, String category) {
        return getRemaining(player, category).getSeconds();
    }

    /**
     * Remove a cooldown.
     */
    public void removeCooldown(UUID player, String category) {
        Map<UUID, Instant> categoryMap = cooldowns.get(category);
        if (categoryMap != null) categoryMap.remove(player);
    }

    /**
     * Clear all cooldowns for a player.
     */
    public void clearAll(UUID player) {
        cooldowns.values().forEach(map -> map.remove(player));
    }

    /**
     * Clear all cooldowns for a category.
     */
    public void clearCategory(String category) {
        cooldowns.remove(category);
    }

    /**
     * Try to use a cooldown - returns true if NOT on cooldown (and sets it).
     * This is the most common usage pattern.
     */
    public boolean tryUse(UUID player, String category, Duration duration) {
        if (isOnCooldown(player, category)) return false;
        setCooldown(player, category, duration);
        return true;
    }

    /**
     * Purge all expired cooldowns (call periodically for memory cleanup).
     */
    public void purgeExpired() {
        Instant now = Instant.now();
        cooldowns.values().forEach(map -> map.entrySet().removeIf(e -> now.isAfter(e.getValue())));
        cooldowns.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Get total tracked cooldowns (for monitoring).
     */
    public int size() {
        return cooldowns.values().stream().mapToInt(Map::size).sum();
    }
}
