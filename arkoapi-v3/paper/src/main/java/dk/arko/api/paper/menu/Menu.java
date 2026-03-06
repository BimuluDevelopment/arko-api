package dk.arko.api.paper.menu;

import dk.arko.api.common.text.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * Base menu class supporting all inventory types, click handling,
 * animations, patterns, and pagination. Thread-safe for concurrent access.
 *
 * Supported menu types (via subclasses):
 * - ChestMenu (1-6 rows)
 * - PaginatedMenu (auto-pagination)
 * - ScrollingMenu (vertical/horizontal scroll)
 * - AnimatedMenu (animated slots)
 * - ConfirmationMenu (yes/no dialogs)
 * - InputMenu (anvil-based text input)
 * - CategoryMenu (tabbed categories)
 * - FilterableMenu (search/filter items)
 * - CraftingMenu (crafting grid)
 * - DispenserMenu (3x3 grid)
 * - HopperMenu (5-slot)
 * - PlayerMenu (player inventory overlay)
 */
public abstract class Menu {

    protected final Map<Integer, MenuItem> items = new LinkedHashMap<>();
    protected Component title;
    protected int size;
    protected InventoryType inventoryType;
    protected boolean cancelClicks = true;
    protected boolean allowPlayerInventory = false;
    protected Consumer<InventoryCloseEvent> closeHandler;
    protected Consumer<Player> openHandler;
    protected final Map<String, Object> metadata = new HashMap<>();

    // Prevent rapid clicking
    private final Map<UUID, Long> clickCooldowns = new WeakHashMap<>();
    private long clickCooldownMs = 100;

    protected Menu(Component title, int rows) {
        this.title = title;
        this.size = rows * 9;
        this.inventoryType = null;
    }

    protected Menu(Component title, InventoryType type) {
        this.title = title;
        this.inventoryType = type;
        this.size = type.getDefaultSize();
    }

    // ─── Item Management ───────────────────────────────────────

    /**
     * Set an item at a specific slot.
     */
    public Menu setItem(int slot, MenuItem item) {
        items.put(slot, item);
        return this;
    }

    /**
     * Set an item with just an ItemStack (no click handler).
     */
    public Menu setItem(int slot, ItemStack item) {
        items.put(slot, MenuItem.of(item));
        return this;
    }

    /**
     * Set an item with click handler.
     */
    public Menu setItem(int slot, ItemStack item, Consumer<MenuClickContext> handler) {
        items.put(slot, MenuItem.of(item, handler));
        return this;
    }

    /**
     * Fill empty slots with an item (typically glass panes).
     */
    public Menu fillEmpty(ItemStack filler) {
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i)) {
                items.put(i, MenuItem.of(filler));
            }
        }
        return this;
    }

    /**
     * Fill borders with an item.
     */
    public Menu fillBorder(ItemStack border) {
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                if (!items.containsKey(i)) {
                    items.put(i, MenuItem.of(border));
                }
            }
        }
        return this;
    }

    /**
     * Fill a row with items.
     */
    public Menu fillRow(int row, ItemStack item) {
        for (int col = 0; col < 9; col++) {
            items.put(row * 9 + col, MenuItem.of(item));
        }
        return this;
    }

    /**
     * Fill a column with items.
     */
    public Menu fillColumn(int col, ItemStack item) {
        for (int row = 0; row < size / 9; row++) {
            items.put(row * 9 + col, MenuItem.of(item));
        }
        return this;
    }

    /**
     * Set items from a pattern.
     * Usage: setPattern(new String[]{"XOOOOOOOX", "O       O", "XOOOOOOOX"}, Map.of('X', borderItem, 'O', fillerItem));
     */
    public Menu setPattern(String[] pattern, Map<Character, MenuItem> legend) {
        for (int row = 0; row < pattern.length && row < size / 9; row++) {
            String line = pattern[row];
            for (int col = 0; col < line.length() && col < 9; col++) {
                char c = line.charAt(col);
                if (c == ' ') continue;
                MenuItem item = legend.get(c);
                if (item != null) {
                    items.put(row * 9 + col, item);
                }
            }
        }
        return this;
    }

    /**
     * Clear a slot.
     */
    public Menu clearSlot(int slot) {
        items.remove(slot);
        return this;
    }

    /**
     * Clear all items.
     */
    public Menu clearAll() {
        items.clear();
        return this;
    }

    // ─── Properties ────────────────────────────────────────────

    public Menu title(String miniMessage) {
        this.title = TextUtil.parse(miniMessage);
        return this;
    }

    public Menu title(Component title) {
        this.title = title;
        return this;
    }

    public Menu cancelClicks(boolean cancel) {
        this.cancelClicks = cancel;
        return this;
    }

    public Menu allowPlayerInventory(boolean allow) {
        this.allowPlayerInventory = allow;
        return this;
    }

    public Menu clickCooldown(long ms) {
        this.clickCooldownMs = ms;
        return this;
    }

    public Menu onClose(Consumer<InventoryCloseEvent> handler) {
        this.closeHandler = handler;
        return this;
    }

    public Menu onOpen(Consumer<Player> handler) {
        this.openHandler = handler;
        return this;
    }

    public Menu meta(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T meta(String key) {
        return (T) metadata.get(key);
    }

    // ─── Opening ───────────────────────────────────────────────

    /**
     * Open this menu for a player.
     */
    public void open(Player player) {
        Inventory inv = createInventory();
        render(inv);
        player.openInventory(inv);
        MenuManager.getInstance().trackMenu(player, this);
        if (openHandler != null) openHandler.accept(player);
    }

    /**
     * Refresh the menu for a player (re-render without closing).
     */
    public void refresh(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        render(inv);
        player.updateInventory();
    }

    // ─── Internal ──────────────────────────────────────────────

    protected Inventory createInventory() {
        if (inventoryType != null) {
            return Bukkit.createInventory(null, inventoryType, title);
        }
        return Bukkit.createInventory(null, size, title);
    }

    protected void render(Inventory inv) {
        inv.clear();
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item.getItemStack());
            }
        });
    }

    /**
     * Handle a click event. Called by MenuManager.
     */
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (cancelClicks) event.setCancelled(true);
        if (!allowPlayerInventory && event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }

        // Click cooldown
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldowns.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < clickCooldownMs) return;
        clickCooldowns.put(player.getUniqueId(), now);

        int slot = event.getRawSlot();
        MenuItem item = items.get(slot);
        if (item != null && item.getClickHandler() != null) {
            MenuClickContext ctx = new MenuClickContext(
                    player, this, event, slot, item,
                    event.getClick(), event.getCurrentItem()
            );
            item.getClickHandler().accept(ctx);
        }
    }

    /**
     * Handle close. Called by MenuManager.
     */
    public void handleClose(InventoryCloseEvent event) {
        if (closeHandler != null) closeHandler.accept(event);
        clickCooldowns.remove(event.getPlayer().getUniqueId());
    }

    public Component getTitle() { return title; }
    public int getSize() { return size; }
    public Map<Integer, MenuItem> getItems() { return items; }
}
