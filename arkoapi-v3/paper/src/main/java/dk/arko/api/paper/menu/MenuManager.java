package dk.arko.api.paper.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all open menus. Handles event routing.
 * Registered automatically when ArkoAPI Paper is enabled.
 */
public class MenuManager implements Listener {

    private static MenuManager instance;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    private MenuManager() {}

    public static MenuManager getInstance() {
        if (instance == null) instance = new MenuManager();
        return instance;
    }

    public void register(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void trackMenu(Player player, Menu menu) {
        openMenus.put(player.getUniqueId(), menu);
    }

    public Menu getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    public boolean hasOpenMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = openMenus.get(player.getUniqueId());
        if (menu != null) {
            menu.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Menu menu = openMenus.get(player.getUniqueId());
        if (menu != null && menu.cancelClicks) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Menu menu = openMenus.remove(player.getUniqueId());
        if (menu != null) {
            menu.handleClose(event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    public int getOpenMenuCount() {
        return openMenus.size();
    }
}
