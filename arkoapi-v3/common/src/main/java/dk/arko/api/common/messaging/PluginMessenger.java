package dk.arko.api.common.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.List;

/**
 * Abstraction for Minecraft plugin messaging channels (BungeeCord/Velocity channel protocol).
 * Provides typed message sending/receiving with automatic serialization.
 */
public abstract class PluginMessenger {

    public static final String ARKO_CHANNEL = "arko:main";
    public static final String BUNGEECORD_CHANNEL = "BungeeCord";

    private static final Gson GSON = new GsonBuilder().create();
    private final Map<String, List<SubChannelHandler<?>>> handlers = new ConcurrentHashMap<>();

    // ─── Sending ───────────────────────────────────────────────

    /**
     * Send raw bytes on a channel. Implemented by platform.
     */
    protected abstract void sendRaw(UUID player, String channel, byte[] data);

    /**
     * Send a typed message on a sub-channel.
     */
    public <T> void send(UUID player, String subChannel, T message) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(subChannel);
            out.writeUTF(GSON.toJson(message));
            out.writeUTF(message.getClass().getName());
            sendRaw(player, ARKO_CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to send plugin message", e);
        }
    }

    /**
     * Send a BungeeCord-protocol message.
     */
    public void sendBungee(UUID player, String subChannel, String... args) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(subChannel);
            for (String arg : args) out.writeUTF(arg);
            sendRaw(player, BUNGEECORD_CHANNEL, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to send BungeeCord message", e);
        }
    }

    /**
     * Connect a player to a server via BungeeCord channel.
     */
    public void connectServer(UUID player, String serverName) {
        sendBungee(player, "Connect", serverName);
    }

    // ─── Receiving ─────────────────────────────────────────────

    /**
     * Register a handler for a sub-channel.
     */
    @SuppressWarnings("unchecked")
    public <T> void onMessage(String subChannel, Class<T> type, Consumer<PluginMessageContext<T>> handler) {
        handlers.computeIfAbsent(subChannel, k -> new CopyOnWriteArrayList<>())
                .add(new SubChannelHandler<>(type, handler));
    }

    /**
     * Process incoming plugin message bytes. Call from platform listener.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void handleIncoming(UUID player, byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream in = new DataInputStream(bais);
            String subChannel = in.readUTF();
            String json = in.readUTF();
            String typeName = in.readUTF();

            List<SubChannelHandler<?>> subHandlers = handlers.get(subChannel);
            if (subHandlers == null) return;

            for (SubChannelHandler handler : subHandlers) {
                try {
                    Object message = GSON.fromJson(json, handler.type);
                    handler.handler.accept(new PluginMessageContext<>(player, subChannel, message));
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read plugin message", e);
        }
    }

    // ─── Inner Classes ─────────────────────────────────────────

    private record SubChannelHandler<T>(Class<T> type, Consumer<PluginMessageContext<T>> handler) {}

    /**
     * Context for received plugin messages.
     */
    public record PluginMessageContext<T>(UUID player, String subChannel, T data) {}
}
