package dk.arko.api.paper.dialog;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages open dialogs and routes submissions back to handlers.
 */
public class DialogManager {

    private static DialogManager instance;
    private final Map<UUID, DialogBuilder.DialogDefinition> openDialogs = new ConcurrentHashMap<>();
    private final Map<String, DialogBuilder.DialogDefinition> registeredDialogs = new ConcurrentHashMap<>();

    private DialogManager() {}

    public static DialogManager getInstance() {
        if (instance == null) instance = new DialogManager();
        return instance;
    }

    public void register(JavaPlugin plugin) {
        // Register Paper dialog event listeners here when the Dialog API is available
    }

    /**
     * Register a reusable dialog definition.
     */
    public void registerDialog(DialogBuilder.DialogDefinition definition) {
        registeredDialogs.put(definition.id(), definition);
    }

    /**
     * Open a dialog for a player.
     */
    public void openDialog(Player player, DialogBuilder.DialogDefinition definition) {
        openDialogs.put(player.getUniqueId(), definition);
        // Build and send the actual Paper Dialog to the player
        // This integrates with Paper 1.21.6+ Dialog API
        buildAndSendDialog(player, definition);
    }

    /**
     * Open a registered dialog by ID.
     */
    public void openDialog(Player player, String dialogId) {
        DialogBuilder.DialogDefinition def = registeredDialogs.get(dialogId);
        if (def == null) throw new IllegalArgumentException("Dialog not registered: " + dialogId);
        openDialog(player, def);
    }

    /**
     * Handle a dialog submission from a player.
     */
    public void handleSubmit(Player player, DialogBuilder.DialogData data) {
        DialogBuilder.DialogDefinition def = openDialogs.remove(player.getUniqueId());
        if (def != null && def.submitHandler() != null) {
            def.submitHandler().accept(player, data);
        }
    }

    /**
     * Handle a dialog cancellation.
     */
    public void handleCancel(Player player) {
        DialogBuilder.DialogDefinition def = openDialogs.remove(player.getUniqueId());
        if (def != null && def.cancelHandler() != null) {
            def.cancelHandler().accept(player);
        }
    }

    /**
     * Check if a player has an open dialog.
     */
    public boolean hasOpenDialog(Player player) {
        return openDialogs.containsKey(player.getUniqueId());
    }

    /**
     * Close a player's dialog.
     */
    public void closeDialog(Player player) {
        openDialogs.remove(player.getUniqueId());
    }

    private void buildAndSendDialog(Player player, DialogBuilder.DialogDefinition definition) {
        // Paper 1.21.6+ Dialog API integration point
        // This is where we convert our DialogDefinition into Paper's native Dialog objects
        // and send them to the player. The implementation depends on Paper's API version.
        //
        // Example with Paper's Dialog API:
        // Dialog dialog = Dialog.dialog()
        //     .title(definition.title())
        //     .body(...)
        //     .build();
        // player.openDialog(dialog);
    }
}
