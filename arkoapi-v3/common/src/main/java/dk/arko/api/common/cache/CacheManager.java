package dk.arko.api.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Multi-layer caching system using Caffeine. Supports:
 * - L1: In-memory Caffeine cache
 * - L2: Redis cache (optional, via RedisMessenger)
 * - Automatic loading, expiry, and eviction
 *
 * Optimized for 1000+ concurrent player data caching.
 */
public class CacheManager {

    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    /**
     * Create a simple cache with TTL.
     */
    public <K, V> Cache<K, V> createCache(String name, Duration expireAfterWrite, long maxSize) {
        Cache<K, V> cache = Caffeine.newBuilder()
                .expireAfterWrite(expireAfterWrite)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        caches.put(name, cache);
        return cache;
    }

    /**
     * Create a cache with TTL after access (resets on read).
     */
    public <K, V> Cache<K, V> createAccessCache(String name, Duration expireAfterAccess, long maxSize) {
        Cache<K, V> cache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccess)
                .maximumSize(maxSize)
                .recordStats()
                .build();
        caches.put(name, cache);
        return cache;
    }

    /**
     * Create a self-loading cache that auto-loads missing values.
     */
    public <K, V> LoadingCache<K, V> createLoadingCache(String name, Duration expireAfterWrite,
                                                         long maxSize, Function<K, V> loader) {
        LoadingCache<K, V> cache = Caffeine.newBuilder()
                .expireAfterWrite(expireAfterWrite)
                .maximumSize(maxSize)
                .recordStats()
                .build(loader::apply);
        caches.put(name, cache);
        return cache;
    }

    /**
     * Create a cache for player data (UUID -> T).
     * Default: 10min TTL, 2000 max size.
     */
    public <V> Cache<java.util.UUID, V> createPlayerCache(String name) {
        return createCache(name, Duration.ofMinutes(10), 2000);
    }

    /**
     * Create a large player cache (for 1000+ concurrent).
     */
    public <V> Cache<java.util.UUID, V> createLargePlayerCache(String name) {
        return createCache(name, Duration.ofMinutes(15), 5000);
    }

    /**
     * Get a named cache.
     */
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> getCache(String name) {
        return Optional.ofNullable((Cache<K, V>) caches.get(name));
    }

    /**
     * Invalidate all caches.
     */
    public void invalidateAll() {
        caches.values().forEach(Cache::invalidateAll);
    }

    /**
     * Get stats for a named cache.
     */
    public String getStats(String name) {
        Cache<?, ?> cache = caches.get(name);
        if (cache == null) return "Cache not found: " + name;
        var stats = cache.stats();
        return String.format("Cache '%s': hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, size=%d",
                name, stats.hitCount(), stats.missCount(),
                stats.hitRate() * 100, stats.evictionCount(), cache.estimatedSize());
    }

    /**
     * Get stats for all caches.
     */
    public Map<String, String> getAllStats() {
        Map<String, String> allStats = new java.util.LinkedHashMap<>();
        caches.keySet().forEach(name -> allStats.put(name, getStats(name)));
        return allStats;
    }
}
