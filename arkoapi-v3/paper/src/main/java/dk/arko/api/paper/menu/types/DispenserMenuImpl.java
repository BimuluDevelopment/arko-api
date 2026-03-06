package dk.arko.api.paper.menu.types;

import dk.arko.api.paper.menu.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;

public class DispenserMenuImpl extends Menu {
    public DispenserMenuImpl(Component title) {
        super(title, InventoryType.DISPENSER);
    }
}
