package dk.arko.api.paper.menu.types;

import dk.arko.api.common.text.TextUtil;
import dk.arko.api.paper.menu.Menu;
import dk.arko.api.paper.menu.MenuItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ScrollingMenuImpl extends Menu {
    public enum Direction { VERTICAL, HORIZONTAL }

    private final List<MenuItem> contentItems = new ArrayList<>();
    private int scrollOffset = 0;
    private Direction direction = Direction.VERTICAL;
    private int scrollUpSlot;
    private int scrollDownSlot;

    public ScrollingMenuImpl(Component title, int rows) {
        super(title, rows);
        this.scrollUpSlot = rows * 9 - 9;  // Bottom-left
        this.scrollDownSlot = rows * 9 - 1; // Bottom-right
    }

    public ScrollingMenuImpl setDirection(Direction dir) { this.direction = dir; return this; }
    public ScrollingMenuImpl setItems(List<MenuItem> items) { this.contentItems.clear(); this.contentItems.addAll(items); return this; }
    public ScrollingMenuImpl addItem(MenuItem item) { this.contentItems.add(item); return this; }

    public void scrollUp() { if (scrollOffset > 0) scrollOffset--; }
    public void scrollDown() { scrollOffset++; }

    @Override
    protected void render(Inventory inv) {
        inv.clear();
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item.getItemStack());
        });

        int cols = 7; // Columns 1-7 for content
        int rows = (size / 9) - 1; // Reserve last row for nav
        int visibleItems = cols * rows;

        int startIndex = scrollOffset * (direction == Direction.VERTICAL ? cols : 1);
        for (int i = 0; i < visibleItems && startIndex + i < contentItems.size(); i++) {
            int row = i / cols;
            int col = 1 + (i % cols);
            int slot = row * 9 + col;
            MenuItem item = contentItems.get(startIndex + i);
            if (slot < size) {
                inv.setItem(slot, item.getItemStack());
                this.items.put(slot, item);
            }
        }

        // Scroll buttons
        setItem(scrollUpSlot, createButton(Material.ARROW, "<yellow>▲ Rul op"), ctx -> { scrollUp(); refresh(ctx.player()); });
        setItem(scrollDownSlot, createButton(Material.ARROW, "<yellow>▼ Rul ned"), ctx -> { scrollDown(); refresh(ctx.player()); });

        this.items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item.getItemStack());
        });
    }

    private ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(name));
        item.setItemMeta(meta);
        return item;
    }
}
