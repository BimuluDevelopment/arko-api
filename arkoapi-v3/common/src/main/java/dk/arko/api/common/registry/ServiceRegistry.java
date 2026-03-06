package dk.arko.api.common.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple service locator for registering and resolving services across the API.
 * Thread-safe singleton pattern.
 */
public final class ServiceRegistry {

    private static final ServiceRegistry INSTANCE = new ServiceRegistry();
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    private ServiceRegistry() {}

    public static ServiceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a service implementation.
     */
    public <T> void register(Class<T> type, T implementation) {
        services.put(type, implementation);
    }

    /**
     * Resolve a service by type.
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        T service = (T) services.get(type);
        if (service == null) {
            throw new IllegalStateException("No service registered for: " + type.getName());
        }
        return service;
    }

    /**
     * Try to resolve a service, returning null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T resolveOrNull(Class<T> type) {
        return (T) services.get(type);
    }

    /**
     * Check if a service is registered.
     */
    public boolean isRegistered(Class<?> type) {
        return services.containsKey(type);
    }

    /**
     * Unregister a service.
     */
    public void unregister(Class<?> type) {
        services.remove(type);
    }

    /**
     * Clear all services (for shutdown).
     */
    public void clear() {
        services.clear();
    }
}
