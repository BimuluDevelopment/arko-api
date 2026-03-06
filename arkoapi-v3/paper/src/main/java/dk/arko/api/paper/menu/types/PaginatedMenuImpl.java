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
import java.util.Collection;
import java.util.List;
import java.util.function.Function; /**
 * Automatically paginates a list of items across multiple pages.
 * Navigation buttons are added automatically.
 *
 * Usage:
 *   PaginatedMenu menu = new PaginatedMenu("Shop", 6);
 *   menu.setContentSlots(10,11,12,13,14,15,16, 19,20,21,22,23,24,25);
 *   menu.setItems(shopItems);
 *   menu.open(player);
 */
public class PaginatedMenuImpl extends Menu {
    private final List<MenuItem> contentItems = new ArrayList<>();
    private int[] contentSlots;
    private int currentPage = 0;
    private int prevButtonSlot = 45;
    private int nextButtonSlot = 53;
    private int pageInfoSlot = 49;
    private ItemStack prevButton;
    private ItemStack nextButton;
    private Function<int[], ItemStack> pageInfoBuilder;

    public PaginatedMenuImpl(Component title, int rows) {
        super(title, rows);
        // Default content slots (center area for 6-row chest)
        contentSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }

    public PaginatedMenuImpl setContentSlots(int... slots) {
        this.contentSlots = slots;
        return this;
    }

    public PaginatedMenuImpl setItems(List<MenuItem> items) {
        this.contentItems.clear();
        this.contentItems.addAll(items);
        return this;
    }

    public PaginatedMenuImpl addItem(MenuItem item) {
        this.contentItems.add(item);
        return this;
    }

    public PaginatedMenuImpl addItems(Collection<MenuItem> items) {
        this.contentItems.addAll(items);
        return this;
    }

    public PaginatedMenuImpl setPrevButton(int slot, ItemStack item) {
        this.prevButtonSlot = slot;
        this.prevButton = item;
        return this;
    }

    public PaginatedMenuImpl setNextButton(int slot, ItemStack item) {
        this.nextButtonSlot = slot;
        this.nextButton = item;
        return this;
    }

    public PaginatedMenuImpl setPageInfoSlot(int slot) {
        this.pageInfoSlot = slot;
        return this;
    }

    public PaginatedMenuImpl setPageInfoBuilder(Function<int[], ItemStack> builder) {
        this.pageInfoBuilder = builder;
        return this;
    }

    public int getMaxPage() {
        return Math.max(0, (contentItems.size() - 1) / contentSlots.length);
    }

    public int getCurrentPage() { return currentPage; }

    public void setPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, getMaxPage()));
    }

    @Override
    protected void render(Inventory inv) {
        inv.clear();

        // Render static items first
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item.getItemStack());
            }
        });

        // Render paginated content
        int startIndex = currentPage * contentSlots.length;
        for (int i = 0; i < contentSlots.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex < contentItems.size()) {
                MenuItem item = contentItems.get(dataIndex);
                inv.setItem(contentSlots[i], item.getItemStack());
                // Register click handler in menu items map
                this.items.put(contentSlots[i], item);
            } else {
                inv.setItem(contentSlots[i], null);
                this.items.remove(contentSlots[i]);
            }
        }

        // Navigation buttons
        if (currentPage > 0) {
            ItemStack prev = prevButton != null ? prevButton : createNavItem(Material.ARROW, "<green>← Forrige side");
            this.setItem(prevButtonSlot, prev, ctx -> { currentPage--; refresh(ctx.player()); });
        }
        if (currentPage < getMaxPage()) {
            ItemStack next = nextButton != null ? nextButton : createNavItem(Material.ARROW, "<green>Næste side →");
            this.setItem(nextButtonSlot, next, ctx -> { currentPage++; refresh(ctx.player()); });
        }

        // Page info
        if (pageInfoSlot >= 0) {
            ItemStack info;
            if (pageInfoBuilder != null) {
                info = pageInfoBuilder.apply(new int[]{currentPage + 1, getMaxPage() + 1});
            } else {
                info = createNavItem(Material.PAPER, "<yellow>Side " + (currentPage + 1) + "/" + (getMaxPage() + 1));
            }
            inv.setItem(pageInfoSlot, info);
        }

        // Re-render static items on top
        items.forEach((slot, item) -> {
            boolean isContentSlot = false;
            for (int cs : contentSlots) if (cs == slot) { isContentSlot = true; break; }
            if (!isContentSlot && slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item.getItemStack());
            }
        });
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parse(name));
        item.setItemMeta(meta);
        return item;
    }
}
