package dk.arko.api.paper.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Represents an item in a menu with optional click handler.
 */
public class MenuItem {

    private ItemStack itemStack;
    private Consumer<MenuClickContext> clickHandler;
    private boolean visible = true;
    private String permission;

    private MenuItem(ItemStack itemStack, Consumer<MenuClickContext> clickHandler) {
        this.itemStack = itemStack;
        this.clickHandler = clickHandler;
    }

    public static MenuItem of(ItemStack item) {
        return new MenuItem(item, null);
    }

    public static MenuItem of(ItemStack item, Consumer<MenuClickContext> handler) {
        return new MenuItem(item, handler);
    }

    public static MenuItem empty() {
        return new MenuItem(null, null);
    }

    // ─── Builder Methods ───────────────────────────────────────

    public MenuItem onClick(Consumer<MenuClickContext> handler) {
        this.clickHandler = handler;
        return this;
    }

    public MenuItem onLeftClick(Consumer<MenuClickContext> handler) {
        Consumer<MenuClickContext> existing = this.clickHandler;
        this.clickHandler = ctx -> {
            if (ctx.clickType() == ClickType.LEFT) handler.accept(ctx);
            else if (existing != null) existing.accept(ctx);
        };
        return this;
    }

    public MenuItem onRightClick(Consumer<MenuClickContext> handler) {
        Consumer<MenuClickContext> existing = this.clickHandler;
        this.clickHandler = ctx -> {
            if (ctx.clickType() == ClickType.RIGHT) handler.accept(ctx);
            else if (existing != null) existing.accept(ctx);
        };
        return this;
    }

    public MenuItem onShiftClick(Consumer<MenuClickContext> handler) {
        Consumer<MenuClickContext> existing = this.clickHandler;
        this.clickHandler = ctx -> {
            if (ctx.clickType().isShiftClick()) handler.accept(ctx);
            else if (existing != null) existing.accept(ctx);
        };
        return this;
    }

    public MenuItem onMiddleClick(Consumer<MenuClickContext> handler) {
        Consumer<MenuClickContext> existing = this.clickHandler;
        this.clickHandler = ctx -> {
            if (ctx.clickType() == ClickType.MIDDLE) handler.accept(ctx);
            else if (existing != null) existing.accept(ctx);
        };
        return this;
    }

    public MenuItem visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MenuItem permission(String permission) {
        this.permission = permission;
        return this;
    }

    // ─── Getters ───────────────────────────────────────────────

    public ItemStack getItemStack() {
        return visible ? itemStack : null;
    }

    public Consumer<MenuClickContext> getClickHandler() {
        return clickHandler;
    }

    public boolean isVisible() { return visible; }

    public String getPermission() { return permission; }

    public boolean canView(Player player) {
        if (!visible) return false;
        if (permission != null) return player.hasPermission(permission);
        return true;
    }
}
