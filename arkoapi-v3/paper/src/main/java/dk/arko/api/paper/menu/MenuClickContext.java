package dk.arko.api.paper.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Context provided to menu click handlers with all click information.
 */
public record MenuClickContext(
        Player player,
        Menu menu,
        InventoryClickEvent event,
        int slot,
        MenuItem menuItem,
        ClickType clickType,
        ItemStack clickedItem
) {
    /**
     * Close the menu.
     */
    public void close() {
        player.closeInventory();
    }

    /**
     * Refresh the current menu.
     */
    public void refresh() {
        menu.refresh(player);
    }

    /**
     * Open a different menu.
     */
    public void openMenu(Menu newMenu) {
        newMenu.open(player);
    }

    /**
     * Check if it's a left click.
     */
    public boolean isLeftClick() { return clickType == ClickType.LEFT; }

    /**
     * Check if it's a right click.
     */
    public boolean isRightClick() { return clickType == ClickType.RIGHT; }

    /**
     * Check if it's a shift click.
     */
    public boolean isShiftClick() { return clickType.isShiftClick(); }

    /**
     * Get metadata from the menu.
     */
    @SuppressWarnings("unchecked")
    public <T> T meta(String key) { return (T) menu.metadata.get(key); }
}
