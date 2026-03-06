package dk.arko.api.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Unified configuration manager supporting JSON files with type-safe access,
 * defaults, comments, reload, and auto-save. For YAML, use the Paper/Velocity
 * platform-specific wrappers that delegate to this for non-YAML features.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configDir;
    private final Map<String, ConfigFile> configs = new HashMap<>();

    public ConfigManager(Path configDir) {
        this.configDir = configDir;
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory", e);
        }
    }

    /**
     * Load or create a JSON config file.
     */
    public ConfigFile loadConfig(String fileName) {
        return configs.computeIfAbsent(fileName, name -> {
            Path path = configDir.resolve(name);
            return new ConfigFile(path);
        });
    }

    /**
     * Load a typed config object from JSON.
     */
    public <T> T loadTyped(String fileName, Class<T> type) {
        Path path = configDir.resolve(fileName);
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config: " + fileName, e);
        }
    }

    /**
     * Save a typed config object to JSON.
     */
    public <T> void saveTyped(String fileName, T config) {
        Path path = configDir.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + fileName, e);
        }
    }

    /**
     * Reload all configs.
     */
    public void reloadAll() {
        configs.values().forEach(ConfigFile::reload);
    }

    /**
     * JSON config file with get/set operations.
     */
    public static class ConfigFile {
        private final Path path;
        private Map<String, Object> data;

        ConfigFile(Path path) {
            this.path = path;
            reload();
        }

        public void reload() {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    data = GSON.fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
                    if (data == null) data = new LinkedHashMap<>();
                } catch (IOException e) {
                    data = new LinkedHashMap<>();
                }
            } else {
                data = new LinkedHashMap<>();
            }
        }

        public void save() {
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, writer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save config", e);
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String path, T defaultValue) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = data;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = current.get(parts[i]);
                if (!(next instanceof Map)) return defaultValue;
                current = (Map<String, Object>) next;
            }
            Object value = current.get(parts[parts.length - 1]);
            if (value == null) return defaultValue;
            if (defaultValue != null && defaultValue.getClass().isInstance(value)) {
                return (T) value;
            }
            // Handle numeric conversions (Gson uses Double for all numbers)
            if (value instanceof Number num) {
                if (defaultValue instanceof Integer) return (T) Integer.valueOf(num.intValue());
                if (defaultValue instanceof Long) return (T) Long.valueOf(num.longValue());
                if (defaultValue instanceof Float) return (T) Float.valueOf(num.floatValue());
                if (defaultValue instanceof Double) return (T) Double.valueOf(num.doubleValue());
            }
            return (T) value;
        }

        public <T> T get(String path) {
            return get(path, null);
        }

        public String getString(String path, String def) { return get(path, def); }
        public int getInt(String path, int def) { return get(path, def); }
        public long getLong(String path, long def) { return get(path, def); }
        public double getDouble(String path, double def) { return get(path, def); }
        public boolean getBoolean(String path, boolean def) { return get(path, def); }

        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path) {
            Object value = get(path);
            if (value instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
            return List.of();
        }

        @SuppressWarnings("unchecked")
        public void set(String path, Object value) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = data;
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
            }
            current.put(parts[parts.length - 1], value);
        }

        public boolean contains(String path) {
            return get(path) != null;
        }

        public Map<String, Object> getData() { return data; }
    }
}
