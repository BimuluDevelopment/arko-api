package dk.arko.api.paper.menu.types;

import dk.arko.api.paper.menu.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;

public class HopperMenuImpl extends Menu {
    public HopperMenuImpl(Component title) {
        super(title, InventoryType.HOPPER);
    }
}
