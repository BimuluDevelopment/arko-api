package dk.arko.api.paper.menu.types;

import dk.arko.api.common.text.TextUtil;
import dk.arko.api.paper.menu.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

// ═══════════════════════════════════════════════════════════════
// CHEST MENU - Standard chest inventory (1-6 rows)
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// PAGINATED MENU - Auto-paging with navigation buttons
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// CONFIRMATION MENU - Yes/No dialog
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// CATEGORY / TABBED MENU - Switch between categories
// ═══════════════════════════════════════════════════════════════

public class CategoryMenuImpl extends Menu {
    private final List<Category> categories = new ArrayList<>();
    private int activeCategory = 0;
    private int[] tabSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private int[] contentSlots;

    public CategoryMenuImpl(Component title, int rows) {
        super(title, rows);
        contentSlots = generateContentSlots(rows);
    }

    public CategoryMenuImpl addCategory(String name, ItemStack icon, List<MenuItem> items) {
        categories.add(new Category(name, icon, items));
        return this;
    }

    public CategoryMenuImpl setTabSlots(int... slots) {
        this.tabSlots = slots;
        return this;
    }

    public CategoryMenuImpl setContentSlots(int... slots) {
        this.contentSlots = slots;
        return this;
    }

    public void switchCategory(int index) {
        this.activeCategory = Math.max(0, Math.min(index, categories.size() - 1));
    }

    @Override
    protected void render(Inventory inv) {
        inv.clear();

        // Render tabs
        for (int i = 0; i < categories.size() && i < tabSlots.length; i++) {
            Category cat = categories.get(i);
            ItemStack tabIcon = cat.icon.clone();
            ItemMeta meta = tabIcon.getItemMeta();
            if (i == activeCategory) {
                meta.displayName(TextUtil.parse("<green><bold>" + cat.name));
                meta.setEnchantmentGlintOverride(true);
            } else {
                meta.displayName(TextUtil.parse("<gray>" + cat.name));
            }
            tabIcon.setItemMeta(meta);
            final int catIndex = i;
            setItem(tabSlots[i], tabIcon, ctx -> {
                switchCategory(catIndex);
                refresh(ctx.player());
            });
        }

        // Render content items
        if (!categories.isEmpty()) {
            Category active = categories.get(activeCategory);
            for (int i = 0; i < contentSlots.length; i++) {
                if (i < active.items.size()) {
                    MenuItem item = active.items.get(i);
                    this.items.put(contentSlots[i], item);
                    inv.setItem(contentSlots[i], item.getItemStack());
                }
            }
        }

        // Render static items
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item.getItemStack());
            }
        });
    }

    private int[] generateContentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private record Category(String name, ItemStack icon, List<MenuItem> items) {}
}

// ═══════════════════════════════════════════════════════════════
// SCROLLING MENU - Vertical/horizontal scrolling
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// ANIMATED MENU - Animated slot changes
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// HOPPER MENU - 5-slot hopper inventory
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// DISPENSER MENU - 3x3 grid
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// FILTERABLE MENU - Search/filter support
// ═══════════════════════════════════════════════════════════════

