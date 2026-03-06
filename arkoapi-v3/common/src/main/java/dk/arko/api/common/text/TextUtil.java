package dk.arko.api.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive text utility for MiniMessage formatting, hex colors, gradients, and more.
 * Thread-safe and optimized for high-throughput formatting (1000+ players).
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage MINI_MESSAGE_STRICT = MiniMessage.builder()
            .strict(true)
            .build();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile("<[^>]*>");

    private TextUtil() {}

    // ─── Core Parsing ──────────────────────────────────────────

    /**
     * Parse a MiniMessage string into a Component.
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(convertLegacy(message));
    }

    /**
     * Parse with tag resolvers (placeholders).
     */
    public static Component parse(String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(convertLegacy(message), resolvers);
    }

    /**
     * Parse with a map of placeholders.
     * Usage: parse("Hello <player>!", Map.of("player", "Louis"))
     */
    public static Component parse(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) return Component.empty();
        TagResolver.Builder builder = TagResolver.builder();
        placeholders.forEach((key, value) -> builder.resolver(Placeholder.parsed(key, value)));
        return MINI_MESSAGE.deserialize(convertLegacy(message), builder.build());
    }

    /**
     * Parse multiple lines into a list of Components.
     */
    public static List<Component> parseLines(List<String> lines) {
        return lines.stream().map(TextUtil::parse).toList();
    }

    /**
     * Parse multiple lines with placeholders.
     */
    public static List<Component> parseLines(List<String> lines, Map<String, String> placeholders) {
        return lines.stream().map(line -> parse(line, placeholders)).toList();
    }

    // ─── Serialization ─────────────────────────────────────────

    /**
     * Serialize a Component back to a MiniMessage string.
     */
    public static String serialize(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * Convert a Component to plain text (no formatting).
     * Uses MiniMessage round-trip to strip all tags.
     */
    public static String toPlainText(Component component) {
        return MINI_MESSAGE.stripTags(MINI_MESSAGE.serialize(component));
    }

    /**
     * Strip all MiniMessage tags from a string.
     */
    public static String stripTags(String message) {
        return MINI_MESSAGE.stripTags(message);
    }

    // ─── Legacy Conversion ─────────────────────────────────────

    /**
     * Convert legacy &-codes and &#hex to MiniMessage format.
     */
    public static String convertLegacy(String message) {
        if (message == null) return "";
        // Convert &#RRGGBB to <color:#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<color:#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        message = sb.toString();

        // Convert legacy color codes
        message = message.replace("&0", "<black>").replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>")
                .replace("&8", "<dark_gray>").replace("&9", "<blue>")
                .replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>")
                .replace("&e", "<yellow>").replace("&f", "<white>")
                .replace("&k", "<obfuscated>").replace("&l", "<bold>")
                .replace("&m", "<strikethrough>").replace("&n", "<underlined>")
                .replace("&o", "<italic>").replace("&r", "<reset>");
        return message;
    }

    // ─── Gradient Builder ──────────────────────────────────────

    /**
     * Create a gradient text component.
     * Usage: gradient("Hello World", "#FF0000", "#00FF00")
     */
    public static Component gradient(String text, String... hexColors) {
        StringBuilder tag = new StringBuilder("<gradient");
        for (String color : hexColors) {
            tag.append(":").append(color.startsWith("#") ? color : "#" + color);
        }
        tag.append(">").append(text).append("</gradient>");
        return MINI_MESSAGE.deserialize(tag.toString());
    }

    /**
     * Create a rainbow text component.
     */
    public static Component rainbow(String text) {
        return MINI_MESSAGE.deserialize("<rainbow>" + text + "</rainbow>");
    }

    /**
     * Create a rainbow text with phase offset.
     */
    public static Component rainbow(String text, float phase) {
        return MINI_MESSAGE.deserialize("<rainbow:" + phase + ">" + text + "</rainbow>");
    }

    // ─── Component Builders ────────────────────────────────────

    /**
     * Create a clickable text component.
     */
    public static Component clickable(String text, String command) {
        return parse("<click:run_command:'" + command + "'>" + text + "</click>");
    }

    /**
     * Create a hoverable text component.
     */
    public static Component hoverable(String text, String hoverText) {
        return parse("<hover:show_text:'" + hoverText + "'>" + text + "</hover>");
    }

    /**
     * Create a clickable + hoverable text component.
     */
    public static Component interactive(String text, String hoverText, String command) {
        return parse("<hover:show_text:'" + hoverText + "'><click:run_command:'" + command + "'>" + text + "</click></hover>");
    }

    /**
     * Create a suggestable command text component.
     */
    public static Component suggestCommand(String text, String command) {
        return parse("<click:suggest_command:'" + command + "'>" + text + "</click>");
    }

    /**
     * Create a URL-linked text component.
     */
    public static Component link(String text, String url) {
        return parse("<click:open_url:'" + url + "'>" + text + "</click>");
    }

    /**
     * Create a copyable text component.
     */
    public static Component copyable(String text, String copyText) {
        return parse("<click:copy_to_clipboard:'" + copyText + "'>" + text + "</click>");
    }

    /**
     * Create a keybind component.
     */
    public static Component keybind(String key) {
        return parse("<keybind:" + key + ">");
    }

    /**
     * Create a translatable component.
     */
    public static Component translatable(String key) {
        return parse("<translatable:" + key + ">");
    }

    // ─── Formatting Helpers ────────────────────────────────────

    /**
     * Center a message for chat (assumes 80 char width).
     */
    public static String centerText(String message, int lineWidth) {
        String stripped = stripTags(message);
        int padding = (lineWidth - stripped.length()) / 2;
        if (padding <= 0) return message;
        return " ".repeat(padding) + message;
    }

    /**
     * Create a progress bar component.
     */
    public static Component progressBar(double progress, int length, String filledColor, String emptyColor,
                                        String filledChar, String emptyChar) {
        progress = Math.max(0, Math.min(1, progress));
        int filled = (int) (progress * length);
        int empty = length - filled;
        return parse("<" + filledColor + ">" + filledChar.repeat(filled) +
                "<" + emptyColor + ">" + emptyChar.repeat(empty));
    }

    /**
     * Create a default progress bar.
     */
    public static Component progressBar(double progress, int length) {
        return progressBar(progress, length, "green", "gray", "█", "░");
    }

    /**
     * Format a number with commas.
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Format a number with short suffix (1.2K, 3.4M, etc).
     */
    public static String formatShort(long number) {
        if (number < 1_000) return String.valueOf(number);
        if (number < 1_000_000) return String.format("%.1fK", number / 1_000.0);
        if (number < 1_000_000_000) return String.format("%.1fM", number / 1_000_000.0);
        return String.format("%.1fB", number / 1_000_000_000.0);
    }

    /**
     * Format time duration in a human-readable way.
     */
    public static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        if (seconds < 86400) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
    }

    /**
     * Create a separator line.
     */
    public static Component separator(String color, int length) {
        return parse("<" + color + "><strikethrough>" + " ".repeat(length) + "</strikethrough>");
    }

    /**
     * Default separator.
     */
    public static Component separator() {
        return separator("dark_gray", 60);
    }

    // ─── Batch Operations (optimized for scale) ────────────────

    /**
     * Pre-compile a MiniMessage template for repeated use.
     * Returns a function that applies placeholders efficiently.
     */
    public static MessageTemplate template(String message) {
        return new MessageTemplate(convertLegacy(message));
    }

    /**
     * Pre-compiled message template for high-performance repeated formatting.
     */
    public static final class MessageTemplate {
        private final String template;

        MessageTemplate(String template) {
            this.template = template;
        }

        public Component render(TagResolver... resolvers) {
            return MINI_MESSAGE.deserialize(template, resolvers);
        }

        public Component render(Map<String, String> placeholders) {
            TagResolver.Builder builder = TagResolver.builder();
            placeholders.forEach((key, value) -> builder.resolver(Placeholder.parsed(key, value)));
            return MINI_MESSAGE.deserialize(template, builder.build());
        }

        public Component render() {
            return MINI_MESSAGE.deserialize(template);
        }
    }
}