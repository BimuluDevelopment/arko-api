package dk.arko.api.paper.menu.types;

import dk.arko.api.paper.menu.Menu;
import dk.arko.api.paper.menu.MenuItem;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class FilterableMenuImpl extends Menu {
    private final List<MenuItem> allItems = new ArrayList<>();
    private List<MenuItem> filteredItems;
    private Predicate<MenuItem> filter;
    private int[] contentSlots;

    public FilterableMenuImpl(Component title, int rows) {
        super(title, rows);
        this.filteredItems = allItems;
        this.contentSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
    }

    public FilterableMenuImpl setAllItems(List<MenuItem> items) { allItems.clear(); allItems.addAll(items); applyFilter(); return this; }
    public FilterableMenuImpl setContentSlots(int... slots) { this.contentSlots = slots; return this; }

    public FilterableMenuImpl setFilter(Predicate<MenuItem> filter) {
        this.filter = filter;
        applyFilter();
        return this;
    }

    public FilterableMenuImpl clearFilter() {
        this.filter = null;
        this.filteredItems = allItems;
        return this;
    }

    private void applyFilter() {
        if (filter == null) {
            filteredItems = allItems;
        } else {
            filteredItems = allItems.stream().filter(filter).toList();
        }
    }

    @Override
    protected void render(Inventory inv) {
        inv.clear();
        items.forEach((slot, item) -> {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item.getItemStack());
        });
        for (int i = 0; i < contentSlots.length && i < filteredItems.size(); i++) {
            MenuItem item = filteredItems.get(i);
            inv.setItem(contentSlots[i], item.getItemStack());
            this.items.put(contentSlots[i], item);
        }
    }
}
