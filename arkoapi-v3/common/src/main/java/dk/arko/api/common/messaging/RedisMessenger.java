package dk.arko.api.common.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Redis pub/sub messaging system for cross-server communication.
 * Supports typed messages, channels, and automatic serialization.
 * Thread-safe and designed for high-throughput messaging across a network.
 */
public class RedisMessenger implements AutoCloseable {

    private static final Gson GSON = new GsonBuilder().create();
    private final Logger logger;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final String serverId;
    private final Map<String, List<MessageHandler<?>>> handlers = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    public RedisMessenger(String redisUri, String serverId, Logger logger) {
        this.serverId = serverId;
        this.logger = logger;
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.pubSubConnection = client.connectPubSub();

        pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                handleMessage(channel, message);
            }
        });
    }

    // ─── Publishing ────────────────────────────────────────────

    /**
     * Publish a message to a channel.
     */
    public <T> void publish(String channel, T message) {
        if (closed) return;
        MessageWrapper wrapper = new MessageWrapper(
                serverId,
                message.getClass().getName(),
                GSON.toJson(message),
                System.currentTimeMillis()
        );
        connection.async().publish(channel, GSON.toJson(wrapper));
    }

    /**
     * Publish to a specific server.
     */
    public <T> void publishTo(String targetServer, String channel, T message) {
        MessageWrapper wrapper = new MessageWrapper(
                serverId,
                message.getClass().getName(),
                GSON.toJson(message),
                System.currentTimeMillis()
        );
        wrapper.targetServer = targetServer;
        connection.async().publish(channel, GSON.toJson(wrapper));
    }

    /**
     * Broadcast to all servers.
     */
    public <T> void broadcast(String channel, T message) {
        publish(channel, message);
    }

    // ─── Subscribing ───────────────────────────────────────────

    /**
     * Subscribe to a channel with a typed handler.
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String channel, Class<T> type, Consumer<MessageContext<T>> handler) {
        MessageHandler<T> messageHandler = new MessageHandler<>(type, handler);
        handlers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(messageHandler);
        pubSubConnection.async().subscribe(channel);
    }

    /**
     * Subscribe to a channel with a raw string handler.
     */
    public void subscribeRaw(String channel, Consumer<MessageContext<String>> handler) {
        subscribe(channel, String.class, handler);
    }

    /**
     * Unsubscribe from a channel.
     */
    public void unsubscribe(String channel) {
        handlers.remove(channel);
        pubSubConnection.async().unsubscribe(channel);
    }

    // ─── Key-Value Store ───────────────────────────────────────

    /**
     * Set a value in Redis.
     */
    public void set(String key, String value) {
        connection.async().set(key, value);
    }

    /**
     * Set a value with expiry.
     */
    public void setex(String key, String value, long seconds) {
        connection.async().setex(key, seconds, value);
    }

    /**
     * Get a value from Redis synchronously.
     */
    public String get(String key) {
        return connection.sync().get(key);
    }

    /**
     * Delete a key.
     */
    public void delete(String key) {
        connection.async().del(key);
    }

    /**
     * Set a JSON-serialized object.
     */
    public <T> void setObject(String key, T object) {
        set(key, GSON.toJson(object));
    }

    /**
     * Set a JSON-serialized object with expiry.
     */
    public <T> void setObject(String key, T object, long seconds) {
        setex(key, GSON.toJson(object), seconds);
    }

    /**
     * Get a JSON-deserialized object.
     */
    public <T> T getObject(String key, Class<T> type) {
        String json = get(key);
        return json != null ? GSON.fromJson(json, type) : null;
    }

    /**
     * Check if a key exists.
     */
    public boolean exists(String key) {
        return connection.sync().exists(key) > 0;
    }

    // ─── Server Tracking ───────────────────────────────────────

    /**
     * Register this server as online.
     */
    public void registerServer() {
        setex("server:" + serverId + ":online", "true", 30);
    }

    /**
     * Heartbeat (call every 15s).
     */
    public void heartbeat() {
        setex("server:" + serverId + ":online", "true", 30);
        setex("server:" + serverId + ":heartbeat", String.valueOf(System.currentTimeMillis()), 30);
    }

    /**
     * Check if a server is online.
     */
    public boolean isServerOnline(String targetServerId) {
        return exists("server:" + targetServerId + ":online");
    }

    // ─── Internal ──────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleMessage(String channel, String rawMessage) {
        try {
            MessageWrapper wrapper = GSON.fromJson(rawMessage, MessageWrapper.class);
            if (wrapper.sourceServer.equals(serverId)) return; // Ignore own messages
            if (wrapper.targetServer != null && !wrapper.targetServer.equals(serverId)) return;

            List<MessageHandler<?>> channelHandlers = handlers.get(channel);
            if (channelHandlers == null) return;

            for (MessageHandler handler : channelHandlers) {
                try {
                    Object data;
                    if (handler.type == String.class) {
                        data = wrapper.data;
                    } else {
                        data = GSON.fromJson(wrapper.data, handler.type);
                    }
                    MessageContext context = new MessageContext<>(data, wrapper.sourceServer, channel, wrapper.timestamp);
                    handler.handler.accept(context);
                } catch (Exception e) {
                    logger.warning("[ArkoAPI] Error handling message on channel " + channel + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("[ArkoAPI] Error parsing Redis message: " + e.getMessage());
        }
    }

    public String getServerId() {
        return serverId;
    }

    @Override
    public void close() {
        closed = true;
        pubSubConnection.close();
        connection.close();
        client.shutdown();
    }

    // ─── Inner Classes ─────────────────────────────────────────

    private record MessageHandler<T>(Class<T> type, Consumer<MessageContext<T>> handler) {}

    private static class MessageWrapper {
        String sourceServer;
        String type;
        String data;
        long timestamp;
        String targetServer;

        MessageWrapper(String sourceServer, String type, String data, long timestamp) {
            this.sourceServer = sourceServer;
            this.type = type;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    /**
     * Context provided to message handlers.
     */
    public record MessageContext<T>(T data, String sourceServer, String channel, long timestamp) {}
}
