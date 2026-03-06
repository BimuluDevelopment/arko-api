package dk.arko.api.common.registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Generic registry for registering and looking up typed objects by key.
 * Used for commands, items, menus, effects, mechanics, and more.
 */
public class Registry<K, V> {

    private final Map<K, V> entries = new ConcurrentHashMap<>();
    private final List<Consumer<RegistryEvent<K, V>>> listeners = new ArrayList<>();

    public void register(K key, V value) {
        V old = entries.put(key, value);
        notify(old == null ? RegistryEvent.Type.REGISTER : RegistryEvent.Type.REPLACE, key, value);
    }

    public void unregister(K key) {
        V removed = entries.remove(key);
        if (removed != null) {
            notify(RegistryEvent.Type.UNREGISTER, key, removed);
        }
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    public V getOrThrow(K key) {
        return Optional.ofNullable(entries.get(key))
                .orElseThrow(() -> new NoSuchElementException("No entry for key: " + key));
    }

    public V getOrDefault(K key, V defaultValue) {
        return entries.getOrDefault(key, defaultValue);
    }

    public boolean contains(K key) {
        return entries.containsKey(key);
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Set<K> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public Map<K, V> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<V> findAll(Predicate<V> predicate) {
        return entries.values().stream().filter(predicate).toList();
    }

    public Optional<V> findFirst(Predicate<V> predicate) {
        return entries.values().stream().filter(predicate).findFirst();
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }

    public void onEvent(Consumer<RegistryEvent<K, V>> listener) {
        listeners.add(listener);
    }

    private void notify(RegistryEvent.Type type, K key, V value) {
        RegistryEvent<K, V> event = new RegistryEvent<>(type, key, value);
        listeners.forEach(l -> l.accept(event));
    }

    public record RegistryEvent<K, V>(Type type, K key, V value) {
        public enum Type { REGISTER, UNREGISTER, REPLACE }
    }
}
