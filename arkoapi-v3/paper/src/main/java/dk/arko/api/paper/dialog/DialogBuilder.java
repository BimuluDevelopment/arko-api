package dk.arko.api.paper.dialog;

import dk.arko.api.common.text.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fluent Dialog API builder wrapping Paper 1.21.6+ native Dialog system.
 * Supports multi-page dialogs, text inputs, selections, number inputs,
 * and conditional branching.
 *
 * Usage:
 *   DialogBuilder.create("ban_wizard")
 *       .title("Ban Wizard")
 *       .page("reason")
 *           .textInput("reason", "Grund til ban")
 *           .dropdown("duration", "Varighed", List.of("1 dag", "7 dage", "30 dage", "Permanent"))
 *       .page("confirm")
 *           .body("Er du sikker?")
 *           .button("confirm", "Bekræft", ButtonStyle.SUCCESS)
 *           .button("cancel", "Annuller", ButtonStyle.DANGER)
 *       .onSubmit((player, data) -> {
 *           String reason = data.getString("reason");
 *           String duration = data.getString("duration");
 *           // Process ban...
 *       })
 *       .open(player);
 */
public class DialogBuilder {

    private final String id;
    private Component title;
    private final List<DialogPage> pages = new ArrayList<>();
    private BiConsumer<Player, DialogData> submitHandler;
    private Consumer<Player> cancelHandler;
    private boolean canClose = true;
    private DialogPage currentPage;

    private DialogBuilder(String id) {
        this.id = id;
    }

    public static DialogBuilder create(String id) {
        return new DialogBuilder(id);
    }

    // ─── Dialog Properties ─────────────────────────────────────

    public DialogBuilder title(String miniMessage) {
        this.title = TextUtil.parse(miniMessage);
        return this;
    }

    public DialogBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public DialogBuilder canClose(boolean canClose) {
        this.canClose = canClose;
        return this;
    }

    // ─── Pages ─────────────────────────────────────────────────

    public DialogBuilder page(String pageId) {
        currentPage = new DialogPage(pageId);
        pages.add(currentPage);
        return this;
    }

    public DialogBuilder body(String miniMessage) {
        if (currentPage == null) page("default");
        currentPage.body = TextUtil.parse(miniMessage);
        return this;
    }

    public DialogBuilder body(Component body) {
        if (currentPage == null) page("default");
        currentPage.body = body;
        return this;
    }

    // ─── Input Elements ────────────────────────────────────────

    public DialogBuilder textInput(String id, String label) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.TEXT_INPUT, id, label, null, null, null, false));
        return this;
    }

    public DialogBuilder textInput(String id, String label, String placeholder) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.TEXT_INPUT, id, label, placeholder, null, null, false));
        return this;
    }

    public DialogBuilder textInput(String id, String label, String placeholder, String defaultValue) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.TEXT_INPUT, id, label, placeholder, defaultValue, null, false));
        return this;
    }

    public DialogBuilder numberInput(String id, String label) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.NUMBER_INPUT, id, label, null, null, null, false));
        return this;
    }

    public DialogBuilder numberInput(String id, String label, String defaultValue) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.NUMBER_INPUT, id, label, null, defaultValue, null, false));
        return this;
    }

    public DialogBuilder dropdown(String id, String label, List<String> options) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.DROPDOWN, id, label, null, null, options, false));
        return this;
    }

    public DialogBuilder checkbox(String id, String label) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.CHECKBOX, id, label, null, null, null, false));
        return this;
    }

    public DialogBuilder checkbox(String id, String label, boolean defaultValue) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.CHECKBOX, id, label, null, String.valueOf(defaultValue), null, false));
        return this;
    }

    // ─── Buttons ───────────────────────────────────────────────

    public DialogBuilder button(String id, String label, ButtonStyle style) {
        requirePage();
        currentPage.elements.add(new DialogElement(DialogElement.Type.BUTTON, id, label, null, null, null, false));
        currentPage.elements.get(currentPage.elements.size() - 1).buttonStyle = style;
        return this;
    }

    public DialogBuilder button(String id, String label) {
        return button(id, label, ButtonStyle.PRIMARY);
    }

    public DialogBuilder submitButton(String label) {
        return button("submit", label, ButtonStyle.SUCCESS);
    }

    public DialogBuilder cancelButton(String label) {
        return button("cancel", label, ButtonStyle.DANGER);
    }

    // ─── Handlers ──────────────────────────────────────────────

    public DialogBuilder onSubmit(BiConsumer<Player, DialogData> handler) {
        this.submitHandler = handler;
        return this;
    }

    public DialogBuilder onCancel(Consumer<Player> handler) {
        this.cancelHandler = handler;
        return this;
    }

    // ─── Opening ───────────────────────────────────────────────

    /**
     * Build and open the dialog for a player.
     */
    public void open(Player player) {
        DialogManager.getInstance().openDialog(player, build());
    }

    /**
     * Build the dialog definition (for registration).
     */
    public DialogDefinition build() {
        return new DialogDefinition(id, title, pages, submitHandler, cancelHandler, canClose);
    }

    private void requirePage() {
        if (currentPage == null) page("default");
    }

    // ─── Inner Classes ─────────────────────────────────────────

    public enum ButtonStyle {
        PRIMARY, SECONDARY, SUCCESS, DANGER, WARNING
    }

    public static class DialogPage {
        final String id;
        Component body;
        final List<DialogElement> elements = new ArrayList<>();

        DialogPage(String id) { this.id = id; }
    }

    public static class DialogElement {
        public enum Type { TEXT_INPUT, NUMBER_INPUT, DROPDOWN, CHECKBOX, BUTTON }

        final Type type;
        final String id;
        final String label;
        final String placeholder;
        final String defaultValue;
        final List<String> options;
        final boolean required;
        ButtonStyle buttonStyle;

        DialogElement(Type type, String id, String label, String placeholder,
                      String defaultValue, List<String> options, boolean required) {
            this.type = type;
            this.id = id;
            this.label = label;
            this.placeholder = placeholder;
            this.defaultValue = defaultValue;
            this.options = options;
            this.required = required;
        }
    }

    /**
     * Data container for dialog submissions.
     */
    public static class DialogData {
        private final Map<String, Object> data = new HashMap<>();

        public void set(String key, Object value) { data.put(key, value); }

        public String getString(String key) { return (String) data.get(key); }
        public String getString(String key, String def) { return data.containsKey(key) ? (String) data.get(key) : def; }
        public int getInt(String key, int def) {
            Object v = data.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
            return def;
        }
        public boolean getBoolean(String key) {
            Object v = data.get(key);
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) return Boolean.parseBoolean(s);
            return false;
        }
        public Object get(String key) { return data.get(key); }
        public boolean has(String key) { return data.containsKey(key); }
        public Map<String, Object> getAll() { return Collections.unmodifiableMap(data); }
    }

    public record DialogDefinition(
            String id,
            Component title,
            List<DialogPage> pages,
            BiConsumer<Player, DialogData> submitHandler,
            Consumer<Player> cancelHandler,
            boolean canClose
    ) {}
}
